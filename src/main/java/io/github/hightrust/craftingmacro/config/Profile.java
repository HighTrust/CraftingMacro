package io.github.hightrust.craftingmacro.config;

import io.github.hightrust.craftingmacro.macro.Macro;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A named set of macros. */
public final class Profile {
	public String name = "Default";
	public List<Macro> macros = new ArrayList<>();

	public Profile() {
	}

	public Profile(String name) {
		this.name = name;
	}

	public Profile copy(String newName) {
		Profile p = new Profile(newName);
		for (Macro m : macros) p.macros.add(m.copy(true));
		return p;
	}

	public Macro find(String id) {
		for (Macro m : macros) {
			if (Objects.equals(m.id, id)) return m;
		}
		return null;
	}
}
