package io.github.hightrust.craftingmacro.crafting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Finds crafting recipes through the client recipe book. Since 1.21.2 the server only sends the
 * client the recipes the player has unlocked, and placement packets are only accepted for those,
 * so the recipe book is the authoritative, legitimate source.
 */
public final class RecipeResolver {
	private RecipeResolver() {
	}

	/** A crafting recipe usable for automation. */
	public record CraftRecipe(RecipeDisplayEntry entry, ItemStack result, List<Set<String>> slots, boolean fits2x2) {
		public String resultId() {
			return itemId(result.getItem());
		}

		public int resultCount() {
			return Math.max(1, result.getCount());
		}
	}

	public static String itemId(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	public static boolean isCraftingDisplay(RecipeDisplay display) {
		return display instanceof ShapedCraftingRecipeDisplay || display instanceof ShapelessCraftingRecipeDisplay;
	}

	public static boolean fits2x2(RecipeDisplay display) {
		if (display instanceof ShapedCraftingRecipeDisplay shaped) {
			return shaped.width() <= 2 && shaped.height() <= 2;
		}
		if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
			return shapeless.ingredients().size() <= 4;
		}
		return false;
	}

	/** All known crafting recipes, one per entry, in recipe book order. */
	public static List<CraftRecipe> allCraftingRecipes() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		List<CraftRecipe> out = new ArrayList<>();
		if (player == null || mc.level == null) return out;
		ContextMap ctx = SlotDisplayContext.fromLevel(mc.level);
		for (RecipeCollection collection : player.getRecipeBook().getCollections()) {
			for (RecipeDisplayEntry entry : collection.getRecipes()) {
				CraftRecipe r = toCraftRecipe(entry, ctx);
				if (r != null) out.add(r);
			}
		}
		return out;
	}

	private static CraftRecipe toCraftRecipe(RecipeDisplayEntry entry, ContextMap ctx) {
		if (!isCraftingDisplay(entry.display())) return null;
		if (entry.craftingRequirements().isEmpty()) return null;
		List<ItemStack> results = entry.resultItems(ctx);
		if (results.isEmpty() || results.get(0).isEmpty()) return null;
		List<Set<String>> slots = new ArrayList<>();
		for (Ingredient ingredient : entry.craftingRequirements().get()) {
			Set<String> choices = new LinkedHashSet<>();
			ingredient.items().forEach((Holder<Item> h) -> choices.add(itemId(h.value())));
			if (!choices.isEmpty()) slots.add(choices);
		}
		if (slots.isEmpty()) return null;
		return new CraftRecipe(entry, results.get(0).copy(), slots, fits2x2(entry.display()));
	}

	/**
	 * Unique result items for the recipe picker, sorted by name. When several recipes make the same
	 * item (e.g. different wood types), the first is used as the representative.
	 */
	public static List<CraftRecipe> uniqueByResult() {
		Map<String, CraftRecipe> byId = new LinkedHashMap<>();
		for (CraftRecipe r : allCraftingRecipes()) byId.putIfAbsent(r.resultId(), r);
		List<CraftRecipe> list = new ArrayList<>(byId.values());
		list.sort(Comparator.comparing(r -> r.result().getHoverName().getString().toLowerCase(Locale.ROOT)));
		return list;
	}

	/**
	 * Picks the best recipe producing {@code itemId}: one craftable right now beats one that is not;
	 * among those, one that fits the 2x2 grid is preferred when {@code prefer2x2}.
	 */
	public static CraftRecipe resolve(String itemId, StackedItemContents available, boolean prefer2x2) {
		CraftRecipe best = null;
		int bestScore = Integer.MIN_VALUE;
		for (CraftRecipe r : allCraftingRecipes()) {
			if (!r.resultId().equals(itemId)) continue;
			int score = 0;
			if (available != null && r.entry().canCraft(available)) score += 10;
			if (prefer2x2 && r.fits2x2()) score += 1;
			if (score > bestScore) {
				best = r;
				bestScore = score;
			}
		}
		return best;
	}
}
