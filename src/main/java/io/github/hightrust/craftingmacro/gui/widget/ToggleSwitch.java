package io.github.hightrust.craftingmacro.gui.widget;

import io.github.hightrust.craftingmacro.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Animated on/off switch with an optional label to its right. */
public class ToggleSwitch extends AbstractWidget {
	public static final int SWITCH_W = 22;
	public static final int SWITCH_H = 12;

	private boolean value;
	private final Consumer<Boolean> onChange;
	private float knob;
	private float hover;

	public ToggleSwitch(int x, int y, int width, String label, boolean value, Consumer<Boolean> onChange) {
		super(x, y, width, SWITCH_H + 2, Component.literal(label));
		this.value = value;
		this.knob = value ? 1f : 0f;
		this.onChange = onChange;
	}

	public ToggleSwitch tooltip(String text) {
		setTooltip(text == null ? null : Tooltip.create(Component.literal(text)));
		return this;
	}

	public boolean value() {
		return value;
	}

	public void setValue(boolean value) {
		this.value = value;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		knob = Theme.approach(knob, value ? 1f : 0f, 0.35f);
		hover = Theme.approach(hover, isHoveredOrFocused() ? 1f : 0f, 0.3f);
		int x = getX();
		int y = getY() + 1;
		Theme.toggle(g, x, y, knob, hover);
		String label = getMessage().getString();
		if (!label.isEmpty()) {
			Font font = Minecraft.getInstance().font;
			g.text(font, label, x + SWITCH_W + 6, y + 2, active ? Theme.TEXT : Theme.TEXT_FAINT, false);
		}
	}

	private void toggle() {
		value = !value;
		onChange.accept(value);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (active) toggle();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (active && isFocused() && event.isSelection()) {
			toggle();
			return true;
		}
		return false;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, Component.literal(getMessage().getString() + ": " + (value ? "on" : "off")));
	}
}
