package io.github.hightrust.craftingmacro.macro;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Validates and clamps macros. Used by the editor, on load, and on import. */
public final class MacroValidator {
	public static final int MAX_NAME = 48;
	public static final int MAX_DESCRIPTION = 256;
	public static final int MAX_TEXT = 256;
	public static final int MAX_STEPS = 64;
	public static final int MAX_COOLDOWN_MS = 600_000;
	public static final int MAX_DELAY_MS = 60_000;
	public static final int MAX_REPEAT = 100;
	public static final int MAX_CRAFT_QUANTITY = 2304; // 36 stacks of 64
	public static final int MAX_WAIT_TICKS = 20 * 60;
	public static final int MAX_WAIT_MS = 60_000;
	public static final int MAX_SLOT = 200;
	public static final int MAX_SERVERS = 32;

	private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
	private static final Pattern KEY_NAME = Pattern.compile("key\\.(keyboard|mouse)\\.[a-z0-9_.]+");

	private MacroValidator() {
	}

	/** Returns human-readable problems that block saving. Empty list = valid. */
	public static List<String> validate(Macro m) {
		List<String> errors = new ArrayList<>();
		if (m == null) {
			errors.add("Macro is missing");
			return errors;
		}
		if (m.name == null || m.name.isBlank()) errors.add("Name cannot be empty");
		if (m.steps == null || m.steps.isEmpty()) errors.add("Add at least one step");
		if (m.steps != null && m.steps.size() > MAX_STEPS) errors.add("Too many steps (max " + MAX_STEPS + ")");
		if (m.key != null && m.key.isBound() && !KEY_NAME.matcher(m.key.key).matches()) errors.add("Invalid key: " + m.key.key);
		if (m.steps != null) {
			for (int i = 0; i < m.steps.size(); i++) {
				String problem = validateStep(m.steps.get(i), i);
				if (problem != null) errors.add("Step " + (i + 1) + ": " + problem);
			}
		}
		return errors;
	}

	/** Returns a problem description for a step, or null if valid. {@code index} is its position in the list. */
	public static String validateStep(MacroStep s, int index) {
		if (s == null || s.type == null) return "unknown step type";
		return switch (s.type) {
			case COMMAND -> s.text == null || stripSlash(s.text).isBlank() ? "command is empty" : null;
			case CRAFT -> {
				if (s.text == null || !ITEM_ID.matcher(s.text).matches()) yield "choose a recipe";
				if (s.amount < 1) yield "quantity must be at least 1";
				yield null;
			}
			case CRAFT_PATTERN -> {
				boolean any = false;
				for (String id : s.grid()) {
					if (id.isEmpty()) continue;
					if (!ITEM_ID.matcher(id).matches()) yield "invalid item in pattern: " + id;
					any = true;
				}
				if (!any) yield "design a pattern (put at least one item in the grid)";
				if (s.amount < 1) yield "amount must be at least 1";
				yield null;
			}
			case WAIT_TICKS, WAIT_MS -> s.amount < 0 ? "wait cannot be negative" : null;
			case CLICK_SLOT -> s.slot < 0 ? "slot cannot be negative" : null;
			case MOVE_ITEMS -> {
				if (s.slot < 0 || s.slot2 < 0) yield "slots cannot be negative";
				if (s.slot == s.slot2) yield "source and target slot are the same";
				yield null;
			}
			case NOTIFY -> s.text == null || s.text.isBlank() ? "message is empty" : null;
			case REPEAT -> {
				if (s.slot < 1) yield "must repeat at least one previous step";
				if (s.slot > index) yield "there are only " + index + " previous step(s)";
				if (s.amount < 1) yield "repeat count must be at least 1";
				yield null;
			}
			case OPEN_CRAFTING, CLOSE_SCREEN -> null;
		};
	}

	/** Clamps every numeric field into its allowed range and trims strings. Never throws. */
	public static Macro sanitize(Macro m) {
		if (m.id == null || m.id.isBlank()) m.id = java.util.UUID.randomUUID().toString();
		m.name = clip(m.name == null ? "Unnamed" : m.name.strip(), MAX_NAME);
		if (m.name.isEmpty()) m.name = "Unnamed";
		m.description = clip(m.description == null ? "" : m.description, MAX_DESCRIPTION);
		if (m.key == null) m.key = KeyCombo.unbound();
		if (m.key.key == null || (!m.key.key.isEmpty() && !KEY_NAME.matcher(m.key.key).matches())) m.key = KeyCombo.unbound();
		if (m.type == null) m.type = MacroType.SEQUENCE;
		if (m.trigger == null) m.trigger = TriggerMode.CLICK;
		if (m.holdToRepeat) {
			if (m.trigger == TriggerMode.CLICK) m.trigger = TriggerMode.HOLD;
			m.holdToRepeat = false;
		}
		if (m.steps == null) m.steps = new ArrayList<>();
		m.steps.removeIf(s -> s == null || s.type == null);
		while (m.steps.size() > MAX_STEPS) m.steps.remove(m.steps.size() - 1);
		for (MacroStep s : m.steps) sanitizeStep(s);
		m.cooldownMs = clamp(m.cooldownMs, 0, MAX_COOLDOWN_MS);
		m.startDelayMs = clamp(m.startDelayMs, 0, MAX_DELAY_MS);
		m.repeatCount = clamp(m.repeatCount, 1, MAX_REPEAT);
		m.holdIntervalMs = clamp(m.holdIntervalMs, 50, MAX_DELAY_MS);
		if (m.servers == null) m.servers = new ArrayList<>();
		m.servers.removeIf(s -> s == null || s.isBlank());
		while (m.servers.size() > MAX_SERVERS) m.servers.remove(m.servers.size() - 1);
		m.servers.replaceAll(s -> clip(s.strip(), 128));
		return m;
	}

	public static void sanitizeStep(MacroStep s) {
		s.text = clip(s.text == null ? "" : s.text, MAX_TEXT);
		s.button = s.button == 1 ? 1 : 0;
		s.slot = clamp(s.slot, 0, MAX_SLOT);
		s.slot2 = clamp(s.slot2, 0, MAX_SLOT);
		switch (s.type) {
			case CRAFT -> {
				s.amount = clamp(s.amount, 1, MAX_CRAFT_QUANTITY);
				s.text = s.text.strip().toLowerCase(java.util.Locale.ROOT);
			}
			case CRAFT_PATTERN -> {
				s.amount = clamp(s.amount, 1, MAX_CRAFT_QUANTITY);
				List<String> g = s.grid();
				g.replaceAll(id -> id.strip().toLowerCase(java.util.Locale.ROOT));
				g.replaceAll(id -> ITEM_ID.matcher(id).matches() ? id : "");
				s.pattern = new ArrayList<>(g);
				s.text = clip(s.text.strip(), MAX_NAME);
			}
			case WAIT_TICKS -> s.amount = clamp(s.amount, 0, MAX_WAIT_TICKS);
			case WAIT_MS -> s.amount = clamp(s.amount, 0, MAX_WAIT_MS);
			case REPEAT -> {
				s.amount = clamp(s.amount, 1, MAX_REPEAT);
				s.slot = clamp(s.slot, 1, MAX_STEPS);
			}
			default -> s.amount = clamp(s.amount, 0, MAX_CRAFT_QUANTITY);
		}
	}

	public static String stripSlash(String command) {
		String c = command == null ? "" : command.strip();
		while (c.startsWith("/")) c = c.substring(1);
		return c.strip();
	}

	public static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	private static String clip(String s, int max) {
		return s.length() > max ? s.substring(0, max) : s;
	}
}
