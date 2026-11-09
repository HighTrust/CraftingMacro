package io.github.hightrust.craftingmacro.config;

import io.github.hightrust.craftingmacro.macro.KeyCombo;
import io.github.hightrust.craftingmacro.macro.Macro;
import io.github.hightrust.craftingmacro.macro.MacroStep;
import io.github.hightrust.craftingmacro.macro.MacroType;
import io.github.hightrust.craftingmacro.macro.StepType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigSerializationTest {
	@Test
	void defaultConfigRoundTrips() {
		ModConfig original = DefaultMacros.defaultConfig();
		String json = ConfigStore.toJson(original);
		ModConfig loaded = ConfigStore.fromJson(json);

		assertEquals(ModConfig.CURRENT_VERSION, loaded.configVersion);
		assertEquals("Default", loaded.activeProfile);
		assertEquals(3, loaded.active().macros.size());
		Macro k = loaded.active().macros.get(0);
		assertEquals("key.keyboard.k", k.key.key);
		assertEquals(StepType.COMMAND, k.steps.get(0).type);
		assertEquals("/craft", k.steps.get(0).text);
		assertEquals(original.active().macros.get(1).id, loaded.active().macros.get(1).id);
		assertEquals(ConfigStore.toJson(original), ConfigStore.toJson(loaded), "second serialization must be identical");
	}

	@Test
	void allFieldsSurviveRoundTrip() {
		ModConfig c = new ModConfig();
		Profile p = new Profile("PvP");
		Macro m = new Macro("Test", MacroType.SEQUENCE, new KeyCombo("key.keyboard.f6", true, false, true));
		m.steps.add(MacroStep.command("/home"));
		m.steps.add(MacroStep.waitMs(250));
		MacroStep click = MacroStep.of(StepType.CLICK_SLOT);
		click.slot = 12;
		click.button = 1;
		click.shift = true;
		m.steps.add(click);
		m.cooldownMs = 1234;
		m.repeatCount = 3;
		m.trigger = io.github.hightrust.craftingmacro.macro.TriggerMode.TOGGLE;
		m.servers.add("play.example.net");
		p.macros.add(m);
		c.profiles.add(p);
		c.activeProfile = "PvP";
		c.serverProfiles.put("play.example.net", "PvP");
		c.settings.craftOpenCommand = "/workbench";

		ModConfig loaded = ConfigStore.fromJson(ConfigStore.toJson(c));
		Macro lm = loaded.active().macros.get(0);
		assertEquals(new KeyCombo("key.keyboard.f6", true, false, true), lm.key);
		assertEquals(m.steps, lm.steps);
		assertEquals(1234, lm.cooldownMs);
		assertEquals(3, lm.repeatCount);
		assertEquals(io.github.hightrust.craftingmacro.macro.TriggerMode.TOGGLE, lm.trigger);
		assertEquals("play.example.net", lm.servers.get(0));
		assertEquals("PvP", loaded.serverProfiles.get("play.example.net"));
		assertEquals("/workbench", loaded.settings.craftOpenCommand);
	}

	@Test
	void corruptFileIsBackedUpAndReplacedWithDefaults(@TempDir Path dir) throws IOException {
		Files.writeString(dir.resolve(ConfigStore.CONFIG_FILE), "{ this is not json");
		ConfigStore store = new ConfigStore(dir);
		ConfigStore.LoadResult result = store.load();
		assertEquals(3, result.config().active().macros.size());
		assertFalse(result.warnings().isEmpty());
		try (var files = Files.list(dir)) {
			assertTrue(files.anyMatch(f -> f.getFileName().toString().startsWith(ConfigStore.CONFIG_FILE + ".broken-")));
		}
	}

	@Test
	void saveThenLoadFromDisk(@TempDir Path dir) throws IOException {
		ConfigStore store = new ConfigStore(dir);
		ModConfig c = DefaultMacros.defaultConfig();
		c.active().macros.get(0).name = "Renamed";
		store.save(c);
		assertTrue(Files.exists(dir.resolve(ConfigStore.CONFIG_FILE)));
		assertFalse(Files.exists(dir.resolve(ConfigStore.CONFIG_FILE + ".tmp")), "temp file must be moved into place");
		assertEquals("Renamed", store.load().config().active().macros.get(0).name);
	}

	@Test
	void unknownEnumsAndHostileValuesAreRepaired() {
		String json = """
				{"configVersion":2,"activeProfile":"Nope","profiles":[{"name":"../../evil<>","macros":[
				  {"id":"a","name":"","key":{"key":"not a key"},"type":"HACK","steps":[{"type":"DELETE_WORLD"},{"type":"WAIT_TICKS","amount":-50}],
				   "cooldownMs":-1,"repeatCount":99999,"holdIntervalMs":0},
				  {"id":"a","name":"dup id","steps":[]}
				]}],"settings":{"serverTimeoutTicks":999999}}
				""";
		ModConfig c = ConfigStore.fromJson(json);
		Profile p = c.profiles.get(0);
		assertTrue(ConfigValidator.isValidProfileName(p.name), "name sanitized: " + p.name);
		assertFalse(p.name.contains("/") || p.name.contains("."));
		assertEquals(p.name, c.activeProfile, "missing active profile falls back to first");
		Macro m = p.macros.get(0);
		assertEquals("Unnamed", m.name);
		assertFalse(m.key.isBound());
		assertEquals(MacroType.SEQUENCE, m.type);
		assertEquals(1, m.steps.size(), "unknown step type dropped");
		assertEquals(0, m.steps.get(0).amount);
		assertEquals(0, m.cooldownMs);
		assertEquals(100, m.repeatCount);
		assertEquals(50, m.holdIntervalMs);
		assertNotEquals(p.macros.get(0).id, p.macros.get(1).id, "duplicate ids made unique");
		assertEquals(400, c.settings.serverTimeoutTicks);
	}

	@Test
	void newerConfigVersionIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> ConfigStore.fromJson("{\"configVersion\":999}"));
	}
}
