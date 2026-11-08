package io.github.hightrust.craftingmacro.macro;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KeybindConflictsTest {
	private static Macro macro(String name, KeyCombo key) {
		return new Macro(name, MacroType.COMMAND, key).withStep(MacroStep.command("/" + name));
	}

	@Test
	void identicalCombosConflict() {
		Macro a = macro("a", KeyCombo.of("key.keyboard.k"));
		Macro b = macro("b", KeyCombo.of("key.keyboard.k"));
		Macro c = macro("c", KeyCombo.of("key.keyboard.j"));
		List<Macro> all = List.of(a, b, c);
		assertEquals(List.of(b), KeybindConflicts.conflictingMacros(a.key, all, a));
		assertTrue(KeybindConflicts.hasConflict(a, all));
		assertFalse(KeybindConflicts.hasConflict(c, all));
		Map<KeyCombo, List<Macro>> groups = KeybindConflicts.allConflicts(all);
		assertEquals(1, groups.size());
		assertEquals(List.of(a, b), groups.get(KeyCombo.of("key.keyboard.k")));
	}

	@Test
	void modifiersMakeCombosDistinct() {
		Macro plain = macro("plain", KeyCombo.of("key.keyboard.k"));
		Macro ctrl = macro("ctrl", new KeyCombo("key.keyboard.k", true, false, false));
		Macro ctrlShift = macro("cs", new KeyCombo("key.keyboard.k", true, true, false));
		List<Macro> all = List.of(plain, ctrl, ctrlShift);
		assertTrue(KeybindConflicts.allConflicts(all).isEmpty());
	}

	@Test
	void disabledAndUnboundMacrosNeverConflict() {
		Macro a = macro("a", KeyCombo.of("key.keyboard.k"));
		Macro b = macro("b", KeyCombo.of("key.keyboard.k"));
		b.enabled = false;
		Macro u1 = macro("u1", KeyCombo.unbound());
		Macro u2 = macro("u2", KeyCombo.unbound());
		List<Macro> all = List.of(a, b, u1, u2);
		assertFalse(KeybindConflicts.hasConflict(a, all));
		assertTrue(KeybindConflicts.allConflicts(all).isEmpty());
		assertFalse(u1.key.conflictsWith(u2.key));
	}

	@Test
	void selfIsNotAConflict() {
		Macro a = macro("a", KeyCombo.of("key.keyboard.k"));
		Macro edited = a.copy(false);
		assertTrue(KeybindConflicts.conflictingMacros(edited.key, List.of(a), edited).isEmpty());
	}

	@Test
	void vanillaBindingConflictsMatchBaseKey() {
		Map<String, String> vanilla = new LinkedHashMap<>();
		vanilla.put("Advancements", "key.keyboard.l");
		vanilla.put("Inventory", "key.keyboard.e");
		assertEquals(List.of("Advancements"), KeybindConflicts.conflictingBindings(KeyCombo.of("key.keyboard.l"), vanilla));
		assertEquals(List.of("Advancements"), KeybindConflicts.conflictingBindings(new KeyCombo("key.keyboard.l", true, false, false), vanilla));
		assertTrue(KeybindConflicts.conflictingBindings(KeyCombo.of("key.keyboard.k"), vanilla).isEmpty());
	}

	@Test
	void describe() {
		assertEquals("Ctrl+Shift+K", new KeyCombo("key.keyboard.k", true, true, false).describe());
		assertEquals("Unbound", KeyCombo.unbound().describe());
	}
}
