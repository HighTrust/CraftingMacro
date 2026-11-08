package io.github.hightrust.craftingmacro.gui;

import io.github.hightrust.craftingmacro.MacroManager;
import io.github.hightrust.craftingmacro.exec.MacroRun;
import io.github.hightrust.craftingmacro.gui.widget.FlatButton;
import io.github.hightrust.craftingmacro.input.KeybindManager;
import io.github.hightrust.craftingmacro.macro.KeybindConflicts;
import io.github.hightrust.craftingmacro.macro.Macro;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main screen: a compact list of macros. Click a row to edit it; click the switch on the
 * right of a row to enable/disable it.
 */
public final class MacroListScreen extends BaseScreen {
	private static final int ROW_H = 24;
	/** Width of the clickable on/off area on the right of each row. */
	private static final int SWITCH_ZONE = 44;
	private static String rememberedSearch = "";

	private EditBox search;
	private final List<Macro> filtered = new ArrayList<>();
	private final Map<String, Float> rowHover = new HashMap<>();
	private double scroll;
	private FlatButton stopBtn;

	private int listX;
	private int listY;
	private int listW;
	private int listH;

	public MacroListScreen(Screen parent) {
		super("Macros", parent);
	}

	@Override
	protected int preferredWidth() {
		return 320;
	}

	@Override
	protected int preferredHeight() {
		return 250;
	}

	private MacroManager mgr() {
		return MacroManager.get();
	}

	@Override
	protected void build() {
		listX = panelX + 8;
		listW = panelW - 16;

		// header: profile + settings
		String profile = mgr().activeProfile().name;
		int profW = Math.min(100, font.width(profile) + 14);
		button(panelX + panelW - 8 - 52, panelY + 3, 52, "Settings", FlatButton.Style.GHOST,
				() -> minecraft.gui.setScreen(new SettingsScreen(this))).tooltip("Settings");
		button(panelX + panelW - 8 - 52 - 4 - profW, panelY + 3, profW, profile, FlatButton.Style.GHOST,
				() -> minecraft.gui.setScreen(new ProfileScreen(this))).tooltip("Profile (click to manage, import, export)");

		int top = panelY + 30;
		search = textField(listX, top, listW, rememberedSearch, 64, "Search...", null);
		search.setResponder(s -> {
			rememberedSearch = s;
			refilter();
		});
		listY = top + 22;
		int bottom = panelY + panelH - 26;
		listH = bottom - 6 - listY;

		int half = (listW - 4) / 2;
		button(listX, bottom, half, "+ New Macro", FlatButton.Style.PRIMARY,
				() -> minecraft.gui.setScreen(new MacroEditorScreen(this, null)));
		stopBtn = button(listX + half + 4, bottom, half, "Stop All", FlatButton.Style.DANGER,
				() -> mgr().stopAll("Stopped from menu")).tooltip("Stop every running macro ("
				+ KeybindManager.stopAllKey().getTranslatedKeyMessage().getString() + ")");
		refilter();
	}

	private void refilter() {
		filtered.clear();
		for (Macro m : mgr().macros()) {
			if (m.matchesSearch(search == null ? "" : search.getValue())) filtered.add(m);
		}
		clampScroll();
	}

	void onMacroSaved(Macro saved) {
		refilter();
	}

