package io.github.hightrust.craftingmacro.config;

import io.github.hightrust.craftingmacro.macro.Macro;
import io.github.hightrust.craftingmacro.macro.MacroValidator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Repairs and bounds configuration data after loading or importing. Configuration is pure
 * data: nothing in it is ever executed as code or used as a file path.
 */
public final class ConfigValidator {
	public static final int MAX_PROFILES = 32;
	public static final int MAX_MACROS_PER_PROFILE = 256;
	public static final int MAX_PROFILE_NAME = 32;
	private static final Pattern PROFILE_NAME = Pattern.compile("[A-Za-z0-9 _\\-()]{1," + MAX_PROFILE_NAME + "}");

	private ConfigValidator() {
	}

	public static boolean isValidProfileName(String name) {
		return name != null && PROFILE_NAME.matcher(name).matches() && !name.isBlank();
	}

	/** Makes a profile name safe: strips disallowed characters and clips length. */
	public static String cleanProfileName(String name) {
		if (name == null) return "Profile";
		String cleaned = name.replaceAll("[^A-Za-z0-9 _\\-()]", "").strip();
		if (cleaned.length() > MAX_PROFILE_NAME) cleaned = cleaned.substring(0, MAX_PROFILE_NAME).strip();
		return cleaned.isEmpty() ? "Profile" : cleaned;
	}

	/** Repairs a config in place. Returns warnings describing anything that was changed. */
	public static List<String> repair(ModConfig config) {
		List<String> warnings = new ArrayList<>();
		if (config.settings == null) {
			config.settings = new Settings();
			warnings.add("Settings were missing and have been reset");
		}
		Settings s = config.settings;
		s.serverTimeoutTicks = MacroValidator.clamp(s.serverTimeoutTicks, 10, 400);
		if (s.craftOpenCommand == null) s.craftOpenCommand = "";
		if (s.craftOpenCommand.length() > MacroValidator.MAX_TEXT) s.craftOpenCommand = s.craftOpenCommand.substring(0, MacroValidator.MAX_TEXT);

		if (config.profiles == null) config.profiles = new ArrayList<>();
		config.profiles.removeIf(p -> p == null);
		Set<String> seen = new HashSet<>();
		for (Iterator<Profile> it = config.profiles.iterator(); it.hasNext(); ) {
			Profile p = it.next();
			String clean = cleanProfileName(p.name);
			String unique = clean;
			for (int i = 2; seen.contains(unique.toLowerCase(Locale.ROOT)); i++) unique = clean + " " + i;
			if (!unique.equals(p.name)) warnings.add("Profile renamed to \"" + unique + "\"");
			p.name = unique;
			seen.add(unique.toLowerCase(Locale.ROOT));
			repairProfile(p, warnings);
		}
		while (config.profiles.size() > MAX_PROFILES) {
			config.profiles.remove(config.profiles.size() - 1);
			warnings.add("Too many profiles; extras were dropped");
		}
		if (config.profiles.isEmpty()) {
			config.profiles.add(DefaultMacros.defaultProfile());
			warnings.add("No profiles found; created the Default profile");
		}
		if (config.activeProfile == null || config.profile(config.activeProfile) == null) {
			config.activeProfile = config.profiles.get(0).name;
		}
		if (config.serverProfiles == null) config.serverProfiles = new LinkedHashMap<>();
		Map<String, String> cleaned = new LinkedHashMap<>();
		for (Map.Entry<String, String> e : config.serverProfiles.entrySet()) {
			if (e.getKey() == null || e.getValue() == null) continue;
			Profile p = config.profile(e.getValue());
			if (p != null) cleaned.put(Macro.normalizeServer(e.getKey()), p.name);
		}
		config.serverProfiles = cleaned;
		config.configVersion = ModConfig.CURRENT_VERSION;
		return warnings;
	}

	public static void repairProfile(Profile p, List<String> warnings) {
		if (p.macros == null) p.macros = new ArrayList<>();
		p.macros.removeIf(m -> m == null);
		while (p.macros.size() > MAX_MACROS_PER_PROFILE) {
			p.macros.remove(p.macros.size() - 1);
			warnings.add("Profile \"" + p.name + "\" had too many macros; extras were dropped");
		}
		Set<String> ids = new HashSet<>();
		for (Macro m : p.macros) {
			MacroValidator.sanitize(m);
			if (!ids.add(m.id)) {
				m.id = java.util.UUID.randomUUID().toString();
				ids.add(m.id);
			}
		}
	}
}
