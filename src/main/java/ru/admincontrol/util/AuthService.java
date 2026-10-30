package ru.admincontrol.util;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.net.ModPackets;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthService {
	private static final Map<UUID, Session> PENDING = new ConcurrentHashMap<>();
	private static final java.util.Set<UUID> LAN_ADMIN_NOTICE = ConcurrentHashMap.newKeySet();
	/** После успешного входа игрок ещё {@link #PROTECT_MS} мс не получает урон. */
	private static final Map<UUID, Long> PROTECTED = new ConcurrentHashMap<>();
	private static final long PROTECT_MS = 5000L;

	private AuthService() {
	}

	public static final class Session {
		public boolean registered;
		public boolean createdAccount;
		public int hintsSent;
		public int attempts;
		public int tick;
		public double x;
		public double y;
		public double z;
		public float yaw;
		public float pitch;
	}

	public static boolean pending(ServerPlayerEntity player) {
		return player != null && PENDING.containsKey(player.getUuid());
	}

	public static boolean invulnerable(ServerPlayerEntity player) {
		if (player == null) {
			return false;
		}
		if (pending(player)) {
			return true;
		}
		Long until = PROTECTED.get(player.getUuid());
		if (until == null) {
			return false;
		}
		if (System.currentTimeMillis() >= until) {
			PROTECTED.remove(player.getUuid());
			return false;
		}
		return true;
	}

	/** Данные с экрана входа: вход по паролю или создание пароля, если аккаунта ещё нет. */
	public static void submit(ServerPlayerEntity player, String nick, String password) {
		Session session = player == null ? null : PENDING.get(player.getUuid());
		if (session == null) {
			return;
		}
		if (password == null || password.isEmpty()) {
			fail(player, "Введите пароль.");
			return;
		}
		if (session.registered) {
			tryLogin(player, nick == null ? "" : nick, password);
		} else {
			tryRegister(player, password, password);
		}
	}

	private static void fail(ServerPlayerEntity player, String text) {
		Chat.send(player, Chat.red(text));
		syncMessage(player, text);
	}

	private static void syncMessage(ServerPlayerEntity player, String text) {
		if (player == null || player.networkHandler == null) {
			return;
		}
		var buf = PacketByteBufs.create();
		buf.writeString(text == null ? "" : text, 256);
		ServerPlayNetworking.send(player, ModPackets.AUTH_MSG, buf);
	}

	public static boolean enabled() {
		DataManager data = DataManager.get();
		return data != null && data.settings().passwordEnabled;
	}

	/** A server-side relocation (CW profile recovery on join) happened while the login screen is up: the freeze must hold the NEW place, not the one before the move. */
	public static void rebase(ServerPlayerEntity player) {
		Session session = player == null ? null : PENDING.get(player.getUuid());
		if (session == null) {
			return;
		}
		session.x = player.getX();
		session.y = player.getY();
		session.z = player.getZ();
		session.yaw = player.getYaw();
		session.pitch = player.getPitch();
	}

	public static void begin(ServerPlayerEntity player) {
		if (player == null) {
			return;
		}
		if (!enabled()) {
			PENDING.remove(player.getUuid());
			syncLock(player, false);
			complete(player, null, false);
			return;
		}
		Session session = new Session();
		session.registered = PasswordService.registered(player);
		session.attempts = 0;
		session.x = player.getX();
		session.y = player.getY();
		session.z = player.getZ();
		session.yaw = player.getYaw();
		session.pitch = player.getPitch();
		PENDING.put(player.getUuid(), session);
		BalanceBoard.hide(player);
		freeze(player, session);
		syncLock(player, true);
		hint(player, session);
	}

	public static void tick(ServerPlayerEntity player) {
		Session session = PENDING.get(player.getUuid());
		if (session == null) {
			return;
		}
		session.tick++;
		freeze(player, session);
		if (session.hintsSent < 2 && session.tick > 0 && session.tick % 80 == 0) {
			hint(player, session);
		}
	}

	public static boolean tryChatAuth(ServerPlayerEntity player, String message) {
		Session session = PENDING.get(player.getUuid());
		if (session == null) {
			return false;
		}
		hint(player, session);
		return true;
	}

	public static boolean tryLogin(ServerPlayerEntity player, String nick, String password) {
		Session session = PENDING.get(player.getUuid());
		if (session == null) {
			Chat.send(player, Chat.red(enabled() ? "/l доступна только при входе в аккаунт."
					: "Система паролей выключена — вход без пароля."));
			return false;
		}
		if (!session.registered) {
			Chat.send(player, Chat.red("Сначала создайте пароль: /password <пароль> <повтор>."));
			return false;
		}
		if (PasswordService.matches(player, nick, password)) {
			finish(player, true);
			return true;
		}
		session.attempts++;
		int left = 3 - session.attempts;
		if (left <= 0) {
			player.networkHandler.disconnect(Chat.red("Неверный пароль. 3 попытки исчерпаны."));
			PENDING.remove(player.getUuid());
			syncLock(player, false);
			return false;
		}
		fail(player, "Неверный ник или пароль. Осталось попыток: " + left);
		return false;
	}

	public static boolean tryChatLogin(ServerPlayerEntity player, String message) {
		Session session = PENDING.get(player.getUuid());
		if (session == null || !session.registered) {
			return false;
		}
		String[] parts = message.trim().split("\\s+");
		if (parts.length < 2) {
			if (session.hintsSent < 2) {
				hint(player, session);
			}
			return true;
		}
		String nick = parts[0];
		String pass = message.trim().substring(nick.length()).trim();
		if (PasswordService.matches(player, nick, pass)) {
			finish(player, true);
			return true;
		}
		session.attempts++;
		int left = 3 - session.attempts;
		if (left <= 0) {
			player.networkHandler.disconnect(Chat.red("Неверный пароль. 3 попытки исчерпаны."));
			PENDING.remove(player.getUuid());
			syncLock(player, false);
			return true;
		}
		Chat.send(player, Chat.red("Неверный пароль. Осталось попыток: " + left));
		return true;
	}

	public static boolean tryRegister(ServerPlayerEntity player, String password, String repeat) {
		Session session = PENDING.get(player.getUuid());
		if (session == null) {
			Chat.send(player, Chat.red(enabled() ? "/password доступна только при создании аккаунта."
					: "Система паролей выключена — пароль не нужен."));
			return false;
		}
		if (session.registered) {
			if (session.hintsSent < 2) {
				hint(player, session);
			}
			return false;
		}
		if (!password.equals(repeat)) {
			fail(player, "Пароли не совпадают.");
			return false;
		}
		if (password.length() < 3 || password.length() > 32) {
			fail(player, "Пароль: от 3 до 32 символов.");
			return false;
		}
		if (!PasswordService.register(player, password, repeat)) {
			fail(player, "Не удалось сохранить пароль.");
			return false;
		}
		session.createdAccount = true;
		finish(player, true);
		return true;
	}

	public static void finish(ServerPlayerEntity player, boolean success) {
		Session session = player == null ? null : PENDING.remove(player.getUuid());
		if (player == null) {
			return;
		}
		syncLock(player, false);
		if (!success) {
			return;
		}
		PROTECTED.put(player.getUuid(), System.currentTimeMillis() + PROTECT_MS);
		player.sendMessage(net.minecraft.text.Text.literal("Защита от урона: 5 секунд").formatted(net.minecraft.util.Formatting.GREEN), true);
		complete(player, session, true);
	}

	/** Всё, что происходит после входа (с паролем или без). */
	private static void complete(ServerPlayerEntity player, Session session, boolean viaPassword) {
		DataManager data = DataManager.get();
		if (data != null) {
			data.deliverPending(player);
		}
		if (viaPassword) {
			Chat.send(player, Chat.green("Вход выполнен."));
		}
		InventoryVault.restoreOnJoin(player);
		WorldGate.ejectIfLocked(player);
		if (session != null && session.createdAccount) {
			AdminNotify.playerAction(player, ru.admincontrol.data.ActionKind.JOIN, "создал пароль");
		} else if (viaPassword) {
			AdminNotify.playerAction(player, ru.admincontrol.data.ActionKind.JOIN, "вошёл в аккаунт");
		}
		BalanceBoard.refresh(player, true);
		InvButtons.sync(player);
		FreezeService.sync(player);
		ru.admincontrol.util.EventService.onHostJoin(player);
		if (LAN_ADMIN_NOTICE.remove(player.getUuid())) {
			Chat.send(player, Chat.green("Вы назначены администратором — мультиплеер начат. /ahelp"));
			ru.admincontrol.command.CommandSync.refresh(player);
		}
	}

	/** /cw password false: всех, кто сейчас на экране входа, пускаем без пароля. */
	public static void releaseAll(net.minecraft.server.MinecraftServer server) {
		if (server == null) {
			return;
		}
		for (UUID id : new java.util.ArrayList<>(PENDING.keySet())) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
			if (player == null) {
				PENDING.remove(id);
				continue;
			}
			PENDING.remove(id);
			syncLock(player, false);
			complete(player, null, false);
		}
	}

	public static void noteLanAdmin(ServerPlayerEntity player) {
		if (player != null) {
			LAN_ADMIN_NOTICE.add(player.getUuid());
		}
	}

	public static void forget(ServerPlayerEntity player) {
		if (player != null) {
			PENDING.remove(player.getUuid());
			PROTECTED.remove(player.getUuid());
			syncLock(player, false);
		}
	}

	private static void freeze(ServerPlayerEntity player, Session session) {
		if (player == null || player.networkHandler == null || session == null) {
			return;
		}
		player.setVelocity(0, 0, 0);
		player.velocityModified = true;
		player.setSprinting(false);
		player.fallDistance = 0;
		if (session.tick % 5 == 0) {
			player.networkHandler.requestTeleport(session.x, session.y, session.z, session.yaw, session.pitch);
		}
	}

	private static void syncLock(ServerPlayerEntity player, boolean locked) {
		if (player == null || player.networkHandler == null) {
			return;
		}
		var buf = PacketByteBufs.create();
		buf.writeBoolean(locked);
		buf.writeBoolean(PasswordService.registered(player));
		ServerPlayNetworking.send(player, ModPackets.AUTH_LOCK, buf);
	}

	public static boolean nudge(ServerPlayerEntity player) {
		Session session = PENDING.get(player == null ? null : player.getUuid());
		if (session == null) {
			return false;
		}
		if (session.hintsSent < 2) {
			hint(player, session);
		}
		return true;
	}

	private static void hint(ServerPlayerEntity player, Session session) {
		if (session.hintsSent >= 2) {
			return;
		}
		session.hintsSent++;
		if (session.registered) {
			Chat.send(player, Chat.yellow("Вход: /l <ник> <пароль>"));
		} else {
			Chat.send(player, Chat.yellow("Создание аккаунта: /password <пароль> <повтор_пароля>"));
		}
	}
}
