package io.github.hightrust.craftingmacro.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;

/**
 * Modern typography for the mod's screens. Bundles Inter (UI text) and Lucide (icons) as
 * TrueType font providers, and offers a {@link Font} whose default face is Inter, so every
 * plain string drawn by our screens and widgets uses it without touching the rest of the game.
 */
public final class Fonts {
	public static final FontDescription UI = new FontDescription.Resource(Identifier.fromNamespaceAndPath("craftingmacro", "ui"));
	public static final FontDescription BOLD = new FontDescription.Resource(Identifier.fromNamespaceAndPath("craftingmacro", "ui_bold"));
	public static final FontDescription ICONS = new FontDescription.Resource(Identifier.fromNamespaceAndPath("craftingmacro", "icons"));

	private static Font ui;
	private static Font.Provider wrapped;

	private Fonts() {
	}

	/** Font whose default face is Inter. Falls back to the vanilla font if anything goes wrong. */
	public static Font ui() {
		Font base = Minecraft.getInstance().font;
		if (ui != null) return ui;
		try {
			Font.Provider provider = baseProvider(base);
			wrapped = new Font.Provider() {
				@Override
				public net.minecraft.client.gui.GlyphSource glyphs(FontDescription font) {
					return provider.glyphs(font == null || FontDescription.DEFAULT.equals(font) ? UI : font);
				}

				@Override
				public EffectGlyph effect() {
					return provider.effect();
				}
			};
			ui = new Font(wrapped);
		} catch (ReflectiveOperationException | RuntimeException e) {
			io.github.hightrust.craftingmacro.MacroManager.LOGGER.warn("Custom UI font unavailable, using vanilla font", e);
			ui = base;
		}
		return ui;
	}

	private static Font.Provider baseProvider(Font font) throws ReflectiveOperationException {
		for (Field f : Font.class.getDeclaredFields()) {
			if (f.getType() == Font.Provider.class) {
				f.setAccessible(true);
				return (Font.Provider) f.get(font);
			}
		}
		throw new NoSuchFieldException("Font.provider");
	}

	/** Text in the bold Inter face (use instead of ChatFormatting.BOLD, which smears TTF glyphs). */
	public static MutableComponent bold(String text) {
		return Component.literal(text).withStyle(Style.EMPTY.withFont(BOLD));
	}

	/** A single Lucide icon glyph. */
	public static MutableComponent icon(int codepoint) {
		return Component.literal(Character.toString(codepoint)).withStyle(Style.EMPTY.withFont(ICONS));
	}

	/** Lucide codepoints (see font/LUCIDE_LICENSE.txt). */
	public static final class Icon {
		public static final int SPARKLES = 0xe412;
		public static final int TERMINAL = 0xe181;
		public static final int HAMMER = 0xe0ec;
		public static final int LIST_ORDERED = 0xe1d1;
		public static final int SETTINGS = 0xe154;
		public static final int CHART = 0xe2a2;
		public static final int USERS = 0xe1a4;
		public static final int PALETTE = 0xe1dd;
		public static final int SEARCH = 0xe151;
		public static final int PLUS = 0xe13d;
		public static final int CHEVRON_DOWN = 0xe06d;
		public static final int CHEVRON_RIGHT = 0xe06f;
		public static final int PICKAXE = 0xe5c6;
		public static final int ZAP = 0xe1b4;
		public static final int TIMER = 0xe1e0;
		public static final int WRENCH = 0xe1b1;
		public static final int PACKAGE = 0xe129;
		public static final int TRASH = 0xe18e;
		public static final int PENCIL = 0xe1f9;
		public static final int PLAY = 0xe13c;
		public static final int PAUSE = 0xe12e;
		public static final int X = 0xe1b2;
		public static final int CHECK = 0xe06c;
		public static final int KEYBOARD = 0xe284;
		public static final int STOP = 0xe083;
		public static final int ARROW_UP = 0xe04a;
		public static final int ARROW_DOWN = 0xe042;
		public static final int WORKFLOW = 0xe425;
		public static final int REFRESH = 0xe145;
		public static final int CIRCLE = 0xe226;

		private Icon() {
		}
	}
}
