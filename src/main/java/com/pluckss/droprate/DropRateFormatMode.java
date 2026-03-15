package com.pluckss.droprate;

public enum DropRateFormatMode
{
	RAW_RATE("Raw wiki rate"),
	EFFECTIVE_RATE("Effective 1/x"),
	BOTH("Raw + effective");

	private final String label;

	DropRateFormatMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
