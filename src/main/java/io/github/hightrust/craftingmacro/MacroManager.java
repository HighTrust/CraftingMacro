package io.github.hightrust.craftingmacro;

import io.github.hightrust.craftingmacro.config.ConfigStore;
import io.github.hightrust.craftingmacro.config.ConfigValidator;
import io.github.hightrust.craftingmacro.config.DefaultMacros;
import io.github.hightrust.craftingmacro.config.ModConfig;
import io.github.hightrust.craftingmacro.config.Profile;
import io.github.hightrust.craftingmacro.config.Settings;
import io.github.hightrust.craftingmacro.crafting.ScreenController;
import io.github.hightrust.craftingmacro.exec.MacroExecutor;
import io.github.hightrust.craftingmacro.exec.MacroRun;
import io.github.hightrust.craftingmacro.exec.StepHandlers;
import io.github.hightrust.craftingmacro.macro.Macro;
import io.github.hightrust.craftingmacro.notify.Notifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.inventory.CraftingMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Central owner of configuration, the executor and notifications. Client thread only. */
public final class MacroManager {
	public static final Logger LOGGER = LoggerFactory.getLogger("CraftingMacro");
	private static final long SAVE_DEBOUNCE_MS = 750;

	private static MacroManager instance;

	private final ConfigStore store;
	private ModConfig config;
	private final MacroExecutor executor;
	private final Notifier notifier;
	private final io.github.hightrust.craftingmacro.exec.RuleEngine rules;
	private long dirtySince = -1;
	private final io.github.hightrust.craftingmacro.notify.CraftLog craftLog;

	public io.github.hightrust.craftingmacro.notify.CraftLog craftLog() {
		return craftLog;
	}

	private MacroManager(Path configDir) {
		this.store = new ConfigStore(configDir);
		ConfigStore.LoadResult loaded = store.load();
		this.config = loaded.config();
		this.notifier = new Notifier(this::settings);
		this.craftLog = new io.github.hightrust.craftingmacro.notify.CraftLog(configDir);
		this.executor = new MacroExecutor(StepHandlers.create(this::settings), System::currentTimeMillis);
		this.executor.setEndHook(this::onRunEnd);
		this.rules = new io.github.hightrust.craftingmacro.exec.RuleEngine(executor, msg -> notifier.info(msg, true));
		this.executor.setListener(new MacroExecutor.Listener() {
			@Override
			public void onStarted(MacroRun run) {
			}

			@Override
			public void onFinished(MacroRun run) {
				reportFinished(run);
			}
		});
		for (String w : loaded.warnings()) LOGGER.warn(w);
		if (config.presetsVersion < ModConfig.CURRENT_PRESETS) {
			// 1.3.0 added the "Shovel Snow" preset; offer it once to existing setups.
			Macro shovel = DefaultMacros.shovelSnow();
			if (config.active().macros.stream().noneMatch(m -> m.name.equalsIgnoreCase(shovel.name))) {
				config.active().macros.add(shovel);
			}
			config.presetsVersion = ModConfig.CURRENT_PRESETS;
		}
		saveNow();
	}

	public static MacroManager init(Path configDir) {
		instance = new MacroManager(configDir);
		return instance;
	}

	public static MacroManager get() {
		return instance;
	}

	public ModConfig config() {
		return config;
	}

	public Settings settings() {
		return config.settings;
	}

	public Profile activeProfile() {
		return config.active();
	}

	public List<Macro> macros() {
		return activeProfile().macros;
	}

	public MacroExecutor executor() {
		return executor;
	}

	/** Ticks the executor and then the automation rules of running macros. */
	public void tickMacros() {
		executor.tick();
		rules.tick(System.currentTimeMillis());
	}

	public Notifier notifier() {
		return notifier;
	}

	public ConfigStore store() {
		return store;
	}

	// ------------------------------------------------------------------ persistence

	/** Marks the config changed; it is written shortly after (debounced) from the tick loop. */
	public void markDirty() {
		if (dirtySince < 0) dirtySince = System.currentTimeMillis();
	}

	public void saveNow() {
		dirtySince = -1;
		if (craftLog != null) craftLog.save();
		try {
			store.save(config);
		} catch (IOException e) {
			LOGGER.error("Failed to save macro config", e);
			notifier.error("Could not save macros: " + e.getMessage());
		}
	}

	void tickSave() {
		if (dirtySince >= 0 && System.currentTimeMillis() - dirtySince > SAVE_DEBOUNCE_MS) saveNow();
	}

	public void resetToDefaults() {
		executor.stopAll("Configuration reset");
		Settings keep = config.settings;
		config = DefaultMacros.defaultConfig();
		config.settings = keep;
		saveNow();
	}

	public void replaceConfig(ModConfig newConfig) {
		ConfigValidator.repair(newConfig);
		config = newConfig;
		saveNow();
	}

	// ------------------------------------------------------------------ profiles

	public void switchProfile(String name) {
		Profile p = config.profile(name);
		if (p == null || p.name.equals(config.activeProfile)) return;
		executor.stopAll("Profile switched");
		config.activeProfile = p.name;
		markDirty();
		notifier.info("Profile: " + p.name, true);
	}

