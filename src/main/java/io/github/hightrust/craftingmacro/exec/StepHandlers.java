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
		map.put(StepType.CRAFT_PATTERN, new PatternHandler(settings));
		map.put(StepType.WALK, new WalkHandler());
		map.put(StepType.LOOK, new LookHandler());
		map.put(StepType.MOUSE_CLICK, new MouseClickHandler());
		map.put(StepType.SELECT_ITEM, (ctx, step) -> {
			String err = io.github.hightrust.craftingmacro.world.PlayerActions.selectItem(step.text);
			return err == null ? StepResult.done() : StepResult.failed(err);
		});
		map.put(StepType.MINE_BLOCKS, new MineHandler());
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
			ctx.stepState = ctx.unlimited
					? CraftingSession.asMuchAsPossible(step.text, settings.get())
					: new CraftingSession(step.text, step.amount, settings.get());
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

	static final class PatternHandler implements StepHandler {
		private final Supplier<Settings> settings;

		PatternHandler(Supplier<Settings> settings) {
			this.settings = settings;
		}

		@Override
		public StepResult start(RunContext ctx, MacroStep step) {
			ctx.stepState = new io.github.hightrust.craftingmacro.crafting.PatternCraftSession(step.grid(), step.amount, ctx.unlimited, settings.get());
			return tick(ctx, step);
		}

		@Override
		public StepResult tick(RunContext ctx, MacroStep step) {
			var session = (io.github.hightrust.craftingmacro.crafting.PatternCraftSession) ctx.stepState;
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
			if (ctx.stepState instanceof io.github.hightrust.craftingmacro.crafting.PatternCraftSession session) {
				session.cancel();
				if (session.openedScreen()) ctx.openedCraftingScreen = true;
			}
		}

		@Override
		public int timeoutTicks(RunContext ctx, MacroStep step) {
			return ctx.stepState instanceof io.github.hightrust.craftingmacro.crafting.PatternCraftSession s ? s.timeoutTicks() : 600;
		}
	}

	/** Holds movement keys (and optionally jump) for {@code amount} ticks. */
	static final class WalkHandler implements StepHandler {
		@Override
		public StepResult start(RunContext ctx, MacroStep step) {
			if (!ScreenController.inWorld()) return StepResult.failed("Not in a world");
			var mc = net.minecraft.client.Minecraft.getInstance();
			String dirs = step.text == null || step.text.isBlank() ? "forward" : step.text;
			for (String d : dirs.split("[,+ ]+")) {
				if (!d.isBlank()) io.github.hightrust.craftingmacro.world.PlayerActions.press(io.github.hightrust.craftingmacro.world.PlayerActions.direction(d), true);
			}
			if (step.shift) io.github.hightrust.craftingmacro.world.PlayerActions.press(mc.options.keyJump, true);
			return StepResult.running();
		}

		@Override
		public StepResult tick(RunContext ctx, MacroStep step) {
			if (ctx.stepTicks >= step.amount) {
				io.github.hightrust.craftingmacro.world.PlayerActions.releaseAll();
				return StepResult.done();
			}
			return StepResult.running();
		}

		@Override
		public void cancel(RunContext ctx, MacroStep step) {
			io.github.hightrust.craftingmacro.world.PlayerActions.releaseAll();
		}

		@Override
		public int timeoutTicks(RunContext ctx, MacroStep step) {
			return step.amount + 20;
		}
	}

	/** Turns smoothly to an absolute yaw,pitch. */
	static final class LookHandler implements StepHandler {
		@Override
		public StepResult start(RunContext ctx, MacroStep step) {
			if (!ScreenController.inWorld()) return StepResult.failed("Not in a world");
			return tick(ctx, step);
		}

		@Override
		public StepResult tick(RunContext ctx, MacroStep step) {
			String[] parts = step.text.split(",");
			float yaw = Float.parseFloat(parts[0].trim());
			float pitch = Float.parseFloat(parts[1].trim());
			return io.github.hightrust.craftingmacro.world.PlayerActions.turnTowards(yaw, pitch) ? StepResult.done() : StepResult.running();
		}

		@Override
		public int timeoutTicks(RunContext ctx, MacroStep step) {
			return 40;
		}
	}

	/** Left (attack) or right (use) click; amount > 0 holds the button for that many ticks. */
	static final class MouseClickHandler implements StepHandler {
		@Override
		public StepResult start(RunContext ctx, MacroStep step) {
			if (!ScreenController.inWorld()) return StepResult.failed("Not in a world");
			if (!io.github.hightrust.craftingmacro.world.PlayerActions.canUseMouseKeys()) {
				return StepResult.failed("Clicking needs the game focused with no menu open");
			}
			var o = net.minecraft.client.Minecraft.getInstance().options;
			var key = step.button == 1 ? o.keyUse : o.keyAttack;
			if (step.amount <= 0) {
				io.github.hightrust.craftingmacro.world.PlayerActions.click(key);
				return StepResult.done();
			}
			io.github.hightrust.craftingmacro.world.PlayerActions.press(key, true);
			return StepResult.running();
		}

		@Override
		public StepResult tick(RunContext ctx, MacroStep step) {
			if (ctx.stepTicks >= step.amount) {
				io.github.hightrust.craftingmacro.world.PlayerActions.releaseAll();
				return StepResult.done();
			}
			return StepResult.running();
		}

		@Override
		public void cancel(RunContext ctx, MacroStep step) {
			io.github.hightrust.craftingmacro.world.PlayerActions.releaseAll();
		}

		@Override
		public int timeoutTicks(RunContext ctx, MacroStep step) {
			return step.amount + 20;
		}
	}

	static final class MineHandler implements StepHandler {
		@Override
		public StepResult start(RunContext ctx, MacroStep step) {
			if (!ScreenController.inWorld()) return StepResult.failed("Not in a world");
			ctx.stepState = new io.github.hightrust.craftingmacro.world.MineSession(step.text, step.amount, step.slot <= 0 ? 6 : step.slot, step.shift);
			return tick(ctx, step);
		}

		@Override
		public StepResult tick(RunContext ctx, MacroStep step) {
			return ((io.github.hightrust.craftingmacro.world.MineSession) ctx.stepState).tick();
		}

		@Override
		public void cancel(RunContext ctx, MacroStep step) {
			if (ctx.stepState instanceof io.github.hightrust.craftingmacro.world.MineSession s) s.cancel();
			io.github.hightrust.craftingmacro.world.PlayerActions.releaseAll();
		}

		@Override
		public int timeoutTicks(RunContext ctx, MacroStep step) {
			return 20 * 60 * 30;
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
