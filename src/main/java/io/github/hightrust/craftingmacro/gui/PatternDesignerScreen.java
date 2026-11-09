package io.github.hightrust.craftingmacro.gui;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.hightrust.craftingmacro.gui.widget.FlatButton;
import io.github.hightrust.craftingmacro.macro.MacroStep;
import io.github.hightrust.craftingmacro.macro.MacroValidator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A "fake" crafting table for designing custom patterns. Click a slot to choose an item,
 * right-click to clear. Slots pop in with a staggered animation and glow on hover.
 */
public final class PatternDesignerScreen extends BaseScreen {
	private static final int SLOT = 24;

	private final MacroStep step;
	private final Consumer<MacroStep> onDone;
	private final List<String> grid;
	private final float[] hover = new float[9];
	private final long[] changedAt = new long[9];
	private final long openedAt = System.currentTimeMillis();
	private int gridX;
	private int gridY;

	public PatternDesignerScreen(Screen parent, MacroStep step, Consumer<MacroStep> onDone) {
		super("Design Pattern", parent);
		this.step = step.copy();
		this.onDone = onDone;
		this.grid = new ArrayList<>(this.step.grid());
	}

	@Override
	protected int preferredWidth() {
		return 270;
	}

	@Override
	protected int preferredHeight() {
		return 210;
	}

	@Override
	protected void build() {
		gridX = panelX + 18;
		gridY = panelY + 36;
		int rightX = gridX + SLOT * 3 + 56;
		int rightW = panelX + panelW - 10 - rightX;

		EditBox label = textField(rightX, gridY + 10, rightW, step.text, MacroValidator.MAX_NAME, "Label (optional)",
				"A name shown in the step list, e.g. 'Custom sword'");
		label.setResponder(s -> step.text = s);
		EditBox amount = numberField(rightX, gridY + 46, 50, step.amount, "How many times to craft this pattern (Hold/Toggle = as many as possible)");
		amount.setResponder(s -> {
			try {
				step.amount = Math.max(1, Integer.parseInt(s.trim()));
			} catch (NumberFormatException e) {
				step.amount = 1;
			}
		});

		int by = panelY + panelH - 26;
		button(panelX + 10, by, 50, "Clear", FlatButton.Style.GHOST, () -> {
			for (int i = 0; i < 9; i++) set(i, "");
		}).tooltip("Empty the whole grid");
		button(panelX + panelW - 70, by, 60, "Done", FlatButton.Style.PRIMARY, this::done);
		button(panelX + panelW - 134, by, 60, "Cancel", FlatButton.Style.NORMAL, this::onClose);
	}

	private void set(int i, String id) {
		grid.set(i, id);
		changedAt[i] = System.currentTimeMillis();
	}

	private void done() {
		step.pattern = new ArrayList<>(grid);
		if (MacroValidator.validateStep(step, 0) != null) return;
		onClose();
		onDone.accept(step);
	}

	private int slotAt(double mx, double my) {
		for (int i = 0; i < 9; i++) {
			int x = gridX + (i % 3) * SLOT;
			int y = gridY + (i / 3) * SLOT;
			if (inside(mx, my, x, y, SLOT - 2, SLOT - 2)) return i;
		}
		return -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int i = slotAt(event.x(), event.y());
		if (i >= 0) {
			if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
				final int idx = i;
				minecraft.gui.setScreen(new ItemPickerScreen(this, id -> set(idx, id)));
			} else {
				set(i, "");
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private static ItemStack stackOf(String id) {
		Identifier rid = Identifier.tryParse(id);
		if (id.isEmpty() || rid == null || !BuiltInRegistries.ITEM.containsKey(rid)) return ItemStack.EMPTY;
		return new ItemStack(BuiltInRegistries.ITEM.getValue(rid));
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		long now = System.currentTimeMillis();
		// table backdrop
		Theme.rounded(g, gridX - 8, gridY - 8, SLOT * 3 + 14, SLOT * 3 + 14, 0xFF12151C);
		int hovered = slotAt(mouseX, mouseY);
		for (int i = 0; i < 9; i++) {
			int x = gridX + (i % 3) * SLOT;
			int y = gridY + (i / 3) * SLOT;
			// staggered pop-in
			float t = Math.max(0f, Math.min(1f, (now - openedAt - i * 35) / 180f));
			float ease = 1f - (1f - t) * (1f - t) * (1f - t);
			if (ease <= 0f) continue;
			hover[i] = Theme.approach(hover[i], i == hovered ? 1f : 0f, 0.3f);
			int size = SLOT - 2;
			g.pose().pushMatrix();
			g.pose().translate(x + size / 2f, y + size / 2f);
			float s = ease * (1f + 0.06f * hover[i]);
			// a quick "bounce" when an item is placed
			float since = (now - changedAt[i]) / 220f;
			if (since < 1f) s *= 1f + 0.18f * (float) Math.sin(since * Math.PI);
			g.pose().scale(s, s);
			g.pose().translate(-size / 2f, -size / 2f);
			int fill = Theme.lerpColor(Theme.SURFACE, Theme.SURFACE_HOVER, hover[i]);
			Theme.rounded(g, 0, 0, size, size, fill);
			if (hover[i] > 0.01f) {
				int glow = Theme.withAlpha(Theme.ACCENT, hover[i]);
				g.outline(0, 0, size, size, glow);
			}
			ItemStack stack = stackOf(grid.get(i));
			if (!stack.isEmpty()) g.item(stack, (size - 16) / 2, (size - 16) / 2);
			g.pose().popMatrix();
		}
		// arrow + result
		int ax = gridX + SLOT * 3 + 8;
		int ay = gridY + SLOT + 7;
		float pulse = (float) (0.5 + 0.5 * Math.sin(now / 300.0));
		int arrow = Theme.lerpColor(Theme.TEXT_FAINT, Theme.ACCENT, pulse * 0.6f);
		g.fill(ax, ay + 3, ax + 14, ay + 5, arrow);
		g.fill(ax + 12, ay + 1, ax + 14, ay + 7, arrow);
		g.fill(ax + 14, ay + 2, ax + 15, ay + 6, arrow);
		g.fill(ax + 15, ay + 3, ax + 16, ay + 5, arrow);
		int rx = ax + 22;
		Theme.rounded(g, rx, gridY + SLOT - 2, 26, 26, Theme.SURFACE);
		g.centeredText(font, "?", rx + 13, gridY + SLOT + 7, Theme.TEXT_DIM);
		if (inside(mouseX, mouseY, rx, gridY + SLOT - 2, 26, 26)) {
			g.setTooltipForNextFrame(font, Component.literal("The result is decided by the server.\nWorks with custom recipes too."), mouseX, mouseY);
		}

		int rightX = gridX + SLOT * 3 + 56;
		label(g, "Name", rightX, gridY);
		label(g, "Times", rightX, gridY + 36);
		g.text(font, "Left-click: choose item", gridX - 8, gridY + SLOT * 3 + 12, Theme.TEXT_FAINT, false);
		g.text(font, "Right-click: clear slot", gridX - 8, gridY + SLOT * 3 + 22, Theme.TEXT_FAINT, false);
		if (hovered >= 0 && !grid.get(hovered).isEmpty()) {
			g.setTooltipForNextFrame(font, stackOf(grid.get(hovered)).getHoverName(), mouseX, mouseY);
		}
	}
}
