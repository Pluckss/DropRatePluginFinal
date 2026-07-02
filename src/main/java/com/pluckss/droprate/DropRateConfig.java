package com.pluckss.droprate;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("droprate")
public interface DropRateConfig extends Config
{
	@ConfigSection(
		name = "Display",
		description = "Control when and how drop rates are shown in chat",
		position = 0
	)
	String dropFeedSection = "dropFeedSection";

	@ConfigSection(
		name = "Filtering",
		description = "Reduce chat clutter from frequent low-value drops",
		position = 1,
		closedByDefault = true
	)
	String cleanerFeedSection = "cleanerFeedSection";

	@ConfigSection(
		name = "Notifications",
		description = "Get a desktop notification when you receive a rare drop",
		position = 2,
		closedByDefault = true
	)
	String notificationsSection = "notificationsSection";

	@ConfigSection(
		name = "Appearance",
		description = "Set drop message colors and rarity tiers",
		position = 3,
		closedByDefault = true
	)
	String colorsSection = "colorsSection";

	@ConfigSection(
		name = "Collection log",
		description = "Show drop-rate sources when hovering items in the Collection Log",
		position = 4,
		closedByDefault = true
	)
	String clogSection = "clogSection";

	@ConfigItem(
		keyName = "displayMode",
		name = "Drop visibility",
		description = "<html>All drops: show a rate for every item received.<br>"
			+ "Notable drops only: hide guaranteed drops (bones, always-drops) and any items in the filler list below.<br>"
			+ "Rare drops only: only show messages for drops rarer than the threshold below.</html>",
		position = 0,
		section = dropFeedSection
	)
	default DropRateDisplayMode displayMode()
	{
		return DropRateDisplayMode.ALL_MATCHES;
	}

	@ConfigItem(
		keyName = "highDropThreshold",
		name = "Rare-only minimum rate",
		description = "Used only in Rare drops only mode. Example: 700 shows 1/700 and rarer drops",
		position = 4,
		section = dropFeedSection
	)
	@Range(
		min = 1,
		max = 100000
	)
	default int rareDropThreshold()
	{
		return 500;
	}

	@ConfigItem(
		keyName = "rateFormatMode",
		name = "Rate display",
		description = "Show wiki rates, simplified standard 1/x rates, or both",
		position = 1,
		section = dropFeedSection
	)
	default DropRateFormatMode rateFormatMode()
	{
		return DropRateFormatMode.EFFECTIVE_RATE;
	}

	@ConfigItem(
		keyName = "showRatePercentage",
		name = "Show percentage",
		description = "Adds the computed percentage chance, for example 1/100 becomes 1%",
		position = 2,
		section = dropFeedSection
	)
	default boolean showRatePercentage()
	{
		return false;
	}

	@ConfigItem(
		keyName = "colorMode",
		name = "Color style",
		description = "Use rarity-based colors or show all drop messages in neutral white",
		position = 0,
		section = colorsSection
	)
	default DropRateColorMode colorMode()
	{
		return DropRateColorMode.TIERED;
	}

	@ConfigItem(
		keyName = "commonTierThreshold",
		name = "Common tier max",
		description = "Tiered colors only. Rates up to this value use the common color",
		position = 1,
		section = colorsSection
	)
	@Range(
		min = 1,
		max = 100000
	)
	default int commonTierThreshold()
	{
		return 300;
	}

	@ConfigItem(
		keyName = "rareColorThreshold",
		name = "Rare tier minimum",
		description = "Tiered colors only. Rates at or above this value use the rare color",
		position = 2,
		section = colorsSection
	)
	@Range(
		min = 1,
		max = 100000
	)
	default int rareColorThreshold()
	{
		return 1000;
	}

	@ConfigItem(
		keyName = "ultraRareColorThreshold",
		name = "Ultra-rare tier minimum",
		description = "Tiered colors only. Rates at or above this value use the ultra-rare color",
		position = 3,
		section = colorsSection
	)
	@Range(
		min = 1,
		max = 100000
	)
	default int ultraRareColorThreshold()
	{
		return 5000;
	}

	@Alpha
	@ConfigItem(
		keyName = "commonTierColor",
		name = "Common color",
		description = "Color for common drops in tiered mode",
		position = 4,
		section = colorsSection
	)
	default Color commonTierColor()
	{
		return new Color(46, 125, 50);
	}

