package io.github.hightrust.craftingmacro.gui;

import io.github.hightrust.craftingmacro.MacroManager;
import io.github.hightrust.craftingmacro.exec.MacroRun;
import io.github.hightrust.craftingmacro.gui.widget.FlatButton;
import io.github.hightrust.craftingmacro.input.KeybindManager;
import io.github.hightrust.craftingmacro.macro.KeybindConflicts;
import io.github.hightrust.craftingmacro.macro.Macro;
import io.github.hightrust.craftingmacro.macro.MacroStep;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Main screen: searchable macro list, details, and management actions. */
public final class MacroListScreen extends BaseScreen {
	private static final int ROW_H = 26;
	private static String rememberedSearch = "";
	private static String rememberedSelection;

	private EditBox search;
	private final List<Macro> filtered = new ArrayList<>();
	private final Map<String, Float> rowHover = new HashMap<>();
	private String selectedId = rememberedSelection;
	private double scroll;

	private FlatButton editBtn;
	private FlatButton dupBtn;
	private FlatButton deleteBtn;
	private FlatButton runBtn;
	private FlatButton stopBtn;

	// list geometry
	private int listX;
	private int listY;
	private int listW;
	private int listH;
	private int detailX;
	private int detailW;

	public MacroListScreen(Screen parent) {
		super("Crafting Macro", parent);
	}

	@Override
	protected int preferredWidth() {
		return 600;
	}

	@Override
	protected int preferredHeight() {
		return 340;
	}

	private MacroManager mgr() {
		return MacroManager.get();
	}

	@Override
	protected void build() {
		int top = panelY + 32;
		int bottom = panelY + panelH - 26;
		boolean compact = compact();

		// header buttons
		String profile = "Profile: " + mgr().activeProfile().name;
		int profW = Math.min(140, font.width(profile) + 16);
		button(panelX + panelW - 8 - 60, panelY + 4, 60, "Settings", FlatButton.Style.GHOST,
				() -> minecraft.gui.setScreen(new SettingsScreen(this))).tooltip("Global mod settings");
		button(panelX + panelW - 8 - 60 - 4 - profW, panelY + 4, profW, profile, FlatButton.Style.GHOST,
				() -> minecraft.gui.setScreen(new ProfileScreen(this))).tooltip("Manage, import and export profiles");

		listX = panelX + 8;
		listW = compact ? panelW - 16 : (int) ((panelW - 16) * 0.55f);
		detailX = listX + listW + 8;
		detailW = panelX + panelW - 8 - detailX;

		search = textField(listX, top, listW, rememberedSearch, 64, "Search macros...", null);
		search.setResponder(s -> {
			rememberedSearch = s;
			refilter();
		});
		listY = top + 22;
		listH = bottom - 18 - listY;

		// bottom bar
		int x = panelX + 8;
		int by = bottom;
		int bw = compact ? (panelW - 16 - 4 * 4) / 5 : 74;
		button(x, by, compact ? bw : 96, "+ Create Macro", FlatButton.Style.PRIMARY, this::create)
				.tooltip("Create a new macro");
		x += (compact ? bw : 96) + 4;
		editBtn = button(x, by, bw, "Edit", FlatButton.Style.NORMAL, this::editSelected).tooltip("Edit the selected macro (or double-click it)");
		x += bw + 4;
		dupBtn = button(x, by, bw, "Duplicate", FlatButton.Style.NORMAL, this::duplicateSelected).tooltip("Copy the selected macro");
		x += bw + 4;
		deleteBtn = button(x, by, bw, "Delete", FlatButton.Style.DANGER, this::deleteSelected).tooltip("Delete the selected macro (asks first)");
		x += bw + 4;
		stopBtn = button(compact ? x : panelX + panelW - 8 - 80, by, compact ? bw : 80, "Stop All", FlatButton.Style.DANGER,
				() -> mgr().stopAll("Stopped from menu")).tooltip("Emergency stop for every running macro ("
				+ KeybindManager.stopAllKey().getTranslatedKeyMessage().getString() + ")");

		if (!compact) {
			runBtn = button(detailX + 8, bottom - 22 - 18 + 14, Math.min(100, detailW - 16), "Run now", FlatButton.Style.NORMAL, this::runSelected)
					.tooltip("Run the selected macro once now (closes this menu)");
		}
		refilter();
	}

