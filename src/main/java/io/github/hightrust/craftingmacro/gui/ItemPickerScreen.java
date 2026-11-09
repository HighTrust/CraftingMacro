package io.github.hightrust.craftingmacro.gui;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.hightrust.craftingmacro.crafting.InventoryAnalyzer;
import io.github.hightrust.craftingmacro.crafting.RecipeResolver;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Searchable item grid. Items you are carrying are listed first and marked with a green dot. */
public final class ItemPickerScreen extends BaseScreen {
	private static final int CELL = 20;

	private final Consumer<String> onPick;
	private final List<ItemStack> entries = new ArrayList<>();
	private Set<String> owned = Set.of();
	private Map<String, Integer> counts = Map.of();
	private EditBox search;
	private int scrollRows;
	private int gridX;
	private int gridY;
	private int gridW;
	private int gridH;
	private final long openedAt = System.currentTimeMillis();

	public ItemPickerScreen(Screen parent, Consumer<String> onPick) {
		super("Choose Item", parent);
		this.onPick = onPick;
	}

	@Override
	protected int preferredWidth() {
		return 300;
	}

	@Override
	protected int preferredHeight() {
		return 240;
	}

	@Override
	protected void build() {
		if (minecraft.player != null) {
			counts = InventoryAnalyzer.counts(minecraft.player, null);
			owned = new LinkedHashSet<>(counts.keySet());
		}
		gridX = panelX + 8;
		gridY = panelY + 52;
		gridW = (panelW - 16) / CELL * CELL;
		gridH = (panelY + panelH - 10 - gridY) / CELL * CELL;
		search = textField(gridX, panelY + 30, panelW - 16, search == null ? "" : search.getValue(), 64, "Search items...", null);
		search.setResponder(s -> {
			scrollRows = 0;
			refilter();
		});
		setInitialFocus(search);
		refilter();
	}

	private void refilter() {
		entries.clear();
		String q = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT).trim();
		List<ItemStack> rest = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			if (item == Items.AIR) continue;
			ItemStack stack = new ItemStack(item);
			String id = RecipeResolver.itemId(item);
			if (!q.isEmpty() && !id.contains(q) && !stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) continue;
			if (owned.contains(id)) entries.add(stack);
			else rest.add(stack);
		}
		entries.addAll(rest);
	}

	private int cols() {
		return Math.max(1, gridW / CELL);
	}

	private int maxScroll() {
		int rows = (entries.size() + cols() - 1) / cols();
		return Math.max(0, rows - gridH / CELL);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && inside(event.x(), event.y(), gridX, gridY, gridW, gridH)) {
			int idx = ((int) (event.y() - gridY) / CELL + scrollRows) * cols() + (int) (event.x() - gridX) / CELL;
			if (idx >= 0 && idx < entries.size()) {
				String id = RecipeResolver.itemId(entries.get(idx).getItem());
				onClose();
				onPick.accept(id);
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
		Theme.rounded(g, gridX - 2, gridY - 2, gridW + 4, gridH + 4, 0xFF12151C);
		long now = System.currentTimeMillis();
		int cols = cols();
		int start = scrollRows * cols;
		int end = Math.min(entries.size(), start + (gridH / CELL) * cols);
		ItemStack hovered = null;
		for (int i = start; i < end; i++) {
			int rel = i - start;
			int x = gridX + (rel % cols) * CELL;
			int y = gridY + (rel / cols) * CELL;
			float t = Math.max(0f, Math.min(1f, (now - openedAt - (rel / cols) * 25) / 160f));
			if (t <= 0f) continue;
			ItemStack stack = entries.get(i);
			boolean hov = inside(mouseX, mouseY, x, y, CELL, CELL);
			if (hov) {
				Theme.rounded(g, x, y, CELL, CELL, Theme.SURFACE_HOVER);
				hovered = stack;
			}
			int offset = Math.round((1f - t) * 6);
			g.item(stack, x + 2, y + 2 + offset);
			if (owned.contains(RecipeResolver.itemId(stack.getItem()))) g.fill(x + CELL - 4, y + 1, x + CELL - 1, y + 4, Theme.SUCCESS);
		}
		if (hovered != null) {
			int have = counts.getOrDefault(RecipeResolver.itemId(hovered.getItem()), 0);
			g.setTooltipForNextFrame(font, net.minecraft.network.chat.Component.literal(hovered.getHoverName().getString()
					+ (have > 0 ? "\nYou have " + have : "")), mouseX, mouseY);
		}
	}
}
