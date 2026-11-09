package io.github.hightrust.craftingmacro.crafting;

import io.github.hightrust.craftingmacro.MacroManager;
import net.minecraft.client.player.LocalPlayer;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Sends crafting results to the HUD feed and the persistent stats. */
final class CraftReport {
	private CraftReport() {
	}

	/** Items just landed in the inventory: animated "+N" in the HUD feed. */
	static void batch(String itemId, int count) {
		MacroManager m = MacroManager.get();
		if (m != null) m.craftLog().crafted(itemId, count);
	}

	/**
	 * Session finished: ingredients used = (start count - current count) for every item that could
	 * be an ingredient, measured from the synced inventory.
	 */
	static void session(String resultId, int crafted, Map<String, Integer> startCounts, Collection<? extends Collection<String>> ingredientChoices) {
		MacroManager m = MacroManager.get();
		LocalPlayer player = ScreenController.player();
		if (m == null || player == null) return;
		Set<String> ingredients = new HashSet<>();
		for (Collection<String> c : ingredientChoices) ingredients.addAll(c);
		ingredients.remove(resultId);
		Map<String, Integer> now = InventoryAnalyzer.counts(player, ScreenController.gridSlots(player.containerMenu));
		Map<String, Integer> used = new LinkedHashMap<>();
		for (String id : ingredients) {
			int diff = startCounts.getOrDefault(id, 0) - now.getOrDefault(id, 0);
			if (diff > 0) used.put(id, diff);
		}
		m.craftLog().record(resultId, crafted, used);
	}
}
