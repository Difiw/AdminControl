package ru.admincontrol.util;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.data.ActionKind;
import ru.admincontrol.data.DataManager;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Правка карты МП разработчиком (/devent): карта строится над ним, он в креативе ломает и ставит блоки,
 * затем публикует карту или сохраняет черновик. Помощники (/dbuild) строят вместе с ним в креативе с пустым
 * инвентарём. Вещи и точки входа всех участников лежат в бэкапах на диске, черновик сохраняется
 * автоматически, а исходные блоки мира под картой — в файле отката на случай падения сервера.
 * Одновременно идёт не больше одной правки и только когда нет МП.
 */
public final class EventMapEditor {
	public static final int MAX_HELPERS = 8;
	public static final int FINISH_DISCARD = 0;
	public static final int FINISH_DRAFT = 1;
	public static final int FINISH_PUBLISH = 2;
	private static final int AUTOSAVE_TICKS = 2 * 60 * 20;
	/** Помощнику на стройке доступны только переписка и справка. */
	private static final Set<String> HELPER_COMMANDS = Set.of("msg", "tell", "w", "r", "m", "list", "help", "ptime");
	private static Session session;

	private EventMapEditor() {
	}

	/** Что было у игрока до стройки: вещи, точка, режим, здоровье. */
	private static final class Spot {
		String inventoryNbt = "";
		String dimension = "";
		double x;
		double y;
		double z;
		float yaw;
		float pitch;
		GameMode mode = GameMode.SURVIVAL;
		float health;
		int food;
		float saturation;

		static Spot of(ServerPlayerEntity p) {
			Spot s = new Spot();
			s.inventoryNbt = InventoryUtil.serialize(p);
			s.dimension = p.getWorld().getRegistryKey().getValue().toString();
			s.x = p.getX();
			s.y = p.getY();
			s.z = p.getZ();
			s.yaw = p.getYaw();
			s.pitch = p.getPitch();
			s.mode = p.interactionManager.getGameMode();
			s.health = p.getHealth();
			s.food = p.getHungerManager().getFoodLevel();
			s.saturation = p.getHungerManager().getSaturationLevel();
			return s;
		}

		boolean vault(ServerPlayerEntity p) {
			return InventoryVault.save(p, InventoryVault.EVENT_MAP, this.inventoryNbt, this.dimension, this.x, this.y,
					this.z, this.yaw, this.pitch, this.mode, this.health, this.food, this.saturation);
		}

		void restore(ServerPlayerEntity p, String label) {
			p.closeHandledScreen();
			InventoryUtil.restore(p, this.inventoryNbt);
			if (p.interactionManager.getGameMode() != this.mode) {
				p.changeGameMode(this.mode);
			}
			ServerWorld world = SpawnService.resolveWorld(p.getServer(), this.dimension);
			TeleportLog.labeled(label, () -> WorldGate.allow(() -> p.teleport(world, this.x, this.y, this.z, this.yaw, this.pitch)));
			p.fallDistance = 0;
			p.setVelocity(0, 0, 0);
			InventoryVault.applyVitals(p, this.health, this.food, this.saturation);
			InventoryVault.clear(p);
		}
	}

	private static final class Session {
		UUID editor;
		String editorName = "";
		String key = "0";
		int base;
		String title = "";
		EventArena arena;
		Spot spot;
		final Map<UUID, String> helperNames = new LinkedHashMap<>();
		final Map<UUID, Spot> helperSpots = new LinkedHashMap<>();
		int ticks;
	}

	public static boolean active() {
		return session != null;
	}

	public static boolean editing(ServerPlayerEntity player) {
		return session != null && player != null && player.getUuid().equals(session.editor);
	}

	public static boolean helper(ServerPlayerEntity player) {
		return session != null && player != null && session.helperSpots.containsKey(player.getUuid());
	}

	/** Разработчик или помощник на стройке карты. */
	public static boolean participant(ServerPlayerEntity player) {
		return editing(player) || helper(player);
	}

	/** Индекс правящегося МП в EventCatalog; -1 — правки нет. */
	public static int type() {
		return session == null ? -1 : EventCatalog.byKey(session.key);
	}

	public static String key() {
		return session == null ? "" : session.key;
	}

	public static String editorName() {
		return session == null ? "" : session.editorName;
	}

	public static int helperCount() {
		return session == null ? 0 : session.helperSpots.size();
	}

	public static List<String> helperNames() {
		return session == null ? List.of() : new ArrayList<>(session.helperNames.values());
	}

