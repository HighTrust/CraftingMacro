package io.github.hightrust.craftingmacro.crafting;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InventoryCapacityTest {
	private static InventoryCapacity.SlotInfo empty() {
		return new InventoryCapacity.SlotInfo(null, 0, 64);
	}

	private static InventoryCapacity.SlotInfo of(String id, int count, int max) {
		return new InventoryCapacity.SlotInfo(id, count, max);
	}

	private static List<InventoryCapacity.SlotInfo> fullInventory(String id, int count, int max) {
		List<InventoryCapacity.SlotInfo> slots = new ArrayList<>();
		for (int i = 0; i < 36; i++) slots.add(of(id, count, max));
		return slots;
	}

	@Test
	void emptyInventoryFitsEverything() {
		List<InventoryCapacity.SlotInfo> slots = new ArrayList<>();
		for (int i = 0; i < 36; i++) slots.add(empty());
		assertEquals(36 * 64, InventoryCapacity.spaceFor(slots, "minecraft:snow_block", 64));
		assertEquals(36 * 16, InventoryCapacity.spaceFor(slots, "minecraft:snowball", 16));
		assertEquals(36, InventoryCapacity.emptySlots(slots));
	}

	@Test
	void partialStacksOfSameItemCount() {
		List<InventoryCapacity.SlotInfo> slots = fullInventory("minecraft:dirt", 64, 64);
		slots.set(0, of("minecraft:snow_block", 60, 64));
		slots.set(1, of("minecraft:snow_block", 64, 64));
		assertEquals(4, InventoryCapacity.spaceFor(slots, "minecraft:snow_block", 64));
		assertEquals(0, InventoryCapacity.spaceFor(slots, "minecraft:stone", 64));
	}

	@Test
	void fullInventoryFitsNothing() {
		List<InventoryCapacity.SlotInfo> slots = fullInventory("minecraft:dirt", 64, 64);
		assertEquals(0, InventoryCapacity.craftsThatFit(slots, "minecraft:snow_block", 64, 1));
		assertEquals(0, InventoryCapacity.emptySlots(slots));
	}

	@Test
	void craftsThatFitAccountsForResultCount() {
		List<InventoryCapacity.SlotInfo> slots = fullInventory("minecraft:dirt", 64, 64);
		slots.set(5, empty());
		// 64 layers fit, 6 per craft -> 10 crafts
		assertEquals(10, InventoryCapacity.craftsThatFit(slots, "minecraft:snow", 64, 6));
		assertEquals(0, InventoryCapacity.craftsThatFit(slots, "minecraft:snow", 64, 0));
	}

	@Test
	void unstackableItems() {
		List<InventoryCapacity.SlotInfo> slots = fullInventory("minecraft:dirt", 64, 64);
		slots.set(0, empty());
		slots.set(1, empty());
		slots.set(2, of("minecraft:wooden_pickaxe", 1, 1));
		assertEquals(2, InventoryCapacity.spaceFor(slots, "minecraft:wooden_pickaxe", 1));
	}
}
