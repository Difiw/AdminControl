package ru.admincontrol.lumira;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.cw.CwStoryRules;
import ru.admincontrol.util.BalanceBoard;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Пакеты сюжетного HUD и общий тик Люмиры (смена режима, ожерелья, синхронизация состояния). */
public final class LumiraNet {
	/** Сервер → клиент: включён ли сюжетный HUD, бонус максимума HP и надетое ожерелье. */
	public static final Identifier STATE = new Identifier(AdminControlMod.MOD_ID, "lumira_state");
	/** Сервер → клиент: сюжетная правая панель (лиры, мирты). */
	public static final Identifier BOARD = new Identifier(AdminControlMod.MOD_ID, "lumira_board");

	private static final Map<UUID, Boolean> LAST_STORY = new ConcurrentHashMap<>();
	private static final Map<UUID, Boolean> LAST_BLOCKED = new ConcurrentHashMap<>();
	private static final java.util.Set<UUID> ENCRYPTION_SENT = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, String> LAST_STATE = new ConcurrentHashMap<>();

	private LumiraNet() {
	}

	public static void sendState(ServerPlayerEntity player, boolean force) {
		if (player.networkHandler == null || !ServerPlayNetworking.canSend(player, STATE)) {
			return;
		}
		boolean story = LumiraMode.hud(player);
		boolean inStory = CwStoryRules.inside(player);
		ItemStack necklace = inStory ? LumiraNecklaces.equipped(player.getUuid()) : ItemStack.EMPTY;
		int bonus = inStory ? LumiraNecklaces.bonusMaxHealth(player) : 0;
		String key = story + "|" + bonus + "|" + (necklace.isEmpty() ? "" : necklace.getItem() + ":" + necklace.getDamage());
		if (!force && key.equals(LAST_STATE.get(player.getUuid()))) {
			return;
		}
		LAST_STATE.put(player.getUuid(), key);
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBoolean(story);
		buf.writeVarInt(bonus);
		buf.writeItemStack(necklace);
		ServerPlayNetworking.send(player, STATE, buf);
	}

	public static void sendBoard(ServerPlayerEntity player, boolean visible) {
		if (player.networkHandler == null || !ServerPlayNetworking.canSend(player, BOARD)) {
			return;
		}
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBoolean(visible);
		buf.writeLong(LumiraEconomy.lyres(player.getUuid()));
		buf.writeLong(LumiraEconomy.myrtles(player.getUuid()));
		ServerPlayNetworking.send(player, BOARD, buf);
	}

	/** Вызывается каждый серверный тик. */
	public static void tick(MinecraftServer server) {
		LumiraNecklaces.tick(server);
		LumiraHealth.tick(server);
		if (server.getTicks() % 10 != 0) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			// Список шифрования уходит клиенту, как только канал доступен (сразу при входе он ещё может быть не готов).
			if (!ENCRYPTION_SENT.contains(player.getUuid()) && player.networkHandler != null
					&& ServerPlayNetworking.canSend(player, ru.admincontrol.item.ItemEncryption.SYNC)) {
				ru.admincontrol.item.ItemEncryption.sendTo(player);
				ENCRYPTION_SENT.add(player.getUuid());
			}
			boolean uiBlocked = ru.admincontrol.util.InvButtons.blocked(player);
			Boolean blockedBefore = LAST_BLOCKED.put(player.getUuid(), uiBlocked);
			if (blockedBefore == null ? uiBlocked : blockedBefore != uiBlocked) {
				ru.admincontrol.util.InvButtons.sync(player);
			}
			boolean story = LumiraMode.hud(player);
			Boolean before = LAST_STORY.put(player.getUuid(), story);
			if (before == null || before != story) {
				BalanceBoard.refresh(player, true);
				LumiraHealth.apply(player);
				sendState(player, true);
			} else {
				sendState(player, false);
			}
		}
	}

	public static void forget(UUID id) {
		LAST_STORY.remove(id);
		LAST_BLOCKED.remove(id);
		ENCRYPTION_SENT.remove(id);
		LAST_STATE.remove(id);
		LumiraNecklaces.forget(id);
		LumiraHealth.forget(id);
	}
}
