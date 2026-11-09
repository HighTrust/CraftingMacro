package io.github.hightrust.craftingmacro.macro;

/** How a macro's key starts and stops it. */
public enum TriggerMode {
	/** Each press runs the macro once, exactly as configured (e.g. craft 1 snow block). */
	CLICK("Click", "Each press runs the macro once"),
	/** While the key is held: craft as much as possible / keep repeating. Releasing stops it. */
	HOLD("Hold", "Hold the key: crafts as much as possible, stops when you let go"),
	/** Press to start crafting as much as possible / repeating; press again (or run out) to stop. */
	TOGGLE("Toggle", "Press once to start (crafts as much as possible), press again to stop");

	private final String displayName;
	private final String description;

	TriggerMode(String displayName, String description) {
		this.displayName = displayName;
		this.description = description;
	}

	public String displayName() {
		return displayName;
	}

	public String description() {
		return description;
	}

	/** HOLD and TOGGLE craft "as much as possible" instead of the configured quantity. */
	public boolean unlimited() {
		return this != CLICK;
	}
}
