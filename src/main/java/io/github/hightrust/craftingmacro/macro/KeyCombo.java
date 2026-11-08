package io.github.hightrust.craftingmacro.macro;

import java.util.Locale;
import java.util.Objects;

/**
 * A key plus optional modifiers. {@code key} is a Minecraft input key name such as
 * {@code key.keyboard.k}; names are stable across platforms and scancode tables.
 * An empty key means "unbound".
 */
public final class KeyCombo {
	public static final String UNBOUND = "";

	public String key = UNBOUND;
	public boolean ctrl;
	public boolean shift;
	public boolean alt;

	public KeyCombo() {
	}

	public KeyCombo(String key, boolean ctrl, boolean shift, boolean alt) {
		this.key = key == null ? UNBOUND : key;
		this.ctrl = ctrl;
		this.shift = shift;
		this.alt = alt;
	}

	public static KeyCombo of(String key) {
		return new KeyCombo(key, false, false, false);
	}

	public static KeyCombo unbound() {
		return new KeyCombo();
	}

	public boolean isBound() {
		return key != null && !key.isBlank();
	}

	public KeyCombo copy() {
		return new KeyCombo(key, ctrl, shift, alt);
	}

	/** Label without Minecraft's localized key names (used for tests and logs). */
	public String describe() {
		if (!isBound()) {
			return "Unbound";
		}
		String base = key.startsWith("key.keyboard.") ? key.substring("key.keyboard.".length()) : key;
		return prefix() + base.toUpperCase(Locale.ROOT).replace('.', ' ');
	}

	/** Modifier prefix like "Ctrl+Shift+". */
	public String prefix() {
		StringBuilder sb = new StringBuilder();
		if (ctrl) sb.append("Ctrl+");
		if (shift) sb.append("Shift+");
		if (alt) sb.append("Alt+");
		return sb.toString();
	}

	/** Two combos conflict when they would fire on exactly the same input. */
	public boolean conflictsWith(KeyCombo other) {
		return other != null && isBound() && other.isBound() && equals(other);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof KeyCombo k)) return false;
		return ctrl == k.ctrl && shift == k.shift && alt == k.alt && Objects.equals(key, k.key);
	}

	@Override
	public int hashCode() {
		return Objects.hash(key, ctrl, shift, alt);
	}

	@Override
	public String toString() {
		return describe();
	}
}