	/** Normalized address of the current server, or "singleplayer". */
	public static String currentServer() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getSingleplayerServer() != null) return Macro.SINGLEPLAYER;
		ServerData data = mc.getCurrentServer();
		return Macro.normalizeServer(data == null ? null : data.ip);
	}

	void onJoin() {
		String server = currentServer();
		String profile = config.serverProfiles.get(server);
		if (profile != null && config.profile(profile) != null && !profile.equals(config.activeProfile)) {
			config.activeProfile = config.profile(profile).name;
			markDirty();
			notifier.info("Using profile \"" + config.activeProfile + "\" for this server", true);
		}
	}

	// ------------------------------------------------------------------ execution

	/** Starts a macro (a snapshot copy, so editing it mid-run is safe). Returns true if it started. */
	public boolean trigger(Macro macro, boolean fromHold) {
		return trigger(macro, fromHold, false);
	}

	/** State of each macro's most recent finished run (used by toggle mode to stop when done). */
	private final java.util.Map<String, MacroRun.State> lastResult = new java.util.HashMap<>();

	public MacroRun.State lastResult(String macroId) {
		return lastResult.get(macroId);
	}

	/**
	 * @param fromHold  quiet automatic re-trigger (no cooldown / "already running" messages)
	 * @param unlimited crafting steps craft as much as possible
	 */
	public boolean trigger(Macro macro, boolean fromHold, boolean unlimited) {
		lastResult.remove(macro.id);
		if (!ScreenController.inWorld()) return false;
		if (!macro.enabled) return false;
		if (!macro.availableOn(currentServer())) {
			if (!fromHold) notifier.warn(macro.name + " is not enabled for this server");
			return false;
		}
		if (!fromHold) rules.reset(macro.id);
		Macro snapshot = macro.copy(false);
		MacroExecutor.StartResult result = executor.start(snapshot, fromHold, unlimited);
		switch (result) {
			case STARTED -> {
				return true;
			}
			case COOLDOWN -> {
				if (!fromHold) {
					long left = executor.cooldowns().remaining(macro.id, macro.cooldownMs);
					notifier.post("Cooldown active: " + macro.name + " (" + String.format("%.1f", left / 1000.0) + "s)", Notifier.Level.WARN, macro.notify);
				}
			}
			case INVENTORY_BUSY -> {
				if (!fromHold) notifier.warn("Another inventory macro is running");
			}
			case ALREADY_RUNNING -> {
				if (!fromHold) notifier.post(macro.name + " is already running", Notifier.Level.INFO, macro.notify);
			}
			case INVALID -> notifier.error(macro.name + ": " + executor.lastStartError());
		}
		return false;
	}

	/**
	 * Background mode: mining keeps working while tabbed out. Also turns off vanilla's
	 * "pause on lost focus" so the game keeps running (and restores it when switched off).
	 */
	public void setBackgroundMode(boolean on) {
		settings().backgroundMining = on;
		Minecraft mc = Minecraft.getInstance();
		mc.options.pauseOnLostFocus = !on;
		mc.options.save();
		markDirty();
		notifier.banner("Background mode", on ? "Keeps running when you tab out" : "Game pauses when you tab out", on);
	}

	public void stopAll(String reason) {
		int n = executor.stopAll(reason);
		io.github.hightrust.craftingmacro.world.PlayerActions.releaseAll();
		if (n > 0) notifier.warn("Stopped " + n + " macro" + (n == 1 ? "" : "s") + ": " + reason);
	}

	/** Closes a crafting screen the run opened, unless items would be left in the grid. */
	private void onRunEnd(MacroRun run) {
		// Never leave movement / attack / use keys held after a macro ends.
		io.github.hightrust.craftingmacro.world.PlayerActions.releaseAll();
		if (!run.context().openedCraftingScreen || !run.macro().autoClose) return;
		if (ScreenController.inWorld() && ScreenController.menu() instanceof CraftingMenu menu
				&& ScreenController.gridEmpty(menu) && menu.getCarried().isEmpty()) {
			ScreenController.closeContainer();
		}
	}

	private void reportFinished(MacroRun run) {
		Macro m = run.macro();
		lastResult.put(m.id, run.state());
		switch (run.state()) {
			case SUCCEEDED -> {
				// Hold/toggle loops re-run constantly; only report runs that produced something.
				if (run.context().unlimited && !m.crafts() && (run.message() == null || run.message().isEmpty())) return;
				String msg = run.message() == null || run.message().isEmpty() ? m.name + " done" : run.message();
				if (run.warnings() > 0) {
					notifier.post(msg + " (" + run.warnings() + " warning" + (run.warnings() == 1 ? "" : "s") + ": " + run.lastWarning() + ")", Notifier.Level.WARN, m.notify);
				} else {
					notifier.success(msg, m.notify);
				}
			}
			// Errors always show (if notifications are on globally), even when the macro is quiet.
			case FAILED -> notifier.error(m.name + ": " + run.message());
			case CANCELLED -> notifier.post(m.name + " interrupted" + (run.message().isEmpty() ? "" : ": " + run.message()), Notifier.Level.WARN, true);
			default -> {
			}
		}
	}
}
