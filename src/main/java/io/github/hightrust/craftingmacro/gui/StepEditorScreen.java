package io.github.hightrust.craftingmacro.gui;

import io.github.hightrust.craftingmacro.MacroManager;
import io.github.hightrust.craftingmacro.gui.widget.FlatButton;
import io.github.hightrust.craftingmacro.macro.MacroStep;
import io.github.hightrust.craftingmacro.macro.MacroValidator;
import io.github.hightrust.craftingmacro.macro.StepType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/** Edits a single step. Fields shown depend on the step type. */
public final class StepEditorScreen extends BaseScreen {
	private final MacroStep step;
	private final int index;
	private final Consumer<MacroStep> onSave;
	private String error;

	public StepEditorScreen(Screen parent, MacroStep step, int index, Consumer<MacroStep> onSave) {
		super("Step " + (index + 1), parent);
		this.step = step;
		this.index = index;
		this.onSave = onSave;
	}

	@Override
	protected int preferredWidth() {
		return 360;
	}

	@Override
	protected int preferredHeight() {
		return 280;
	}

	/** Space taken by the type selector plus its wrapped description. */
	private int descOffset() {
		return 24 + font.split(Component.literal(step.type.description()), fieldW()).size() * 9 + 10;
	}

	private int fieldX() {
		return panelX + 100;
	}

	private int fieldW() {
		return panelW - 112;
	}

