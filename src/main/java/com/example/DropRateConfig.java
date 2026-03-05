package com.example;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("droprate")
public interface DropRateConfig extends Config
{
	@ConfigItem(
		keyName = "allowSpam",
		name = "Allow spam",
		description = "Show common multi-roll drops like 2/x, 5/x, 16/x"
	)
	default boolean allowSpam()
	{
		return false;
	}

	@ConfigItem(
		keyName = "onlyHighDrops",
		name = "Only rare drops",
		description = "Only show drops at or above the rare drop threshold (e.g. 1/700)"
	)
	default boolean onlyHighDrops()
	{
		return false;
	}

	@ConfigItem(
		keyName = "highDropThreshold",
		name = "Rare drop threshold",
		description = "Minimum denominator to show when Only rare drops is enabled (700 = 1/700)"
	)
	default int highDropThreshold()
	{
		return 500;
	}

	@ConfigItem(
		keyName = "hideAlwaysDrops",
		name = "Hide always drops",
		description = "Hide guaranteed drops like 1/1"
	)
	default boolean hideAlwaysDrops()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideUselessDrops",
		name = "Hide useless drops",
		description = "Hide common filler drops (bones/ashes/custom list)"
	)
	default boolean hideUselessDrops()
	{
		return false;
	}

	@ConfigItem(
		keyName = "uselessItems",
		name = "Useless items",
		description = "Comma-separated items to hide, e.g. Bones, Ashes, Zulrah's scales"
	)
	default String uselessItems()
	{
		return "Bones, Ashes, Zulrah's scales";
	}

}
