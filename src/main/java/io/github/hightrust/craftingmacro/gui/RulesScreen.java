package io.github.hightrust.craftingmacro.gui;

import io.github.hightrust.craftingmacro.gui.widget.FlatButton;
import io.github.hightrust.craftingmacro.macro.Macro;
import io.github.hightrust.craftingmacro.macro.MacroRule;
import io.github.hightrust.craftingmacro.macro.MacroValidator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

/**
 * Edits a macro's automation rules: "when [condition] [amount], run [command], wait [pause],
 * then [continue / restart / stop]". Edits the editor's working copy directly.
 */
public final class RulesScreen extends BaseScreen {
	private static final int CARD_H = 44;
	private static final int GAP = 6;

	private final Macro m;

	public RulesScreen(Screen parent, Macro working) {
		super("Automation Rules", parent);
		this.m = working;
	}

	@Override
	protected int preferredWidth() {
		return 400;
	}

	@Override
	protected int preferredHeight() {
		return 60 + MacroValidator.MAX_RULES * (CARD_H + GAP) + 30;
	}

	@Override
	protected void build() {
		int x = panelX + 10;
		int w = panelW - 20;
		int y = panelY + 46;
		for (int i = 0; i < m.rules.size(); i++) {
			MacroRule r = m.rules.get(i);
			int ry = y + i * (CARD_H + GAP);
			int cx = x + 30;
			int condW = 132;
			button(cx, ry + 5, condW, r.condition.label(), FlatButton.Style.NORMAL, () -> {
				r.condition = r.condition.next();
				r.amount = r.condition.defaultAmount();
				rebuildWidgets();
			}).tooltip(r.condition.description() + " (click to change)");
			cx += condW + 4;
			if (r.condition.hasAmount()) {
				EditBox amount = numberField(cx, ry + 6, 50, r.amount, r.condition.unit());
				amount.setResponder(s -> r.amount = parse(s, r.amount));
			}
			button(x + w - 22, ry + 5, 18, "", FlatButton.Style.GHOST, () -> {
				m.rules.remove(r);
				rebuildWidgets();
			}).tooltip("Delete rule");
			button(x + w - 44, ry + 5, 18, "", FlatButton.Style.GHOST, () -> {
				r.enabled = !r.enabled;
				rebuildWidgets();
			}).tooltip(r.enabled ? "Rule on (click to turn off)" : "Rule off (click to turn on)");

			int lx = x + 30;
			EditBox cmd = textField(lx, ry + 25, 132, r.command, MacroValidator.MAX_TEXT, "Command e.g. /fix", "Runs when the rule fires (leave empty for none)");
			cmd.setResponder(s -> r.command = s);
			lx += 136;
			if (r.then != MacroRule.Then.STOP) {
				EditBox pause = numberField(lx, ry + 25, 50, r.pauseMs / 1000, "Seconds to wait before continuing");
				pause.setResponder(s -> r.pauseMs = Math.max(0, parse(s, r.pauseMs / 1000)) * 1000);
			}
			button(x + w - 100, ry + 24, 96, r.then.label(), r.then == MacroRule.Then.STOP ? FlatButton.Style.DANGER : FlatButton.Style.NORMAL, () -> {
				r.then = r.then.next();
				rebuildWidgets();
			}).tooltip("What the macro does after the command (click to change)");
		}

		int addY = y + m.rules.size() * (CARD_H + GAP);
		if (m.rules.size() < MacroValidator.MAX_RULES) {
			button(x, addY, 120, "+ Add rule", FlatButton.Style.GHOST, () -> {
				m.rules.add(new MacroRule(MacroRule.Condition.DURABILITY, 20, "/fix", 1000, MacroRule.Then.RESUME));
				rebuildWidgets();
			});
			button(x + 124, addY, 120, "+ Timer rule", FlatButton.Style.GHOST, () -> {
				m.rules.add(new MacroRule(MacroRule.Condition.EVERY, 300, "/fix", 1000, MacroRule.Then.RESUME));
				rebuildWidgets();
			});
		}
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
		int w = panelW - 20;
		g.text(font, "Checked while the macro runs. The current step pauses, the command runs,", x, panelY + 30, Theme.TEXT_FAINT, false);
		int y = panelY + 46;
		if (m.rules.isEmpty()) {
			g.text(font, "No rules yet. Example: durability at or below 20 -> /fix -> continue.", x, y + 26, Theme.TEXT_DIM, false);
		}
		for (int i = 0; i < m.rules.size(); i++) {
			MacroRule r = m.rules.get(i);
			int ry = y + i * (CARD_H + GAP);
			Theme.card(g, x, ry, w, CARD_H, Theme.SURFACE, Theme.withAlpha(r.enabled ? Theme.ACCENT : Theme.PANEL_BORDER, r.enabled ? 0.45f : 0.8f));
			Theme.accentBar(g, x + 1, ry + 6, CARD_H - 12, r.enabled ? 1f : 0.2f);
			int icon = switch (r.condition) {
				case EVERY -> Fonts.Icon.TIMER;
				case DURABILITY -> Fonts.Icon.WRENCH;
				case INVENTORY_FULL -> Fonts.Icon.PACKAGE;
				case ITEMS_ON_GROUND -> Fonts.Icon.PICKAXE;
			};
			Theme.rounded(g, x + 7, ry + 6, 17, 17, Theme.withAlpha(Theme.ACCENT, r.enabled ? 0.22f : 0.08f));
			g.text(font, Fonts.icon(icon), x + 11, ry + 10, r.enabled ? Theme.ACCENT_HOVER : Theme.TEXT_FAINT, false);
			if (r.condition.hasAmount()) {
				g.text(font, r.condition.unit(), x + 30 + 132 + 4 + 54, ry + 10, Theme.TEXT_DIM, false);
			}
			g.text(font, Fonts.icon(Fonts.Icon.TERMINAL), x + 13, ry + 29, Theme.TEXT_FAINT, false);
			if (r.then != MacroRule.Then.STOP) {
				g.text(font, "sec", x + 30 + 136 + 54, ry + 29, Theme.TEXT_DIM, false);
			}
		}
	}

	@Override
	protected void extractOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = panelX + 10;
		int w = panelW - 20;
		int y = panelY + 46;
		for (int i = 0; i < m.rules.size(); i++) {
			MacroRule r = m.rules.get(i);
			int ry = y + i * (CARD_H + GAP);
			g.centeredText(font, Fonts.icon(Fonts.Icon.TRASH), x + w - 13, ry + 9, Theme.DANGER);
			g.centeredText(font, Fonts.icon(r.enabled ? Fonts.Icon.PAUSE : Fonts.Icon.PLAY), x + w - 35, ry + 9, r.enabled ? Theme.TEXT_DIM : Theme.SUCCESS);
		}
	}
}
