package io.github.hightrust.craftingmacro.exec;

/** Outcome of starting or ticking a step. */
public record StepResult(Status status, String message) {
	public enum Status { RUNNING, DONE, FAILED }

	private static final StepResult RUNNING = new StepResult(Status.RUNNING, "");
	private static final StepResult DONE = new StepResult(Status.DONE, "");

	public static StepResult running() {
		return RUNNING;
	}

	public static StepResult done() {
		return DONE;
	}

	public static StepResult done(String message) {
		return new StepResult(Status.DONE, message);
	}

	public static StepResult failed(String message) {
		return new StepResult(Status.FAILED, message);
	}

	public boolean isRunning() {
		return status == Status.RUNNING;
	}

	public boolean isFailed() {
		return status == Status.FAILED;
	}
}
