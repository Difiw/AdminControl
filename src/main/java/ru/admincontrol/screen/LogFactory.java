package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.util.ActivityLog;

import java.util.ArrayList;
import java.util.List;

public final class LogFactory implements ExtendedScreenHandlerFactory {
	private final String title;
	private final List<ActivityLog.Line> lines;
	private final boolean showMarks;
	private final boolean filters;
	private final String query;
	private boolean tracked;

	public LogFactory(String title, List<ActivityLog.Line> lines) {
		this(title, lines, true, false, "");
	}

	public LogFactory(String title, List<ActivityLog.Line> lines, boolean showMarks) {
		this(title, lines, showMarks, false, "");
	}

	public LogFactory(String title, List<ActivityLog.Line> lines, boolean showMarks, boolean filters) {
		this(title, lines, showMarks, filters, "");
	}

	public LogFactory(String title, List<ActivityLog.Line> lines, boolean showMarks, boolean filters, String query) {
		this.title = title == null ? "Логи" : title;
		this.lines = lines == null ? List.of() : lines;
		this.showMarks = showMarks;
		this.filters = filters;
		this.query = query == null ? "" : query;
	}

	public static LogFactory ofText(String title, List<String> text) {
		List<ActivityLog.Line> parsed = new ArrayList<>();
		if (text != null) {
			for (String line : text) {
				parsed.add(new ActivityLog.Line(line, true));
			}
		}
		return new LogFactory(title, parsed, false);
	}

	/** Время просмотра попадёт в /admstats как проверка логов. */
	public LogFactory tracked() {
		this.tracked = true;
		return this;
	}

	@Override
	public Text getDisplayName() {
		return Text.literal(title);
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity viewer) {
		LogScreenHandler handler = new LogScreenHandler(syncId, title, lines, showMarks, filters, query);
		if (tracked && viewer instanceof ServerPlayerEntity player) {
			handler.tracked = true;
			ru.admincontrol.util.AdminStats.logOpened(player, title);
		}
		return handler;
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
		buf.writeString(title, 200);
		buf.writeBoolean(showMarks);
		buf.writeBoolean(filters);
		String q = query.length() > 120 ? query.substring(0, 120) : query;
		buf.writeString(q, 120);
		int from = Math.max(0, lines.size() - 2500);
		buf.writeVarInt(lines.size() - from);
		for (int i = from; i < lines.size(); i++) {
			ActivityLog.Line line = lines.get(i);
			String text = line == null || line.text == null ? "" : line.text;
			if (text.length() > 400) {
				text = text.substring(0, 400);
			}
			buf.writeString(text, 400);
			buf.writeBoolean(line == null || line.ok);
			buf.writeVarInt(line == null || line.kind == null ? -1 : line.kind.ordinal());
		}
	}
}
