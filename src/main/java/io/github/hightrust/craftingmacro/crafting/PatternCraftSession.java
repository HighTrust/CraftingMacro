package io.github.hightrust.craftingmacro.crafting;

import io.github.hightrust.craftingmacro.config.Settings;
import io.github.hightrust.craftingmacro.exec.StepResult;
import io.github.hightrust.craftingmacro.macro.MacroStep;
import io.github.hightrust.craftingmacro.macro.MacroValidator;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Crafts a user-designed 3x3 pattern by placing items with ordinary slot clicks, exactly as a
 * player would by hand. This works for any recipe the server accepts, including custom/plugin
 * recipes that are not in the recipe book.
 *
 * <p>Per batch: put N items into each pattern cell (one cell per tick, verified against the
 * server-synced grid), wait for the server to show a result, shift-click it, then measure what
 * arrived. Items are only ever moved between the player's own slots and the grid; the cursor is
 * always emptied back into the inventory and nothing is thrown.
 */
public final class PatternCraftSession {
	private enum Phase { OPEN, CLEAR, PLACE, WAIT_RESULT, TAKE, WAIT_TAKE, CLEANUP, WAIT_CLEANUP, DONE }

	private static final int MAX_BATCH = 16;
	private static final int SETTLE_TICKS = 3;

	private final List<String> pattern;
	private final int wantedCrafts;
	private final boolean unlimited;
	private final Settings settings;
	private final boolean fits2x2;

	private Phase phase = Phase.OPEN;
	private CraftingOpener opener;
	private boolean openedScreen;
	private int menuId = -1;
	private int craftsDone;
	private int batch;
	private int cell;
	private int cellAttempts;
	private int waitTicks;
	private long fingerprint;
	private int stable;
	private String resultId;
	private int resultStart;
	private int itemsCrafted;
	private Map<String, Integer> startCounts = Map.of();
	private String failure;
	private String warning = "";
	private boolean keepOpen;

	public PatternCraftSession(List<String> pattern, int crafts, boolean unlimited, Settings settings) {
		this.pattern = pattern;
		this.wantedCrafts = unlimited ? MacroValidator.MAX_CRAFT_QUANTITY : crafts;
		this.unlimited = unlimited;
		this.settings = settings;
		boolean small = true;
		for (int i = 0; i < 9; i++) {
			int row = i / 3, col = i % 3;
			if ((row == 2 || col == 2) && !pattern.get(i).isEmpty()) small = false;
		}
		this.fits2x2 = small;
	}

	public boolean openedScreen() {
		return openedScreen;
	}

	public boolean mustKeepScreenOpen() {
		return keepOpen;
	}

	public int itemsCrafted() {
		return itemsCrafted;
	}

	public int timeoutTicks() {
		return Math.min(20 * 60 * 10, 400 + wantedCrafts * 30);
	}

	// ------------------------------------------------------------------ grid mapping

	private boolean isTable(AbstractContainerMenu menu) {
		return menu instanceof CraftingMenu;
	}

	/** Menu slot index for pattern cell i, or -1 if the current grid can't hold it. */
	private int gridSlot(AbstractContainerMenu menu, int i) {
		if (isTable(menu)) return 1 + i;
		int row = i / 3, col = i % 3;
		if (row > 1 || col > 1) return -1;
		return 1 + row * 2 + col; // inventory 2x2 grid: slots 1-4
	}

	private int invStart(AbstractContainerMenu menu) {
		return isTable(menu) ? 10 : 9;
	}

	private int invEnd(AbstractContainerMenu menu) {
		return isTable(menu) ? 46 : 45;
	}

	private List<Slot> grid(AbstractContainerMenu menu) {
		return ScreenController.gridSlots(menu);
	}

	private Map<String, Integer> needPerCraft() {
		Map<String, Integer> need = new LinkedHashMap<>();
		for (String id : pattern) if (!id.isEmpty()) need.merge(id, 1, Integer::sum);
		return need;
	}

	// ------------------------------------------------------------------ state machine