	@Override
	protected void build() {
		int y = panelY + 34;
		int fx = fieldX();
		int fw = fieldW();
		button(fx, y, 18, "<", FlatButton.Style.NORMAL, () -> cycle(-1)).tooltip("Previous step type");
		button(fx + 20, y, fw - 40, step.type.displayName(), FlatButton.Style.NORMAL, () -> cycle(1)).tooltip(step.type.description());
		button(fx + fw - 18, y, 18, ">", FlatButton.Style.NORMAL, () -> cycle(1)).tooltip("Next step type");
		y += descOffset();

		switch (step.type) {
			case COMMAND -> {
				EditBox cmd = textField(fx, y, fw, step.text, MacroValidator.MAX_TEXT, "/command arguments", "The command to send, e.g. /craft or /home");
				cmd.setResponder(s -> step.text = s);
			}
			case OPEN_CRAFTING -> {
				EditBox cmd = textField(fx, y, fw, step.text, MacroValidator.MAX_TEXT, "Default: " + MacroManager.get().settings().craftOpenCommand,
						"Optional command used to open crafting for this step. Blank = the global setting.");
				cmd.setResponder(s -> step.text = s);
			}
			case CRAFT -> {
				button(fx + 20, y, fw - 20, step.text.isBlank() ? "Choose recipe..." : MacroStep.prettyItem(step.text), FlatButton.Style.PRIMARY,
						() -> minecraft.gui.setScreen(new RecipeSelectorScreen(this, step.text, step.amount, (item, qty) -> {
							step.text = item;
							step.amount = qty;
						}))).tooltip("Pick from your unlocked crafting recipes");
				y += 24;
				amountField(fx, y, fw, "How many result items to craft");
			}
			case CRAFT_PATTERN -> button(fx + 62, y, fw - 62, "Design pattern...", FlatButton.Style.PRIMARY,
					() -> minecraft.gui.setScreen(new PatternDesignerScreen(this, step, r -> {
						step.pattern = r.pattern;
						step.amount = r.amount;
						step.text = r.text;
					}))).tooltip("Open the crafting grid designer");
			case WALK -> {
				String dir = step.text == null || step.text.isBlank() ? "forward" : step.text;
				button(fx, y, fw, "Direction: " + dir, FlatButton.Style.NORMAL, () -> {
					List<String> dirs = List.of("forward", "back", "left", "right", "forward+left", "forward+right");
					int i = dirs.indexOf(step.text == null || step.text.isBlank() ? "forward" : step.text);
					step.text = dirs.get((i + 1) % dirs.size());
					rebuildWidgets();
				}).tooltip("Click to change direction");
				y += 22;
				amountField(fx, y, fw, "How long to walk, in ticks (20 = 1 second)");
				y += 22;
				toggle(fx, y, fw, "Jump while walking", step.shift, null, v -> step.shift = v);
			}
			case LOOK -> {
				EditBox look = textField(fx, y, fw - 70, step.text, 32, "yaw,pitch", "Yaw: 0 south, 90 west, 180 north, -90 east. Pitch: -90 up, 90 down");
				look.setResponder(s -> step.text = s);
				button(fx + fw - 66, y, 66, "Current", FlatButton.Style.NORMAL, () -> {
					if (minecraft.player != null) {
						step.text = Math.round(net.minecraft.util.Mth.wrapDegrees(minecraft.player.getYRot())) + "," + Math.round(minecraft.player.getXRot());
						rebuildWidgets();
					}
				}).tooltip("Use the direction you are looking right now");
			}
			case MOUSE_CLICK -> {
				toggle(fx, y, fw, step.button == 1 ? "Right click (use / place)" : "Left click (attack / mine)", step.button == 1,
						"Off = left click, on = right click", v -> {
							step.button = v ? 1 : 0;
							rebuildWidgets();
						});
				y += 22;
				amountField(fx, y, fw, "0 = a single click. More = hold the button this many ticks");
			}
			case SELECT_ITEM -> {
				EditBox item = textField(fx, y, fw - 50, step.text, 64, "*_shovel", "Item id. * is a wildcard: *_shovel matches any shovel");
				item.setResponder(s -> step.text = s);
				button(fx + fw - 46, y, 46, "Pick", FlatButton.Style.NORMAL,
						() -> minecraft.gui.setScreen(new ItemPickerScreen(this, id -> step.text = id)));
			}
			case MINE_BLOCKS -> {
				EditBox blocks = textField(fx, y, fw, step.text, MacroValidator.MAX_TEXT, "minecraft:snow, minecraft:snow_block",
						"Comma-separated block ids; * wildcards allowed (e.g. *_log)");
				blocks.setResponder(s -> step.text = s);
				y += 20;
				button(fx, y, fw, "+ Block I'm looking at", FlatButton.Style.GHOST, () -> {
					if (minecraft.level != null && minecraft.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr
							&& minecraft.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
						String id = BuiltInRegistries.BLOCK.getKey(minecraft.level.getBlockState(bhr.getBlockPos()).getBlock()).toString();
						if (!step.text.contains(id)) step.text = step.text.isBlank() ? id : step.text + ", " + id;
						rebuildWidgets();
					}
				}).tooltip("Adds the block under your crosshair");
				y += 22;
				EditBox radius = numberField(fx, y, 40, step.slot <= 0 ? 6 : step.slot, "Search radius in blocks (1-16)");
				radius.setResponder(s -> step.slot = parse(s, 6));
				EditBox max = numberField(fx + fw - 50, y, 50, step.amount, "Stop after this many blocks (0 = until none left)");
				max.setResponder(s -> step.amount = parse(s, 0));
				y += 22;
				toggle(fx, y, fw, "Walk to blocks out of reach", step.shift, "Walks straight toward the nearest block; skips ones it can't reach",
						v -> step.shift = v);
			}
			case WAIT_TICKS -> amountField(fx, y, fw, "Ticks to wait (20 ticks = 1 second)");
			case WAIT_MS -> amountField(fx, y, fw, "Milliseconds to wait");
			case CLICK_SLOT -> {
				EditBox slot = numberField(fx, y, 60, step.slot, "Slot index in the open container. In a crafting table: 0 = result, 1-9 = grid, 10-45 = inventory.");
				slot.setResponder(s -> step.slot = parse(s, 0));
				y += 24;
				toggle(fx, y, fw, "Right click", step.button == 1, "Right-click instead of left-click", v -> step.button = v ? 1 : 0);
				y += 18;
				toggle(fx, y, fw, "Shift-click", step.shift, "Shift-click (quick move) the slot", v -> step.shift = v);
			}
			case MOVE_ITEMS -> {
				EditBox from = numberField(fx, y, 60, step.slot, "Source slot index in the open container");
				from.setResponder(s -> step.slot = parse(s, 0));
				EditBox to = numberField(fx + 100, y, 60, step.slot2, "Target slot index in the open container");
				to.setResponder(s -> step.slot2 = parse(s, 0));
			}
			case NOTIFY -> {
				EditBox msg = textField(fx, y, fw, step.text, MacroValidator.MAX_TEXT, "Message", "Text shown on screen");
				msg.setResponder(s -> step.text = s);
			}
			case REPEAT -> {
				EditBox back = numberField(fx, y, 60, step.slot, "How many steps before this one to repeat");
				back.setResponder(s -> step.slot = parse(s, 1));
				y += 24;
				amountField(fx, y, 60, "Extra repetitions (1 = run them twice in total)");
			}
			case CLOSE_SCREEN -> {
			}
		}

		int by = panelY + panelH - 26;
		button(panelX + panelW - 88, by, 80, "Done", FlatButton.Style.PRIMARY, this::save);
		button(panelX + panelW - 172, by, 80, "Cancel", FlatButton.Style.NORMAL, this::onClose);
	}

