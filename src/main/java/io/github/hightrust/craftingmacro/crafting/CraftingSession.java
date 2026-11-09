package io.github.hightrust.craftingmacro.crafting;

import io.github.hightrust.craftingmacro.config.Settings;
import io.github.hightrust.craftingmacro.exec.StepResult;
import io.github.hightrust.craftingmacro.macro.MacroStep;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tick-driven crafting state machine.
 *
 * <p>Each batch: ask the server to place N recipe sets via the recipe book (the same packet the
 * recipe book button sends), wait until the server-updated grid shows them, shift-click the result
 * (vanilla crafts every set in the grid and moves results to the inventory), then wait until the
 * inventory has settled and measure what was actually crafted from the synced inventory. Nothing is
 * assumed from client-side prediction, every wait is bounded, and no item is ever thrown or dropped.
 */
public final class CraftingSession {
	private enum Phase { RESOLVE, OPENING, PREPARE, PLACE, WAIT_PLACE, TAKE, WAIT_TAKE, CLEANUP, WAIT_CLEANUP, DONE }

	private static final int SETTLE_TICKS = 3;
	private static final int MAX_BATCH_PLACEMENTS = 64;

	private final String targetId;
	private final int wantedItems;
	private final Settings settings;

	private Phase phase = Phase.RESOLVE;
	private RecipeResolver.CraftRecipe recipe;
	private CraftingOpener opener;
	private boolean openedScreen;
	private int menuId = -1;
	private int craftsWanted;
	private int craftsDone;
	private int itemsCrafted;
	private int startCount;
	private int batchTarget;
	private int lastGridSets;
	private int waitTicks;
	private long lastFingerprint;
	private int stableTicks;
	private String failure;
	private String finalWarning = "";

	/** Hold/toggle mode: keep crafting until ingredients or inventory space run out. */
	private final boolean unlimited;

	public CraftingSession(String targetId, int wantedItems, Settings settings) {
		this(targetId, wantedItems, settings, false);
	}

	private CraftingSession(String targetId, int wantedItems, Settings settings, boolean unlimited) {
		this.targetId = targetId;
		this.wantedItems = wantedItems;
		this.settings = settings;
		this.unlimited = unlimited;
	}

	/** Crafts as many as the inventory allows. */
	public static CraftingSession asMuchAsPossible(String targetId, Settings settings) {
		return new CraftingSession(targetId, io.github.hightrust.craftingmacro.macro.MacroValidator.MAX_CRAFT_QUANTITY, settings, true);
	}

	public boolean openedScreen() {
		return openedScreen;
	}

	public int itemsCrafted() {
		return itemsCrafted;
	}

	/** Upper bound for the whole session: generous per batch, still finite. */
	public int timeoutTicks() {
		int batches = Math.max(1, wantedItems);
		return Math.min(20 * 60 * 10, 200 + batches * 20);
	}

	public StepResult tick() {
		if (!ScreenController.inWorld()) return StepResult.failed("Not in a world");
		LocalPlayer player = ScreenController.player();
		if (player.isDeadOrDying()) return StepResult.failed("Interrupted: player died");
		if (phase != Phase.RESOLVE && phase != Phase.OPENING && menuId >= 0 && player.containerMenu.containerId != menuId) {
			return StepResult.failed("Macro interrupted: crafting screen was closed");
		}
		return switch (phase) {
			case RESOLVE -> resolve(player);
			case OPENING -> opening(player);
			case PREPARE -> prepare(player);
			case PLACE -> place(player);
			case WAIT_PLACE -> waitPlace(player);
			case TAKE -> take(player);
			case WAIT_TAKE -> waitTake(player);
			case CLEANUP -> cleanup(player);
			case WAIT_CLEANUP -> waitCleanup(player);
			case DONE -> finish();
		};
	}

