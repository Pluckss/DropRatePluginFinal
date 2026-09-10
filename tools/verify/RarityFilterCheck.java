import com.pluckss.droprate.DropRateColorMode;
import com.pluckss.droprate.DropRateConfig;
import com.pluckss.droprate.DropRatePlugin;
import com.pluckss.droprate.DropRateTier;
import java.awt.Color;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * Drives the compiled plugin's tier classification and its "Minimum rarity to show in
 * chat" filter against a config whose values we control, and checks:
 *   - the tier boundaries, including the exact edges;
 *   - every minimum-rarity setting against every tier;
 *   - that the filter follows the player's own tier boundaries when they move them;
 *   - that the colours and the filter never disagree about a drop's tier.
 *
 * Run from the repo root after ./gradlew build (Git Bash syntax):
 *   ./gradlew -q -I tools/verify/printcp.gradle printCp > build/cp.txt
 *   CP="build/classes/java/main;build/resources/main;$(cat build/cp.txt)"
 *   javac -encoding UTF-8 -cp "$CP" -d build/verify tools/verify/RarityFilterCheck.java
 *   java -Dfile.encoding=UTF-8 -cp "build/verify;$CP" RarityFilterCheck
 */
public class RarityFilterCheck
{
	static int failures = 0;

	/** Overrides layered onto DropRateConfig's own defaults, keyed by method name. */
	static final Map<String, Object> overrides = new HashMap<>();

	static DropRatePlugin plugin;
	static DropRateConfig config;
	static Method classifyTier;
	static Method passesMinimumRarity;
	static Method getColor;

	public static void main(String[] args) throws Throwable
	{
		Constructor<DropRatePlugin> ctor = DropRatePlugin.class.getDeclaredConstructor();
		ctor.setAccessible(true);
		plugin = ctor.newInstance();
		config = overridableConfig();
		set(plugin, "config", config);

		classifyTier = method("classifyTier", DropRateConfig.class, double.class);
		passesMinimumRarity = method("passesMinimumRarity", double.class);
		getColor = method("getColor", double.class);

		checkDefaultBoundaries();
		checkEveryMinimumAgainstEveryTier();
		checkCustomBoundariesAreFollowed();
		checkColoursAgreeWithTiers();
		checkColourStyleDoesNotChangeTheFilter();
		checkRetiredThresholdMapsToATier();

		System.out.println(failures == 0 ? "ALL OK" : failures + " FAILURES");
		System.exit(failures == 0 ? 0 : 1);
	}

	/**
	 * The shipped tiers: common up to 300, rare from 1000, ultra-rare from 5000, with
	 * uncommon filling the gap. The edges are what a duplicated copy of the thresholds
	 * would get subtly wrong, so each one is pinned from both sides.
	 */
	static void checkDefaultBoundaries() throws Throwable
	{
		System.out.println("-- default tier boundaries (300 / 1000 / 5000)");
		tier(0, DropRateTier.COMMON);          // unparseable rate; same tier its colour has always used
		tier(1, DropRateTier.COMMON);          // a guaranteed 1/1 drop
		tier(128, DropRateTier.COMMON);
		tier(300, DropRateTier.COMMON);        // "Common tier max" is inclusive
		tier(300.5, DropRateTier.UNCOMMON);
		tier(999, DropRateTier.UNCOMMON);
		tier(999.9, DropRateTier.UNCOMMON);
		tier(1000, DropRateTier.RARE);         // "Rare tier minimum" is inclusive
		tier(4999.9, DropRateTier.RARE);
		tier(5000, DropRateTier.ULTRA_RARE);   // "Ultra-rare tier minimum" is inclusive
		tier(1502.4, DropRateTier.RARE);
		tier(1000000, DropRateTier.ULTRA_RARE);
	}

	/** The whole user-facing promise: a tier shows itself and everything rarer. */
	static void checkEveryMinimumAgainstEveryTier() throws Throwable
	{
		System.out.println("-- minimum rarity vs tier");
		// One representative rate per tier, under the default boundaries.
		double[] rates = {128, 512, 2000, 20000};

		shows(DropRateTier.COMMON, rates, true, true, true, true);
		shows(DropRateTier.UNCOMMON, rates, false, true, true, true);
		shows(DropRateTier.RARE, rates, false, false, true, true);
		shows(DropRateTier.ULTRA_RARE, rates, false, false, false, true);
	}

	/**
	 * There must be one source of truth: moving a tier boundary in the Appearance
	 * settings has to move the filter with it. 512 is uncommon by default; pulling
	 * "Rare tier minimum" down to 500 must make the same drop survive a Rare minimum.
	 */
	static void checkCustomBoundariesAreFollowed() throws Throwable
	{
		System.out.println("-- custom tier boundaries");
		overrides.put("minimumChatRarity", DropRateTier.RARE);
		expect("512 with default tiers, minimum Rare", passes(512), false);

		overrides.put("rareColorThreshold", 500);
		expect("512 after rare tier minimum -> 500", passes(512), true);
		expect("512 is now rare", tierOf(512), DropRateTier.RARE);

		// Boundaries are clamped into order, so a nonsensical set cannot invert the
		// tiers: common up to 2000 swallows the 1000 rare minimum, and rare then
		// starts at 2001.
		overrides.put("commonTierThreshold", 2000);
		overrides.put("rareColorThreshold", 1000);
		expect("1500 with common max 2000", tierOf(1500), DropRateTier.COMMON);
		expect("2001 with common max 2000", tierOf(2001), DropRateTier.RARE);

		overrides.clear();
	}