	@Alpha
	@ConfigItem(
		keyName = "uncommonTierColor",
		name = "Uncommon color",
		description = "Color for uncommon drops in tiered mode",
		position = 5,
		section = colorsSection
	)
	default Color uncommonTierColor()
	{
		return new Color(255, 140, 0);
	}

	@Alpha
	@ConfigItem(
		keyName = "rareTierColor",
		name = "Rare color",
		description = "Color for rare drops in tiered mode",
		position = 6,
		section = colorsSection
	)
	default Color rareTierColor()
	{
		return new Color(178, 34, 34);
	}

	@Alpha
	@ConfigItem(
		keyName = "ultraRareTierColor",
		name = "Ultra-rare color",
		description = "Color for ultra-rare drops in tiered mode",
		position = 7,
		section = colorsSection
	)
	default Color ultraRareTierColor()
	{
		return new Color(156, 39, 176);
	}

	@ConfigItem(
		keyName = "allowSpam",
		name = "Show bundle drops",
		description = "Include multi-roll drops such as 2/115, 6/378, or 2 x 1/637",
		position = 0,
		section = cleanerFeedSection
	)
	default boolean showMultiRollDrops()
	{
		return true;
	}

	@ConfigItem(
		keyName = "uselessItems",
		name = "Hidden filler items",
		description = "Notable drops only mode. Comma-separated item names to always hide, for example: Bones, Ashes, Zulrah's scales",
		position = 1,
		section = cleanerFeedSection
	)
	default String extraClutterItems()
	{
		return "Bones, Ashes, Zulrah's scales";
	}

	@ConfigItem(
		keyName = "minItemValue",
		name = "Min item value (gp)",
		description = "<html>Hide drop rate messages for items worth less than this on the GE.<br>"
			+ "Set to 0 to disable. Example: 10000 hides anything under 10k gp.</html>",
		position = 2,
		section = cleanerFeedSection
	)
	@Range(
		min = 0,
		max = 10000000
	)
	default int minItemValue()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "showAlternateTables",
		name = "Show source hints",
		description = "Show hints when a drop can come from more than one source, for example Normal and RDT",
		position = 3,
		section = dropFeedSection
	)
	default boolean showAlternateTables()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showKillCounter",
		name = "Show kill counter",
		description = "<html>Shows your KC and the average kills needed next to rare drops.<br>"
			+ "Example: 1x Abyssal whip (1/512 — KC: 203, avg: ~512 kills)<br>"
			+ "If your KC is lower than avg you got lucky, higher means unlucky.<br>"
			+ "Resets each session when the plugin starts.</html>",
		position = 5,
		section = dropFeedSection
	)
	default boolean showKillCounter()
	{
		return false;
	}

	@ConfigItem(
		keyName = "killCounterThreshold",
		name = "Kill counter min rarity",
		description = "Only show the kill counter for drops rarer than this. Example: 100 means only 1/100 or rarer drops show the kill count.",
		position = 6,
		section = dropFeedSection
	)
	@Range(
		min = 1,
		max = 100000
	)
	default int killCounterThreshold()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "notifyOnRareDrops",
		name = "Notify on rare drops",
		description = "Send a desktop notification when you receive a drop rarer than the threshold below. Useful when alt-tabbed.",
		position = 0,
		section = notificationsSection
	)
	default boolean notifyOnRareDrops()
	{
		return false;
	}

	@ConfigItem(
		keyName = "notificationThreshold",
		name = "Notification threshold",
		description = "Minimum rarity to trigger a notification. Example: 1000 means only 1/1000 or rarer drops notify you.",
		position = 1,
		section = notificationsSection
	)
	@Range(
		min = 1,
		max = 100000
	)
	default int notificationThreshold()
	{
		return 1000;
	}

	@ConfigItem(
		keyName = "showClogTooltip",
		name = "Collection log tooltips",
		description = "Show a tooltip with each item's drop-rate sources when you hover it in the Collection Log",
		position = 0,
		section = clogSection
	)
	default boolean showClogTooltip()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideClogTooltipIfObtained",
		name = "Hide for obtained items",
		description = "Don't show the tooltip for collection log items you have already unlocked",
		position = 1,
		section = clogSection
	)
	default boolean hideClogTooltipIfObtained()
	{
		return false;
	}
}
