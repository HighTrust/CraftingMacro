package io.github.hightrust.craftingmacro.exec;

import io.github.hightrust.craftingmacro.macro.MacroStep;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SequenceFlattenerTest {
	private static List<String> texts(List<MacroStep> steps) {
		List<String> out = new ArrayList<>();
		for (MacroStep s : steps) out.add(s.text);
		return out;
	}

	@Test
	void keepsOrder() {
		List<MacroStep> steps = List.of(MacroStep.command("a"), MacroStep.command("b"), MacroStep.command("c"));
		assertEquals(List.of("a", "b", "c"), texts(SequenceFlattener.flatten(steps, 1)));
	}

	@Test
	void macroRepeatCountRepeatsWholeList() {
		List<MacroStep> steps = List.of(MacroStep.command("a"), MacroStep.command("b"));
		assertEquals(List.of("a", "b", "a", "b", "a", "b"), texts(SequenceFlattener.flatten(steps, 3)));
		assertEquals(List.of("a", "b"), texts(SequenceFlattener.flatten(steps, 0)), "repeat < 1 runs once");
	}

	@Test
	void repeatStepRepeatsPreviousSteps() {
		List<MacroStep> steps = List.of(
				MacroStep.command("open"),
				MacroStep.command("craft"),
				MacroStep.command("collect"),
				MacroStep.repeat(2, 2),
				MacroStep.command("close"));
		assertEquals(List.of("open", "craft", "collect", "craft", "collect", "craft", "collect", "close"),
				texts(SequenceFlattener.flatten(steps, 1)));
	}

	@Test
	void nestedRepeatsExpandCorrectly() {
		List<MacroStep> steps = new ArrayList<>(List.of(
				MacroStep.command("a"),
				MacroStep.repeat(1, 1), // a a
				MacroStep.command("b"),
				MacroStep.repeat(3, 1))); // (a a b) twice — the window covers the expanded first repeat
		assertEquals(List.of("a", "a", "b", "a", "a", "b"), texts(SequenceFlattener.flatten(steps, 1)));
	}

	@Test
	void repeatWithNothingBeforeItIsIgnored() {
		List<MacroStep> steps = List.of(MacroStep.repeat(1, 5), MacroStep.command("x"));
		assertEquals(List.of("x"), texts(SequenceFlattener.flatten(steps, 1)));
	}

	@Test
	void runawayExpansionIsRejected() {
		List<MacroStep> steps = new ArrayList<>();
		steps.add(MacroStep.command("a"));
		for (int i = 0; i < 20; i++) steps.add(MacroStep.repeat(i + 1, 100));
		assertThrows(IllegalStateException.class, () -> SequenceFlattener.flatten(steps, 1));
	}
}
