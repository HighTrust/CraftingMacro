package io.github.hightrust.craftingmacro.gui.widget;

import io.github.hightrust.craftingmacro.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Flat, animated button with primary / normal / danger / ghost styles. */
public class FlatButton extends AbstractWidget {
	public enum Style { NORMAL, PRIMARY, DANGER, GHOST }

	private final Runnable onPress;
	private Style style;
	private float hover;

	public FlatButton(int x, int y, int w, int h, String label, Style style, Runnable onPress) {
		super(x, y, w, h, Component.literal(label));
		this.onPress = onPress;
		this.style = style;
	}

	public FlatButton tooltip(String text) {
		setTooltip(text == null ? null : Tooltip.create(Component.literal(text)));
		return this;
	}

	public FlatButton enabled(boolean enabled) {
		this.active = enabled;
		return this;
	}

	public void setStyle(Style style) {
		this.style = style;
	}

	public void setLabel(String label) {
		setMessage(Component.literal(label));
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		hover = Theme.approach(hover, isHoveredOrFocused() && active ? 1f : 0f, 0.3f);
		int base;
		int hot;
		int text = Theme.TEXT;
		switch (style) {
			case PRIMARY -> {
				base = Theme.ACCENT;
				hot = Theme.ACCENT_HOVER;
			}
			case DANGER -> {
				base = 0xFF3A2024;
				hot = Theme.DANGER;
			}
			case GHOST -> {
				base = 0x00000000;
				hot = Theme.SURFACE_HOVER;
				text = Theme.TEXT_DIM;
			}
			default -> {
				base = Theme.SURFACE;
				hot = Theme.SURFACE_HOVER;
			}
		}
		int color = active ? Theme.lerpColor(base, hot, hover) : 0xFF1A1E26;
		if (!active) text = Theme.TEXT_FAINT;
		else if (style == Style.GHOST) text = Theme.lerpColor(Theme.TEXT_DIM, Theme.TEXT, hover);
		if ((color >>> 24) != 0) Theme.rounded(g, getX(), getY(), getWidth(), getHeight(), color);
		if (style == Style.NORMAL && active) {
			g.fill(getX() + 2, getY() + getHeight() - 1, getX() + getWidth() - 2, getY() + getHeight(), Theme.withAlpha(Theme.ACCENT, hover));
		}
		Font font = Minecraft.getInstance().font;
		String label = getMessage().getString();
		int maxW = getWidth() - 6;
		if (font.width(label) > maxW) label = font.plainSubstrByWidth(label, maxW - font.width("...")) + "...";
		g.centeredText(font, label, getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, text);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (active) onPress.run();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (active && isFocused() && event.isSelection()) {
			playDownSound(Minecraft.getInstance().getSoundManager());
			onPress.run();
			return true;
		}
		return false;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
