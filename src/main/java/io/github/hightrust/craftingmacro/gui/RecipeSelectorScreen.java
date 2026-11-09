package io.github.hightrust.craftingmacro.gui;

import io.github.hightrust.craftingmacro.crafting.IngredientPlanner;
import io.github.hightrust.craftingmacro.crafting.InventoryAnalyzer;
import io.github.hightrust.craftingmacro.crafting.RecipeResolver;
import io.github.hightrust.craftingmacro.gui.widget.FlatButton;
import io.github.hightrust.craftingmacro.gui.widget.ToggleSwitch;
import io.github.hightrust.craftingmacro.macro.MacroValidator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/** Searchable grid of craftable items with ingredient preview and quantity input. */
public final class RecipeSelectorScreen extends BaseScreen {
	private static final int CELL = 20;
	private static boolean showAllItems;

	private final BiConsumer<String, Integer> onPick;
	private String selectedId;
	private int quantity;

	private final Map<String, RecipeResolver.CraftRecipe> recipes = new LinkedHashMap<>();
	private final List<ItemStack> entries = new ArrayList<>();
	private EditBox search;
	private EditBox qtyBox;
	private FlatButton confirm;
	private int scrollRows;
	private Map<String, Integer> inventory = new HashMap<>();

	private int gridX;
	private int gridY;
	private int gridW;
	private int gridH;
	private int infoX;
	private int infoW;

	public RecipeSelectorScreen(Screen parent, String currentId, int quantity, BiConsumer<String, Integer> onPick) {
		super("Choose Recipe", parent);
		this.selectedId = currentId == null || currentId.isBlank() ? null : currentId;
		this.quantity = Math.max(1, quantity);
		this.onPick = onPick;
	}

	@Override
	protected int preferredWidth() {
		return 470;
	}

	@Override
	protected int preferredHeight() {
		return 300;
	}

	@Override
	protected void build() {
		recipes.clear();
		for (RecipeResolver.CraftRecipe r : RecipeResolver.uniqueByResult()) recipes.put(r.resultId(), r);
		if (minecraft.player != null) inventory = InventoryAnalyzer.counts(minecraft.player, null);

		infoW = compact() ? 0 : 160;
		gridX = panelX + 8;
		gridY = panelY + 54;
		gridW = panelW - 16 - (infoW > 0 ? infoW + 8 : 0);
		gridW -= gridW % CELL;
		gridH = panelY + panelH - 34 - gridY;
		gridH -= gridH % CELL;
		infoX = gridX + gridW + 8;
		infoW = panelX + panelW - 8 - infoX;

		search = textField(gridX, panelY + 32, Math.max(80, gridW - 120), search == null ? "" : search.getValue(), 64, "Search items...", null);
		search.setResponder(s -> {
			scrollRows = 0;
			refilter();
		});
		addRenderableWidget(new ToggleSwitch(gridX + Math.max(80, gridW - 120) + 6, panelY + 34, 114, "All items", showAllItems, v -> {
			showAllItems = v;
			scrollRows = 0;
			refilter();
		}).tooltip("Show every item, not just recipes you have unlocked. Locked recipes cannot be crafted until unlocked."));

		int by = panelY + panelH - 26;
		qtyBox = numberField(panelX + 64, by + 1, 50, quantity, "Number of result items to craft (1-" + MacroValidator.MAX_CRAFT_QUANTITY + ")");
		qtyBox.setResponder(s -> {
			try {
				quantity = Math.max(1, Math.min(MacroValidator.MAX_CRAFT_QUANTITY, Integer.parseInt(s.trim())));
			} catch (NumberFormatException e) {
				quantity = 1;
			}
		});
		confirm = button(panelX + panelW - 88, by, 80, "Select", FlatButton.Style.PRIMARY, this::confirm);
		button(panelX + panelW - 172, by, 80, "Cancel", FlatButton.Style.NORMAL, this::onClose);
		refilter();
	}

