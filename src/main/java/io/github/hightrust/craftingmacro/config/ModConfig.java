package io.github.hightrust.craftingmacro.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Root of the persisted configuration file. */
public final class ModConfig {
	public static final int CURRENT_VERSION = 2;

	public int configVersion = CURRENT_VERSION;
	public Settings settings = new Settings();
	public String activeProfile = "Default";
	/** Which built-in presets have been offered; new presets are added once to existing configs. */
	public int presetsVersion = 0;
	public static final int CURRENT_PRESETS = 2;
	public List<Profile> profiles = new ArrayList<>();
	/** Normalized server address -> profile name, applied automatically on join. */
	public Map<String, String> serverProfiles = new LinkedHashMap<>();

	public Profile profile(String name) {
		for (Profile p : profiles) {
			if (p.name.equalsIgnoreCase(name)) return p;
		}
		return null;
	}

	public Profile active() {
		Profile p = profile(activeProfile);
		if (p == null) {
			if (profiles.isEmpty()) profiles.add(new Profile("Default"));
			p = profiles.get(0);
			activeProfile = p.name;
		}
		return p;
	}
}
