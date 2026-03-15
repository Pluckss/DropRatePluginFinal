package com.pluckss.droprate;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.awt.Color;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Drop Rate",
	enabledByDefault = true
)
public class DropRatePlugin extends Plugin
{
	private static final String CONFIG_GROUP = "droprate";
	private static final String DISPLAY_MODE_KEY = "displayMode";
	private static final String LEGACY_RARE_ONLY_KEY = "onlyHighDrops";
	private static final String LEGACY_HIDE_ALWAYS_KEY = "hideAlwaysDrops";
	private static final String LEGACY_HIDE_USELESS_KEY = "hideUselessDrops";
	private static final int BOSSES_TASK_ID = 98;
	private static final Pattern CHANCE_PATTERN = Pattern.compile(
		"(?i)(?:(\\d+(?:\\.\\d+)?)\\s*[x*]\\s*)?(\\d+(?:\\.\\d+)?)\\s*/\\s*(\\d+(?:\\.\\d+)?)"
	);

	private static final Color DEFAULT_GREEN = new Color(46, 125, 50);
	private static final Color DEFAULT_ORANGE = new Color(255, 140, 0);
	private static final Color DEFAULT_RED = new Color(178, 34, 34);
	private static final Color DEFAULT_PURPLE = new Color(156, 39, 176);
	private static final Color WHITE = new Color(255, 255, 255);
	private static final DecimalFormat EFFECTIVE_RATE_FORMAT = createEffectiveRateFormat();
	private static final DecimalFormat PERCENTAGE_FORMAT = createPercentageFormat();

	@Inject
	private Client client;

	@Inject
	private DropRateConfig config;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private Gson gson;

	@Inject
	private ConfigManager configManager;

	private Map<String, Map<String, String>> primaryDrops;
	private Map<String, Map<String, String>> invertedDrops;
	private Map<String, Map<String, String>> rdtDrops;
	private Map<String, Map<String, String>> invertedRdtDrops;
	private DropMetadata dropMetadata = DropMetadata.empty();
	private final Map<String, Integer> recentLootSignatures = new HashMap<>();
	private final Map<String, Integer> recentMessageKeys = new HashMap<>();

	@Provides
	DropRateConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DropRateConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		migrateLegacyConfig();

		primaryDrops = loadPrimaryDrops("/droprates_clean.json");
		invertedDrops = invertDropMap(primaryDrops);
		rdtDrops = loadOptionalDrops("/rare_drop_table.json");
		invertedRdtDrops = invertDropMap(rdtDrops);
		dropMetadata = loadDropMetadata("/drop_metadata.json");

		DropRateDisplayMode displayMode = getDisplayMode();
		log.info(
			"DropRate database loaded: {} primary entries, {} RDT entries, {} drop overrides, {} context rules",
			primaryDrops != null ? primaryDrops.size() : 0,
			rdtDrops != null ? rdtDrops.size() : 0,
			dropMetadata.getDropOverrideCount(),
			dropMetadata.getNpcContextCount()
		);
		log.info(
			"DropRate config active: mode={}, multiRolls={}, rareThreshold={}, rateFormatMode={}, showRatePercentage={}, colorMode={}, commonTierMax={}, rareTierMin={}, ultraRareTierMin={}, alternateTables={}",
			displayMode,
			config.showMultiRollDrops(),
			config.rareDropThreshold(),
			config.rateFormatMode(),
			config.showRatePercentage(),
			config.colorMode(),
			config.commonTierThreshold(),
			config.rareColorThreshold(),
			config.ultraRareColorThreshold(),
			config.showAlternateTables()
		);
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		NPC npc = event.getNpc();
		if (npc == null)
		{
			return;
		}

