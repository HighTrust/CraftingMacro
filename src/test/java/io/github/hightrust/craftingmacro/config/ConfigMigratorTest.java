package io.github.hightrust.craftingmacro.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.hightrust.craftingmacro.macro.Macro;
import io.github.hightrust.craftingmacro.macro.StepType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigMigratorTest {
	private static final String V1 = """
			{
			  "macros": [
			    {"id": "m1", "name": "Home", "keybind": "H", "delayMs": 300,
			     "steps": [{"type": "COMMAND", "text": "/home"}]},
			    {"id": "m2", "name": "Unbound", "keybind": "", "steps": [{"type": "NOTIFY", "text": "hi"}]}
			  ]
			}
			""";

	@Test
	void detectsVersionOneWhenFieldMissing() {
		JsonObject root = JsonParser.parseString(V1).getAsJsonObject();
		assertEquals(1, ConfigMigrator.versionOf(root));
	}

	@Test
	void migratesV1ToCurrent() {
		JsonObject root = ConfigMigrator.migrate(JsonParser.parseString(V1).getAsJsonObject());
		assertEquals(ModConfig.CURRENT_VERSION, root.get("configVersion").getAsInt());
		assertFalse(root.has("macros"));
		assertTrue(root.has("profiles"));

		ModConfig config = ConfigStore.fromJson(V1);
		assertEquals("Default", config.activeProfile);
		Macro home = config.active().macros.get(0);
		assertEquals("key.keyboard.h", home.key.key);
		assertFalse(home.key.ctrl);
		assertEquals(300, home.startDelayMs);
		assertEquals(StepType.COMMAND, home.steps.get(0).type);
		assertFalse(config.active().macros.get(1).key.isBound());
	}

	@Test
	void currentVersionIsUntouched() {
		String json = ConfigStore.toJson(DefaultMacros.defaultConfig());
		JsonObject before = JsonParser.parseString(json).getAsJsonObject();
		JsonObject after = ConfigMigrator.migrate(before.deepCopy());
		assertEquals(before, after);
	}

	@Test
	void rejectsFutureVersion() {
		JsonObject root = new JsonObject();
		root.addProperty("configVersion", ModConfig.CURRENT_VERSION + 1);
		assertThrows(IllegalArgumentException.class, () -> ConfigMigrator.migrate(root));
	}
}
