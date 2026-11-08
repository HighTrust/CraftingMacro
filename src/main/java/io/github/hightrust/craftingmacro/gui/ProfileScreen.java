package io.github.hightrust.craftingmacro.gui;

import io.github.hightrust.craftingmacro.MacroManager;
import io.github.hightrust.craftingmacro.config.ConfigValidator;
import io.github.hightrust.craftingmacro.config.DefaultMacros;
import io.github.hightrust.craftingmacro.config.ModConfig;
import io.github.hightrust.craftingmacro.config.Profile;
import io.github.hightrust.craftingmacro.gui.widget.FlatButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Create, rename, delete, export/import and server-link profiles. */
public final class ProfileScreen extends BaseScreen {
	private static final int ROW_H = 18;

	private String selected;
	private EditBox nameBox;
	private String message = "";
	private int messageColor = Theme.TEXT_DIM;
	private int listX;
	private int listY;
	private int listW;
	private int listH;
	private int scroll;

	public ProfileScreen(Screen parent) {
		super("Profiles", parent);
		this.selected = MacroManager.get().activeProfile().name;
	}

	@Override
	protected int preferredWidth() {
		return 440;
	}

	@Override
	protected int preferredHeight() {
		return 290;
	}

	private ModConfig cfg() {
		return MacroManager.get().config();
	}

	@Override
	protected void build() {
		listX = panelX + 8;
		listY = panelY + 32;
		listW = compact() ? panelW / 2 - 12 : 170;
		listH = panelY + panelH - 48 - listY;
		int ax = listX + listW + 10;
		int aw = panelX + panelW - 8 - ax;
		int y = listY;
		int half = (aw - 4) / 2;

		nameBox = textField(ax, y, aw, selected, ConfigValidator.MAX_PROFILE_NAME, "Profile name", "Letters, numbers, spaces, _ - ( )");
		y += 22;
		button(ax, y, half, "Rename", FlatButton.Style.NORMAL, this::rename).tooltip("Rename the selected profile to the name above");
		button(ax + half + 4, y, half, "New", FlatButton.Style.NORMAL, this::create).tooltip("Create an empty profile with the name above");
		y += 22;
		button(ax, y, half, "Activate", FlatButton.Style.PRIMARY, this::activate).tooltip("Use the selected profile's macros");
		button(ax + half + 4, y, half, "Duplicate", FlatButton.Style.NORMAL, this::duplicate).tooltip("Copy the selected profile");
		y += 22;
		button(ax, y, half, "Export", FlatButton.Style.NORMAL, this::export).tooltip("Save to config/craftingmacro/exports/");
		button(ax + half + 4, y, half, "Import...", FlatButton.Style.NORMAL, () -> minecraft.gui.setScreen(new ImportScreen(this)))
				.tooltip("Import a profile from config/craftingmacro/exports/");
		y += 22;
		String server = MacroManager.currentServer();
		boolean linked = selected != null && selected.equalsIgnoreCase(cfg().serverProfiles.get(server));
		button(ax, y, aw, linked ? "Unlink from this server" : "Use on this server", FlatButton.Style.NORMAL, this::toggleServerLink)
				.tooltip("Automatically switch to this profile when joining " + server);
		y += 22;
		button(ax, y, half, "Delete", FlatButton.Style.DANGER, this::delete).enabled(cfg().profiles.size() > 1)
				.tooltip("Delete the selected profile (asks first)");
		button(ax + half + 4, y, half, "Reset all", FlatButton.Style.DANGER, this::resetAll)
				.tooltip("Delete every profile and restore the default macros (asks first)");

		button(panelX + panelW - 88, panelY + panelH - 26, 80, "Done", FlatButton.Style.PRIMARY, this::onClose);
	}

	private Profile sel() {
		return selected == null ? null : cfg().profile(selected);
	}

	private void info(String msg, int color) {
		message = msg;
		messageColor = color;
	}

	private String requestedName() {
		String raw = nameBox.getValue().strip();
		if (!ConfigValidator.isValidProfileName(raw)) {
			info("Invalid name. Use letters, numbers, spaces, _ - ( )", Theme.DANGER);
			return null;
		}
		return raw;
	}

	private void create() {
		String name = requestedName();
		if (name == null) return;
		if (cfg().profile(name) != null) {
			info("A profile with that name already exists", Theme.DANGER);
			return;
		}
		if (cfg().profiles.size() >= ConfigValidator.MAX_PROFILES) {
			info("Profile limit reached", Theme.DANGER);
			return;
		}
		cfg().profiles.add(new Profile(name));
		selected = name;
		MacroManager.get().markDirty();
		info("Created " + name, Theme.SUCCESS);
		rebuildWidgets();
	}

	private void rename() {
		Profile p = sel();
		String name = requestedName();
		if (p == null || name == null) return;
		Profile existing = cfg().profile(name);
		if (existing != null && existing != p) {
			info("A profile with that name already exists", Theme.DANGER);
			return;
		}
		String old = p.name;
		p.name = name;
		if (cfg().activeProfile.equalsIgnoreCase(old)) cfg().activeProfile = name;
		for (Map.Entry<String, String> e : cfg().serverProfiles.entrySet()) {
			if (e.getValue().equalsIgnoreCase(old)) e.setValue(name);
		}
		selected = name;
		MacroManager.get().markDirty();
		info("Renamed to " + name, Theme.SUCCESS);
		rebuildWidgets();
	}

	private void activate() {
		Profile p = sel();
		if (p == null) return;
		MacroManager.get().switchProfile(p.name);
		info("Active profile: " + p.name, Theme.SUCCESS);
	}

