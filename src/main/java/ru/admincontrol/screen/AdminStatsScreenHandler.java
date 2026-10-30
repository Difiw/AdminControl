package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.util.AdminStats;
import ru.admincontrol.util.ScreenNav;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AdminStatsScreenHandler extends ScreenHandler {
	public static final int PAGE = 100;
	public static final int OLDER = 120;
	public static final int NEWER = 121;
	public static final int SESSIONS = 122;
	private final String nick;
	private final int page;
	private final int pages;
	private final String lastJoin;
	private final String lastLeave;
	private final boolean online;
	private final List<String> labels = new ArrayList<>();

	public static AdminStatsScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		String nick = buf.readString(32);
		int page = buf.readVarInt();
		int pages = buf.readVarInt();
		String lastJoin = buf.readString(48);
		String lastLeave = buf.readString(48);
		boolean online = buf.readBoolean();
		int n = buf.readVarInt();
		List<String> labels = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			labels.add(buf.readString(120));
		}
		return new AdminStatsScreenHandler(syncId, nick, page, pages, lastJoin, lastLeave, online, labels);
	}

	public AdminStatsScreenHandler(int syncId, String nick, int page, int pages, String lastJoin, String lastLeave,
			boolean online, List<String> labels) {
		super(ModScreenHandlers.ADMIN_STATS, syncId);
		this.nick = nick == null ? "" : nick;
		this.page = page;
		this.pages = Math.max(1, pages);
		this.lastJoin = lastJoin == null || lastJoin.isBlank() ? "неизвестно" : lastJoin;
		this.lastLeave = lastLeave == null || lastLeave.isBlank() ? "неизвестно" : lastLeave;
		this.online = online;
		if (labels != null) {
			this.labels.addAll(labels);
		}
	}

	public String nick() {
		return this.nick;
	}

	public int page() {
		return this.page;
	}

	public int pages() {
		return this.pages;
	}

	public String lastJoin() {
		return this.lastJoin;
	}

	public String lastLeave() {
		return this.lastLeave;
	}

	public boolean online() {
		return this.online;
	}

	public List<String> labels() {
		return this.labels;
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!(player instanceof ServerPlayerEntity viewer) || !AdminStatsFactory.allowed(viewer.getCommandSource(), this.nick)) {
			return false;
		}
		if (id == OLDER || id == NEWER) {
			int next = Math.max(0, Math.min(this.pages - 1, this.page + (id == OLDER ? 1 : -1)));
			ScreenNav.replace(viewer, new AdminStatsFactory(this.nick, next));
			return true;
		}
		if (id == SESSIONS) {
			List<String> lines = AdminStatsFactory.sessionLines(this.nick);
			ScreenNav.open(viewer, LogFactory.ofText("Входы и выходы · " + this.nick, lines));
			return true;
		}
		List<LocalDate> days = AdminStats.days(this.nick);
		int index = this.page * PAGE + id;
		if (id < 0 || id >= PAGE || index >= days.size()) {
			return false;
		}
		LocalDate day = days.get(index);
		ScreenNav.open(viewer, LogFactory.ofText("Статистика " + this.nick + " · "
				+ DateTimeFormatter.ofPattern("dd.MM.yyyy").format(day), AdminStats.dayLines(this.nick, day)));
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
}
