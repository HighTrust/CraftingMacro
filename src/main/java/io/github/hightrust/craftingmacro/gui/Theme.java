package io.github.hightrust.craftingmacro.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Colors and drawing helpers. Style: near-black panels with a faint violet haze, a
 * violet-to-pink accent gradient, soft outer glow, rounded corners and accent bars.
 * All colors are ARGB. Surface colors depend on the active {@link Mode} (dark, glass, clear).
 */
public final class Theme {
	/** Visual style of the panels. GLASS blurs the world behind the menu; CLEAR shows it untouched. */
	public enum Mode {
		DARK("Dark"), GLASS("Glass"), CLEAR("Clear");

		private final String displayName;

		Mode(String displayName) {
			this.displayName = displayName;
		}

		public String displayName() {
			return displayName;
		}

		public Mode next() {
			return values()[(ordinal() + 1) % values().length];
		}

		public static Mode parse(String s) {
			for (Mode m : values()) if (m.name().equalsIgnoreCase(s)) return m;
			return DARK;
		}
	}

	private static Mode mode = Mode.DARK;

	public static int OVERLAY = 0xC0050308;
	public static int PANEL = 0xF50C0912;
	public static int PANEL_BORDER = 0xFF2A1D3B;
	public static int SURFACE = 0xFF16111E;
	public static int SURFACE_HOVER = 0xFF201829;
	public static int SURFACE_SELECTED = 0xFF2A1D3B;
	public static final int ACCENT = 0xFFB04DFF;
	public static final int ACCENT_HOVER = 0xFFC575FF;
	public static final int ACCENT2 = 0xFFFF4FA8;
	public static final int DANGER = 0xFFFF5470;
	public static final int DANGER_HOVER = 0xFFFF7A90;
	public static final int SUCCESS = 0xFF4BE08A;
	public static final int WARNING = 0xFFFFB547;
	public static final int TEXT = 0xFFF2ECF8;
	public static final int TEXT_DIM = 0xFFA399B3;
	public static final int TEXT_FAINT = 0xFF665C75;
	public static int SWITCH_OFF = 0xFF2B2335;

	/** Slightly darker strip for sidebars. */
	public static int SIDEBAR = 0xFF0A0710;

	private Theme() {
	}

	public static Mode mode() {
		return mode;
	}

	/** True when the world behind menus should be blurred. */
	public static boolean blur() {
		return mode == Mode.GLASS;
	}

	public static void apply(String name) {
		apply(Mode.parse(name));
	}

	public static void apply(Mode m) {
		mode = m;
		switch (m) {
			case GLASS -> {
				OVERLAY = 0x38050308;
				PANEL = 0x8C0E0A16;
				PANEL_BORDER = 0x55D9C2FF;
				SURFACE = 0x5A1C1528;
				SURFACE_HOVER = 0x80281E38;
				SURFACE_SELECTED = 0x9A33244A;
				SWITCH_OFF = 0x8C3A3048;
				SIDEBAR = 0x40000000;
			}
			case CLEAR -> {
				OVERLAY = 0x00000000;
				PANEL = 0x4A0C0912;
				PANEL_BORDER = 0x40D9C2FF;
				SURFACE = 0x40141020;
				SURFACE_HOVER = 0x66201830;
				SURFACE_SELECTED = 0x882A1D3B;
				SWITCH_OFF = 0x803A3048;
				SIDEBAR = 0x30000000;
			}
			default -> {
				OVERLAY = 0xC0050308;
				PANEL = 0xF50C0912;
				PANEL_BORDER = 0xFF2A1D3B;
				SURFACE = 0xFF16111E;
				SURFACE_HOVER = 0xFF201829;
				SURFACE_SELECTED = 0xFF2A1D3B;
				SWITCH_OFF = 0xFF2B2335;
				SIDEBAR = 0xFF0A0710;
			}
		}
	}

