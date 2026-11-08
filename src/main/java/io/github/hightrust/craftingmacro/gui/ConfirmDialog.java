package io.github.hightrust.craftingmacro.gui;

import io.github.hightrust.craftingmacro.gui.widget.FlatButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Small yes/no confirmation dialog. Returns to the parent screen either way. */
public final class ConfirmDialog extends BaseScreen {
	private final String message;
	private final String confirmLabel;
	private final Runnable onConfirm;

	public ConfirmDialog(Screen parent, String title, String message, String confirmLabel, Runnable onConfirm) {
		super(title, parent);
		this.message = message;
		this.confirmLabel = confirmLabel;
		this.onConfirm = onConfirm;
	}

	@Override
	protected int preferredWidth() {
		return 280;
	}

	@Override
	protected int preferredHeight() {
		return 120;
	}

	@Override
	protected void build() {
		int bw = (panelW - 30) / 2;
		int by = panelY + panelH - 28;
		button(panelX + 10, by, bw, "Cancel", FlatButton.Style.NORMAL, this::onClose);
		button(panelX + 20 + bw, by, bw, confirmLabel, FlatButton.Style.DANGER, () -> {
			onClose();
			onConfirm.run();
		});
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.textWithWordWrap(font, Component.literal(message), panelX + 10, panelY + 34, panelW - 20, Theme.TEXT, false);
	}
}
