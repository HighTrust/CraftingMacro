package io.github.hightrust.craftingmacro;

import io.github.hightrust.craftingmacro.gui.HudOverlay;
import io.github.hightrust.craftingmacro.input.KeybindManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;

/** Client entrypoint: wires config, key bindings, tick handling and the HUD. */
public final class CraftingMacroClient implements ClientModInitializer {
	public static final String MOD_ID = "craftingmacro";

	private ClientLevel lastLevel;

	@Override
	public void onInitializeClient() {
		MacroManager.init(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID));
		KeybindManager.register();

		ClientTickEvents.START_CLIENT_TICK.register(KeybindManager::startTick);
		ClientTickEvents.END_CLIENT_TICK.register(this::endTick);

		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
			KeybindManager.clearState();
			MacroManager.get().onJoin();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			MacroManager.get().executor().stopAll("Disconnected");
			KeybindManager.clearState();
			MacroManager.get().saveNow();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> MacroManager.get().saveNow());

		// Show the "+N crafted" feed on top of crafting / inventory screens too.
		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) {
				net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterExtract(screen).register(
						(s, g, mx, my, delta) -> {
							HudOverlay.drawCraftFeed(g, mc.font);
							HudOverlay.drawBanner(g, mc.font);
						});
			}
		});
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "status"), new HudOverlay());
		MacroManager.LOGGER.info("Crafting Macro loaded");
	}

	private void endTick(Minecraft mc) {
		MacroManager manager = MacroManager.get();
		KeybindManager.endTick(mc);
		if (mc.player == null || mc.level == null) {
			if (!manager.executor().isIdle()) manager.executor().stopAll("Left the world");
			lastLevel = null;
		} else {
			if (lastLevel != null && lastLevel != mc.level) manager.stopAll("Changed dimension");
			lastLevel = mc.level;
			if (mc.player.isDeadOrDying()) {
				if (!manager.executor().isIdle()) manager.stopAll("Player died");
			} else {
				manager.executor().tick();
			}
		}
		manager.tickSave();
	}
}
