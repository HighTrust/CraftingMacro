package io.github.hightrust.craftingmacro.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads and writes configuration JSON. All file access is confined to one directory
 * (normally {@code .minecraft/config/craftingmacro}) and its {@code exports} subfolder.
 * File names for export/import are sanitized, so imported data can never point outside it.
 */
public final class ConfigStore {
	public static final String CONFIG_FILE = "macros.json";
	public static final String EXPORT_DIR = "exports";
	public static final String PROFILE_FORMAT = "craftingmacro-profile";
	public static final long MAX_IMPORT_BYTES = 1024 * 1024;

	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final Path dir;

	public ConfigStore(Path dir) {
		this.dir = dir;
	}

	public Path directory() {
		return dir;
	}

	public Path configFile() {
		return dir.resolve(CONFIG_FILE);
	}

	public Path exportDir() {
		return dir.resolve(EXPORT_DIR);
	}

	/** Result of loading: the config plus any warnings to show the user. */
	public record LoadResult(ModConfig config, List<String> warnings) {
	}

	/** Loads the config, creating defaults if missing. A corrupt file is backed up and replaced. */
	public LoadResult load() {
		List<String> warnings = new ArrayList<>();
		Path file = configFile();
		if (!Files.exists(file)) {
			return new LoadResult(DefaultMacros.defaultConfig(), warnings);
		}
		try {
			String json = Files.readString(file, StandardCharsets.UTF_8);
			ModConfig config = fromJson(json);
			warnings.addAll(ConfigValidator.repair(config));
			return new LoadResult(config, warnings);
		} catch (IOException | RuntimeException e) {
			Path backup = dir.resolve(CONFIG_FILE + ".broken-" + System.currentTimeMillis());
			try {
				Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
				warnings.add("Config was unreadable (" + e.getMessage() + "); backed up to " + backup.getFileName() + " and reset");
			} catch (IOException copyError) {
				warnings.add("Config was unreadable and could not be backed up: " + copyError.getMessage());
			}
			return new LoadResult(DefaultMacros.defaultConfig(), warnings);
		}
	}

	/** Parses, migrates and repairs config JSON. */
	public static ModConfig fromJson(String json) {
		JsonElement tree = JsonParser.parseString(json);
		if (!tree.isJsonObject()) throw new JsonParseException("Config root must be an object");
		JsonObject root = ConfigMigrator.migrate(tree.getAsJsonObject());
		ModConfig config = GSON.fromJson(root, ModConfig.class);
		if (config == null) throw new JsonParseException("Empty config");
		ConfigValidator.repair(config);
		return config;
	}

	public static String toJson(ModConfig config) {
		return GSON.toJson(config);
	}

	/** Atomic save: write to a temp file, then move over the real file. */
	public void save(ModConfig config) throws IOException {
		Files.createDirectories(dir);
		writeAtomically(configFile(), toJson(config));
	}

	private static void writeAtomically(Path target, String content) throws IOException {
		Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
		Files.writeString(tmp, content, StandardCharsets.UTF_8);
		try {
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	// ---------------------------------------------------------------- export / import

	/** Turns any user-entered name into a safe bare file name ending in .json. */
	public static String safeFileName(String name) {
		String base = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "_");
		base = base.replaceAll("^_+|_+$", "");
		if (base.isEmpty()) base = "profile";
		if (base.length() > 48) base = base.substring(0, 48);
		return base + ".json";
	}

	public static String profileToJson(Profile profile) {
		JsonObject root = new JsonObject();
		root.addProperty("format", PROFILE_FORMAT);
		root.addProperty("configVersion", ModConfig.CURRENT_VERSION);
		root.add("profile", GSON.toJsonTree(profile));
		return GSON.toJson(root);
	}

	/** Parses and validates an exported profile. Throws IllegalArgumentException with a readable message. */
	public static Profile profileFromJson(String json) {
		JsonElement tree;
		try {
			tree = JsonParser.parseString(json);
		} catch (JsonParseException e) {
			throw new IllegalArgumentException("Not valid JSON");
		}
		if (!tree.isJsonObject()) throw new IllegalArgumentException("Not a profile export");
		JsonObject root = tree.getAsJsonObject();
		if (!root.has("format") || !PROFILE_FORMAT.equals(root.get("format").getAsString())) {
			throw new IllegalArgumentException("Not a Crafting Macro profile export");
		}
		int version = ConfigMigrator.versionOf(root);
		if (version > ModConfig.CURRENT_VERSION) throw new IllegalArgumentException("Export is from a newer mod version");
		if (!root.has("profile") || !root.get("profile").isJsonObject()) throw new IllegalArgumentException("Export has no profile");
		Profile profile;
		try {
			profile = GSON.fromJson(root.get("profile"), Profile.class);
		} catch (JsonParseException e) {
			throw new IllegalArgumentException("Profile data is malformed");
		}
		if (profile == null) throw new IllegalArgumentException("Profile data is empty");
		profile.name = ConfigValidator.cleanProfileName(profile.name);
		ConfigValidator.repairProfile(profile, new ArrayList<>());
		return profile;
	}

	/** Writes a profile to {@code exports/<safe name>.json} and returns the file written. */
	public Path exportProfile(Profile profile) throws IOException {
		Path exports = exportDir();
		Files.createDirectories(exports);
		Path target = exports.resolve(safeFileName(profile.name)).normalize();
		if (!target.getParent().equals(exports.normalize())) throw new IOException("Refusing to write outside the exports folder");
		writeAtomically(target, profileToJson(profile));
		return target;
	}

	/** Lists importable .json files in the exports folder. */
	public List<String> listExports() {
		List<String> names = new ArrayList<>();
		Path exports = exportDir();
		if (!Files.isDirectory(exports)) return names;
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(exports, "*.json")) {
			for (Path p : stream) {
				if (Files.isRegularFile(p)) names.add(p.getFileName().toString());
			}
		} catch (IOException ignored) {
			// An unreadable folder simply lists nothing.
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	/** Imports {@code exports/<fileName>}. Only bare file names inside the exports folder are accepted. */
	public Profile importProfile(String fileName) throws IOException {
		if (fileName == null || !fileName.matches("[A-Za-z0-9_.-]+\\.json") || fileName.contains("..")) {
			throw new IOException("Invalid file name");
		}
		Path exports = exportDir().normalize();
		Path source = exports.resolve(fileName).normalize();
		if (!source.getParent().equals(exports) || !Files.isRegularFile(source)) throw new IOException("File not found in exports folder");
		if (Files.size(source) > MAX_IMPORT_BYTES) throw new IOException("File is too large");
		return profileFromJson(Files.readString(source, StandardCharsets.UTF_8));
	}
}
