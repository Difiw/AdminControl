package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import ru.admincontrol.data.ActionKind;
import ru.admincontrol.util.ActivityLog;

import java.util.ArrayList;
import java.util.List;

public class LogScreenHandler extends ScreenHandler {
	public final String title;
	public final List<ActivityLog.Line> lines;
	public final boolean showMarks;
	public final boolean filters;
	public final String initialQuery;
	public boolean tracked;

	public static LogScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		String heading = buf.readString(200);
		boolean marks = buf.readBoolean();
		boolean filter = buf.readBoolean();
		String query = buf.readString(120);
		int n = buf.readVarInt();
		List<ActivityLog.Line> parsed = new ArrayList<>(Math.max(0, n));
		for (int i = 0; i < n; i++) {
			String text = buf.readString(400);
			boolean ok = buf.readBoolean();
			ActionKind kind = ActionKind.byOrdinal(buf.readVarInt());
			parsed.add(new ActivityLog.Line(text, ok, kind));
		}
		return new LogScreenHandler(syncId, heading, parsed, marks, filter, query);
	}

	public LogScreenHandler(int syncId, String title, List<ActivityLog.Line> lines) {
		this(syncId, title, lines, true, false, "");
	}

	public LogScreenHandler(int syncId, String title, List<ActivityLog.Line> lines, boolean showMarks) {
		this(syncId, title, lines, showMarks, false, "");
	}

	public LogScreenHandler(int syncId, String title, List<ActivityLog.Line> lines, boolean showMarks, boolean filters) {
		this(syncId, title, lines, showMarks, filters, "");
	}

	public LogScreenHandler(int syncId, String title, List<ActivityLog.Line> lines, boolean showMarks, boolean filters, String query) {
		super(ModScreenHandlers.LOG, syncId);
		this.title = title == null ? "Логи" : title;
		this.lines = lines == null ? new ArrayList<>() : new ArrayList<>(lines);
		this.showMarks = showMarks;
		this.filters = filters;
		this.initialQuery = query == null ? "" : query;
	}

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		if (tracked && player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
			ru.admincontrol.util.AdminStats.logClosed(serverPlayer);
		}
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return true;
	}
}