	/** 1px rounded outline that matches {@link #rounded}; safe with translucent fills. */
	public static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		if (w <= 6 || h <= 6) {
			g.fill(x, y, x + w, y + 1, color);
			g.fill(x, y + h - 1, x + w, y + h, color);
			g.fill(x, y + 1, x + 1, y + h - 1, color);
			g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
			return;
		}
		g.fill(x + 3, y, x + w - 3, y + 1, color);
		g.fill(x + 3, y + h - 1, x + w - 3, y + h, color);
		g.fill(x, y + 3, x + 1, y + h - 3, color);
		g.fill(x + w - 1, y + 3, x + w, y + h - 3, color);
		g.fill(x + 1, y + 1, x + 3, y + 2, color);
		g.fill(x + w - 3, y + 1, x + w - 1, y + 2, color);
		g.fill(x + 1, y + h - 2, x + 3, y + h - 1, color);
		g.fill(x + w - 3, y + h - 2, x + w - 1, y + h - 1, color);
		g.fill(x + 1, y + 2, x + 2, y + 3, color);
		g.fill(x + w - 2, y + 2, x + w - 1, y + 3, color);
		g.fill(x + 1, y + h - 3, x + 2, y + h - 2, color);
		g.fill(x + w - 2, y + h - 3, x + w - 1, y + h - 2, color);
	}

	/** Translucent-friendly card: fill plus outline (outline drawn on top, never underneath). */
	public static void card(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
		rounded(g, x, y, w, h, fill);
		outline(g, x, y, w, h, border);
	}

	/** Cubic ease-out for 0..1. */
	public static float easeOut(float t) {
		t = Math.max(0, Math.min(1, t));
		float u = 1 - t;
		return 1 - u * u * u;
	}

	/** Rounded filled rectangle (radius 3, anti-aliased look via trimmed corner rows). */
	public static void rounded(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		if (w <= 6 || h <= 6) {
			g.fill(x, y, x + w, y + h, color);
			return;
		}
		g.fill(x, y + 3, x + w, y + h - 3, color);
		g.fill(x + 3, y, x + w - 3, y + 1, color);
		g.fill(x + 1, y + 1, x + w - 1, y + 3, color);
		g.fill(x + 3, y + h - 1, x + w - 3, y + h, color);
		g.fill(x + 1, y + h - 3, x + w - 1, y + h - 1, color);
	}

	/** Rounded panel with a 1px border. */
	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
		glow(g, x, y, w, h, ACCENT, mode == Mode.DARK ? 0.5f : 0.8f);
		rounded(g, x, y, w, h, fill);
		outline(g, x, y, w, h, border);
		if (mode != Mode.DARK) {
			// glass sheen: a soft white highlight along the top edge
			g.fillGradient(x + 3, y + 1, x + w - 3, y + 14, 0x16FFFFFF, 0x00FFFFFF);
		}
		// faint violet haze at the top and pink at the bottom-left
		g.fillGradient(x + 2, y + 2, x + w - 2, y + Math.min(h / 2, 90), 0x1CB04DFF, 0x00B04DFF);
		g.fillGradient(x + 2, y + h - Math.min(h / 3, 60), x + w / 2, y + h - 2, 0x00FF4FA8, 0x10FF4FA8);
	}

	/** Soft outer glow made of expanding translucent rounded outlines. */
	public static void glow(GuiGraphicsExtractor g, int x, int y, int w, int h, int color, float strength) {
		float[] alphas = {0.16f, 0.09f, 0.05f, 0.025f};
		for (int i = 0; i < alphas.length; i++) {
			int d = i + 1;
			int c = withAlpha(color, alphas[i] * strength);
			g.fill(x - d + 3, y - d, x + w + d - 3, y - d + 1, c);
			g.fill(x - d + 3, y + h + d - 1, x + w + d - 3, y + h + d, c);
			g.fill(x - d, y - d + 3, x - d + 1, y + h + d - 3, c);
			g.fill(x + w + d - 1, y - d + 3, x + w + d, y + h + d - 3, c);
		}
	}

	/** Horizontal gradient (drawn in thin columns). */
	public static void hGradient(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int left, int right) {
		int w = x1 - x0;
		if (w <= 0) return;
		int step = Math.max(1, w / 48);
		for (int x = x0; x < x1; x += step) {
			float t = (x - x0) / (float) w;
			g.fill(x, y0, Math.min(x + step, x1), y1, lerpColor(left, right, t));
		}
	}

	/** Rounded rectangle filled with the accent gradient. */
	public static void accentPill(GuiGraphicsExtractor g, int x, int y, int w, int h, float brightness) {
		int a = lerpColor(ACCENT, 0xFFFFFFFF, brightness * 0.15f);
		int b = lerpColor(ACCENT2, 0xFFFFFFFF, brightness * 0.15f);
		rounded(g, x, y, w, h, a); // corners take the left color
		hGradient(g, x + 3, y, x + w - 3, y + h, a, b);
		g.fill(x + w - 3, y + 3, x + w, y + h - 3, b);
		g.fill(x + w - 3, y + 1, x + w - 1, y + 3, b);
		g.fill(x + w - 3, y + h - 3, x + w - 1, y + h - 1, b);
	}

	/** Vertical accent bar (violet to pink), used on the left edge of rows and headers. */
	public static void accentBar(GuiGraphicsExtractor g, int x, int y, int h, float alpha) {
		g.fillGradient(x, y, x + 2, y + h, withAlpha(ACCENT, alpha), withAlpha(ACCENT2, alpha));
	}

	/** Pill switch with glow when on. {@code knob} 0..1 animates the position. */
	public static void toggle(GuiGraphicsExtractor g, int x, int y, float knob, float hover) {
		int w = 22;
		int h = 12;
		if (knob > 0.01f) glow(g, x, y, w, h, ACCENT2, knob);
		rounded(g, x, y, w, h, SWITCH_OFF);
		if (knob > 0.01f) {
			int fillW = Math.max(6, Math.round(w * knob));
			accentPill(g, x, y, fillW, h, hover);
		}
		int kx = x + 2 + Math.round((w - 12) * knob);
		rounded(g, kx, y + 2, 8, h - 4, knob > 0.5f ? 0xFFFFFFFF : 0xFFB9B0C6);
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

	/** Frame-rate friendly easing toward a target (hover animations). */
	public static float approach(float current, float target, float speed) {
		float next = current + (target - current) * speed;
		return Math.abs(next - target) < 0.01f ? target : next;
	}
}
