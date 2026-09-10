package com.pluckss.droprate;

/**
 * The rarity tiers the plugin already colours drops by, ordered from most common to
 * rarest. Declaration order is the tier order, so "this tier and everything rarer"
 * is a plain {@link Enum#compareTo} against the minimum the player picked.
 *
 * <p>The boundaries between the tiers are the player's own Appearance settings, and
 * {@code DropRatePlugin.classifyTier} is the only place that applies them.
 */
public enum DropRateTier
{
	COMMON("Common"),
	UNCOMMON("Uncommon"),
	RARE("Rare"),
	ULTRA_RARE("Ultra-rare");

	private final String label;

	DropRateTier(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
