package io.github.hightrust.craftingmacro.exec;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Tracks per-macro cooldowns against an injectable millisecond clock. */
public final class CooldownTracker {
	private final LongSupplier clock;
	private final Map<String, Long> lastActivation = new HashMap<>();

	public CooldownTracker(LongSupplier clock) {
		this.clock = clock;
	}

	/** Remaining cooldown in ms (0 when ready). */
	public long remaining(String macroId, int cooldownMs) {
		Long last = lastActivation.get(macroId);
		if (last == null || cooldownMs <= 0) return 0;
		long left = last + cooldownMs - clock.getAsLong();
		return Math.max(0, left);
	}

	public boolean isReady(String macroId, int cooldownMs) {
		return remaining(macroId, cooldownMs) == 0;
	}

	/** Atomically checks and marks. Returns false (without marking) while still cooling down. */
	public boolean tryActivate(String macroId, int cooldownMs) {
		if (!isReady(macroId, cooldownMs)) return false;
		lastActivation.put(macroId, clock.getAsLong());
		return true;
	}

	public void reset(String macroId) {
		lastActivation.remove(macroId);
	}

	public void clear() {
		lastActivation.clear();
	}
}