	private StepResult resolve(LocalPlayer player) {
		AbstractContainerMenu current = player.containerMenu;
		List<Slot> currentGrid = ScreenController.gridSlots(current);
		recipe = RecipeResolver.resolve(targetId, InventoryAnalyzer.stacked(player, currentGrid), settings.preferInventoryGrid);
		if (recipe == null) {
			return StepResult.failed("No unlocked crafting recipe for " + MacroStep.prettyItem(targetId)
					+ " (recipes must be unlocked in your recipe book)");
		}
		craftsWanted = IngredientPlanner.craftsNeeded(wantedItems, recipe.resultCount());

		Map<String, Integer> have = InventoryAnalyzer.counts(player, currentGrid);
		if (IngredientPlanner.maxCrafts(recipe.slots(), have, 1) < 1) {
			return StepResult.failed("Missing ingredients for " + recipe.result().getHoverName().getString() + ": " + describeMissing(have, 1));
		}
		if (InventoryCapacity.craftsThatFit(InventoryAnalyzer.mainSlots(player), recipe.resultId(), recipe.result().getMaxStackSize(), recipe.resultCount()) < 1
				&& InventoryCapacity.emptySlots(InventoryAnalyzer.mainSlots(player)) == 0) {
			return StepResult.failed("Inventory full: no room for " + recipe.result().getHoverName().getString());
		}
		startCount = InventoryAnalyzer.count(player, recipe.resultId());
		startCounts = InventoryAnalyzer.counts(player, currentGrid);

		if (current instanceof CraftingMenu) {
			menuId = current.containerId;
			phase = Phase.PREPARE;
		} else if (recipe.fits2x2() && settings.preferInventoryGrid && current == player.inventoryMenu) {
			menuId = current.containerId;
			phase = Phase.PREPARE;
		} else {
			opener = new CraftingOpener(settings.craftOpenCommand, settings.useLookedAtCraftingTable, settings.serverTimeoutTicks);
			phase = Phase.OPENING;
		}
		return StepResult.running();
	}

	private StepResult opening(LocalPlayer player) {
		StepResult r = opener.tick();
		if (r.isRunning()) return r;
		if (r.isFailed()) {
			// Fall back to the 2x2 grid when possible instead of failing.
			if (recipe.fits2x2() && ScreenController.isInventoryMenuActive()) {
				menuId = player.inventoryMenu.containerId;
				phase = Phase.PREPARE;
				return StepResult.running();
			}
			return r;
		}
		openedScreen = opener.openedScreen();
		menuId = player.containerMenu.containerId;
		phase = Phase.PREPARE;
		return StepResult.running();
	}

	/** If the grid holds unrelated items, move them to the inventory first. */
	private StepResult prepare(LocalPlayer player) {
		AbstractContainerMenu menu = player.containerMenu;
		if (!menu.getCarried().isEmpty()) {
			return StepResult.failed("Cannot craft while holding an item on the cursor");
		}
		if (!ScreenController.gridEmpty(menu)) {
			for (Slot s : ScreenController.gridSlots(menu)) {
				if (s.hasItem()) ScreenController.quickMove(menu, s.index);
			}
			phase = Phase.WAIT_CLEANUP;
			beginSettle(menu);
			cleanupThenPlace = true;
			return StepResult.running();
		}
		phase = Phase.PLACE;
		return StepResult.running();
	}

	private boolean cleanupThenPlace;

	private StepResult place(LocalPlayer player) {
		AbstractContainerMenu menu = player.containerMenu;
		int remaining = craftsWanted - craftsDone;
		if (remaining <= 0) {
			phase = Phase.CLEANUP;
			return StepResult.running();
		}
		List<Slot> grid = ScreenController.gridSlots(menu);
		Map<String, Integer> have = InventoryAnalyzer.counts(player, grid);
		int possible = IngredientPlanner.maxCrafts(recipe.slots(), have, remaining);
		if (possible < 1) {
			if (craftsDone == 0) {
				return StepResult.failed("Missing ingredients for " + recipe.result().getHoverName().getString() + ": " + describeMissing(have, 1));
			}
			finalWarning = "Ran out of ingredients";
			phase = Phase.CLEANUP;
			return StepResult.running();
		}
		int fit = InventoryCapacity.craftsThatFit(InventoryAnalyzer.mainSlots(player), recipe.resultId(),
				recipe.result().getMaxStackSize(), recipe.resultCount());
		// Consumed ingredients free space too, so allow at least one craft if any slot is free.
		int target = Math.min(possible, Math.max(fit, 1));
		target = Math.min(target, maxIngredientStack(player));
		target = Math.min(target, MAX_BATCH_PLACEMENTS);
		batchTarget = Math.max(1, target);
		lastGridSets = ScreenController.gridSets(menu);
		// One placement with useMax=true fills to the server-computed maximum, which is exactly what
		// we want when the batch target already equals that maximum; otherwise add sets one by one.
		boolean useMax = batchTarget == Math.min(IngredientPlanner.maxCrafts(recipe.slots(), have, Integer.MAX_VALUE), maxIngredientStack(player));
		ScreenController.placeRecipe(menu, recipe, useMax);
		waitTicks = 0;
		phase = Phase.WAIT_PLACE;
		return StepResult.running();
	}