	/** Участник стройки правит блок внутри карты: приваты и прочие запреты не мешают. */
	public static boolean allows(ServerPlayerEntity player, BlockPos pos) {
		return participant(player) && pos != null && session.arena.world() == player.getWorld() && session.arena.contains(pos);
	}

	public static boolean spawnBanned(ServerWorld world, Entity entity) {
		Session s = session;
		return s != null && s.arena != null && entity instanceof MobEntity && s.arena.inSpawnBan(world, entity.getBlockPos());
	}

	public static boolean blocksCommand(ServerPlayerEntity player, String name) {
		return helper(player) && name != null && !name.isBlank() && !HELPER_COMMANDS.contains(name);
	}

	/** Эндер-сундук — личное хранилище: креативные вещи помощника не должны туда попасть. */
	public static boolean blocksUse(ServerPlayerEntity player, BlockState state) {
		return helper(player) && state != null && state.isOf(Blocks.ENDER_CHEST);
	}

	/** null — правка началась, иначе причина отказа. */
	public static String start(ServerPlayerEntity dev, int choice) {
		if (dev == null || !EventCatalog.valid(choice)) {
			return "Неизвестное мероприятие.";
		}
		if (session != null) {
			return "Карту «" + session.title + "» уже правит " + session.editorName + ".";
		}
		if (EventService.active()) {
			return "Сейчас идёт мероприятие — правка карт недоступна до его окончания.";
		}
		if (TrainingService.active(dev)) {
			return "Сначала выйдите с тренировки (/off).";
		}
		if (ClanWarService.memberOfWar(dev) || ClanWarService.jailed(dev)) {
			return "Нельзя править карту во время битвы кланов.";
		}
		if (FreezeService.frozen(dev)) {
			return "Вы заморожены.";
		}
		Path restore = EventMaps.restoreFile();
		if (restore == null) {
			return "Нет папки данных мода.";
		}
		Session s = new Session();
		s.editor = dev.getUuid();
		s.editorName = dev.getGameProfile().getName();
		s.key = EventCatalog.key(choice);
		s.base = EventCatalog.base(choice);
		s.title = EventCatalog.name(choice);
		s.spot = Spot.of(dev);
		if (!s.spot.vault(dev)) {
			return "Не удалось сохранить ваш инвентарь на диск — правка отменена.";
		}
		ServerWorld world = (ServerWorld) dev.getWorld();
		s.arena = new EventArena();
		s.arena.mapKey = s.key;
		s.arena.build(world, EventArena.adminStand(world, dev.getBlockPos()), s.base);
		s.arena.coverVolume();
		boolean published = EventMaps.exists(s.key);
		String loaded = published ? "сохранённая версия" : "стандартная карта";
		EventMaps.Snapshot draft = EventMaps.draft(s.key);
		String draftNote = null;
		if (draft != null) {
			if (s.arena.applySnapshot(draft)) {
				loaded = "черновик";
			} else {
				draftNote = "Черновик другого размера (правила МП сменились) — не загружен.";
			}
		}
		if (!EventMaps.write(restore, s.arena.restoreData())) {
			s.arena.sweepDebris();
			s.arena.remove();
			InventoryVault.clear(dev);
			return "Не удалось записать файл отката мира — правка отменена.";
		}
		session = s;
		dev.closeHandledScreen();
		dev.getInventory().clear();
		dev.playerScreenHandler.sendContentUpdates();
		dev.changeGameMode(GameMode.CREATIVE);
		dev.getAbilities().flying = true;
		dev.sendAbilitiesUpdate();
		EventArena a = s.arena;
		TeleportLog.labeled("телепортировался на карту МП (правка)", () ->
				WorldGate.allow(() -> dev.teleport(world, entryX(a), a.floorY(), entryZ(a), dev.getYaw(), 0)));
		dev.fallDistance = 0;
		JournalLog.add(JournalLog.EVENTS, "Правка карты «" + s.title + "» · " + s.editorName + " · " + where(a) + " · " + loaded);
		AdminNotify.playerAction(dev, ActionKind.ADMIN, "начал правку карты МП «" + s.title + "» (" + where(a) + ", " + loaded + ")");
		Chat.send(dev, Chat.gold("Правка карты «" + s.title + "» (" + loaded + "). Вы в креативе: ломайте и ставьте блоки внутри карты."));
		if (draftNote != null) {
			Chat.send(dev, Chat.red(draftNote));
		}
		Chat.send(dev, Chat.yellow("Сохраняются только блоки внутри объёма карты, существа (рамки, стойки, мобы) — нет. "
				+ "Черновик сохраняется сам каждые 2 минуты и при вылете. Помощник: /dbuild <ник>, убрать: /dbuildkick <ник>. "
				+ "Готово — /devent."));
		return null;
	}

