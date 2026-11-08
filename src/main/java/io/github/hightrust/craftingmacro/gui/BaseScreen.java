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
		super(Component.literal(title));
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
		g.fill(0, 0, width, height, Theme.withAlpha(Theme.OVERLAY, fade));
		Theme.panel(g, panelX, panelY, panelW, panelH, Theme.PANEL, Theme.PANEL_BORDER);
		// header strip
		g.fill(panelX + 1, panelY + 24, panelX + panelW - 1, panelY + 25, Theme.PANEL_BORDER);
		g.text(font, title, panelX + 10, panelY + 8, Theme.TEXT, false);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		extractContent(g, mouseX, mouseY, a);
		super.extractRenderState(g, mouseX, mouseY, a);
		extractOverlay(g, mouseX, mouseY, a);
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