	private StepResult waitPlace(LocalPlayer player) {
		AbstractContainerMenu menu = player.containerMenu;
		int sets = ScreenController.gridSets(menu);
		ItemStack result = ScreenController.resultStack(menu);
		if (sets > lastGridSets) {
			lastGridSets = sets;
			waitTicks = 0;
			if (sets < batchTarget) {
				ScreenController.placeRecipe(menu, recipe, false);
				return StepResult.running();
			}
		}
		if (sets >= batchTarget || (sets > 0 && sets == lastGridSets && waitTicks > 4)) {
			if (!result.isEmpty() && RecipeResolver.itemId(result.getItem()).equals(recipe.resultId())) {
				phase = Phase.TAKE;
				return StepResult.running();
			}
		}
		if (++waitTicks > settings.serverTimeoutTicks) {
			if (sets > 0 && !result.isEmpty()) {
				phase = Phase.TAKE;
				return StepResult.running();
			}
			finalWarning = "The server did not place the recipe (it may not be unlocked, or the inventory is full)";
			if (craftsDone == 0) failure = finalWarning;
			phase = Phase.CLEANUP;
			return StepResult.running();
		}
		return StepResult.running();
	}

	private StepResult take(LocalPlayer player) {
		AbstractContainerMenu menu = player.containerMenu;
		ScreenController.quickMove(menu, 0);
		beginSettle(menu);
		phase = Phase.WAIT_TAKE;
		return StepResult.running();
	}

	private StepResult waitTake(LocalPlayer player) {
		AbstractContainerMenu menu = player.containerMenu;
		if (!settled(menu)) {
			if (++waitTicks > settings.serverTimeoutTicks) {
				failure = "Server did not confirm the craft in time";
				phase = Phase.CLEANUP;
			}
			return StepResult.running();
		}
		int now = InventoryAnalyzer.count(player, recipe.resultId());
		int gained = Math.max(0, now - startCount);
		int craftedThisBatch = Math.max(0, gained - itemsCrafted) / recipe.resultCount();
		if (gained > itemsCrafted) CraftReport.batch(recipe.resultId(), gained - itemsCrafted);
		itemsCrafted = gained;
		craftsDone += craftedThisBatch;
		if (!ScreenController.gridEmpty(menu) && !ScreenController.resultStack(menu).isEmpty()) {
			// Sets are still in the grid but the result could not be moved: no room left.
			finalWarning = "Inventory full";
			phase = Phase.CLEANUP;
			return StepResult.running();
		}
		if (craftedThisBatch == 0) {
			failure = "Crafting failed: the server rejected the craft";
			phase = Phase.CLEANUP;
			return StepResult.running();
		}
		phase = craftsDone >= craftsWanted ? Phase.CLEANUP : Phase.PLACE;
		return StepResult.running();
	}

	/** Return anything left in the grid to the inventory so nothing is dropped when the screen closes. */
	private StepResult cleanup(LocalPlayer player) {
		AbstractContainerMenu menu = player.containerMenu;
		cleanupThenPlace = false;
		if (ScreenController.gridEmpty(menu)) {
			phase = Phase.DONE;
			return StepResult.running();
		}
		for (Slot s : ScreenController.gridSlots(menu)) {
			if (s.hasItem()) ScreenController.quickMove(menu, s.index);
		}
		beginSettle(menu);
		phase = Phase.WAIT_CLEANUP;
		return StepResult.running();
	}

