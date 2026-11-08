package io.github.hightrust.craftingmacro.crafting;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IngredientPlannerTest {
	private static final Set<String> SNOWBALL = Set.of("minecraft:snowball");
	private static final Set<String> SNOW_BLOCK = Set.of("minecraft:snow_block");
	private static final Set<String> PLANKS = Set.of("minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:birch_planks");

	/** Snow block: 2x2 of snowballs. */
	private static final List<Set<String>> SNOW_BLOCK_RECIPE = Collections.nCopies(4, SNOWBALL);
	/** Snow layers: 3 snow blocks in a row -> 6 layers. */
	private static final List<Set<String>> SNOW_LAYER_RECIPE = Collections.nCopies(3, SNOW_BLOCK);
	/** Crafting table: 4 planks of any kind. */
	private static final List<Set<String>> CRAFTING_TABLE = Collections.nCopies(4, PLANKS);

	@Test
	void craftsNeededRoundsUp() {
		assertEquals(1, IngredientPlanner.craftsNeeded(1, 1));
		assertEquals(16, IngredientPlanner.craftsNeeded(16, 1));
		assertEquals(3, IngredientPlanner.craftsNeeded(18, 6), "18 snow layers = 3 operations");
		assertEquals(4, IngredientPlanner.craftsNeeded(19, 6));
		assertEquals(0, IngredientPlanner.craftsNeeded(0, 6));
		assertThrows(IllegalArgumentException.class, () -> IngredientPlanner.craftsNeeded(5, 0));
	}

	@Test
	void totalRequirementsMultiplyPerSlot() {
		Map<Set<String>, Integer> need = IngredientPlanner.totalRequirements(SNOW_BLOCK_RECIPE, 16);
		assertEquals(Map.of(SNOWBALL, 64), need, "16 snow blocks need 64 snowballs");
		assertEquals(Map.of(SNOW_BLOCK, 9), IngredientPlanner.totalRequirements(SNOW_LAYER_RECIPE, 3));
	}

	@Test
	void maxCraftsFromInventory() {
		assertEquals(4, IngredientPlanner.maxCrafts(SNOW_BLOCK_RECIPE, Map.of("minecraft:snowball", 17), 100));
		assertEquals(0, IngredientPlanner.maxCrafts(SNOW_BLOCK_RECIPE, Map.of("minecraft:snowball", 3), 100));
		assertEquals(2, IngredientPlanner.maxCrafts(SNOW_BLOCK_RECIPE, Map.of("minecraft:snowball", 64), 2), "limit respected");
		assertEquals(0, IngredientPlanner.maxCrafts(List.of(), Map.of("minecraft:snowball", 64), 10));
	}

	@Test
	void alternativesAreCombined() {
		// 3 oak + 3 spruce + 2 birch = 8 planks -> 2 crafting tables, mixing types.
		Map<String, Integer> inv = Map.of("minecraft:oak_planks", 3, "minecraft:spruce_planks", 3, "minecraft:birch_planks", 2);
		assertEquals(2, IngredientPlanner.maxCrafts(CRAFTING_TABLE, inv, 100));
		assertTrue(IngredientPlanner.missing(CRAFTING_TABLE, inv, 2).isEmpty());
		assertEquals(Map.of(PLANKS, 4), IngredientPlanner.missing(CRAFTING_TABLE, inv, 3));
	}

	@Test
	void mixedRecipeWithDistinctIngredients() {
		// Torch-like: 1 coal + 1 stick per craft.
		Set<String> coal = Set.of("minecraft:coal", "minecraft:charcoal");
		Set<String> stick = Set.of("minecraft:stick");
		List<Set<String>> torch = List.of(coal, stick);
		Map<String, Integer> inv = Map.of("minecraft:coal", 2, "minecraft:charcoal", 3, "minecraft:stick", 4);
		assertEquals(4, IngredientPlanner.maxCrafts(torch, inv, 100), "sticks are the bottleneck");
		assertEquals(Map.of(stick, 1), IngredientPlanner.missing(torch, inv, 5));
	}

	@Test
	void missingReportsShortfall() {
		Map<Set<String>, Integer> missing = IngredientPlanner.missing(SNOW_BLOCK_RECIPE, Map.of("minecraft:snowball", 10), 16);
		assertEquals(Map.of(SNOWBALL, 54), missing);
	}
}
