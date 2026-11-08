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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Create / edit a macro. Works on a copy; nothing changes until Save. */
public final class MacroEditorScreen extends BaseScreen {
	private enum Tab { GENERAL, STEPS, OPTIONS }

	private static final int STEP_ROW = 20;

	private final MacroListScreen list;
	private final Macro original;
	private final Macro working;
	private Tab tab = Tab.GENERAL;
	private boolean capturing;
	private int pendingModifier = -1;
	private int stepScroll;
	private List<String> errors = new ArrayList<>();
	private FlatButton keyButton;

	public MacroEditorScreen(MacroListScreen list, Macro original) {
		super(original == null ? "Create Macro" : "Edit Macro", list);
		this.list = list;
		this.original = original;
		if (original == null) {
			working = new Macro();
			working.name = "New Macro";
			working.type = MacroType.SEQUENCE;
		} else {
			working = original.copy(false);
		}
	}

	@Override
	protected int preferredWidth() {
		return 440;
	}

	@Override
	protected int preferredHeight() {
		return 320;
	}

	@Override
	protected void build() {
		// tabs
		int tx = panelX + panelW - 8;
		Tab[] tabs = Tab.values();
		String[] names = {"General", "Steps (" + working.steps.size() + ")", "Options"};
		for (int i = tabs.length - 1; i >= 0; i--) {
			int w = font.width(names[i]) + 14;
			tx -= w;
			Tab t = tabs[i];
			FlatButton b = button(tx, panelY + 4, w, names[i], t == tab ? FlatButton.Style.PRIMARY : FlatButton.Style.GHOST, () -> switchTab(t));
			b.tooltip(switch (t) {
				case GENERAL -> "Name, key and type";
				case STEPS -> "What the macro does, in order";
				case OPTIONS -> "Cooldown, repeat, notifications and servers";
			});
			tx -= 3;
		}

		switch (tab) {
			case GENERAL -> buildGeneral();
			case STEPS -> buildSteps();
			case OPTIONS -> buildOptions();
		}

		int by = panelY + panelH - 26;
		int bw = 80;
		button(panelX + panelW - 8 - bw, by, bw, "Save", FlatButton.Style.PRIMARY, this::save).tooltip("Save and start using this macro");
		button(panelX + panelW - 8 - bw * 2 - 4, by, bw, "Cancel", FlatButton.Style.NORMAL, this::onClose).tooltip("Discard changes");
	}

	private void switchTab(Tab t) {
		capturing = false;
		tab = t;
		rebuildWidgets();
	}

	private int contentX() {
		return panelX + 12;
	}

	private int contentW() {
		return panelW - 24;
	}

	// ------------------------------------------------------------------ general tab

	private void buildGeneral() {
		int x = contentX();
		int w = contentW();
		int labelW = 80;
		int fx = x + labelW;
		int fw = w - labelW;
		int y = panelY + 34;

		EditBox name = textField(fx, y, fw, working.name, MacroValidator.MAX_NAME, "Macro name", "Shown in the list and notifications");
		name.setResponder(s -> working.name = s);
		y += 24;
		EditBox desc = textField(fx, y, fw, working.description, MacroValidator.MAX_DESCRIPTION, "Optional description", "What this macro is for");
		desc.setResponder(s -> working.description = s);
		y += 24;
		keyButton = button(fx, y, Math.min(160, fw), keyLabel(), FlatButton.Style.NORMAL, this::startCapture)
				.tooltip("Click, then press a key (with Ctrl/Shift/Alt if you like). Esc cancels, Backspace unbinds.");
		button(fx + Math.min(160, fw) + 4, y, 50, "Clear", FlatButton.Style.GHOST, () -> {
			working.key = KeyCombo.unbound();
			capturing = false;
			keyButton.setLabel(keyLabel());
		}).tooltip("Remove the keybind");
		y += 34;
		button(fx, y, Math.min(160, fw), "Type: " + working.type.displayName(), FlatButton.Style.NORMAL, this::cycleType)
				.tooltip(typeTooltip());
		y += 24;
		toggle(fx, y, fw, "Enabled", working.enabled, "Disabled macros never run", v -> working.enabled = v);
	}

	private String typeTooltip() {
		StringBuilder sb = new StringBuilder("Click to change.\n");
		for (MacroType t : MacroType.values()) sb.append(t == working.type ? "> " : "  ").append(t.displayName()).append(": ").append(t.description()).append('\n');
		sb.append("Changing type on a macro with no steps adds starter steps.");
		return sb.toString();
	}

	private void cycleType() {
		MacroType[] types = MacroType.values();
		working.type = types[(working.type.ordinal() + 1) % types.length];
		if (working.steps.isEmpty() || isOnlyStarterSteps()) {
			working.steps.clear();
			working.steps.addAll(starterSteps(working.type));
		}
		rebuildWidgets();
	}

