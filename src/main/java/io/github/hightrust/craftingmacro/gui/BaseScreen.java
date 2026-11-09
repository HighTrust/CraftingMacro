package io.github.hightrust.craftingmacro.gui;

import io.github.hightrust.craftingmacro.gui.widget.FlatButton;
import io.github.hightrust.craftingmacro.gui.widget.NumberBox;
import io.github.hightrust.craftingmacro.gui.widget.ToggleSwitch;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Shared layout and helpers for all mod screens: dimmed backdrop, centered rounded panel, fade-in. */
public abstract class BaseScreen extends Screen {
	protected final Screen parent;
	protected int panelX;
	protected int panelY;
	protected int panelW;
	protected int panelH;
	private final long openedAt = System.currentTimeMillis();

	protected BaseScreen(String title, Screen parent) {
		super(net.minecraft.client.Minecraft.getInstance(), Fonts.ui(), Component.literal(title));
		this.parent = parent;
	}

	/** Preferred panel size; clamped to the window with a margin. */
	protected int preferredWidth() {
		return 520;
	}

	protected int preferredHeight() {
		return 320;
	}

	@Override
	protected void init() {
		Theme.apply(io.github.hightrust.craftingmacro.MacroManager.get().settings().theme);
		panelW = Math.min(preferredWidth(), width - 16);
		panelH = Math.min(preferredHeight(), height - 16);
		panelX = (width - panelW) / 2;
		panelY = (height - panelH) / 2;
		build();
	}

	/** Create widgets; panel bounds are already computed. */
	protected abstract void build();

	protected boolean compact() {
		return panelW < 420;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		float fade = Math.min(1f, (System.currentTimeMillis() - openedAt) / 150f);
		if (Theme.blur() && minecraft.level != null) extractBlurredBackground(g);
		g.fill(0, 0, width, height, Theme.withAlpha(Theme.OVERLAY, fade));
		pushOpenAnimation(g);
		extractPanel(g);
		g.pose().popMatrix();
	}

	/** Open animation progress, 0..1 (eased). */
	protected float openProgress() {
		return Theme.easeOut((System.currentTimeMillis() - openedAt) / 240f);
	}

	/** Scales the panel up from 94% and lifts it slightly while the screen opens. */
	private void pushOpenAnimation(GuiGraphicsExtractor g) {
		float p = openProgress();
		float s = 0.94f + 0.06f * p;
		float cx = panelX + panelW / 2f;
		float cy = panelY + panelH / 2f;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy + (1 - p) * 8);
		g.pose().scale(s, s);
		g.pose().translate(-cx, -cy);
	}

	/** Draws the panel frame and header. Screens with a custom layout override this. */
	protected void extractPanel(GuiGraphicsExtractor g) {
		Theme.panel(g, panelX, panelY, panelW, panelH, Theme.PANEL, Theme.PANEL_BORDER);
		// header: accent bar + title + fading gradient divider
		Theme.accentBar(g, panelX + 8, panelY + 7, 10, 1f);
		g.text(font, title, panelX + 14, panelY + 8, Theme.TEXT, false);
		Theme.hGradient(g, panelX + 1, panelY + 24, panelX + panelW - 1, panelY + 25, Theme.withAlpha(Theme.ACCENT, 0.7f), Theme.withAlpha(Theme.ACCENT2, 0.05f));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		pushOpenAnimation(g);
		extractContent(g, mouseX, mouseY, a);
		super.extractRenderState(g, mouseX, mouseY, a);
		extractOverlay(g, mouseX, mouseY, a);
		g.pose().popMatrix();
	}

	/** Switches to the next menu style (Dark, Glass, Clear), saves it, and rebuilds the screen. */
	protected void cycleTheme() {
		var mgr = io.github.hightrust.craftingmacro.MacroManager.get();
		Theme.Mode next = Theme.mode().next();
		mgr.settings().theme = next.name().toLowerCase(java.util.Locale.ROOT);
		mgr.markDirty();
		Theme.apply(next);
		mgr.notifier().info("Theme: " + next.displayName(), true);
	}

	/** Custom drawing below widgets. */
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
	}

	/** Custom drawing above widgets. */
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	// ------------------------------------------------------------------ widget helpers

	protected FlatButton button(int x, int y, int w, String label, FlatButton.Style style, Runnable onPress) {
		return addRenderableWidget(new FlatButton(x, y, w, 18, label, style, onPress));
	}

	protected ToggleSwitch toggle(int x, int y, int w, String label, boolean value, String tooltip, Consumer<Boolean> onChange) {
		return addRenderableWidget(new ToggleSwitch(x, y, w, label, value, onChange).tooltip(tooltip));
	}

	protected EditBox textField(int x, int y, int w, String value, int maxLength, String hint, String tooltip) {
		EditBox box = new EditBox(font, x, y, w, 16, Component.literal(hint));
		box.setMaxLength(maxLength);
		box.setValue(value == null ? "" : value);
		box.setHint(Component.literal(hint).withColor(Theme.TEXT_FAINT));
		if (tooltip != null) box.setTooltip(Tooltip.create(Component.literal(tooltip)));
		return addRenderableWidget(box);
	}

	protected EditBox numberField(int x, int y, int w, int value, String tooltip) {
		NumberBox box = new NumberBox(font, x, y, w, 16, Component.literal(tooltip == null ? "Number" : tooltip));
		box.setMaxLength(7);
		box.setValue(Integer.toString(value));
		box.setHint(Component.literal("0").withColor(Theme.TEXT_FAINT));
		if (tooltip != null) box.setTooltip(Tooltip.create(Component.literal(tooltip)));
		return addRenderableWidget(box);
	}

	protected static int parseInt(EditBox box, int fallback) {
		try {
			return Integer.parseInt(box.getValue().trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	protected void label(GuiGraphicsExtractor g, String text, int x, int y) {
		g.text(font, text, x, y, Theme.TEXT_DIM, false);
	}

	protected static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}

	protected String ellipsize(String text, int maxWidth) {
		if (font.width(text) <= maxWidth) return text;
		return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
	}
}
