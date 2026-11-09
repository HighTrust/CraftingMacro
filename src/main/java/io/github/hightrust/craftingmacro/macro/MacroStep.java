package io.github.hightrust.craftingmacro.macro;

import java.util.Objects;

/**
 * One step of a macro. Fields are interpreted according to {@link #type}:
 * <ul>
 *   <li>COMMAND: {@code text} = command</li>
 *   <li>OPEN_CRAFTING: {@code text} = optional open-command override</li>
 *   <li>CRAFT: {@code text} = result item id, {@code amount} = result items wanted</li>
 *   <li>WAIT_TICKS / WAIT_MS: {@code amount} = duration</li>
 *   <li>CLICK_SLOT: {@code slot} = slot index, {@code button} 0 = left / 1 = right, {@code shift} = shift-click</li>
 *   <li>MOVE_ITEMS: {@code slot} = from, {@code slot2} = to</li>
 *   <li>NOTIFY: {@code text} = message</li>
 *   <li>REPEAT: {@code slot} = how many previous steps, {@code amount} = extra repetitions</li>
 * </ul>
 * Plain mutable POJO so Gson can (de)serialize it directly.
 */
public final class MacroStep {
	public StepType type = StepType.NOTIFY;
	public String text = "";
	public int amount = 1;
	public int slot = 0;
	public int slot2 = 0;
	public int button = 0;
	public boolean shift = false;
	/** CRAFT_PATTERN: 9 item ids (row-major 3x3, "" = empty). */
	public java.util.List<String> pattern = new java.util.ArrayList<>();

	public MacroStep() {
	}

	public MacroStep(StepType type) {
		this.type = type;
	}

	public static MacroStep of(StepType type) {
		return new MacroStep(type);
	}

	public static MacroStep command(String command) {
		MacroStep s = new MacroStep(StepType.COMMAND);
		s.text = command;
		return s;
	}

	public static MacroStep craft(String itemId, int quantity) {
		MacroStep s = new MacroStep(StepType.CRAFT);
		s.text = itemId;
		s.amount = quantity;
		return s;
	}

	public static MacroStep waitTicks(int ticks) {
		MacroStep s = new MacroStep(StepType.WAIT_TICKS);
		s.amount = ticks;
		return s;
	}

	public static MacroStep waitMs(int ms) {
		MacroStep s = new MacroStep(StepType.WAIT_MS);
		s.amount = ms;
		return s;
	}

	public static MacroStep notify(String message) {
		MacroStep s = new MacroStep(StepType.NOTIFY);
		s.text = message;
		return s;
	}

	public static MacroStep repeat(int previousSteps, int extraTimes) {
		MacroStep s = new MacroStep(StepType.REPEAT);
		s.slot = previousSteps;
		s.amount = extraTimes;
		return s;
	}

	public MacroStep copy() {
		MacroStep s = new MacroStep(type);
		s.text = text;
		s.amount = amount;
		s.slot = slot;
		s.slot2 = slot2;
		s.button = button;
		s.shift = shift;
		s.pattern = new java.util.ArrayList<>(pattern == null ? java.util.List.of() : pattern);
		return s;
	}

	/** Pattern padded/trimmed to exactly 9 entries. */
	public java.util.List<String> grid() {
		java.util.List<String> g = new java.util.ArrayList<>();
		for (int i = 0; i < 9; i++) {
			String id = pattern != null && i < pattern.size() && pattern.get(i) != null ? pattern.get(i) : "";
			g.add(id);
		}
		return g;
	}

	public static MacroStep pattern(java.util.List<String> grid, int crafts) {
		MacroStep s = new MacroStep(StepType.CRAFT_PATTERN);
		s.pattern = new java.util.ArrayList<>(grid);
		s.amount = crafts;
		return s;
	}

	/** Short human-readable summary used in lists. */
	public String summary() {
		if (type == null) {
			return "Invalid step";
		}
		return switch (type) {
			case COMMAND -> "Run " + (text == null || text.isBlank() ? "<empty>" : text);
			case OPEN_CRAFTING -> "Open crafting" + (text == null || text.isBlank() ? "" : " via " + text);
			case CRAFT -> "Craft " + amount + "x " + prettyItem(text);
			case CRAFT_PATTERN -> "Craft pattern " + amount + "x" + (text == null || text.isBlank() ? "" : " (" + text + ")");
			case WAIT_TICKS -> "Wait " + amount + " ticks";
			case WAIT_MS -> "Wait " + amount + " ms";
			case CLICK_SLOT -> (shift ? "Shift-" : "") + (button == 1 ? "right" : "left") + "-click slot " + slot;
			case MOVE_ITEMS -> "Move slot " + slot + " -> " + slot2;
			case CLOSE_SCREEN -> "Close screen";
			case NOTIFY -> "Notify \"" + text + "\"";
			case REPEAT -> "Repeat previous " + slot + " step(s) " + amount + "x more";
		};
	}

	/** "minecraft:snow_block" -> "Snow Block". */
	public static String prettyItem(String id) {
		if (id == null || id.isBlank()) {
			return "<no recipe>";
		}
		String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		StringBuilder sb = new StringBuilder();
		for (String part : path.split("_")) {
			if (part.isEmpty()) continue;
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return sb.toString();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof MacroStep s)) return false;
		return type == s.type && amount == s.amount && slot == s.slot && slot2 == s.slot2
				&& button == s.button && shift == s.shift && Objects.equals(text, s.text) && Objects.equals(grid(), s.grid());
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, text, amount, slot, slot2, button, shift, grid());
	}
}