	public StepResult tick() {
		if (!ScreenController.inWorld()) return StepResult.failed("Not in a world");
		LocalPlayer player = ScreenController.player();
		if (player.isDeadOrDying()) return StepResult.failed("Interrupted: player died");
		if (menuId >= 0 && player.containerMenu.containerId != menuId) {
			return StepResult.failed("Macro interrupted: crafting screen was closed");
		}
		AbstractContainerMenu menu = player.containerMenu;
		return switch (phase) {
			case OPEN -> open(player);
			case CLEAR -> clear(menu);
			case PLACE -> place(player, menu);
			case WAIT_RESULT -> waitResult(player, menu);
			case TAKE -> {
				ScreenController.quickMove(menu, 0);
				beginSettle(menu);
				phase = Phase.WAIT_TAKE;
				yield StepResult.running();
			}
			case WAIT_TAKE -> waitTake(player, menu);
			case CLEANUP -> cleanup(menu);
			case WAIT_CLEANUP -> waitCleanup(menu);
			case DONE -> finish();
		};
	}

	private StepResult open(LocalPlayer player) {
		if (opener == null) {
			Map<String, Integer> have = InventoryAnalyzer.counts(player, null);
			StringBuilder missing = new StringBuilder();
			for (Map.Entry<String, Integer> e : needPerCraft().entrySet()) {
				if (have.getOrDefault(e.getKey(), 0) < e.getValue()) {
					if (!missing.isEmpty()) missing.append(", ");
					missing.append(e.getValue() - have.getOrDefault(e.getKey(), 0)).append("x ").append(MacroStep.prettyItem(e.getKey()));
				}
			}
			if (!missing.isEmpty() && ScreenController.gridEmpty(player.containerMenu)) {
				return StepResult.failed("Missing ingredients: " + missing);
			}
			if (ScreenController.isCraftingTableOpen() || (fits2x2 && settings.preferInventoryGrid && ScreenController.isInventoryMenuActive())) {
				return ready(player);
			}
			opener = new CraftingOpener(settings.craftOpenCommand, settings.useLookedAtCraftingTable, settings.serverTimeoutTicks);
		}
		StepResult r = opener.tick();
		if (r.isRunning()) return r;
		if (r.isFailed()) {
			if (fits2x2 && ScreenController.isInventoryMenuActive()) return ready(player);
			return r;
		}
		openedScreen = opener.openedScreen();
		return ready(player);
	}

	private StepResult ready(LocalPlayer player) {
		AbstractContainerMenu menu = player.containerMenu;
		if (!menu.getCarried().isEmpty()) return StepResult.failed("Cannot craft while holding an item on the cursor");
		menuId = menu.containerId;
		startCounts = InventoryAnalyzer.counts(player, grid(menu));
		phase = Phase.CLEAR;
		return StepResult.running();
	}

	/** Grid must start empty so the layout is exact. */
	private StepResult clear(AbstractContainerMenu menu) {
		if (ScreenController.gridEmpty(menu)) {
			startBatch(menu);
			return StepResult.running();
		}
		for (Slot s : grid(menu)) if (s.hasItem()) ScreenController.quickMove(menu, s.index);
		beginSettle(menu);
		phase = Phase.WAIT_CLEANUP;
		clearing = true;
		return StepResult.running();
	}

	private boolean clearing;

	private void startBatch(AbstractContainerMenu menu) {
		LocalPlayer player = ScreenController.player();
		Map<String, Integer> have = InventoryAnalyzer.counts(player, null);
		int possible = Integer.MAX_VALUE;
		for (Map.Entry<String, Integer> e : needPerCraft().entrySet()) {
			possible = Math.min(possible, have.getOrDefault(e.getKey(), 0) / e.getValue());
		}
		int remaining = wantedCrafts - craftsDone;
		batch = Math.min(Math.min(possible, remaining), MAX_BATCH);
		// a grid cell can't hold more than the item's stack size
		for (int i = 0; i < 9; i++) {
			if (pattern.get(i).isEmpty()) continue;
			ItemStack sample = findStack(menu, pattern.get(i));
			if (sample != null) batch = Math.min(batch, sample.getMaxStackSize());
		}
		if (batch <= 0) {
			if (craftsDone == 0) failure = "Missing ingredients for this pattern";
			else warning = "Ran out of ingredients";
			phase = Phase.CLEANUP;
			return;
		}
		cell = 0;
		cellAttempts = 0;
		phase = Phase.PLACE;
	}

