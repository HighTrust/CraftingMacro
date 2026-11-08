package io.github.hightrust.craftingmacro.exec;

import io.github.hightrust.craftingmacro.macro.MacroStep;

/**
 * Executes one step type. Handlers are stateless singletons; per-run state lives in
 * {@link RunContext#stepState}. All methods are called on the client thread, once per tick at most.
 */
public interface StepHandler {
	/** Begin the step. Return DONE for instant steps, RUNNING to be ticked, FAILED on error. */
	StepResult start(RunContext ctx, MacroStep step);

	/** Advance a RUNNING step. */
	default StepResult tick(RunContext ctx, MacroStep step) {
		return StepResult.done();
	}

	/** Called when a RUNNING step is cancelled or times out. Must leave inventories safe. */
	default void cancel(RunContext ctx, MacroStep step) {
	}

	/** Hard upper bound in ticks before the step is failed as timed out. */
	default int timeoutTicks(RunContext ctx, MacroStep step) {
		return 200;
	}
}
