package io.github.hightrust.craftingmacro.input;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.hightrust.craftingmacro.CraftingMacroClient;
import io.github.hightrust.craftingmacro.MacroManager;
import io.github.hightrust.craftingmacro.config.Settings;
import io.github.hightrust.craftingmacro.gui.MacroListScreen;
import io.github.hightrust.craftingmacro.macro.KeyCombo;
import io.github.hightrust.craftingmacro.macro.Macro;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registers the two vanilla-style key mappings (menu, emergency stop) and polls macro key combos.
 * Macro keys are polled from the keyboard state at the start of each client tick so they work with
 * any key, support modifiers, and never fire while a text field has focus.
 */
public final class KeybindManager {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(CraftingMacroClient.MOD_ID, "main"));

	private static KeyMapping openMenu;
	private static KeyMapping stopAll;

	/** Combos currently held (edge detection). */
	private static final Set<KeyCombo> held = new HashSet<>();
	/** Per macro: when hold-to-repeat may fire again. */
	private static final Map<String, Long> nextHoldFire = new HashMap<>();
	private static final Set<KeyCombo> warnedConflicts = new HashSet<>();

	private KeybindManager() {
	}

	public static void register() {
		openMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.craftingmacro.open_menu", InputConstants.KEY_RSHIFT, CATEGORY));
		stopAll = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.craftingmacro.stop_all", InputConstants.KEY_END, CATEGORY));
	}

	public static KeyMapping openMenuKey() {
		return openMenu;
	}

	public static KeyMapping stopAllKey() {
		return stopAll;
	}

	/** End of tick: vanilla-style mappings (only fire with no screen open, like vanilla keys). */
	public static void endTick(Minecraft mc) {
		while (stopAll.consumeClick()) {
			MacroManager.get().stopAll("Stop key pressed");
		}
		while (openMenu.consumeClick()) {
			if (mc.gui.screen() == null && mc.player != null) {
				mc.gui.setScreen(new MacroListScreen(null));
			}
		}
	}

	/** Start of tick, before vanilla processes key mappings. */
	public static void startTick(Minecraft mc) {
		MacroManager manager = MacroManager.get();
		if (mc.player == null || !mc.isWindowActive()) {
			held.clear();
			return;
		}
		Settings settings = manager.settings();
		boolean allowed = macrosAllowed(mc, settings);
		boolean ctrl = InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL);
		boolean shift = InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
		boolean alt = InputConstants.isKeyDown(InputConstants.KEY_LALT) || InputConstants.isKeyDown(InputConstants.KEY_RALT);

		List<Macro> macros = manager.macros();
		String server = MacroManager.currentServer();
		Set<KeyCombo> nowHeld = new HashSet<>();
		Set<KeyCombo> firedThisTick = new HashSet<>();
		long now = System.currentTimeMillis();

		for (Macro macro : macros) {
			if (!macro.enabled || macro.key == null || !macro.key.isBound()) continue;
			if (!comboDown(macro.key, ctrl, shift, alt)) continue;
			nowHeld.add(macro.key);
			if (!allowed || !macro.availableOn(server)) continue;
			boolean newPress = !held.contains(macro.key);
			if (newPress) {
				if (firedThisTick.contains(macro.key)) {
					// Two macros share this combo: run only the first and warn once.
					if (warnedConflicts.add(macro.key)) {
						manager.notifier().warn("Keybind conflict: " + macro.key.describe() + " is used by several macros");
					}
					continue;
				}
				firedThisTick.add(macro.key);
				if (manager.trigger(macro, false)) {
					nextHoldFire.put(macro.id, now + Math.max(macro.holdIntervalMs, 50));
					if (settings.suppressVanillaConflicts) suppressVanilla(mc, macro.key.key);
				}
			} else if (macro.holdToRepeat && now >= nextHoldFire.getOrDefault(macro.id, 0L)
					&& !manager.executor().isRunning(macro.id)) {
				if (manager.trigger(macro, true)) {
					nextHoldFire.put(macro.id, now + Math.max(macro.holdIntervalMs, 50));
				}
			}
		}
		held.clear();
		held.addAll(nowHeld);
	}

	/** Macros fire in-game, or in container screens if enabled; never while any text input has focus. */
	public static boolean macrosAllowed(Minecraft mc, Settings settings) {
		Screen screen = mc.gui.screen();
		if (screen == null) return true;
		if (screen.isInputCaptured()) return false;
		return settings.allowInContainerScreens && screen instanceof AbstractContainerScreen<?>;
	}

	private static boolean comboDown(KeyCombo combo, boolean ctrl, boolean shift, boolean alt) {
		InputConstants.Key key;
		try {
			key = InputConstants.getKey(combo.key);
		} catch (RuntimeException e) {
			return false;
		}
		if (key.getType() != InputConstants.Type.KEYBOARD) return false;
		int code = key.getValue();
		if (code <= 0 || !InputConstants.isKeyDown(code)) return false;
		// The key itself may be a modifier (e.g. binding Right Shift); don't require it twice.
		boolean keyIsCtrl = code == InputConstants.KEY_LCONTROL || code == InputConstants.KEY_RCONTROL;
		boolean keyIsShift = code == InputConstants.KEY_LSHIFT || code == InputConstants.KEY_RSHIFT;
		boolean keyIsAlt = code == InputConstants.KEY_LALT || code == InputConstants.KEY_RALT;
		return (keyIsCtrl || ctrl == combo.ctrl) && (keyIsShift || shift == combo.shift) && (keyIsAlt || alt == combo.alt);
	}

	/** Swallow pending clicks for vanilla bindings on the same key so e.g. L doesn't also open Advancements. */
	private static void suppressVanilla(Minecraft mc, String keyName) {
		for (KeyMapping mapping : mc.options.keyMappings) {
			if (mapping == openMenu || mapping == stopAll) continue;
			if (KeyMappingHelper.getBoundKeyOf(mapping).getName().equals(keyName)) {
				while (mapping.consumeClick()) {
					// drain
				}
			}
		}
	}

	/** Display name -> key name of every vanilla/mod key mapping, for conflict warnings in the editor. */
	public static Map<String, String> otherBindings(Minecraft mc) {
		Map<String, String> map = new LinkedHashMap<>();
		for (KeyMapping mapping : mc.options.keyMappings) {
			InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(mapping);
			if (key.equals(InputConstants.UNKNOWN)) continue;
			map.put(net.minecraft.network.chat.Component.translatable(mapping.getName()).getString(), key.getName());
		}
		return map;
	}

	/** Localized label like "Ctrl+K". */
	public static String label(KeyCombo combo) {
		if (combo == null || !combo.isBound()) return "Unbound";
		try {
			return combo.prefix() + InputConstants.getKey(combo.key).getDisplayName().getString();
		} catch (RuntimeException e) {
			return combo.describe();
		}
	}

	public static void clearState() {
		held.clear();
		nextHoldFire.clear();
		warnedConflicts.clear();
	}
}
