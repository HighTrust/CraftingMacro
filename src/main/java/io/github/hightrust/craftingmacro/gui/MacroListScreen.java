package io.github.hightrust.craftingmacro.gui;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.hightrust.craftingmacro.MacroManager;
import io.github.hightrust.craftingmacro.exec.MacroRun;
import io.github.hightrust.craftingmacro.gui.widget.FlatButton;
import io.github.hightrust.craftingmacro.input.KeybindManager;
import io.github.hightrust.craftingmacro.macro.KeybindConflicts;
import io.github.hightrust.craftingmacro.macro.Macro;
import io.github.hightrust.craftingmacro.macro.MacroStep;
import io.github.hightrust.craftingmacro.macro.MacroType;
import io.github.hightrust.craftingmacro.macro.TriggerMode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main manager screen. Left: a sidebar with macro categories and general pages. Right: a
 * search pill, a title and animated cards. Click a card to expand it (details, Edit, Run),
 * click its switch to enable/disable, right-click to edit straight away.
 */
public final class MacroListScreen extends BaseScreen {
	private static final int CARD_H = 34;
	private static final int EXPAND_H = 44;
	private static final int CARD_GAP = 5;
	private static final int NAV_H = 18;

	private enum Category {
		ALL("All Macros", Fonts.Icon.SPARKLES, null),
		COMMAND("Commands", Fonts.Icon.TERMINAL, MacroType.COMMAND),
		CRAFT("Crafting", Fonts.Icon.HAMMER, MacroType.CRAFT),
		SEQUENCE("Sequences", Fonts.Icon.WORKFLOW, MacroType.SEQUENCE);

		final String label;
		final int icon;
		final MacroType type;

		Category(String label, int icon, MacroType type) {
			this.label = label;
			this.icon = icon;
			this.type = type;
		}

		boolean matches(Macro m) {
			return type == null || m.type == type;
		}
	}

	private record NavItem(String label, int icon, Category category, Runnable action) {
	}

	private static String rememberedSearch = "";
	private static Category rememberedCategory = Category.ALL;

	private EditBox search;
	private Category category = rememberedCategory;
	private final List<Macro> filtered = new ArrayList<>();
	private final List<NavItem> nav = new ArrayList<>();
	private final Map<String, Float> cardHover = new HashMap<>();
	private final Map<String, Float> switchAnim = new HashMap<>();
	private final Map<String, Float> expandAnim = new HashMap<>();
	private final Map<String, Float> buttonHover = new HashMap<>();
	private final Map<Integer, Float> navHover = new HashMap<>();
	private String expandedId;
	private double scroll;
	private float scrollSmooth;
	private float navSelY = -1;
	private long listShownAt = System.currentTimeMillis();
	private FlatButton stopBtn;

	private int sideW;
	private int navTop;
	private int generalTop;
	private int mainX;
	private int mainW;
	private int listY;
	private int listH;

	public MacroListScreen(Screen parent) {
		super("Macros", parent);
	}

	@Override
	protected int preferredWidth() {
		return 540;
	}

	@Override
	protected int preferredHeight() {
		return 320;
	}

	private MacroManager mgr() {
		return MacroManager.get();
	}

	@Override
	protected void build() {
		sideW = panelW < 440 ? 92 : 116;
		mainX = panelX + sideW + 12;
		mainW = panelW - sideW - 24;
		navTop = panelY + 44;

		nav.clear();
		for (Category c : Category.values()) nav.add(new NavItem(c.label, c.icon, c, null));
		generalTop = navTop + nav.size() * NAV_H + 24;
		nav.add(new NavItem("Settings", Fonts.Icon.SETTINGS, null, () -> minecraft.gui.setScreen(new SettingsScreen(this))));
		nav.add(new NavItem("Stats", Fonts.Icon.CHART, null, () -> minecraft.gui.setScreen(new StatsScreen(this))));
		nav.add(new NavItem("Profiles", Fonts.Icon.USERS, null, () -> minecraft.gui.setScreen(new ProfileScreen(this))));
		nav.add(new NavItem("Theme", Fonts.Icon.PALETTE, null, this::cycleTheme));

		// top bar: search pill + actions
		int btnW = 56;
		int stopW = 44;
		int searchW = Math.min(220, mainW - btnW - stopW - 12);
		search = textField(mainX + 18, panelY + 15, searchW - 22, rememberedSearch, 64, "Search macros...", null);
		search.setBordered(false);
		search.setResponder(s -> {
			rememberedSearch = s;
			refilter(true);
		});
		button(mainX + mainW - btnW - stopW - 4, panelY + 11, btnW, "+ New", FlatButton.Style.PRIMARY,
				() -> minecraft.gui.setScreen(new MacroEditorScreen(this, null)));
		stopBtn = button(mainX + mainW - stopW, panelY + 11, stopW, "Stop", FlatButton.Style.DANGER,
				() -> mgr().stopAll("Stopped from menu")).tooltip("Stop every running macro ("
				+ KeybindManager.stopAllKey().getTranslatedKeyMessage().getString() + ")");

		listY = panelY + 62;
		listH = panelY + panelH - 10 - listY;
		refilter(false);
	}

