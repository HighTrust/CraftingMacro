package io.github.hightrust.craftingmacro.exec;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class CooldownTrackerTest {
	@Test
	void cooldownBlocksUntilElapsed() {
		AtomicLong now = new AtomicLong(1000);
		CooldownTracker t = new CooldownTracker(now::get);
		assertTrue(t.tryActivate("m", 500));
		assertFalse(t.tryActivate("m", 500));
		assertEquals(500, t.remaining("m", 500));
		now.addAndGet(499);
		assertFalse(t.isReady("m", 500));
		assertEquals(1, t.remaining("m", 500));
		now.addAndGet(1);
		assertTrue(t.isReady("m", 500));
		assertTrue(t.tryActivate("m", 500));
	}

	@Test
	void macrosAreIndependent() {
		AtomicLong now = new AtomicLong(0);
		CooldownTracker t = new CooldownTracker(now::get);
		assertTrue(t.tryActivate("a", 1000));
		assertTrue(t.tryActivate("b", 1000));
		assertFalse(t.tryActivate("a", 1000));
	}

	@Test
	void zeroCooldownAlwaysReady() {
		CooldownTracker t = new CooldownTracker(() -> 5);
		assertTrue(t.tryActivate("m", 0));
		assertTrue(t.tryActivate("m", 0));
	}

	@Test
	void resetClearsCooldown() {
		CooldownTracker t = new CooldownTracker(() -> 5);
		t.tryActivate("m", 10_000);
		t.reset("m");
		assertTrue(t.isReady("m", 10_000));
	}

	@Test
	void failedActivationDoesNotExtendCooldown() {
		AtomicLong now = new AtomicLong(0);
		CooldownTracker t = new CooldownTracker(now::get);
		t.tryActivate("m", 100);
		now.set(50);
		assertFalse(t.tryActivate("m", 100));
		now.set(100);
		assertTrue(t.isReady("m", 100), "a rejected press must not restart the cooldown");
	}
}
