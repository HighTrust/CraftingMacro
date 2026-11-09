package io.github.hightrust.craftingmacro.exec;

import io.github.hightrust.craftingmacro.macro.Macro;

import java.util.function.LongSupplier;

/** Mutable per-run state shared between the run and its step handlers. */
public final class RunContext {
	public final Macro macro;
	private final LongSupplier clock;

	/** Handler-private state for the current step; reset between steps. */
	public Object stepState;
	/** Set by handlers when this run opened a crafting screen (so it can be closed afterwards). */
	public boolean openedCraftingScreen;
	/** Counts items crafted during the run, for the completion notification. */
	public int itemsCrafted;
	/** Ticks spent in the current step. */
	public int stepTicks;
	/** Hold/toggle mode: crafting steps craft as much as possible instead of their quantity. */
	public boolean unlimited;

	public RunContext(Macro macro, LongSupplier clock) {
		this.macro = macro;
		this.clock = clock;
	}

	public long nowMs() {
		return clock.getAsLong();
	}
}
