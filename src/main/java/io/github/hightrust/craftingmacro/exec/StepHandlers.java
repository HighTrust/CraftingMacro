package io.github.hightrust.craftingmacro.exec;

import io.github.hightrust.craftingmacro.MacroManager;
import io.github.hightrust.craftingmacro.config.Settings;
import io.github.hightrust.craftingmacro.crafting.CraftingOpener;
import io.github.hightrust.craftingmacro.crafting.CraftingSession;
import io.github.hightrust.craftingmacro.crafting.ScreenController;
import io.github.hightrust.craftingmacro.macro.MacroStep;
import io.github.hightrust.craftingmacro.macro.StepType;
import io.github.hightrust.craftingmacro.notify.Notifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/** Minecraft-backed handlers for every {@link StepType}. */
public final class StepHandlers {
	private StepHandlers() {
	}

	public static Map<StepType, StepHandler> create(Supplier<Settings> settings) {
		Map<StepType, StepHandler> map = new EnumMap<>(StepType.class);
		map.put(StepType.COMMAND, new CommandHandler());
		map.put(StepType.OPEN_CRAFTING, new OpenCraftingHandler(settings));
		map.put(StepType.CRAFT, new CraftHandler(settings));
		map.put(StepType.WAIT_TICKS, new WaitTicksHandler());
		map.put(StepType.WAIT_MS, new WaitMsHandler());
		map.put(StepType.CLICK_SLOT, new ClickSlotHandler());
		map.put(StepType.MOVE_ITEMS, new MoveItemsHandler(settings));
		map.put(StepType.CLOSE_SCREEN, new CloseScreenHandler());
		map.put(StepType.NOTIFY, new NotifyHandler());
		// REPEAT is expanded by SequenceFlattener and never executed directly.
		map.put(StepType.REPEAT, (ctx, step) -> StepResult.done());
		return map;
	}

	static final class CommandHandler implements StepHandler {
		@Override
		public StepResult start(RunContext ctx, MacroStep step) {
			if (!ScreenController.inWorld()) return StepResult.failed("Not in a world");
			if (!ScreenController.commandKnown(step.text)) {
				// Servers may hide commands from the client; send anyway but tell the player.
				MacroManager.get().notifier().warn("Command may be unavailable: " + step.text);
			}
			if (!ScreenController.sendCommand(step.text)) return StepResult.failed("Command is empty");
			return StepResult.done();
		}
	}

	static final class OpenCraftingHandler implements StepHandler {
		private final Supplier<Settings> settings;

		OpenCraftingHandler(Supplier<Settings> settings) {
			this.settings = settings;
		}

		@Override
		public StepResult start(RunContext ctx, MacroStep step) {
			Settings s = settings.get();
			String command = step.text == null || step.text.isBlank() ? s.craftOpenCommand : step.text;
			ctx.stepState = new CraftingOpener(command, s.useLookedAtCraftingTable, s.serverTimeoutTicks);
			return tick(ctx, step);
		}

		@Override
		public StepResult tick(RunContext ctx, MacroStep step) {
			CraftingOpener opener = (CraftingOpener) ctx.stepState;
			StepResult r = opener.tick();
			if (!r.isRunning() && opener.openedScreen()) ctx.openedCraftingScreen = true;
			return r;
		}

		@Override
		public int timeoutTicks(RunContext ctx, MacroStep step) {
			return settings.get().serverTimeoutTicks + 40;
		}
	}

	static final class CraftHandler implements StepHandler {
		private final Supplier<Settings> settings;

		CraftHandler(Supplier<Settings> settings) {
			this.settings = settings;
		}

		@Override
		public StepResult start(RunContext ctx, MacroStep step) {
			ctx.stepState = new CraftingSession(step.text, step.amount, settings.get());
			return tick(ctx, step);
		}

		@Override
		public StepResult tick(RunContext ctx, MacroStep step) {
			CraftingSession session = (CraftingSession) ctx.stepState;
			StepResult r = session.tick();
			if (!r.isRunning()) {
				if (session.openedScreen()) ctx.openedCraftingScreen = true;
				if (session.mustKeepScreenOpen()) ctx.openedCraftingScreen = false;
				ctx.itemsCrafted += session.itemsCrafted();
			}
			return r;
		}

		@Override
		public void cancel(RunContext ctx, MacroStep step) {
			if (ctx.stepState instanceof CraftingSession session) {
				session.cancel();
				if (session.openedScreen()) ctx.openedCraftingScreen = true;
			}
		}

		@Override
		public int timeoutTicks(RunContext ctx, MacroStep step) {
			return ctx.stepState instanceof CraftingSession session ? session.timeoutTicks() : 600;
		}
	}

