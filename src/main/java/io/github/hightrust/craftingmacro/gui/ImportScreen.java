package io.github.hightrust.craftingmacro.gui;

import io.github.hightrust.craftingmacro.MacroManager;
import io.github.hightrust.craftingmacro.config.Profile;
import io.github.hightrust.craftingmacro.gui.widget.FlatButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.List;

/** Lists profile exports in config/craftingmacro/exports and imports the chosen one after validation. */
public final class ImportScreen extends BaseScreen {
	private final ProfileScreen profiles;
	private List<String> files = List.of();
	private int page;
	private String error = "";

	public ImportScreen(ProfileScreen parent) {
		super("Import Profile", parent);
		this.profiles = parent;
	}

	@Override
	protected int preferredWidth() {
		return 340;
	}

	@Override
	protected int preferredHeight() {
		return 260;
	}

	private int perPage() {
		return Math.max(1, (panelH - 100) / 20);
	}

	@Override
	protected void build() {
		files = MacroManager.get().store().listExports();
		int pages = Math.max(1, (files.size() + perPage() - 1) / perPage());
		page = Math.max(0, Math.min(page, pages - 1));
		int y = panelY + 52;
		for (int i = page * perPage(); i < Math.min(files.size(), (page + 1) * perPage()); i++) {
			String file = files.get(i);
			button(panelX + 10, y, panelW - 20, file, FlatButton.Style.NORMAL, () -> importFile(file)).tooltip("Import " + file);
			y += 20;
		}
		int by = panelY + panelH - 26;
		if (pages > 1) {
			button(panelX + 10, by, 24, "<", FlatButton.Style.GHOST, () -> {
				page--;
				rebuildWidgets();
			}).enabled(page > 0);
			button(panelX + 38, by, 24, ">", FlatButton.Style.GHOST, () -> {
				page++;
				rebuildWidgets();
			}).enabled(page < pages - 1);
		}
		button(panelX + panelW - 88, by, 80, "Cancel", FlatButton.Style.NORMAL, this::onClose);
	}

	private void importFile(String file) {
		try {
			Profile p = MacroManager.get().store().importProfile(file);
			onClose();
			profiles.imported(p);
		} catch (IOException | IllegalArgumentException e) {
			error = "Import failed: " + e.getMessage();
		}
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.textWithWordWrap(font, Component.literal("Files in config/craftingmacro/exports:"), panelX + 10, panelY + 34, panelW - 20, Theme.TEXT_DIM, false);
		if (files.isEmpty()) {
			g.textWithWordWrap(font, Component.literal("No exports found. Export a profile first, or place a .json export file in that folder."),
					panelX + 10, panelY + 60, panelW - 20, Theme.TEXT_FAINT, false);
		}
		if (!error.isEmpty()) g.text(font, ellipsize(error, panelW - 110), panelX + 70, panelY + panelH - 21, Theme.DANGER, false);
	}
}