	private static double entryX(EventArena a) {
		return a.kind() == 6 ? a.raceSpawnX(0) : a.centerX();
	}

	private static double entryZ(EventArena a) {
		return a.kind() == 6 ? a.raceSpawnZ(0) : a.centerZ();
	}

	public static void tick(MinecraftServer server) {
		Session s = session;
		if (s == null || s.arena == null || s.arena.world() == null) {
			return;
		}
		s.ticks++;
		if (s.ticks % 10 == 0) {
			confineHelpers(server, s);
			EventArena a = s.arena;
			Box box = new Box(a.minX - 16, a.minY - 16, a.minZ - 16, a.maxX + 17, a.maxY + 17, a.maxZ + 17);
			for (ItemEntity item : a.world().getEntitiesByClass(ItemEntity.class, box, e -> true)) {
				item.discard();
			}
		}
		if (s.ticks % AUTOSAVE_TICKS == 0 && EventMaps.saveDraft(s.key, s.arena)) {
			ServerPlayerEntity dev = server.getPlayerManager().getPlayer(s.editor);
			if (dev != null) {
				dev.sendMessage(Chat.gray("Черновик карты «" + s.title + "» сохранён."), true);
			}
		}
	}

	/** Помощник не уходит со стройки: вне объёма карты (или в другом мире) — обратно в центр. */
	private static void confineHelpers(MinecraftServer server, Session s) {
		EventArena a = s.arena;
		for (UUID id : new ArrayList<>(s.helperSpots.keySet())) {
			ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
			if (p == null) {
				continue;
			}
			if (p.interactionManager.getGameMode() != GameMode.CREATIVE) {
				p.changeGameMode(GameMode.CREATIVE);
			}
			boolean inside = p.getWorld() == a.world()
					&& p.getX() >= a.minX - 4 && p.getX() <= a.maxX + 5
					&& p.getZ() >= a.minZ - 4 && p.getZ() <= a.maxZ + 5
					&& p.getY() >= a.minY - 8 && p.getY() <= a.maxY + 8;
			if (!inside) {
				ServerWorld world = a.world();
				TeleportLog.silent(() -> WorldGate.allow(() -> p.teleport(world, entryX(a), a.floorY(), entryZ(a), p.getYaw(), 0)));
				p.fallDistance = 0;
				p.sendMessage(Chat.red("Со стройки карты МП уходить нельзя."), true);
			}
		}
	}

	/** null — помощник добавлен, иначе причина отказа. */
	public static String addHelper(ServerPlayerEntity dev, String nick) {
		if (!editing(dev)) {
			return "Сначала начните правку карты в /devent.";
		}
		Session s = session;
		ServerPlayerEntity target = CommandUtil.findOnline(dev.getServer(), nick).orElse(null);
		if (target == null) {
			return "Игрок «" + nick + "» не в сети.";
		}
		String name = target.getGameProfile().getName();
		if (target.getUuid().equals(dev.getUuid())) {
			return "Вы и так на стройке.";
		}
		if (s.helperSpots.containsKey(target.getUuid())) {
			return name + " уже помогает строить.";
		}
		if (s.helperSpots.size() >= MAX_HELPERS) {
			return "На стройке уже " + MAX_HELPERS + " помощников.";
		}
		if (NpcService.isNpc(target) || AuthService.pending(target)) {
			return name + " сейчас недоступен.";
		}
		if (EventService.member(target) || EventService.isHost(target)) {
			return name + " на мероприятии.";
		}
		if (TrainingService.active(target)) {
			return name + " на тренировке.";
		}
		if (FreezeService.frozen(target)) {
			return name + " заморожен.";
		}
		if (ClanWarService.memberOfWar(target) || ClanWarService.jailed(target)) {
			return name + " участвует в битве кланов.";
		}
		DataManager data = DataManager.get();
		if (data != null && data.findActiveJail(target.getUuid(), name).isPresent()) {
			return name + " в тюрьме.";
		}
		Spot spot = Spot.of(target);
		if (!spot.vault(target)) {
			return "Не удалось сохранить инвентарь " + name + " на диск — он не добавлен.";
		}
		s.helperNames.put(target.getUuid(), name);
		s.helperSpots.put(target.getUuid(), spot);
		target.closeHandledScreen();
		target.stopRiding();
		target.getInventory().clear();
		target.playerScreenHandler.sendContentUpdates();
		target.changeGameMode(GameMode.CREATIVE);
		target.getAbilities().flying = true;
		target.sendAbilitiesUpdate();
		EventArena a = s.arena;
		boolean devInside = dev.getWorld() == a.world() && a.contains(dev.getBlockPos());
		double tx = devInside ? dev.getX() : entryX(a);
		double ty = devInside ? dev.getY() : a.floorY();
		double tz = devInside ? dev.getZ() : entryZ(a);
		ServerWorld world = a.world();
		TeleportLog.labeled("телепортирован на стройку карты МП к " + s.editorName, () ->
				WorldGate.allow(() -> target.teleport(world, tx, ty, tz, dev.getYaw(), dev.getPitch())));
		target.fallDistance = 0;
		Chat.send(target, Chat.gold("Разработчик " + s.editorName + " позвал вас строить карту МП «" + s.title + "»."));
		Chat.send(target, Chat.yellow("Вы в креативе с пустым инвентарём. Ваши вещи и место сохранены и вернутся, когда стройка "
				+ "закончится или вы выйдете с сервера. Из команд доступны только /msg, /r, /list, /help и /ptime."));
		Chat.send(dev, Chat.green(name + " на стройке карты."));
		JournalLog.add(JournalLog.EVENTS, "Стройка карты «" + s.title + "» · " + s.editorName + " позвал " + name);
		AdminNotify.playerAction(dev, ActionKind.ADMIN, "позвал " + name + " строить карту МП «" + s.title + "»");
		return null;
	}

