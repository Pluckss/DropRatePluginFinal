package com.pluckss.droprate;

public enum DropRateColorMode
{
	TIERED("Tiered colors"),
	NEUTRAL_WHITE("Neutral white");

	private final String label;

	DropRateColorMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