	private void refilter() {
		filtered.clear();
		for (Macro m : mgr().macros()) {
			if (m.matchesSearch(search == null ? "" : search.getValue())) filtered.add(m);
		}
		if (selected() == null && !filtered.isEmpty()) selectedId = filtered.get(0).id;
		clampScroll();
		updateButtons();
	}

	private Macro selected() {
		if (selectedId == null) return null;
		for (Macro m : filtered) if (Objects.equals(m.id, selectedId)) return m;
		return null;
	}

	private void select(Macro m) {
		selectedId = m == null ? null : m.id;
		rememberedSelection = selectedId;
		updateButtons();
	}

	private void updateButtons() {
		boolean has = selected() != null;
		if (editBtn != null) editBtn.enabled(has);
		if (dupBtn != null) dupBtn.enabled(has);
		if (deleteBtn != null) deleteBtn.enabled(has);
		if (runBtn != null) runBtn.enabled(has && selected().enabled);
	}

	// ------------------------------------------------------------------ actions

	private void create() {
		minecraft.gui.setScreen(new MacroEditorScreen(this, null));
	}

	private void editSelected() {
		Macro m = selected();
		if (m != null) minecraft.gui.setScreen(new MacroEditorScreen(this, m));
	}

	private void duplicateSelected() {
		Macro m = selected();
		if (m == null) return;
		Macro copy = m.copy(true);
		copy.name = (m.name + " (copy)").length() > 48 ? m.name : m.name + " (copy)";
		List<Macro> list = mgr().macros();
		list.add(list.indexOf(m) + 1, copy);
		mgr().markDirty();
		select(copy);
		refilter();
	}

	private void deleteSelected() {
		Macro m = selected();
		if (m == null) return;
		minecraft.gui.setScreen(new ConfirmDialog(this, "Delete macro", "Delete \"" + m.name + "\"? This cannot be undone.", "Delete", () -> {
			mgr().executor().cancel(m.id, "Deleted");
			mgr().macros().remove(m);
			mgr().markDirty();
			selectedId = null;
			refilter();
		}));
	}

	private void runSelected() {
		Macro m = selected();
		if (m == null) return;
		onClose();
		mgr().trigger(m, false);
	}

	/** Called by the editor after saving. */
	void onMacroSaved(Macro saved) {
		select(saved);
		refilter();
		scrollTo(saved);
	}

	private void scrollTo(Macro m) {
		int idx = filtered.indexOf(m);
		if (idx < 0) return;
		int top = idx * ROW_H;
		if (top < scroll) scroll = top;
		else if (top + ROW_H > scroll + listH) scroll = top + ROW_H - listH;
		clampScroll();
	}