	/** null — помощник убран со стройки, иначе причина отказа. */
	public static String kickHelper(ServerPlayerEntity admin, String nick) {
		Session s = session;
		if (s == null) {
			return "Сейчас никто не правит карту МП.";
		}
		UUID found = null;
		for (Map.Entry<UUID, String> e : s.helperNames.entrySet()) {
			if (e.getValue().equalsIgnoreCase(nick)) {
				found = e.getKey();
				break;
			}
		}
		if (found == null) {
			return "«" + nick + "» не на стройке. Помощники: "
					+ (s.helperNames.isEmpty() ? "нет" : String.join(", ", s.helperNames.values())) + ".";
		}
		String name = s.helperNames.remove(found);
		Spot spot = s.helperSpots.remove(found);
		MinecraftServer server = admin == null ? null : admin.getServer();
		ServerPlayerEntity target = server == null ? null : server.getPlayerManager().getPlayer(found);
		if (target != null && spot != null) {
			spot.restore(target, "убран со стройки карты МП");
			Chat.send(target, Chat.yellow("Вас убрали со стройки карты МП: вещи и место возвращены."));
		}
		String by = admin == null ? "?" : admin.getGameProfile().getName();
		if (server != null) {
			ServerPlayerEntity dev = server.getPlayerManager().getPlayer(s.editor);
			if (dev != null && dev != admin) {
				Chat.send(dev, Chat.yellow(by + " убрал " + name + " со стройки карты."));
			}
		}
		JournalLog.add(JournalLog.EVENTS, "Стройка карты «" + s.title + "» · " + name + " убран (" + by + ")");
		if (admin != null) {
			AdminNotify.playerAction(admin, ActionKind.ADMIN, "убрал " + name + " со стройки карты МП «" + s.title + "»");
		}
		return null;
	}

	/** mode — FINISH_PUBLISH (карта для МП), FINISH_DRAFT (черновик) или FINISH_DISCARD (без сохранения). */
	public static void finish(ServerPlayerEntity dev, int mode) {
		if (!editing(dev)) {
			if (dev != null) {
				Chat.send(dev, Chat.red("Вы сейчас не правите карту МП."));
			}
			return;
		}
		Session s = session;
		String result;
		if (mode == FINISH_PUBLISH) {
			if (EventMaps.save(s.key, s.arena)) {
				EventMaps.deleteDraft(s.key);
				result = "опубликована";
				Chat.send(dev, Chat.green("Карта «" + s.title + "» сохранена: МП теперь запускается на ней."));
			} else {
				EventMaps.saveDraft(s.key, s.arena);
				result = "ошибка записи, оставлен черновик";
				Chat.send(dev, Chat.red("Не удалось записать карту «" + s.title + "» — изменения сохранены в черновик."));
			}
		} else if (mode == FINISH_DRAFT) {
			EventMaps.saveDraft(s.key, s.arena);
			result = "сохранён черновик";
			Chat.send(dev, Chat.green("Черновик карты «" + s.title + "» сохранён. МП пока идёт на прежней карте; "
					+ "продолжить — /devent → «Изменить карту»."));
		} else {
			EventMaps.deleteDraft(s.key);
			result = "выход без сохранения";
			Chat.send(dev, Chat.yellow("Правка карты «" + s.title + "» отменена, изменения и черновик удалены."));
		}
		end(s, dev, "стройка карты закончена");
		JournalLog.add(JournalLog.EVENTS, "Правка карты «" + s.title + "» · " + s.editorName + " · " + result);
		AdminNotify.playerAction(dev, ActionKind.ADMIN, "закончил правку карты МП «" + s.title + "»: " + result);
	}

