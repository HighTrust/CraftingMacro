package io.github.hightrust.craftingmacro.gui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Text box that only accepts digits (typed or pasted). */
public class NumberBox extends EditBox {
	public NumberBox(Font font, int x, int y, int width, int height, Component narration) {
		super(font, x, y, width, height, narration);
	}

	@Override
	public void insertText(String input) {
		StringBuilder digits = new StringBuilder();
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (c >= '0' && c <= '9') digits.append(c);
		}
		if (!digits.isEmpty()) super.insertText(digits.toString());
	}
}