	private void refilter() {
		entries.clear();
		String q = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT).trim();
		if (showAllItems) {
			for (Item item : BuiltInRegistries.ITEM) {
				if (item == Items.AIR) continue;
				ItemStack stack = new ItemStack(item);
				if (matches(stack, q)) entries.add(stack);
			}
		} else {
			for (RecipeResolver.CraftRecipe r : recipes.values()) {
				if (matches(r.result(), q)) entries.add(r.result());
			}
		}
		if (confirm != null) confirm.enabled(selectedId != null);
	}

	private static boolean matches(ItemStack stack, String q) {
		if (q.isEmpty()) return true;
		return stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)
				|| RecipeResolver.itemId(stack.getItem()).contains(q);
	}

	private int columns() {
		return Math.max(1, gridW / CELL);
	}

	private int visibleRows() {
		return Math.max(1, gridH / CELL);
	}

	private int maxScroll() {
		int rows = (entries.size() + columns() - 1) / columns();
		return Math.max(0, rows - visibleRows());
	}

	private void confirm() {
		if (selectedId == null) return;
		onClose();
		onPick.accept(selectedId, quantity);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && inside(event.x(), event.y(), gridX, gridY, gridW, gridH)) {
			int col = (int) ((event.x() - gridX) / CELL);
			int row = (int) ((event.y() - gridY) / CELL) + scrollRows;
			int idx = row * columns() + col;
			if (col < columns() && idx >= 0 && idx < entries.size()) {
				selectedId = RecipeResolver.itemId(entries.get(idx).getItem());
				confirm.enabled(true);
				if (doubleClick) confirm();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (inside(mx, my, gridX, gridY, gridW, gridH)) {
			scrollRows = Math.max(0, Math.min(maxScroll(), scrollRows - (int) Math.signum(sy)));
			return true;
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		scrollRows = Math.min(scrollRows, maxScroll());
		Theme.rounded(g, gridX - 2, gridY - 2, gridW + 4, gridH + 4, 0xFF12151C);
		label(g, "Quantity", panelX + 12, panelY + panelH - 21);
		if (minecraft.player == null) {
			g.centeredText(font, "Join a world to browse recipes.", gridX + gridW / 2, gridY + gridH / 2, Theme.TEXT_DIM);
			return;
		}
		if (entries.isEmpty()) {
			String msg = recipes.isEmpty() && !showAllItems ? "No unlocked crafting recipes yet." : "Nothing matches your search.";
			g.centeredText(font, msg, gridX + gridW / 2, gridY + gridH / 2 - 4, Theme.TEXT_DIM);
		}
		int cols = columns();
		int start = scrollRows * cols;
		int end = Math.min(entries.size(), start + visibleRows() * cols);
		ItemStack hovered = null;
		for (int i = start; i < end; i++) {
			int rel = i - start;
			int cx = gridX + (rel % cols) * CELL;
			int cy = gridY + (rel / cols) * CELL;
			ItemStack stack = entries.get(i);
			String id = RecipeResolver.itemId(stack.getItem());
			boolean sel = id.equals(selectedId);
			boolean hov = inside(mouseX, mouseY, cx, cy, CELL, CELL);
			if (sel) Theme.rounded(g, cx, cy, CELL, CELL, Theme.SURFACE_SELECTED);
			else if (hov) Theme.rounded(g, cx, cy, CELL, CELL, Theme.SURFACE_HOVER);
			g.item(stack, cx + 2, cy + 2);
			if (showAllItems && !recipes.containsKey(id)) g.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, 0x99101318);
			if (hov) hovered = stack;
		}
		if (maxScroll() > 0) {
			int barH = Math.max(12, gridH * visibleRows() / (visibleRows() + maxScroll()));
			int barY = gridY + (gridH - barH) * scrollRows / maxScroll();
			g.fill(gridX + gridW, barY, gridX + gridW + 2, barY + barH, 0xFF3A4252);
		}
		if (infoW > 40) drawInfo(g);
		if (hovered != null) {
			String id = RecipeResolver.itemId(hovered.getItem());
			g.setTooltipForNextFrame(font, Component.literal(hovered.getHoverName().getString()
					+ (recipes.containsKey(id) ? "" : "\n(no unlocked crafting recipe)")), mouseX, mouseY);
		}
	}

	private void drawInfo(GuiGraphicsExtractor g) {
		int x = infoX;
		int y = gridY - 2;
		int h = gridH + 4;
		Theme.rounded(g, x, y, infoW, h, 0xFF12151C);
		if (selectedId == null) {
			g.textWithWordWrap(font, Component.literal("Select an item to see its recipe."), x + 6, y + 8, infoW - 12, Theme.TEXT_DIM, false);
			return;
		}
		Identifier rid = Identifier.tryParse(selectedId);
		ItemStack icon = rid != null && BuiltInRegistries.ITEM.containsKey(rid) ? new ItemStack(BuiltInRegistries.ITEM.getValue(rid)) : ItemStack.EMPTY;
		g.item(icon, x + 6, y + 6);
		g.text(font, ellipsize(icon.getHoverName().getString(), infoW - 32), x + 26, y + 10, Theme.TEXT, false);
		int ty = y + 28;
		RecipeResolver.CraftRecipe r = recipes.get(selectedId);
		if (r == null) {
			g.textWithWordWrap(font, Component.literal("No unlocked crafting recipe. Unlock it in your recipe book first, or the macro will report it as unavailable."),
					x + 6, ty, infoW - 12, Theme.WARNING, false);
			return;
		}
		int crafts = IngredientPlanner.craftsNeeded(quantity, r.resultCount());
		g.text(font, "Makes " + r.resultCount() + " per craft", x + 6, ty, Theme.TEXT_DIM, false);
		ty += 11;
		g.text(font, crafts + " craft" + (crafts == 1 ? "" : "s") + " -> " + crafts * r.resultCount() + " items", x + 6, ty, Theme.TEXT_DIM, false);
		ty += 11;
		g.text(font, r.fits2x2() ? "Fits inventory grid (2x2)" : "Needs crafting table (3x3)", x + 6, ty, Theme.TEXT_FAINT, false);
		ty += 14;
		g.text(font, "Ingredients (have / need)", x + 6, ty, Theme.TEXT, false);
		ty += 12;
		Map<Set<String>, Integer> totals = IngredientPlanner.totalRequirements(r.slots(), crafts);
		for (Map.Entry<Set<String>, Integer> e : totals.entrySet()) {
			if (ty > y + h - 18) break;
			String first = e.getKey().iterator().next();
			Identifier id = Identifier.tryParse(first);
			if (id != null && BuiltInRegistries.ITEM.containsKey(id)) g.item(new ItemStack(BuiltInRegistries.ITEM.getValue(id)), x + 6, ty - 4);
			int have = 0;
			for (String choice : e.getKey()) have += inventory.getOrDefault(choice, 0);
			int need = e.getValue();
			String name = id != null && BuiltInRegistries.ITEM.containsKey(id) ? new ItemStack(BuiltInRegistries.ITEM.getValue(id)).getHoverName().getString() : first;
			if (e.getKey().size() > 1) name += " (any)";
			g.text(font, ellipsize(name, infoW - 80), x + 26, ty, Theme.TEXT, false);
			String counts = have + "/" + need;
			g.text(font, counts, x + infoW - 6 - font.width(counts), ty, have >= need ? Theme.SUCCESS : Theme.DANGER, false);
			ty += 18;
		}
	}
}
