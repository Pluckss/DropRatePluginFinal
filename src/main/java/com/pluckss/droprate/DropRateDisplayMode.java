package com.pluckss.droprate;

public enum DropRateDisplayMode
{
	ALL_MATCHES("All drops"),
	CLEANER_FEED("Notable drops only");

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
