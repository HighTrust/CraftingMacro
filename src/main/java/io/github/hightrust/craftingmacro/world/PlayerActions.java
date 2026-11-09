package io.github.hightrust.craftingmacro.world;

import io.github.hightrust.craftingmacro.crafting.RecipeResolver;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Drives the player through the same inputs a person uses: vanilla key mappings (movement,
 * attack, use), smooth view rotation and hotbar selection. Every key this class presses is
 * tracked and released by {@link #releaseAll()}, which runs whenever a macro ends or is stopped.
 */
public final class PlayerActions {
	private static final Set<KeyMapping> pressed = new HashSet<>();
	/** Max degrees turned per tick; keeps rotation human-like instead of snapping. */
	public static final float MAX_TURN = 20f;

	private PlayerActions() {
	}

	public static Minecraft mc() {
		return Minecraft.getInstance();
	}

	public static void press(KeyMapping mapping, boolean down) {
		mapping.setDown(down);
		if (down) pressed.add(mapping);
		else pressed.remove(mapping);
	}

	/** One click (like tapping the mouse button once). */
	public static void click(KeyMapping mapping) {
		KeyMapping.click(KeyMappingHelper.getBoundKeyOf(mapping));
	}

	public static void releaseAll() {
		for (KeyMapping k : pressed) k.setDown(false);
		pressed.clear();
	}

	public static KeyMapping direction(String dir) {
		var o = mc().options;
		return switch (dir.strip().toLowerCase(Locale.ROOT)) {
			case "back" -> o.keyDown;
			case "left" -> o.keyLeft;
			case "right" -> o.keyRight;
			default -> o.keyUp;
		};
	}

	/** True when vanilla will act on held attack/use keys: game focused, no menu open. */
	public static boolean canUseMouseKeys() {
		Minecraft mc = mc();
		return mc.gui.screen() == null && mc.mouseHandler.isMouseGrabbed() && mc.isWindowActive();
	}

	// ------------------------------------------------------------------ rotation

	/** Turns toward yaw/pitch by at most {@link #MAX_TURN} degrees. Returns true once within 1 degree. */
	public static boolean turnTowards(float yaw, float pitch) {
		LocalPlayer p = mc().player;
		if (p == null) return false;
		float dy = Mth.wrapDegrees(yaw - p.getYRot());
		float dp = pitch - p.getXRot();
		float sy = Mth.clamp(dy, -MAX_TURN, MAX_TURN);
		float sp = Mth.clamp(dp, -MAX_TURN, MAX_TURN);
		p.setYRot(p.getYRot() + sy);
		p.setXRot(Mth.clamp(p.getXRot() + sp, -90f, 90f));
		return Math.abs(dy - sy) < 1f && Math.abs(dp - sp) < 1f;
	}

	/** Yaw/pitch that points the eyes at {@code target}. */
	public static float[] anglesTo(Vec3 target) {
		LocalPlayer p = mc().player;
		Vec3 eye = p.getEyePosition();
		double dx = target.x - eye.x;
		double dy = target.y - eye.y;
		double dz = target.z - eye.z;
		double horiz = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90f;
		float pitch = (float) -(Mth.atan2(dy, horiz) * (180.0 / Math.PI));
		return new float[] {yaw, pitch};
	}

	// ------------------------------------------------------------------ items

	/** Matches ids like "minecraft:snow", "snow", "*_shovel", "minecraft:*_shovel". */
	public static boolean idMatches(String id, String pattern) {
		String p = pattern.strip().toLowerCase(Locale.ROOT);
		if (p.isEmpty()) return false;
		String target = p.contains(":") ? id : id.substring(id.indexOf(':') + 1);
		String regex = "\\Q" + p.replace("*", "\\E.*\\Q") + "\\E";
		return target.matches(regex);
	}

	public static boolean matchesAny(String id, String patternList) {
		for (String p : patternList.split(",")) {
			if (!p.isBlank() && idMatches(id, p)) return true;
		}
		return false;
	}

	/**
	 * Selects a hotbar slot holding an item matching {@code pattern}. If the item is only in the main
	 * inventory, swaps it into the current hotbar slot (inventory screen closed). Returns null on
	 * success or an error message.
	 */
	public static String selectItem(String pattern) {
		Minecraft mc = mc();
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null) return "Not in a world";
		Inventory inv = p.getInventory();
		if (matches(inv.getItem(inv.getSelectedSlot()), pattern)) return null;
		for (int i = 0; i < Inventory.SELECTION_SIZE; i++) {
			if (matches(inv.getItem(i), pattern)) {
				inv.setSelectedSlot(i);
				return null;
			}
		}
		if (p.containerMenu != p.inventoryMenu) return "No " + pattern + " in your hotbar";
		for (int i = Inventory.SELECTION_SIZE; i < Inventory.INVENTORY_SIZE; i++) {
			if (matches(inv.getItem(i), pattern)) {
				// inventory slot i (9-35) is menu slot i; SWAP with the selected hotbar index
				mc.gameMode.handleContainerInput(p.inventoryMenu.containerId, i, inv.getSelectedSlot(), ContainerInput.SWAP, p);
				return null;
			}
		}
		return "You don't have " + pattern;
	}

	private static boolean matches(ItemStack stack, String pattern) {
		return !stack.isEmpty() && idMatches(RecipeResolver.itemId(stack.getItem()), pattern);
	}
}
