package io.github.hightrust.craftingmacro.crafting;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reads the (server-synchronized) client view of the player's inventory. */
public final class InventoryAnalyzer {
	private InventoryAnalyzer() {
	}

	/** Item id -> count over the 36 main inventory slots, plus optional extra slots (e.g. a crafting grid). */
	public static Map<String, Integer> counts(LocalPlayer player, List<Slot> extraSlots) {
		Map<String, Integer> counts = new HashMap<>();
		Inventory inv = player.getInventory();
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) add(counts, inv.getItem(i));
		if (extraSlots != null) {
			for (Slot s : extraSlots) add(counts, s.getItem());
		}
		return counts;
	}

	private static void add(Map<String, Integer> counts, ItemStack stack) {
		if (stack == null || stack.isEmpty()) return;
		counts.merge(RecipeResolver.itemId(stack.getItem()), stack.getCount(), Integer::sum);
	}

	public static int count(LocalPlayer player, String itemId) {
		return counts(player, null).getOrDefault(itemId, 0);
	}

	public static List<InventoryCapacity.SlotInfo> mainSlots(LocalPlayer player) {
		List<InventoryCapacity.SlotInfo> slots = new ArrayList<>();
		Inventory inv = player.getInventory();
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			ItemStack s = inv.getItem(i);
			slots.add(s.isEmpty()
					? new InventoryCapacity.SlotInfo(null, 0, 64)
					: new InventoryCapacity.SlotInfo(RecipeResolver.itemId(s.getItem()), s.getCount(), s.getMaxStackSize()));
		}
		return slots;
	}

	/** Same contents the server uses when placing recipes: main inventory plus the grid. */
	public static StackedItemContents stacked(LocalPlayer player, List<Slot> grid) {
		StackedItemContents contents = new StackedItemContents();
		player.getInventory().fillStackedContents(contents);
		if (grid != null) {
			for (Slot s : grid) contents.accountStack(s.getItem());
		}
		return contents;
	}

	/** Cheap fingerprint of every slot plus the carried stack, used to detect when the server has settled. */
	public static long fingerprint(AbstractContainerMenu menu) {
		long h = 1125899906842597L;
		for (Slot slot : menu.slots) {
			ItemStack s = slot.getItem();
			h = 31 * h + (s.isEmpty() ? 0 : System.identityHashCode(s.getItem()));
			h = 31 * h + s.getCount();
		}
		ItemStack carried = menu.getCarried();
		h = 31 * h + (carried.isEmpty() ? 0 : carried.getCount() + 7L * System.identityHashCode(carried.getItem()));
		return h;
	}
}
