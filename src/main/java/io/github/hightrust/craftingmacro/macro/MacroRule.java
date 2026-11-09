package io.github.hightrust.craftingmacro.macro;

/**
 * An automation rule that watches a running macro: when its condition is met, the current step
 * is paused, an optional command runs (e.g. {@code /fix}), and after {@link #pauseMs} the macro
 * continues, restarts from step 1, or stops. Plain POJO persisted with Gson.
 */
public final class MacroRule {
	public enum Condition {
		EVERY("Every", "sec", "Fires on a timer while the macro runs"),
		DURABILITY("Durability at or below", "uses left", "Held item is about to break"),
		INVENTORY_FULL("Inventory full", "", "No empty slot left in your inventory"),
		ITEMS_ON_GROUND("Items on ground at least", "items", "Dropped items within 8 blocks of you");

		private final String label;
		private final String unit;
		private final String description;

		Condition(String label, String unit, String description) {
			this.label = label;
			this.unit = unit;
			this.description = description;
		}

		public String label() {
			return label;
		}

		public String unit() {
			return unit;
		}

		public String description() {
			return description;
		}

		public boolean hasAmount() {
			return !unit.isEmpty();
		}

		public int defaultAmount() {
			return switch (this) {
				case EVERY -> 300;
				case DURABILITY -> 20;
				case INVENTORY_FULL -> 0;
				case ITEMS_ON_GROUND -> 64;
			};
		}

		public Condition next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	public enum Then {
		RESUME("then continue"),
		RESTART("then restart"),
		STOP("then stop");

		private final String label;

		Then(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}

		public Then next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	public boolean enabled = true;
	public Condition condition = Condition.EVERY;
	/** Seconds (EVERY), remaining uses (DURABILITY) or item count (ITEMS_ON_GROUND). */
	public int amount = 300;
	/** Command to run when the rule fires; blank = none. */
	public String command = "";
	/** How long the macro stays paused after firing before it continues or restarts. */
	public int pauseMs = 1000;
	public Then then = Then.RESUME;

	public MacroRule() {
	}

	public MacroRule(Condition condition, int amount, String command, int pauseMs, Then then) {
		this.condition = condition;
		this.amount = amount;
		this.command = command;
		this.pauseMs = pauseMs;
		this.then = then;
	}

	public MacroRule copy() {
		MacroRule r = new MacroRule(condition, amount, command, pauseMs, then);
		r.enabled = enabled;
		return r;
	}

	/** "Durability at or below 20 uses left: /fix, then continue". */
	public String summary() {
		StringBuilder sb = new StringBuilder(condition == null ? "?" : condition.label());
		if (condition != null && condition.hasAmount()) sb.append(' ').append(amount).append(' ').append(condition.unit());
		sb.append(": ");
		if (command != null && !command.isBlank()) sb.append(command).append(", ");
		if (pauseMs > 0 && then != Then.STOP) sb.append("wait ").append(pauseMs / 1000.0).append("s ");
		sb.append(then == null ? "" : then.label());
		return sb.toString();
	}
}
