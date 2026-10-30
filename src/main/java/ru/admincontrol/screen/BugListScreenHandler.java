package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.BugReport;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.util.BugList;

import java.util.ArrayList;
import java.util.List;

/** /buglist: список багов и ошибок; данные приходят целиком при открытии. */
public class BugListScreenHandler extends ScreenHandler {
	public static final int LIMIT = 300;
	public static final int OP_TELEPORT = 0;
	public static final int OP_DELETE = 1;
	public static final int OP_CLEAR = 2;

	public record Entry(long id, String time, String last, int count, String type, String nick, String where,
			boolean hasPos, String description) {
	}

	private final List<Entry> entries = new ArrayList<>();
	private boolean canDelete;
	private int total;

	public static BugListScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		BugListScreenHandler h = new BugListScreenHandler(syncId);
		h.canDelete = buf.readBoolean();
		h.total = buf.readVarInt();
		int n = Math.min(LIMIT, buf.readVarInt());
		for (int i = 0; i < n; i++) {
			h.entries.add(new Entry(buf.readLong(), buf.readString(32), buf.readString(32), buf.readVarInt(),
					buf.readString(128), buf.readString(64), buf.readString(160), buf.readBoolean(), buf.readString(4096)));
		}
		return h;
	}

	public static void write(PacketByteBuf buf, ServerPlayerEntity viewer) {
		List<BugReport> list = BugList.list();
		buf.writeBoolean(canDelete(viewer));
		buf.writeVarInt(list.size());
		int n = Math.min(LIMIT, list.size());
		buf.writeVarInt(n);
		for (int i = 0; i < n; i++) {
			BugReport r = list.get(i);
			buf.writeLong(r.id);
			buf.writeString(BugList.time(r.at), 32);
			buf.writeString(r.count > 1 ? BugList.time(r.lastAt) : "", 32);
			buf.writeVarInt(Math.max(1, r.count));
			buf.writeString(cut(r.type, 128), 128);
			buf.writeString(cut(r.nick, 64), 64);
			buf.writeString(r.hasPos ? cut(r.dimension + "  " + r.x + " " + r.y + " " + r.z, 160) : "", 160);
			buf.writeBoolean(r.hasPos);
			buf.writeString(cut(r.description, 1000), 4096);
		}
	}

	/** Удалять записи и чистить список может куратор администрации и выше. */
	public static boolean canDelete(ServerPlayerEntity player) {
		return player != null && DataManager.get() != null && DataManager.get().getAdminLevel(player) >= AdminRanks.CURATOR;
	}

	private static String cut(String s, int max) {
		if (s == null) {
			return "";
		}
		return s.length() > max ? s.substring(0, max) : s;
	}

	public BugListScreenHandler(int syncId) {
		super(ModScreenHandlers.BUG_LIST, syncId);
	}

	public List<Entry> entries() {
		return this.entries;
	}

	public boolean canDelete() {
		return this.canDelete;
	}

	public int total() {
		return this.total;
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
