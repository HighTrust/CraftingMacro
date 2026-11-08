package io.github.hightrust.craftingmacro.config;

import io.github.hightrust.craftingmacro.macro.KeyCombo;
import io.github.hightrust.craftingmacro.macro.Macro;
import io.github.hightrust.craftingmacro.macro.MacroStep;
import io.github.hightrust.craftingmacro.macro.MacroType;

import java.util.List;

/** Example presets. They are ordinary macros: users can edit, rebind or delete them. */
public final class DefaultMacros {
	private DefaultMacros() {
	}

	public static List<Macro> create() {
		Macro craftTable = new Macro("Open Crafting Table", MacroType.COMMAND, KeyCombo.of("key.keyboard.k"))
				.withDescription("Runs /craft to open the server's virtual crafting table (if the server supports it).")
				.withStep(MacroStep.command("/craft"));
		craftTable.cooldownMs = 1000;
		craftTable.notify = false;

		Macro snowBlock = new Macro("Craft Snow Block", MacroType.CRAFT, KeyCombo.of("key.keyboard.l"))
				.withDescription("Crafts one snow block from 4 snowballs and closes the crafting screen if it was opened.")
				.withStep(MacroStep.craft("minecraft:snow_block", 1));

		// 3 snow blocks per operation -> 6 layers each; three operations = 18 layers.
		Macro snowLayers = new Macro("Craft Snow Layers", MacroType.CRAFT, KeyCombo.of("key.keyboard.m"))
				.withDescription("Performs three snow-layer crafting operations (18 layers) and collects the results.")
				.withStep(MacroStep.craft("minecraft:snow", 18));

		return List.of(craftTable, snowBlock, snowLayers);
	}

	public static Profile defaultProfile() {
		Profile p = new Profile("Default");
		p.macros.addAll(create());
		return p;
	}

	public static ModConfig defaultConfig() {
		ModConfig c = new ModConfig();
		c.profiles.add(defaultProfile());
		c.activeProfile = "Default";
		return c;
	}
}