	private int searchW() {
		return Math.min(220, mainW - 56 - 44 - 12);
	}

	private void refilter(boolean animate) {
		filtered.clear();
		for (Macro m : mgr().macros()) {
			if (category.matches(m) && m.matchesSearch(search == null ? "" : search.getValue())) filtered.add(m);
		}
		if (animate) listShownAt = System.currentTimeMillis();
		clampScroll();
	}

	void onMacroSaved(Macro saved) {
		refilter(false);
	}

	private void selectCategory(Category c) {
		if (c == category) return;
		category = c;
		rememberedCategory = c;
		expandedId = null;
		scroll = 0;
		refilter(true);
	}

	// ------------------------------------------------------------------ layout

	private float expandOf(Macro m) {
		return expandAnim.getOrDefault(m.id, 0f);
	}

	private int cardHeight(Macro m) {
		return CARD_H + Math.round(EXPAND_H * Theme.easeOut(expandOf(m)));
	}

	private int contentHeight() {
		int h = 0;
		for (Macro m : filtered) h += cardHeight(m) + CARD_GAP;
		return Math.max(0, h - CARD_GAP);
	}

	private void clampScroll() {
		int max = Math.max(0, contentHeight() - listH);
		scroll = Math.max(0, Math.min(max, scroll));
	}