	static final class WaitTicksHandler implements StepHandler {
		@Override
		public StepResult start(RunContext ctx, MacroStep step) {
			return step.amount <= 0 ? StepResult.done() : StepResult.running();
		}

		@Override
		public StepResult tick(RunContext ctx, MacroStep step) {
			return ctx.stepTicks >= step.amount ? StepResult.done() : StepResult.running();
		}

		@Override
		public int timeoutTicks(RunContext ctx, MacroStep step) {
			return step.amount + 20;
		}
	}

	static final class WaitMsHandler implements StepHandler {
		@Override
		public StepResult start(RunContext ctx, MacroStep step) {
			if (step.amount <= 0) return StepResult.done();
			ctx.stepState = ctx.nowMs() + step.amount;
			return StepResult.running();
		}

		@Override
		public StepResult tick(RunContext ctx, MacroStep step) {
			return ctx.nowMs() >= (Long) ctx.stepState ? StepResult.done() : StepResult.running();
		}

		@Override
		public int timeoutTicks(RunContext ctx, MacroStep step) {
			return step.amount / 50 + 40;
		}
	}

	static final class ClickSlotHandler implements StepHandler {
		@Override
		public StepResult start(RunContext ctx, MacroStep step) {
			if (!ScreenController.inWorld()) return StepResult.failed("Not in a world");
			AbstractContainerMenu menu = ScreenController.menu();
			if (step.slot < 0 || step.slot >= menu.slots.size()) {
				return StepResult.failed("Slot " + step.slot + " does not exist in the open screen (0-" + (menu.slots.size() - 1) + ")");
			}
			ContainerInput input = step.shift ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP;
			ScreenController.click(menu, step.slot, step.button == 1 ? 1 : 0, input);
			return StepResult.done();
		}
	}

	/** Moves a stack: pick up source, place on target, put any remainder back on the source. */
	static final class MoveItemsHandler implements StepHandler {
		private final Supplier<Settings> settings;

		MoveItemsHandler(Supplier<Settings> settings) {
			this.settings = settings;
		}

		@Override
		public StepResult start(RunContext ctx, MacroStep step) {
			if (!ScreenController.inWorld()) return StepResult.failed("Not in a world");
			AbstractContainerMenu menu = ScreenController.menu();
			int size = menu.slots.size();
			if (step.slot >= size || step.slot2 >= size) return StepResult.failed("Slot out of range for the open screen (0-" + (size - 1) + ")");
			if (!menu.getCarried().isEmpty()) return StepResult.failed("Cursor is not empty");
			Slot from = menu.getSlot(step.slot);
			if (!from.hasItem()) return StepResult.done();
			ScreenController.click(menu, step.slot, 0, ContainerInput.PICKUP);
			ScreenController.click(menu, step.slot2, 0, ContainerInput.PICKUP);
			// state[0] = container id, state[1] = 1 once the remainder was put back
			ctx.stepState = new int[] {menu.containerId, 0};
			return StepResult.running();
		}

		@Override
		public StepResult tick(RunContext ctx, MacroStep step) {
			if (!ScreenController.inWorld()) return StepResult.failed("Not in a world");
			AbstractContainerMenu menu = ScreenController.menu();
			int[] state = (int[]) ctx.stepState;
			if (menu.containerId != state[0]) return StepResult.failed("Screen changed while moving items");
			if (!menu.getCarried().isEmpty()) {
				if (state[1] == 0) {
					// Target was full or held a different item: put the remainder back where it came from.
					ScreenController.click(menu, step.slot, 0, ContainerInput.PICKUP);
					state[1] = 1;
					return StepResult.running();
				}
				if (ctx.stepTicks > 5) {
					return StepResult.failed("Could not put items back; they remain on your cursor (nothing was dropped)");
				}
				return StepResult.running();
			}
			return ctx.stepTicks >= 2 ? StepResult.done() : StepResult.running();
		}

		@Override
		public int timeoutTicks(RunContext ctx, MacroStep step) {
			return settings.get().serverTimeoutTicks;
		}
	}

	static final class CloseScreenHandler implements StepHandler {
		@Override
		public StepResult start(RunContext ctx, MacroStep step) {
			if (!ScreenController.inWorld()) return StepResult.failed("Not in a world");
			ScreenController.closeContainer();
			ctx.openedCraftingScreen = false;
			return StepResult.done();
		}
	}

	static final class NotifyHandler implements StepHandler {
		@Override
		public StepResult start(RunContext ctx, MacroStep step) {
			MacroManager.get().notifier().info(step.text, true);
			return StepResult.done();
		}
	}
}
