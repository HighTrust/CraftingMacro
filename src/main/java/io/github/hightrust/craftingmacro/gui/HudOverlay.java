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
		Font font = mc.font;

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
}
