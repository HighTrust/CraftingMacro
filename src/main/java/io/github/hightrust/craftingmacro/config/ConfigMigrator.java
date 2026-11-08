package io.github.hightrust.craftingmacro.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Upgrades raw configuration JSON from older versions to {@link ModConfig#CURRENT_VERSION}.
 * Works on the JSON tree so old field names never need to exist in the Java model.
 * <ul>
 *   <li>v1: a single top-level {@code macros} array, keys stored as {@code keybind} strings, no profiles.</li>
 *   <li>v2: profiles, {@code key} objects with modifiers, server profiles.</li>
 * </ul>
 */
public final class ConfigMigrator {
	private ConfigMigrator() {
	}

	public static int versionOf(JsonObject root) {
		JsonElement v = root.get("configVersion");
		if (v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) return v.getAsInt();
		return 1;
	}

	/** Migrates in place and returns the same object. Throws if the file is from a newer mod version. */
	public static JsonObject migrate(JsonObject root) {
		int version = versionOf(root);
		if (version > ModConfig.CURRENT_VERSION) {
			throw new IllegalArgumentException("Config version " + version + " is newer than this mod supports (" + ModConfig.CURRENT_VERSION + ")");
		}
		if (version < 2) migrateV1ToV2(root);
		root.addProperty("configVersion", ModConfig.CURRENT_VERSION);
		return root;
	}

	private static void migrateV1ToV2(JsonObject root) {
		JsonArray macros = root.has("macros") && root.get("macros").isJsonArray() ? root.getAsJsonArray("macros") : new JsonArray();
		for (JsonElement e : macros) {
			if (!e.isJsonObject()) continue;
			JsonObject m = e.getAsJsonObject();
			if (m.has("keybind") && !m.has("key")) {
				JsonObject key = new JsonObject();
				String raw = m.get("keybind").isJsonPrimitive() ? m.get("keybind").getAsString() : "";
				key.addProperty("key", raw.isBlank() ? "" : (raw.startsWith("key.") ? raw : "key.keyboard." + raw.toLowerCase(java.util.Locale.ROOT)));
				key.addProperty("ctrl", false);
				key.addProperty("shift", false);
				key.addProperty("alt", false);
				m.add("key", key);
				m.remove("keybind");
			}
			// v1 stored delay as "delayMs"
			if (m.has("delayMs") && !m.has("startDelayMs")) {
				m.add("startDelayMs", m.get("delayMs"));
				m.remove("delayMs");
			}
		}
		JsonObject profile = new JsonObject();
		profile.add("name", new JsonPrimitive("Default"));
		profile.add("macros", macros);
		JsonArray profiles = new JsonArray();
		profiles.add(profile);
		root.remove("macros");
		root.add("profiles", profiles);
		root.addProperty("activeProfile", "Default");
		if (!root.has("serverProfiles")) root.add("serverProfiles", new JsonObject());
	}
}
