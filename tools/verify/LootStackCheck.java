import com.pluckss.droprate.DropRatePlugin;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import net.runelite.client.game.ItemStack;

/** Regression checks using the compiled plugin and RuneLite's actual item stacks. */
public class LootStackCheck
{
	public static void main(String[] args) throws Exception
	{
		Constructor<DropRatePlugin> constructor = DropRatePlugin.class.getDeclaredConstructor();
		constructor.setAccessible(true);
		DropRatePlugin plugin = constructor.newInstance();
		Method signature = DropRatePlugin.class.getDeclaredMethod("buildLootSignature", String.class, Collection.class);
		signature.setAccessible(true);
		Collection<ItemStack> split = Arrays.asList(new ItemStack(385, 1), new ItemStack(5316, 2), new ItemStack(385, 1));
		Collection<ItemStack> merged = Arrays.asList(new ItemStack(385, 2), new ItemStack(5316, 2));
		Object first = signature.invoke(plugin, "Alchemical Hydra", split);
		if (!first.equals(signature.invoke(plugin, "Alchemical Hydra", merged)))
		{
			throw new AssertionError("The same Hydra loot must have one signature regardless of stack grouping/order");
		}
		if (first.equals(signature.invoke(plugin, "Alchemical Hydra", Arrays.asList(new ItemStack(385, 3), new ItemStack(5316, 2))))
			|| first.equals(signature.invoke(plugin, "Vorkath", merged)))
		{
			throw new AssertionError("Different quantities or sources must remain distinct");
		}
		Method aggregate = DropRatePlugin.class.getDeclaredMethod("aggregateLoot", Collection.class);
		aggregate.setAccessible(true);
		@SuppressWarnings("unchecked")
		Collection<ItemStack> totals = (Collection<ItemStack>) aggregate.invoke(plugin, split);
		if (totals.size() != 2 || totals.stream().filter(s -> s.getId() == 385).findFirst().get().getQuantity() != 2
			|| totals.stream().filter(s -> s.getId() == 5316).findFirst().get().getQuantity() != 2)
		{
			throw new AssertionError("Chat and value/quantity filters must receive the complete per-item stack");
		}
		if (split.size() != 3 || split.iterator().next().getQuantity() != 1)
		{
			throw new AssertionError("Aggregation must not mutate RuneLite's event collection");
		}
		Collection<ItemStack> empty = (Collection<ItemStack>) aggregate.invoke(plugin, Collections.emptyList());
		if (!empty.isEmpty()) throw new AssertionError("Empty loot must remain empty");
		System.out.println("ALL OK: Hydra split/merged events, quantities, sources, complete chat stacks and event ownership");
	}
}