	private void clampScroll() {
		int max = Math.max(0, filtered.size() * ROW_H - listH);
		scroll = Math.max(0, Math.min(max, scroll));
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (event.button() == 0 && inside(mx, my, listX, listY, listW, listH)) {
			int idx = (int) ((my - listY + scroll) / ROW_H);
			if (idx >= 0 && idx < filtered.size()) {
				Macro m = filtered.get(idx);
				int rowY = listY + idx * ROW_H - (int) scroll;
				int switchX = listX + listW - 8 - 22;
				if (inside(mx, my, switchX - 2, rowY + 5, 26, 16)) {
					m.enabled = !m.enabled;
					mgr().markDirty();
					if (!m.enabled) mgr().executor().cancel(m.id, "Disabled");
					select(m);
					return true;
				}
				select(m);
				if (doubleClick) editSelected();
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
		updateButtons();
		stopBtn.enabled(!mgr().executor().isIdle());
	}

	// ------------------------------------------------------------------ drawing

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		drawList(g, mouseX, mouseY);
		if (!compact()) drawDetails(g);
		drawStatus(g);
	}

	private void drawList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		Theme.rounded(g, listX, listY, listW, listH, 0xFF12151C);
		if (filtered.isEmpty()) {
			String msg = mgr().macros().isEmpty() ? "No macros yet. Click \"+ Create Macro\"." : "No macros match your search.";
			g.centeredText(font, msg, listX + listW / 2, listY + listH / 2 - 4, Theme.TEXT_DIM);
			return;
		}
		List<Macro> all = mgr().macros();
		g.enableScissor(listX, listY, listX + listW, listY + listH);
		for (int i = 0; i < filtered.size(); i++) {
			int y = listY + i * ROW_H - (int) scroll;
			if (y + ROW_H < listY || y > listY + listH) continue;
			Macro m = filtered.get(i);
			boolean hovered = inside(mouseX, mouseY, listX, Math.max(y, listY), listW, ROW_H) && inside(mouseX, mouseY, listX, listY, listW, listH);
			float h = Theme.approach(rowHover.getOrDefault(m.id, 0f), hovered ? 1f : 0f, 0.3f);
			rowHover.put(m.id, h);
			boolean sel = Objects.equals(m.id, selectedId);
			int bg = sel ? Theme.SURFACE_SELECTED : Theme.lerpColor(Theme.SURFACE, Theme.SURFACE_HOVER, h);
			Theme.rounded(g, listX + 3, y + 2, listW - 6, ROW_H - 3, bg);
			if (sel) g.fill(listX + 3, y + 5, listX + 5, y + ROW_H - 4, Theme.ACCENT);

			boolean running = mgr().executor().isRunning(m.id);
			int nameColor = m.enabled ? Theme.TEXT : Theme.TEXT_FAINT;
			int textX = listX + 10;
			// key badge
			String key = KeybindManager.label(m.key);
			int badgeW = font.width(key) + 8;
			int switchX = listX + listW - 8 - 22;
			int badgeX = switchX - 6 - badgeW;
			boolean conflict = KeybindConflicts.hasConflict(m, all);
			Theme.rounded(g, badgeX, y + 7, badgeW, 12, conflict ? 0xFF4A2A10 : 0xFF2A3140);
			g.text(font, key, badgeX + 4, y + 9, conflict ? Theme.WARNING : (m.key.isBound() ? Theme.TEXT : Theme.TEXT_FAINT), false);

			String name = ellipsize(m.name, badgeX - textX - 6);
			g.text(font, name, textX, y + 5, nameColor, false);
			String sub = (running ? "Running... " : "") + m.type.displayName() + " - " + m.steps.size() + " step" + (m.steps.size() == 1 ? "" : "s");
			g.text(font, ellipsize(sub, badgeX - textX - 6), textX, y + 15, running ? Theme.ACCENT : Theme.TEXT_DIM, false);

			// enable switch (drawn; click handled in mouseClicked)
			int track = m.enabled ? Theme.ACCENT : Theme.SWITCH_OFF;
			Theme.rounded(g, switchX, y + 7, 22, 12, track);
			Theme.rounded(g, m.enabled ? switchX + 12 : switchX + 2, y + 9, 8, 8, 0xFFF5F7FA);
			if (inside(mouseX, mouseY, switchX - 2, y + 5, 26, 16) && hovered) {
				g.setTooltipForNextFrame(font, Component.literal(m.enabled ? "Enabled - click to disable" : "Disabled - click to enable"), mouseX, mouseY);
			}
			if (conflict && inside(mouseX, mouseY, badgeX, y + 7, badgeW, 12)) {
				g.setTooltipForNextFrame(font, Component.literal("Keybind conflict: another enabled macro uses " + key), mouseX, mouseY);
			}
		}
		g.disableScissor();
		// scrollbar
		int content = filtered.size() * ROW_H;
		if (content > listH) {
			int barH = Math.max(16, listH * listH / content);
			int barY = listY + (int) ((listH - barH) * (scroll / (content - listH)));
			g.fill(listX + listW - 3, barY, listX + listW - 1, barY + barH, 0xFF3A4252);
		}
	}