	private StepResult waitCleanup(LocalPlayer player) {
		AbstractContainerMenu menu = player.containerMenu;
		if (!settled(menu) && ++waitTicks <= settings.serverTimeoutTicks) return StepResult.running();
		if (!ScreenController.gridEmpty(menu)) {
			// Never close the screen with items in the grid: vanilla would drop them if the inventory is full.
			keepScreenOpen = true;
			String msg = "Inventory full: items were left in the crafting grid";
			if (cleanupThenPlace) return StepResult.failed(msg);
			finalWarning = msg;
		}
		if (cleanupThenPlace) {
			cleanupThenPlace = false;
			phase = Phase.PLACE;
			return StepResult.running();
		}
		phase = Phase.DONE;
		return StepResult.running();
	}

	private boolean keepScreenOpen;

	/** True when the macro must not close the screen (items still in the grid). */
	public boolean mustKeepScreenOpen() {
		return keepScreenOpen;
	}

	private Map<String, Integer> startCounts = Map.of();

	private StepResult finish() {
		if (recipe != null && itemsCrafted > 0) {
			CraftReport.session(recipe.resultId(), itemsCrafted, startCounts, recipe.slots());
		}
		if (failure != null) return StepResult.failed(failure);
		String name = recipe.result().getHoverName().getString();
		if (craftsDone >= craftsWanted) {
			return StepResult.done("Crafted " + itemsCrafted + "x " + name);
		}
		String reason = finalWarning.isEmpty() ? "" : " (" + finalWarning + ")";
		if (itemsCrafted == 0) return StepResult.failed("Crafted nothing" + reason);
		if (unlimited) return StepResult.done("Crafted " + itemsCrafted + "x " + name + reason);
		return StepResult.done("Crafted " + itemsCrafted + "/" + (craftsWanted * recipe.resultCount()) + "x " + name + reason);
	}

	/** Best-effort safe cancel: move grid contents back to the inventory. Never drops items. */
	public void cancel() {
		if (!ScreenController.inWorld()) return;
		LocalPlayer player = ScreenController.player();
		AbstractContainerMenu menu = player.containerMenu;
		if (menuId >= 0 && menu.containerId == menuId) {
			for (Slot s : ScreenController.gridSlots(menu)) {
				if (s.hasItem()) ScreenController.quickMove(menu, s.index);
			}
		}
	}

	private int maxIngredientStack(LocalPlayer player) {
		int min = 64;
		net.minecraft.world.entity.player.Inventory inv = player.getInventory();
		for (Set<String> slot : recipe.slots()) {
			int best = 1;
			for (int i = 0; i < net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE; i++) {
				ItemStack s = inv.getItem(i);
				if (!s.isEmpty() && slot.contains(RecipeResolver.itemId(s.getItem()))) best = Math.max(best, s.getMaxStackSize());
			}
			for (Slot g : ScreenController.gridSlots(player.containerMenu)) {
				ItemStack s = g.getItem();
				if (!s.isEmpty() && slot.contains(RecipeResolver.itemId(s.getItem()))) best = Math.max(best, s.getMaxStackSize());
			}
			min = Math.min(min, best);
		}
		return Math.max(1, min);
	}

	private String describeMissing(Map<String, Integer> have, int crafts) {
		Map<Set<String>, Integer> missing = IngredientPlanner.missing(recipe.slots(), have, crafts);
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<Set<String>, Integer> e : missing.entrySet()) {
			if (!sb.isEmpty()) sb.append(", ");
			String first = e.getKey().iterator().next();
			sb.append(e.getValue()).append("x ").append(MacroStep.prettyItem(first));
			if (e.getKey().size() > 1) sb.append(" (or similar)");
		}
		return sb.isEmpty() ? "unknown" : sb.toString();
	}

	private void beginSettle(AbstractContainerMenu menu) {
		lastFingerprint = InventoryAnalyzer.fingerprint(menu);
		stableTicks = 0;
		waitTicks = 0;
	}

	/** True once the menu contents have not changed for a few ticks (server updates have arrived). */
	private boolean settled(AbstractContainerMenu menu) {
		long fp = InventoryAnalyzer.fingerprint(menu);
		if (fp == lastFingerprint) {
			stableTicks++;
		} else {
			lastFingerprint = fp;
			stableTicks = 0;
		}
		return stableTicks >= SETTLE_TICKS;
	}
}
