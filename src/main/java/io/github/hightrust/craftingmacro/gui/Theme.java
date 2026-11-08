package io.github.hightrust.craftingmacro.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Colors and drawing helpers for the dark, flat UI. All colors are ARGB. */
public final class Theme {
	public static final int OVERLAY = 0xB0080A0F;
	public static final int PANEL = 0xF0161A22;
	public static final int PANEL_BORDER = 0xFF2A3140;
	public static final int SURFACE = 0xFF1E2430;
	public static final int SURFACE_HOVER = 0xFF283041;
	public static final int SURFACE_SELECTED = 0xFF2D3A55;
	public static final int ACCENT = 0xFF5B8CFF;
	public static final int ACCENT_HOVER = 0xFF7AA2FF;
	public static final int DANGER = 0xFFE5534B;
	public static final int DANGER_HOVER = 0xFFF0706A;
	public static final int SUCCESS = 0xFF3FB950;
	public static final int WARNING = 0xFFD29922;
	public static final int TEXT = 0xFFE6EDF3;
	public static final int TEXT_DIM = 0xFF8B949E;
	public static final int TEXT_FAINT = 0xFF5C6370;
	public static final int SWITCH_OFF = 0xFF3A4252;

	private Theme() {
	}

	/** Filled rectangle with 2px "rounded" corners (corner pixels trimmed). */
	public static void rounded(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		if (w <= 4 || h <= 4) {
			g.fill(x, y, x + w, y + h, color);
			return;
		}
		g.fill(x + 2, y, x + w - 2, y + h, color);
		g.fill(x + 1, y + 1, x + 2, y + h - 1, color);
		g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, color);
		g.fill(x, y + 2, x + 1, y + h - 2, color);
		g.fill(x + w - 1, y + 2, x + w, y + h - 2, color);
	}

	/** Rounded panel with a 1px border. */
	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
		rounded(g, x, y, w, h, border);
		rounded(g, x + 1, y + 1, w - 2, h - 2, fill);
	}

	public static int lerpColor(int a, int b, float t) {
		t = Math.max(0, Math.min(1, t));
		int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
				| ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
	}

	public static int withAlpha(int color, float alpha) {
		int a = (int) (((color >>> 24) & 0xFF) * Math.max(0, Math.min(1, alpha)));
		return (a << 24) | (color & 0xFFFFFF);
	}

	/** Frame-rate independent easing toward a target (used for hover animations). */
	public static float approach(float current, float target, float speed) {
		float next = current + (target - current) * speed;
		return Math.abs(next - target) < 0.01f ? target : next;
	}
}
