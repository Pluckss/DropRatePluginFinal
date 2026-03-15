package com.pluckss.droprate;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.awt.Color;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
	private static final Pattern CHANCE_PATTERN = Pattern.compile(
		"(?i)(?:(\\d+(?:\\.\\d+)?)\\s*[x*]\\s*)?(\\d+(?:\\.\\d+)?)\\s*/\\s*(\\d+(?:\\.\\d+)?)"
	);

	private static final Color GREEN = new Color(46, 125, 50);
	private static final Color ORANGE = new Color(255, 140, 0);
	private static final Color RED = new Color(178, 34, 34);

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

		InputStream in = getClass().getResourceAsStream("/droprates_clean.json");
		if (in == null)
		{
			throw new IllegalStateException("Missing resource: /droprates_clean.json");
		}

		try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			primaryDrops = gson.fromJson(
				reader,
				new TypeToken<Map<String, Map<String, String>>>() {}.getType()
			);
		}
		invertedDrops = invertDropMap(primaryDrops);

		DropRateDisplayMode displayMode = getDisplayMode();
		log.info("DropRate database loaded: {} primary entries", primaryDrops != null ? primaryDrops.size() : 0);
		log.info(
			"DropRate config active: mode={}, multiRolls={}, rareThreshold={}",
			displayMode,
			config.showMultiRollDrops(),
			config.rareDropThreshold()
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

		for (ItemStack stack : items)
		{
			String itemName = cleanName(client.getItemDefinition(stack.getId()).getName());
			if (itemName == null)
			{
				continue;
			}

			String rarity = findRarity(npcName, itemName);
			if (rarity == null)
			{
				log.debug("No rarity entry found for {} from {}", itemName, npcName);
				continue;
			}

			Chance chance = parseChance(rarity);
			if (displayMode == DropRateDisplayMode.CLEANER_FEED && isGuaranteedDrop(chance))
			{
				continue;
			}

			if (displayMode == DropRateDisplayMode.CLEANER_FEED && isFillerDrop(itemName))
			{
				continue;
			}

			double effectiveRate = getEffectiveRate(chance, rarity);
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
			String message = colorTag(color) + stack.getQuantity() + "x " + itemName + " (" + rarity + ")</col>";
			String messageKey = buildMessageKey(npcName, stack, rarity);

			if (isDuplicateMessage(messageKey))
			{
				log.debug("Ignoring duplicate drop rate message for {} from {}: {}", itemName, npcName, rarity);
				continue;
			}

			chatMessageManager.queue(
				QueuedMessage.builder()
					.type(ChatMessageType.GAMEMESSAGE)
					.runeLiteFormattedMessage(message)
					.build()
			);

			log.debug("Queued drop rate message for {} from {}: {}", itemName, npcName, rarity);
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

	private String findRarity(String npcName, String itemName)
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
		if (rate >= 1000)
		{
			return RED;
		}

		if (rate > 300)
		{
			return ORANGE;
		}

		return GREEN;
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
