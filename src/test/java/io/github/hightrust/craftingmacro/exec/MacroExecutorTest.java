package io.github.hightrust.craftingmacro.exec;

import io.github.hightrust.craftingmacro.macro.KeyCombo;
import io.github.hightrust.craftingmacro.macro.Macro;
import io.github.hightrust.craftingmacro.macro.MacroStep;
import io.github.hightrust.craftingmacro.macro.MacroType;
import io.github.hightrust.craftingmacro.macro.StepType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** Executor and run state machine tests using fake handlers (no Minecraft needed). */
class MacroExecutorTest {
	private final AtomicLong clock = new AtomicLong(0);
	private final List<String> log = new ArrayList<>();
	private final Map<StepType, StepHandler> handlers = new EnumMap<>(StepType.class);
	private MacroExecutor executor;
	private final List<MacroRun> finished = new ArrayList<>();
	private int endHookCalls;

	/** COMMAND: instant, logs its text; "fail" fails. CRAFT: runs for `amount` ticks. OPEN_CRAFTING: never finishes. */
	@BeforeEach
	void setUp() {
		handlers.put(StepType.COMMAND, (ctx, step) -> {
			log.add(step.text);
			return step.text.equals("fail") ? StepResult.failed("boom") : StepResult.done();
		});
		handlers.put(StepType.CRAFT, new StepHandler() {
			@Override
			public StepResult start(RunContext ctx, MacroStep step) {
				log.add("craft-start");
				return StepResult.running();
			}

			@Override
			public StepResult tick(RunContext ctx, MacroStep step) {
				return ctx.stepTicks >= step.amount ? StepResult.done("crafted") : StepResult.running();
			}

			@Override
			public void cancel(RunContext ctx, MacroStep step) {
				log.add("craft-cancel");
			}
		});
		handlers.put(StepType.OPEN_CRAFTING, new StepHandler() {
			@Override
			public StepResult start(RunContext ctx, MacroStep step) {
				return StepResult.running();
			}

			@Override
			public StepResult tick(RunContext ctx, MacroStep step) {
				return StepResult.running();
			}

			@Override
			public void cancel(RunContext ctx, MacroStep step) {
				log.add("open-cancel");
			}

			@Override
			public int timeoutTicks(RunContext ctx, MacroStep step) {
				return 5;
			}
		});
		handlers.put(StepType.WAIT_TICKS, new StepHandler() {
			@Override
			public StepResult start(RunContext ctx, MacroStep step) {
				return StepResult.running();
			}

			@Override
			public StepResult tick(RunContext ctx, MacroStep step) {
				return ctx.stepTicks >= step.amount ? StepResult.done() : StepResult.running();
			}
		});
		handlers.put(StepType.REPEAT, (ctx, step) -> StepResult.done());
		executor = new MacroExecutor(handlers, clock::get);
		executor.setListener(new MacroExecutor.Listener() {
			@Override
			public void onStarted(MacroRun run) {
			}

			@Override
			public void onFinished(MacroRun run) {
				finished.add(run);
			}
		});
		executor.setEndHook(run -> endHookCalls++);
	}

	private static Macro macro(String name, MacroStep... steps) {
		Macro m = new Macro(name, MacroType.SEQUENCE, KeyCombo.unbound());
		for (MacroStep s : steps) m.steps.add(s);
		m.cooldownMs = 0;
		return m;
	}

	private void ticks(int n) {
		for (int i = 0; i < n; i++) executor.tick();
	}

	@Test
	void instantStepsRunInOrderInOneTick() {
		executor.start(macro("m", MacroStep.command("a"), MacroStep.command("b"), MacroStep.command("c")));
		ticks(1);
		assertEquals(List.of("a", "b", "c"), log);
		assertEquals(MacroRun.State.SUCCEEDED, finished.get(0).state());
		assertTrue(executor.isIdle());
		assertEquals(1, endHookCalls);
	}

	@Test
	void longStepIsTickDriven() {
		executor.start(macro("m", MacroStep.command("before"), MacroStep.craft("minecraft:x", 3), MacroStep.command("after")));
		ticks(1);
		assertEquals(List.of("before", "craft-start"), log);
		ticks(2);
		assertFalse(log.contains("after"));
		ticks(2);
		assertEquals(List.of("before", "craft-start", "after"), log);
		assertEquals(MacroRun.State.SUCCEEDED, finished.get(0).state());
	}

	@Test
	void startDelayIsRespected() {
		Macro m = macro("m", MacroStep.command("a"));
		m.startDelayMs = 100;
		executor.start(m);
		ticks(3);
		assertTrue(log.isEmpty());
		clock.addAndGet(100);
		ticks(1);
		assertEquals(List.of("a"), log);
	}

	@Test
	void stopOnErrorStopsRun() {
		executor.start(macro("m", MacroStep.command("a"), MacroStep.command("fail"), MacroStep.command("never")));
		ticks(1);
		assertEquals(List.of("a", "fail"), log);
		MacroRun run = finished.get(0);
		assertEquals(MacroRun.State.FAILED, run.state());
		assertEquals("boom", run.message());
	}

