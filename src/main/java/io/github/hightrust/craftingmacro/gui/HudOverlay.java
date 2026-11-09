package io.github.hightrust.craftingmacro.gui;

import io.github.hightrust.craftingmacro.MacroManager;
import io.github.hightrust.craftingmacro.exec.MacroRun;
import io.github.hightrust.craftingmacro.notify.Notifier;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/** Running-macro indicator (top-left) and notification cards (top-right). */
public final class HudOverlay implements HudElement {
	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		MacroManager manager = MacroManager.get();
		Minecraft mc = Minecraft.getInstance();
		if (manager == null) return;
		Font font = Fonts.ui();

		if (manager.settings().showRunningIndicator) {
			int y = 4;
			for (MacroRun run : manager.executor().activeRuns()) {
				String text = run.macro().name + "  " + run.currentStepNumber() + "/" + run.totalSteps();
				int w = font.width(text) + 18;
				Theme.rounded(g, 4, y, w, 14, 0xC0161A22);
				long t = System.currentTimeMillis();
				float pulse = (float) (0.5 + 0.5 * Math.sin(t / 180.0));
				Theme.rounded(g, 8, y + 4, 6, 6, Theme.lerpColor(Theme.ACCENT, 0xFFFFFFFF, pulse * 0.4f));
				g.text(font, text, 18, y + 3, Theme.TEXT, false);
				y += 17;
			}
		}

		if (mc.gui.screen() == null) {
			drawCraftFeed(g, font);
			drawBanner(g, font);
		}

