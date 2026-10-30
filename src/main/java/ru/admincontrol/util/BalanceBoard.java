package ru.admincontrol.util;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ScoreboardDisplayS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardObjectiveUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardPlayerUpdateS2CPacket;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.net.ModPackets;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BalanceBoard {
	private static final String OBJECTIVE = "ac_wc";
	private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();
	/** До какого момента табло игрока не обновляется: пока крутится колесо казино, исход не должен быть виден. */
	private static final Map<UUID, Long> HOLD = new ConcurrentHashMap<>();

	private BalanceBoard() {
	}

	public static void hold(ServerPlayerEntity player, long ms) {
		if (player != null) {
			HOLD.put(player.getUuid(), System.currentTimeMillis() + Math.max(0, ms));
		}
	}

	private static boolean held(UUID id) {
		Long until = HOLD.get(id);
		return until != null && until > System.currentTimeMillis();
	}

	/** Истёкшие задержки снимаются, табло сразу показывает актуальный баланс. */
	public static void releaseHolds(net.minecraft.server.MinecraftServer server) {
		if (HOLD.isEmpty() || server == null) {
			return;
		}
		long now = System.currentTimeMillis();
		HOLD.entrySet().removeIf(entry -> {
			if (entry.getValue() > now) {
				return false;
			}
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			if (player != null) {
				refresh(player, true);
			}
			return true;
		});
	}

	public static void toggle(ServerPlayerEntity player) {
		if (player == null || DataManager.get() == null || NpcService.isIdleNpc(player)) {
			return;
		}
		PlayerRecord record = DataManager.get().progress(player);
		boolean show = !record.boardVisible();
		record.showBoard = show;
		DataManager.get().saveAll();
		if (show) {
			refresh(player, true);
			Chat.send(player, Chat.green("Табло показано. /board — скрыть."));
		} else {
			hide(player);
			Chat.send(player, Chat.yellow("Табло скрыто. /board — показать."));
		}
	}

	public static void refresh(ServerPlayerEntity player) {
		refresh(player, false);
	}

	public static void refresh(ServerPlayerEntity player, boolean force) {
		if (player == null || player.networkHandler == null || player.networkHandler instanceof NpcNetworkHandler
				|| AuthService.pending(player) || NpcService.isIdleNpc(player) || held(player.getUuid())) {
			return;
		}
		DataManager data = DataManager.get();
		if (data == null) {
			return;
		}
		PlayerRecord record = data.progress(player);
		if (!record.boardVisible()) {
			if (LAST.containsKey(player.getUuid())) {
				hide(player);
			}
			return;
		}
		if (ru.admincontrol.lumira.LumiraMode.hud(player)) {
			// В сюжетке — собственная панель «Лиры» / «Мирты» вместо обычной; обычная возвращается при выходе.
			long lyres = ru.admincontrol.lumira.LumiraEconomy.lyres(player.getUuid());
			long myrtles = ru.admincontrol.lumira.LumiraEconomy.myrtles(player.getUuid());
			long storyStamp = 0x5700000000000000L ^ (lyres * 1_000_003L) ^ (myrtles * 7919L);
			if (force || storyStamp != LAST.getOrDefault(player.getUuid(), Long.MIN_VALUE)) {
				clearVanilla(player);
				ru.admincontrol.lumira.LumiraNet.sendBoard(player, true);
				LAST.put(player.getUuid(), storyStamp);
			}
			return;
		}
		int coins = Math.max(0, record.coins);
		double power = record.power();
		double luck = record.luck();
		String clan = ClanService.nameOf(record.clanId);
		long stamp = ((long) coins << 16) ^ Double.doubleToLongBits(power) ^ Double.doubleToLongBits(luck)
				^ (clan == null ? 0 : clan.hashCode());
		if (!force && stamp == LAST.getOrDefault(player.getUuid(), Long.MIN_VALUE)) {
			return;
		}
		clearVanilla(player);
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBoolean(true);
		buf.writeVarInt(coins);
		buf.writeDouble(power);
		buf.writeDouble(luck);
		if (clan == null || clan.isBlank() || "—".equals(clan)) {
			clan = "нет";
		}
		buf.writeString(clan, 16);
		buf.writeBoolean(data.settings().boardTitleEnabled);
		ServerPlayNetworking.send(player, ModPackets.BOARD, buf);
		LAST.put(player.getUuid(), stamp);
	}

	public static void refreshAll(net.minecraft.server.MinecraftServer server) {
		if (server == null) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			refresh(player, true);
		}
	}

	public static void hide(ServerPlayerEntity player) {
		if (player == null || player.networkHandler == null || player.networkHandler instanceof NpcNetworkHandler) {
			return;
		}
		LAST.remove(player.getUuid());
		clearVanilla(player);
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBoolean(false);
		buf.writeVarInt(0);
		buf.writeDouble(1.0);
		buf.writeDouble(0.0);
		buf.writeString("", 16);
		buf.writeBoolean(false);
		ServerPlayNetworking.send(player, ModPackets.BOARD, buf);
	}

	private static void clearVanilla(ServerPlayerEntity player) {
		ScoreboardObjective objective = new Scoreboard().addObjective(OBJECTIVE, ScoreboardCriterion.DUMMY,
				Text.literal("Common World"), ScoreboardCriterion.RenderType.INTEGER);
		player.networkHandler.sendPacket(new ScoreboardPlayerUpdateS2CPacket(
				ServerScoreboard.UpdateMode.REMOVE, OBJECTIVE, "Баланс", 0));
		player.networkHandler.sendPacket(new ScoreboardPlayerUpdateS2CPacket(
				ServerScoreboard.UpdateMode.REMOVE, OBJECTIVE, "Удача", 0));
		player.networkHandler.sendPacket(new ScoreboardPlayerUpdateS2CPacket(
				ServerScoreboard.UpdateMode.REMOVE, OBJECTIVE, "Сила", 0));
		player.networkHandler.sendPacket(new ScoreboardDisplayS2CPacket(1, null));
		player.networkHandler.sendPacket(new ScoreboardObjectiveUpdateS2CPacket(objective, 1));
	}
}
