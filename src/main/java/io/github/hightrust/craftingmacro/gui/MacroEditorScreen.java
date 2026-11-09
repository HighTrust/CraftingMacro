package io.github.hightrust.craftingmacro.gui;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.hightrust.craftingmacro.MacroManager;
import io.github.hightrust.craftingmacro.gui.widget.FlatButton;
import io.github.hightrust.craftingmacro.input.KeybindManager;
import io.github.hightrust.craftingmacro.macro.KeyCombo;
import io.github.hightrust.craftingmacro.macro.KeybindConflicts;
import io.github.hightrust.craftingmacro.macro.Macro;
import io.github.hightrust.craftingmacro.macro.MacroStep;
import io.github.hightrust.craftingmacro.macro.MacroType;
import io.github.hightrust.craftingmacro.macro.MacroValidator;
import io.github.hightrust.craftingmacro.macro.StepType;
import io.github.hightrust.craftingmacro.macro.TriggerMode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.List;

/**
 * Single-page macro editor: name, key, and the step list. Click a step to edit it; the
 * arrows / x on the right of each row reorder or remove it. Less common settings live in
 * {@link MacroOptionsScreen}. Works on a copy; nothing changes until Save.
 */
public final class MacroEditorScreen extends BaseScreen {
	private static final int ROW_H = 18;
	private static final int ICON_W = 12;

	private final MacroListScreen list;
	private final Macro original;
	private final Macro working;
	private boolean capturing;
	private int pendingModifier = -1;
	private int scroll;
	private String error = "";
	private FlatButton keyButton;

	private int stepsX;
	private int stepsY;
	private int stepsW;
	private int stepsH;

	public MacroEditorScreen(MacroListScreen list, Macro original) {
		super(original == null ? "New Macro" : "Edit Macro", list);
		this.list = list;
		this.original = original;
		this.working = original == null ? new Macro() : original.copy(false);
	}

	@Override
	protected int preferredWidth() {
		return 320;
	}

	@Override
	protected int preferredHeight() {
		return 280;
	}

	@Override
	protected void build() {
		int x = panelX + 8;
		int w = panelW - 16;
		int y = panelY + 30;

		EditBox name = textField(x, y, w, working.name, MacroValidator.MAX_NAME, "Name", null);
		name.setResponder(s -> working.name = s);
		y += 22;
		keyButton = button(x, y, w - 22, keyLabel(), FlatButton.Style.NORMAL, this::startCapture)
				.tooltip("Click, then press a key (Ctrl/Shift/Alt allowed). Esc cancels.");
		button(x + w - 18, y, 18, "x", FlatButton.Style.GHOST, () -> {
			working.key = KeyCombo.unbound();
			capturing = false;
			keyButton.setLabel(keyLabel());
		}).tooltip("Remove key");
		y += 22;
		TriggerMode[] modes = TriggerMode.values();
		int segW = (w - 2 * 2) / modes.length;
		for (int i = 0; i < modes.length; i++) {
			TriggerMode mode = modes[i];
			button(x + i * (segW + 2), y, segW, mode.displayName(),
					working.trigger == mode ? FlatButton.Style.PRIMARY : FlatButton.Style.NORMAL, () -> {
						working.trigger = mode;
						rebuildWidgets();
					}).tooltip(mode.description());
		}
		y += 30;

		stepsX = x;
		stepsY = y;
		stepsW = w;
		int bottom = panelY + panelH - 26;
		stepsH = bottom - 22 - stepsY;
		clampScroll();

		button(x, bottom - 20, w, "+ Add step", FlatButton.Style.GHOST, this::addStep)
				.enabled(working.steps.size() < MacroValidator.MAX_STEPS);

		int bw = 60;
		button(panelX + panelW - 8 - bw, bottom, bw, "Save", FlatButton.Style.PRIMARY, this::save);
		button(panelX + panelW - 8 - bw * 2 - 4, bottom, bw, "Cancel", FlatButton.Style.NORMAL, this::onClose);
		int lx = x;
		button(lx, bottom, 52, "Options", FlatButton.Style.NORMAL,
				() -> minecraft.gui.setScreen(new MacroOptionsScreen(this, working))).tooltip("Cooldown, repeat, servers, notifications...");
		lx += 56;
		if (original != null) {
			button(lx, bottom, 36, "Copy", FlatButton.Style.GHOST, this::duplicate).tooltip("Duplicate this macro");
			lx += 40;
			button(lx, bottom, 42, "Delete", FlatButton.Style.DANGER, this::delete).tooltip("Delete this macro");
		}
	}

