package com.pluckss.droprate;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.Notifier;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Notification;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;
import net.runelite.http.api.loottracker.LootRecordType;

@Slf4j
@PluginDescriptor(
	name = "Drop Rate",
	description = "Shows drop rates in chat when you receive loot, and as a tooltip when hovering Collection Log items",
	tags = {"drop", "rate", "loot", "collection", "log", "clog", "rarity", "clue"},
	enabledByDefault = true
)
public class DropRatePlugin extends Plugin
{
	private static final String CONFIG_GROUP = "droprate";
	private static final String DISPLAY_MODE_KEY = "displayMode";
	private static final String LEGACY_RARE_ONLY_KEY = "onlyHighDrops";
	private static final String LEGACY_HIDE_ALWAYS_KEY = "hideAlwaysDrops";
	private static final String LEGACY_HIDE_USELESS_KEY = "hideUselessDrops";
	private static final String LEGACY_NOTIFY_KEY = "notifyOnRareDrops";
	private static final String NOTIFICATION_KEY = "rareDropNotification";
	private static final String NOTIFICATION_MIGRATED_KEY = "notificationMigrated";
	// Owned by RuneLite's Chat Commands plugin, which writes the real kill counts there.
	private static final String KILL_COUNT_CONFIG_GROUP = "killcount";
	private static final String RING_OF_WEALTH_NAME = "ring of wealth";
	private static final int BOSSES_TASK_ID = 98;
	private static final Pattern CHANCE_PATTERN = Pattern.compile(
		"(?i)(?:([\\d.,]+)\\s*[x×*]\\s*)?([\\d.,]+)\\s*/\\s*([\\d.,]+)"
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

	@Inject
	private Notifier notifier;

	@Inject
	private ItemManager itemManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ClogTooltipOverlay clogTooltipOverlay;

	private Map<String, Map<String, String>> primaryDrops;
	private Map<String, Map<String, String>> invertedDrops;
	private Map<String, Map<String, String>> rdtDrops;
	private DropMetadata dropMetadata = DropMetadata.empty();
	private final Map<String, Integer> recentLootSignatures = new HashMap<>();
	private final Map<String, Integer> recentMessageKeys = new HashMap<>();
	// Fallback only. Counts kills seen since the plugin loaded, so it is reset by
	// every client restart, plugin toggle and update. Never presented as a real KC.
	private final Map<String, Integer> sessionKillCounts = new HashMap<>();

	// Collection Log tooltip: item name -> every source that drops it, kept
	// separate from the chat feature's data so it never alters chat behaviour.
	private Map<String, List<SourceRate>> clogSources = Collections.emptyMap();
	private String hoveredClogItem;
	private boolean hoveredClogItemObtained;
	private long clogHoverSeenAt;

	// The overlay asks for the tooltip once per rendered frame, so building it from
	// scratch every time would re-parse every rate of the hovered item ~50x a second
	// (Nature rune alone has 264 sources). The rendered text only depends on the item
	// and on config, so it is memoised and dropped whenever our config changes.
	// null values are cached too, to make repeated misses free.
	private final Map<String, String> clogTooltipCache = new HashMap<>();

	// Parsed form of the "Hidden filler items" CSV, rebuilt only when config changes
	// instead of once per item per drop.
	private Set<String> fillerItems;

	private static final long CLOG_HOVER_TIMEOUT_MS = 150;
	private static final int CLOG_MAX_RATE_GROUPS = 7;
	private static final int CLOG_MAX_SOURCES_PER_GROUP = 3;

	@Provides
	DropRateConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DropRateConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		migrateLegacyConfig();
		migrateNotificationConfig();

		primaryDrops = loadPrimaryDrops("/droprates_clean.json");
		mergeDropTable(primaryDrops, loadPrimaryDrops("/minigame_droprates.json", false));
		// Bosses whose drops the main crawler structurally misses (combined activity
		// pages, Unsired sink rewards). Merged like minigame data so both the chat
		// feed and the Collection Log tooltip see them.
		mergeDropTable(primaryDrops, loadPrimaryDrops("/special_droprates.json", false));
		invertedDrops = invertDropMap(primaryDrops);
		rdtDrops = loadOptionalDrops("/rare_drop_table.json");
		dropMetadata = loadDropMetadata("/drop_metadata.json");

		// Clue reward rates are loaded only for the Collection Log tooltip. They are
		// deliberately NOT merged into primaryDrops, so the chat feed is unaffected.
		Map<String, Map<String, String>> clueDrops = loadPrimaryDrops("/clue_droprates.json", false);
		clogSources = buildClogSources(primaryDrops, clueDrops, rdtDrops);
		overlayManager.add(clogTooltipOverlay);

		DropRateDisplayMode displayMode = getDisplayMode();
		log.info(
			"DropRate database loaded: {} primary entries, {} RDT entries, {} drop overrides, {} context rules, {} source aliases, {} source context rules",
			primaryDrops != null ? primaryDrops.size() : 0,
			rdtDrops != null ? rdtDrops.size() : 0,
			dropMetadata.getDropOverrideCount(),
			dropMetadata.getNpcContextCount(),
			dropMetadata.getSourceAliasCount(),
			dropMetadata.getSourceContextCount()
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

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(clogTooltipOverlay);
		hoveredClogItem = null;
		clogTooltipCache.clear();
		fillerItems = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CONFIG_GROUP.equals(event.getGroup()))
		{
			return;
		}

		// Rate format, percentages and the colour tiers all feed the rendered tooltip
		// text, so any of our settings changing invalidates the memoised tooltips.
		clogTooltipCache.clear();
		fillerItems = null;
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

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		// NPC loot already arrives via onNpcLootReceived / onServerNpcLoot. Only
		// activity reward sources (minigames, reward chests) come through here as
		// EVENT loot; filtering to EVENT avoids reporting NPC drops twice.
		if (event.getType() != LootRecordType.EVENT)
		{
			return;
		}

		handleLoot(cleanName(event.getName()), event.getItems());
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.showClogTooltip())
		{
			return;
		}

