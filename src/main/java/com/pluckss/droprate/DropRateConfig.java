package com.pluckss.droprate;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("droprate")
public interface DropRateConfig extends Config
{
	@ConfigSection(
		name = "Drop Feed",
		description = "Choose how much drop information the plugin should show",
		position = 0
	)
	String dropFeedSection = "dropFeedSection";

	@ConfigSection(
		name = "Cleaner Feed",
		description = "Extra clutter filters used by Cleaner feed mode",
		position = 1,
		closedByDefault = true
	)
	String cleanerFeedSection = "cleanerFeedSection";

	@ConfigItem(
		keyName = "displayMode",
		name = "What to show",
		description = "Pick whether to show everything, a cleaner feed, or only rare drops",
		position = 0,
		section = dropFeedSection
	)
	default DropRateDisplayMode displayMode()
	{
		return DropRateDisplayMode.ALL_MATCHES;
	}

	@ConfigItem(
		keyName = "allowSpam",
		name = "Show common bundle drops",
		description = "Show drops with rates like 2/115, 5/115, and other multi-roll common drops",
		position = 1,
		section = dropFeedSection
	)
	default boolean showMultiRollDrops()
	{
		return false;
	}

	@ConfigItem(
		keyName = "highDropThreshold",
		name = "Rare drop threshold",
		description = "Used only in Rare drops only mode. 700 means show 1/700 and rarer",
		position = 2,
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
		keyName = "uselessItems",
		name = "Extra clutter items",
		description = "Cleaner feed mode only. Comma-separated items to hide, e.g. Bones, Ashes, Zulrah's scales",
		position = 0,
		section = cleanerFeedSection
	)
	default String extraClutterItems()
	{
		return "Bones, Ashes, Zulrah's scales";
	}
}
