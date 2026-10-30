package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.util.Achievements;

import java.util.UUID;

public final class AchievementsFactory implements ExtendedScreenHandlerFactory {
	private final String nick;
	private final UUID uuid;

	public AchievementsFactory(ServerPlayerEntity target) {
		this(target.getGameProfile().getName(), target.getUuid());
	}

	public AchievementsFactory(String nick, UUID uuid) {
		this.nick = nick;
		this.uuid = uuid;
	}

	@Override
	public Text getDisplayName() {
		return Text.literal("Достижения");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity viewer) {
		boolean curator = viewer instanceof ServerPlayerEntity p
				&& DataManager.get() != null
				&& AdminRanks.curatorOrBlogger(DataManager.get().getAdminLevel(p));
		AchievementsScreenHandler handler = new AchievementsScreenHandler(syncId, this.nick, curator);
		MinecraftServer server = viewer instanceof ServerPlayerEntity p ? p.getServer() : null;
		for (AchievementsScreenHandler.Row row : Achievements.rows(server, this.uuid)) {
			handler.ids.add(row.id);
			handler.titles.add(row.title);
			handler.done.add(row.done);
		}
		return handler;
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
		AchievementsScreenHandler.write(buf, opener, this.nick, this.uuid);
	}
}
