package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.ClanRecord;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.util.ActivityLog;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.ClanService;
import ru.admincontrol.util.ClanTop;
import ru.admincontrol.util.PlayFeatures;
import ru.admincontrol.util.ScreenNav;

import java.util.ArrayList;
import java.util.List;

public class ClanScreenHandler extends ScreenHandler {
	public static final int ACCEPT = 0;
	public static final int HOME = 1;
	public static final int SET_HOME = 2;
	public static final int LEAVE = 3;
	public static final int DISBAND = 4;
	public static final int DEP_1 = 5;
	public static final int DEP_10 = 6;
	public static final int DEP_50 = 7;
	public static final int WAREHOUSE = 8;
	public static final int BUY_HOME = 9;
	public static final int BUY_PRIV = 10;
	public static final int UPGRADE_WH = 11;
	public static final int WITH_1 = 12;
	public static final int WITH_10 = 13;
	public static final int WITH_50 = 14;
	public static final int UPGRADE_HALL = 15;
	public static final int BUY_BANK = 16;
	public static final int UPGRADE_CAP = 17;
	public static final int LOGS = 18;
	public static final int PROMOTE_BASE = 300;
	public static final int DEMOTE_BASE = 400;
	public static final int KICK_BASE = 500;
	/** id = RANK_FLAG_BASE + уровень * 32 + номер бита. */
	public static final int RANK_FLAG_BASE = 1000;

	public boolean enabled;
	public boolean inClan;
	public boolean owner;
	public boolean hasInvite;
	public boolean hasHome;
	public boolean canLogs;
	public int hallLevel;
	public int warehouseLevel;
	public int bankLevel;
	public int treasuryCap;
	public int treasury;
	public int price;
	public int myRank;
	public int myFlags;
	public final int[] rankFlags = new int[ClanRecord.LEADER + 1];
	public String name;
	public String ownerNick;
	public final List<String> members;
	public final List<Integer> memberRanks = new ArrayList<>();
	public final List<Boolean> memberOnline = new ArrayList<>();
	public boolean homePrivate;
	public int treasuryLevel;
	/** Цены: дом, приват, ратуша, банк, склад, лимит казны. */
	public final int[] prices = new int[6];
	public int weekPoints;
	public int weekMp;
	public int weekWar;
	public int totalPoints;
	public int totalMp;
	public int totalWar;
	public int weekPlace;
	public int totalPlace;
	public final List<String> topWeek = new ArrayList<>();
	public final List<Integer> topWeekPoints = new ArrayList<>();
	public final List<String> topTotal = new ArrayList<>();
	public final List<Integer> topTotalPoints = new ArrayList<>();
	private final PropertyDelegate live = new ArrayPropertyDelegate(2);

	public static final int P_HOME = 0;
	public static final int P_PRIV = 1;
	public static final int P_HALL = 2;
	public static final int P_BANK = 3;
	public static final int P_WH = 4;
	public static final int P_CAP = 5;
	private static final int TOP = 10;

