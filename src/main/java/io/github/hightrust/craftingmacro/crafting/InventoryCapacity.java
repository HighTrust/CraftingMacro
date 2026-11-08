package io.github.hightrust.craftingmacro.crafting;

import java.util.List;

/** Pure inventory space math used to stop crafting before results would be dropped. */
public final class InventoryCapacity {
	private InventoryCapacity() {
	}

	/** A simplified slot: item id (null/empty = empty slot), count and the item's max stack size. */
	public record SlotInfo(String itemId, int count, int maxStack) {
		public boolean isEmpty() {
			return itemId == null || itemId.isEmpty() || count <= 0;
		}
	}

	/** How many more items of {@code itemId} (max stack {@code maxStack}) fit into {@code slots}. */
	public static int spaceFor(List<SlotInfo> slots, String itemId, int maxStack) {
		long space = 0;
		for (SlotInfo s : slots) {
			if (s.isEmpty()) {
				space += maxStack;
			} else if (s.itemId().equals(itemId) && s.count() < maxStack) {
				space += maxStack - s.count();
			}
		}
		return (int) Math.min(Integer.MAX_VALUE, space);
	}

	/** How many crafts' worth of results fit, given results per craft. */
	public static int craftsThatFit(List<SlotInfo> slots, String resultId, int resultMaxStack, int resultPerCraft) {
		if (resultPerCraft <= 0) return 0;
		return spaceFor(slots, resultId, resultMaxStack) / resultPerCraft;
	}

	public static int emptySlots(List<SlotInfo> slots) {
		int n = 0;
		for (SlotInfo s : slots) if (s.isEmpty()) n++;
		return n;
	}
}
