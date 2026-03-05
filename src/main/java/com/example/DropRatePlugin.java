package com.example;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.awt.Color;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
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
import net.runelite.api.Player;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
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

	private Map<String, Map<String, String>> primaryDrops;
	private Map<String, Map<String, String>> invertedDrops;

	@Provides
	DropRateConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DropRateConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
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

		log.info("DropRate database loaded: {} primary entries", primaryDrops != null ? primaryDrops.size() : 0);
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		if (primaryDrops == null || primaryDrops.isEmpty())
		{
			return;
		}

		NPC npc = event.getNpc();
		if (npc == null)
		{
			return;
		}

		String npcName = cleanName(npc.getName());
		if (npcName == null)
		{
			return;
		}

		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}

		Collection<ItemStack> items = event.getItems();
		if (items == null || items.isEmpty())
		{
			return;
		}

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
				continue;
			}

			Chance chance = parseChance(rarity);
			if (config.hideAlwaysDrops() && chance != null && chance.numerator >= chance.denominator)
			{
				continue;
			}

			if (isUselessDrop(itemName))
			{
				continue;
			}

			int rate = parseRate(rarity);
			if (config.onlyHighDrops())
			{
				int threshold = Math.max(1, config.highDropThreshold());
				if (rate <= 0 || rate < threshold)
				{
					continue;
				}
			}

			if (!config.allowSpam() && chance != null && chance.numerator >= 2.0d)
			{
				continue;
			}

			Color color = getColor(rate);
			String message = colorTag(color) + stack.getQuantity() + "x " + itemName + " (" + rarity + ")</col>";

			chatMessageManager.queue(
				QueuedMessage.builder()
					.type(ChatMessageType.GAMEMESSAGE)
					.runeLiteFormattedMessage(message)
					.build()
			);
		}
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

	private int parseRate(String rarity)
	{
		try
		{
			Matcher matcher = CHANCE_PATTERN.matcher(rarity);
			if (!matcher.find())
			{
				return 0;
			}

			return (int) Math.round(Double.parseDouble(matcher.group(3)));
		}
		catch (Exception e)
		{
			return 0;
		}
	}

	private Color getColor(int rate)
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

	private boolean isUselessDrop(String itemName)
	{
		if (!config.hideUselessDrops())
		{
			return false;
		}

		String normalized = itemName.toLowerCase();
		if (normalized.equals("bones") || normalized.endsWith(" bones")
			|| normalized.equals("ashes") || normalized.endsWith(" ashes"))
		{
			return true;
		}

		Set<String> extraIgnored = parseIgnoredItems(config.uselessItems());
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
