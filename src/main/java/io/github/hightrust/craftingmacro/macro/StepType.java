package io.github.hightrust.craftingmacro.macro;

/**
 * Every kind of step a macro can contain. New step types are added here and
 * given a handler in {@code io.github.hightrust.craftingmacro.exec.StepHandlers}.
 */
public enum StepType {
	COMMAND("Run command", "Sends a command as you, e.g. /craft. Normal server permissions apply.", false),
	OPEN_CRAFTING("Open crafting", "Opens a 3x3 crafting interface (crafting table you are looking at, or the open command) and waits until the server opens it.", true),
	CRAFT("Craft recipe", "Crafts an item through the recipe book. Quantity = number of result items you want.", true),
	CRAFT_PATTERN("Craft custom pattern", "Places items into the crafting grid in the exact layout you design, then collects the result. Works for custom server recipes too. Amount = number of crafts.", true),
	WAIT_TICKS("Wait (ticks)", "Pauses for a number of game ticks (20 ticks = 1 second).", false),
	WAIT_MS("Wait (ms)", "Pauses for a number of milliseconds.", false),
	CLICK_SLOT("Click slot", "Clicks a slot index in the open container. Optional right-click or shift-click.", true),
	MOVE_ITEMS("Move items", "Moves the stack in one container slot to another. Leftovers go back to the source slot.", true),
	CLOSE_SCREEN("Close screen", "Closes the currently open container screen.", true),
	NOTIFY("Notification", "Shows an on-screen notification with your message.", false),
	REPEAT("Repeat previous", "Repeats the previous N steps a number of extra times.", false);

	private final String displayName;
	private final String description;
	private final boolean mutatesInventory;

	StepType(String displayName, String description, boolean mutatesInventory) {
		this.displayName = displayName;
		this.description = description;
		this.mutatesInventory = mutatesInventory;
	}

	public String displayName() {
		return displayName;
	}

	public String description() {
		return description;
	}

	/** Whether this step touches inventories/screens and therefore needs the exclusive inventory lock. */
	public boolean mutatesInventory() {
		return mutatesInventory;
	}
}
