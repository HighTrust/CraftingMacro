package io.github.hightrust.craftingmacro.config;

/** Global mod settings. */
public final class Settings {
	/** Master switch for on-screen notifications (per-macro preference still applies). */
	public boolean notificationsEnabled = true;
	/** Small on-screen indicator while a macro runs. */
	public boolean showRunningIndicator = true;
	/** Also echo notifications into chat (client-side only, never sent to the server). */
	public boolean notificationsInChat = false;
	/** Allow macro keys to fire while a container screen (inventory, chest...) is open and no text field is focused. */
	public boolean allowInContainerScreens = false;
	/** When a macro fires, swallow the same key press for vanilla bindings using that key (e.g. L = Advancements). */
	public boolean suppressVanillaConflicts = true;
	/** Command used to open a 3x3 crafting interface when none is open (blank = disabled). */
	public String craftOpenCommand = "/craft";
	/** Use the crafting table under the crosshair before falling back to the open command. */
	public boolean useLookedAtCraftingTable = true;
	/** Prefer the 2x2 inventory grid for small recipes instead of opening a crafting table. */
	public boolean preferInventoryGrid = true;
	/**
	 * Let mining keep working while the game window is unfocused (tabbed out). Requires vanilla's
	 * "pause on lost focus" to be off (F3+P). Off by default.
	 */
	public boolean backgroundMining = false;
	/** Maximum ticks to wait for the server to open a screen or confirm an inventory change. */
	public int serverTimeoutTicks = 60;
	/** Menu style: "dark", "glass" (blurred, see-through) or "clear" (see-through, no blur). */
	public String theme = "dark";

	public Settings copy() {
		Settings s = new Settings();
		s.notificationsEnabled = notificationsEnabled;
		s.showRunningIndicator = showRunningIndicator;
		s.notificationsInChat = notificationsInChat;
		s.allowInContainerScreens = allowInContainerScreens;
		s.suppressVanillaConflicts = suppressVanillaConflicts;
		s.craftOpenCommand = craftOpenCommand;
		s.useLookedAtCraftingTable = useLookedAtCraftingTable;
		s.preferInventoryGrid = preferInventoryGrid;
		s.serverTimeoutTicks = serverTimeoutTicks;
		s.backgroundMining = backgroundMining;
		s.theme = theme;
		return s;
	}
}