	private void drawDetails(GuiGraphicsExtractor g) {
		int x = detailX;
		int y = listY - 22;
		int h = listH + 22;
		Theme.rounded(g, x, y, detailW, h, 0xFF12151C);
		Macro m = selected();
		if (m == null) {
			g.centeredText(font, "Select a macro", x + detailW / 2, y + h / 2 - 4, Theme.TEXT_DIM);
			return;
		}
		int tx = x + 8;
		int ty = y + 8;
		int w = detailW - 16;
		g.text(font, ellipsize(m.name, w), tx, ty, Theme.TEXT, false);
		ty += 12;
		if (m.description != null && !m.description.isBlank()) {
			ty = g.textWithWordWrap(font, Component.literal(m.description), tx, ty, w, Theme.TEXT_DIM, false) + 4;
			ty = Math.min(ty, y + 70);
		}
		g.text(font, "Key: " + KeybindManager.label(m.key) + "   Type: " + m.type.displayName(), tx, ty, Theme.TEXT_DIM, false);
		ty += 11;
		String opts = "Cooldown " + m.cooldownMs + "ms" + (m.repeatCount > 1 ? ", x" + m.repeatCount : "") + (m.holdToRepeat ? ", hold" : "");
		g.text(font, ellipsize(opts, w), tx, ty, Theme.TEXT_DIM, false);
		ty += 11;
		if (!m.servers.isEmpty()) {
			g.text(font, ellipsize("Servers: " + String.join(", ", m.servers), w), tx, ty, Theme.TEXT_DIM, false);
			ty += 11;
		}
		List<Macro> conflicts = KeybindConflicts.conflictingMacros(m.key, mgr().macros(), m);
		if (m.enabled && !conflicts.isEmpty()) {
			g.text(font, ellipsize("Conflicts with: " + conflicts.get(0).name, w), tx, ty, Theme.WARNING, false);
			ty += 11;
		}
		ty += 4;
		g.fill(tx, ty, tx + w, ty + 1, Theme.PANEL_BORDER);
		ty += 5;
		int limitY = y + h - 30;
		for (int i = 0; i < m.steps.size(); i++) {
			if (ty + 10 > limitY) {
				g.text(font, "+" + (m.steps.size() - i) + " more...", tx, ty, Theme.TEXT_FAINT, false);
				break;
			}
			MacroStep s = m.steps.get(i);
			g.text(font, (i + 1) + ".", tx, ty, Theme.TEXT_FAINT, false);
			g.text(font, ellipsize(s.summary(), w - 14), tx + 14, ty, Theme.TEXT, false);
			ty += 11;
		}
	}

	private void drawStatus(GuiGraphicsExtractor g) {
		int y = panelY + panelH - 40;
		String text;
		int color;
		List<MacroRun> runs = mgr().executor().activeRuns();
		if (!runs.isEmpty()) {
			MacroRun r = runs.get(0);
			text = "Running: " + r.macro().name + " (step " + r.currentStepNumber() + "/" + r.totalSteps() + ")"
					+ (runs.size() > 1 ? " +" + (runs.size() - 1) + " more" : "");
			color = Theme.ACCENT;
		} else if (!mgr().notifier().lastStatus().isEmpty()) {
			text = mgr().notifier().lastStatus();
			color = switch (mgr().notifier().lastStatusLevel()) {
				case SUCCESS -> Theme.SUCCESS;
				case WARN -> Theme.WARNING;
				case ERROR -> Theme.DANGER;
				default -> Theme.TEXT_DIM;
			};
		} else {
			text = "Press " + KeybindManager.openMenuKey().getTranslatedKeyMessage().getString() + " to open this menu. "
					+ mgr().macros().size() + " macro(s) in this profile.";
			color = Theme.TEXT_FAINT;
		}
		g.text(font, ellipsize(text, panelW - 16), panelX + 8, y, color, false);
	}

	@Override
	public void removed() {
		mgr().saveNow();
	}
}
