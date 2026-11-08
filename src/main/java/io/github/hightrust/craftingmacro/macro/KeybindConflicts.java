package io.github.hightrust.craftingmacro.macro;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Finds keybind conflicts between macros and with other (e.g. vanilla) key bindings. */
public final class KeybindConflicts {
	private KeybindConflicts() {
	}

	/** Enabled macros (other than {@code self}) whose combo is identical to {@code combo}. */
	public static List<Macro> conflictingMacros(KeyCombo combo, Collection<Macro> macros, Macro self) {
		List<Macro> out = new ArrayList<>();
		if (combo == null || !combo.isBound()) return out;
		for (Macro m : macros) {
			if (m == null || !m.enabled) continue;
			if (self != null && Objects.equals(m.id, self.id)) continue;
			if (combo.conflictsWith(m.key)) out.add(m);
		}
		return out;
	}

	/** Groups of two or more enabled macros sharing the exact same combo. */
	public static Map<KeyCombo, List<Macro>> allConflicts(Collection<Macro> macros) {
		Map<KeyCombo, List<Macro>> byCombo = new LinkedHashMap<>();
		for (Macro m : macros) {
			if (m == null || !m.enabled || m.key == null || !m.key.isBound()) continue;
			byCombo.computeIfAbsent(m.key, k -> new ArrayList<>()).add(m);
		}
		byCombo.values().removeIf(list -> list.size() < 2);
		return byCombo;
	}

	/**
	 * Names of other key bindings that use the same base key. Modifier-only differences
	 * still count, because most vanilla bindings ignore modifiers.
	 *
	 * @param otherBindings map of binding display name to key name (e.g. "Advancements" -> "key.keyboard.l")
	 */
	public static List<String> conflictingBindings(KeyCombo combo, Map<String, String> otherBindings) {
		List<String> out = new ArrayList<>();
		if (combo == null || !combo.isBound()) return out;
		for (Map.Entry<String, String> e : otherBindings.entrySet()) {
			if (combo.key.equals(e.getValue())) out.add(e.getKey());
		}
		return out;
	}

	public static boolean hasConflict(Macro macro, Collection<Macro> macros) {
		return macro != null && macro.enabled && !conflictingMacros(macro.key, macros, macro).isEmpty();
	}
}