	private int navY(int index) {
		return index < Category.values().length ? navTop + index * NAV_H
				: generalTop + (index - Category.values().length) * NAV_H;
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		int button = event.button();

		// sidebar
		if (button == InputConstants.MOUSE_BUTTON_LEFT && inside(mx, my, panelX + 6, navTop, sideW - 12, panelH)) {
			for (int i = 0; i < nav.size(); i++) {
				if (inside(mx, my, panelX + 6, navY(i), sideW - 12, NAV_H - 2)) {
					NavItem item = nav.get(i);
					if (item.category() != null) selectCategory(item.category());
					else item.action().run();
					return true;
				}
			}
			if (inside(mx, my, panelX + 6, panelY + panelH - 30, sideW - 12, 24)) {
				minecraft.gui.setScreen(new ProfileScreen(this));
				return true;
			}
		}

		// cards
		if (inside(mx, my, mainX, listY, mainW, listH)) {
			int y = listY - (int) scrollSmooth;
			for (Macro m : filtered) {
				int h = cardHeight(m);
				if (my >= y && my < y + h) {
					if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
						minecraft.gui.setScreen(new MacroEditorScreen(this, m));
						return true;
					}
					if (button != InputConstants.MOUSE_BUTTON_LEFT) return true;
					if (my < y + CARD_H) {
						if (mx >= mainX + mainW - 40) {
							m.enabled = !m.enabled;
							if (!m.enabled) mgr().executor().cancel(m.id, "Disabled");
							mgr().markDirty();
						} else {
							expandedId = m.id.equals(expandedId) ? null : m.id;
						}
					} else if (expandOf(m) > 0.9f) {
						int by = y + CARD_H + 22;
						if (my >= by && my < by + 14) {
							int bx = mainX + 10;
							if (mx >= bx && mx < bx + 44) {
								minecraft.gui.setScreen(new MacroEditorScreen(this, m));
							} else if (mx >= bx + 48 && mx < bx + 92 && m.enabled) {
								mgr().trigger(m, false);
							}
						}
					}
					return true;
				}
				y += h + CARD_GAP;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (inside(mx, my, mainX, listY, mainW, listH)) {
			scroll -= sy * 24;
			clampScroll();
			return true;
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}

	@Override
	public void tick() {
		stopBtn.enabled(!mgr().executor().isIdle());
	}

	// ------------------------------------------------------------------ drawing

	@Override
	protected void extractPanel(GuiGraphicsExtractor g) {
		Theme.panel(g, panelX, panelY, panelW, panelH, Theme.PANEL, Theme.PANEL_BORDER);
		// sidebar strip + divider
		g.fill(panelX + 1, panelY + 3, panelX + sideW, panelY + panelH - 3, Theme.SIDEBAR);
		g.fillGradient(panelX + sideW, panelY + 8, panelX + sideW + 1, panelY + panelH - 8,
				Theme.withAlpha(Theme.ACCENT, 0.5f), Theme.withAlpha(Theme.ACCENT2, 0.1f));
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		long now = System.currentTimeMillis();
		extractSidebar(g, mouseX, mouseY, now);
		extractTopBar(g, mouseX, mouseY);

		// title
		String title = category.label;
		g.pose().pushMatrix();
		g.pose().translate(mainX, panelY + 40);
		g.pose().scale(1.5f, 1.5f);
		g.text(font, Fonts.bold(title), 0, 0, Theme.TEXT, false);
		g.pose().popMatrix();
		String count = filtered.size() + (filtered.size() == 1 ? " macro" : " macros");
		g.text(font, count, mainX + mainW - font.width(count), panelY + 44, Theme.TEXT_FAINT, false);

		extractCards(g, mouseX, mouseY, now);
		drawStatus(g);
	}

	private void extractSidebar(GuiGraphicsExtractor g, int mouseX, int mouseY, long now) {
		int x = panelX + 6;
		int w = sideW - 12;
		// logo
		float shimmer = (float) (0.5 + 0.5 * Math.sin(now / 600.0));
		g.text(font, Fonts.icon(Fonts.Icon.SPARKLES), x + 1, panelY + 12, Theme.lerpColor(Theme.ACCENT, Theme.ACCENT2, shimmer), false);
		g.text(font, Fonts.bold(panelW < 440 ? "Macros" : "Crafting Macro"),
				x + 13, panelY + 13, Theme.ACCENT_HOVER, false);

		smallCaps(g, "MACROS", x + 2, navTop - 10);
		smallCaps(g, "GENERAL", x + 2, generalTop - 10);

		// sliding selection highlight
		int selIndex = category.ordinal();
		float target = navY(selIndex);
		navSelY = navSelY < 0 ? target : Theme.approach(navSelY, target, 0.25f);
		int sy = Math.round(navSelY);
		Theme.rounded(g, x, sy, w, NAV_H - 2, Theme.SURFACE_SELECTED);
		Theme.accentBar(g, x, sy + 3, NAV_H - 8, 1f);
		float pulse = (float) (0.65 + 0.35 * Math.sin(now / 300.0));
		Theme.rounded(g, x + w - 10, sy + 5, 6, 6, Theme.withAlpha(Theme.ACCENT, pulse));

		for (int i = 0; i < nav.size(); i++) {
			NavItem item = nav.get(i);
			int y = navY(i);
			boolean hovered = inside(mouseX, mouseY, x, y, w, NAV_H - 2);
			float h = Theme.approach(navHover.getOrDefault(i, 0f), hovered ? 1f : 0f, 0.3f);
			navHover.put(i, h);
			boolean selected = item.category() == category;
			if (!selected && h > 0.01f) Theme.rounded(g, x, y, w, NAV_H - 2, Theme.withAlpha(Theme.SURFACE_HOVER, h));
			int color = selected ? Theme.TEXT : Theme.lerpColor(Theme.TEXT_DIM, Theme.TEXT, h);
			int iconColor = selected ? Theme.ACCENT_HOVER : Theme.lerpColor(Theme.TEXT_FAINT, Theme.ACCENT, h);
			int slide = Math.round(2 * h);
			g.text(font, Fonts.icon(item.icon()), x + 5 + slide, y + 3, iconColor, false);
			String label = item.label();
			if (item.category() == null && label.equals("Theme")) label = "Theme: " + Theme.mode().displayName();
			g.text(font, ellipsize(label, w - 34), x + 18 + slide, y + 4, color, false);
			if (item.category() != null && !selected) {
				String n = Integer.toString(countIn(item.category()));
				g.text(font, n, x + w - 4 - font.width(n), y + 4, Theme.TEXT_FAINT, false);
			}
		}

		// profile chip
		int cy = panelY + panelH - 30;
		boolean chipHover = inside(mouseX, mouseY, x, cy, w, 24);
		Theme.rounded(g, x, cy, w, 24, chipHover ? Theme.SURFACE_HOVER : Theme.SURFACE);
		String profile = mgr().activeProfile().name;
		Theme.accentPill(g, x + 4, cy + 4, 16, 16, chipHover ? 1f : 0f);
		g.centeredText(font, profile.isEmpty() ? "?" : profile.substring(0, 1).toUpperCase(), x + 12, cy + 8, 0xFFFFFFFF);
		g.text(font, ellipsize(profile, w - 30), x + 25, cy + 3, Theme.TEXT, false);
		g.pose().pushMatrix();
		g.pose().translate(x + 25, cy + 14);
		g.pose().scale(0.75f, 0.75f);
		Theme.rounded(g, 1, 2, 5, 5, Theme.SUCCESS);
		g.text(font, "Active profile", 9, 0, Theme.TEXT_FAINT, false);
		g.pose().popMatrix();
	}

	private int countIn(Category c) {
		int n = 0;
		for (Macro m : mgr().macros()) if (c.matches(m)) n++;
		return n;
	}

	private void smallCaps(GuiGraphicsExtractor g, String text, int x, int y) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(0.75f, 0.75f);
		g.text(font, text, 0, 0, Theme.TEXT_FAINT, false);
		g.pose().popMatrix();
	}

	private void extractTopBar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int sx = mainX;
		int sy = panelY + 10;
		int sw = searchW();
		boolean focused = search.isFocused();
		Theme.card(g, sx, sy, sw, 18, Theme.SURFACE, focused ? Theme.withAlpha(Theme.ACCENT, 0.9f) : Theme.PANEL_BORDER);
		if (focused) Theme.glow(g, sx, sy, sw, 18, Theme.ACCENT, 0.7f);
		// magnifier
		int ix = sx + 7;
		int iy = sy + 5;
		int c = focused ? Theme.ACCENT_HOVER : Theme.TEXT_FAINT;
		g.text(font, Fonts.icon(Fonts.Icon.SEARCH), ix - 1, iy - 1, c, false);
	}