	private void clampScroll() {
		int max = Math.max(0, filtered.size() * ROW_H - listH);
		scroll = Math.max(0, Math.min(max, scroll));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (event.button() == 0 && inside(mx, my, listX, listY, listW, listH)) {
			int idx = (int) ((my - listY + scroll) / ROW_H);
			if (idx >= 0 && idx < filtered.size()) {
				Macro m = filtered.get(idx);
				if (mx >= listX + listW - SWITCH_ZONE) {
					m.enabled = !m.enabled;
					if (!m.enabled) mgr().executor().cancel(m.id, "Disabled");
					mgr().markDirty();
				} else {
					minecraft.gui.setScreen(new MacroEditorScreen(this, m));
				}
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (inside(mx, my, listX, listY, listW, listH)) {
			scroll -= sy * ROW_H;
			clampScroll();
			return true;
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}

	@Override
	public void tick() {
		stopBtn.enabled(!mgr().executor().isIdle());
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		if (filtered.isEmpty()) {
			String msg = mgr().macros().isEmpty() ? "No macros yet" : "Nothing matches";
			g.centeredText(font, msg, listX + listW / 2, listY + listH / 2 - 4, Theme.TEXT_FAINT);
		}
		List<Macro> all = mgr().macros();
		boolean inList = inside(mouseX, mouseY, listX, listY, listW, listH);
		g.enableScissor(listX, listY, listX + listW, listY + listH);
		for (int i = 0; i < filtered.size(); i++) {
			int y = listY + i * ROW_H - (int) scroll;
			if (y + ROW_H < listY || y > listY + listH) continue;
			Macro m = filtered.get(i);
			boolean hovered = inList && inside(mouseX, mouseY, listX, y, listW, ROW_H);
			float h = Theme.approach(rowHover.getOrDefault(m.id, 0f), hovered ? 1f : 0f, 0.3f);
			rowHover.put(m.id, h);
			Theme.rounded(g, listX, y + 1, listW, ROW_H - 2, Theme.lerpColor(Theme.SURFACE, Theme.SURFACE_HOVER, h));

			boolean running = mgr().executor().isRunning(m.id);
			if (running) Theme.rounded(g, listX + 4, y + 9, 4, 4, Theme.ACCENT);

			// key badge
			String key = KeybindManager.label(m.key);
			boolean conflict = KeybindConflicts.hasConflict(m, all);
			int badgeW = font.width(key) + 8;
			int badgeX = listX + listW - SWITCH_ZONE - badgeW;
			Theme.rounded(g, badgeX, y + 6, badgeW, 12, conflict ? 0xFF4A2A10 : 0xFF11141A);
			g.text(font, key, badgeX + 4, y + 8, conflict ? Theme.WARNING : Theme.TEXT_DIM, false);

			g.text(font, ellipsize(m.name, badgeX - listX - 18), listX + 12, y + 8, m.enabled ? Theme.TEXT : Theme.TEXT_FAINT, false);

			// switch
			int sx = listX + listW - 8 - 22;
			boolean swHover = hovered && mouseX >= listX + listW - SWITCH_ZONE;
			int track = m.enabled ? Theme.ACCENT : Theme.SWITCH_OFF;
			if (swHover) track = Theme.lerpColor(track, 0xFFFFFFFF, 0.15f);
			Theme.rounded(g, sx, y + 6, 22, 12, track);
			Theme.rounded(g, m.enabled ? sx + 12 : sx + 2, y + 8, 8, 8, 0xFFF5F7FA);
		}
		g.disableScissor();
		int content = filtered.size() * ROW_H;
		if (content > listH) {
			int barH = Math.max(16, listH * listH / content);
			int barY = listY + (int) ((listH - barH) * (scroll / (content - listH)));
			g.fill(listX + listW + 2, barY, listX + listW + 4, barY + barH, 0xFF3A4252);
		}
		drawStatus(g);
	}

	private void drawStatus(GuiGraphicsExtractor g) {
		List<MacroRun> runs = mgr().executor().activeRuns();
		String text;
		int color;
		if (!runs.isEmpty()) {
			MacroRun r = runs.get(0);
			text = "Running " + r.macro().name + " (" + r.currentStepNumber() + "/" + r.totalSteps() + ")";
			color = Theme.ACCENT;
		} else {
			text = mgr().notifier().lastStatus();
			color = switch (mgr().notifier().lastStatusLevel()) {
				case SUCCESS -> Theme.SUCCESS;
				case WARN -> Theme.WARNING;
				case ERROR -> Theme.DANGER;
				default -> Theme.TEXT_FAINT;
			};
		}
		if (text != null && !text.isEmpty()) {
			g.text(font, ellipsize(text, panelW - 20), panelX + 8, panelY + panelH + 4, color, false);
		}
	}

	@Override
	public void removed() {
		mgr().saveNow();
	}
}
