package io.github.hightrust.craftingmacro.macro;

/** Editor-level category of a macro. All macros are step lists; the type picks starter steps and the editor hint. */
public enum MacroType {
	COMMAND("Command", "Runs one or more commands with optional delays."),
	CRAFT("Auto craft", "Crafts a chosen recipe automatically."),
	SEQUENCE("Sequence", "Any combination of steps, run in order.");

	private final String displayName;
	private final String description;

	MacroType(String displayName, String description) {
		this.displayName = displayName;
		this.description = description;
	}

	public String displayName() {
		return displayName;
	}

	public String description() {
		return description;
	}
}