		List<Notifier.Notice> notices = manager.notifier().visible();
		int y = 4;
		long now = System.currentTimeMillis();
		for (int i = notices.size() - 1; i >= 0; i--) {
			Notifier.Notice n = notices.get(i);
			long age = now - n.createdMs();
			float in = Math.min(1f, age / 150f);
			float out = Math.min(1f, (Notifier.LIFETIME_MS - age) / 300f);
			float alpha = Math.max(0f, Math.min(in, out));
			String text = n.text();
			int maxW = Math.min(260, g.guiWidth() / 2);
			if (font.width(text) > maxW) text = font.plainSubstrByWidth(text, maxW - font.width("...")) + "...";
			int w = font.width(text) + 16;
			int x = g.guiWidth() - w - 4 + Math.round((1 - in) * 20);
			int color = switch (n.level()) {
				case SUCCESS -> Theme.SUCCESS;
				case WARN -> Theme.WARNING;
				case ERROR -> Theme.DANGER;
				default -> Theme.ACCENT;
			};
			Theme.rounded(g, x, y, w, 16, Theme.withAlpha(0xE0161A22, alpha));
			g.fill(x, y + 2, x + 2, y + 14, Theme.withAlpha(color, alpha));
			g.text(font, text, x + 9, y + 4, Theme.withAlpha(Theme.TEXT, Math.max(0.05f, alpha)), false);
			y += 19;
		}
	}

	/**
	 * Toggle banner, top centre: drops in with an overshoot, the switch slides to its new state,
	 * a coloured glow pulses (violet/pink for ON, dim red for OFF), then it rises and fades out.
	 */
	public static void drawBanner(GuiGraphicsExtractor g, Font font) {
		MacroManager manager = MacroManager.get();
		if (manager == null) return;
		Notifier.Banner b = manager.notifier().currentBanner();
		if (b == null) return;
		long age = System.currentTimeMillis() - b.createdMs();
		float in = Math.min(1f, age / 280f);
		float c1 = 1.70158f, c3 = c1 + 1f;
		float drop = 1f + c3 * (float) Math.pow(in - 1, 3) + c1 * (float) Math.pow(in - 1, 2);
		float out = Math.min(1f, (Notifier.BANNER_MS - age) / 350f);
		float alpha = Math.max(0f, Math.min(in * 1.4f, out));
		if (alpha <= 0.02f) return;

		String state = b.on() ? "ON" : "OFF";
		int w = Math.max(150, Math.max(font.width(b.title()), font.width(b.subtitle())) + 70);
		int h = 34;
		int x = (g.guiWidth() - w) / 2;
		int y = Math.round(-h + (h + 14) * drop) - Math.round((1f - out) * 10);

		int glowColor = b.on() ? Theme.ACCENT2 : Theme.DANGER;
		float pulse = (float) (0.6 + 0.4 * Math.sin(age / 140.0));
		Theme.glow(g, x, y, w, h, glowColor, alpha * (b.on() ? 1.4f : 0.8f) * pulse);
		Theme.rounded(g, x, y, w, h, Theme.withAlpha(0xF00C0912, alpha));
		g.fillGradient(x + 2, y + 2, x + w - 2, y + h / 2, Theme.withAlpha(b.on() ? 0x33B04DFF : 0x22FF5470, alpha), 0x00000000);
		if (b.on()) Theme.accentBar(g, x + 6, y + 6, h - 12, alpha);
		else g.fill(x + 6, y + 6, x + 8, y + h - 6, Theme.withAlpha(Theme.DANGER, alpha));

		g.text(font, b.title(), x + 14, y + 7, Theme.withAlpha(Theme.TEXT, alpha), false);
		g.text(font, b.subtitle(), x + 14, y + 19, Theme.withAlpha(Theme.TEXT_DIM, alpha), false);

		// animated switch sliding to the new state, with the state label
		float t = Math.min(1f, Math.max(0f, (age - 120) / 220f));
		float knob = b.on() ? t : 1f - t;
		int sx = x + w - 34;
		int sy = y + 8;
		if (alpha > 0.4f) Theme.toggle(g, sx, sy, knob, 0f);
		int stateColor = b.on() ? Theme.lerpColor(0xFFFFFFFF, Theme.ACCENT2, Math.min(1f, age / 500f)) : Theme.DANGER;
		g.text(font, state, sx + 11 - font.width(state) / 2, sy + 15, Theme.withAlpha(stateColor, alpha), false);
	}

	/**
	 * Animated "+N item" cards, bottom-right: slide in with a slight overshoot, pop when the count
	 * grows, a shimmer sweeps across new entries, then they fade out.
	 */
	public static void drawCraftFeed(GuiGraphicsExtractor g, Font font) {
		MacroManager manager = MacroManager.get();
		if (manager == null) return;
		List<io.github.hightrust.craftingmacro.notify.CraftLog.FeedEntry> feed = manager.craftLog().feed();
		if (feed.isEmpty()) return;
		long now = System.currentTimeMillis();
		int y = g.guiHeight() - 60;
		for (int i = feed.size() - 1; i >= 0; i--) {
			var e = feed.get(i);
			long age = now - e.createdMs;
			long sinceBump = now - e.bumpedMs;
			float in = Math.min(1f, age / 260f);
			// ease-out-back for a little overshoot
			float c1 = 1.70158f, c3 = c1 + 1f;
			float slide = 1f + c3 * (float) Math.pow(in - 1, 3) + c1 * (float) Math.pow(in - 1, 2);
			float out = Math.min(1f, (io.github.hightrust.craftingmacro.notify.CraftLog.FEED_LIFETIME_MS - sinceBump) / 400f);
			float alpha = Math.max(0f, Math.min(in * 1.5f, out));
			if (alpha <= 0.02f) continue;

			net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(e.itemId);
			net.minecraft.world.item.ItemStack stack = id != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)
					? new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id))
					: net.minecraft.world.item.ItemStack.EMPTY;
			String count = "+" + e.count;
			String name = stack.isEmpty() ? e.itemId : stack.getHoverName().getString();
			int w = 30 + font.width(count) + 6 + font.width(name) + 10;
			int h = 22;
			int x = g.guiWidth() - w - 6 + Math.round((1f - slide) * (w + 12));

			Theme.rounded(g, x, y, w, h, Theme.withAlpha(0xE6161A22, alpha));
			g.fill(x, y + 3, x + 2, y + h - 3, Theme.withAlpha(Theme.SUCCESS, alpha));
			// shimmer sweep across fresh entries
			if (age < 700) {
				int sx = x + (int) ((w + 20) * (age / 700f)) - 20;
				for (int k = 0; k < 10; k++) {
					int cx = sx + k;
					if (cx > x + 2 && cx < x + w - 2) {
						float strength = (1f - Math.abs(k - 5) / 5f) * 0.25f * alpha;
						g.fill(cx, y + 2, cx + 1, y + h - 2, Theme.withAlpha(0xFFFFFFFF, strength));
					}
				}
			}
			if (!stack.isEmpty() && alpha > 0.5f) g.item(stack, x + 7, y + 3);

			// pop the count when it grows
			float pop = sinceBump < 250 ? 1f + 0.35f * (float) Math.sin(sinceBump / 250f * Math.PI) : 1f;
			int countColor = Theme.lerpColor(0xFF7CF29A, Theme.SUCCESS, Math.min(1f, sinceBump / 600f));
			int tx = x + 28;
			g.pose().pushMatrix();
			g.pose().translate(tx, y + 7);
			g.pose().scale(pop, pop);
			g.text(font, count, 0, 0, Theme.withAlpha(countColor, alpha), true);
			g.pose().popMatrix();
			g.text(font, name, tx + font.width(count) + 6, y + 7, Theme.withAlpha(Theme.TEXT, alpha), false);
			y -= h + 4;
		}
	}
}