	private ItemStack findStack(AbstractContainerMenu menu, String id) {
		for (int s = invStart(menu); s < invEnd(menu) && s < menu.slots.size(); s++) {
			ItemStack st = menu.getSlot(s).getItem();
			if (!st.isEmpty() && RecipeResolver.itemId(st.getItem()).equals(id)) return st;
		}
		return null;
	}

	private int findSlot(AbstractContainerMenu menu, String id) {
		int best = -1;
		int bestCount = 0;
		for (int s = invStart(menu); s < invEnd(menu) && s < menu.slots.size(); s++) {
			ItemStack st = menu.getSlot(s).getItem();
			if (!st.isEmpty() && RecipeResolver.itemId(st.getItem()).equals(id) && st.getCount() > bestCount) {
				best = s;
				bestCount = st.getCount();
			}
		}
		return best;
	}

	/** Fills one cell per tick: pick up a stack, right-click the cell N times, put the rest back. */
	private StepResult place(LocalPlayer player, AbstractContainerMenu menu) {
		if (!menu.getCarried().isEmpty()) {
			returnCarried(menu);
			return StepResult.running();
		}
		while (cell < 9 && pattern.get(cell).isEmpty()) cell++;
		if (cell >= 9) {
			waitTicks = 0;
			phase = Phase.WAIT_RESULT;
			return StepResult.running();
		}
		String id = pattern.get(cell);
		int slot = gridSlot(menu, cell);
		if (slot < 0) return StepResult.failed("This pattern needs a 3x3 crafting table");
		ItemStack inCell = menu.getSlot(slot).getItem();
		if (!inCell.isEmpty() && !RecipeResolver.itemId(inCell.getItem()).equals(id)) {
			failure = "Crafting grid changed unexpectedly";
			phase = Phase.CLEANUP;
			return StepResult.running();
		}
		int need = batch - inCell.getCount();
		if (need <= 0) {
			cell++;
			cellAttempts = 0;
			return StepResult.running();
		}
		if (++cellAttempts > 6) {
			failure = "Server did not accept item placement";
			phase = Phase.CLEANUP;
			return StepResult.running();
		}
		int source = findSlot(menu, id);
		if (source < 0) {
			warning = "Ran out of " + MacroStep.prettyItem(id);
			phase = Phase.CLEANUP;
			return StepResult.running();
		}
		ScreenController.click(menu, source, 0, ContainerInput.PICKUP);
		int carried = menu.getCarried().getCount();
		int clicks = Math.min(need, carried);
		for (int k = 0; k < clicks; k++) ScreenController.click(menu, slot, 1, ContainerInput.PICKUP);
		if (!menu.getCarried().isEmpty()) ScreenController.click(menu, source, 0, ContainerInput.PICKUP);
		return StepResult.running(); // verify on the next tick against the synced grid
	}

	/** Put whatever is on the cursor back into the inventory (never dropped). */
	private void returnCarried(AbstractContainerMenu menu) {
		String id = RecipeResolver.itemId(menu.getCarried().getItem());
		int target = -1;
		for (int s = invStart(menu); s < invEnd(menu) && s < menu.slots.size(); s++) {
			ItemStack st = menu.getSlot(s).getItem();
			if (st.isEmpty() && target < 0) target = s;
			if (!st.isEmpty() && RecipeResolver.itemId(st.getItem()).equals(id) && st.getCount() < st.getMaxStackSize()) {
				target = s;
				break;
			}
		}
		if (target >= 0) ScreenController.click(menu, target, 0, ContainerInput.PICKUP);
	}

	private StepResult waitResult(LocalPlayer player, AbstractContainerMenu menu) {
		ItemStack result = ScreenController.resultStack(menu);
		if (!result.isEmpty()) {
			String id = RecipeResolver.itemId(result.getItem());
			if (resultId == null) {
				resultId = id;
				resultStart = InventoryAnalyzer.count(player, id);
			}
			phase = Phase.TAKE;
			return StepResult.running();
		}
		if (++waitTicks > settings.serverTimeoutTicks) {
			failure = "This pattern doesn't make anything (no recipe matches)";
			phase = Phase.CLEANUP;
		}
		return StepResult.running();
	}

