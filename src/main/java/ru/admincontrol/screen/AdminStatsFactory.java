package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.util.AdminStats;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class AdminStatsFactory implements ExtendedScreenHandlerFactory {
	private final String nick;
	private final int page;

	public AdminStatsFactory(String nick, int page) {
		this.nick = nick == null ? "" : nick;
		this.page = Math.max(0, page);
	}

	/** Свою статистику видит любой админ, чужую — по /admstats и не старше себя. */
	public static boolean allowed(ServerCommandSource source, String nick) {
		if (source == null || nick == null) {
			return false;
		}
		if (nick.equalsIgnoreCase(CommandUtil.adminName(source))) {
			return CommandUtil.canUse(source, "astats");
		}
		if (!CommandUtil.canUse(source, "admstats")) {
			return false;
		}
		DataManager data = DataManager.get();
		if (data == null) {
			return false;
		}
		PlayerRecord record = data.findByNick(nick).orElse(null);
		return data.getAdminLevel(record == null ? null : record.uuidOrNull(), nick) <= CommandUtil.sourceRank(source);
	}

	private int pages(List<LocalDate> days) {
		return Math.max(1, (days.size() + AdminStatsScreenHandler.PAGE - 1) / AdminStatsScreenHandler.PAGE);
	}

	private List<String> labels(List<LocalDate> days) {
		List<String> out = new ArrayList<>();
		int from = Math.min(this.page, pages(days) - 1) * AdminStatsScreenHandler.PAGE;
		for (int i = from; i < days.size() && i < from + AdminStatsScreenHandler.PAGE; i++) {
			out.add(AdminStats.dayLabel(this.nick, days.get(i)));
		}
		return out;
	}

	private boolean onlineNow(ServerPlayerEntity viewer) {
		if (viewer == null || viewer.getServer() == null) {
			return false;
		}
		return viewer.getServer().getPlayerManager().getPlayer(this.nick) != null;
	}

	private String lastJoinText() {
		return AdminStats.lastJoinText(this.nick);
	}

	private String lastLeaveText(ServerPlayerEntity viewer) {
		return onlineNow(viewer) ? "сейчас онлайн" : AdminStats.lastLeaveText(this.nick);
	}

	public static List<String> sessionLines(String nick) {
		return AdminStats.sessionLines(nick);
	}

	@Override
	public Text getDisplayName() {
		return Text.literal("Статистика " + this.nick);
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity viewer) {
		ServerPlayerEntity serverViewer = viewer instanceof ServerPlayerEntity sp ? sp : null;
		List<LocalDate> days = AdminStats.days(this.nick);
		int pages = pages(days);
		return new AdminStatsScreenHandler(syncId, this.nick, Math.min(this.page, pages - 1), pages,
				lastJoinText(), lastLeaveText(serverViewer), onlineNow(serverViewer), labels(days));
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
		List<LocalDate> days = AdminStats.days(this.nick);
		int pages = pages(days);
		List<String> labels = labels(days);
		buf.writeString(this.nick, 32);
		buf.writeVarInt(Math.min(this.page, pages - 1));
		buf.writeVarInt(pages);
		buf.writeString(lastJoinText(), 48);
		buf.writeString(lastLeaveText(opener), 48);
		buf.writeBoolean(onlineNow(opener));
		buf.writeVarInt(labels.size());
		for (String label : labels) {
			buf.writeString(label.length() > 120 ? label.substring(0, 120) : label, 120);
		}
	}
}
