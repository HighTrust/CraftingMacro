package io.github.hightrust.craftingmacro.gui;

import io.github.hightrust.craftingmacro.MacroManager;
import io.github.hightrust.craftingmacro.config.Settings;
import io.github.hightrust.craftingmacro.gui.widget.FlatButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

/** Global settings. Changes apply immediately and are saved automatically. */
public final class SettingsScreen extends BaseScreen {
	public SettingsScreen(Screen parent) {
		super("Settings", parent);
	}

	@Override
	protected int preferredWidth() {
		return 400;
	}

	@Override
	protected int preferredHeight() {
		return 290;
	}

	@Override
	protected void build() {
		Settings s = MacroManager.get().settings();
		int x = panelX + 12;
		int w = panelW - 24;
		int y = panelY + 34;
		Runnable dirty = MacroManager.get()::markDirty;

		toggle(x, y, w, "Notifications", s.notificationsEnabled, "Master switch for on-screen notifications",
				v -> { s.notificationsEnabled = v; dirty.run(); });
		y += 18;
		toggle(x, y, w, "Also show notifications in chat", s.notificationsInChat, "Mirror notifications to your local chat (never sent to the server)",
				v -> { s.notificationsInChat = v; dirty.run(); });
		y += 18;
		toggle(x, y, w, "Running-macro indicator", s.showRunningIndicator, "Small indicator in the top-left while a macro runs",
				v -> { s.showRunningIndicator = v; dirty.run(); });
		y += 18;
		toggle(x, y, w, "Allow macros in container screens", s.allowInContainerScreens,
				"Let macro keys work while an inventory/chest screen is open (never while typing in a text field)",
				v -> { s.allowInContainerScreens = v; dirty.run(); });
		y += 18;
		toggle(x, y, w, "Macro keys override vanilla keys", s.suppressVanillaConflicts,
				"When a macro uses a key that a vanilla binding also uses (e.g. L = Advancements), only the macro runs",
				v -> { s.suppressVanillaConflicts = v; dirty.run(); });
		y += 18;
		toggle(x, y, w, "Use looked-at crafting table", s.useLookedAtCraftingTable,
				"When crafting needs a 3x3 grid, use the crafting table under your crosshair first",
				v -> { s.useLookedAtCraftingTable = v; dirty.run(); });
		y += 18;
		toggle(x, y, w, "Prefer 2x2 inventory grid", s.preferInventoryGrid,
				"Craft small recipes in the inventory grid instead of opening a crafting table",
				v -> { s.preferInventoryGrid = v; dirty.run(); });
		y += 18;
		toggle(x, y, w, "Mine in background (tabbed out)", s.backgroundMining,
				"Macros keep running while the game window isn't focused, and the game no longer pauses when you tab out. Shortcut: Ctrl+P in-game.",
				v -> MacroManager.get().setBackgroundMode(v));
		y += 24;
		EditBox cmd = textField(x + 150, y, w - 150, s.craftOpenCommand, 128, "(disabled)",
				"Command that opens a 3x3 crafting screen on your server, e.g. /craft or /workbench. Blank disables it.");
		cmd.setResponder(v -> { s.craftOpenCommand = v.strip(); dirty.run(); });
		y += 22;
		EditBox timeout = numberField(x + 150, y, 60, s.serverTimeoutTicks, "Max ticks to wait for the server (10-400). Raise this on laggy servers.");
		timeout.setResponder(v -> {
			try {
				s.serverTimeoutTicks = Math.max(10, Math.min(400, Integer.parseInt(v.trim())));
				dirty.run();
			} catch (NumberFormatException ignored) {
				// keep previous
			}
		});

		button(panelX + panelW - 88, panelY + panelH - 26, 80, "Done", FlatButton.Style.PRIMARY, this::onClose);
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = panelX + 12;
		int y = panelY + 34 + 18 * 8 + 6;
		label(g, "Crafting open command", x, y + 4);
		label(g, "Server timeout (ticks)", x, y + 26);
	}

	@Override
	public void removed() {
		MacroManager.get().saveNow();
	}
}