	/** The colour a drop is printed in and the tier the filter judges it by must match. */
	static void checkColoursAgreeWithTiers() throws Throwable
	{
		System.out.println("-- colours agree with tiers");
		Map<DropRateTier, Color> expected = new HashMap<>();
		expected.put(DropRateTier.COMMON, new Color(46, 125, 50));
		expected.put(DropRateTier.UNCOMMON, new Color(255, 140, 0));
		expected.put(DropRateTier.RARE, new Color(178, 34, 34));
		expected.put(DropRateTier.ULTRA_RARE, new Color(156, 39, 176));

		for (double rate : new double[]{0, 1, 300, 301, 999, 1000, 4999, 5000, 50000})
		{
			DropRateTier tier = tierOf(rate);
			expect("colour of 1/" + rate + " is the " + tier + " colour", getColor.invoke(plugin, rate), expected.get(tier));
		}
	}

	/**
	 * Neutral white paints every drop the same, but it must not flatten the tiers the
	 * filter reads — someone on neutral white still gets to hide common drops.
	 */
	static void checkColourStyleDoesNotChangeTheFilter() throws Throwable
	{
		System.out.println("-- neutral white does not change the filter");
		overrides.put("colorMode", DropRateColorMode.NEUTRAL_WHITE);
		overrides.put("minimumChatRarity", DropRateTier.UNCOMMON);
		expect("neutral white, 128 hidden by a minimum of Uncommon", passes(128), false);
		expect("neutral white, 512 still shown", passes(512), true);
		expect("neutral white paints 512 white", getColor.invoke(plugin, 512.0), new Color(255, 255, 255));
		overrides.clear();
	}

	/**
	 * What a "Rare drops only" user is carried over to. The migration is
	 * classifyTier(old threshold), so this pins the mapping itself; the ConfigManager
	 * plumbing around it is not exercised here.
	 *
	 * The result is always at or below their old threshold, so the new setting shows
	 * them at least what the old one did — never less.
	 */
	static void checkRetiredThresholdMapsToATier() throws Throwable
	{
		System.out.println("-- retired rare-only threshold -> tier");
		expect("threshold 500 (the old default)", tierOf(500), DropRateTier.UNCOMMON);
		expect("threshold 50", tierOf(50), DropRateTier.COMMON);
		expect("threshold 300", tierOf(300), DropRateTier.COMMON);
		expect("threshold 1000", tierOf(1000), DropRateTier.RARE);
		expect("threshold 2500", tierOf(2500), DropRateTier.RARE);
		expect("threshold 10000", tierOf(10000), DropRateTier.ULTRA_RARE);
	}

	static void shows(DropRateTier minimum, double[] rates, boolean... wanted) throws Throwable
	{
		overrides.put("minimumChatRarity", minimum);
		for (int i = 0; i < rates.length; i++)
		{
			expect("minimum " + minimum + ": 1/" + rates[i] + " (" + tierOf(rates[i]) + ")", passes(rates[i]), wanted[i]);
		}
		overrides.clear();
	}

	static DropRateTier tierOf(double rate) throws Throwable
	{
		return (DropRateTier) classifyTier.invoke(null, config, rate);
	}

	static boolean passes(double rate) throws Throwable
	{
		return (Boolean) passesMinimumRarity.invoke(plugin, rate);
	}

	static void tier(double rate, DropRateTier want) throws Throwable
	{
		expect("1/" + rate, tierOf(rate), want);
	}

	static void expect(String what, Object got, Object want)
	{
		boolean ok = want == null ? got == null : want.equals(got);
		if (!ok)
		{
			failures++;
		}
		System.out.println((ok ? "ok   " : "FAIL ") + what + " -> " + got + (ok ? "" : "  (wanted " + want + ")"));
	}

	static Method method(String name, Class<?>... types) throws Exception
	{
		Method m = DropRatePlugin.class.getDeclaredMethod(name, types);
		m.setAccessible(true);
		return m;
	}

	static void set(Object target, String field, Object value) throws Exception
	{
		Field f = target.getClass().getDeclaredField(field);
		f.setAccessible(true);
		f.set(target, value);
	}

	/** A DropRateConfig returning its declared defaults, except where overridden. */
	static DropRateConfig overridableConfig()
	{
		return (DropRateConfig) Proxy.newProxyInstance(
			DropRateConfig.class.getClassLoader(),
			new Class<?>[]{DropRateConfig.class},
			(proxy, m, a) ->
			{
				Object override = overrides.get(m.getName());
				if (override != null)
				{
					return override;
				}
				if (m.isDefault())
				{
					return MethodHandles.privateLookupIn(DropRateConfig.class, MethodHandles.lookup())
						.unreflectSpecial(m, DropRateConfig.class)
						.bindTo(proxy)
						.invokeWithArguments(a == null ? new Object[0] : a);
				}
				return null;
			});
	}
}
