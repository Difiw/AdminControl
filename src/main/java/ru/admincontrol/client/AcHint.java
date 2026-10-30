package ru.admincontrol.client;

import net.minecraft.client.gui.widget.TextFieldWidget;

import java.util.function.Consumer;

/** Подсказка в поле: видна, пока текст пустой, и сразу пропадает при вводе. */
public final class AcHint {
	private AcHint() {
	}

	public static void apply(TextFieldWidget field, String hint) {
		apply(field, hint, null);
	}

	public static void apply(TextFieldWidget field, String hint, Consumer<String> extra) {
		if (field == null) {
			return;
		}
		String text = hint == null || hint.isBlank() ? null : hint;
		field.setChangedListener(value -> {
			field.setSuggestion(value == null || value.isEmpty() ? text : null);
			if (extra != null) {
				extra.accept(value);
			}
		});
		field.setSuggestion(field.getText().isEmpty() ? text : null);
	}

	/** Каждый кадр: подсказка только в пустом поле (ваниль рисует suggestion после текста). */
	public static void tick(TextFieldWidget field, String hint) {
		if (field == null) {
			return;
		}
		field.setSuggestion(field.getText().isEmpty() ? hint : null);
	}
}
