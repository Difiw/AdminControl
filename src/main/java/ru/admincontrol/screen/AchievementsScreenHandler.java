package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.util.Achievements;
import ru.admincontrol.util.Chat;

import java.util.ArrayList;
import java.util.List;

public class AchievementsScreenHandler extends ScreenHandler {
	public final String nick;
	public final boolean curator;
	public final List<String> ids = new ArrayList<>();
	public final List<String> titles = new ArrayList<>();
	public final List<Boolean> done = new ArrayList<>();

	public static AchievementsScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		AchievementsScreenHandler h = new AchievementsScreenHandler(syncId, buf.readString(16), buf.readBoolean());
		int n = buf.readVarInt();
		for (int i = 0; i < n; i++) {
			h.ids.add(buf.readString(128));
			h.titles.add(buf.readString(80));
			h.done.add(buf.readBoolean());
		}
		return h;
	}

	public AchievementsScreenHandler(int syncId, String nick, boolean curator) {
		super(ModScreenHandlers.ACHIEVEMENTS, syncId);
		this.nick = nick;
		this.curator = curator;
	}

	public static void write(PacketByteBuf buf, ServerPlayerEntity viewer, String nick, java.util.UUID uuid) {
		buf.writeString(nick == null ? "" : nick, 16);
		boolean curator = DataManager.get() != null && AdminRanks.curatorOrBlogger(DataManager.get().getAdminLevel(viewer));
		buf.writeBoolean(curator);
		List<Row> rows = Achievements.rows(viewer.getServer(), uuid);
		buf.writeVarInt(rows.size());
		for (Row row : rows) {
			buf.writeString(row.id, 128);
			buf.writeString(row.title, 80);
			buf.writeBoolean(row.done);
		}
	}

	public static void write(PacketByteBuf buf, ServerPlayerEntity viewer, ServerPlayerEntity target) {
		write(buf, viewer, target.getGameProfile().getName(), target.getUuid());
	}

	public static List<Row> rows(ServerPlayerEntity target) {
		if (target == null || target.getServer() == null) {
			return List.of();
		}
		return Achievements.rows(target.getServer(), target.getUuid());
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!(player instanceof ServerPlayerEntity admin) || !this.curator) {
			return false;
		}
		if (id < 0 || id >= ids.size()) {
			return false;
		}
		Chat.send(admin, Chat.yellow("Используйте кнопки Выдать/Забрать."));
		return true;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return true;
	}

	public static final class Row {
		public final String id;
		public final String title;
		public final boolean done;

		public Row(String id, String title, boolean done) {
			this.id = id;
			this.title = title;
			this.done = done;
		}
	}
}
