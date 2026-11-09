package io.github.hightrust.craftingmacro.gui;

import io.github.hightrust.craftingmacro.MacroManager;
import io.github.hightrust.craftingmacro.gui.widget.FlatButton;
import io.github.hightrust.craftingmacro.notify.CraftLog;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Everything the mod has crafted: totals, runs, and the ingredients that were used. */
public final class StatsScreen extends BaseScreen {
	private static final int ROW_H = 34;

	private final long openedAt = System.currentTimeMillis();
	private List<Map.Entry<String, CraftLog.ItemStats>> rows = new ArrayList<>();
	private long maxCrafted = 1;
	private int scroll;
	private int listY;
	private int listH;

	public StatsScreen(Screen parent) {
		super("Crafting Stats", parent);
	}

	@Override
	protected int preferredWidth() {
		return 320;
	}

	@Override
	protected int preferredHeight() {
		return 260;
	}

	@Override
	protected void build() {
		rows = new ArrayList<>(MacroManager.get().craftLog().stats().entrySet());
		rows.sort((a, b) -> Long.compare(b.getValue().crafted, a.getValue().crafted));
		maxCrafted = 1;
		for (var r : rows) maxCrafted = Math.max(maxCrafted, r.getValue().crafted);
		listY = panelY + 50;
		listH = panelY + panelH - 32 - listY;
		button(panelX + panelW - 68, panelY + panelH - 26, 60, "Done", FlatButton.Style.PRIMARY, this::onClose);
		button(panelX + 8, panelY + panelH - 26, 60, "Reset", FlatButton.Style.DANGER,
				() -> minecraft.gui.setScreen(new ConfirmDialog(this, "Reset stats", "Clear all crafting statistics?", "Reset", () -> {
					MacroManager.get().craftLog().reset();
					rebuildWidgets();
				})));
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		int max = Math.max(0, rows.size() - listH / ROW_H);
		scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(sy)));
		return true;
	}

	private static ItemStack stackOf(String id) {
		Identifier rid = Identifier.tryParse(id);
		return rid != null && BuiltInRegistries.ITEM.containsKey(rid) ? new ItemStack(BuiltInRegistries.ITEM.getValue(rid)) : ItemStack.EMPTY;
	}

	/** Numbers count up when the screen opens. */
	private long countUp(long value, int index) {
		float t = Math.max(0f, Math.min(1f, (System.currentTimeMillis() - openedAt - index * 60) / 600f));
		float ease = 1f - (1f - t) * (1f - t);
		return Math.round(value * ease);
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = panelX + 8;
		int w = panelW - 16;
		long total = MacroManager.get().craftLog().totalCrafted();
		g.text(font, "Total crafted: ", x, panelY + 32, Theme.TEXT_DIM, false);
		g.text(font, String.valueOf(countUp(total, 0)), x + font.width("Total crafted: "), panelY + 32, Theme.SUCCESS, false);
		String kinds = rows.size() + " item type" + (rows.size() == 1 ? "" : "s");
		g.text(font, kinds, x + w - font.width(kinds), panelY + 32, Theme.TEXT_FAINT, false);

		if (rows.isEmpty()) {
			g.centeredText(font, "Nothing crafted yet", panelX + panelW / 2, listY + listH / 2 - 4, Theme.TEXT_FAINT);
			return;
		}
		g.enableScissor(x, listY, x + w, listY + listH);
		long now = System.currentTimeMillis();
		for (int i = scroll; i < rows.size(); i++) {
			int y = listY + (i - scroll) * ROW_H;
			if (y > listY + listH) break;
			var entry = rows.get(i);
			CraftLog.ItemStats s = entry.getValue();
			float t = Math.max(0f, Math.min(1f, (now - openedAt - (i - scroll) * 60) / 300f));
			int slide = Math.round((1f - t) * 30);
			Theme.rounded(g, x + slide, y, w, ROW_H - 3, Theme.SURFACE);
			ItemStack stack = stackOf(entry.getKey());
			g.item(stack, x + 6 + slide, y + 7);
			String name = stack.isEmpty() ? entry.getKey() : stack.getHoverName().getString();
			g.text(font, ellipsize(name, w - 110), x + 28 + slide, y + 4, Theme.TEXT, false);
			String made = countUp(s.crafted, i) + " made";
			g.text(font, made, x + w - 6 - font.width(made) + slide, y + 4, Theme.SUCCESS, false);
			// progress bar relative to the most crafted item
			int barW = w - 34 - 6;
			int filled = (int) (barW * (countUp(s.crafted, i) / (double) maxCrafted));
			g.fill(x + 28 + slide, y + 15, x + 28 + barW + slide, y + 17, 0xFF11141A);
			g.fillGradient(x + 28 + slide, y + 15, x + 28 + filled + slide, y + 17, Theme.ACCENT, Theme.SUCCESS);
			StringBuilder used = new StringBuilder("Used: ");
			int n = 0;
			for (var u : s.used.entrySet()) {
				if (n++ > 0) used.append(", ");
				ItemStack us = stackOf(u.getKey());
				used.append(u.getValue()).append("x ").append(us.isEmpty() ? u.getKey() : us.getHoverName().getString());
			}
			if (n == 0) used.append("-");
			g.text(font, ellipsize(used.toString(), w - 34), x + 28 + slide, y + 20, Theme.TEXT_FAINT, false);
		}
		g.disableScissor();
	}
}
