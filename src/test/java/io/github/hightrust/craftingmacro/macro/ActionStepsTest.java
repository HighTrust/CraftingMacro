package io.github.hightrust.craftingmacro.macro;

import io.github.hightrust.craftingmacro.config.DefaultMacros;
import io.github.hightrust.craftingmacro.world.PlayerActions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ActionStepsTest {
	@Test
	void wildcardMatching() {
		assertTrue(PlayerActions.idMatches("minecraft:diamond_shovel", "*_shovel"));
		assertTrue(PlayerActions.idMatches("minecraft:diamond_shovel", "minecraft:*_shovel"));
		assertTrue(PlayerActions.idMatches("minecraft:snow", "snow"));
		assertTrue(PlayerActions.idMatches("minecraft:snow", "minecraft:snow"));
		assertFalse(PlayerActions.idMatches("minecraft:snow_block", "minecraft:snow"));
		assertFalse(PlayerActions.idMatches("minecraft:diamond_pickaxe", "*_shovel"));
		assertFalse(PlayerActions.idMatches("othermod:iron_shovel", "minecraft:*_shovel"));
		assertTrue(PlayerActions.matchesAny("minecraft:snow_block", "minecraft:snow, minecraft:snow_block"));
		assertFalse(PlayerActions.matchesAny("minecraft:dirt", "minecraft:snow, minecraft:snow_block"));
		assertFalse(PlayerActions.idMatches("minecraft:a.b", "a?b"), "regex characters are literal");
	}

	@Test
	void actionStepValidation() {
		MacroStep walk = MacroStep.of(StepType.WALK);
		walk.text = "forward+left";
		walk.amount = 20;
		assertNull(MacroValidator.validateStep(walk, 0));
		walk.text = "up";
		assertNotNull(MacroValidator.validateStep(walk, 0));

		MacroStep look = MacroStep.of(StepType.LOOK);
		look.text = "-90, 45";
		assertNull(MacroValidator.validateStep(look, 0));
		look.text = "north";
		assertNotNull(MacroValidator.validateStep(look, 0));

		MacroStep select = MacroStep.of(StepType.SELECT_ITEM);
		select.text = "*_shovel";
		assertNull(MacroValidator.validateStep(select, 0));
		select.text = "";
		assertNotNull(MacroValidator.validateStep(select, 0));

		MacroStep mine = MacroStep.of(StepType.MINE_BLOCKS);
		mine.text = "minecraft:snow, *_log";
		assertNull(MacroValidator.validateStep(mine, 0));
		mine.text = "bad id!";
		assertNotNull(MacroValidator.validateStep(mine, 0));

		assertTrue(StepType.MINE_BLOCKS.mutatesInventory(), "world actions take the exclusive lock");
	}

	@Test
	void shovelSnowPresetIsValid() {
		Macro m = DefaultMacros.shovelSnow();
		assertEquals(TriggerMode.TOGGLE, m.trigger);
		assertEquals("key.keyboard.n", m.key.key);
		assertTrue(MacroValidator.validate(m).isEmpty(), MacroValidator.validate(m).toString());
		assertFalse(m.crafts(), "toggle keeps looping for non-crafting macros");
	}
}
