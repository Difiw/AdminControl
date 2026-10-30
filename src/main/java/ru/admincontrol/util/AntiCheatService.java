package ru.admincontrol.util;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import ru.admincontrol.command.ModerationService;
import ru.admincontrol.data.ActionKind;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.AntiCheatCase;
import ru.admincontrol.data.AntiCheatRule;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.ModSettings;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.mixin.InteractionManagerMiningAccessor;
import ru.admincontrol.net.ModPackets;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AntiCheatService {
	private static final Map<UUID, Deque<Long>> CLICKS = new ConcurrentHashMap<>();
	private static final Map<UUID, Deque<Long>> PACKETS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_PACKET = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_CLICK = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_BREAK = new ConcurrentHashMap<>();
	private static final Map<UUID, Deque<Long>> CLICK_INTERVALS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> CLICK_PUNISH_AT = new ConcurrentHashMap<>();
	private static final Map<UUID, Deque<Long>> HIDDEN_ORES = new ConcurrentHashMap<>();
	private static final Map<String, Long> INCIDENTS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_ACTION = new ConcurrentHashMap<>();
	private static final Set<UUID> XRAY_PACK = ConcurrentHashMap.newKeySet();
	/** /actest: ловит администратора как игрока, без таймера — пока сам не выключит или не кикнет. */
	private static final Set<UUID> TEST_ACTIVE = ConcurrentHashMap.newKeySet();
	/** Уже сообщили про выключенный чит в текущем /actest (чтобы не спамить). */
	private static final Set<UUID> TEST_IDLE_TOLD = ConcurrentHashMap.newKeySet();
	/** Ступени автокликера в тесте — {ступень, время последнего срабатывания}; в запись игрока не пишутся. */
	private static final Map<UUID, long[]> TEST_CLICK = new ConcurrentHashMap<>();
	private static int testScanTick;

	private AntiCheatService() {
	}

	public static void click(ServerPlayerEntity player) {
		if (breakingBlocks(player)) {
			noteAction(player);
			return;
		}
		recordClick(player);
	}

	public static void clickStartBreak(ServerPlayerEntity player) {
		noteAction(player);
		if (player != null) {
			LAST_BREAK.put(player.getUuid(), System.currentTimeMillis());
		}
	}

	public static void clickAttack(ServerPlayerEntity player) {
		if (breakingBlocks(player)) {
			noteAction(player);
			return;
		}
		recordClick(player);
	}

	private static void recordClick(ServerPlayerEntity player) {
		noteAction(player);
		if (player == null || DataManager.get() == null || NpcService.isNpc(player) || AuthService.pending(player)) {
			return;
		}
		if (player.isSpectator()) {
			return;
		}
		ModSettings s = settings();
		if ((!s.anticheatEnabled && !s.fileScanEnabled) || s.anticheatClicksPerSec <= 0) {
			return;
		}
		long now = System.currentTimeMillis();
		UUID id = player.getUuid();
		Deque<Long> raw = PACKETS.computeIfAbsent(id, k -> new ArrayDeque<>());
		raw.addLast(now);
		while (!raw.isEmpty() && now - raw.peekFirst() > 1200L) {
			raw.removeFirst();
		}
		int packets = 0;
		for (long t : raw) {
			if (now - t <= 1000L) {
				packets++;
			}
		}
		Long prevPacket = LAST_PACKET.put(id, now);
		boolean holding = prevPacket != null && now - prevPacket < 80L;
		if (!holding) {
			Long prevClick = LAST_CLICK.get(id);
			if (prevClick == null || now - prevClick >= 80L) {
				LAST_CLICK.put(id, now);
				if (prevClick != null && now - prevClick <= 250L) {
					Deque<Long> intervals = CLICK_INTERVALS.computeIfAbsent(id, k -> new ArrayDeque<>());
					intervals.addLast(now - prevClick);
					while (intervals.size() > 32) intervals.removeFirst();
				}
				Deque<Long> clicks = CLICKS.computeIfAbsent(id, k -> new ArrayDeque<>());
				clicks.addLast(now);
				while (!clicks.isEmpty() && now - clicks.peekFirst() > 1200L) {
					clicks.removeFirst();
				}
			}
		}
		Long punishedAt = CLICK_PUNISH_AT.get(id);
		if (punishedAt != null && now - punishedAt < 1200L) {
			return;
		}
		int limit = Math.max(25, s.anticheatClicksPerSec);
		int cps = 0;
		Deque<Long> clicks = CLICKS.get(id);
		if (clicks != null) {
			for (long t : clicks) {
				if (now - t <= 1000L) {
					cps++;
				}
			}
		}
		boolean stable = roboticPattern(CLICK_INTERVALS.get(id));
		boolean lagging = ServerTps.tps() < 17.0 || ServerTps.mspt() > 70.0;
		boolean spam = !lagging && packets >= 40 && cps > limit;
		boolean overLimit = cps > limit;
		if (!spam && !overLimit && !stable) {
			return;
		}
		String why;
		String flag;
		int shown;
		if (stable) {
			why = "стабильный машинный ритм 24+ кликов (не только превышение CPS)";
			flag = "pattern";
			shown = cps;
		} else if (spam) {
			why = packets + " пакетов в секунду (спам кликов, не зажатие)";
			flag = "spam";
			shown = packets;
		} else {
			why = cps + " отдельных кликов/с при лимите " + limit;
			flag = "limit";
			shown = cps;
		}
		CLICK_PUNISH_AT.put(id, now);
		if (clicks != null) {
			clicks.clear();
		}
		raw.clear();
		Deque<Long> intervals = CLICK_INTERVALS.get(id);
		if (intervals != null) intervals.clear();
		if (exempt(player)) {
			FileScanService.suspect(player, "автокликер", why, FileScanService.STAFF_STATE);
			return;
		}
		if (!s.anticheatEnabled && !testing(player)) {
			FileScanService.suspect(player, "автокликер", why);
			return;
		}
		punishClick(player, why, shown, limit, flag);
	}

	private static boolean roboticPattern(Deque<Long> intervals) {
		if (intervals == null || intervals.size() < 24) return false;
		double sum = 0.0;
		long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
		for (long v : intervals) { sum += v; min = Math.min(min, v); max = Math.max(max, v); }
		double mean = sum / intervals.size();
		if (mean < 80.0 || mean > 180.0 || max - min > 10L) return false;
		double variance = 0.0;
		for (long v : intervals) { double d = v - mean; variance += d * d; }
		return Math.sqrt(variance / intervals.size()) < 2.5;
	}

	public static void onOreBreak(ServerPlayerEntity player, World world, BlockPos pos, BlockState state) {
		noteAction(player);
		if (!playing(player) || world == null || pos == null || state == null) {
			return;
		}
		ModSettings s = settings();
		if (!s.anticheatXray || (!s.anticheatEnabled && !s.fileScanEnabled)) {
			return;
		}
		if (!valuableOre(state)) {
			return;
		}
		if (!hidden(world, pos)) {
			return;
		}
		long now = System.currentTimeMillis();
		int windowMs = Math.max(30, s.anticheatXrayWindowSec) * 1000;
		int need = Math.max(12, s.anticheatXrayHidden);
		Deque<Long> window = HIDDEN_ORES.computeIfAbsent(player.getUuid(), id -> new ArrayDeque<>());
		window.addLast(now);
		while (!window.isEmpty() && now - window.peekFirst() > windowMs) {
			window.removeFirst();
		}
		int n = window.size();
		if (n < need) {
			return;
		}
		window.clear();
		String detail = "скрытая руда ×" + n + " за " + (windowMs / 1000) + " с, "
				+ ItemLabels.of(state.getBlock()) + " " + pos.toShortString();
		if (exempt(player)) {
			FileScanService.suspect(player, "X-Ray (скрытая руда)", detail, FileScanService.STAFF_STATE);
			return;
		}
		if (!s.anticheatEnabled && !testing(player)) {
			FileScanService.suspect(player, "X-Ray (скрытая руда)", detail);
			return;
		}
		punishCheat(player, detail);
	}

	public static void onXrayResourcePack(ServerPlayerEntity player, String file) {
		if (player == null || DataManager.get() == null || NpcService.isNpc(player) || AuthService.pending(player)) {
			return;
		}
		if (player.isSpectator()) {
			return;
		}
		String detail = file == null ? "" : file.replace('\n', ' ').replace('\r', ' ').trim();
		if (detail.length() > 200) {
			detail = detail.substring(0, 200);
		}
		boolean idle = detail.toLowerCase(Locale.ROOT).startsWith("выкл:");
		if (idle) {
			if (!testing(player)) {
				return;
			}
			String what = detail.substring(detail.indexOf(':') + 1).trim();
			if (what.isEmpty()) {
				what = detail;
			}
			if (TEST_IDLE_TOLD.add(player.getUuid())) {
				Chat.send(player, Chat.gold("[/actest] Найден чит, но он выключен: " + what));
				Chat.send(player, Chat.yellow("Пока чит выключен — вас не трогаем. Включите чит — сразу кик и сброс /actest."));
			}
			return;
		}
		ModSettings s = settings();
		if (!s.anticheatXray || (!s.anticheatEnabled && !s.fileScanEnabled && !testing(player))) {
			return;
		}
		if (ownModReport(detail) || !xrayFileName(detail)) {
			return;
		}
		String kind = cheatKind(detail);
		String punishDetail = kind + ": " + detail;
		// Отчёт о файлах/паках прислан самим клиентом: это сигнал для проверки, не доказательство для бана.
		if (testing(player)) Chat.send(player, Chat.yellow("[/actest] Клиент сообщил: " + punishDetail + ". Записано для ручной проверки."));
		FileScanService.suspect(player, kind, detail, testing(player) ? "тест: неподтверждённый отчёт клиента" : FileScanService.OFF_STATE);
		return;
	}

	private static String cheatKind(String detail) {
		if (overlayHit(detail)) {
			return "подсветка структур";
		}
		if (detail == null) {
			return "X-Ray";
		}
		String n = detail.toLowerCase(Locale.ROOT);
		if (n.contains("fullbright") || n.contains("яркост") || n.contains("gamma")) {
			return "Fullbright";
		}
		if (n.contains("ресурс") || n.contains("пак") || n.contains("прозрач") || n.contains("модель")) {
			return "X-Ray ресурс-пак";
		}
		if (n.contains("mod") || n.contains("fabric") || n.contains("xray-mod")) {
			return "X-Ray мод";
		}
		return "X-Ray";
	}

	private static boolean overlayHit(String detail) {
		if (detail == null) {
			return false;
		}
		String n = detail.toLowerCase(Locale.ROOT);
		return n.contains("подсветк") || n.contains("minihud") || n.contains("bbor")
				|| n.contains("bounding");
	}

	private static void punishCheat(ServerPlayerEntity player, String detail) {
		DataManager data = DataManager.get();
		PlayerRecord record = data.getOrCreate(player);
		boolean overlay = overlayHit(detail);
		boolean pack = detail != null && detail.toLowerCase(Locale.ROOT).contains("ресурс");
		String key = overlay ? AntiCheatRules.OVERLAY : pack ? AntiCheatRules.XRAY_PACK : AntiCheatRules.XRAY;
		AntiCheatRule rule = AntiCheatRules.rule(key);
		String verdict = AntiCheatRules.verdict(rule);
		String title = overlay ? "подсветка" : "X-Ray";
		String cheatName = overlay ? "подсветка структур" : "x-ray";
		String banReason = cheatName + (detail == null || detail.isEmpty() ? "" : " (" + detail + ")");
		if (testing(player)) {
			testKick(player, record, title, banReason);
			return;
		}
		AdminNotify.toAdmins(player.getServer(), null, Chat.red("[Античит] "
				+ player.getGameProfile().getName() + " — чит: " + detail + " → " + verdict));
		AntiCheatCase ac = baseCase(player, record);
		ac.cheat = cheatName;
		ac.verdict = verdict;
		ac.trigger = detail == null ? "" : detail;
		if (overlay) {
			ac.why.add("Клиент Admin Control увидел включённую подсветку мира, не имя файла мода.");
			ac.why.add("MiniHUD: рамки структур, чанки слизней, свет цифрами, сфера деспавна. BBOR: рамки деревень/особняков и других структур в прогруженных чанках.");
			ac.why.add("Это облегчает поиск структур — на сервере запрещено.");
			ac.why.add("Находка: " + detail);
		} else if (pack) {
			ac.why.add("Клиент Admin Control проверил загруженные текстуры и модели блоков.");
			ac.why.add("Ванильный камень/земля/сланец непрозрачные. У игрока они просвечивают, а руда остаётся плотной — так устроен X-Ray ресурс-пак.");
			ac.why.add("Находка клиента: " + detail);
		} else {
			ac.why.add("Сервер посчитал добычу скрытой руды (руда со всех сторон закрыта непрозрачными блоками).");
			ac.why.add("Игрок ломал ценную руду, которую с поверхности не видно. Порог срабатывания задаётся в /anticheat.");
			ac.why.add("Факт: " + detail);
		}
		ac.why.add("Решение: " + verdict + " (наказание настраивается в /anticheat).");
		storeAndLog(player, record, ac);
		punish(player, record, rule, banReason, title);
	}

	private static void punishClick(ServerPlayerEntity player, String detail, int cps, int limit, String flag) {
		DataManager data = DataManager.get();
		PlayerRecord record = data.getOrCreate(player);
		String reason = "подозрение в автокликере";
		long now = System.currentTimeMillis();
		int window = Math.max(30, settings().anticheatRepeatSeconds) * 1000;
		if (testing(player)) {
			long[] t = TEST_CLICK.computeIfAbsent(player.getUuid(), k -> new long[2]);
			if (t[1] <= 0 || now - t[1] >= window) {
				t[0] = 0;
			}
			t[0]++;
			t[1] = now;
			AntiCheatRule rule = AntiCheatRules.rule(clickKey((int) t[0]));
			String banReason = reason + " (ступень " + t[0] + ", игроку было бы: " + AntiCheatRules.verdict(rule) + ")";
			testKick(player, record, "автокликер", banReason);
			return;
		}
		if (record.lastAutoclickWarnMs <= 0 || now - record.lastAutoclickWarnMs >= window) {
			record.anticheatStrikes = 0;
		}
		record.anticheatStrikes++;
		record.lastAutoclickWarnMs = now;
		AntiCheatRule rule = AntiCheatRules.rule(clickKey(record.anticheatStrikes));
		String verdict = AntiCheatRules.verdict(rule);
		AdminNotify.toAdmins(player.getServer(), null, Chat.red("[Античит] "
				+ player.getGameProfile().getName() + " — автокликер: " + detail + " → " + verdict));
		AntiCheatCase ac = baseCase(player, record);
		ac.cheat = "автокликер";
		ac.verdict = verdict;
		ac.trigger = detail == null ? "" : detail;
		ac.cps = cps;
		ac.limit = limit;
		ac.strikes = record.anticheatStrikes;
		ac.why.add("Считаются отдельные нажатия мыши. Ломание блоков и зажатая ЛКМ кликом не считаются.");
		ac.why.add("Проверка начинается только если отдельных кликов больше 25 в секунду.");
		ac.why.add("Сейчас за 1 с: " + cps + ("spam".equals(flag) ? " пакетов" : " кликов") + ", лимит сервера: " + limit + ".");
		if ("spam".equals(flag)) {
			ac.why.add("Признак: пакетов больше, чем даёт зажатие ЛКМ (зажатие ≈ 20 взмахов/с). Это спам кликов.");
		} else {
			ac.why.add("Признак: слишком много отдельных нажатий (отпустил и нажал снова), выше лимита из /anticheat.");
		}
		ac.why.add("Ступени наказания (/anticheat): 1 — " + AntiCheatRules.verdict(AntiCheatRules.rule(AntiCheatRules.CLICK1))
				+ ", 2 — " + AntiCheatRules.verdict(AntiCheatRules.rule(AntiCheatRules.CLICK2))
				+ ", 3 и далее — " + AntiCheatRules.verdict(AntiCheatRules.rule(AntiCheatRules.CLICK3))
				+ ". Сейчас ступень " + record.anticheatStrikes + " → " + verdict + ".");
		storeAndLog(player, record, ac);
		data.saveAll();
		punish(player, record, rule, reason, "автокликер");
	}

	private static String clickKey(int strike) {
		return strike <= 1 ? AntiCheatRules.CLICK1 : strike == 2 ? AntiCheatRules.CLICK2 : AntiCheatRules.CLICK3;
	}

	/** Выдать наказание из /anticheat; title — коротко, за что (для экрана игрока). */
	private static void punish(ServerPlayerEntity player, PlayerRecord record, AntiCheatRule rule, String reason, String title) {
		switch (rule.kind) {
			case AntiCheatRules.NONE -> {
			}
			case AntiCheatRules.NOTICE -> {
				alert(player, "АНТИЧИТ", title + ": предупреждение");
				Chat.send(player, Chat.brightRed("Античит: " + reason + ". Это предупреждение — повтор накажется строже."));
				player.sendMessage(Chat.brightRed("Античит: " + title + ". Предупреждение."), true);
			}
			case AntiCheatRules.WARN -> {
				alert(player, "АНТИЧИТ", "Варн: " + title);
				ModerationService.autoWarn(player, record, reason);
			}
			case AntiCheatRules.KICK -> {
				alert(player, "АНТИЧИТ", "Кик: " + title);
				ModerationService.autoKick(player, record, reason);
			}
			case AntiCheatRules.JAIL -> {
				alert(player, "АНТИЧИТ", "Jail: " + title);
				ModerationService.autoJail(player, record, rule.minutes, reason);
			}
			case AntiCheatRules.BAN -> {
				alert(player, "АНТИЧИТ", "Бан: " + title);
				ModerationService.autoBan(player, record, rule.days, reason);
			}
			case AntiCheatRules.PERMBAN -> {
				alert(player, "АНТИЧИТ", "Пермбан: " + title);
				ModerationService.autoPermBan(player, record, reason);
			}
			default -> {
			}
		}
	}

	/** Сервером доказанное нарушение. Клиентские заявления сюда не попадают. */
	public static void confirmed(ServerPlayerEntity player, String ruleKey, String cheat, String trigger,
			java.util.List<String> facts) {
		if (player == null || DataManager.get() == null || NpcService.isNpc(player)) return;
		long now = System.currentTimeMillis();
		String dedup = player.getUuid() + "|" + ruleKey + "|" + trigger;
		Long previous = INCIDENTS.put(dedup, now);
		if (previous != null && now - previous < 30_000L) return;
		PlayerRecord record = DataManager.get().getOrCreate(player);
		AntiCheatRule rule = AntiCheatRules.rule(ruleKey);
		boolean staff = exempt(player);
		String verdict = staff ? "без автонаказания: администрация" : AntiCheatRules.verdict(rule);
		AntiCheatCase ac = baseCase(player, record);
		ac.cheat = cheat;
		ac.trigger = trigger == null ? "" : trigger;
		ac.verdict = verdict;
		ac.why.add("Тип данных: ФАКТ, проверен сервером.");
		if (facts != null) ac.why.addAll(facts);
		storeAndLog(player, record, ac);
		AdminNotify.toAdmins(player.getServer(), null, Chat.red("[Античит/факт] "
				+ player.getGameProfile().getName() + " — " + cheat + ": " + trigger + " → " + verdict));
		if (!staff && settings().anticheatEnabled) punish(player, record, rule, trigger, cheat);
	}

	/**
	 * /actest: сначала гасит тест (чтобы после кика не зациклиться), потом кикает
	 * с той же причиной, что ушла бы в бан за чит.
	 */
	private static void testKick(ServerPlayerEntity player, PlayerRecord record, String title, String reason) {
		if (player == null) {
			return;
		}
		clearTest(player);
		Chat.send(player, Chat.yellow("/actest выключен — найден активный чит («" + title + "»)."));
		AdminNotify.playerAction(player, ActionKind.ANTICHEAT,
				"/actest: " + title + " → кик (тест сброшен): " + reason, false);
		alert(player, "/ACTEST", "Кик: " + title);
		if (record == null) {
			record = DataManager.get().getOrCreate(player);
		}
		ModerationService.autoKick(player, record, reason);
	}

	/** Сбросить /actest без кика. */
	public static void clearTest(ServerPlayerEntity player) {
		if (player == null) {
			return;
		}
		UUID id = player.getUuid();
		boolean was = TEST_ACTIVE.remove(id);
		TEST_CLICK.remove(id);
		TEST_IDLE_TOLD.remove(id);
		if (was) {
			sendTestMode(player, false);
		}
	}

	public static boolean testing(ServerPlayerEntity player) {
		return player != null && TEST_ACTIVE.contains(player.getUuid());
	}

	/** Для GUI: 1 — /actest включён, 0 — выключен. */
	public static int testSecondsLeft(ServerPlayerEntity player) {
		return testing(player) ? 1 : 0;
	}

	/** Включить или выключить /actest; true — теперь включён. */
	public static boolean toggleTest(ServerPlayerEntity admin) {
		UUID id = admin.getUuid();
		if (testing(admin)) {
			clearTest(admin);
			Chat.send(admin, Chat.yellow("/actest выключен."));
			return false;
		}
		TEST_ACTIVE.add(id);
		TEST_CLICK.remove(id);
		TEST_IDLE_TOLD.remove(id);
		XRAY_PACK.remove(id);
		HIDDEN_ORES.remove(id);
		LAST_ACTION.put(id, System.currentTimeMillis());
		sendTestMode(admin, true);
		Chat.send(admin, Chat.gold("/actest включён: без таймера. Пока читы выключены — не трогаем. "
				+ "Если чит только установлен/выключен — напишем в чат. Если чит активен — сразу кик и сброс /actest."));
		Chat.send(admin, Chat.yellow("Повторный /actest — выключить. Работает и в одиночном мире."));
		return true;
	}

	private static void sendTestMode(ServerPlayerEntity player, boolean on) {
		if (player == null || player.networkHandler == null) {
			return;
		}
		if (!ServerPlayNetworking.canSend(player, ModPackets.XRAY_TEST)) {
			return;
		}
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBoolean(on);
		ServerPlayNetworking.send(player, ModPackets.XRAY_TEST, buf);
	}

	private static AntiCheatCase baseCase(ServerPlayerEntity player, PlayerRecord record) {
		AntiCheatCase ac = new AntiCheatCase();
		ac.at = System.currentTimeMillis();
		if (player != null) {
			ac.world = player.getWorld().getRegistryKey().getValue().toString();
			ac.pos = player.getBlockPos().getX() + " " + player.getBlockPos().getY() + " " + player.getBlockPos().getZ();
			GameMode mode = player.interactionManager == null ? null : player.interactionManager.getGameMode();
			ac.mode = mode == null ? "?" : mode.asString();
			ac.hand = player.getMainHandStack() == null || player.getMainHandStack().isEmpty()
					? "пусто"
					: ItemLabels.of(player.getMainHandStack());
		}
		if (record != null) {
			ac.strikes = record.anticheatStrikes;
		}
		return ac;
	}

	private static void storeAndLog(ServerPlayerEntity player, PlayerRecord record, AntiCheatCase ac) {
		if (record.anticheatCases == null) {
			record.anticheatCases = new java.util.ArrayList<>();
		}
		record.anticheatCases.add(ac);
		DataManager.get().saveAll();
		for (String line : reportLines(ac, player == null ? record.nickname : player.getGameProfile().getName())) {
			if (player != null) {
				AdminNotify.playerAction(player, ActionKind.ANTICHEAT, line, false);
			} else {
				ActivityLog.player(record.nickname, ActionKind.ANTICHEAT.label + ": " + line, false);
			}
		}
	}

	public static java.util.List<ActivityLog.Line> kotoLines(PlayerRecord record) {
		java.util.List<ActivityLog.Line> out = new java.util.ArrayList<>();
		if (record == null) {
			return out;
		}
		if (record.anticheatCases != null) {
			for (int i = record.anticheatCases.size() - 1; i >= 0; i--) {
				AntiCheatCase ac = record.anticheatCases.get(i);
				if (ac == null) {
					continue;
				}
				for (String line : reportLines(ac, record.nickname)) {
					out.add(new ActivityLog.Line("[" + stamp(ac.at) + "] <" + record.nickname + "> "
							+ ActionKind.ANTICHEAT.label + ": " + line, false, ActionKind.ANTICHEAT));
				}
				out.add(new ActivityLog.Line(" ", true, ActionKind.ANTICHEAT));
			}
		}
		if (out.isEmpty()) {
			for (ActivityLog.Line line : ActivityLog.readPlayer(record.nickname)) {
				if (line != null && line.kind == ActionKind.ANTICHEAT) {
					out.add(line);
				}
			}
		}
		return out;
	}

	private static java.util.List<String> reportLines(AntiCheatCase ac, String nick) {
		java.util.List<String> lines = new java.util.ArrayList<>();
		lines.add("вердикт по " + nick + " — " + ac.cheat + " → " + ac.verdict);
		if (ac.trigger != null && !ac.trigger.isBlank()) {
			lines.add("сработал триггер: " + ac.trigger);
		}
		if (ac.cps > 0) {
			lines.add("CPS " + ac.cps + " при лимите " + ac.limit + ", ступень " + ac.strikes);
		}
		if (ac.world != null && !ac.world.isBlank()) {
			lines.add("мир " + ac.world + ", координаты " + ac.pos + ", режим " + ac.mode + ", в руке " + ac.hand);
		}
		if (ac.why != null) {
			for (String why : ac.why) {
				if (why != null && !why.isBlank()) {
					lines.add(why);
				}
			}
		}
		lines.add("полный разбор: /koto " + nick);
		return lines;
	}

	private static String stamp(long at) {
		if (at <= 0L) {
			return "—";
		}
		return java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
				.withZone(java.time.ZoneId.systemDefault())
				.format(java.time.Instant.ofEpochMilli(at));
	}

	public static void noteAction(ServerPlayerEntity player) {
		if (player == null) {
			return;
		}
		LAST_ACTION.put(player.getUuid(), System.currentTimeMillis());
	}

	public static void tickAfk(MinecraftServer server) {
		if (server == null || DataManager.get() == null) {
			return;
		}
		long now = System.currentTimeMillis();
		// /actest: каждые ~0.5 с просим клиент пересканировать (включение X-Ray посреди сессии).
		if (++testScanTick >= 10) {
			testScanTick = 0;
			for (UUID id : Set.copyOf(TEST_ACTIVE)) {
				ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
				if (p != null) {
					XRAY_PACK.remove(id);
					sendTestMode(p, true);
				}
			}
		}
		ModSettings s = settings();
		if (!s.afkKickEnabled || s.afkMinutes <= 0) {
			return;
		}
		long maxIdle = Math.max(1, s.afkMinutes) * 60_000L;
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!ready(player) || player.networkHandler == null) {
				continue;
			}
			// Во время /actest АФК не трогаем — проверяем только читы.
			if (testing(player)) {
				continue;
			}
			long last = LAST_ACTION.getOrDefault(player.getUuid(), now);
			if (now - last < maxIdle) {
				continue;
			}
			LAST_ACTION.put(player.getUuid(), now);
			AntiCheatRule rule = AntiCheatRules.rule(AntiCheatRules.AFK);
			String verdict = AntiCheatRules.verdict(rule);
			String reason = "АФК " + s.afkMinutes + " мин без действий";
			PlayerRecord record = DataManager.get().getOrCreate(player);
			AdminNotify.playerAction(player, ActionKind.ANTICHEAT, reason + " → " + verdict, false);
			AdminNotify.toAdmins(server, null, Chat.red("[Античит] "
					+ player.getGameProfile().getName() + " — АФК " + s.afkMinutes + " мин → " + verdict + "."));
			if (rule.kind == AntiCheatRules.NONE) {
				continue;
			}
			punish(player, record, rule, reason, "АФК");
		}
	}

	public static void forget(ServerPlayerEntity player) {
		if (player == null) {
			return;
		}
		UUID id = player.getUuid();
		CLICKS.remove(id);
		PACKETS.remove(id);
		LAST_PACKET.remove(id);
		LAST_CLICK.remove(id);
		CLICK_PUNISH_AT.remove(id);
		HIDDEN_ORES.remove(id);
		LAST_ACTION.remove(id);
		XRAY_PACK.remove(id);
		TEST_ACTIVE.remove(id);
		TEST_IDLE_TOLD.remove(id);
		TEST_CLICK.remove(id);
		INCIDENTS.keySet().removeIf(k -> k.startsWith(id.toString() + "|"));
	}

	private static void alert(ServerPlayerEntity player, String big, String small) {
		if (player == null || player.networkHandler == null) {
			return;
		}
		player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 45, 10));
		player.networkHandler.sendPacket(new TitleS2CPacket(
				Text.literal(big).formatted(Formatting.RED, Formatting.BOLD)));
		player.networkHandler.sendPacket(new SubtitleS2CPacket(
				Text.literal(small).formatted(Formatting.YELLOW)));
	}

	private static boolean ownModReport(String raw) {
		if (raw == null || raw.isBlank()) {
			return false;
		}
		String name = raw.toLowerCase(Locale.ROOT).replace('\\', '/');
		return name.contains("admin-control") || name.contains("admin_control")
				|| name.contains("ru/admincontrol") || name.contains("resourcepackxrayscan")
				|| name.contains("cheatoverlayscan");
	}

	private static boolean xrayFileName(String raw) {
		if (raw == null || raw.isBlank()) {
			return false;
		}
		if (ownModReport(raw)) {
			return false;
		}
		String name = raw.toLowerCase(Locale.ROOT).replace('\\', '/').replace(' ', '_');
		if (name.contains("x-ray") || name.contains("x_ray") || name.contains("xray")
				|| name.contains("иксрей") || name.contains("икс-рей") || name.contains("рентген")
				|| name.contains("просвеч") || name.contains("wallhack") || name.contains("oreesp")
				|| name.contains("ore_esp") || name.contains("fullbright") || name.contains("full_bright")
				|| name.contains("seethrough") || name.contains("see_through") || name.contains("see-through")
				|| name.contains("cavefinder") || name.contains("blockesp") || name.contains("orefinder")
				|| name.contains("xray-mod") || name.contains("только_руды") || name.contains("только руды")
				|| name.contains("включён") || name.contains("включен") || name.contains("прозрач")
				|| name.contains("стекл") || name.contains("glass") || name.contains("частица")
				|| name.contains("модель") || name.contains("подсветк") || name.contains("minihud")
				|| name.contains("bbor") || name.contains("bounding") || name.contains("slime")
				|| name.contains("structure") || name.contains("overlay") || name.contains("spawn_chunk")
				|| name.contains("light_level") || name.contains("villages") || name.contains("mansion")
				|| name.contains("яркост") || name.contains("gamma") || name.contains("fullbright")) {
			return true;
		}
		return name.contains("textures/block/") && (name.contains("stone.png")
				|| name.contains("deepslate.png") || name.contains("netherrack.png")
				|| name.contains("dirt.png") || name.contains("cobblestone.png"));
	}

	private static boolean mining(ServerPlayerEntity player) {
		if (player == null || player.interactionManager == null) {
			return false;
		}
		try {
			return ((InteractionManagerMiningAccessor) player.interactionManager).adminControl$isMining();
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static boolean breakingBlocks(ServerPlayerEntity player) {
		if (player == null) {
			return false;
		}
		if (mining(player)) {
			return true;
		}
		Long broke = LAST_BREAK.get(player.getUuid());
		return broke != null && System.currentTimeMillis() - broke < 450L;
	}

	private static boolean ready(ServerPlayerEntity player) {
		return playing(player) && !exempt(player);
	}

	/** Администрацию античит не наказывает, а только сообщает о находке — если в /anticheat не включено «наказывать админов» и нет теста на себе. */
	private static boolean exempt(ServerPlayerEntity player) {
		return staff(player) && !testing(player) && !settings().anticheatPunishStaff;
	}

	private static boolean playing(ServerPlayerEntity player) {
		if (player == null || DataManager.get() == null || NpcService.isNpc(player) || AuthService.pending(player)) {
			return false;
		}
		return !player.isSpectator() && !player.isCreative();
	}

	/** Модератор и выше. */
	private static boolean staff(ServerPlayerEntity player) {
		return DataManager.get().getAdminLevel(player) >= AdminRanks.MOD;
	}

	private static boolean valuableOre(BlockState state) {
		String path = Registries.BLOCK.getId(state.getBlock()).getPath();
		return path.contains("diamond_ore") || path.contains("emerald_ore")
				|| "ancient_debris".equals(path) || path.contains("nether_gold_ore");
	}

	private static boolean hidden(World world, BlockPos pos) {
		int solid = 0;
		for (Direction dir : Direction.values()) {
			BlockState side = world.getBlockState(pos.offset(dir));
			if (side.isAir() || side.isOf(Blocks.WATER) || side.isOf(Blocks.LAVA)
					|| side.isOf(Blocks.CAVE_AIR) || !side.isOpaqueFullCube(world, pos.offset(dir))) {
				continue;
			}
			solid++;
		}
		return solid >= 5;
	}

	private static ModSettings settings() {
		return DataManager.get().settings();
	}
}
