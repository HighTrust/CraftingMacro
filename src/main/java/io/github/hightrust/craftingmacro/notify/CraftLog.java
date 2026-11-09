package io.github.hightrust.craftingmacro.notify;

import com.google.gson.reflect.TypeToken;
import io.github.hightrust.craftingmacro.config.ConfigStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records everything the mod crafted: a short-lived animated "+N item" feed for the HUD and
 * persistent totals (items made, ingredients used) shown on the Stats screen.
 */
public final class CraftLog {
	private static final Logger LOGGER = LoggerFactory.getLogger("CraftingMacro");
	public static final long FEED_LIFETIME_MS = 3200;
	/** Same item crafted within this window bumps the existing entry instead of adding a new one. */
	private static final long MERGE_MS = 1500;

	/** One HUD feed line. Mutable so repeated crafts can bump the count (with a "pop" animation). */
	public static final class FeedEntry {
		public final String itemId;
		public int count;
		public final long createdMs;
		public long bumpedMs;

		FeedEntry(String itemId, int count, long now) {
			this.itemId = itemId;
			this.count = count;
			this.createdMs = now;
			this.bumpedMs = now;
		}
	}

	/** Persistent totals for one crafted item. */
	public static final class ItemStats {
		public long crafted;
		public long sessions;
		public Map<String, Long> used = new LinkedHashMap<>();
		public long lastCraftedMs;
	}

	private final Path file;
	private final List<FeedEntry> feed = new ArrayList<>();
	private Map<String, ItemStats> stats = new LinkedHashMap<>();
	private boolean dirty;

	public CraftLog(Path dir) {
		this.file = dir.resolve("stats.json");
		load();
	}

	/** A batch of items just arrived in the inventory. */
	public synchronized void crafted(String itemId, int count) {
		if (count <= 0) return;
		long now = System.currentTimeMillis();
		for (FeedEntry e : feed) {
			if (e.itemId.equals(itemId) && now - e.bumpedMs < MERGE_MS) {
				e.count += count;
				e.bumpedMs = now;
				return;
			}
		}
		feed.add(new FeedEntry(itemId, count, now));
		while (feed.size() > 6) feed.remove(0);
	}

	/** A crafting session ended: add totals. */
	public synchronized void record(String itemId, int crafted, Map<String, Integer> used) {
		if (crafted <= 0) return;
		ItemStats s = stats.computeIfAbsent(itemId, k -> new ItemStats());
		s.crafted += crafted;
		s.sessions++;
		s.lastCraftedMs = System.currentTimeMillis();
		for (Map.Entry<String, Integer> e : used.entrySet()) {
			if (e.getValue() > 0) s.used.merge(e.getKey(), (long) e.getValue(), Long::sum);
		}
		dirty = true;
	}

	public synchronized List<FeedEntry> feed() {
		long now = System.currentTimeMillis();
		feed.removeIf(e -> now - e.bumpedMs > FEED_LIFETIME_MS);
		return new ArrayList<>(feed);
	}

	public synchronized Map<String, ItemStats> stats() {
		return new LinkedHashMap<>(stats);
	}

	public synchronized long totalCrafted() {
		long t = 0;
		for (ItemStats s : stats.values()) t += s.crafted;
		return t;
	}

	public synchronized void reset() {
		stats.clear();
		dirty = true;
		save();
	}

	private void load() {
		if (!Files.exists(file)) return;
		try {
			Map<String, ItemStats> loaded = ConfigStore.GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
					new TypeToken<LinkedHashMap<String, ItemStats>>() { }.getType());
			if (loaded != null) {
				loaded.values().removeIf(v -> v == null);
				for (ItemStats s : loaded.values()) if (s.used == null) s.used = new LinkedHashMap<>();
				stats = loaded;
			}
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Could not read crafting stats; starting fresh", e);
		}
	}

	public synchronized void save() {
		if (!dirty) return;
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, ConfigStore.GSON.toJson(stats), StandardCharsets.UTF_8);
			dirty = false;
		} catch (IOException e) {
			LOGGER.warn("Could not save crafting stats", e);
		}
	}
}