	@Test
	void continueOnErrorCountsWarnings() {
		Macro m = macro("m", MacroStep.command("fail"), MacroStep.command("after"));
		m.stopOnError = false;
		executor.start(m);
		ticks(1);
		assertEquals(List.of("fail", "after"), log);
		assertEquals(MacroRun.State.SUCCEEDED, finished.get(0).state());
		assertEquals(1, finished.get(0).warnings());
	}

	@Test
	void stepTimeoutCancelsAndFails() {
		executor.start(macro("m", MacroStep.of(StepType.OPEN_CRAFTING), MacroStep.command("never")));
		ticks(10);
		MacroRun run = finished.get(0);
		assertEquals(MacroRun.State.FAILED, run.state());
		assertTrue(run.message().startsWith("Timed out"), run.message());
		assertTrue(log.contains("open-cancel"), "timed-out step must be cancelled for cleanup");
		assertFalse(log.contains("never"));
	}

	@Test
	void stopAllCancelsActiveStep() {
		executor.start(macro("m", MacroStep.craft("minecraft:x", 100)));
		ticks(2);
		assertEquals(1, executor.stopAll("test"));
		assertEquals(List.of("craft-start", "craft-cancel"), log);
		assertEquals(MacroRun.State.CANCELLED, finished.get(0).state());
		assertEquals("test", finished.get(0).message());
		assertTrue(executor.isIdle());
		assertEquals(1, endHookCalls);
	}

	@Test
	void onlyOneInventoryMacroAtATime() {
		Macro craftA = macro("a", MacroStep.craft("minecraft:x", 10));
		Macro craftB = macro("b", MacroStep.craft("minecraft:y", 10));
		Macro command = macro("c", MacroStep.of(StepType.WAIT_TICKS));
		command.steps.get(0).amount = 10;
		assertEquals(MacroExecutor.StartResult.STARTED, executor.start(craftA));
		assertEquals(MacroExecutor.StartResult.INVENTORY_BUSY, executor.start(craftB));
		assertEquals(MacroExecutor.StartResult.STARTED, executor.start(command), "non-inventory macros may run concurrently");
		assertEquals(MacroExecutor.StartResult.ALREADY_RUNNING, executor.start(craftA));
		ticks(15);
		assertEquals(MacroExecutor.StartResult.STARTED, executor.start(craftB));
	}

	@Test
	void cooldownIsEnforcedByExecutor() {
		Macro m = macro("m", MacroStep.command("a"));
		m.cooldownMs = 1000;
		assertEquals(MacroExecutor.StartResult.STARTED, executor.start(m));
		ticks(1);
		assertEquals(MacroExecutor.StartResult.COOLDOWN, executor.start(m));
		assertEquals(MacroExecutor.StartResult.STARTED, executor.start(m, true), "hold-repeat bypasses the cooldown");
		ticks(1);
		clock.addAndGet(1000);
		assertEquals(MacroExecutor.StartResult.STARTED, executor.start(m));
	}

	@Test
	void repeatCountRunsStepsMultipleTimes() {
		Macro m = macro("m", MacroStep.command("x"));
		m.repeatCount = 4;
		executor.start(m);
		ticks(1);
		assertEquals(List.of("x", "x", "x", "x"), log);
	}

	@Test
	void handlerExceptionBecomesFailureNotCrash() {
		handlers.put(StepType.NOTIFY, (ctx, step) -> {
			throw new IllegalStateException("kaboom");
		});
		executor.start(macro("m", MacroStep.notify("hi")));
		ticks(1);
		assertEquals(MacroRun.State.FAILED, finished.get(0).state());
		assertTrue(finished.get(0).message().contains("kaboom"));
	}

	@Test
	void invalidExpansionIsRejectedAtStart() {
		Macro m = macro("m", MacroStep.command("a"));
		for (int i = 0; i < 20; i++) m.steps.add(MacroStep.repeat(i + 1, 100));
		assertEquals(MacroExecutor.StartResult.INVALID, executor.start(m));
		assertTrue(executor.isIdle());
	}

	@Test
	void interruptPausesThenResumesTheSameStep() {
		Macro m = new Macro("Rule", MacroType.SEQUENCE, KeyCombo.unbound());
		m.withStep(MacroStep.command("a")).withStep(MacroStep.craft("x", 10)).withStep(MacroStep.command("b"));
		MacroRun run = new MacroRun(m, handlers, clock::get);
		run.tick();
		assertEquals(List.of("a", "craft-start"), log);
		run.interrupt(1000, false, "test");
		assertTrue(run.paused());
		assertEquals("craft-cancel", log.get(log.size() - 1));
		run.tick();
		assertEquals(3, log.size(), "nothing runs while paused");
		clock.addAndGet(1000);
		run.tick();
		assertFalse(run.paused());
		assertEquals(List.of("a", "craft-start", "craft-cancel", "craft-start"), log);
	}

	@Test
	void interruptWithRestartGoesBackToStepOne() {
		Macro m = new Macro("Rule", MacroType.SEQUENCE, KeyCombo.unbound());
		m.withStep(MacroStep.command("a")).withStep(MacroStep.craft("x", 10));
		MacroRun run = new MacroRun(m, handlers, clock::get);
		run.tick();
		run.interrupt(0, true, "test");
		run.tick();
		assertEquals(List.of("a", "craft-start", "craft-cancel", "a", "craft-start"), log);
	}
}
