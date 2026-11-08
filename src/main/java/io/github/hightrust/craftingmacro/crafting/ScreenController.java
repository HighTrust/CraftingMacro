package io.github.hightrust.craftingmacro.crafting;

import io.github.hightrust.craftingmacro.macro.MacroValidator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;

/**
 * The only place that sends inventory/screen interactions. Everything goes through vanilla
 * {@code MultiPlayerGameMode} / packet listener methods, exactly as if the player clicked, so
 * the server validates every action.
 */
public final class ScreenController {
	private ScreenController() {
	}

	public static Minecraft mc() {
		return Minecraft.getInstance();
	}

	public static LocalPlayer player() {
		return mc().player;
	}

	public static boolean inWorld() {
		Minecraft mc = mc();
		return mc.player != null && mc.level != null && mc.gameMode != null && mc.getConnection() != null;
	}

	public static AbstractContainerMenu menu() {
		LocalPlayer p = player();
		return p == null ? null : p.containerMenu;
	}

	public static boolean isCraftingTableOpen() {
		return menu() instanceof CraftingMenu;
	}

	public static boolean isInventoryMenuActive() {
		LocalPlayer p = player();
		return p != null && p.containerMenu == p.inventoryMenu;
	}

	/** Grid slots of a crafting-capable menu (3x3 crafting table or the 2x2 inventory grid). */
	public static List<Slot> gridSlots(AbstractContainerMenu menu) {
		if (menu instanceof CraftingMenu crafting) return crafting.getInputGridSlots();
		LocalPlayer p = player();
		if (p != null && menu == p.inventoryMenu) return p.inventoryMenu.getInputGridSlots();
		return List.of();
	}

	public static boolean gridEmpty(AbstractContainerMenu menu) {
		for (Slot s : gridSlots(menu)) if (s.hasItem()) return false;
		return true;
	}

	/** Number of full recipe sets in the grid: the smallest non-empty grid stack. */
	public static int gridSets(AbstractContainerMenu menu) {
		int min = Integer.MAX_VALUE;
		for (Slot s : gridSlots(menu)) {
			if (s.hasItem()) min = Math.min(min, s.getItem().getCount());
		}
		return min == Integer.MAX_VALUE ? 0 : min;
	}

	public static ItemStack resultStack(AbstractContainerMenu menu) {
		return menu.slots.isEmpty() ? ItemStack.EMPTY : menu.getSlot(0).getItem();
	}

	// ------------------------------------------------------------------ actions

	public static void click(AbstractContainerMenu menu, int slot, int button, ContainerInput input) {
		Minecraft mc = mc();
		if (mc.gameMode == null || mc.player == null) return;
		mc.gameMode.handleContainerInput(menu.containerId, slot, button, input, mc.player);
	}

	public static void quickMove(AbstractContainerMenu menu, int slot) {
		click(menu, slot, 0, ContainerInput.QUICK_MOVE);
	}

	public static void placeRecipe(AbstractContainerMenu menu, RecipeResolver.CraftRecipe recipe, boolean useMax) {
		Minecraft mc = mc();
		if (mc.gameMode == null) return;
		mc.gameMode.handlePlaceRecipe(menu.containerId, recipe.entry().id(), useMax);
	}

	/** Closes the open container through the normal close path (server is told, carried items handled by vanilla). */
	public static void closeContainer() {
		LocalPlayer p = player();
		if (p == null) return;
		if (p.containerMenu != p.inventoryMenu) {
			p.closeContainer();
		} else if (mc().gui.screen() != null) {
			mc().gui.setScreen(null);
		}
	}

	/** True if the player is looking at a crafting table within reach. */
	public static BlockHitResult lookedAtCraftingTable() {
		Minecraft mc = mc();
		if (mc.level == null) return null;
		HitResult hit = mc.hitResult;
		if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK
				&& mc.level.getBlockState(bhr.getBlockPos()).is(Blocks.CRAFTING_TABLE)) {
			return bhr;
		}
		return null;
	}

	public static void useBlock(BlockHitResult hit) {
		Minecraft mc = mc();
		if (mc.gameMode == null || mc.player == null) return;
		mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
	}

	/** Whether the server advertised a root command with this name to the client. */
	public static boolean commandKnown(String command) {
		ClientPacketListener connection = mc().getConnection();
		if (connection == null) return false;
		String stripped = MacroValidator.stripSlash(command);
		if (stripped.isEmpty()) return false;
		String root = stripped.split("\\s+", 2)[0];
		return connection.getCommands().getRoot().getChild(root) != null;
	}

	/** Sends a command exactly as if typed in chat (signed when required). */
	public static boolean sendCommand(String command) {
		ClientPacketListener connection = mc().getConnection();
		String stripped = MacroValidator.stripSlash(command);
		if (connection == null || stripped.isEmpty()) return false;
		connection.sendCommand(stripped);
		return true;
	}
}
