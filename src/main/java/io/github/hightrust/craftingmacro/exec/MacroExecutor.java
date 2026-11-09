package io.github.hightrust.craftingmacro.exec;

import io.github.hightrust.craftingmacro.macro.Macro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Schedules macro runs. At most one inventory-mutating run is active at a time; command-only
 * macros may run alongside it. Driven by {@link #tick()} from the client tick event.
 */
public final class MacroExecutor {
	public enum StartResult { STARTED, COOLDOWN, ALREADY_RUNNING, INVENTORY_BUSY, INVALID }

	public interface Listener {
		void onStarted(MacroRun run);

		void onFinished(MacroRun run);
	}

	private final Map<?, StepHandler> handlers;
	private final LongSupplier clock;
	private final CooldownTracker cooldowns;
	private final List<MacroRun> runs = new ArrayList<>();
	private Listener listener;
	private RunEndHook endHook = run -> { };
	private String lastStartError = "";

	/** Invoked after every run ends (success, failure or cancel); used for auto-close and cleanup. */
	public interface RunEndHook {
		void onRunEnd(MacroRun run);
	}

	public MacroExecutor(Map<?, StepHandler> handlers, LongSupplier clock) {
		this.handlers = handlers;
		this.clock = clock;
		this.cooldowns = new CooldownTracker(clock);
	}

	public void setListener(Listener listener) {
		this.listener = listener;
	}

	public void setEndHook(RunEndHook hook) {
		this.endHook = Objects.requireNonNull(hook);
	}

	public CooldownTracker cooldowns() {
		return cooldowns;
	}

	public String lastStartError() {
		return lastStartError;
	}

	public StartResult start(Macro macro) {
		return start(macro, false);
	}

	/** @param ignoreCooldown used by hold-to-repeat, which paces itself */
	public StartResult start(Macro macro, boolean ignoreCooldown) {
		return start(macro, ignoreCooldown, false);
	}

	/** @param unlimited crafting steps craft as much as possible (hold / toggle mode) */
	public StartResult start(Macro macro, boolean ignoreCooldown, boolean unlimited) {
		if (isRunning(macro.id)) return StartResult.ALREADY_RUNNING;
		if (macro.mutatesInventory() && inventoryBusy()) return StartResult.INVENTORY_BUSY;
		if (!ignoreCooldown && !cooldowns.isReady(macro.id, macro.cooldownMs)) return StartResult.COOLDOWN;
		MacroRun run;
		try {
			run = new MacroRun(macro, handlers, clock, unlimited);
		} catch (RuntimeException e) {
			lastStartError = e.getMessage();
			return StartResult.INVALID;
		}
		cooldowns.tryActivate(macro.id, ignoreCooldown ? 0 : macro.cooldownMs);
		runs.add(run);
		if (listener != null) listener.onStarted(run);
		return StartResult.STARTED;
	}

	public void tick() {
		if (runs.isEmpty()) return;
		for (MacroRun run : new ArrayList<>(runs)) {
			run.tick();
			if (run.isFinished()) finish(run);
		}
	}

	private void finish(MacroRun run) {
		runs.remove(run);
		try {
			endHook.onRunEnd(run);
		} finally {
			if (listener != null) listener.onFinished(run);
		}
	}

	public void cancel(String macroId, String reason) {
		for (MacroRun run : new ArrayList<>(runs)) {
			if (run.macro().id.equals(macroId)) {
				run.cancel(reason);
				finish(run);
			}
		}
	}

	/** Emergency stop: cancels everything immediately. Returns how many runs were stopped. */
	public int stopAll(String reason) {
		List<MacroRun> copy = new ArrayList<>(runs);
		for (MacroRun run : copy) {
			run.cancel(reason);
			finish(run);
		}
		return copy.size();
	}

	public boolean isRunning(String macroId) {
		for (MacroRun run : runs) {
			if (run.macro().id.equals(macroId)) return true;
		}
		return false;
	}

	public boolean inventoryBusy() {
		for (MacroRun run : runs) {
			if (run.macro().mutatesInventory()) return true;
		}
		return false;
	}

	public List<MacroRun> activeRuns() {
		return Collections.unmodifiableList(runs);
	}

	public boolean isIdle() {
		return runs.isEmpty();
	}
}