	private void extractCards(GuiGraphicsExtractor g, int mouseX, int mouseY, long now) {
		if (filtered.isEmpty()) {
			String msg = mgr().macros().isEmpty() ? "No macros yet - press + New" : "Nothing here";
			g.centeredText(font, msg, mainX + mainW / 2, listY + listH / 2 - 4, Theme.TEXT_FAINT);
		}
		// animate expansion, then smooth scrolling
		for (Macro m : filtered) {
			float e = Theme.approach(expandOf(m), m.id.equals(expandedId) ? 1f : 0f, 0.22f);
			expandAnim.put(m.id, e);
		}
		clampScroll();
		scrollSmooth = Theme.approach(scrollSmooth, (float) scroll, 0.35f);

		List<Macro> all = mgr().macros();
		boolean inList = inside(mouseX, mouseY, mainX, listY, mainW, listH);
		g.enableScissor(mainX - 4, listY, mainX + mainW + 4, listY + listH);
		int y = listY - Math.round(scrollSmooth);
		for (int i = 0; i < filtered.size(); i++) {
			Macro m = filtered.get(i);
			int h = cardHeight(m);
			if (y + h >= listY && y <= listY + listH) {
				// staggered slide-in
				float appear = Theme.easeOut((now - listShownAt - i * 40L) / 260f);
				if (appear > 0.01f) {
					g.pose().pushMatrix();
					g.pose().translate((1 - appear) * 18, 0);
					extractCard(g, m, i, y, h, appear, inList, mouseX, mouseY, now, all);
					g.pose().popMatrix();
				}
			}
			y += h + CARD_GAP;
		}
		g.disableScissor();

		int content = contentHeight();
		if (content > listH) {
			int barH = Math.max(16, listH * listH / content);
			int barY = listY + (int) ((listH - barH) * (scrollSmooth / (content - listH)));
			Theme.rounded(g, mainX + mainW + 3, barY, 3, barH, Theme.withAlpha(Theme.ACCENT, 0.8f));
		}
	}