	private StepResult waitTake(LocalPlayer player, AbstractContainerMenu menu) {
		if (!settled(menu)) {
			if (++waitTicks > settings.serverTimeoutTicks) {
				failure = "Server did not confirm the craft in time";
				phase = Phase.CLEANUP;
			}
			return StepResult.running();
		}
		int gained = Math.max(0, InventoryAnalyzer.count(player, resultId) - resultStart);
		if (gained > itemsCrafted) CraftReport.batch(resultId, gained - itemsCrafted);
		boolean progressed = gained > itemsCrafted;
		itemsCrafted = gained;
		if (!ScreenController.gridEmpty(menu) && !ScreenController.resultStack(menu).isEmpty()) {
			warning = "Inventory full";
			phase = Phase.CLEANUP;
			return StepResult.running();
		}
		if (!progressed) {
			failure = "Crafting failed: the server rejected the craft";
			phase = Phase.CLEANUP;
			return StepResult.running();
		}
		craftsDone += batch;
		if (craftsDone >= wantedCrafts) {
			phase = Phase.CLEANUP;
		} else {
			startBatch(menu);
		}
		return StepResult.running();
	}

	private StepResult cleanup(AbstractContainerMenu menu) {
		clearing = false;
		if (!menu.getCarried().isEmpty()) {
			returnCarried(menu);
			return StepResult.running();
		}
		if (ScreenController.gridEmpty(menu)) {
			phase = Phase.DONE;
			return StepResult.running();
		}
		for (Slot s : grid(menu)) if (s.hasItem()) ScreenController.quickMove(menu, s.index);
		beginSettle(menu);
		phase = Phase.WAIT_CLEANUP;
		return StepResult.running();
	}

	private StepResult waitCleanup(AbstractContainerMenu menu) {
		if (!settled(menu) && ++waitTicks <= settings.serverTimeoutTicks) return StepResult.running();
		if (!ScreenController.gridEmpty(menu)) {
			keepOpen = true;
			if (clearing) return StepResult.failed("Inventory full: could not empty the crafting grid");
			warning = "Inventory full: items were left in the crafting grid";
		}
		if (clearing) {
			clearing = false;
			startBatch(menu);
			return StepResult.running();
		}
		phase = Phase.DONE;
		return StepResult.running();
	}

	private StepResult finish() {
		if (resultId != null && itemsCrafted > 0) {
			List<Set<String>> choices = new ArrayList<>();
			for (String id : needPerCraft().keySet()) choices.add(Set.of(id));
			CraftReport.session(resultId, itemsCrafted, startCounts, choices);
		}
		if (failure != null && itemsCrafted == 0) return StepResult.failed(failure);
		String reason = warning.isEmpty() ? (failure == null ? "" : " (" + failure + ")") : " (" + warning + ")";
		if (itemsCrafted == 0) return StepResult.failed("Crafted nothing" + reason);
		String name = MacroStep.prettyItem(resultId);
		if (unlimited || craftsDone >= wantedCrafts) return StepResult.done("Crafted " + itemsCrafted + "x " + name + (unlimited ? reason : ""));
		return StepResult.done("Crafted " + itemsCrafted + "x " + name + reason);
	}

	/** Safe cancel: empty the cursor and the grid back into the inventory. */
	public void cancel() {
		if (!ScreenController.inWorld()) return;
		AbstractContainerMenu menu = ScreenController.menu();
		if (menuId < 0 || menu.containerId != menuId) return;
		if (!menu.getCarried().isEmpty()) returnCarried(menu);
		for (Slot s : grid(menu)) if (s.hasItem()) ScreenController.quickMove(menu, s.index);
	}

	private void beginSettle(AbstractContainerMenu menu) {
		fingerprint = InventoryAnalyzer.fingerprint(menu);
		stable = 0;
		waitTicks = 0;
	}

	private boolean settled(AbstractContainerMenu menu) {
		long fp = InventoryAnalyzer.fingerprint(menu);
		if (fp == fingerprint) stable++;
		else {
			fingerprint = fp;
			stable = 0;
		}
		return stable >= SETTLE_TICKS;
	}
}