	private void duplicate() {
		Profile p = sel();
		if (p == null) return;
		if (cfg().profiles.size() >= ConfigValidator.MAX_PROFILES) {
			info("Profile limit reached", Theme.DANGER);
			return;
		}
		String base = ConfigValidator.cleanProfileName(p.name + " copy");
		String name = base;
		for (int i = 2; cfg().profile(name) != null; i++) name = ConfigValidator.cleanProfileName(base + " " + i);
		cfg().profiles.add(p.copy(name));
		selected = name;
		MacroManager.get().markDirty();
		info("Duplicated as " + name, Theme.SUCCESS);
		rebuildWidgets();
	}

	private void export() {
		Profile p = sel();
		if (p == null) return;
		try {
			Path file = MacroManager.get().store().exportProfile(p);
			info("Exported to exports/" + file.getFileName(), Theme.SUCCESS);
		} catch (IOException e) {
			info("Export failed: " + e.getMessage(), Theme.DANGER);
		}
	}

	private void toggleServerLink() {
		Profile p = sel();
		if (p == null) return;
		String server = MacroManager.currentServer();
		if (p.name.equalsIgnoreCase(cfg().serverProfiles.get(server))) {
			cfg().serverProfiles.remove(server);
			info("Unlinked from " + server, Theme.TEXT_DIM);
		} else {
			cfg().serverProfiles.put(server, p.name);
			info(p.name + " will be used on " + server, Theme.SUCCESS);
		}
		MacroManager.get().markDirty();
		rebuildWidgets();
	}

	private void delete() {
		Profile p = sel();
		if (p == null || cfg().profiles.size() <= 1) return;
		minecraft.gui.setScreen(new ConfirmDialog(this, "Delete profile", "Delete profile \"" + p.name + "\" and its " + p.macros.size()
				+ " macro(s)? Export it first if you want a backup.", "Delete", () -> {
			boolean wasActive = cfg().activeProfile.equalsIgnoreCase(p.name);
			if (wasActive) MacroManager.get().executor().stopAll("Profile deleted");
			cfg().profiles.remove(p);
			cfg().serverProfiles.values().removeIf(v -> v.equalsIgnoreCase(p.name));
			if (wasActive) cfg().activeProfile = cfg().profiles.get(0).name;
			selected = cfg().activeProfile;
			MacroManager.get().markDirty();
			info("Deleted " + p.name, Theme.TEXT_DIM);
		}));
	}

	private void resetAll() {
		minecraft.gui.setScreen(new ConfirmDialog(this, "Reset to defaults", "Delete ALL profiles and macros and restore the default K / L / M examples? Settings are kept.",
				"Reset", () -> {
			MacroManager.get().resetToDefaults();
			selected = DefaultMacros.defaultProfile().name;
			info("Restored defaults", Theme.SUCCESS);
		}));
	}

	/** Called by the import screen. */
	void imported(Profile p) {
		String base = ConfigValidator.cleanProfileName(p.name);
		String name = base;
		for (int i = 2; cfg().profile(name) != null; i++) name = ConfigValidator.cleanProfileName(base + " " + i);
		if (cfg().profiles.size() >= ConfigValidator.MAX_PROFILES) {
			info("Profile limit reached", Theme.DANGER);
			return;
		}
		p.name = name;
		cfg().profiles.add(p);
		selected = name;
		MacroManager.get().markDirty();
		info("Imported " + name + " (" + p.macros.size() + " macros)", Theme.SUCCESS);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && inside(event.x(), event.y(), listX, listY, listW, listH)) {
			int idx = (int) ((event.y() - listY) / ROW_H) + scroll;
			List<Profile> profiles = cfg().profiles;
			if (idx >= 0 && idx < profiles.size()) {
				selected = profiles.get(idx).name;
				if (doubleClick) activate();
				rebuildWidgets();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (inside(mx, my, listX, listY, listW, listH)) {
			int max = Math.max(0, cfg().profiles.size() - listH / ROW_H);
			scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(sy)));
			return true;
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Theme.rounded(g, listX, listY, listW, listH, 0xFF12151C);
		List<Profile> profiles = cfg().profiles;
		String server = MacroManager.currentServer();
		int rows = listH / ROW_H;
		for (int i = scroll; i < Math.min(profiles.size(), scroll + rows); i++) {
			Profile p = profiles.get(i);
			int y = listY + (i - scroll) * ROW_H;
			boolean sel = p.name.equalsIgnoreCase(selected);
			boolean hov = inside(mouseX, mouseY, listX, y, listW, ROW_H);
			if (sel || hov) Theme.rounded(g, listX + 2, y + 1, listW - 4, ROW_H - 2, sel ? Theme.SURFACE_SELECTED : Theme.SURFACE_HOVER);
			boolean active = p.name.equalsIgnoreCase(cfg().activeProfile);
			if (active) Theme.rounded(g, listX + 6, y + 6, 6, 6, Theme.SUCCESS);
			String suffix = p.name.equalsIgnoreCase(cfg().serverProfiles.get(server)) ? " [server]" : "";
			g.text(font, ellipsize(p.name + suffix, listW - 50), listX + 16, y + 5, Theme.TEXT, false);
			String count = String.valueOf(p.macros.size());
			g.text(font, count, listX + listW - 8 - font.width(count), y + 5, Theme.TEXT_FAINT, false);
		}
		g.text(font, ellipsize(message, panelW - 110), panelX + 10, panelY + panelH - 21, messageColor, false);
	}

	@Override
	public void removed() {
		MacroManager.get().saveNow();
	}

	@Override
	public void onClose() {
		// Rebuild the list screen so the profile button label and macros refresh.
		minecraft.gui.setScreen(parent instanceof MacroListScreen ? new MacroListScreen(null) : parent);
	}
}
