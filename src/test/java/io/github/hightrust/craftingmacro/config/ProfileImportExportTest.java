package io.github.hightrust.craftingmacro.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProfileImportExportTest {
	@Test
	void exportThenImportRoundTrips(@TempDir Path dir) throws IOException {
		ConfigStore store = new ConfigStore(dir);
		Profile p = DefaultMacros.defaultProfile();
		p.name = "My Server";
		Path file = store.exportProfile(p);
		assertEquals("my_server.json", file.getFileName().toString());
		assertEquals(dir.resolve("exports").normalize(), file.getParent().normalize());

		List<String> exports = store.listExports();
		assertEquals(List.of("my_server.json"), exports);

		Profile imported = store.importProfile("my_server.json");
		assertEquals("My Server", imported.name);
		assertEquals(p.macros.size(), imported.macros.size());
		assertEquals(p.macros.get(2).steps, imported.macros.get(2).steps);
	}

	@Test
	void exportFileNameCannotEscapeFolder(@TempDir Path dir) throws IOException {
		ConfigStore store = new ConfigStore(dir);
		Profile p = new Profile("../../../etc/passwd");
		Path file = store.exportProfile(p);
		assertEquals(dir.resolve("exports").normalize(), file.getParent().normalize());
		assertFalse(file.getFileName().toString().contains(".."));
		assertEquals("etc_passwd.json", ConfigStore.safeFileName("../../../etc/passwd"));
		assertEquals("profile.json", ConfigStore.safeFileName("////"));
	}

	@Test
	void importRejectsTraversalAndBadNames(@TempDir Path dir) throws IOException {
		ConfigStore store = new ConfigStore(dir);
		Files.createDirectories(dir.resolve("exports"));
		Files.writeString(dir.resolve("secret.json"), ConfigStore.profileToJson(new Profile("x")));
		assertThrows(IOException.class, () -> store.importProfile("../secret.json"));
		assertThrows(IOException.class, () -> store.importProfile("..\\secret.json"));
		assertThrows(IOException.class, () -> store.importProfile("C:/secret.json"));
		assertThrows(IOException.class, () -> store.importProfile("missing.json"));
		assertThrows(IOException.class, () -> store.importProfile("notjson.txt"));
	}

	@Test
	void importRejectsWrongFormatAndOversizedFiles(@TempDir Path dir) throws IOException {
		ConfigStore store = new ConfigStore(dir);
		Path exports = Files.createDirectories(dir.resolve("exports"));
		Files.writeString(exports.resolve("wrong.json"), "{\"format\":\"something-else\",\"profile\":{}}");
		Files.writeString(exports.resolve("broken.json"), "{{{");
		Files.writeString(exports.resolve("big.json"), "x".repeat((int) ConfigStore.MAX_IMPORT_BYTES + 1));
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> store.importProfile("wrong.json"));
		assertTrue(e.getMessage().contains("Not a Crafting Macro"));
		assertThrows(IllegalArgumentException.class, () -> store.importProfile("broken.json"));
		assertThrows(IOException.class, () -> store.importProfile("big.json"));
	}

	@Test
	void importedDataIsValidatedAndClamped() {
		String json = """
				{"format":"craftingmacro-profile","configVersion":2,"profile":{"name":"Imported!!","macros":[
				  {"name":"x","steps":[{"type":"CRAFT","text":"MINECRAFT:STICK","amount":999999}],"cooldownMs":-5}
				]}}
				""";
		Profile p = ConfigStore.profileFromJson(json);
		assertEquals("Imported", p.name);
		assertEquals("minecraft:stick", p.macros.get(0).steps.get(0).text);
		assertEquals(2304, p.macros.get(0).steps.get(0).amount);
		assertEquals(0, p.macros.get(0).cooldownMs);
		assertNotNull(p.macros.get(0).id);
	}
}