	private boolean isOnlyStarterSteps() {
		for (MacroType t : MacroType.values()) {
			if (working.steps.equals(starterSteps(t))) return true;
		}
		return false;
	}

	private static List<MacroStep> starterSteps(MacroType type) {
		return switch (type) {
			case COMMAND -> List.of(MacroStep.command("/"));
			case CRAFT -> List.of(MacroStep.craft("", 1));
			case SEQUENCE -> List.of();
		};
	}

	private String keyLabel() {
		if (capturing) return pendingModifier >= 0 ? "Press a key..." : "> Press a key <";
		return "Key: " + KeybindManager.label(working.key);
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
				// Might be a modifier for the next key, or a bind on its own (decided on release).
				pendingModifier = key;
			} else {
				String name = InputConstants.getKey(event).getName();
				working.key = new KeyCombo(name, event.hasControlDown(), event.hasShiftDown(), event.hasAltDown());
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

	// ------------------------------------------------------------------ steps tab

	private int stepsTop() {
		return panelY + 34 + (craftShortcut() >= 0 ? 30 : 0);
	}

	private int visibleSteps() {
		return Math.max(1, (panelY + panelH - 62 - stepsTop()) / STEP_ROW);
	}

	/** Index of the CRAFT step shown in the quick recipe row (auto-craft macros), or -1. */
	private int craftShortcut() {
		if (working.type != MacroType.CRAFT) return -1;
		for (int i = 0; i < working.steps.size(); i++) if (working.steps.get(i).type == StepType.CRAFT) return i;
		return -1;
	}

	private void buildSteps() {
		int x = contentX();
		int w = contentW();
		int craftIdx = craftShortcut();
		if (craftIdx >= 0) {
			MacroStep craft = working.steps.get(craftIdx);
			int y = panelY + 34;
			button(x + 22, y, Math.min(170, w - 140), craft.text.isBlank() ? "Choose recipe..." : MacroStep.prettyItem(craft.text),
					FlatButton.Style.PRIMARY, () -> openRecipePicker(craftIdx))
					.tooltip("Pick the item to craft from your unlocked recipes");
			EditBox qty = numberField(x + w - 60, y, 60, craft.amount, "How many result items to craft");
			qty.setResponder(s -> craft.amount = Math.max(1, parseIntSafe(s, 1)));
		}

		stepScroll = Math.max(0, Math.min(stepScroll, Math.max(0, working.steps.size() - visibleSteps())));
		int y = stepsTop();
		int end = Math.min(working.steps.size(), stepScroll + visibleSteps());
		for (int i = stepScroll; i < end; i++) {
			final int idx = i;
			int bx = x + w;
			bx -= 18;
			button(bx, y, 18, "X", FlatButton.Style.DANGER, () -> {
				working.steps.remove(idx);
				rebuildWidgets();
			}).tooltip("Remove step");
			bx -= 32;
			button(bx, y, 30, "Edit", FlatButton.Style.NORMAL, () -> editStep(idx)).tooltip("Edit step");
			bx -= 18;
			button(bx, y, 16, "v", FlatButton.Style.GHOST, () -> move(idx, 1)).enabled(idx < working.steps.size() - 1).tooltip("Move down");
			bx -= 17;
			button(bx, y, 16, "^", FlatButton.Style.GHOST, () -> move(idx, -1)).enabled(idx > 0).tooltip("Move up");
			y += STEP_ROW;
		}

		int by = panelY + panelH - 26;
		button(contentX() - 4, by, 90, "+ Add Step", FlatButton.Style.NORMAL, this::addStep)
				.enabled(working.steps.size() < MacroValidator.MAX_STEPS).tooltip("Add a new step to the end");
		if (working.steps.size() > visibleSteps()) {
			button(contentX() + 90, by, 18, "^", FlatButton.Style.GHOST, () -> scrollSteps(-1)).enabled(stepScroll > 0);
			button(contentX() + 110, by, 18, "v", FlatButton.Style.GHOST, () -> scrollSteps(1))
					.enabled(stepScroll + visibleSteps() < working.steps.size());
		}
	}

	private static int parseIntSafe(String s, int fallback) {
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private void scrollSteps(int delta) {
		stepScroll += delta;
		rebuildWidgets();
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (tab == Tab.STEPS && working.steps.size() > visibleSteps()) {
			int before = stepScroll;
			stepScroll = Math.max(0, Math.min(working.steps.size() - visibleSteps(), stepScroll - (int) Math.signum(sy)));
			if (before != stepScroll) rebuildWidgets();
			return true;
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}

	private void move(int idx, int delta) {
		int to = idx + delta;
		if (to < 0 || to >= working.steps.size()) return;
		MacroStep s = working.steps.remove(idx);
		working.steps.add(to, s);
		rebuildWidgets();
	}

	private void addStep() {
		StepType defaultType = switch (working.type) {
			case COMMAND -> working.steps.isEmpty() ? StepType.COMMAND : StepType.WAIT_TICKS;
			case CRAFT -> StepType.CRAFT;
			case SEQUENCE -> StepType.COMMAND;
		};
		MacroStep step = MacroStep.of(defaultType);
		if (defaultType == StepType.WAIT_TICKS) step.amount = 20;
		minecraft.gui.setScreen(new StepEditorScreen(this, step, working.steps.size(), result -> working.steps.add(result)));
	}

	private void editStep(int idx) {
		minecraft.gui.setScreen(new StepEditorScreen(this, working.steps.get(idx).copy(), idx, result -> working.steps.set(idx, result)));
	}

	private void openRecipePicker(int idx) {
		MacroStep step = working.steps.get(idx);
		minecraft.gui.setScreen(new RecipeSelectorScreen(this, step.text, step.amount, (item, qty) -> {
			step.text = item;
			step.amount = qty;
			if (working.name.equals("New Macro")) working.name = "Craft " + MacroStep.prettyItem(item);
		}));
	}

	// ------------------------------------------------------------------ options tab

	private void buildOptions() {
		int x = contentX();
		int w = contentW();
		int colW = (w - 10) / 2;
		int labelW = 86;
		int y = panelY + 34;
		EditBox cooldown = numberField(x + labelW, y, colW - labelW, working.cooldownMs, "Minimum milliseconds between activations");
		cooldown.setResponder(s -> working.cooldownMs = parseIntSafe(s, 0));
		EditBox delay = numberField(x + colW + 10 + labelW, y, colW - labelW, working.startDelayMs, "Milliseconds to wait after the key press before starting");
		delay.setResponder(s -> working.startDelayMs = parseIntSafe(s, 0));
		y += 22;
		EditBox repeat = numberField(x + labelW, y, colW - labelW, working.repeatCount, "Run all steps this many times per activation (1-100)");
		repeat.setResponder(s -> working.repeatCount = parseIntSafe(s, 1));
		EditBox holdInterval = numberField(x + colW + 10 + labelW, y, colW - labelW, working.holdIntervalMs, "Milliseconds between runs while the key is held");
		holdInterval.setResponder(s -> working.holdIntervalMs = parseIntSafe(s, 500));
		y += 26;
		toggle(x, y, colW, "Hold to repeat", working.holdToRepeat, "Keep running while the key is held down", v -> working.holdToRepeat = v);
		toggle(x + colW + 10, y, colW, "Notifications", working.notify, "Show success/status notifications for this macro (errors always show)", v -> working.notify = v);
		y += 20;
		toggle(x, y, colW, "Auto-close crafting", working.autoClose, "Close a crafting screen this macro opened when it finishes", v -> working.autoClose = v);
		toggle(x + colW + 10, y, colW, "Stop on error", working.stopOnError, "Stop at the first failing step instead of continuing", v -> working.stopOnError = v);
		y += 26;
		EditBox servers = textField(x + labelW, y, w - labelW - 92, String.join(", ", working.servers), 512, "All servers",
				"Comma-separated server addresses this macro is limited to. Empty = everywhere. Use 'singleplayer' for local worlds.");
		servers.setResponder(s -> working.servers = parseServers(s));
		button(x + w - 88, y, 88, "+ This server", FlatButton.Style.NORMAL, () -> {
			String current = MacroManager.currentServer();
			if (!working.servers.contains(current)) working.servers.add(current);
			rebuildWidgets();
		}).tooltip("Limit this macro to the server you are on (" + MacroManager.currentServer() + ")");
	}

	private static List<String> parseServers(String s) {
		List<String> out = new ArrayList<>();
		for (String part : s.split(",")) {
			String p = part.trim();
			if (!p.isEmpty()) out.add(p);
		}
		return out;
	}

	// ------------------------------------------------------------------ save

	private void save() {
		errors = MacroValidator.validate(working);
		if (!errors.isEmpty()) {
			// Jump to the tab that has the problem.
			if (errors.stream().anyMatch(e -> e.startsWith("Step") || e.startsWith("Add at least"))) {
				if (tab != Tab.STEPS) switchTab(Tab.STEPS);
			} else if (tab != Tab.GENERAL) {
				switchTab(Tab.GENERAL);
			}
			return;
		}
		MacroValidator.sanitize(working);
		List<Macro> macros = MacroManager.get().macros();
		Macro saved = working.copy(false);
		int idx = original == null ? -1 : macros.indexOf(original);
		if (idx >= 0) {
			macros.set(idx, saved);
		} else {
			macros.add(saved);
		}
		MacroManager.get().markDirty();
		MacroManager.get().saveNow();
		onClose();
		list.onMacroSaved(saved);
		MacroManager.get().notifier().success("Saved " + saved.name, true);
	}

	// ------------------------------------------------------------------ drawing

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = contentX();
		switch (tab) {
			case GENERAL -> {
				int y = panelY + 34;
				label(g, "Name", x, y + 4);
				label(g, "Description", x, y + 28);
				label(g, "Keybind", x, y + 52);
				drawKeyWarnings(g, x + 80, y + 72);
				label(g, "Type", x, y + 86);
				g.text(font, ellipsize(working.type.description(), contentW() - 80 - 170), x + 80 + 168, y + 91, Theme.TEXT_FAINT, false);
			}
			case STEPS -> drawSteps(g, mouseX, mouseY);
			case OPTIONS -> {
				int y = panelY + 34;
				int colW = (contentW() - 10) / 2;
				label(g, "Cooldown ms", x, y + 4);
				label(g, "Start delay ms", x + colW + 10, y + 4);
				label(g, "Repeat count", x, y + 26);
				label(g, "Hold interval", x + colW + 10, y + 26);
				label(g, "Servers", x, y + 96);
			}
		}
		if (!errors.isEmpty()) {
			String text = errors.get(0) + (errors.size() > 1 ? "  (+" + (errors.size() - 1) + " more)" : "");
			g.text(font, ellipsize(text, panelW - 200), panelX + 12, panelY + panelH - 38, Theme.DANGER, false);
		}
	}

	private void drawKeyWarnings(GuiGraphicsExtractor g, int x, int y) {
		if (!working.key.isBound()) {
			g.text(font, "No key: the macro can only be run from this menu.", x, y, Theme.TEXT_FAINT, false);
			return;
		}
		List<Macro> conflicts = KeybindConflicts.conflictingMacros(working.key, MacroManager.get().macros(), working);
		if (working.enabled && !conflicts.isEmpty()) {
			g.text(font, ellipsize("Conflicts with macro \"" + conflicts.get(0).name + "\"", contentW() - 80), x, y, Theme.WARNING, false);
			return;
		}
		List<String> vanilla = KeybindConflicts.conflictingBindings(working.key, KeybindManager.otherBindings(minecraft));
		if (!vanilla.isEmpty()) {
			boolean suppressed = MacroManager.get().settings().suppressVanillaConflicts;
			g.text(font, ellipsize("Also bound to: " + String.join(", ", vanilla) + (suppressed ? " (macro takes priority)" : ""), contentW() - 80),
					x, y, Theme.WARNING, false);
		}
	}

	private void drawSteps(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int x = contentX();
		int w = contentW();
		int craftIdx = craftShortcut();
		if (craftIdx >= 0) {
			MacroStep craft = working.steps.get(craftIdx);
			int y = panelY + 34;
			Identifier id = Identifier.tryParse(craft.text);
			if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
				g.item(new ItemStack(BuiltInRegistries.ITEM.getValue(id)), x, y + 1);
			} else {
				Theme.rounded(g, x, y + 1, 16, 16, Theme.SURFACE);
			}
			label(g, "Quantity", x + w - 60 - font.width("Quantity") - 6, y + 5);
		}
		int y = stepsTop();
		if (working.steps.isEmpty()) {
			g.centeredText(font, "No steps yet. Click \"+ Add Step\".", panelX + panelW / 2, y + 20, Theme.TEXT_DIM);
			return;
		}
		int end = Math.min(working.steps.size(), stepScroll + visibleSteps());
		int textW = w - 110;
		for (int i = stepScroll; i < end; i++) {
			MacroStep s = working.steps.get(i);
			Theme.rounded(g, x - 4, y - 1, w + 4, STEP_ROW - 2, Theme.SURFACE);
			g.text(font, (i + 1) + ".", x, y + 4, Theme.TEXT_FAINT, false);
			String problem = MacroValidator.validateStep(s, i);
			g.text(font, ellipsize(s.summary(), textW - 16), x + 16, y + 4, problem == null ? Theme.TEXT : Theme.WARNING, false);
			if (inside(mouseX, mouseY, x, y, textW, STEP_ROW - 2)) {
				g.setTooltipForNextFrame(font, Component.literal(s.type.displayName() + ": " + s.type.description()
						+ (problem == null ? "" : "\nProblem: " + problem)), mouseX, mouseY);
			}
			y += STEP_ROW;
		}
		if (working.steps.size() > visibleSteps()) {
			g.text(font, (stepScroll + 1) + "-" + end + " of " + working.steps.size(), contentX() + 132, panelY + panelH - 21, Theme.TEXT_FAINT, false);
		}
	}

	/** Re-entry from step editor / recipe picker. */
	@Override
	public void added() {
		errors = new ArrayList<>();
	}

	static List<StepType> allStepTypes() {
		return Arrays.asList(StepType.values());
	}
}
