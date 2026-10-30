package ru.admincontrol.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.data.ActionKind;
import ru.admincontrol.data.DataManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AdminNotify {
	private static final Map<String, Long> NOISY = new ConcurrentHashMap<>();

	private AdminNotify() {
	}

	public static void playerAction(ServerPlayerEntity actor, String action) {
		playerAction(actor, ActionKind.COMMAND, action);
	}

	public static void playerAction(ServerPlayerEntity actor, ActionKind kind, String action) {
		playerAction(actor, kind, action, kind != ActionKind.COMMAND_FAIL);
	}

	public static void playerAction(ServerPlayerEntity actor, ActionKind kind, String action, boolean ok) {
		if (actor == null || actor.getServer() == null || action == null || action.isBlank()) {
			return;
		}
		if (NpcService.isIdleNpc(actor)) {
			return;
		}
		if (AuthService.pending(actor) && kind != ActionKind.JOIN
				&& kind != ActionKind.COMMAND && kind != ActionKind.COMMAND_FAIL) {
			return;
		}
		if (quietBuilderAction(actor, kind)) {
			return;
		}
		PrivateService.track(actor, kind, action, ok);
		if (EventService.quietActions(actor) && (kind == ActionKind.BREAK || kind == ActionKind.HIT_BLOCK
				|| kind == ActionKind.PICKUP || kind == ActionKind.PLACE || kind == ActionKind.DROP
				|| kind == ActionKind.DAMAGE || kind == ActionKind.USE_ITEM)) {
			return;
		}
		if (noisy(kind) && !allowNoisy(actor, kind)) {
			return;
		}
		boolean admin = DataManager.get() != null && DataManager.get().isAdmin(actor);
		String line = (admin ? "Администратор " : "Игрок ") + actor.getGameProfile().getName() + " " + action;
		Text text = kind == ActionKind.ATTACK_PLAYER ? Chat.purple(line) : Chat.gray(line);
		toAdminsFeed(actor.getServer(), kind, text);
		String prefix = kind == null ? "" : kind.label + ": ";
		ActivityLog.player(actor.getGameProfile().getName(), prefix + action, ok);
		if (DataManager.get() != null && DataManager.get().isAdmin(actor)) {
			ActivityLog.admin(actor.getGameProfile().getName(), prefix + action, ok);
		}
	}

	/** На стройке (строительное измерение CW, стройка карты МП) действия с предметами не пишутся ни в чат, ни в логи. */
	private static boolean quietBuilderAction(ServerPlayerEntity actor, ActionKind kind) {
		if (kind == null || !(kind == ActionKind.CREATIVE_TAKE || kind == ActionKind.CREATIVE_DELETE || kind == ActionKind.USE_ITEM
				|| kind == ActionKind.PICKUP || kind == ActionKind.DROP || kind == ActionKind.PLACE || kind == ActionKind.BREAK
				|| kind == ActionKind.HIT_BLOCK || kind == ActionKind.INVENTORY || kind == ActionKind.CRAFT
				|| kind == ActionKind.CONSUME || kind == ActionKind.SCREEN || kind == ActionKind.WORKSTATION)) {
			return false;
		}
		return ru.admincontrol.cw.CwBuilderWorld.inside(actor) || EventMapEditor.participant(actor);
	}

	private static boolean noisy(ActionKind kind) {
		return kind == ActionKind.HIT_BLOCK || kind == ActionKind.INVENTORY || kind == ActionKind.DAMAGE
				|| kind == ActionKind.PICKUP || kind == ActionKind.XP;
	}

	private static boolean allowNoisy(ServerPlayerEntity actor, ActionKind kind) {
		String key = actor.getUuid() + ":" + kind.name();
		long now = System.currentTimeMillis();
		Long prev = NOISY.get(key);
		if (prev != null && now - prev < 400L) {
			return false;
		}
		NOISY.put(key, now);
		if (NOISY.size() > 4000) {
			NOISY.clear();
		}
		return true;
	}

	public static void playerHitPlayer(ServerPlayerEntity actor, ServerPlayerEntity victim) {
		if (actor == null || victim == null || actor.getServer() == null) {
			return;
		}
		playerAction(actor, ActionKind.ATTACK_PLAYER, "атаковал игрока " + victim.getGameProfile().getName());
	}

	public static void creativeTake(ServerPlayerEntity actor, String item) {
		if (actor == null || actor.getServer() == null || item == null || item.isBlank()) {
			return;
		}
		playerAction(actor, ActionKind.CREATIVE_TAKE, "взял из креатива " + item);
	}

	public static void record(ServerCommandSource source, String action, String target, String reason) {
		String admin = CommandUtil.adminName(source);
		ActionLog.action(action, admin, target, reason);
		ActivityLog.admin(admin, action + " " + target + " " + reason, true);
		AdminStats.onRecord(source, action);
		if (source == null || source.getServer() == null) {
			return;
		}
		StringBuilder line = new StringBuilder("Администратор ").append(admin).append(" ").append(action);
		if (target != null && !target.isBlank() && !"-".equals(target)) {
			line.append(" → ").append(target);
		}
		if (reason != null && !reason.isBlank() && !"-".equals(reason)) {
			line.append(" (").append(reason).append(")");
		}
		toAdminsFeed(source.getServer(), ActionKind.ADMIN, Chat.gray(line.toString()));
	}

	public static void denied(ServerCommandSource source, String message) {
		if (source == null) {
			return;
		}
		source.sendError(Chat.brightRed(message));
		String admin = CommandUtil.adminName(source);
		ActionLog.action("DENIED", admin, "-", message);
		ActivityLog.admin(admin, message, false);
		ActivityLog.player(admin, message, false);
		toAdminsFeed(source.getServer(), ActionKind.ADMIN, Chat.brightRed("Администратор " + admin + " " + message));
		CommandLog.mark();
	}

	public static void toAdmins(MinecraftServer server, ServerPlayerEntity except, Text text) {
		DataManager data = DataManager.get();
		if (server == null || data == null || text == null) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (except != null && player.getUuid().equals(except.getUuid())) {
				continue;
			}
			if (data.isAdmin(player) && !ru.admincontrol.data.AdminRanks.isBlogger(data.getAdminLevel(player))) {
				player.sendMessage(text, false);
			}
		}
	}

	public static void toAdminsFeed(MinecraftServer server, ActionKind kind, Text text) {
		DataManager data = DataManager.get();
		if (server == null || data == null || text == null) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (data.wantsActionFeed(player, kind)) {
				player.sendMessage(text, false);
			}
		}
	}
}
