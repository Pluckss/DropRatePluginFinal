package com.example;

import static org.junit.Assert.assertNotNull;

import java.io.InputStream;
import org.junit.Test;

public class DropRatePluginResourceTest
{
	@Test
	public void dropRateDatabaseIsPackagedOnClasspath()
	{
		try (InputStream in = DropRatePlugin.class.getResourceAsStream("/droprates_clean.json"))
		{
			assertNotNull("droprates_clean.json should be available at runtime", in);
		}
		catch (Exception e)
		{
			throw new AssertionError("Failed to read drop rate database resource", e);
		}
	}
}