	public static void onDisconnect(ServerPlayerEntity player) {
		Session s = session;
		if (s == null || player == null) {
			return;
		}
		if (editing(player)) {
			EventMaps.saveDraft(s.key, s.arena);
			end(s, player, "разработчик " + s.editorName + " вышел с сервера");
			JournalLog.add(JournalLog.EVENTS, "Правка карты «" + s.title + "» · " + s.editorName
					+ " · вышел с сервера, черновик сохранён");
			return;
		}
		Spot spot = s.helperSpots.remove(player.getUuid());
		if (spot == null) {
			return;
		}
		String name = s.helperNames.remove(player.getUuid());
		spot.restore(player, "вышел со стройки карты МП (отключение)");
		ServerPlayerEntity dev = player.getServer() == null ? null : player.getServer().getPlayerManager().getPlayer(s.editor);
		if (dev != null) {
			Chat.send(dev, Chat.yellow(name + " вышел с сервера и убран со стройки."));
		}
		JournalLog.add(JournalLog.EVENTS, "Стройка карты «" + s.title + "» · " + name + " вышел с сервера");
	}

	public static void stopAll(MinecraftServer server, String reason) {
		Session s = session;
		if (s == null) {
			return;
		}
		EventMaps.saveDraft(s.key, s.arena);
		ServerPlayerEntity player = server == null ? null : server.getPlayerManager().getPlayer(s.editor);
		end(s, player, reason);
		EventMaps.flush();
		if (player != null) {
			Chat.send(player, Chat.red("Правка карты «" + s.title + "» прервана (" + reason + "), черновик сохранён."));
		}
		JournalLog.add(JournalLog.EVENTS, "Правка карты «" + s.title + "» · " + s.editorName
				+ " · прервана: " + reason + ", черновик сохранён");
	}

	private static void end(Session s, ServerPlayerEntity dev, String reason) {
		session = null;
		MinecraftServer server = s.arena.world() == null ? null : s.arena.world().getServer();
		for (Map.Entry<UUID, Spot> e : s.helperSpots.entrySet()) {
			ServerPlayerEntity p = server == null ? null : server.getPlayerManager().getPlayer(e.getKey());
			if (p != null) {
				e.getValue().restore(p, "вернулся со стройки карты МП");
				Chat.send(p, Chat.yellow("Стройка карты МП закончена (" + reason + "): вещи и место возвращены."));
			}
		}
		s.helperSpots.clear();
		s.helperNames.clear();
		s.arena.sweepDebris();
		s.arena.remove();
		deleteRestoreFile();
		if (dev != null) {
			s.spot.restore(dev, "вернулся с правки карты МП");
		}
	}

	private static void deleteRestoreFile() {
		Path file = EventMaps.restoreFile();
		if (file == null) {
			return;
		}
		try {
			Files.deleteIfExists(file);
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("Не удалось удалить файл отката правки карты МП {}", file, e);
		}
	}

	/** После падения сервера во время правки: вернуть блоки мира под картой. Вещи людей вернёт бэкап при входе. */
	public static void recoverAfterCrash(MinecraftServer server) {
		Path file = EventMaps.restoreFile();
		if (file == null || !Files.isRegularFile(file)) {
			return;
		}
		try (InputStream in = Files.newInputStream(file)) {
			NbtCompound root = NbtIo.readCompressed(in);
			if (EventArena.restoreFromData(server, root)) {
				AdminControlMod.LOGGER.info("Правка карты МП была прервана падением сервера — блоки мира под картой восстановлены.");
				JournalLog.add(JournalLog.EVENTS, "Правка карты прервана падением сервера · блоки мира восстановлены, черновик остался");
			} else {
				AdminControlMod.LOGGER.error("Файл отката правки карты МП {}: мир не найден, блоки не восстановлены", file);
				return;
			}
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("Не удалось восстановить мир после правки карты МП {}", file, e);
			return;
		}
		deleteRestoreFile();
	}

	private static String where(EventArena a) {
		return a.world().getRegistryKey().getValue() + " " + (int) Math.floor(a.centerX()) + ", " + a.minY + ", "
				+ (int) Math.floor(a.centerZ());
	}
}