	private void extractCard(GuiGraphicsExtractor g, Macro m, int index, int y, int h, float alpha,
			boolean inList, int mouseX, int mouseY, long now, List<Macro> all) {
		int x = mainX;
		int w = mainW;
		boolean hovered = inList && inside(mouseX, mouseY, x, y, w, h);
		float hv = Theme.approach(cardHover.getOrDefault(m.id, 0f), hovered ? 1f : 0f, 0.25f);
		cardHover.put(m.id, hv);
		float ex = Theme.easeOut(expandOf(m));
		boolean running = mgr().executor().isRunning(m.id);

		int fill = Theme.lerpColor(Theme.SURFACE, Theme.SURFACE_HOVER, Math.max(hv, ex * 0.6f));
		int border = Theme.lerpColor(Theme.withAlpha(Theme.PANEL_BORDER, 0.6f), Theme.withAlpha(Theme.ACCENT, 0.7f), Math.max(hv * 0.6f, ex));
		if (hv > 0.01f || running) Theme.glow(g, x, y, w, h, Theme.ACCENT, Math.max(hv * 0.6f, running ? 0.8f : 0f) * alpha);
		Theme.card(g, x, y, w, h, Theme.withAlpha(fill, alpha), Theme.withAlpha(border, alpha));
		if (hv > 0.01f) Theme.hGradient(g, x + 2, y + 2, x + w / 2, y + CARD_H - 2, Theme.withAlpha(Theme.ACCENT, 0.14f * hv * alpha), 0x00000000);

		// running: pulsing accent bar + a shimmer band sweeping across the card
		float pulse = running ? (float) (0.6 + 0.4 * Math.sin(now / 150.0)) : 1f;
		Theme.accentBar(g, x + 1, y + 6, CARD_H - 12, (m.enabled ? pulse : 0.2f) * alpha);
		if (running) {
			int bandX = x + (int) ((now / 4) % (w + 60)) - 60;
			g.enableScissor(x + 2, y + 1, x + w - 2, y + h - 1);
			Theme.hGradient(g, bandX, y + 1, bandX + 30, y + h - 1, 0x00FFFFFF, 0x18FFFFFF);
			Theme.hGradient(g, bandX + 30, y + 1, bandX + 60, y + h - 1, 0x18FFFFFF, 0x00FFFFFF);
			g.disableScissor();
		}

		// right side: switch, chevron, key badge
		int swX = x + w - 32;
		boolean swHover = hovered && mouseY < y + CARD_H && mouseX >= x + w - 40;
		float knob = Theme.approach(switchAnim.getOrDefault(m.id, m.enabled ? 1f : 0f), m.enabled ? 1f : 0f, 0.3f);
		switchAnim.put(m.id, knob);
		Theme.toggle(g, swX, y + 11, knob, swHover ? 1f : 0f);

		int chevX = swX - 14;
		g.pose().pushMatrix();
		g.pose().translate(chevX + 3, y + 16);
		g.pose().rotate((float) Math.toRadians(90 - 180 * ex));
		g.text(font, Fonts.icon(Fonts.Icon.CHEVRON_RIGHT), -4, -4, Theme.withAlpha(Theme.lerpColor(Theme.TEXT_FAINT, Theme.TEXT, Math.max(hv, ex)), alpha), false);
		g.pose().popMatrix();

		String key = KeybindManager.label(m.key) + (m.trigger == TriggerMode.CLICK ? "" : " · " + m.trigger.displayName());
		boolean conflict = KeybindConflicts.hasConflict(m, all);
		int badgeW = font.width(key) + 10;
		int badgeX = chevX - 8 - badgeW;
		Theme.card(g, badgeX, y + 10, badgeW, 14, Theme.withAlpha(conflict ? 0xFF4A2A10 : 0x80000000, alpha),
				Theme.withAlpha(conflict ? Theme.WARNING : Theme.PANEL_BORDER, 0.5f * alpha));
		g.text(font, key, badgeX + 5, y + 13, Theme.withAlpha(conflict ? Theme.WARNING : Theme.TEXT_DIM, alpha), false);

		// name + subtitle
		int textW = badgeX - x - 20;
		int nameColor = Theme.withAlpha(m.enabled ? Theme.TEXT : Theme.TEXT_FAINT, alpha);
		g.text(font, Fonts.bold(ellipsize(m.name, textW)), x + 10, y + 7, nameColor, false);
		String sub = subtitle(m, running);
		g.text(font, ellipsize(sub, textW), x + 10, y + 19, Theme.withAlpha(running ? Theme.ACCENT_HOVER : Theme.TEXT_DIM, alpha), false);

		// expanded details
		if (ex > 0.02f) {
			g.enableScissor(x, y + CARD_H - 2, x + w, y + h - 1);
			float fa = alpha * ex;
			int dy = y + CARD_H;
			Theme.hGradient(g, x + 8, dy - 1, x + w - 8, dy, Theme.withAlpha(Theme.ACCENT, 0.5f * fa), Theme.withAlpha(Theme.ACCENT2, 0.02f * fa));
			int cx = x + 10;
			cx = chip(g, cx, dy + 5, m.type.displayName(), fa);
			cx = chip(g, cx, dy + 5, m.steps.size() + (m.steps.size() == 1 ? " step" : " steps"), fa);
			cx = chip(g, cx, dy + 5, m.trigger.displayName(), fa);
			if (m.cooldownMs > 0) cx = chip(g, cx, dy + 5, m.cooldownMs + "ms cooldown", fa);
			if (m.repeatCount > 1) cx = chip(g, cx, dy + 5, "x" + m.repeatCount, fa);
			if (m.rules != null && !m.rules.isEmpty()) chip(g, cx, dy + 5, m.rules.size() + (m.rules.size() == 1 ? " rule" : " rules"), fa);

			int by = dy + 22;
			actionButton(g, m.id + ":edit", x + 10, by, 44, "Edit", true, false, mouseX, mouseY, fa);
			actionButton(g, m.id + ":run", x + 58, by, 44, running ? "Running" : "Run", m.enabled, true, mouseX, mouseY, fa);
			String hint = "Right-click a card to edit";
			g.text(font, hint, x + w - 10 - font.width(hint), by + 3, Theme.withAlpha(Theme.TEXT_FAINT, fa), false);
			g.disableScissor();
		}
	}

