package io.github.hightrust.craftingmacro.exec;

import io.github.hightrust.craftingmacro.macro.Macro;
import io.github.hightrust.craftingmacro.macro.MacroStep;

import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Tick-driven state machine for one activation of a macro. Never blocks: each
 * {@link #tick()} does a bounded amount of work and returns.
 */
public final class MacroRun {
	public enum State { DELAY, RUNNING, SUCCEEDED, FAILED, CANCELLED }

	private final Macro macro;
	private final List<MacroStep> steps;
	private final Map<?, StepHandler> handlers;
	private final RunContext ctx;
	private final long startAtMs;

	private State state = State.DELAY;
	private int index = -1;
	private boolean stepActive;
	private String message = "";
	private int warnings;
	private String lastWarning = "";

	public MacroRun(Macro macro, Map<?, StepHandler> handlers, LongSupplier clock) {
		this.macro = macro;
		this.steps = SequenceFlattener.flatten(macro.steps, macro.repeatCount);
		this.handlers = handlers;
		this.ctx = new RunContext(macro, clock);
		this.startAtMs = clock.getAsLong() + Math.max(0, macro.startDelayMs);
	}

	public Macro macro() {
		return macro;
	}

	public RunContext context() {
		return ctx;
	}

	public State state() {
		return state;
	}

	public boolean isFinished() {
		return state == State.SUCCEEDED || state == State.FAILED || state == State.CANCELLED;
	}

	public String message() {
		return message;
	}

	public int warnings() {
		return warnings;
	}

	public String lastWarning() {
		return lastWarning;
	}

	/** 1-based index of the current step for display; 0 while waiting for the start delay. */
	public int currentStepNumber() {
		return Math.max(0, index + 1);
	}

	public int totalSteps() {
		return steps.size();
	}

	public MacroStep currentStep() {
		return index >= 0 && index < steps.size() ? steps.get(index) : null;
	}

	public void tick() {
		if (isFinished()) return;
		if (state == State.DELAY) {
			if (ctx.nowMs() < startAtMs) return;
			state = State.RUNNING;
		}
		// Instant steps chain within one tick, but cap the work to stay responsive.
		int budget = 32;
		while (budget-- > 0 && state == State.RUNNING) {
			if (!stepActive) {
				index++;
				if (index >= steps.size()) {
					state = State.SUCCEEDED;
					return;
				}
				ctx.stepState = null;
				ctx.stepTicks = 0;
				StepResult r = safeStart(steps.get(index));
				if (!handle(r)) return;
				if (r.isRunning()) {
					stepActive = true;
					return;
				}
			} else {
				MacroStep step = steps.get(index);
				ctx.stepTicks++;
				if (ctx.stepTicks > handlerFor(step).timeoutTicks(ctx, step)) {
					safeCancel(step);
					stepActive = false;
					if (!handle(StepResult.failed("Timed out: " + step.summary()))) return;
					continue;
				}
				StepResult r = safeTick(step);
				if (r.isRunning()) return;
				stepActive = false;
				if (!handle(r)) return;
				// A finished long-running step ends this tick's work so the next one sees fresh state.
				return;
			}
		}
	}

	/** Records a result; returns false if the run has stopped. */
	private boolean handle(StepResult r) {
		if (r.isFailed()) {
			if (macro.stopOnError) {
				state = State.FAILED;
				message = r.message();
				return false;
			}
			warnings++;
			lastWarning = r.message();
		} else if (r.status() == StepResult.Status.DONE && r.message() != null && !r.message().isEmpty()) {
			message = r.message();
		}
		return true;
	}

	public void cancel(String reason) {
		if (isFinished()) return;
		if (stepActive && index >= 0 && index < steps.size()) {
			safeCancel(steps.get(index));
		}
		stepActive = false;
		state = State.CANCELLED;
		message = reason;
	}

	private StepHandler handlerFor(MacroStep step) {
		StepHandler h = handlers.get(step.type);
		if (h == null) throw new IllegalStateException("No handler for " + step.type);
		return h;
	}

	private StepResult safeStart(MacroStep step) {
		try {
			return handlerFor(step).start(ctx, step);
		} catch (RuntimeException e) {
			return StepResult.failed("Error in " + step.summary() + ": " + e.getMessage());
		}
	}

	private StepResult safeTick(MacroStep step) {
		try {
			return handlerFor(step).tick(ctx, step);
		} catch (RuntimeException e) {
			safeCancel(step);
			return StepResult.failed("Error in " + step.summary() + ": " + e.getMessage());
		}
	}

	private void safeCancel(MacroStep step) {
		try {
			handlerFor(step).cancel(ctx, step);
		} catch (RuntimeException ignored) {
			// Cancellation must never propagate; the executor still ends the run.
		}
	}
}
