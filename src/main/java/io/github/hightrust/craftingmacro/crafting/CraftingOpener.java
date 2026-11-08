package io.github.hightrust.craftingmacro.crafting;

import io.github.hightrust.craftingmacro.exec.StepResult;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Small state machine that gets a 3x3 crafting menu open: uses the crafting table under the
 * crosshair, otherwise the configured open command (e.g. {@code /craft}), then waits until the
 * server actually opens the menu. Success is only reported once the client has the server's menu.
 */
public final class CraftingOpener {
	private enum Phase { INIT, CLOSING_OTHER, WAITING, DONE }

	private final String openCommand;
	private final boolean useLookedAtTable;
	private final int timeoutTicks;
	private Phase phase = Phase.INIT;
	private int waited;
	private boolean opened;
	private String via = "";

	public CraftingOpener(String openCommand, boolean useLookedAtTable, int timeoutTicks) {
		this.openCommand = openCommand == null ? "" : openCommand.strip();
		this.useLookedAtTable = useLookedAtTable;
		this.timeoutTicks = timeoutTicks;
	}

	/** True if this opener opened the screen (so the macro may close it later). */
	public boolean openedScreen() {
		return opened;
	}

	public StepResult tick() {
		if (!ScreenController.inWorld()) return StepResult.failed("Not in a world");
		switch (phase) {
			case INIT -> {
				if (ScreenController.isCraftingTableOpen()) {
					phase = Phase.DONE;
					return StepResult.done();
				}
				if (!ScreenController.isInventoryMenuActive()) {
					// Another container is open; close it first through the normal path.
					ScreenController.closeContainer();
					phase = Phase.CLOSING_OTHER;
					return StepResult.running();
				}
				return trigger();
			}
			case CLOSING_OTHER -> {
				return trigger();
			}
			case WAITING -> {
				if (ScreenController.isCraftingTableOpen()) {
					phase = Phase.DONE;
					opened = true;
					return StepResult.done();
				}
				if (++waited > timeoutTicks) {
					return StepResult.failed("Crafting interface did not open via " + via
							+ (via.startsWith("/") ? " (is it available on this server?)" : ""));
				}
				return StepResult.running();
			}
			default -> {
				return StepResult.done();
			}
		}
	}

	private StepResult trigger() {
		BlockHitResult table = useLookedAtTable ? ScreenController.lookedAtCraftingTable() : null;
		if (table != null) {
			ScreenController.useBlock(table);
			via = "the crafting table";
		} else if (!openCommand.isEmpty()) {
			ScreenController.sendCommand(openCommand);
			via = openCommand.startsWith("/") ? openCommand : "/" + openCommand;
		} else {
			return StepResult.failed("Crafting interface not available: look at a crafting table or set an open command in Settings");
		}
		phase = Phase.WAITING;
		waited = 0;
		return StepResult.running();
	}
}
