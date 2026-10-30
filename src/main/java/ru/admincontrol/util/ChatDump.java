package ru.admincontrol.util;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.List;

public final class ChatDump {
	private ChatDump() {
	}

	public static int lines(ServerCommandSource source, List<String> lines) {
		if (source == null) {
			return 0;
		}
		if (lines == null || lines.isEmpty()) {
			source.sendFeedback(() -> Chat.gray("(пусто)"), false);
			return 1;
		}
		for (String line : lines) {
			source.sendFeedback(() -> Chat.gray(line == null ? "" : line), false);
		}
		return 1;
	}

	public static int logLines(ServerCommandSource source, List<ActivityLog.Line> lines) {
		if (source == null) {
			return 0;
		}
		if (lines == null || lines.isEmpty()) {
			source.sendFeedback(() -> Chat.gray("(пусто)"), false);
			source.sendFeedback(() -> Text.literal("ACLOG empty"), false);
			return 1;
		}
		int n = lines.size();
		int from = Math.max(0, n - 500);
		source.sendFeedback(() -> Text.literal("ACLOG begin " + n), false);
		if (from > 0) {
			source.sendFeedback(() -> Chat.gray("Всего записей: " + n + ". Показаны последние 500."), false);
		}
		for (int i = from; i < n; i++) {
			ActivityLog.Line line = lines.get(i);
			String text = line == null || line.text == null ? "" : line.text.replace('\n', ' ').replace('\r', ' ');
			String kind = line == null || line.kind == null ? "OTHER" : line.kind.name();
			String ok = line != null && !line.ok ? "0" : "1";
			source.sendFeedback(() -> Text.literal("ACLOG kind=" + kind + " ok=" + ok + " " + text), false);
		}
		source.sendFeedback(() -> Text.literal("ACLOG end"), false);
		return 1;
	}

	public static List<String> zip(List<String> labels, List<String> values) {
		java.util.ArrayList<String> out = new java.util.ArrayList<>();
		int n = Math.max(labels == null ? 0 : labels.size(), values == null ? 0 : values.size());
		for (int i = 0; i < n; i++) {
			String label = labels != null && i < labels.size() ? labels.get(i) : "";
			String value = values != null && i < values.size() ? values.get(i) : "";
			out.add(label + ": " + value);
		}
		return out;
	}
}
