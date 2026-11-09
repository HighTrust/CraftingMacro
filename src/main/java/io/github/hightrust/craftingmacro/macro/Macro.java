package io.github.hightrust.craftingmacro.macro;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** A user-defined macro. Plain mutable POJO persisted with Gson. */
public final class Macro {
	public static final String SINGLEPLAYER = "singleplayer";

	public String id = UUID.randomUUID().toString();
	public String name = "New Macro";
	public String description = "";
	public KeyCombo key = KeyCombo.unbound();
	public MacroType type = MacroType.SEQUENCE;
	public boolean enabled = true;
	public List<MacroStep> steps = new ArrayList<>();

	/** Minimum time between two activations. */
	public int cooldownMs = 250;
	/** Delay between activation and the first step. */
	public int startDelayMs = 0;
	/** How many times the whole step list runs per activation (1 = once). */
	public int repeatCount = 1;
	/** How the key triggers the macro: click (once), hold, or toggle. */
	public TriggerMode trigger = TriggerMode.CLICK;
	/** @deprecated legacy flag from 1.0.0; migrated to {@link #trigger} = HOLD on load. */
	@Deprecated
	public boolean holdToRepeat = false;
	/** Pause between repeated runs in hold/toggle mode (for non-crafting macros). */
	public int holdIntervalMs = 500;
	public boolean notify = true;
	/** Close a crafting screen that this macro opened once it finishes. */
	public boolean autoClose = true;
	public boolean stopOnError = true;
	/** Server addresses this macro is limited to. Empty = everywhere. "singleplayer" matches local worlds. */
	public List<String> servers = new ArrayList<>();

	public Macro() {
	}

	public Macro(String name, MacroType type, KeyCombo key) {
		this.name = name;
		this.type = type;
		this.key = key;
	}

	public Macro withStep(MacroStep step) {
		steps.add(step);
		return this;
	}

	public Macro withDescription(String description) {
		this.description = description;
		return this;
	}

	/** Deep copy. {@code newId} gives the copy a fresh identity (used by "duplicate"). */
	public Macro copy(boolean newId) {
		Macro m = new Macro();
		m.id = newId ? UUID.randomUUID().toString() : id;
		m.name = name;
		m.description = description;
		m.key = key == null ? KeyCombo.unbound() : key.copy();
		m.type = type;
		m.enabled = enabled;
		m.steps = new ArrayList<>();
		for (MacroStep s : steps) m.steps.add(s.copy());
		m.cooldownMs = cooldownMs;
		m.startDelayMs = startDelayMs;
		m.repeatCount = repeatCount;
		m.trigger = trigger;
		m.holdToRepeat = holdToRepeat;
		m.holdIntervalMs = holdIntervalMs;
		m.notify = notify;
		m.autoClose = autoClose;
		m.stopOnError = stopOnError;
		m.servers = new ArrayList<>(servers);
		return m;
	}

	/** True if the macro contains a crafting step. */
	public boolean crafts() {
		for (MacroStep s : steps) if (s.type == StepType.CRAFT || s.type == StepType.CRAFT_PATTERN) return true;
		return false;
	}

	/** True if any step needs the exclusive inventory lock. */
	public boolean mutatesInventory() {
		for (MacroStep s : steps) {
			if (s.type != null && s.type.mutatesInventory()) return true;
		}
		return false;
	}

	/** Whether this macro may run on the given server address (null/blank = singleplayer). */
	public boolean availableOn(String serverAddress) {
		if (servers == null || servers.isEmpty()) return true;
		String current = normalizeServer(serverAddress);
		for (String s : servers) {
			if (s != null && normalizeServer(s).equals(current)) return true;
		}
		return false;
	}

	public static String normalizeServer(String address) {
		if (address == null || address.isBlank()) return SINGLEPLAYER;
		String a = address.toLowerCase(Locale.ROOT).trim();
		if (a.endsWith(":25565")) a = a.substring(0, a.length() - 6);
		return a;
	}

	public boolean matchesSearch(String query) {
		if (query == null || query.isBlank()) return true;
		String q = query.toLowerCase(Locale.ROOT).trim();
		if (name != null && name.toLowerCase(Locale.ROOT).contains(q)) return true;
		if (description != null && description.toLowerCase(Locale.ROOT).contains(q)) return true;
		if (key != null && key.describe().toLowerCase(Locale.ROOT).contains(q)) return true;
		for (MacroStep s : steps) {
			if (s.summary().toLowerCase(Locale.ROOT).contains(q)) return true;
		}
		return false;
	}
}
