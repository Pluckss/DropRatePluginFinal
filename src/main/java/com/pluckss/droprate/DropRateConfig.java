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
		description = "Core drop message settings",
		position = 0
	)
	String dropFeedSection = "dropFeedSection";

	@ConfigSection(
		name = "Filtering",
		description = "Hide filler drops and common spam",
		position = 1,
		closedByDefault = true
	)
	String cleanerFeedSection = "cleanerFeedSection";

	@ConfigSection(
		name = "Appearance",
		description = "How drop messages look in chat",
		position = 2,
		closedByDefault = true
	)
	String colorsSection = "colorsSection";

	@ConfigSection(
		name = "Advanced",
		description = "Extra details for edge cases and ambiguous drops",
		position = 3,
		closedByDefault = true
	)
	String advancedSection = "advancedSection";

	@ConfigItem(
		keyName = "displayMode",
		name = "Drop visibility",
		description = "Choose how much drop information the plugin sends to chat",
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
		description = "Rare drops only mode. Example: 700 shows 1/700 and rarer",
		position = 3,
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
		description = "Show raw wiki rates, effective 1/x rates, or both",
		position = 1,
		section = dropFeedSection
	)
	default DropRateFormatMode rateFormatMode()
	{
		return DropRateFormatMode.RAW_RATE;
	}

	@ConfigItem(
		keyName = "showRatePercentage",
		name = "Show percentage",
		description = "Adds the computed drop chance at the end, for example 1/100 becomes 1%",
		position = 2,
		section = dropFeedSection
	)
	default boolean showRatePercentage()
	{
		return false;
	}

	@ConfigItem(
		keyName = "colorMode",
		name = "Chat colors",
		description = "Use rarity colors or keep all drop messages neutral white",
		position = 0,
		section = colorsSection
	)
	default DropRateColorMode colorMode()
	{
		return DropRateColorMode.TIERED;
	}

	@ConfigItem(
		keyName = "commonTierThreshold",
		name = "Normal tier max",
		description = "Tiered colors only. Rates up to this value stay in the normal color tier",
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
		name = "Rare tier min",
		description = "Tiered colors only. Rates at or above this value use the rare color tier",
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
		name = "Ultra-rare tier min",
		description = "Tiered colors only. Rates at or above this value use the ultra-rare purple tier",
		position = 3,
		section = colorsSection
	)
	@Range(
		min = 1,
		max = 100000
	)
	default int ultraRareColorThreshold()
	{
		return 6000;
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
		description = "Include common multi-roll or bundled drops such as 2/115 and 6/378",
		position = 0,
		section = cleanerFeedSection
	)
	default boolean showMultiRollDrops()
	{
		return false;
	}

	@ConfigItem(
		keyName = "uselessItems",
		name = "Hidden filler items",
		description = "Cleaner feed only. Comma-separated items to hide, for example Bones, Ashes, Zulrah's scales",
		position = 1,
		section = cleanerFeedSection
	)
	default String extraClutterItems()
	{
		return "Bones, Ashes, Zulrah's scales";
	}

	@ConfigItem(
		keyName = "showAlternateTables",
		name = "Show possible sources",
		description = "Adds notes for drops with multiple possible tables or context-specific rates. This does not detect the exact hidden roll",
		position = 0,
		section = advancedSection
	)
	default boolean showAlternateTables()
	{
		return false;
	}
}
