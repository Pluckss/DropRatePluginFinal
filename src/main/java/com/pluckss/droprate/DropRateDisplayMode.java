package com.pluckss.droprate;

public enum DropRateDisplayMode
{
	ALL_MATCHES("Show all matched drops"),
	CLEANER_FEED("Cleaner feed"),
	RARE_DROPS_ONLY("Rare drops only");

	private final String label;

	DropRateDisplayMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
