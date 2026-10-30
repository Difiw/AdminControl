package ru.admincontrol.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.ActionKind;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.AntiCheatCase;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.net.ModPackets;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Проверка файлов игры у игроков и администраторов: клиент присылает подозрительные файлы,
 * сервер сообщает старшим администраторам (красным), пишет в логи и /koto. Никого не наказывает.
 * Тем, кого не было в сети, сообщения выдаются при входе.
 */
public final class FileScanService {
	public static final int PATH_MAX = 400;
	public static final int REASON_MAX = 300;
	public static final int STATE_MAX = 48;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final DateTimeFormatter TIME =
			DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());
	private static final int FIRST_DELAY_TICKS = 20 * 15;
	private static final int RESCAN_TICKS = 20 * 60 * 30;
	private static final int MAX_ALERTS = 1000;
	private static final int MAX_ON_JOIN = 20;
	private static final long KEEP_UNSEEN_MS = 30L * 24 * 60 * 60 * 1000;
	private static final String WRITER = "автоматическая проверка файлов Admin Control";

	private static final Map<UUID, Integer> DUE = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> SCANS = new ConcurrentHashMap<>();
	private static final Map<String, Long> SUSPECT_AT = new ConcurrentHashMap<>();
	private static final long SUSPECT_COOLDOWN_MS = 10L * 60 * 1000;
	/** Уже сообщённые находки файлов за текущий заход игрока: uuid|путь|причина|состояние. */
	private static final Set<String> SESSION_SEEN = ConcurrentHashMap.newKeySet();
	public static final String OFF_STATE = "античит выключен — без наказания";
	public static final String STAFF_STATE = "администратор — автоблокировка не применяется";
	private static Store store = new Store();
	private static int nextScan = 1;

	private FileScanService() {
	}

	public static final class Alert {
		public long at;
		public String uuid = "";
		public String nick = "";
		public String role = "";
		/** Вид подозрения от античита (автокликер, X-Ray…); пусто — подозрительный файл. */
		public String kind = "";
		public String path = "";
		public String reason = "";
		public String state = "";
		public List<String> seen = new ArrayList<>();
		/** Кураторы+, которым находка у администратора уже показана при входе. */
		public List<String> joinShown = new ArrayList<>();
	}

	private static final class Store {
		List<Alert> alerts = new ArrayList<>();
	}

	public static void load() {
		store = new Store();
		DUE.clear();
		SCANS.clear();
		Path file = file();
		if (file == null || !Files.exists(file)) {
			return;
		}
		try {
			Store loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Store.class);
			if (loaded != null && loaded.alerts != null) {
				store.alerts = loaded.alerts;
			}
		} catch (Exception e) {
			ActionLog.error("Не удалось прочитать file_scan_alerts.json", e);
		}
	}

	private static void save() {
		Path file = file();
		if (file == null) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling("file_scan_alerts.json.tmp");
			Files.writeString(tmp, GSON.toJson(store), StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			ActionLog.error("Не удалось сохранить file_scan_alerts.json", e);
		}
	}

	private static Path file() {
		DataManager data = DataManager.get();
		return data == null ? null : data.getConfigDir().resolve("file_scan_alerts.json");
	}

	/** Вход: старшим администраторам — накопленные сообщения; всем — проверка файлов через 15 с. */
	public static void onJoin(ServerPlayerEntity player) {
		if (player == null || NpcService.isNpc(player)) {
			return;
		}
		if (receives(player)) {
			deliverPending(player);
		}
		DUE.put(player.getUuid(), FIRST_DELAY_TICKS);
	}

	public static void forget(ServerPlayerEntity player) {
		if (player != null) {
			DUE.remove(player.getUuid());
			SCANS.remove(player.getUuid());
			String prefix = player.getUuid() + "|";
			SESSION_SEEN.removeIf(key -> key.startsWith(prefix));
		}
	}

	public static void tick(MinecraftServer server, int tickCounter) {
		if (server == null || DataManager.get() == null || tickCounter % 20 != 0) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			Integer left = DUE.get(player.getUuid());
			if (left == null) {
				continue;
			}
			if (left > 20) {
				DUE.put(player.getUuid(), left - 20);
				continue;
			}
			DUE.put(player.getUuid(), RESCAN_TICKS);
			request(player);
		}
	}

	private static void request(ServerPlayerEntity player) {
		if (!DataManager.get().settings().fileScanEnabled || NpcService.isNpc(player)
				|| !ServerPlayNetworking.canSend(player, ModPackets.FILE_SCAN_REQUEST)) {
			return;
		}
		if (SCANS.containsKey(player.getUuid())) {
			suspect(player, "нет ответа модуля проверки", "предыдущий challenge не завершён; мод может быть отключён или изменён");
		}
		int id;
		synchronized (FileScanService.class) {
			id = nextScan++;
		}
		SCANS.put(player.getUuid(), id);
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(id);
		ServerPlayNetworking.send(player, ModPackets.FILE_SCAN_REQUEST, buf);
	}

	/** Находки клиента (по частям). */
	public static void onResult(ServerPlayerEntity player, int scanId, boolean last, List<String[]> findings) {
		if (player == null || DataManager.get() == null) {
			return;
		}
		Integer expected = SCANS.get(player.getUuid());
		if (expected == null || expected != scanId) {
			AntiCheatService.confirmed(player, AntiCheatRules.PACKET_ABUSE, "поддельный ответ проверки", "scan challenge mismatch",
					List.of("Ответ не совпал с активным id сервера."));
			return;
		}
		if (last) {
			SCANS.remove(player.getUuid());
		}
		if (!DataManager.get().settings().fileScanEnabled) {
			return;
		}
		boolean changed = false;
		for (String[] f : findings) {
			changed |= report(player, "", f[0], f[1], f[2]);
		}
		if (changed) {
			trim();
			save();
		}
	}

	/**
	 * Подозрение от античита, когда сам античит (автобан) выключен, а проверка читов включена:
	 * только сообщение администрации. Один вид подозрения на игрока — не чаще раза в 10 минут.
	 */
	public static void suspect(ServerPlayerEntity player, String kind, String detail) {
		DataManager data = DataManager.get();
		if (data == null || !data.settings().fileScanEnabled) {
			return;
		}
		suspect(player, kind, detail, OFF_STATE);
	}

	/** Подозрение без наказания с указанной причиной (античит выключен или игрок — администратор). */
	public static void suspect(ServerPlayerEntity player, String kind, String detail, String state) {
		DataManager data = DataManager.get();
		if (player == null || data == null || NpcService.isNpc(player)) {
			return;
		}
		String key = player.getUuid() + "|" + kind + "|" + state;
		long now = System.currentTimeMillis();
		Long prev = SUSPECT_AT.get(key);
		if (prev != null && now - prev < SUSPECT_COOLDOWN_MS) {
			return;
		}
		SUSPECT_AT.put(key, now);
		if (report(player, kind, "", detail, state)) {
			trim();
			save();
		}
	}

	private static boolean report(ServerPlayerEntity player, String kind, String path, String reason, String state) {
		String uuid = player.getUuid().toString();
		if (kind.isEmpty() && !SESSION_SEEN.add(uuid + "|" + path + "|" + reason + "|" + state)) {
			return false;
		}
		DataManager data = DataManager.get();
		String nick = player.getGameProfile().getName();
		int level = data.getAdminLevel(player);
		Alert alert = new Alert();
		alert.at = System.currentTimeMillis();
		alert.uuid = uuid;
		alert.nick = nick;
		alert.role = level >= AdminRanks.MOD ? AdminRanks.title(level) : "Игрок";
		alert.kind = kind;
		alert.path = path;
		alert.reason = reason;
		alert.state = state;
		alert.seen.add(uuid);
		alert.joinShown.add(uuid);
		store.alerts.add(alert);
		MinecraftServer server = player.getServer();
		if (server != null) {
			for (ServerPlayerEntity staff : server.getPlayerManager().getPlayerList()) {
				if (receives(staff)) {
					send(staff, alert, false);
					alert.seen.add(staff.getUuid().toString());
				}
			}
		}
		String what = kind.isEmpty() ? "клиент сообщил о подозрительном файле " + path : "подозрение: " + kind;
		String line = what + (state.isBlank() ? "" : " (" + state + ")") + " — " + reason + " · записал: " + WRITER;
		ActivityLog.player(nick, ActionKind.ANTICHEAT.label + ": " + line, false);
		if (data.isAdmin(player)) {
			ActivityLog.admin(nick, ActionKind.ANTICHEAT.label + ": " + line, false);
		}
		PlayerRecord record = data.getOrCreate(player);
		if (record.anticheatCases == null) {
			record.anticheatCases = new ArrayList<>();
		}
		AntiCheatCase ac = new AntiCheatCase();
		ac.at = alert.at;
		ac.cheat = kind.isEmpty() ? "подозрительный файл" : "подозрение: " + kind;
		ac.verdict = "без наказания — проверить администрации";
		ac.trigger = kind.isEmpty() ? path : reason;
		ac.world = player.getWorld().getRegistryKey().getValue().toString();
		ac.pos = player.getBlockPos().getX() + " " + player.getBlockPos().getY() + " " + player.getBlockPos().getZ();
		ac.mode = player.interactionManager == null ? "?" : player.interactionManager.getGameMode().asString();
		ac.hand = player.getMainHandStack().isEmpty() ? "пусто" : ItemLabels.of(player.getMainHandStack());
		if (kind.isEmpty()) {
			ac.why.add("Тип данных: ЗАЯВЛЕНИЕ КЛИЕНТА, криптографически не доказано.");
			ac.why.add("Файл: " + path + (state.isBlank() ? "" : " (" + state + ")"));
			ac.why.add("Причина: " + reason);
		} else {
			ac.why.add("Античит заметил: " + kind + " — " + reason + ".");
			ac.why.add(STAFF_STATE.equals(state)
					? "Игрок — администратор: автоблокировка к администрации не применяется, поэтому только сообщение."
					: "Автоматическая блокировка выключена в /anticheat, поэтому только сообщение.");
		}
		ac.why.add("Записал предупреждение: " + WRITER + ". Игрок не наказан, решение — за администрацией.");
		ac.why.add("Проверить файлы: /filecheck " + nick);
		record.anticheatCases.add(ac);
		data.saveAll();
		return true;
	}

	private static void deliverPending(ServerPlayerEntity staff) {
		String me = staff.getUuid().toString();
		long now = System.currentTimeMillis();
		boolean curator = DataManager.get().getAdminLevel(staff) >= AdminRanks.CURATOR;
		List<Alert> admins = new ArrayList<>();
		List<Alert> unseen = new ArrayList<>();
		for (Alert a : store.alerts) {
			if (now - a.at > KEEP_UNSEEN_MS) {
				continue;
			}
			if (a.joinShown == null) {
				a.joinShown = new ArrayList<>();
			}
			if (curator && !"Игрок".equals(a.role) && !a.joinShown.contains(me)) {
				admins.add(a);
			} else if (!a.seen.contains(me)) {
				unseen.add(a);
			}
		}
		if (!admins.isEmpty()) {
			Chat.send(staff, Chat.brightRed("[Античит] Читы у администрации с вашего прошлого входа: " + admins.size()));
			int from = Math.max(0, admins.size() - MAX_ON_JOIN);
			for (int i = from; i < admins.size(); i++) {
				send(staff, admins.get(i), true);
			}
			if (from > 0) {
				Chat.send(staff, Chat.red("…и ещё " + from + " более ранних — полный список в /koto <ник>."));
			}
			for (Alert a : admins) {
				a.joinShown.add(me);
				if (!a.seen.contains(me)) {
					a.seen.add(me);
				}
			}
		}
		if (unseen.isEmpty()) {
			if (!admins.isEmpty()) {
				save();
			}
			return;
		}
		Chat.send(staff, Chat.brightRed("[Проверка читов] Пока вас не было — подозрений: " + unseen.size()));
		int from = Math.max(0, unseen.size() - MAX_ON_JOIN);
		for (int i = from; i < unseen.size(); i++) {
			send(staff, unseen.get(i), true);
		}
		if (from > 0) {
			Chat.send(staff, Chat.red("…и ещё " + from + " более ранних — полный список в /koto <ник>."));
		}
		for (Alert a : unseen) {
			a.seen.add(me);
		}
		save();
	}

	private static void send(ServerPlayerEntity staff, Alert a, boolean withTime) {
		boolean player = "Игрок".equals(a.role);
		String who = (player ? "Игрок " : "Администратор (" + a.role + ") ") + a.nick;
		String state = a.state.isBlank() ? "" : " (" + a.state + ")";
		String time = withTime ? " · " + TIME.format(Instant.ofEpochMilli(a.at)) : "";
		if (player) {
			Chat.send(staff, Chat.brightRed("[Проверка читов] " + who + " подозревается в читах" + time));
		} else {
			Chat.send(staff, Chat.brightRed("[Античит] " + who + " — клиент сообщил о признаках чита; нужна ручная проверка" + time));
		}
		Chat.send(staff, Chat.red(a.kind == null || a.kind.isEmpty()
				? "Подозрительный файл: " + a.path + state
				: "Подозрение: " + a.kind + state));
		boolean canCheck = DataManager.get().getAdminLevel(staff) >= AdminRanks.ADMIN;
		Chat.send(staff, Chat.red("Причина: " + a.reason + (canCheck
				? " · проверить: /filecheck " + a.nick + ", /koto " + a.nick
				: " · передайте администратору для проверки")));
	}

	/** Кто получает сообщения: вся администрация с модератора, кроме блогера. */
	private static boolean receives(ServerPlayerEntity player) {
		DataManager data = DataManager.get();
		if (data == null) {
			return false;
		}
		int level = data.getAdminLevel(player);
		return level >= AdminRanks.MOD && level != AdminRanks.BLOGGER;
	}

	private static void trim() {
		while (store.alerts.size() > MAX_ALERTS) {
			store.alerts.remove(0);
		}
	}
}
