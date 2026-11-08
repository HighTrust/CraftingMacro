package io.github.hightrust.craftingmacro.crafting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure ingredient math, independent of Minecraft classes so it can be unit-tested.
 * A recipe is a list of ingredient slots; each slot accepts any one of a set of item ids
 * (this models tag ingredients such as "any planks").
 */
public final class IngredientPlanner {
	private IngredientPlanner() {
	}

	/** Number of crafting operations needed to obtain at least {@code wantedItems} results. */
	public static int craftsNeeded(int wantedItems, int resultPerCraft) {
		if (wantedItems <= 0) return 0;
		if (resultPerCraft <= 0) throw new IllegalArgumentException("resultPerCraft must be positive");
		return (wantedItems + resultPerCraft - 1) / resultPerCraft;
	}

	/** Total items consumed per item-choice group for {@code crafts} operations (group key = the slot's choice set). */
	public static Map<Set<String>, Integer> totalRequirements(List<Set<String>> slots, int crafts) {
		Map<Set<String>, Integer> totals = new LinkedHashMap<>();
		for (Set<String> slot : slots) {
			if (slot == null || slot.isEmpty()) continue;
			totals.merge(slot, crafts, Integer::sum);
		}
		return totals;
	}

	/**
	 * Maximum number of crafts possible from {@code inventory} (item id -> count).
	 * Greedy simulation: each craft fills each slot from the accepted item with the most
	 * remaining. Exact for recipes whose slots don't partially overlap (all vanilla
	 * crafting recipes); conservative otherwise.
	 */
	public static int maxCrafts(List<Set<String>> slots, Map<String, Integer> inventory, int limit) {
		Map<String, Integer> remaining = new HashMap<>(inventory);
		List<Set<String>> active = new ArrayList<>();
		for (Set<String> s : slots) if (s != null && !s.isEmpty()) active.add(s);
		if (active.isEmpty()) return 0;
		int crafts = 0;
		while (crafts < limit) {
			Map<String, Integer> trial = new HashMap<>(remaining);
			boolean ok = true;
			for (Set<String> slot : active) {
				String best = null;
				int bestCount = 0;
				for (String item : slot) {
					int c = trial.getOrDefault(item, 0);
					if (c > bestCount) {
						best = item;
						bestCount = c;
					}
				}
				if (best == null) {
					ok = false;
					break;
				}
				trial.put(best, bestCount - 1);
			}
			if (!ok) break;
			remaining = trial;
			crafts++;
		}
		return crafts;
	}

	/**
	 * Describes what is missing to perform {@code crafts} operations: choice set -> missing count.
	 * Empty map = enough ingredients.
	 */
	public static Map<Set<String>, Integer> missing(List<Set<String>> slots, Map<String, Integer> inventory, int crafts) {
		Map<Set<String>, Integer> need = totalRequirements(slots, crafts);
		Map<String, Integer> remaining = new HashMap<>(inventory);
		Map<Set<String>, Integer> missing = new LinkedHashMap<>();
		for (Map.Entry<Set<String>, Integer> e : need.entrySet()) {
			int stillNeeded = e.getValue();
			for (String item : e.getKey()) {
				int have = remaining.getOrDefault(item, 0);
				int take = Math.min(have, stillNeeded);
				remaining.put(item, have - take);
				stillNeeded -= take;
				if (stillNeeded == 0) break;
			}
			if (stillNeeded > 0) missing.put(e.getKey(), stillNeeded);
		}
		return missing;
	}
}
