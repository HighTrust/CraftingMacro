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

		return List.of(craftTable, snowBlock, snowLayers, shovelSnow());
	}

	/** Toggle on N: hold a shovel and mine snow nearby, walking to it. Press N again to stop. */
	public static Macro shovelSnow() {
		MacroStep select = MacroStep.of(io.github.hightrust.craftingmacro.macro.StepType.SELECT_ITEM);
		select.text = "*_shovel";
		MacroStep mine = MacroStep.of(io.github.hightrust.craftingmacro.macro.StepType.MINE_BLOCKS);
		mine.text = "minecraft:snow, minecraft:snow_block";
		mine.amount = 0;
		mine.slot = 8;
		mine.shift = true;
		Macro m = new Macro("Shovel Snow", MacroType.SEQUENCE, KeyCombo.of("key.keyboard.n"))
				.withDescription("Toggle with N: holds your shovel and mines all snow nearby, walking to it.")
				.withStep(select)
				.withStep(mine);
		m.trigger = io.github.hightrust.craftingmacro.macro.TriggerMode.TOGGLE;
		m.cooldownMs = 300;
		return m;
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
		c.presetsVersion = ModConfig.CURRENT_PRESETS;
		return c;
	}
}
