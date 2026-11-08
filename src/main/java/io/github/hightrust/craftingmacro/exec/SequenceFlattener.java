package io.github.hightrust.craftingmacro.exec;

import io.github.hightrust.craftingmacro.macro.MacroStep;
import io.github.hightrust.craftingmacro.macro.StepType;

import java.util.ArrayList;
import java.util.List;

/**
 * Expands REPEAT steps and the macro-level repeat count into a flat, ordered list of
 * executable steps. A hard cap protects against runaway nested repeats.
 */
public final class SequenceFlattener {
	public static final int MAX_FLAT_STEPS = 4096;

	private SequenceFlattener() {
	}

	public static List<MacroStep> flatten(List<MacroStep> steps, int repeatCount) {
		List<MacroStep> once = new ArrayList<>();
		for (MacroStep step : steps) {
			if (step == null || step.type == null) continue;
			if (step.type == StepType.REPEAT) {
				// The previous N *source* steps may already be expanded; repeat the tail of the
				// expanded list that corresponds to them.
				int back = countExpandedTail(steps, step, once);
				if (back <= 0) continue;
				List<MacroStep> block = new ArrayList<>(once.subList(once.size() - back, once.size()));
				for (int r = 0; r < step.amount; r++) {
					if (once.size() + block.size() > MAX_FLAT_STEPS) {
						throw new IllegalStateException("Macro expands to more than " + MAX_FLAT_STEPS + " steps");
					}
					once.addAll(block);
				}
			} else {
				if (once.size() + 1 > MAX_FLAT_STEPS) {
					throw new IllegalStateException("Macro expands to more than " + MAX_FLAT_STEPS + " steps");
				}
				once.add(step);
			}
		}
		int times = Math.max(1, repeatCount);
		if ((long) once.size() * times > MAX_FLAT_STEPS) {
			throw new IllegalStateException("Macro expands to more than " + MAX_FLAT_STEPS + " steps");
		}
		List<MacroStep> out = new ArrayList<>(once.size() * times);
		for (int i = 0; i < times; i++) out.addAll(once);
		return out;
	}

	/** How many expanded entries the previous {@code repeat.slot} source steps produced. */
	private static int countExpandedTail(List<MacroStep> source, MacroStep repeat, List<MacroStep> expanded) {
		int idx = indexOfIdentity(source, repeat);
		int n = Math.min(repeat.slot, idx);
		if (n <= 0) return 0;
		// Re-flatten the source prefix to find the expanded size before the window starts.
		List<MacroStep> before = source.subList(0, idx - n);
		int sizeBefore = flattenSizeNoMacroRepeat(before);
		return expanded.size() - sizeBefore;
	}

	private static int flattenSizeNoMacroRepeat(List<MacroStep> steps) {
		return flatten(new ArrayList<>(steps), 1).size();
	}

	private static int indexOfIdentity(List<MacroStep> list, MacroStep target) {
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i) == target) return i;
		}
		return -1;
	}
}
