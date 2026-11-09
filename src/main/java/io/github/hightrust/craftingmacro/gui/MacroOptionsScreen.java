package io.github.hightrust.craftingmacro.gui;

import io.github.hightrust.craftingmacro.MacroManager;
import io.github.hightrust.craftingmacro.gui.widget.FlatButton;
import io.github.hightrust.craftingmacro.macro.Macro;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/** Advanced per-macro options. Edits the editor's working copy directly. */
public final class MacroOptionsScreen extends BaseScreen {
	private final Macro m;

	public MacroOptionsScreen(Screen parent, Macro working) {
		super("Advanced", parent);
		this.m = working;
	}

	@Override
	protected int preferredWidth() {
		return 300;
	}

	@Override
	protected int preferredHeight() {
		return 274;
	}

	@Override
	protected void build() {
		int x = panelX + 10;
		int w = panelW - 20;
		int fx = x + w - 70;
		int y = panelY + 30;

		numberField(fx, y, 70, m.cooldownMs, "Minimum time between activations").setResponder(s -> m.cooldownMs = parse(s, 0));
		y += 20;
		numberField(fx, y, 70, m.startDelayMs, "Wait before the first step").setResponder(s -> m.startDelayMs = parse(s, 0));
		y += 20;
		numberField(fx, y, 70, m.repeatCount, "Run all steps this many times per press").setResponder(s -> m.repeatCount = parse(s, 1));
		y += 24;
		toggle(x, y, w, "Enabled", m.enabled, null, v -> m.enabled = v);
		y += 17;
		toggle(x, y, w, "Show notifications", m.notify, "Errors are always shown", v -> m.notify = v);
		y += 17;
		toggle(x, y, w, "Close crafting screen when done", m.autoClose, null, v -> m.autoClose = v);
		y += 17;
		toggle(x, y, w, "Stop on error", m.stopOnError, null, v -> m.stopOnError = v);
		y += 22;
		EditBox servers = textField(x, y, w - 22, String.join(", ", m.servers), 512, "Only on servers... (empty = all)",
				"Comma-separated server addresses. 'singleplayer' = local worlds.");
		servers.setResponder(s -> {
			List<String> out = new ArrayList<>();
			for (String p : s.split(",")) if (!p.isBlank()) out.add(p.trim());
			m.servers = out;
		});
		button(x + w - 18, y, 18, "+", FlatButton.Style.NORMAL, () -> {
			String cur = MacroManager.currentServer();
			if (!m.servers.contains(cur)) m.servers.add(cur);
			rebuildWidgets();
		}).tooltip("Add this server (" + MacroManager.currentServer() + ")");

		y += 24;
		int n = m.rules == null ? 0 : m.rules.size();
		button(x, y, w, "Automation rules" + (n > 0 ? " (" + n + ")" : "") + "...", FlatButton.Style.NORMAL,
				() -> minecraft.gui.setScreen(new RulesScreen(this, m)))
				.tooltip("Run /fix when durability is low, restart when the inventory is full, pause every X minutes...");

		button(panelX + panelW - 70, panelY + panelH - 26, 60, "Done", FlatButton.Style.PRIMARY, this::onClose);
	}

	private static int parse(String s, int fallback) {
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = panelX + 10;
		int y = panelY + 30;
		label(g, "Cooldown (ms)", x, y + 4);
		label(g, "Start delay (ms)", x, y + 24);
		label(g, "Repeat count", x, y + 44);
	}
}