	private String subtitle(Macro m, boolean running) {
		if (running) {
			for (MacroRun r : mgr().executor().activeRuns()) {
				if (r.macro().id.equals(m.id) && r.paused()) return "Paused: " + r.pauseReason();
				if (r.macro().id.equals(m.id)) return "Running step " + r.currentStepNumber() + " of " + r.totalSteps();
			}
		}
		if (m.description != null && !m.description.isBlank()) return m.description;
		if (!m.steps.isEmpty()) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < Math.min(3, m.steps.size()); i++) {
				MacroStep s = m.steps.get(i);
				if (i > 0) sb.append("  →  ");
				sb.append(s.summary());
			}
			if (m.steps.size() > 3) sb.append("  …");
			return sb.toString();
		}
		return m.type.description();
	}

	private int chip(GuiGraphicsExtractor g, int x, int y, String text, float alpha) {
		int w = font.width(text) + 10;
		Theme.rounded(g, x, y, w, 13, Theme.withAlpha(0x80000000, alpha));
		g.text(font, text, x + 5, y + 3, Theme.withAlpha(Theme.TEXT_DIM, alpha), false);
		return x + w + 4;
	}

	private void actionButton(GuiGraphicsExtractor g, String id, int x, int y, int w, String label, boolean enabled,
			boolean primary, int mouseX, int mouseY, float alpha) {
		boolean hovered = enabled && inside(mouseX, mouseY, x, y, w, 14);
		float h = Theme.approach(buttonHover.getOrDefault(id, 0f), hovered ? 1f : 0f, 0.3f);
		buttonHover.put(id, h);
		if (primary && enabled) {
			if (h > 0.01f) Theme.glow(g, x, y, w, 14, Theme.ACCENT2, h * alpha);
			Theme.accentPill(g, x, y, w, 14, h);
		} else {
			Theme.card(g, x, y, w, 14, Theme.withAlpha(Theme.lerpColor(Theme.SURFACE, Theme.SURFACE_SELECTED, h), alpha),
					Theme.withAlpha(Theme.PANEL_BORDER, alpha));
		}
		int color = !enabled ? Theme.TEXT_FAINT : primary ? 0xFFFFFFFF : Theme.TEXT;
		g.centeredText(font, label, x + w / 2, y + 3, Theme.withAlpha(color, alpha));
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