		MenuEntry entry = event.getMenuEntry();
		// The packed widget id is (groupId << 16 | childId); the high 16 bits are the
		// interface group. 621 is the Collection Log, regardless of which page is open.
		if ((entry.getParam1() >>> 16) != InterfaceID.COLLECTION)
		{
			return;
		}

		// Tabs, page names, and buttons also raise menu entries in this group;
		// only actual item slots carry an item id.
		if (entry.getItemId() <= 0)
		{
			return;
		}

		String itemName = cleanName(event.getTarget());
		if (itemName == null)
		{
			return;
		}

		hoveredClogItem = itemName;
		hoveredClogItemObtained = isClogItemObtained(entry);
		clogHoverSeenAt = System.currentTimeMillis();
	}

	private boolean isClogItemObtained(MenuEntry entry)
	{
		Widget widget = entry.getWidget();
		// Unobtained collection log slots are drawn faded; obtained ones are fully opaque.
		return widget != null && widget.getOpacity() == 0;
	}

	/**
	 * Called every render frame by {@link ClogTooltipOverlay}. Returns the tooltip
	 * text for the hovered collection log item, or null when nothing should show.
	 * MenuEntryAdded has no "hover ended" event, so a short freshness window makes
	 * the tooltip disappear once the mouse leaves the item.
	 */
	String buildActiveClogTooltip()
	{
		if (!config.showClogTooltip() || hoveredClogItem == null)
		{
			return null;
		}

		if (System.currentTimeMillis() - clogHoverSeenAt > CLOG_HOVER_TIMEOUT_MS)
		{
			return null;
		}

		if (client.isMenuOpen())
		{
			return null;
		}

		if (config.hideClogTooltipIfObtained() && hoveredClogItemObtained)
		{
			return null;
		}

		String itemName = hoveredClogItem;
		if (clogTooltipCache.containsKey(itemName))
		{
			return clogTooltipCache.get(itemName);
		}

		String tooltip = renderClogTooltip(itemName);
		clogTooltipCache.put(itemName, tooltip);
		return tooltip;
	}

	private String renderClogTooltip(String itemName)
	{
		List<SourceRate> sources = clogSources.get(itemName);
		if (sources == null || sources.isEmpty())
		{
			return null;
		}

		// Group sources that share the same rate so identical rates collapse to one line.
		LinkedHashMap<String, List<String>> sourcesByRate = new LinkedHashMap<>();
		for (SourceRate source : sources)
		{
			sourcesByRate.computeIfAbsent(source.rate, k -> new ArrayList<>()).add(source.source);
		}

		List<ClogRateGroup> groups = new ArrayList<>();
		for (Map.Entry<String, List<String>> entry : sourcesByRate.entrySet())
		{
			Chance chance = parseChance(entry.getKey());
			groups.add(new ClogRateGroup(entry.getKey(), chance, getEffectiveRate(chance, entry.getKey()), entry.getValue()));
		}

		// Most common source first — that is the best way to obtain the item.
		groups.sort(Comparator.comparingDouble(group -> group.effectiveRate));

		StringBuilder tooltip = new StringBuilder();
		tooltip.append(colorTag(WHITE)).append(itemName).append("</col>");

		int shown = 0;
		for (ClogRateGroup group : groups)
		{
			if (shown >= CLOG_MAX_RATE_GROUPS)
			{
				tooltip.append("<br>+").append(groups.size() - shown).append(" more");
				break;
			}

			String rate = formatSingleRate(group.rate, group.chance);
			Color color = getColor(group.effectiveRate);
			tooltip.append("<br>")
				.append(colorTag(color))
				.append(joinClogSources(group.sources))
				.append(": ")
				.append(rate)
				.append("</col>");
			shown++;
		}

		return tooltip.toString();
	}

	private String joinClogSources(List<String> sources)
	{
		if (sources.size() <= CLOG_MAX_SOURCES_PER_GROUP)
		{
			return String.join(", ", sources);
		}

		List<String> head = sources.subList(0, CLOG_MAX_SOURCES_PER_GROUP);
		return String.join(", ", head) + " +" + (sources.size() - CLOG_MAX_SOURCES_PER_GROUP) + " more";
	}

	private Map<String, List<SourceRate>> buildClogSources(
		Map<String, Map<String, String>> npcAndMinigame,
		Map<String, Map<String, String>> clueDrops,
		Map<String, Map<String, String>> rdt)
	{
		// item name -> (source name -> rate), de-duplicating repeated sources.
		Map<String, LinkedHashMap<String, String>> byItem = new HashMap<>();

		addSourceKeyedDrops(byItem, npcAndMinigame);
		addSourceKeyedDrops(byItem, clueDrops);

		// rdt is already item-keyed: item -> (source -> rate).
		if (rdt != null)
		{
			for (Map.Entry<String, Map<String, String>> entry : rdt.entrySet())
			{
				String item = entry.getKey();
				for (Map.Entry<String, String> sourceRate : entry.getValue().entrySet())
				{
					byItem.computeIfAbsent(item, k -> new LinkedHashMap<>())
						.putIfAbsent(sourceRate.getKey(), sourceRate.getValue());
				}
			}
		}

		Map<String, List<SourceRate>> result = new HashMap<>();
		for (Map.Entry<String, LinkedHashMap<String, String>> entry : byItem.entrySet())
		{
			List<SourceRate> sourceList = new ArrayList<>();
			for (Map.Entry<String, String> sourceRate : entry.getValue().entrySet())
			{
				sourceList.add(new SourceRate(sourceRate.getKey(), sourceRate.getValue()));
			}
			result.put(entry.getKey(), sourceList);
		}

		return result;
	}

	private void addSourceKeyedDrops(
		Map<String, LinkedHashMap<String, String>> byItem,
		Map<String, Map<String, String>> sourceKeyed)
	{
		if (sourceKeyed == null)
		{
			return;
		}

		for (Map.Entry<String, Map<String, String>> source : sourceKeyed.entrySet())
		{
			String sourceName = source.getKey();
			for (Map.Entry<String, String> itemRate : source.getValue().entrySet())
			{
				byItem.computeIfAbsent(itemRate.getKey(), k -> new LinkedHashMap<>())
					.putIfAbsent(sourceName, itemRate.getValue());
			}
		}
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

		sessionKillCounts.merge(npcName, 1, Integer::sum);
		int sessionKillCount = sessionKillCounts.get(npcName);

		log.debug("Processing loot event for {} (session kill {}): {}", npcName, sessionKillCount, items);

		DropRateDisplayMode displayMode = getDisplayMode();
		String currentSlayerTask = getCurrentSlayerTaskName();
		DropContext context = new DropContext(currentSlayerTask, isRingOfWealthEquipped(), isLegendsQuestCompleted());

		for (ItemStack stack : items)
		{
			String itemName = cleanName(client.getItemDefinition(stack.getId()).getName());
			if (itemName == null)
			{
				continue;
			}

			if (config.minItemValue() > 0)
			{
				int unitPrice = itemManager.getItemPrice(stack.getId());
				// Untradeables (pets, jars, Unsired) have no GE price and come back as 0.
				// They are exactly the drops players want to be told about, so a value
				// filter must never hide them — only filter items that do have a price.
				if (unitPrice > 0
					&& (long) unitPrice * Math.max(1, stack.getQuantity()) < config.minItemValue())
				{
					continue;
				}
			}

			ResolvedDrop resolvedDrop = resolveDrop(npcName, itemName, context);
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

			String rateDisplay = resolvedDrop.formattedRate;
			if (config.showKillCounter() && effectiveRate >= config.killCounterThreshold())
			{
				// Prefer the real, persisted kill count. The session counter alone is
				// meaningless next to "avg: ~1001 kills" — it reads like a genuine KC
				// but resets on every restart, so it must be labelled when it is used.
				int trackedKillCount = lookupTrackedKillCount(npcName);
				boolean sessionOnly = trackedKillCount < 0;
				rateDisplay = rateDisplay + " — KC: " + (sessionOnly ? sessionKillCount : trackedKillCount);
				if (sessionOnly)
				{
					rateDisplay = rateDisplay + " this session";
				}

				if (effectiveRate > 0)
				{
					// The average number of kills this drop takes, so the KC on its own
					// tells you whether you got lucky. Config has always promised this.
					rateDisplay = rateDisplay + ", avg: ~" + formatRateNumber(effectiveRate) + " kills";
				}
			}

			Color color = getColor(effectiveRate);
			String message = colorTag(color) + stack.getQuantity() + "x " + itemName + " (" + rateDisplay + ")</col>";
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

			if (effectiveRate >= config.notificationThreshold())
			{
				notifier.notify(
					config.rareDropNotification(),
					stack.getQuantity() + "x " + itemName + " (" + resolvedDrop.formattedRate + ")"
				);
			}

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
		return npcName + '|' + stack.getId() + '|' + rarity;
	}

	private void expireOldEntries(Map<String, Integer> entries, int currentTick)
	{
		entries.entrySet().removeIf(entry -> currentTick - entry.getValue() > 1);
	}

	private Map<String, Map<String, String>> loadPrimaryDrops(String resourcePath)
	{
		return loadPrimaryDrops(resourcePath, true);
	}

	private void mergeDropTable(Map<String, Map<String, String>> target, Map<String, Map<String, String>> additions)
	{
		if (target == null || additions == null)
		{
			return;
		}

		for (Map.Entry<String, Map<String, String>> entry : additions.entrySet())
		{
			target.computeIfAbsent(entry.getKey(), k -> new HashMap<>()).putAll(entry.getValue());
		}
	}

	private Map<String, Map<String, String>> loadPrimaryDrops(String resourcePath, boolean required)
	{
		InputStream in = getClass().getResourceAsStream(resourcePath);
		if (in == null)
		{
			if (required)
			{
				throw new IllegalStateException("Missing resource: " + resourcePath);
			}

			log.info("Optional resource not found: {}", resourcePath);
			return new HashMap<>();
		}

		try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			Map<String, Map<String, JsonElement>> raw = gson.fromJson(
				reader,
				new TypeToken<Map<String, Map<String, JsonElement>>>() {}.getType()
			);
			if (raw == null)
			{
				return new HashMap<>();
			}

			Map<String, Map<String, String>> result = new HashMap<>();
			for (Map.Entry<String, Map<String, JsonElement>> outer : raw.entrySet())
			{
				Map<String, String> inner = new HashMap<>();
				for (Map.Entry<String, JsonElement> entry : outer.getValue().entrySet())
				{
					JsonElement el = entry.getValue();
					if (el.isJsonArray())
					{
						JsonArray arr = el.getAsJsonArray();
						if (arr.size() > 0)
						{
							inner.put(entry.getKey(), arr.get(0).getAsString());
						}
					}
					else if (el.isJsonPrimitive())
					{
						inner.put(entry.getKey(), el.getAsString());
					}
				}
				result.put(outer.getKey(), inner);
			}
			return result;
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

	/**
	 * The player's real kill count for a source, or -1 when RuneLite has none.
	 * <p>
	 * RuneLite's own Chat Commands plugin parses the in-game "Your X kill count is:"
	 * message and stores it per RS profile in the "killcount" config group, keyed by
	 * {@code boss.toLowerCase()}. Reading that is what makes the KC survive restarts.
	 * It only exists for sources that print a kill count message (bosses, chests) and
	 * only once the player has killed one with Chat Commands enabled, so the caller
	 * must handle -1 rather than treat a missing value as zero kills.
	 */
	private int lookupTrackedKillCount(String npcName)
	{
		if (npcName == null)
		{
			return -1;
		}

		for (String key : killCountKeyCandidates(npcName))
		{
			Integer tracked;
			try
			{
				tracked = configManager.getRSProfileConfiguration(KILL_COUNT_CONFIG_GROUP, key, int.class);
			}
			catch (Exception e)
			{
				// A malformed stored value must never cost the player their drop message.
				log.debug("Unable to read kill count for {}", key, e);
				continue;
			}

			if (tracked != null && tracked > 0)
			{
				return tracked;
			}
		}

		return -1;
	}

	private List<String> killCountKeyCandidates(String npcName)
	{
		List<String> candidates = new ArrayList<>(3);

		String alias = dropMetadata.getKillCountAlias(npcName);
		if (alias != null)
		{
			candidates.add(alias.toLowerCase(Locale.ROOT));
		}

		String lower = npcName.toLowerCase(Locale.ROOT);
		candidates.add(lower);

		// The kill count message drops the article that the NPC name carries:
		// "The Whisperer" / "The Nightmare" / "The Hueycoatl" are all stored bare.
		if (lower.startsWith("the "))
		{
			candidates.add(lower.substring(4));
		}

		return candidates;
	}

	private ResolvedDrop resolveDrop(String npcName, String itemName, DropContext context)
	{
		List<String> sourceCandidates = resolveSourceCandidates(npcName, itemName, context);

		// The candidate list is ordered most-specific first, and the context-resolved
		// variants ("Araxxor Legends", "Demonic gorilla RoW") exist ONLY in the rare
		// drop table. Returning the first candidate that matched *anything* therefore
		// reported the RDT rate for items the monster also drops from its own table,
		// because the variant name never has a normal entry to beat it:
		//   Araxxor / Rune kiteshield   -> 1/14720 (RDT) instead of 8/115
		//   Demonic gorilla / Runite bar -> 1/2560 (RDT) instead of 15/500
		// The monster's own table is what the player actually rolled, so search every
		// candidate for a normal match first and only fall back to the RDT when the
		// item is genuinely rare-drop-table only.
		DropMatch normalMatch = null;
		for (String sourceCandidate : sourceCandidates)
		{
			normalMatch = findDropMatch(sourceCandidate, itemName);
			if (normalMatch != null)
			{
				break;
			}
		}

		// Resolved independently of the normal match so the "Show source hints" line
		// quotes the RDT rate for the variant the player is actually on (RoW/Legends),
		// not whichever candidate happened to carry the normal entry.
		String rdtSource = null;
		String rdtRate = null;
		for (String sourceCandidate : sourceCandidates)
		{
			rdtRate = findRdtRate(sourceCandidate, itemName);
			if (rdtRate != null)
			{
				rdtSource = sourceCandidate;
				break;
			}
		}

		if (normalMatch != null)
		{
			Chance chance = parseChance(normalMatch.rate);
			return new ResolvedDrop(
				normalMatch.npcName,
				normalMatch.rate,
				formatRateForMessage(normalMatch.rate, chance, normalMatch.override, rdtRate),
				getEffectiveRate(chance, normalMatch.rate)
			);
		}

		if (rdtRate != null)
		{
			Chance rdtChance = parseChance(rdtRate);
			return new ResolvedDrop(
				rdtSource,
				rdtRate,
				formatSingleRate(rdtRate, rdtChance),
				getEffectiveRate(rdtChance, rdtRate)
			);
		}

		return null;
	}

	private List<String> resolveSourceCandidates(String npcName, String itemName, DropContext context)
	{
		LinkedHashSet<String> baseCandidates = new LinkedHashSet<>();
		if (!isBlank(npcName))
		{
			baseCandidates.add(npcName);
		}

		for (String alias : dropMetadata.getSourceAliases(npcName))
		{
			if (!isBlank(alias))
			{
				baseCandidates.add(alias);
			}
		}

		Map<String, String> ratesForItem = getRdtRatesForItem(itemName);
		if (!isBlank(npcName) && ratesForItem != null)
		{
			String standardKey = npcName + " Standard";
			if (ratesForItem.containsKey(standardKey))
			{
				baseCandidates.add(standardKey);
			}

			if (dropMetadata.getSourceAliases(npcName).isEmpty())
			{
				String uniqueVariant = findUniqueRdtVariant(npcName, ratesForItem);
				if (!isBlank(uniqueVariant))
				{
					baseCandidates.add(uniqueVariant);
				}
			}
		}

		LinkedHashSet<String> resolvedCandidates = new LinkedHashSet<>();
		for (String candidate : baseCandidates)
		{
			if (isBlank(candidate))
			{
				continue;
			}

			String resolved = resolveLookupNpc(candidate, context);
			if (!isBlank(resolved))
			{
				resolvedCandidates.add(resolved);
			}
			resolvedCandidates.add(candidate);
		}

		if (resolvedCandidates.isEmpty() && !isBlank(npcName))
		{
			resolvedCandidates.add(npcName);
		}

		return new ArrayList<>(resolvedCandidates);
	}

	private String findUniqueRdtVariant(String npcName, Map<String, String> ratesForItem)
	{
		if (isBlank(npcName) || ratesForItem == null || ratesForItem.isEmpty())
		{
			return null;
		}

		String unique = null;
		for (String key : ratesForItem.keySet())
		{
			if (key == null || key.equals(npcName) || key.equals(npcName + " Standard"))
			{
				continue;
			}

			if (key.length() <= npcName.length() || !key.startsWith(npcName))
			{
				continue;
			}

			char separator = key.charAt(npcName.length());
			if (separator != ' ' && separator != '(')
			{
				continue;
			}

			if (unique != null && !unique.equals(key))
			{
				return null;
			}

			unique = key;
		}

		return unique;
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

	private String resolveLookupNpc(String npcName, DropContext context)
	{
		String resolvedNpc = npcName;
		for (int depth = 0; depth < 4; depth++)
		{
			String nextNpc = resolveLookupNpcOnce(resolvedNpc, context);
			if (isBlank(nextNpc) || nextNpc.equals(resolvedNpc))
			{
				break;
			}

			resolvedNpc = nextNpc;
		}

		return resolvedNpc;
	}

	private String resolveLookupNpcOnce(String npcName, DropContext context)
	{
		for (NpcContextRule rule : dropMetadata.getContextRules(npcName))
		{
			if (matchesContext(rule, context) && !isBlank(rule.lookupNpc))
			{
				return rule.lookupNpc;
			}
		}

		return npcName;
	}

	private boolean matchesContext(NpcContextRule rule, DropContext context)
	{
		if (rule == null || isBlank(rule.condition))
		{
			return false;
		}

		if ("slayer_task".equalsIgnoreCase(rule.condition))
		{
			String currentSlayerTask = context != null ? context.currentSlayerTask : null;
			return !isBlank(rule.task) && !isBlank(currentSlayerTask) && rule.task.equalsIgnoreCase(currentSlayerTask);
		}

		if ("ring_of_wealth".equalsIgnoreCase(rule.condition) || "ring_of_wealth_equipped".equalsIgnoreCase(rule.condition))
		{
			if (context == null)
			{
				return false;
			}

			boolean expected = rule.equipped == null || rule.equipped;
			return context.ringOfWealthEquipped == expected;
		}

		if ("legends_quest".equalsIgnoreCase(rule.condition) || "legends_quest_completed".equalsIgnoreCase(rule.condition))
		{
			if (context == null)
			{
				return false;
			}

			boolean expected = rule.completed == null || rule.completed;
			return context.legendsQuestCompleted == expected;
		}

		return false;
	}

	private boolean isLegendsQuestCompleted()
	{
		try
		{
			if (client == null || client.getLocalPlayer() == null)
			{
				return false;
			}

			return Quest.LEGENDS_QUEST.getState(client) == QuestState.FINISHED;
		}
		catch (Exception e)
		{
			log.debug("Unable to determine Legends' Quest state", e);
			return false;
		}
	}

	private boolean isRingOfWealthEquipped()
	{
		try
		{
			if (client == null || client.getLocalPlayer() == null)
			{
				return false;
			}

			ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
			if (equipment == null)
			{
				return false;
			}

			Item[] items = equipment.getItems();
			int ringSlot = EquipmentInventorySlot.RING.getSlotIdx();
			if (ringSlot < 0 || ringSlot >= items.length)
			{
				return false;
			}

			Item ringItem = items[ringSlot];
			if (ringItem == null || ringItem.getId() <= 0)
			{
				return false;
			}

			String ringName = cleanName(client.getItemDefinition(ringItem.getId()).getName());
			return ringName != null && ringName.toLowerCase(Locale.US).contains(RING_OF_WEALTH_NAME);
		}
		catch (Exception e)
		{
			log.debug("Unable to determine Ring of Wealth state", e);
			return false;
		}
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
		if (npcName == null || itemName == null)
		{
			return null;
		}

		Map<String, String> ratesForItem = getRdtRatesForItem(itemName);
		if (ratesForItem == null)
		{
			return null;
		}

		return ratesForItem.get(npcName);
	}

	private Map<String, String> getRdtRatesForItem(String itemName)
	{
		if (rdtDrops == null || rdtDrops.isEmpty() || itemName == null)
		{
			return null;
		}

		return rdtDrops.get(itemName);
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
		return Long.toString(Math.round(value));
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
			// Malformed data (e.g. an unevaluated wiki template) must never
			// half-match and produce a wrong rate — reject it outright.
			if (rarity.indexOf('{') >= 0)
			{
				return null;
			}

			Matcher matcher = CHANCE_PATTERN.matcher(rarity);
			if (!matcher.find())
			{
				return null;
			}

			double multiplier = matcher.group(1) != null ? parseRateNumber(matcher.group(1)) : 1.0d;
			double baseNumerator = parseRateNumber(matcher.group(2));
			double denominator = parseRateNumber(matcher.group(3));
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
			if (rarity.indexOf('{') >= 0)
			{
				return 0;
			}

			Matcher matcher = CHANCE_PATTERN.matcher(rarity);
			if (!matcher.find())
			{
				return 0;
			}

			double denominator = parseRateNumber(matcher.group(3));
			return denominator > 0 ? denominator : 0;
		}
		catch (Exception e)
		{
			return 0;
		}
	}

	private double parseRateNumber(String rawNumber)
	{
		if (rawNumber == null)
		{
			throw new NumberFormatException("null");
		}

		String value = rawNumber.trim();
		if (value.isEmpty())
		{
			throw new NumberFormatException("empty");
		}

		int commaIdx = value.lastIndexOf(',');
		int dotIdx = value.lastIndexOf('.');

		if (commaIdx >= 0 && dotIdx >= 0)
		{
			if (commaIdx > dotIdx)
			{
				value = value.replace(".", "").replace(',', '.');
			}
			else
			{
				value = value.replace(",", "");
			}
		}
		else if (commaIdx >= 0)
		{
			if (value.matches("\\d{1,3}(,\\d{3})+"))
			{
				value = value.replace(",", "");
			}
			else if (value.matches("\\d+,\\d{1,2}"))
			{
				value = value.replace(',', '.');
			}
			else
			{
				value = value.replace(",", "");
			}
		}

		return Double.parseDouble(value);
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

	/**
	 * The rare-drop notification used to be a plain boolean. It is now a RuneLite
	 * {@link Notification}, so users get the standard tray/sound/flash controls.
	 * Carry the old on/off state over once, so nobody silently loses their setting.
	 */
	private void migrateNotificationConfig()
	{
		if ("1".equals(configManager.getConfiguration(CONFIG_GROUP, NOTIFICATION_MIGRATED_KEY)))
		{
			return;
		}

		String legacyNotify = configManager.getConfiguration(CONFIG_GROUP, LEGACY_NOTIFY_KEY);
		if (legacyNotify != null)
		{
			boolean wasEnabled = Boolean.parseBoolean(legacyNotify);
			configManager.setConfiguration(
				CONFIG_GROUP,
				NOTIFICATION_KEY,
				wasEnabled ? Notification.ON : Notification.OFF
			);
			configManager.unsetConfiguration(CONFIG_GROUP, LEGACY_NOTIFY_KEY);
			log.info("Migrated rare drop notification setting (was enabled: {})", wasEnabled);
		}

		configManager.setConfiguration(CONFIG_GROUP, NOTIFICATION_MIGRATED_KEY, "1");
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

		if (fillerItems == null)
		{
			fillerItems = parseIgnoredItems(config.extraClutterItems());
		}

		return fillerItems.contains(normalized);
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
		private Map<String, List<String>> sourceAliases;
		private Map<String, List<NpcContextRule>> sourceContexts;
		private Map<String, Map<String, DropOverride>> dropOverrides;
		private Map<String, String> killCountAliases;

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

		/**
		 * The "killcount" config key for a source, when lowercasing our own source
		 * name does not already produce it (e.g. "Reward pool (Tempoross)").
		 */
		private String getKillCountAlias(String npcName)
		{
			if (killCountAliases == null || npcName == null)
			{
				return null;
			}

			return killCountAliases.get(npcName);
		}

		private List<String> getSourceAliases(String npcName)
		{
			if (sourceAliases == null || npcName == null)
			{
				return Collections.emptyList();
			}

			List<String> aliases = sourceAliases.get(npcName);
			return aliases != null ? aliases : Collections.emptyList();
		}

		private List<NpcContextRule> getContextRules(String sourceName)
		{
			if (sourceContexts != null && sourceName != null)
			{
				List<NpcContextRule> rules = sourceContexts.get(sourceName);
				if (rules != null)
				{
					return rules;
				}
			}

			return getNpcContexts(sourceName);
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

		private int getSourceAliasCount()
		{
			if (sourceAliases == null)
			{
				return 0;
			}

			int count = 0;
			for (List<String> aliases : sourceAliases.values())
			{
				if (aliases != null)
				{
					count += aliases.size();
				}
			}
			return count;
		}

		private int getSourceContextCount()
		{
			if (sourceContexts == null)
			{
				return 0;
			}

			int count = 0;
			for (List<NpcContextRule> rules : sourceContexts.values())
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
		private Boolean equipped;
		private Boolean completed;
		private String lookupNpc;
	}

	private static final class DropContext
	{
		private final String currentSlayerTask;
		private final boolean ringOfWealthEquipped;
		private final boolean legendsQuestCompleted;

		private DropContext(String currentSlayerTask, boolean ringOfWealthEquipped, boolean legendsQuestCompleted)
		{
			this.currentSlayerTask = currentSlayerTask;
			this.ringOfWealthEquipped = ringOfWealthEquipped;
			this.legendsQuestCompleted = legendsQuestCompleted;
		}
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

	private static final class SourceRate
	{
		private final String source;
		private final String rate;

		private SourceRate(String source, String rate)
		{
			this.source = source;
			this.rate = rate;
		}
	}

	private static final class ClogRateGroup
	{
		private final String rate;
		private final Chance chance;
		private final double effectiveRate;
		private final List<String> sources;

		private ClogRateGroup(String rate, Chance chance, double effectiveRate, List<String> sources)
		{
			this.rate = rate;
			this.chance = chance;
			this.effectiveRate = effectiveRate;
			this.sources = sources;
		}
	}
}