	// ------------------------------------------------------------------ key capture

	private String keyLabel() {
		if (capturing) return "Press a key...";
		return working.key.isBound() ? "Key: " + KeybindManager.label(working.key) : "Key: none (click to set)";
	}

	private void startCapture() {
		capturing = true;
		pendingModifier = -1;
		keyButton.setLabel(keyLabel());
	}

	private static boolean isModifier(int key) {
		return key == InputConstants.KEY_LCONTROL || key == InputConstants.KEY_RCONTROL || key == InputConstants.KEY_LSHIFT
				|| key == InputConstants.KEY_RSHIFT || key == InputConstants.KEY_LALT || key == InputConstants.KEY_RALT;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (capturing) {
			int key = event.key();
			if (key == InputConstants.KEY_ESCAPE) {
				capturing = false;
			} else if (key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE) {
				working.key = KeyCombo.unbound();
				capturing = false;
			} else if (isModifier(key)) {
				pendingModifier = key;
			} else {
				working.key = new KeyCombo(InputConstants.getKey(event).getName(), event.hasControlDown(), event.hasShiftDown(), event.hasAltDown());
				capturing = false;
			}
			keyButton.setLabel(keyLabel());
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (capturing && pendingModifier >= 0 && event.key() == pendingModifier) {
			working.key = KeyCombo.of(InputConstants.getKey(event).getName());
			capturing = false;
			pendingModifier = -1;
			keyButton.setLabel(keyLabel());
			return true;
		}
		return super.keyReleased(event);
	}

	// ------------------------------------------------------------------ steps

	private int visibleRows() {
		return Math.max(1, stepsH / ROW_H);
	}

	private void clampScroll() {
		scroll = Math.max(0, Math.min(scroll, working.steps.size() - visibleRows()));
	}

	private void addStep() {
		StepType type = working.steps.isEmpty() ? StepType.COMMAND : StepType.WAIT_TICKS;
		MacroStep step = MacroStep.of(type);
		if (type == StepType.WAIT_TICKS) step.amount = 20;
		minecraft.gui.setScreen(new StepEditorScreen(this, step, working.steps.size(), working.steps::add));
	}

	private void editStep(int idx) {
		minecraft.gui.setScreen(new StepEditorScreen(this, working.steps.get(idx).copy(), idx, r -> working.steps.set(idx, r)));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && inside(mx, my, stepsX, stepsY, stepsW, stepsH)) {
			int idx = (int) ((my - stepsY) / ROW_H) + scroll;
			if (idx >= 0 && idx < working.steps.size()) {
				int right = stepsX + stepsW;
				if (mx >= right - ICON_W) {
					working.steps.remove(idx);
					clampScroll();
					rebuildWidgets();
				} else if (mx >= right - ICON_W * 2) {
					swap(idx, idx + 1);
				} else if (mx >= right - ICON_W * 3) {
					swap(idx, idx - 1);
				} else {
					editStep(idx);
				}
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void swap(int a, int b) {
		if (b < 0 || b >= working.steps.size()) return;
		MacroStep s = working.steps.get(a);
		working.steps.set(a, working.steps.get(b));
		working.steps.set(b, s);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (inside(mx, my, stepsX, stepsY, stepsW, stepsH)) {
			scroll -= (int) Math.signum(sy);
			clampScroll();
			return true;
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}

	// ------------------------------------------------------------------ save / delete

	private static MacroType inferType(List<MacroStep> steps) {
		boolean allCommands = !steps.isEmpty();
		for (MacroStep s : steps) {
			if (s.type == StepType.CRAFT) return MacroType.CRAFT;
			if (s.type != StepType.COMMAND && s.type != StepType.WAIT_TICKS && s.type != StepType.WAIT_MS) allCommands = false;
		}
		return allCommands ? MacroType.COMMAND : MacroType.SEQUENCE;
	}

	private void save() {
		List<String> errors = MacroValidator.validate(working);
		if (!errors.isEmpty()) {
			error = errors.get(0);
			return;
		}
		working.type = inferType(working.steps);
		MacroValidator.sanitize(working);
		List<Macro> macros = MacroManager.get().macros();
		Macro saved = working.copy(false);
		int idx = original == null ? -1 : macros.indexOf(original);
		if (idx >= 0) macros.set(idx, saved);
		else macros.add(saved);
		MacroManager.get().saveNow();
		onClose();
		list.onMacroSaved(saved);
	}

	private void duplicate() {
		Macro copy = original.copy(true);
		copy.name = (original.name + " copy").length() > MacroValidator.MAX_NAME ? original.name : original.name + " copy";
		List<Macro> macros = MacroManager.get().macros();
		macros.add(macros.indexOf(original) + 1, copy);
		MacroManager.get().saveNow();
		onClose();
		list.onMacroSaved(copy);
	}

	private void delete() {
		minecraft.gui.setScreen(new ConfirmDialog(this, "Delete macro", "Delete \"" + original.name + "\"?", "Delete", () -> {
			MacroManager.get().executor().cancel(original.id, "Deleted");
			MacroManager.get().macros().remove(original);
			MacroManager.get().saveNow();
			minecraft.gui.setScreen(list);
			list.onMacroSaved(null);
		}));
	}

	// ------------------------------------------------------------------ drawing

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		// key warning under the key button
		String warn = keyWarning();
		if (warn != null) g.text(font, ellipsize(warn, stepsW), stepsX, stepsY - 9, Theme.WARNING, false);

		if (working.steps.isEmpty()) {
			g.centeredText(font, "No steps yet", stepsX + stepsW / 2, stepsY + stepsH / 2 - 4, Theme.TEXT_FAINT);
		}
		int end = Math.min(working.steps.size(), scroll + visibleRows());
		int right = stepsX + stepsW;
		for (int i = scroll; i < end; i++) {
			int y = stepsY + (i - scroll) * ROW_H;
			MacroStep s = working.steps.get(i);
			boolean hov = inside(mouseX, mouseY, stepsX, y, stepsW, ROW_H - 2);
			Theme.rounded(g, stepsX, y, stepsW, ROW_H - 2, hov ? Theme.SURFACE_HOVER : Theme.SURFACE);
			g.text(font, String.valueOf(i + 1), stepsX + 5, y + 4, Theme.TEXT_FAINT, false);
			boolean bad = MacroValidator.validateStep(s, i) != null;
			g.text(font, ellipsize(s.summary(), stepsW - 20 - ICON_W * 3), stepsX + 17, y + 4, bad ? Theme.WARNING : Theme.TEXT, false);
			icon(g, "▲", right - ICON_W * 3, y, mouseX, mouseY, i > 0, Theme.TEXT);
			icon(g, "▼", right - ICON_W * 2, y, mouseX, mouseY, i < working.steps.size() - 1, Theme.TEXT);
			icon(g, "✕", right - ICON_W, y, mouseX, mouseY, true, Theme.DANGER);
		}
		if (!error.isEmpty()) {
			g.text(font, ellipsize(error, panelW - 16), panelX + 8, panelY + panelH + 4, Theme.DANGER, false);
		}
	}

	private void icon(GuiGraphicsExtractor g, String glyph, int x, int y, int mx, int my, boolean enabled, int hotColor) {
		boolean hov = enabled && inside(mx, my, x, y, ICON_W, ROW_H - 2);
		int color = !enabled ? 0x00000000 : hov ? hotColor : Theme.TEXT_FAINT;
		if ((color >>> 24) != 0) g.centeredText(font, glyph, x + ICON_W / 2, y + 4, color);
	}

	private String keyWarning() {
		if (!working.key.isBound()) return null;
		List<Macro> c = KeybindConflicts.conflictingMacros(working.key, MacroManager.get().macros(), working);
		if (working.enabled && !c.isEmpty()) return "Same key as \"" + c.get(0).name + "\"";
		List<String> v = KeybindConflicts.conflictingBindings(working.key, KeybindManager.otherBindings(minecraft));
		if (!v.isEmpty()) return "Also used by " + v.get(0) + (MacroManager.get().settings().suppressVanillaConflicts ? " (macro wins)" : "");
		return null;
	}

	@Override
	public void added() {
		error = "";
	}
}