	private void amountField(int x, int y, int w, String tooltip) {
		EditBox amount = numberField(x, y, Math.min(80, w), step.amount, tooltip);
		amount.setResponder(s -> step.amount = parse(s, 0));
	}

	private static int parse(String s, int fallback) {
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private void cycle(int dir) {
		StepType[] types = StepType.values();
		StepType next = types[Math.floorMod(step.type.ordinal() + dir, types.length)];
		step.type = next;
		// sensible defaults when switching
		switch (next) {
			case WAIT_TICKS -> step.amount = step.amount <= 0 ? 20 : step.amount;
			case WAIT_MS -> step.amount = step.amount <= 0 ? 500 : step.amount;
			case CRAFT, REPEAT, CRAFT_PATTERN -> step.amount = Math.max(1, step.amount);
			case WALK -> step.amount = step.amount <= 1 ? 20 : step.amount;
			case MINE_BLOCKS -> {
				if (step.text == null || step.text.isBlank() || step.text.startsWith("/")) step.text = "minecraft:snow, minecraft:snow_block";
				step.amount = 0;
				if (step.slot <= 0) step.slot = 6;
			}
			case SELECT_ITEM -> {
				if (step.text == null || step.text.isBlank()) step.text = "*_shovel";
			}
			case LOOK -> {
				if (step.text == null || !step.text.contains(",")) step.text = "0,0";
			}
			default -> {
			}
		}
		if (next == StepType.REPEAT && step.slot < 1) step.slot = 1;
		error = null;
		rebuildWidgets();
	}

	private void save() {
		error = MacroValidator.validateStep(step, index);
		if (error != null) return;
		MacroValidator.sanitizeStep(step);
		onClose();
		onSave.accept(step);
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = panelX + 12;
		int y = panelY + 34;
		label(g, "Type", x, y + 5);
		g.textWithWordWrap(font, Component.literal(step.type.description()), fieldX(), y + 22, fieldW(), Theme.TEXT_FAINT, false);
		y += descOffset();
		switch (step.type) {
			case COMMAND, OPEN_CRAFTING -> label(g, "Command", x, y + 4);
			case CRAFT -> {
				label(g, "Recipe", x, y + 5);
				Identifier id = Identifier.tryParse(step.text);
				if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
					g.item(new ItemStack(BuiltInRegistries.ITEM.getValue(id)), fieldX(), y + 1);
				}
				label(g, "Quantity", x, y + 28);
			}
			case CRAFT_PATTERN -> {
				label(g, "Pattern", x, y + 5);
				List<String> grid = step.grid();
				for (int i = 0; i < 9; i++) {
					int cx = fieldX() + (i % 3) * 19;
					int cy = y + (i / 3) * 19;
					Theme.rounded(g, cx, cy, 18, 18, Theme.SURFACE);
					Identifier id = Identifier.tryParse(grid.get(i));
					if (!grid.get(i).isEmpty() && id != null && BuiltInRegistries.ITEM.containsKey(id)) {
						g.item(new ItemStack(BuiltInRegistries.ITEM.getValue(id)), cx + 1, cy + 1);
					}
				}
				g.text(font, step.amount + "x", fieldX() + 62, y + 26, Theme.TEXT_DIM, false);
			}
			case WALK -> {
				label(g, "Direction", x, y + 5);
				label(g, "Ticks", x, y + 26);
			}
			case LOOK -> label(g, "Yaw, pitch", x, y + 4);
			case MOUSE_CLICK -> {
				label(g, "Button", x, y + 3);
				label(g, "Hold ticks", x, y + 26);
			}
			case SELECT_ITEM -> label(g, "Item", x, y + 4);
			case MINE_BLOCKS -> {
				label(g, "Blocks", x, y + 4);
				label(g, "Radius", x, y + 46);
				label(g, "Max (0=all)", fieldX() + 46, y + 46);
			}
			case WAIT_TICKS -> label(g, "Ticks", x, y + 4);
			case WAIT_MS -> label(g, "Milliseconds", x, y + 4);
			case CLICK_SLOT -> label(g, "Slot", x, y + 4);
			case MOVE_ITEMS -> {
				label(g, "From slot", x, y + 4);
				label(g, "to", fieldX() + 70, y + 4);
			}
			case NOTIFY -> label(g, "Message", x, y + 4);
			case REPEAT -> {
				label(g, "Previous steps", x, y + 4);
				label(g, "Extra times", x, y + 28);
			}
			case CLOSE_SCREEN -> label(g, "No settings needed.", x, y + 4);
		}
		if (error != null) {
			g.text(font, ellipsize(error, panelW - 24), panelX + 12, panelY + panelH - 40, Theme.DANGER, false);
		}
	}
}