		handleLoot(cleanName(npc.getName()), event.getItems());
	}

	@Subscribe
	public void onServerNpcLoot(ServerNpcLoot event)
	{
		NPCComposition composition = event.getComposition();
		if (composition == null)
		{
			return;
		}

		handleLoot(cleanName(composition.getName()), event.getItems());
	}

	private void handleLoot(String npcName, Collection<ItemStack> items)
	{
		if (primaryDrops == null || primaryDrops.isEmpty())
		{
			return;
		}

		if (npcName == null)
		{
			return;
		}

		if (client.getLocalPlayer() == null)
		{
			return;
		}

		if (items == null || items.isEmpty())
		{
			return;
		}

		if (isDuplicateLoot(npcName, items))
		{
			log.debug("Ignoring duplicate loot event for {}: {}", npcName, items);
			return;
		}

		log.debug("Processing loot event for {}: {}", npcName, items);

		DropRateDisplayMode displayMode = getDisplayMode();
		String currentSlayerTask = getCurrentSlayerTaskName();

		for (ItemStack stack : items)
		{
			String itemName = cleanName(client.getItemDefinition(stack.getId()).getName());
			if (itemName == null)
			{
				continue;
			}

			ResolvedDrop resolvedDrop = resolveDrop(npcName, itemName, currentSlayerTask);
			if (resolvedDrop == null)
			{
				log.debug("No rarity entry found for {} from {}", itemName, npcName);
				continue;
			}

			Chance chance = parseChance(resolvedDrop.rate);
			if (displayMode == DropRateDisplayMode.CLEANER_FEED && isGuaranteedDrop(chance))
			{
				continue;
			}

			if (displayMode == DropRateDisplayMode.CLEANER_FEED && isFillerDrop(itemName))
			{
				continue;
			}

			double effectiveRate = resolvedDrop.effectiveRate;
			if (displayMode == DropRateDisplayMode.RARE_DROPS_ONLY)
			{
				int threshold = Math.max(1, config.rareDropThreshold());
				if (effectiveRate <= 0 || effectiveRate < threshold)
				{
					continue;
				}
			}

			if (!config.showMultiRollDrops() && chance != null && chance.numerator >= 2.0d)
			{
				continue;
			}

			Color color = getColor(effectiveRate);
			String message = colorTag(color) + stack.getQuantity() + "x " + itemName + " (" + resolvedDrop.formattedRate + ")</col>";
			String messageKey = buildMessageKey(npcName, stack, resolvedDrop.formattedRate);

			if (isDuplicateMessage(messageKey))
			{
				log.debug("Ignoring duplicate drop rate message for {} from {}: {}", itemName, npcName, resolvedDrop.formattedRate);
				continue;
			}

			chatMessageManager.queue(
				QueuedMessage.builder()
					.type(ChatMessageType.GAMEMESSAGE)
					.runeLiteFormattedMessage(message)
					.build()
			);

			log.debug(
				"Queued drop rate message for {} from {} using {}: {}",
				itemName,
				npcName,
				resolvedDrop.lookupNpc,
				resolvedDrop.formattedRate
			);
		}
	}

	private boolean isDuplicateLoot(String npcName, Collection<ItemStack> items)
	{
		int tick = client.getTickCount();
		String signature = buildLootSignature(npcName, items);
		expireOldEntries(recentLootSignatures, tick);

		Integer previousTick = recentLootSignatures.put(signature, tick);
		return previousTick != null && tick - previousTick <= 1;
	}

	private String buildLootSignature(String npcName, Collection<ItemStack> items)
	{
		ArrayList<ItemStack> sortedItems = new ArrayList<>(items);
		sortedItems.sort(Comparator.comparingInt(ItemStack::getId).thenComparingInt(ItemStack::getQuantity));

		StringBuilder signature = new StringBuilder(npcName);
		for (ItemStack item : sortedItems)
		{
			signature.append('|').append(item.getId()).append(':').append(item.getQuantity());
		}

		return signature.toString();
	}

	private boolean isDuplicateMessage(String messageKey)
	{
		int tick = client.getTickCount();
		expireOldEntries(recentMessageKeys, tick);

		Integer previousTick = recentMessageKeys.put(messageKey, tick);
		return previousTick != null && tick - previousTick <= 1;
	}

	private String buildMessageKey(String npcName, ItemStack stack, String rarity)
	{
		return npcName + '|' + stack.getId() + '|' + stack.getQuantity() + '|' + rarity;
	}

	private void expireOldEntries(Map<String, Integer> entries, int currentTick)
	{
		entries.entrySet().removeIf(entry -> currentTick - entry.getValue() > 1);
	}

	private Map<String, Map<String, String>> loadPrimaryDrops(String resourcePath)
	{
		InputStream in = getClass().getResourceAsStream(resourcePath);
		if (in == null)
		{
			throw new IllegalStateException("Missing resource: " + resourcePath);
		}

		try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			Map<String, Map<String, String>> loaded = gson.fromJson(
				reader,
				new TypeToken<Map<String, Map<String, String>>>() {}.getType()
			);
			return loaded != null ? loaded : new HashMap<>();
		}
		catch (Exception e)
		{
			throw new IllegalStateException("Unable to read resource: " + resourcePath, e);
		}
	}

	private Map<String, Map<String, String>> loadOptionalDrops(String resourcePath)
	{
		InputStream in = getClass().getResourceAsStream(resourcePath);
		if (in == null)
		{
			log.info("Optional resource not found: {}", resourcePath);
			return new HashMap<>();
		}

		try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			Map<String, Map<String, String>> loaded = gson.fromJson(
				reader,
				new TypeToken<Map<String, Map<String, String>>>() {}.getType()
			);
			return loaded != null ? loaded : new HashMap<>();
		}
		catch (Exception e)
		{
			log.warn("Unable to read optional resource: {}", resourcePath, e);
			return new HashMap<>();
		}
	}

	private DropMetadata loadDropMetadata(String resourcePath)
	{
		InputStream in = getClass().getResourceAsStream(resourcePath);
		if (in == null)
		{
			log.warn("Missing metadata resource: {}", resourcePath);
			return DropMetadata.empty();
		}

		try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			DropMetadata loaded = gson.fromJson(reader, DropMetadata.class);
			return loaded != null ? loaded : DropMetadata.empty();
		}
		catch (Exception e)
		{
			throw new IllegalStateException("Unable to read resource: " + resourcePath, e);
		}
	}

	private ResolvedDrop resolveDrop(String npcName, String itemName, String currentSlayerTask)
	{
		String lookupNpc = resolveLookupNpc(npcName, currentSlayerTask);

		DropMatch normalMatch = findDropMatch(lookupNpc, itemName);
		if (normalMatch == null && !lookupNpc.equals(npcName))
		{
			normalMatch = findDropMatch(npcName, itemName);
		}

		String rdtRate = findRdtRate(lookupNpc, itemName);
		if (rdtRate == null && !lookupNpc.equals(npcName))
		{
			rdtRate = findRdtRate(npcName, itemName);
		}

		if (normalMatch == null && rdtRate == null)
		{
			return null;
		}

		if (normalMatch == null)
		{
			Chance rdtChance = parseChance(rdtRate);
			return new ResolvedDrop(
				lookupNpc,
				rdtRate,
				formatSingleRate(rdtRate, rdtChance),
				getEffectiveRate(rdtChance, rdtRate)
			);
		}

		Chance chance = parseChance(normalMatch.rate);
		return new ResolvedDrop(
			normalMatch.npcName,
			normalMatch.rate,
			formatRateForMessage(normalMatch.rate, chance, normalMatch.override, rdtRate),
			getEffectiveRate(chance, normalMatch.rate)
		);
	}

	private DropMatch findDropMatch(String npcName, String itemName)
	{
		if (npcName == null || itemName == null)
		{
			return null;
		}

		DropOverride override = dropMetadata.getDropOverride(itemName, npcName);
		String rate = getOverrideRate(override);
		if (rate == null)
		{
			rate = findBaseRate(npcName, itemName);
		}

		return rate == null ? null : new DropMatch(npcName, rate, override);
	}

	private String resolveLookupNpc(String npcName, String currentSlayerTask)
	{
		for (NpcContextRule rule : dropMetadata.getNpcContexts(npcName))
		{
			if (matchesContext(rule, currentSlayerTask) && !isBlank(rule.lookupNpc))
			{
				return rule.lookupNpc;
			}
		}

		return npcName;
	}

	private boolean matchesContext(NpcContextRule rule, String currentSlayerTask)
	{
		if (rule == null || isBlank(rule.condition))
		{
			return false;
		}

		if ("slayer_task".equalsIgnoreCase(rule.condition))
		{
			return !isBlank(rule.task) && !isBlank(currentSlayerTask) && rule.task.equalsIgnoreCase(currentSlayerTask);
		}

		return false;
	}

	private String getCurrentSlayerTaskName()
	{
		try
		{
			if (client == null || client.getLocalPlayer() == null)
			{
				return null;
			}

			int amount = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
			if (amount <= 0)
			{
				return null;
			}

			int taskId = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
			if (taskId <= 0)
			{
				return null;
			}

			int taskRow;
			if (taskId == BOSSES_TASK_ID)
			{
				var bossRows = client.getDBRowsByValue(
					DBTableID.SlayerTaskSublist.ID,
					DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID,
					0,
					client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID)
				);
				if (bossRows.isEmpty())
				{
					return null;
				}

				taskRow = (Integer) client.getDBTableField(bossRows.get(0), DBTableID.SlayerTaskSublist.COL_TASK, 0)[0];
			}
			else
			{
				var taskRows = client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, taskId);
				if (taskRows.isEmpty())
				{
					return null;
				}

				taskRow = taskRows.get(0);
			}

			Object[] taskField = client.getDBTableField(taskRow, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0);
			if (taskField.length == 0 || !(taskField[0] instanceof String))
			{
				return null;
			}

			return cleanName((String) taskField[0]);
		}
		catch (Exception e)
		{
			log.debug("Unable to resolve current slayer task", e);
			return null;
		}
	}

	private String formatRateForMessage(String rawRate, Chance primaryChance, DropOverride override, String rdtRate)
	{
		String formattedPrimaryRate = formatSingleRate(rawRate, primaryChance);

		if (config.showAlternateTables() && override != null && override.hasTableDetails())
		{
			ArrayList<String> parts = new ArrayList<>();
			parts.add(formatLabeledRate(override.primaryLabel, formattedPrimaryRate));

			for (LabeledRate alternate : override.getAlternates())
			{
				if (alternate == null || isBlank(alternate.rate))
				{
					continue;
				}

				parts.add(formatLabeledRate(alternate.label, formatSingleRate(alternate.rate, parseChance(alternate.rate))));
			}

			return parts.isEmpty() ? formattedPrimaryRate : String.join(" | ", parts);
		}

		if (config.showAlternateTables() && rdtRate != null)
		{
			String formattedRdtRate = formatSingleRate(rdtRate, parseChance(rdtRate));
			return formatLabeledRate("Normal", formattedPrimaryRate) + " | " + formatLabeledRate("RDT", formattedRdtRate);
		}

		return formattedPrimaryRate;
	}

	private String formatLabeledRate(String label, String rate)
	{
		return isBlank(label) ? rate : label + " " + rate;
	}

	private String findRdtRate(String npcName, String itemName)
	{
		if (rdtDrops == null || rdtDrops.isEmpty() || npcName == null || itemName == null)
		{
			return null;
		}

		Map<String, String> ratesForItem = rdtDrops.get(itemName);
		if (ratesForItem == null)
		{
			return null;
		}

		String rate = ratesForItem.get(npcName);
		if (rate != null)
		{
			return rate;
		}

		rate = ratesForItem.get(npcName + " Standard");
		if (rate != null)
		{
			return rate;
		}

		for (Map.Entry<String, String> entry : ratesForItem.entrySet())
		{
			String key = entry.getKey();
			if (key.length() > npcName.length() && key.startsWith(npcName))
			{
				char separator = key.charAt(npcName.length());
				if (separator == ' ' || separator == '(')
				{
					return entry.getValue();
				}
			}
		}

		return null;
	}

	private String getOverrideRate(DropOverride override)
	{
		return override == null || isBlank(override.rate) ? null : override.rate;
	}

	private String formatSingleRate(String rawRate, Chance chance)
	{
		if (rawRate == null)
		{
			return null;
		}

		String effectiveRate = formatEffectiveRate(rawRate, chance);
		String formattedRate;
		switch (config.rateFormatMode())
		{
			case EFFECTIVE_RATE:
				formattedRate = effectiveRate != null ? effectiveRate : rawRate;
				break;
			case BOTH:
				if (effectiveRate == null || rawRate.equals(effectiveRate))
				{
					formattedRate = rawRate;
					break;
				}
				formattedRate = rawRate + " -> " + effectiveRate;
				break;
			case RAW_RATE:
			default:
				formattedRate = rawRate;
				break;
		}

		return appendPercentage(formattedRate, chance);
	}

	private String formatEffectiveRate(String rawRate, Chance chance)
	{
		double effectiveRate = getEffectiveRate(chance, rawRate);
		if (effectiveRate <= 0)
		{
			return null;
		}

		return "1/" + formatRateNumber(effectiveRate);
	}

	private String appendPercentage(String baseText, Chance chance)
	{
		if (baseText == null || !config.showRatePercentage() || chance == null || chance.denominator <= 0)
		{
			return baseText;
		}

		double percentage = (chance.numerator / chance.denominator) * 100.0d;
		if (percentage <= 0)
		{
			return baseText;
		}

		return baseText + " (" + formatPercentage(percentage) + "%)";
	}

	private String formatPercentage(double value)
	{
		if (Math.abs(value - Math.rint(value)) < 0.000001d)
		{
			return Long.toString(Math.round(value));
		}

		synchronized (PERCENTAGE_FORMAT)
		{
			return PERCENTAGE_FORMAT.format(value);
		}
	}

	private String formatRateNumber(double value)
	{
		if (Math.abs(value - Math.rint(value)) < 0.000001d)
		{
			return Long.toString(Math.round(value));
		}

		synchronized (EFFECTIVE_RATE_FORMAT)
		{
			return EFFECTIVE_RATE_FORMAT.format(value);
		}
	}

	private static DecimalFormat createEffectiveRateFormat()
	{
		DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
		DecimalFormat format = new DecimalFormat("0.##", symbols);
		format.setGroupingUsed(false);
		return format;
	}

	private static DecimalFormat createPercentageFormat()
	{
		DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
		DecimalFormat format = new DecimalFormat("0.##", symbols);
		format.setGroupingUsed(false);
		return format;
	}

	private String findBaseRate(String npcName, String itemName)
	{
		Map<String, String> fromPrimaryNpc = primaryDrops.get(npcName);
		if (fromPrimaryNpc != null)
		{
			String rarity = fromPrimaryNpc.get(itemName);
			if (rarity != null)
			{
				return rarity;
			}
		}

		Map<String, String> fromPrimaryItem = primaryDrops.get(itemName);
		if (fromPrimaryItem != null)
		{
			String rarity = fromPrimaryItem.get(npcName);
			if (rarity != null)
			{
				return rarity;
			}
		}

		Map<String, String> fromInvertedNpc = invertedDrops.get(npcName);
		if (fromInvertedNpc != null)
		{
			return fromInvertedNpc.get(itemName);
		}

		return null;
	}

	private Map<String, Map<String, String>> invertDropMap(Map<String, Map<String, String>> input)
	{
		Map<String, Map<String, String>> inverted = new HashMap<>();
		if (input == null || input.isEmpty())
		{
			return inverted;
		}

		for (Map.Entry<String, Map<String, String>> outer : input.entrySet())
		{
			String outerKey = outer.getKey();
			Map<String, String> inner = outer.getValue();
			if (inner == null || inner.isEmpty())
			{
				continue;
			}

			for (Map.Entry<String, String> innerEntry : inner.entrySet())
			{
				inverted
					.computeIfAbsent(innerEntry.getKey(), k -> new HashMap<>())
					.put(outerKey, innerEntry.getValue());
			}
		}

		return inverted;
	}

	private Chance parseChance(String rarity)
	{
		try
		{
			Matcher matcher = CHANCE_PATTERN.matcher(rarity);
			if (!matcher.find())
			{
				return null;
			}

			double multiplier = matcher.group(1) != null ? Double.parseDouble(matcher.group(1)) : 1.0d;
			double baseNumerator = Double.parseDouble(matcher.group(2));
			double denominator = Double.parseDouble(matcher.group(3));
			if (denominator <= 0)
			{
				return null;
			}

			return new Chance(multiplier * baseNumerator, denominator);
		}
		catch (Exception e)
		{
			return null;
		}
	}

	private double getEffectiveRate(Chance chance, String rarity)
	{
		if (chance != null && chance.numerator > 0)
		{
			return chance.denominator / chance.numerator;
		}

		try
		{
			Matcher matcher = CHANCE_PATTERN.matcher(rarity);
			if (!matcher.find())
			{
				return 0;
			}

			double denominator = Double.parseDouble(matcher.group(3));
			return denominator > 0 ? denominator : 0;
		}
		catch (Exception e)
		{
			return 0;
		}
	}

	private Color getColor(double rate)
	{
		if (config.colorMode() == DropRateColorMode.NEUTRAL_WHITE)
		{
			return WHITE;
		}

		int commonTierMax = Math.max(1, config.commonTierThreshold());
		int rareTierMin = Math.max(commonTierMax + 1, config.rareColorThreshold());
		int ultraRareTierMin = Math.max(rareTierMin + 1, config.ultraRareColorThreshold());
		if (rate >= ultraRareTierMin)
		{
			Color c = config.ultraRareTierColor();
			return c != null ? c : DEFAULT_PURPLE;
		}

		if (rate >= rareTierMin)
		{
			Color c = config.rareTierColor();
			return c != null ? c : DEFAULT_RED;
		}

		if (rate > commonTierMax)
		{
			Color c = config.uncommonTierColor();
			return c != null ? c : DEFAULT_ORANGE;
		}

		Color c = config.commonTierColor();
		return c != null ? c : DEFAULT_GREEN;
	}

	private String colorTag(Color color)
	{
		return String.format("<col=%06x>", color.getRGB() & 0xFFFFFF);
	}

	private void migrateLegacyConfig()
	{
		if (configManager.getConfiguration(CONFIG_GROUP, DISPLAY_MODE_KEY) != null)
		{
			return;
		}

		if (!hasLegacyModeConfig())
		{
			return;
		}

		String legacyRareOnly = configManager.getConfiguration(CONFIG_GROUP, LEGACY_RARE_ONLY_KEY);
		String legacyHideAlways = configManager.getConfiguration(CONFIG_GROUP, LEGACY_HIDE_ALWAYS_KEY);
		String legacyHideUseless = configManager.getConfiguration(CONFIG_GROUP, LEGACY_HIDE_USELESS_KEY);

		DropRateDisplayMode migratedMode;
		if (parseLegacyBoolean(legacyRareOnly, false))
		{
			migratedMode = DropRateDisplayMode.RARE_DROPS_ONLY;
		}
		else if (parseLegacyBoolean(legacyHideAlways, true) || parseLegacyBoolean(legacyHideUseless, false))
		{
			migratedMode = DropRateDisplayMode.CLEANER_FEED;
		}
		else
		{
			migratedMode = DropRateDisplayMode.ALL_MATCHES;
		}

		configManager.setConfiguration(CONFIG_GROUP, DISPLAY_MODE_KEY, migratedMode.name());
		log.info("Migrated legacy DropRate config to display mode {}", migratedMode);
	}

	private boolean parseLegacyBoolean(String value, boolean fallback)
	{
		return value == null ? fallback : Boolean.parseBoolean(value);
	}

	private DropRateDisplayMode getDisplayMode()
	{
		if (configManager.getConfiguration(CONFIG_GROUP, DISPLAY_MODE_KEY) != null)
		{
			return config.displayMode();
		}

		if (!hasLegacyModeConfig())
		{
			return config.displayMode();
		}

		if (parseLegacyBoolean(configManager.getConfiguration(CONFIG_GROUP, LEGACY_RARE_ONLY_KEY), false))
		{
			return DropRateDisplayMode.RARE_DROPS_ONLY;
		}

		if (parseLegacyBoolean(configManager.getConfiguration(CONFIG_GROUP, LEGACY_HIDE_ALWAYS_KEY), true)
			|| parseLegacyBoolean(configManager.getConfiguration(CONFIG_GROUP, LEGACY_HIDE_USELESS_KEY), false))
		{
			return DropRateDisplayMode.CLEANER_FEED;
		}

		return config.displayMode();
	}

	private boolean hasLegacyModeConfig()
	{
		return configManager.getConfiguration(CONFIG_GROUP, LEGACY_RARE_ONLY_KEY) != null
			|| configManager.getConfiguration(CONFIG_GROUP, LEGACY_HIDE_ALWAYS_KEY) != null
			|| configManager.getConfiguration(CONFIG_GROUP, LEGACY_HIDE_USELESS_KEY) != null;
	}

	private boolean isGuaranteedDrop(Chance chance)
	{
		return chance != null && chance.numerator >= chance.denominator;
	}

	private boolean isFillerDrop(String itemName)
	{
		String normalized = itemName.toLowerCase();
		if (normalized.equals("bones") || normalized.endsWith(" bones")
			|| normalized.equals("ashes") || normalized.endsWith(" ashes"))
		{
			return true;
		}

		Set<String> extraIgnored = parseIgnoredItems(config.extraClutterItems());
		return extraIgnored.contains(normalized);
	}

	private Set<String> parseIgnoredItems(String csv)
	{
		Set<String> ignored = new HashSet<>();
		if (csv == null || csv.trim().isEmpty())
		{
			return ignored;
		}

		for (String token : csv.split(","))
		{
			String cleaned = token.trim().toLowerCase();
			if (!cleaned.isEmpty())
			{
				ignored.add(cleaned);
			}
		}

		return ignored;
	}

	private String cleanName(String name)
	{
		if (name == null)
		{
			return null;
		}

		String cleaned = Text.removeTags(name).trim();
		return cleaned.isEmpty() ? null : cleaned;
	}

	private static boolean isBlank(String value)
	{
		return value == null || value.trim().isEmpty();
	}

	private static final class ResolvedDrop
	{
		private final String lookupNpc;
		private final String rate;
		private final String formattedRate;
		private final double effectiveRate;

		private ResolvedDrop(String lookupNpc, String rate, String formattedRate, double effectiveRate)
		{
			this.lookupNpc = lookupNpc;
			this.rate = rate;
			this.formattedRate = formattedRate;
			this.effectiveRate = effectiveRate;
		}
	}

	private static final class DropMatch
	{
		private final String npcName;
		private final String rate;
		private final DropOverride override;

		private DropMatch(String npcName, String rate, DropOverride override)
		{
			this.npcName = npcName;
			this.rate = rate;
			this.override = override;
		}
	}

	private static final class DropMetadata
	{
		private Map<String, List<NpcContextRule>> npcContexts;
		private Map<String, Map<String, DropOverride>> dropOverrides;

		private static DropMetadata empty()
		{
			return new DropMetadata();
		}

		private List<NpcContextRule> getNpcContexts(String npcName)
		{
			if (npcContexts == null || npcName == null)
			{
				return Collections.emptyList();
			}

			List<NpcContextRule> rules = npcContexts.get(npcName);
			return rules != null ? rules : Collections.emptyList();
		}

		private DropOverride getDropOverride(String itemName, String npcName)
		{
			if (dropOverrides == null || itemName == null || npcName == null)
			{
				return null;
			}

			Map<String, DropOverride> overridesByNpc = dropOverrides.get(itemName);
			return overridesByNpc != null ? overridesByNpc.get(npcName) : null;
		}

		private int getDropOverrideCount()
		{
			if (dropOverrides == null)
			{
				return 0;
			}

			int count = 0;
			for (Map<String, DropOverride> overridesByNpc : dropOverrides.values())
			{
				if (overridesByNpc != null)
				{
					count += overridesByNpc.size();
				}
			}
			return count;
		}

		private int getNpcContextCount()
		{
			if (npcContexts == null)
			{
				return 0;
			}

			int count = 0;
			for (List<NpcContextRule> rules : npcContexts.values())
			{
				if (rules != null)
				{
					count += rules.size();
				}
			}
			return count;
		}
	}

	private static final class NpcContextRule
	{
		private String condition;
		private String task;
		private String lookupNpc;
	}

	private static final class DropOverride
	{
		private String rate;
		private String primaryLabel;
		private List<LabeledRate> alternates;

		private boolean hasTableDetails()
		{
			if (!isBlank(primaryLabel))
			{
				return true;
			}

			for (LabeledRate alternate : getAlternates())
			{
				if (alternate != null && !isBlank(alternate.rate))
				{
					return true;
				}
			}

			return false;
		}

		private List<LabeledRate> getAlternates()
		{
			return alternates != null ? alternates : Collections.emptyList();
		}
	}

	private static final class LabeledRate
	{
		private String label;
		private String rate;
	}

	private static final class Chance
	{
		private final double numerator;
		private final double denominator;

		private Chance(double numerator, double denominator)
		{
			this.numerator = numerator;
			this.denominator = denominator;
		}
	}
}
