package io.github.hightrust.craftingmacro.macro;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MacroValidatorTest {
	private static Macro valid() {
		return new Macro("Snow", MacroType.CRAFT, KeyCombo.of("key.keyboard.l")).withStep(MacroStep.craft("minecraft:snow_block", 1));
	}

	@Test
	void validMacroHasNoErrors() {
		assertEquals(List.of(), MacroValidator.validate(valid()));
	}

	@Test
	void emptyNameAndNoStepsAreErrors() {
		Macro m = new Macro();
		m.name = "  ";
		List<String> errors = MacroValidator.validate(m);
		assertTrue(errors.contains("Name cannot be empty"));
		assertTrue(errors.contains("Add at least one step"));
	}

	@Test
	void stepProblemsAreReported() {
		assertEquals("command is empty", MacroValidator.validateStep(MacroStep.command("/"), 0));
		assertEquals("choose a recipe", MacroValidator.validateStep(MacroStep.craft("", 1), 0));
		assertEquals("choose a recipe", MacroValidator.validateStep(MacroStep.craft("snow block", 1), 0));
		assertEquals("quantity must be at least 1", MacroValidator.validateStep(MacroStep.craft("minecraft:snow", 0), 0));
		assertEquals("message is empty", MacroValidator.validateStep(MacroStep.notify(""), 0));
		MacroStep move = MacroStep.of(StepType.MOVE_ITEMS);
		move.slot = 3;
		move.slot2 = 3;
		assertNotNull(MacroValidator.validateStep(move, 0));
		assertNotNull(MacroValidator.validateStep(MacroStep.repeat(2, 1), 1), "can't repeat more steps than exist before it");
		assertNull(MacroValidator.validateStep(MacroStep.repeat(1, 1), 1));
		assertNull(MacroValidator.validateStep(MacroStep.of(StepType.CLOSE_SCREEN), 0));
	}

	@Test
	void invalidKeyNameIsAnError() {
		Macro m = valid();
		m.key = KeyCombo.of("rm -rf");
		assertFalse(MacroValidator.validate(m).isEmpty());
	}

	@Test
	void sanitizeClampsEverything() {
		Macro m = valid();
		m.name = "x".repeat(500);
		m.cooldownMs = -10;
		m.startDelayMs = Integer.MAX_VALUE;
		m.repeatCount = 0;
		m.steps.get(0).amount = 1_000_000;
		m.steps.add(null);
		m.servers.add("   ");
		MacroValidator.sanitize(m);
		assertEquals(MacroValidator.MAX_NAME, m.name.length());
		assertEquals(0, m.cooldownMs);
		assertEquals(MacroValidator.MAX_DELAY_MS, m.startDelayMs);
		assertEquals(1, m.repeatCount);
		assertEquals(MacroValidator.MAX_CRAFT_QUANTITY, m.steps.get(0).amount);
		assertEquals(1, m.steps.size());
		assertTrue(m.servers.isEmpty());
	}

	@Test
	void stripSlashHandlesVariants() {
		assertEquals("craft", MacroValidator.stripSlash("/craft"));
		assertEquals("craft", MacroValidator.stripSlash("  //craft "));
		assertEquals("home base", MacroValidator.stripSlash("/home base"));
		assertEquals("", MacroValidator.stripSlash("/"));
	}

	@Test
	void serverAvailability() {
		Macro m = valid();
		assertTrue(m.availableOn("anything"));
		m.servers.add("Play.Example.net");
		assertTrue(m.availableOn("play.example.net:25565"));
		assertFalse(m.availableOn("other.net"));
		assertFalse(m.availableOn(null));
		m.servers.add("singleplayer");
		assertTrue(m.availableOn(null));
	}

	@Test
	void mutatesInventoryDetection() {
		assertTrue(valid().mutatesInventory());
		Macro cmd = new Macro("c", MacroType.COMMAND, KeyCombo.unbound()).withStep(MacroStep.command("/home")).withStep(MacroStep.waitTicks(5));
		assertFalse(cmd.mutatesInventory());
	}

	@Test
	@SuppressWarnings("deprecation")
	void legacyHoldFlagMigratesToHoldMode() {
		Macro m = valid();
		m.holdToRepeat = true;
		MacroValidator.sanitize(m);
		assertEquals(TriggerMode.HOLD, m.trigger);
		assertFalse(m.holdToRepeat);
		Macro t = valid();
		t.trigger = null;
		MacroValidator.sanitize(t);
		assertEquals(TriggerMode.CLICK, t.trigger);
	}

	@Test
	void patternStepValidation() {
		MacroStep empty = MacroStep.pattern(List.of(), 1);
		assertNotNull(MacroValidator.validateStep(empty, 0));
		MacroStep stick = MacroStep.pattern(List.of("", "minecraft:oak_planks", "", "", "minecraft:oak_planks"), 4);
		assertNull(MacroValidator.validateStep(stick, 0));
		assertEquals(9, stick.grid().size());
		assertEquals("minecraft:oak_planks", stick.grid().get(4));
		assertEquals("", stick.grid().get(8));
		MacroStep bad = MacroStep.pattern(List.of("not an id"), 1);
		assertNotNull(MacroValidator.validateStep(bad, 0));
		MacroValidator.sanitizeStep(bad);
		assertEquals("", bad.grid().get(0), "invalid ids are dropped on sanitize");
		assertTrue(stick.copy().equals(stick));
		assertTrue(MacroStep.of(StepType.CRAFT_PATTERN).type.mutatesInventory());
	}

	@Test
	void copyIsDeep() {
		Macro m = valid();
		Macro c = m.copy(true);
		assertNotEquals(m.id, c.id);
		c.steps.get(0).amount = 99;
		c.key.ctrl = true;
		assertEquals(1, m.steps.get(0).amount);
		assertFalse(m.key.ctrl);
		assertEquals(m.id, m.copy(false).id);
	}
}
