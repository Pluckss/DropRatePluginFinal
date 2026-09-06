import com.google.gson.Gson;
import com.pluckss.droprate.DropRateConfig;
import com.pluckss.droprate.DropRatePlugin;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * Drives the compiled plugin's private loaders and resolveVersionedDrop with the
 * shipped JSON and a default-valued config, and checks the outputs.
 *
 * Run from the repo root after ./gradlew build (Git Bash syntax):
 *   ./gradlew -q -I tools/verify/printcp.gradle printCp > build/cp.txt
 *   CP="build/classes/java/main;build/resources/main;$(cat build/cp.txt)"
 *   javac -encoding UTF-8 -cp "$CP" -d build/verify tools/verify/VersionLookupCheck.java
 *   java -Dfile.encoding=UTF-8 -cp "build/verify;$CP" VersionLookupCheck
 */
public class VersionLookupCheck
{
	static int failures = 0;

	public static void main(String[] args) throws Throwable
	{
		Constructor<DropRatePlugin> ctor = DropRatePlugin.class.getDeclaredConstructor();
		ctor.setAccessible(true);
		DropRatePlugin plugin = ctor.newInstance();

		set(plugin, "gson", new Gson());
		set(plugin, "config", defaultConfig());

		Map<String, Map<String, java.util.List<String>>> variants = new HashMap<>();
		set(plugin, "primaryVariants", variants);
		Method loadPrimary = method("loadPrimaryDrops", String.class, boolean.class, Map.class);
		set(plugin, "primaryDrops", loadPrimary.invoke(plugin, "/droprates_clean.json", true, variants));
		Method loadRdt = method("loadOptionalDrops", String.class);
		set(plugin, "rdtDrops", loadRdt.invoke(plugin, "/rare_drop_table.json"));
		method("loadNpcVersions", String.class).invoke(plugin, "/npc_versions.json");

		System.out.println("versioned monsters: " + ((Map<?, ?>) get(plugin, "npcVersions")).size()
			+ ", ids: " + ((Map<?, ?>) get(plugin, "npcVersionsById")).size()
			+ ", primary variants: " + variants.size());

		Method resolve = method("resolveVersionedDrop", String.class, int.class, String.class);

		expect(resolve, plugin, "Abyssal demon", 11239, "Adamantite bar", "2/68");
		expect(resolve, plugin, "Abyssal demon", 415, "Adamantite bar", "2/128");
		expect(resolve, plugin, "Abyssal demon", 416, "Adamantite bar", "2/128");
		expect(resolve, plugin, "Abyssal demon", 7241, "Abyssal whip", null); // Catacombs table lists only its extras; normal path supplies the shared rate
		expect(resolve, plugin, "Abyssal demon", 7241, "Dark totem base", "1/350");
		expect(resolve, plugin, "Abyssal demon", -1, "Adamantite bar", "Standard 2/128 | Wilderness Slayer Cave 2/68");
		expect(resolve, plugin, "Cyclops", 2137, "Defensive casket", null); // basement id, item not on that table -> normal path
		expect(resolve, plugin, "Vorkath", -1, "Dragonstone bolt tips", "2 × 5/150 | 2 × 14/2730");
		expect(resolve, plugin, "Vorkath", 8061, "Dragonstone bolt tips", "2 × 5/150 | 2 × 14/2730");
		expect(resolve, plugin, "Kraken", 494, "Trident of the Seas (full)", null);
		expect(resolve, plugin, "Barbarian", 3056, "Bronze arrow", "3/128");
		expect(resolve, plugin, "Barbarian", 3055, "Bronze arrow", "4/128");
		expect(resolve, plugin, "Green dragon", 7868, "Dragon bones", null);
		expect(resolve, plugin, "Green dragon", 261, "Nature rune", "5/128");
		expect(resolve, plugin, "Green dragon", 7868, "Nature rune", "1/128");
		expect(resolve, plugin, "Green dragon", -1, "Nature rune", "Regular 5/128 | Wilderness Slayer Cave 1/128");
		expect(resolve, plugin, "Goblin", 3028, "Bronze arrow", "3/128"); // id undecided, but only one table lists the item
		expect(resolve, plugin, "Does not exist", 1, "Coins", null);

		System.out.println(failures == 0 ? "ALL OK" : failures + " FAILURES");
		System.exit(failures == 0 ? 0 : 1);
	}

	static void expect(Method resolve, Object plugin, String npc, int id, String item, String want) throws Throwable
	{
		Object resolved = resolve.invoke(plugin, npc, id, item);
		String got = resolved == null ? null : (String) get(resolved, "formattedRate");
		boolean ok = want == null ? got == null : want.equals(got);
		if (!ok)
		{
			failures++;
		}
		System.out.println((ok ? "ok   " : "FAIL ") + npc + " #" + id + " / " + item + " -> " + got + (ok ? "" : "  (wanted " + want + ")"));
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

	static Object get(Object target, String field) throws Exception
	{
		Field f = target.getClass().getDeclaredField(field);
		f.setAccessible(true);
		return f.get(target);
	}

	/** A DropRateConfig whose every method returns its declared default. */
	static DropRateConfig defaultConfig()
	{
		return (DropRateConfig) Proxy.newProxyInstance(
			DropRateConfig.class.getClassLoader(),
			new Class<?>[]{DropRateConfig.class},
			(proxy, m, a) ->
			{
				if ("rateFormatMode".equals(m.getName()))
				{
					return com.pluckss.droprate.DropRateFormatMode.RAW_RATE;
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
