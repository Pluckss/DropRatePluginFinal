package com.pluckss.droprate;

public enum DropRateFormatMode
{
	RAW_RATE("Raw wiki rate"),
	EFFECTIVE_RATE("Standard (1/x)"),
	BOTH("Raw + standard");

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
