package ru.admincontrol.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.data.ClanRecord;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.ModSettings;
import ru.admincontrol.data.PlayerRecord;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class ClanService {
	private static final Pattern NAME = Pattern.compile("^[A-Za-zА-Яа-яЁё0-9_]{3,16}$");
	private static final Map<UUID, Invite> INVITES = new ConcurrentHashMap<>();

	private ClanService() {
	}

	private static final class Invite {
		String clanId;
		long expireAt;
	}

	public static boolean enabled() {
		return PlayFeatures.s().clansEnabled;
	}

	public static String nameOf(String id) {
		ClanRecord clan = find(id);
		return clan == null || clan.name == null || clan.name.isBlank() ? "—" : clan.name;
	}

	public static ClanRecord lookup(String query) {
		return byName(query);
	}

	/** Ошибка «клан не найден»; если введён ник игрока — прямо говорит, что нужно название клана. */
	public static String notFound(String query) {
		String q = query == null ? "" : query.trim();
		DataManager data = DataManager.get();
		PlayerRecord record = data == null || q.isEmpty() ? null : data.findByNick(q).orElse(null);
		if (record == null) {
			return "Клан «" + q + "» не найден. Укажите название клана.";
		}
		ClanRecord own = find(record.clanId);
		return "«" + record.nickname + "» — это ник игрока, а нужно название клана."
				+ (own != null ? " Его клан: «" + own.name + "»." : " Игрок не состоит в клане.");
	}

	public static int treasuryCap(ClanRecord clan) {
		ModSettings s = PlayFeatures.s();
		int home = Math.max(0, s.clanHomePrice);
		int bank = Math.max(home, s.clanBankPrice);
		if (clan == null) {
			return home;
		}
		if (clan.bankLevel > 0) {
			int level = Math.max(1, clan.treasuryLevel);
			return Math.max(1, bank) * (level + 1);
		}
		if (clan.homeBought) {
			return bank;
		}
		return home;
	}

	public static boolean onTerritory(ClanRecord clan, ServerPlayerEntity player) {
		if (clan == null || clan.home == null || player == null || !clan.homeBought) {
			return false;
		}
		PlayerRecord fake = new PlayerRecord();
		fake.home = clan.home;
		fake.homeSize = ClanBuildings.territorySize(clan);
		HomeProtection.Box box = HomeProtection.box(fake, 512);
		if (box == null) {
			return false;
		}
		String dim = player.getWorld().getRegistryKey().getValue().toString();
		if (box.dimension != null && !box.dimension.isBlank() && !dim.equalsIgnoreCase(box.dimension)) {
			return false;
		}
		var pos = player.getBlockPos();
		return pos.getX() >= box.minX && pos.getX() <= box.maxX
				&& pos.getY() >= box.minY && pos.getY() <= box.maxY
				&& pos.getZ() >= box.minZ && pos.getZ() <= box.maxZ;
	}

	private static String nameBlocked(String raw) {
		if (raw == null || !NAME.matcher(raw).matches()) {
			return "Название: 3–16 букв, цифр или _.";
		}
		if (DataManager.get() != null && DataManager.get().nickTakenByPlayer(raw)) {
			return "Нельзя назвать клан ником игрока, который заходил на сервер.";
		}
		return null;
	}

	public static List<String> inspectLines(ClanRecord clan) {
		List<String> lines = new ArrayList<>();
		if (clan == null) {
			lines.add("Клан не найден.");
			return lines;
		}
		lines.add("Клан «" + clan.name + "»");
		lines.add("Глава: " + (clan.ownerNick == null || clan.ownerNick.isBlank() ? "—" : clan.ownerNick));
		lines.add("Казна: " + Money.wc(clan.treasury) + " / " + Money.wc(treasuryCap(clan)));
		lines.add("Ратуша: ур. " + Math.max(0, clan.hallLevel));
		lines.add("Банк: ур. " + Math.max(0, clan.bankLevel));
		lines.add("Участники: " + String.join(", ", memberLabels(clan)));
		lines.add("Дом: " + (clan.home == null ? "нет" : clan.home.coordsLabel()));
		lines.add("Приват дома: " + (clan.homePrivate ? "да" : "нет"));
		lines.add("Склад: ур. " + Math.max(0, clan.warehouseLevel));
		List<String> items = warehouseItemLines(clan);
		if (items.isEmpty()) {
			lines.add("Склад пуст");
		} else {
			lines.add("Склад предметы:");
			lines.addAll(items);
		}
		return lines;
	}

	public static List<String> warehouseItemLines(ClanRecord clan) {
		List<String> out = new ArrayList<>();
		SimpleInventory inv = warehouseInv(clan);
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			String id = Registries.ITEM.getId(stack.getItem()).toString();
			String name = stack.getName().getString().replace('\n', ' ').replace('\r', ' ');
			out.add("  [" + i + "] " + name + " × " + stack.getCount() + " (" + id + ")");
		}
		return out;
	}

	public static SimpleInventory warehouseInv(ClanRecord clan) {
		int slots = warehouseSlots(clan);
		SimpleInventory inv = new SimpleInventory(Math.max(9, slots));
		if (clan != null) {
			StackIo.fill(inv, clan.warehouse, inv.size());
		}
		return inv;
	}

	public static int warehouseSlots(ClanRecord clan) {
		int level = clan == null ? 0 : Math.max(0, clan.warehouseLevel);
		if (level <= 0) {
			return 9;
		}
		return PlayFeatures.s().clanWarehouseSlots(level);
	}

	public static int dumpClan(ServerCommandSource source, ClanRecord clan) {
		if (source == null || clan == null) {
			return 0;
		}
		String name = clan.name == null || clan.name.isBlank() ? "?" : clan.name;
		source.sendFeedback(() -> Chat.gold("=== Клан " + name + " ==="), false);
		source.sendFeedback(() -> Text.literal("ACCLAN begin " + name), false);
		dumpField(source, "name", name);
		dumpField(source, "owner", clan.ownerNick == null || clan.ownerNick.isBlank() ? "-" : clan.ownerNick);
		dumpField(source, "treasury", String.valueOf(clan.treasury));
		dumpField(source, "members", clan.memberNicks == null || clan.memberNicks.isEmpty()
				? "-" : String.join(",", clan.memberNicks));
		dumpField(source, "home", clan.home == null ? "-" : clan.home.coordsLabel());
		dumpField(source, "homePrivate", clan.homePrivate ? "1" : "0");
		dumpField(source, "warehouseLevel", String.valueOf(Math.max(0, clan.warehouseLevel)));
		source.sendFeedback(() -> Text.literal("ACCLAN end"), false);
		ChatDump.lines(source, inspectLines(clan));
		return dumpWarehouse(source, clan);
	}

	public static int dumpWarehouse(ServerCommandSource source, ClanRecord clan) {
		if (source == null || clan == null) {
			return 0;
		}
		String name = clan.name == null || clan.name.isBlank() ? "?" : clan.name;
		source.sendFeedback(() -> Chat.gold("=== Склад клана " + name + " ==="), false);
		source.sendFeedback(() -> Text.literal("ACWH begin " + name), false);
		SimpleInventory inv = warehouseInv(clan);
		boolean any = false;
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			any = true;
			int slot = i;
			String id = Registries.ITEM.getId(stack.getItem()).toString();
			String itemName = stack.getName().getString().replace('\n', ' ').replace('\r', ' ');
			int count = stack.getCount();
			source.sendFeedback(() -> Text.literal("ACWH slot=" + slot + " id=" + id + " count=" + count + " name=" + itemName), false);
			source.sendFeedback(() -> Chat.gray("  [" + slot + "] " + itemName + " × " + count + " (" + id + ")"), false);
		}
		if (!any) {
			source.sendFeedback(() -> Text.literal("ACWH empty"), false);
			source.sendFeedback(() -> Chat.gray("Склад пуст."), false);
		}
		source.sendFeedback(() -> Text.literal("ACWH end"), false);
		return 1;
	}

	private static void dumpField(ServerCommandSource source, String key, String value) {
		String safe = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
		source.sendFeedback(() -> Text.literal("ACCLAN " + key + "=" + safe), false);
	}

	public static ClanRecord of(ServerPlayerEntity player) {
		if (player == null || DataManager.get() == null) {
			return null;
		}
		return of(DataManager.get().getOrCreate(player));
	}

	public static ClanRecord of(PlayerRecord record) {
		return record == null ? null : find(record.clanId);
	}

	public static void forceDetach(PlayerRecord record) {
		if (record == null || DataManager.get() == null) {
			return;
		}
		ClanRecord clan = of(record);
		if (clan == null && record.uuid != null && !record.uuid.isBlank()) {
			for (ClanRecord candidate : clans()) {
				if (candidate.memberUuids != null && candidate.memberUuids.stream()
						.anyMatch(id -> id != null && id.equalsIgnoreCase(record.uuid))) {
					clan = candidate;
					break;
				}
			}
		}
		if (clan == null) {
			record.clanId = "";
			return;
		}
		final ClanRecord target = clan;
		UUID inviteId = record.uuidOrNull();
		if (inviteId != null) {
			INVITES.remove(inviteId);
		}
		boolean owner = record.uuid != null && record.uuid.equalsIgnoreCase(target.ownerUuid);
		if (owner) {
			for (PlayerRecord other : DataManager.get().records()) {
				if (target.id.equals(other.clanId)) {
					other.clanId = "";
				}
			}
			clans().removeIf(c -> target.id.equals(c.id));
		} else {
			removeMember(target, record.uuid, record.nickname);
			if (target.memberFlags != null && record.uuid != null) {
				target.memberFlags.entrySet().removeIf(e -> e.getKey() != null && e.getKey().equalsIgnoreCase(record.uuid));
			}
			record.clanId = "";
		}
	}

	public static ClanRecord find(String id) {
		if (id == null || id.isBlank() || DataManager.get() == null) {
			return null;
		}
		List<ClanRecord> clans = clans();
		for (ClanRecord clan : clans) {
			if (id.equals(clan.id)) {
				return clan;
			}
		}
		return null;
	}

	public static List<ClanRecord> clans() {
		ModSettings s = PlayFeatures.s();
		if (s.clans == null) {
			s.clans = new ArrayList<>();
		}
		return s.clans;
	}

	public static ClanRecord byName(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		for (ClanRecord clan : clans()) {
			if (name.equalsIgnoreCase(clan.name)) {
				return clan;
			}
		}
		return null;
	}

	public static List<String> names() {
		List<String> names = new ArrayList<>();
		for (ClanRecord clan : clans()) {
			if (clan.name != null && !clan.name.isBlank()) {
				names.add(clan.name);
			}
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	public static String donate(ServerPlayerEntity player, String clanName, int amount) {
		ClanRecord clan = byName(clanName);
		if (clan == null) {
			return notFound(clanName);
		}
		if (amount <= 0) {
			return "Укажите сумму больше 0.";
		}
		if (!Economy.takeCurrency(player, amount, "перевод в клан " + clan.name)) {
			return "Недостаточно WC. Нужно " + Money.wc(amount) + ".";
		}
		int cap = treasuryCap(clan);
		if (clan.treasury + amount > cap) {
			Economy.giveCurrency(player, amount, "возврат: казна клана полна");
			return "Казна клана вмещает максимум " + Money.wc(cap) + ".";
		}
		clan.treasury += amount;
		DataManager.get().markDirty();
		ClanLog.action(clan, player, "донат в казну +" + Money.wc(amount) + " → " + Money.wc(clan.treasury));
		return null;
	}

	public static String create(ServerPlayerEntity player, String raw) {
		if (!enabled()) {
			return "Кланы выключены.";
		}
		if (player == null || DataManager.get() == null) {
			return "Только игрок.";
		}
		String blocked = nameBlocked(raw);
		if (blocked != null) {
			return blocked;
		}
		PlayerRecord record = DataManager.get().getOrCreate(player);
		if (of(record) != null) {
			return "Вы уже в клане.";
		}
		for (ClanRecord clan : clans()) {
			if (raw.equalsIgnoreCase(clan.name)) {
				return "Такое название уже занято.";
			}
		}
		int price = Math.max(0, PlayFeatures.s().clanCreatePrice);
		if (!Economy.takeCurrency(player, price, "создание клана")) {
			return "Нужно " + Money.wc(price) + ".";
		}
		ClanRecord clan = new ClanRecord();
		clan.id = UUID.randomUUID().toString();
		clan.name = raw;
		clan.ownerUuid = player.getUuid().toString();
		clan.ownerNick = player.getGameProfile().getName();
		clan.memberUuids.add(clan.ownerUuid);
		clan.memberNicks.add(clan.ownerNick);
		clans().add(clan);
		record.clanId = clan.id;
		DataManager.get().saveAll();
		HomeProtection.refresh();
		refreshBoards(player.getServer(), clan);
		ClanLog.action(clan, player, "создал клан");
		return null;
	}

	public static String invite(ServerPlayerEntity owner, String nick) {
		if (!enabled()) {
			return "Кланы выключены.";
		}
		ClanRecord clan = of(owner);
		if (clan == null) {
			return "Вы не в клане.";
		}
		if (!hasFlag(clan, owner, ClanRecord.INVITE)) {
			return "Нет доступа: приглашать в клан.";
		}
		int max = Math.max(2, PlayFeatures.s().clanMaxMembers);
		if (clan.memberUuids.size() >= max) {
			return "Клан полон (" + max + ").";
		}
		ServerPlayerEntity target = CommandUtil.findOnline(owner.getServer(), nick).orElse(null);
		if (target == null) {
			return "Игрок не в сети.";
		}
		PlayerRecord rec = DataManager.get().getOrCreate(target);
		if (of(rec) != null) {
			return "Игрок уже в клане.";
		}
		Invite invite = new Invite();
		invite.clanId = clan.id;
		invite.expireAt = System.currentTimeMillis() + 120_000L;
		INVITES.put(target.getUuid(), invite);
		ClanLog.action(clan, owner, "пригласил " + target.getGameProfile().getName());
		Chat.send(target, Chat.gold(owner.getGameProfile().getName() + " приглашает в клан «"
				+ clan.name + "». Откройте /clan и нажмите «Принять»."));
		return null;
	}

	public static String accept(ServerPlayerEntity player) {
		if (!enabled()) {
			return "Кланы выключены.";
		}
		Invite invite = INVITES.remove(player.getUuid());
		if (invite == null || invite.expireAt < System.currentTimeMillis()) {
			return "Нет приглашения.";
		}
		ClanRecord clan = find(invite.clanId);
		if (clan == null) {
			return "Клан уже не существует.";
		}
		PlayerRecord record = DataManager.get().getOrCreate(player);
		if (of(record) != null) {
			return "Вы уже в клане.";
		}
		int max = Math.max(2, PlayFeatures.s().clanMaxMembers);
		if (clan.memberUuids.size() >= max) {
			return "Клан полон.";
		}
		clan.memberUuids.add(player.getUuid().toString());
		clan.memberNicks.add(player.getGameProfile().getName());
		record.clanId = clan.id;
		DataManager.get().saveAll();
		HomeProtection.refresh();
		refreshBoards(player.getServer(), clan);
		ClanLog.action(clan, player, "вступил в клан");
		return null;
	}

	public static String leave(ServerPlayerEntity player) {
		ClanRecord clan = of(player);
		if (clan == null) {
			return "Вы не в клане.";
		}
		if (ClanWarService.fightingClan(clan.id)) {
			return "Во время битвы кланов нельзя выйти из клана.";
		}
		PlayerRecord record = DataManager.get().getOrCreate(player);
		if (player.getUuid().toString().equalsIgnoreCase(clan.ownerUuid)) {
			return disband(player);
		}
		removeMember(clan, player.getUuid().toString(), player.getGameProfile().getName());
		record.clanId = "";
		DataManager.get().saveAll();
		HomeProtection.refresh();
		refreshBoards(player.getServer(), clan);
		BalanceBoard.refresh(player, true);
		ClanLog.action(clan, player, "покинул клан");
		return null;
	}

	public static String disband(ServerPlayerEntity owner) {
		ClanRecord clan = of(owner);
		if (clan == null || !owner.getUuid().toString().equalsIgnoreCase(clan.ownerUuid)) {
			return "Распустить может только глава.";
		}
		if (ClanWarService.fightingClan(clan.id)) {
			return "Во время битвы кланов нельзя распустить клан.";
		}
		for (PlayerRecord record : DataManager.get().records()) {
			if (clan.id.equals(record.clanId)) {
				record.clanId = "";
			}
		}
		clans().removeIf(c -> clan.id.equals(c.id));
		ClanLog.action(clan, owner, "распустил клан");
		DataManager.get().saveAll();
		HomeProtection.refresh();
		MinecraftServer server = owner.getServer();
		if (server != null) {
			for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
				BalanceBoard.refresh(p, true);
			}
		}
		return null;
	}

	public static String setHome(ServerPlayerEntity owner) {
		ClanRecord clan = of(owner);
		if (clan == null || !isOwner(owner)) {
			return "Дом клана ставит глава.";
		}
		return "Перестановка дома недоступна.";
	}

	public static String rename(ServerPlayerEntity owner, String raw) {
		ClanRecord clan = of(owner);
		if (clan == null || !isOwner(owner)) {
			return "Переименовать может только глава.";
		}
		String blocked = nameBlocked(raw);
		if (blocked != null) {
			return blocked;
		}
		for (ClanRecord other : clans()) {
			if (other != clan && raw.equalsIgnoreCase(other.name)) {
				return "Такое название уже занято.";
			}
		}
		clan.name = raw;
		DataManager.get().saveAll();
		HomeProtection.refresh();
		refreshBoards(owner.getServer(), clan);
		ClanNametags.refreshAll(owner.getServer());
		return null;
	}

	public static String kick(ServerPlayerEntity owner, String nick) {
		ClanRecord clan = of(owner);
		String err = manageError(owner, clan, nick);
		if (err != null) {
			return err;
		}
		if (ClanWarService.fightingClan(clan.id)) {
			return "Во время битвы кланов нельзя выгонять из клана.";
		}
		PlayerRecord rec = DataManager.get().findByNick(nick).orElse(null);
		String uuid = rec == null ? "" : rec.uuid;
		if (uuid.isBlank() && clan.memberUuids != null && clan.memberNicks != null) {
			for (int i = 0; i < clan.memberNicks.size() && i < clan.memberUuids.size(); i++) {
				if (nick.equalsIgnoreCase(clan.memberNicks.get(i))) {
					uuid = clan.memberUuids.get(i);
					break;
				}
			}
		}
		removeMember(clan, uuid, nick);
		if (rec != null && clan.id.equals(rec.clanId)) {
			rec.clanId = "";
		}
		DataManager.get().saveAll();
		HomeProtection.refresh();
		refreshBoards(owner.getServer(), clan);
		ClanLog.action(clan, owner, "исключил " + nick);
		ServerPlayerEntity online = CommandUtil.findOnline(owner.getServer(), nick).orElse(null);
		if (online != null) {
			BalanceBoard.refresh(online, true);
			Chat.send(online, Chat.yellow("Вас исключили из клана «" + clan.name + "»."));
		}
		return null;
	}

	public static boolean isOwner(ServerPlayerEntity player) {
		ClanRecord clan = of(player);
		return clan != null && player.getUuid().toString().equalsIgnoreCase(clan.ownerUuid);
	}

	public static boolean hasInvite(ServerPlayerEntity player) {
		if (player == null) {
			return false;
		}
		Invite invite = INVITES.get(player.getUuid());
		return invite != null && invite.expireAt >= System.currentTimeMillis() && find(invite.clanId) != null;
	}

	public static boolean canSpawnAtClan(ServerPlayerEntity player) {
		ClanRecord clan = of(player);
		return clan != null && clan.home != null && clan.homeBought
				&& (isOwner(player) || hasFlag(clan, player, ClanRecord.TP));
	}

	private static String homeError(ServerPlayerEntity player) {
		if (ClanWarService.jailed(player)) {
			return ClanWarService.jailMessage();
		}
		ClanRecord clan = of(player);
		if (clan == null) {
			return "Вы не в клане.";
		}
		if (clan.home == null) {
			return "У клана нет дома.";
		}
		if (!hasFlag(clan, player, ClanRecord.TP) && !isOwner(player)) {
			return "Нет доступа к телепорту дома клана.";
		}
		return null;
	}

	/** Телепорт на дом клана по команде / из меню — после 5 секунд без урона. */
	public static String requestHome(ServerPlayerEntity player) {
		String err = homeError(player);
		if (err != null) {
			return err;
		}
		TeleportWarmup.start(player, "дом клана", () -> {
			String late = goHome(player);
			Chat.send(player, late == null ? Chat.green("Вы дома клана.") : Chat.red(late));
		});
		return null;
	}

	public static String goHome(ServerPlayerEntity player) {
		String err = homeError(player);
		if (err != null) {
			return err;
		}
		ClanRecord clan = of(player);
		ServerPlayerEntity self = player;
		WorldGate.allow(() -> {
			var world = SpawnService.resolveWorld(self.getServer(), clan.home.dimension);
			self.teleport(world, clan.home.x, clan.home.y, clan.home.z, clan.home.yaw, clan.home.pitch);
		});
		ClanLog.action(clan, player, "телепорт на дом/спавн клана " + clan.home.coordsLabel());
		return null;
	}

	public static String deposit(ServerPlayerEntity player, int amount) {
		ClanRecord clan = of(player);
		if (clan == null) {
			return "Вы не в клане.";
		}
		if (ClanWarService.treasuryLocked(clan, player)) {
			return "Во время битвы казна вашего клана закрыта: ядро сломано.";
		}
		if (!hasFlag(clan, player, ClanRecord.DEPOSIT)) {
			return "Нет доступа: класть WC в казну.";
		}
		if (amount <= 0) {
			return "Укажите сумму больше 0.";
		}
		if (!Economy.takeCurrency(player, amount, "казна клана " + clan.name)) {
			return "Недостаточно WC.";
		}
		int cap = treasuryCap(clan);
		if (clan.treasury + amount > cap) {
			Economy.giveCurrency(player, amount, "возврат: казна клана полна");
			return "Казна клана вмещает максимум " + Money.wc(cap) + ".";
		}
		clan.treasury += amount;
		DataManager.get().markDirty();
		ClanLog.action(clan, player, "казна +" + Money.wc(amount) + " → " + Money.wc(clan.treasury));
		return null;
	}

	public static boolean isMember(ClanRecord clan, ServerPlayerEntity player) {
		if (clan == null || player == null) {
			return false;
		}
		String uuid = player.getUuid().toString();
		String nick = player.getGameProfile().getName();
		if (uuid.equalsIgnoreCase(clan.ownerUuid) || nick.equalsIgnoreCase(clan.ownerNick)) {
			return true;
		}
		if (clan.memberUuids != null) {
			for (String id : clan.memberUuids) {
				if (uuid.equalsIgnoreCase(id)) {
					return true;
				}
			}
		}
		if (clan.memberNicks != null) {
			for (String name : clan.memberNicks) {
				if (nick.equalsIgnoreCase(name)) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean hasFlag(ClanRecord clan, ServerPlayerEntity player, int flag) {
		return (flags(clan, player) & flag) != 0;
	}

	public static String chat(ServerPlayerEntity player, String text) {
		ClanRecord clan = of(player);
		if (clan == null) {
			return "Вы не в клане.";
		}
		if (!hasFlag(clan, player, ClanRecord.CHAT)) {
			return "Нет доступа: писать в чат клана.";
		}
		if (text == null || text.isBlank()) {
			return "Напишите сообщение.";
		}
		ClanLog.action(clan, player, "чат: " + text);
		String line = "[" + clan.name + "] " + player.getGameProfile().getName() + ": " + text;
		if (player.getServer() == null) {
			return null;
		}
		for (ServerPlayerEntity online : player.getServer().getPlayerManager().getPlayerList()) {
			if (isMember(clan, online)) {
				Chat.send(online, Chat.gold(line));
			}
		}
		return null;
	}

	public static String buyHome(ServerPlayerEntity owner) {
		ClanRecord clan = of(owner);
		if (clan == null || !hasFlag(clan, owner, ClanRecord.SPEND)) {
			return "Нет доступа: использовать казну клана.";
		}
		if (clan.homeBought) {
			return "Дом клана уже куплен. Перестановка недоступна.";
		}
		int price = Math.max(0, PlayFeatures.s().clanHomePrice);
		if (clan.treasury < price) {
			return "В казне нужно " + Money.wc(price) + ".";
		}
		clan.treasury -= price;
		clan.homeBought = true;
		clan.hallLevel = Math.max(1, clan.hallLevel);
		clan.home = TpaService.here(owner);
		clan.home.name = "clan";
		clan.homeSize = ClanBuildings.territorySize(clan);
		ClanBuildings.rebuildAll(owner.getServerWorld(), clan);
		DataManager.get().saveAll();
		HomeProtection.refresh();
		ClanLog.action(clan, owner, "купил и поставил дом клана " + clan.home.coordsLabel()
				+ ", казна " + Money.wc(clan.treasury));
		return null;
	}

	public static String upgradeHall(ServerPlayerEntity owner) {
		ClanRecord clan = of(owner);
		if (clan == null || !hasFlag(clan, owner, ClanRecord.SPEND)) {
			return "Нет доступа: использовать казну клана.";
		}
		if (!clan.homeBought || clan.home == null) {
			return "Сначала купите дом клана.";
		}
		int next = Math.max(1, clan.hallLevel) + 1;
		int price = Math.max(1, PlayFeatures.s().clanHomePrice) * next;
		if (clan.treasury < price) {
			return "В казне нужно " + Money.wc(price) + ".";
		}
		clan.treasury -= price;
		clan.hallLevel = next;
		ClanLog.action(clan, owner, "ратуша ур. " + next + ", казна " + Money.wc(clan.treasury));
		clan.homeSize = ClanBuildings.territorySize(clan);
		ClanBuildings.rebuildAll(owner.getServerWorld(), clan);
		DataManager.get().saveAll();
		HomeProtection.refresh();
		return null;
	}

	public static String buyBank(ServerPlayerEntity owner) {
		ClanRecord clan = of(owner);
		if (clan == null || !hasFlag(clan, owner, ClanRecord.SPEND)) {
			return "Нет доступа: использовать казну клана.";
		}
		if (!clan.homeBought || clan.home == null) {
			return "Сначала купите дом клана.";
		}
		if (clan.bankLevel > 0) {
			int next = clan.bankLevel + 1;
			int price = Math.max(1, PlayFeatures.s().clanBankPrice) * next;
			if (clan.treasury < price) {
				return "В казне нужно " + Money.wc(price) + ".";
			}
			clan.treasury -= price;
			clan.bankLevel = next;
			ClanLog.action(clan, owner, "банк ур. " + next + ", казна " + Money.wc(clan.treasury));
			ClanBuildings.rebuildAll(owner.getServerWorld(), clan);
			DataManager.get().saveAll();
			return null;
		}
		int price = Math.max(0, PlayFeatures.s().clanBankPrice);
		if (clan.treasury < price) {
			return "В казне нужно " + Money.wc(price) + ".";
		}
		clan.treasury -= price;
		clan.bankLevel = 1;
		ClanLog.action(clan, owner, "построил банк, казна " + Money.wc(clan.treasury));
		clan.treasuryLevel = Math.max(1, clan.treasuryLevel);
		ClanBuildings.rebuildAll(owner.getServerWorld(), clan);
		DataManager.get().saveAll();
		HomeProtection.refresh();
		return null;
	}

	public static String upgradeTreasury(ServerPlayerEntity owner) {
		ClanRecord clan = of(owner);
		if (clan == null || !hasFlag(clan, owner, ClanRecord.SPEND)) {
			return "Нет доступа: использовать казну клана.";
		}
		if (clan.bankLevel <= 0) {
			return "Сначала купите банк клана.";
		}
		int next = Math.max(1, clan.treasuryLevel) + 1;
		int price = Math.max(1, PlayFeatures.s().clanBankPrice) * Math.max(1, clan.treasuryLevel);
		if (clan.treasury < price) {
			return "В казне нужно " + Money.wc(price) + ".";
		}
		clan.treasury -= price;
		clan.treasuryLevel = next;
		ClanLog.action(clan, owner, "лимит казны ур. " + next + ", казна " + Money.wc(clan.treasury));
		DataManager.get().saveAll();
		return null;
	}

	public static String buyPrivate(ServerPlayerEntity owner) {
		ClanRecord clan = of(owner);
		if (clan == null || !hasFlag(clan, owner, ClanRecord.SPEND)) {
			return "Нет доступа: использовать казну клана.";
		}
		if (clan.home == null || !clan.homeBought) {
			return "Сначала купите дом клана.";
		}
		if (ClanWarService.fightingClan(clan.id)) {
			return "Во время битвы кланов приват дома включить нельзя.";
		}
		if (clan.homePrivate) {
			return "Приват уже куплен.";
		}
		int price = Math.max(0, PlayFeatures.s().clanHomePrivatePrice);
		if (clan.treasury < price) {
			return "В казне нужно " + Money.wc(price) + ".";
		}
		clan.treasury -= price;
		clan.homePrivate = true;
		ClanLog.action(clan, owner, "купил приват дома, казна " + Money.wc(clan.treasury));
		DataManager.get().saveAll();
		HomeProtection.refresh();
		return null;
	}

	public static String upgradeWarehouse(ServerPlayerEntity owner) {
		ClanRecord clan = of(owner);
		if (clan == null || !hasFlag(clan, owner, ClanRecord.SPEND)) {
			return "Нет доступа: использовать казну клана.";
		}
		if (!clan.homeBought || clan.home == null) {
			return "Сначала купите дом клана.";
		}
		int next = clan.warehouseLevel + 1;
		int price = PlayFeatures.s().clanWarehousePrice(next);
		if (clan.treasury < price) {
			return "В казне нужно " + Money.wc(price) + ".";
		}
		clan.treasury -= price;
		clan.warehouseLevel = next;
		ClanBuildings.rebuildAll(owner.getServerWorld(), clan);
		DataManager.get().saveAll();
		HomeProtection.refresh();
		ClanLog.action(clan, owner, "склад ур. " + next + ", казна " + Money.wc(clan.treasury));
		return null;
	}

	public static String resetClan(String name) {
		ClanRecord clan = byName(name);
		if (clan == null) {
			return notFound(name);
		}
		if (ClanWarService.fightingClan(clan.id)) {
			return "Сначала остановите битву кланов (/cwarstop).";
		}
		for (PlayerRecord record : DataManager.get().records()) {
			if (clan.id.equals(record.clanId)) {
				record.clanId = "";
			}
		}
		clans().removeIf(c -> clan.id.equals(c.id));
		DataManager.get().saveAll();
		HomeProtection.refresh();
		return null;
	}

	public static String withdraw(ServerPlayerEntity player, int amount) {
		ClanRecord clan = of(player);
		if (clan == null) {
			return "Вы не в клане.";
		}
		if (ClanWarService.treasuryLocked(clan, player)) {
			return "Во время битвы казна вашего клана закрыта: ядро сломано.";
		}
		if (!hasFlag(clan, player, ClanRecord.WITHDRAW)) {
			return "Нет доступа: брать WC из казны.";
		}
		if (amount <= 0 || clan.treasury < amount) {
			return "Недостаточно в казне.";
		}
		clan.treasury -= amount;
		Economy.giveCurrency(player, amount, "снятие с казны клана");
		DataManager.get().markDirty();
		ClanLog.action(clan, player, "казна −" + Money.wc(amount) + " → " + Money.wc(clan.treasury));
		return null;
	}

	public static int flags(ClanRecord clan, ServerPlayerEntity player) {
		if (clan == null || player == null) {
			return 0;
		}
		return memberFlags(clan, player.getUuid().toString(), player.getGameProfile().getName());
	}

	public static int memberFlags(ClanRecord clan, String uuid, String nick) {
		return rankFlags(clan, rankOf(clan, uuid, nick));
	}

	public static int rankOf(ClanRecord clan, ServerPlayerEntity player) {
		return player == null ? 0 : rankOf(clan, player.getUuid().toString(), player.getGameProfile().getName());
	}

	public static int rankOf(ClanRecord clan, String uuid, String nick) {
		if (clan == null) {
			return 0;
		}
		if ((uuid != null && !uuid.isBlank() && uuid.equalsIgnoreCase(clan.ownerUuid))
				|| (nick != null && !nick.isBlank() && nick.equalsIgnoreCase(clan.ownerNick))) {
			return ClanRecord.LEADER;
		}
		if (!listed(clan, uuid, nick)) {
			return 0;
		}
		ensureRanks(clan);
		Integer rank = null;
		if (uuid != null && !uuid.isBlank()) {
			rank = clan.memberRanks.get(uuid.toLowerCase(java.util.Locale.ROOT));
		}
		if (rank == null && nick != null && !nick.isBlank()) {
			rank = clan.memberRanks.get(nick.toLowerCase(java.util.Locale.ROOT));
		}
		return rank == null ? 1 : Math.max(1, Math.min(ClanRecord.DEPUTY, rank));
	}

	public static int rankFlags(ClanRecord clan, int rank) {
		if (clan == null || rank <= 0) {
			return 0;
		}
		if (rank >= ClanRecord.LEADER) {
			return ClanRecord.ALL;
		}
		ensureRanks(clan);
		return clan.rankFlags.get(rank) & ClanRecord.ALL;
	}

	/** Заполняет доступы уровней по умолчанию и переносит старые личные доступы в уровни. */
	private static void ensureRanks(ClanRecord clan) {
		boolean changed = false;
		if (clan.memberRanks == null) {
			clan.memberRanks = new java.util.LinkedHashMap<>();
			changed = true;
		}
		if (clan.rankFlags == null) {
			clan.rankFlags = new ArrayList<>();
		}
		while (clan.rankFlags.size() < ClanRecord.LEADER) {
			clan.rankFlags.add(ClanRecord.defaultFlags(clan.rankFlags.size()));
			changed = true;
		}
		if (clan.memberFlags != null && !clan.memberFlags.isEmpty()) {
			for (Map.Entry<String, Integer> old : clan.memberFlags.entrySet()) {
				int bits = old.getValue() == null ? 0 : old.getValue();
				int rank = (bits & (ClanRecord.WITHDRAW | ClanRecord.WH_TAKE)) != 0 ? 3
						: (bits & ClanRecord.BUILD) != 0 ? 2 : 1;
				if (old.getKey() != null && rank > 1) {
					clan.memberRanks.putIfAbsent(old.getKey().toLowerCase(java.util.Locale.ROOT), rank);
				}
			}
			clan.memberFlags.clear();
			changed = true;
		}
		if (changed && DataManager.get() != null) {
			DataManager.get().markDirty();
		}
	}

	private static String memberKey(ClanRecord clan, String nick) {
		if (clan.memberNicks != null && clan.memberUuids != null) {
			for (int i = 0; i < clan.memberNicks.size() && i < clan.memberUuids.size(); i++) {
				String id = clan.memberUuids.get(i);
				if (nick.equalsIgnoreCase(clan.memberNicks.get(i)) && id != null && !id.isBlank()) {
					return id.toLowerCase(java.util.Locale.ROOT);
				}
			}
		}
		PlayerRecord rec = DataManager.get().findByNick(nick).orElse(null);
		if (rec != null && rec.uuid != null && !rec.uuid.isBlank()) {
			return rec.uuid.toLowerCase(java.util.Locale.ROOT);
		}
		return nick.toLowerCase(java.util.Locale.ROOT);
	}

	private static String memberUuid(ClanRecord clan, String nick) {
		if (clan.memberNicks != null && clan.memberUuids != null) {
			for (int i = 0; i < clan.memberNicks.size() && i < clan.memberUuids.size(); i++) {
				if (nick.equalsIgnoreCase(clan.memberNicks.get(i))) {
					return clan.memberUuids.get(i);
				}
			}
		}
		return "";
	}

	private static boolean listed(ClanRecord clan, String uuid, String nick) {
		if (clan.memberUuids != null && uuid != null && !uuid.isBlank()) {
			for (String id : clan.memberUuids) {
				if (uuid.equalsIgnoreCase(id)) {
					return true;
				}
			}
		}
		return inClan(clan, nick);
	}

	private static boolean inClan(ClanRecord clan, String nick) {
		if (clan.memberNicks != null && nick != null) {
			for (String name : clan.memberNicks) {
				if (nick.equalsIgnoreCase(name)) {
					return true;
				}
			}
		}
		return false;
	}

	public static int rankOfNick(ClanRecord clan, String nick) {
		return rankOf(clan, memberUuid(clan, nick), nick);
	}

	/** Проверка для повышения/понижения/кика: возвращает текст ошибки или null. */
	private static String manageError(ServerPlayerEntity actor, ClanRecord clan, String nick) {
		if (clan == null) {
			return "Вы не в клане.";
		}
		if (!hasFlag(clan, actor, ClanRecord.MANAGE)) {
			return "Нет доступа: повышать, понижать и выгонять.";
		}
		if (nick == null || nick.isBlank() || !inClan(clan, nick)) {
			return "Игрока нет в клане.";
		}
		if (nick.equalsIgnoreCase(actor.getGameProfile().getName())) {
			return "Нельзя менять свой уровень.";
		}
		if (rankOfNick(clan, nick) >= rankOf(clan, actor)) {
			return "Можно управлять только участниками ниже вашего уровня.";
		}
		return null;
	}

	public static String promote(ServerPlayerEntity actor, String nick) {
		ClanRecord clan = of(actor);
		String err = manageError(actor, clan, nick);
		if (err != null) {
			return err;
		}
		int now = rankOfNick(clan, nick);
		int next = now + 1;
		if (next > ClanRecord.DEPUTY) {
			return "Выше заместителя повысить нельзя.";
		}
		if (next >= rankOf(clan, actor)) {
			return "Нельзя повысить до своего уровня или выше.";
		}
		clan.memberRanks.put(memberKey(clan, nick), next);
		DataManager.get().saveAll();
		HomeProtection.refresh();
		ClanLog.action(clan, actor, "повысил " + nick + ": " + ClanRecord.rankLabel(now) + " → " + ClanRecord.rankLabel(next));
		notifyMember(actor, nick, Chat.green("Вас повысили в клане «" + clan.name + "» до: " + ClanRecord.rankLabel(next) + "."));
		return null;
	}

	public static String demote(ServerPlayerEntity actor, String nick) {
		ClanRecord clan = of(actor);
		String err = manageError(actor, clan, nick);
		if (err != null) {
			return err;
		}
		int now = rankOfNick(clan, nick);
		if (now <= 1) {
			return "Игрок уже на 1 уровне.";
		}
		int next = now - 1;
		clan.memberRanks.put(memberKey(clan, nick), next);
		DataManager.get().saveAll();
		HomeProtection.refresh();
		ClanLog.action(clan, actor, "понизил " + nick + ": " + ClanRecord.rankLabel(now) + " → " + ClanRecord.rankLabel(next));
		notifyMember(actor, nick, Chat.yellow("Вас понизили в клане «" + clan.name + "» до: " + ClanRecord.rankLabel(next) + "."));
		return null;
	}

	/** Переключить доступ уровня. Можно менять только уровни ниже своего и только те доступы, что есть у вас. */
	public static String toggleRankFlag(ServerPlayerEntity actor, int rank, int flag) {
		ClanRecord clan = of(actor);
		if (clan == null) {
			return "Вы не в клане.";
		}
		if (!hasFlag(clan, actor, ClanRecord.GRANT)) {
			return "Нет доступа: настраивать доступы.";
		}
		int mine = rankOf(clan, actor);
		if (rank < 1 || rank >= mine || rank > ClanRecord.DEPUTY) {
			return "Можно настраивать только уровни ниже вашего.";
		}
		if ((flag & ClanRecord.ALL) == 0 || Integer.bitCount(flag) != 1) {
			return "Неизвестный доступ.";
		}
		if (!hasFlag(clan, actor, flag)) {
			return "Нельзя выдать доступ, которого нет у вас.";
		}
		ensureRanks(clan);
		int now = clan.rankFlags.get(rank);
		boolean on = (now & flag) == 0;
		clan.rankFlags.set(rank, on ? now | flag : now & ~flag);
		DataManager.get().saveAll();
		HomeProtection.refresh();
		ClanLog.action(clan, actor, (on ? "выдал" : "забрал") + " доступ «" + ClanRecord.flagLabel(flag) + "» у: "
				+ ClanRecord.rankLabel(rank));
		return null;
	}

	private static void notifyMember(ServerPlayerEntity actor, String nick, Text text) {
		if (actor.getServer() == null) {
			return;
		}
		CommandUtil.findOnline(actor.getServer(), nick).ifPresent(online -> {
			Chat.send(online, text);
			ru.admincontrol.screen.ClanScreenHandler.refreshOpen(online);
		});
	}

	public static List<String> info(ServerPlayerEntity player) {
		List<String> lines = new ArrayList<>();
		ClanRecord clan = of(player);
		if (clan == null) {
			lines.add("Вы не в клане. /clan create <имя>");
			return lines;
		}
		lines.add("Клан «" + clan.name + "»");
		lines.add("Глава: " + clan.ownerNick);
		lines.add("Казна: " + Money.wc(clan.treasury));
		lines.add("Ваш уровень: " + ClanRecord.rankLabel(rankOf(clan, player)));
		lines.add("Участники: " + String.join(", ", memberLabels(clan)));
		lines.add("Дом: " + (clan.home == null ? "нет" : clan.home.coordsLabel()));
		return lines;
	}

	private static List<String> memberLabels(ClanRecord clan) {
		List<String> out = new ArrayList<>();
		if (clan.memberNicks != null) {
			for (String nick : clan.memberNicks) {
				out.add(nick + " (" + ClanRecord.rankLabel(rankOfNick(clan, nick)) + ")");
			}
		}
		if (out.isEmpty()) {
			out.add("—");
		}
		return out;
	}

	public static List<String> list() {
		List<String> lines = new ArrayList<>();
		if (clans().isEmpty()) {
			lines.add("Кланов пока нет.");
			return lines;
		}
		for (ClanRecord clan : clans()) {
			int n = clan.memberNicks == null ? 0 : clan.memberNicks.size();
			lines.add("«" + clan.name + "»  глава " + clan.ownerNick + "  (" + n + ")");
		}
		return lines;
	}

	private static void removeMember(ClanRecord clan, String uuid, String nick) {
		if (clan.memberRanks != null) {
			clan.memberRanks.keySet().removeIf(key -> key != null
					&& ((uuid != null && key.equalsIgnoreCase(uuid)) || (nick != null && key.equalsIgnoreCase(nick))));
		}
		if (clan.memberUuids != null && uuid != null && !uuid.isBlank()) {
			clan.memberUuids.removeIf(id -> id != null && id.equalsIgnoreCase(uuid));
		}
		if (clan.memberNicks != null) {
			clan.memberNicks.removeIf(name -> name != null && name.equalsIgnoreCase(nick));
		}
	}

	private static void refreshBoards(MinecraftServer server, ClanRecord clan) {
		if (server == null) {
			return;
		}
		ClanNametags.refreshAll(server);
		if (clan == null) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (isMember(clan, player)) {
				BalanceBoard.refresh(player, true);
			}
		}
	}

	public static void purgeInvites() {
		long now = System.currentTimeMillis();
		Iterator<Map.Entry<UUID, Invite>> it = INVITES.entrySet().iterator();
		while (it.hasNext()) {
			if (it.next().getValue().expireAt < now) {
				it.remove();
			}
		}
	}
}