	public static ClanScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		ClanScreenHandler handler = new ClanScreenHandler(syncId);
		handler.apply(readState(buf));
		return handler;
	}

	public void applyPacket(PacketByteBuf buf) {
		apply(readState(buf));
	}

	public static void refreshOpen(ServerPlayerEntity player) {
		if (player == null || !(player.currentScreenHandler instanceof ClanScreenHandler current)) {
			return;
		}
		current.apply(capture(player));
		var out = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
		write(out, player);
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, ru.admincontrol.net.ModPackets.CLAN_SYNC, out);
	}

	private static State readState(PacketByteBuf buf) {
		State state = new State();
		state.enabled = buf.readBoolean();
		state.inClan = buf.readBoolean();
		state.owner = buf.readBoolean();
		state.hasInvite = buf.readBoolean();
		state.hasHome = buf.readBoolean();
		state.canLogs = buf.readBoolean();
		state.treasury = buf.readVarInt();
		state.price = buf.readVarInt();
		state.hallLevel = buf.readVarInt();
		state.warehouseLevel = buf.readVarInt();
		state.bankLevel = buf.readVarInt();
		state.treasuryCap = buf.readVarInt();
		state.myRank = buf.readVarInt();
		state.myFlags = buf.readVarInt();
		for (int r = 1; r <= ClanRecord.LEADER; r++) {
			state.rankFlags[r] = buf.readVarInt();
		}
		state.name = buf.readString(16);
		state.ownerNick = buf.readString(16);
		int n = buf.readVarInt();
		for (int i = 0; i < n; i++) {
			state.members.add(buf.readString(16));
			state.memberRanks.add(buf.readVarInt());
			state.memberOnline.add(buf.readBoolean());
		}
		state.homePrivate = buf.readBoolean();
		state.treasuryLevel = buf.readVarInt();
		for (int i = 0; i < state.prices.length; i++) {
			state.prices[i] = buf.readVarInt();
		}
		state.weekPoints = buf.readVarInt();
		state.weekMp = buf.readVarInt();
		state.weekWar = buf.readVarInt();
		state.totalPoints = buf.readVarInt();
		state.totalMp = buf.readVarInt();
		state.totalWar = buf.readVarInt();
		state.weekPlace = buf.readVarInt();
		state.totalPlace = buf.readVarInt();
		readTop(buf, state.topWeek, state.topWeekPoints);
		readTop(buf, state.topTotal, state.topTotalPoints);
		return state;
	}

	private static void readTop(PacketByteBuf buf, List<String> names, List<Integer> points) {
		int n = buf.readVarInt();
		for (int i = 0; i < n; i++) {
			names.add(buf.readString(16));
			points.add(buf.readVarInt());
		}
	}

	private static void writeTop(PacketByteBuf buf, List<String> names, List<Integer> points) {
		buf.writeVarInt(names.size());
		for (int i = 0; i < names.size(); i++) {
			buf.writeString(names.get(i), 16);
			buf.writeVarInt(points.get(i));
		}
	}

	private void apply(State state) {
		this.enabled = state.enabled;
		this.inClan = state.inClan;
		this.owner = state.owner;
		this.hasInvite = state.hasInvite;
		this.hasHome = state.hasHome;
		this.canLogs = state.canLogs;
		this.treasury = state.treasury;
		this.price = state.price;
		this.hallLevel = state.hallLevel;
		this.warehouseLevel = state.warehouseLevel;
		this.bankLevel = state.bankLevel;
		this.treasuryCap = state.treasuryCap;
		this.myRank = state.myRank;
		this.myFlags = state.myFlags;
		System.arraycopy(state.rankFlags, 0, this.rankFlags, 0, this.rankFlags.length);
		this.name = state.name == null ? "" : state.name;
		this.ownerNick = state.ownerNick == null ? "" : state.ownerNick;
		this.members.clear();
		this.members.addAll(state.members);
		this.memberRanks.clear();
		this.memberRanks.addAll(state.memberRanks);
		this.memberOnline.clear();
		this.memberOnline.addAll(state.memberOnline);
		this.homePrivate = state.homePrivate;
		this.treasuryLevel = state.treasuryLevel;
		System.arraycopy(state.prices, 0, this.prices, 0, this.prices.length);
		this.weekPoints = state.weekPoints;
		this.weekMp = state.weekMp;
		this.weekWar = state.weekWar;
		this.totalPoints = state.totalPoints;
		this.totalMp = state.totalMp;
		this.totalWar = state.totalWar;
		this.weekPlace = state.weekPlace;
		this.totalPlace = state.totalPlace;
		this.topWeek.clear();
		this.topWeek.addAll(state.topWeek);
		this.topWeekPoints.clear();
		this.topWeekPoints.addAll(state.topWeekPoints);
		this.topTotal.clear();
		this.topTotal.addAll(state.topTotal);
		this.topTotalPoints.clear();
		this.topTotalPoints.addAll(state.topTotalPoints);
		syncLive(this.treasury, this.hasHome);
	}

	public static ClanScreenHandler of(int syncId, ServerPlayerEntity player) {
		ClanScreenHandler handler = new ClanScreenHandler(syncId);
		handler.apply(capture(player));
		return handler;
	}

	public static void write(PacketByteBuf buf, ServerPlayerEntity player) {
		State state = capture(player);
		buf.writeBoolean(state.enabled);
		buf.writeBoolean(state.inClan);
		buf.writeBoolean(state.owner);
		buf.writeBoolean(state.hasInvite);
		buf.writeBoolean(state.hasHome);
		buf.writeBoolean(state.canLogs);
		buf.writeVarInt(state.treasury);
		buf.writeVarInt(state.price);
		buf.writeVarInt(state.hallLevel);
		buf.writeVarInt(state.warehouseLevel);
		buf.writeVarInt(state.bankLevel);
		buf.writeVarInt(state.treasuryCap);
		buf.writeVarInt(state.myRank);
		buf.writeVarInt(state.myFlags);
		for (int r = 1; r <= ClanRecord.LEADER; r++) {
			buf.writeVarInt(state.rankFlags[r]);
		}
		buf.writeString(state.name == null ? "" : state.name, 16);
		buf.writeString(state.ownerNick == null ? "" : state.ownerNick, 16);
		buf.writeVarInt(state.members.size());
		for (int i = 0; i < state.members.size(); i++) {
			String nick = state.members.get(i);
			buf.writeString(nick == null ? "" : nick, 16);
			buf.writeVarInt(state.memberRanks.get(i));
			buf.writeBoolean(state.memberOnline.get(i));
		}
		buf.writeBoolean(state.homePrivate);
		buf.writeVarInt(state.treasuryLevel);
		for (int price : state.prices) {
			buf.writeVarInt(Math.max(0, price));
		}
		buf.writeVarInt(state.weekPoints);
		buf.writeVarInt(state.weekMp);
		buf.writeVarInt(state.weekWar);
		buf.writeVarInt(state.totalPoints);
		buf.writeVarInt(state.totalMp);
		buf.writeVarInt(state.totalWar);
		buf.writeVarInt(state.weekPlace);
		buf.writeVarInt(state.totalPlace);
		writeTop(buf, state.topWeek, state.topWeekPoints);
		writeTop(buf, state.topTotal, state.topTotalPoints);
	}

	private ClanScreenHandler(int syncId) {
		super(ModScreenHandlers.CLAN, syncId);
		this.name = "";
		this.ownerNick = "";
		this.members = new ArrayList<>();
		this.addProperties(this.live);
	}

	public int treasury() {
		return this.live.get(0);
	}

	public boolean homeSet() {
		return this.live.get(1) != 0;
	}

	public boolean can(int flag) {
		return (this.myFlags & flag) != 0;
	}

	/** Можно ли повышать/понижать/выгонять участника с этим уровнем (сервер всё равно проверяет). */
	public boolean canManage(int targetRank) {
		return can(ClanRecord.MANAGE) && targetRank < this.myRank;
	}

	private void syncLive(int treasury, boolean hasHome) {
		this.live.set(0, treasury);
		this.live.set(1, hasHome ? 1 : 0);
	}

	private static State capture(ServerPlayerEntity player) {
		State state = new State();
		state.enabled = ClanService.enabled();
		state.price = Math.max(0, PlayFeatures.s().clanCreatePrice);
		if (player == null) {
			return state;
		}
		ClanRecord clan = ClanService.of(player);
		state.hasInvite = ClanService.hasInvite(player);
		List<ClanRecord> week = ClanTop.rankedWeek();
		List<ClanRecord> total = ClanTop.rankedTotal();
		for (int i = 0; i < week.size() && i < TOP; i++) {
			state.topWeek.add(clip(week.get(i).name));
			state.topWeekPoints.add(ClanTop.score(week.get(i)));
		}
		for (int i = 0; i < total.size() && i < TOP; i++) {
			state.topTotal.add(clip(total.get(i).name));
			state.topTotalPoints.add(ClanTop.total(total.get(i)));
		}
		if (clan == null) {
			return state;
		}
		state.weekPoints = ClanTop.score(clan);
		state.weekMp = Math.max(0, clan.weekMpWins);
		state.weekWar = Math.max(0, clan.weekWarWins);
		state.totalPoints = ClanTop.total(clan);
		state.totalMp = Math.max(0, clan.totalMpWins);
		state.totalWar = Math.max(0, clan.totalWarWins);
		state.weekPlace = week.indexOf(clan) + 1;
		state.totalPlace = total.indexOf(clan) + 1;
		state.homePrivate = clan.homePrivate;
		state.treasuryLevel = Math.max(1, clan.treasuryLevel);
		var s = PlayFeatures.s();
		state.prices[P_HOME] = Math.max(0, s.clanHomePrice);
		state.prices[P_PRIV] = Math.max(0, s.clanHomePrivatePrice);
		state.prices[P_HALL] = Math.max(1, s.clanHomePrice) * (Math.max(1, clan.hallLevel) + 1);
		state.prices[P_BANK] = clan.bankLevel > 0 ? Math.max(1, s.clanBankPrice) * (clan.bankLevel + 1)
				: Math.max(0, s.clanBankPrice);
		state.prices[P_WH] = s.clanWarehousePrice(clan.warehouseLevel + 1);
		state.prices[P_CAP] = Math.max(1, s.clanBankPrice) * Math.max(1, clan.treasuryLevel);
		state.inClan = true;
		state.owner = ClanService.isOwner(player);
		state.canLogs = state.owner && !isBlogger(player);
		state.hasHome = clan.home != null && clan.homeBought;
		state.treasury = clan.treasury;
		state.hallLevel = Math.max(0, clan.hallLevel);
		state.warehouseLevel = Math.max(0, clan.warehouseLevel);
		state.bankLevel = Math.max(0, clan.bankLevel);
		state.treasuryCap = ClanService.treasuryCap(clan);
		state.myRank = ClanService.rankOf(clan, player);
		state.myFlags = ClanService.flags(clan, player);
		for (int r = 1; r <= ClanRecord.LEADER; r++) {
			state.rankFlags[r] = ClanService.rankFlags(clan, r);
		}
		state.name = clan.name == null ? "" : clan.name;
		state.ownerNick = clan.ownerNick == null ? "" : clan.ownerNick;
		if (clan.memberNicks != null) {
			List<String> sorted = new ArrayList<>(clan.memberNicks);
			sorted.sort((a, b) -> {
				int byRank = Integer.compare(ClanService.rankOfNick(clan, b), ClanService.rankOfNick(clan, a));
				return byRank != 0 ? byRank : a.compareToIgnoreCase(b);
			});
			var players = player.getServer() == null ? null : player.getServer().getPlayerManager();
			for (String nick : sorted) {
				state.members.add(nick);
				state.memberRanks.add(ClanService.rankOfNick(clan, nick));
				state.memberOnline.add(players != null && players.getPlayer(nick) != null);
			}
		}
		return state;
	}

	private static String clip(String name) {
		String n = name == null ? "" : name;
		return n.length() > 16 ? n.substring(0, 16) : n;
	}

	private static boolean isBlogger(ServerPlayerEntity player) {
		DataManager data = DataManager.get();
		return data != null && data.isAdmin(player) && AdminRanks.isBlogger(data.getAdminLevel(player));
	}

	private String memberAt(int index) {
		return index >= 0 && index < this.members.size() ? this.members.get(index) : null;
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			return false;
		}
		String err;
		String ok;
		if (id == ACCEPT) {
			err = ClanService.accept(serverPlayer);
			ok = "Вы вступили в клан.";
		} else if (id == HOME) {
			err = ClanService.requestHome(serverPlayer);
			ok = "";
			if (err == null) {
				serverPlayer.closeHandledScreen();
			}
		} else if (id == SET_HOME) {
			err = ClanService.setHome(serverPlayer);
			ok = "Дом клана установлен.";
		} else if (id == LEAVE) {
			err = ClanService.leave(serverPlayer);
			ok = "Вы покинули клан.";
		} else if (id == DISBAND) {
			err = ClanService.disband(serverPlayer);
			ok = "Клан распущен.";
		} else if (id == DEP_1 || id == DEP_10 || id == DEP_50) {
			int amount = id == DEP_1 ? 1 : id == DEP_10 ? 10 : 50;
			err = ClanService.deposit(serverPlayer, amount);
			ok = "В казну: " + amount + " WC.";
		} else if (id == WITH_1 || id == WITH_10 || id == WITH_50) {
			int amount = id == WITH_1 ? 1 : id == WITH_10 ? 10 : 50;
			err = ClanService.withdraw(serverPlayer, amount);
			ok = "Снято " + amount + " WC.";
		} else if (id == WAREHOUSE) {
			var clan = ClanService.of(serverPlayer);
			if (clan == null || clan.warehouseLevel <= 0) {
				Chat.send(serverPlayer, Chat.red("Сначала купите склад клана."));
				return true;
			}
			if (ru.admincontrol.util.ClanWarService.warehouseLocked(clan, serverPlayer)) {
				Chat.send(serverPlayer, Chat.red("Склад закрыт: ядро дома сломано в битве кланов."));
				return true;
			}
			if (!ClanService.onTerritory(clan, serverPlayer)) {
				Chat.send(serverPlayer, Chat.red("Склад доступен только на территории клана."));
				return true;
			}
			if (!ClanService.hasFlag(clan, serverPlayer, ClanRecord.WH_PUT)
					&& !ClanService.hasFlag(clan, serverPlayer, ClanRecord.WH_TAKE)) {
				Chat.send(serverPlayer, Chat.red("Нет доступа к складу."));
				return true;
			}
			ScreenNav.replace(serverPlayer, new ClanWarehouseFactory());
			return true;
		} else if (id == LOGS) {
			ClanRecord clan = ClanService.of(serverPlayer);
			if (clan == null || !ClanService.isOwner(serverPlayer)) {
				Chat.send(serverPlayer, Chat.red("Логи клана доступны только главе."));
				return true;
			}
			if (isBlogger(serverPlayer)) {
				Chat.send(serverPlayer, Chat.red("Блогеру логи недоступны."));
				return true;
			}
			List<ActivityLog.Line> lines = ActivityLog.readClan(clan.name);
			if (lines.isEmpty()) {
				lines = new ArrayList<>();
				lines.add(new ActivityLog.Line("Пока нет событий.", true, ru.admincontrol.data.ActionKind.CLAN));
			}
			ScreenNav.open(serverPlayer, new LogFactory("Логи клана  " + clan.name, lines, true, true, ""));
			return true;
		} else if (id == BUY_HOME) {
			err = ClanService.buyHome(serverPlayer);
			ok = "Дом клана куплен.";
		} else if (id == BUY_PRIV) {
			err = ClanService.buyPrivate(serverPlayer);
			ok = "Приват дома клана куплен.";
		} else if (id == UPGRADE_WH) {
			err = ClanService.upgradeWarehouse(serverPlayer);
			ok = "Склад улучшен.";
		} else if (id == UPGRADE_HALL) {
			err = ClanService.upgradeHall(serverPlayer);
			ok = "Ратуша улучшена.";
		} else if (id == BUY_BANK) {
			err = ClanService.buyBank(serverPlayer);
			ok = "Банк клана построен / улучшен.";
		} else if (id == UPGRADE_CAP) {
			err = ClanService.upgradeTreasury(serverPlayer);
			ok = "Лимит казны увеличен.";
		} else if (id >= RANK_FLAG_BASE) {
			int packed = id - RANK_FLAG_BASE;
			int rank = packed / 32;
			int bit = packed % 32;
			err = ClanService.toggleRankFlag(serverPlayer, rank, 1 << bit);
			ok = "Доступы уровня обновлены.";
		} else if (id >= KICK_BASE && memberAt(id - KICK_BASE) != null) {
			err = ClanService.kick(serverPlayer, memberAt(id - KICK_BASE));
			ok = "Игрок исключён.";
		} else if (id >= DEMOTE_BASE && id < KICK_BASE && memberAt(id - DEMOTE_BASE) != null) {
			err = ClanService.demote(serverPlayer, memberAt(id - DEMOTE_BASE));
			ok = "Игрок понижен.";
		} else if (id >= PROMOTE_BASE && id < DEMOTE_BASE && memberAt(id - PROMOTE_BASE) != null) {
			err = ClanService.promote(serverPlayer, memberAt(id - PROMOTE_BASE));
			ok = "Игрок повышен.";
		} else {
			return false;
		}
		if (err != null) {
			Chat.send(serverPlayer, Chat.red(err));
		} else if (!ok.isEmpty()) {
			Chat.send(serverPlayer, Chat.green(ok));
		}
		if (id != HOME) {
			refreshOpen(serverPlayer);
		}
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

	private static final class State {
		boolean enabled;
		boolean inClan;
		boolean owner;
		boolean hasInvite;
		boolean hasHome;
		boolean canLogs;
		int treasury;
		int price;
		int hallLevel;
		int warehouseLevel;
		int bankLevel;
		int treasuryCap;
		int myRank;
		int myFlags;
		int[] rankFlags = new int[ClanRecord.LEADER + 1];
		String name = "";
		String ownerNick = "";
		List<String> members = new ArrayList<>();
		List<Integer> memberRanks = new ArrayList<>();
		List<Boolean> memberOnline = new ArrayList<>();
		boolean homePrivate;
		int treasuryLevel;
		int[] prices = new int[6];
		int weekPoints;
		int weekMp;
		int weekWar;
		int totalPoints;
		int totalMp;
		int totalWar;
		int weekPlace;
		int totalPlace;
		List<String> topWeek = new ArrayList<>();
		List<Integer> topWeekPoints = new ArrayList<>();
		List<String> topTotal = new ArrayList<>();
		List<Integer> topTotalPoints = new ArrayList<>();
	}
}
