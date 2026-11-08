package io.github.hightrust.craftingmacro.notify;

import io.github.hightrust.craftingmacro.config.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

/**
 * Client-side notifications shown as small animated cards on the HUD. Nothing is ever sent to the
 * server. Optionally mirrored into the local chat log.
 */
public final class Notifier {
	public enum Level { INFO, SUCCESS, WARN, ERROR }

	public record Notice(String text, Level level, long createdMs) {
	}

	public static final long LIFETIME_MS = 3500;
	private static final int MAX_VISIBLE = 5;

	private final Supplier<Settings> settings;
	private final Deque<Notice> notices = new ArrayDeque<>();
	private String lastStatus = "";
	private Level lastStatusLevel = Level.INFO;

	public Notifier(Supplier<Settings> settings) {
		this.settings = settings;
	}

	/** Show a notice. {@code macroWantsIt} is the per-macro preference (true for non-macro messages). */
	public void post(String text, Level level, boolean macroWantsIt) {
		lastStatus = text;
		lastStatusLevel = level;
		if (!macroWantsIt || !settings.get().notificationsEnabled || text == null || text.isBlank()) return;
		synchronized (notices) {
			notices.addLast(new Notice(text, level, System.currentTimeMillis()));
			while (notices.size() > MAX_VISIBLE) notices.removeFirst();
		}
		if (settings.get().notificationsInChat) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) {
				ChatFormatting color = switch (level) {
					case SUCCESS -> ChatFormatting.GREEN;
					case WARN -> ChatFormatting.GOLD;
					case ERROR -> ChatFormatting.RED;
					default -> ChatFormatting.AQUA;
				};
				mc.player.sendSystemMessage(Component.literal("[Macro] ").withStyle(ChatFormatting.DARK_GRAY)
						.append(Component.literal(text).withStyle(color)));
			}
		}
	}

	public void info(String text, boolean macroWantsIt) {
		post(text, Level.INFO, macroWantsIt);
	}

	public void success(String text, boolean macroWantsIt) {
		post(text, Level.SUCCESS, macroWantsIt);
	}

	public void warn(String text) {
		post(text, Level.WARN, true);
	}

	public void error(String text) {
		post(text, Level.ERROR, true);
	}

	/** Visible notices, oldest first; expired ones are pruned. */
	public List<Notice> visible() {
		long now = System.currentTimeMillis();
		synchronized (notices) {
			notices.removeIf(n -> now - n.createdMs() > LIFETIME_MS);
			return new ArrayList<>(notices);
		}
	}

	/** Most recent status line, shown in the macro manager screen. */
	public String lastStatus() {
		return lastStatus;
	}

	public Level lastStatusLevel() {
		return lastStatusLevel;
	}
}
