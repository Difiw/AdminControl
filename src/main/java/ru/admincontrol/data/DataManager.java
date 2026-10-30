package ru.admincontrol.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import ru.admincontrol.util.ActionLog;
import ru.admincontrol.util.SecureJsonStorage;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JSON-хранилище внутри папки мира: <мир>/admin_control/*.json
 */
public final class DataManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Type PLAYER_LIST = new TypeToken<List<PlayerRecord>>() {}.getType();
	private static final Type BAN_LIST = new TypeToken<List<BanEntry>>() {}.getType();

	private static final Type AUCTION_TYPE = new TypeToken<AuctionStore>() {}.getType();

	private static DataManager instance;

	private final Path configDir;
	private final Path worldRoot;
	private final Path playersFile;
	private final Path adminsFile;
	private final Path bansFile;
	private final Path settingsFile;
	private final Path auctionFile;
	private final Path passwordsFile;

	private final Map<String, PlayerRecord> byUuid = new ConcurrentHashMap<>();
	private final Map<String, PlayerRecord> byNick = new ConcurrentHashMap<>();
	private final List<BanEntry> bans = new ArrayList<>();
	private ModSettings settings = new ModSettings();
	private AuctionStore auction = new AuctionStore();
	private PasswordStore passwords = new PasswordStore();
	private long lastSaveMillis;
	private boolean dirty;

	private DataManager(Path configDir, Path worldRoot) {
		this.configDir = configDir;
		this.worldRoot = worldRoot;
		this.playersFile = configDir.resolve("players.json");
		this.adminsFile = configDir.resolve("admins.json");
		this.bansFile = configDir.resolve("bans.json");
		this.settingsFile = configDir.resolve("settings.json");
		this.auctionFile = configDir.resolve("auction.json");
		this.passwordsFile = configDir.resolve("pasword").resolve("paswords.json");
	}

	public static DataManager get() {
		return instance;
	}

	public static synchronized void initialize(MinecraftServer server) {
		// Данные и баны только у этого мира: saves/<мир>/admin_control/
		Path worldRoot = server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
		Path configDir = worldRoot.resolve("admin_control");
		try {
			Files.createDirectories(configDir);
			Files.createDirectories(configDir.resolve("logs"));
		} catch (IOException e) {
			throw new IllegalStateException("Не удалось создать папку admin_control в мире", e);
		}
		instance = new DataManager(configDir, worldRoot);
		ActionLog.init(configDir.resolve("logs"), configDir.resolve("logs"));
		instance.load();
		ru.admincontrol.util.AdminStats.load();
		ru.admincontrol.util.NpcStore.load();
		ru.admincontrol.util.PrivateService.load();
		ru.admincontrol.util.WeatherLog.load();
		ru.admincontrol.util.FileScanService.load();
		ru.admincontrol.util.HomeProtection.refresh();
		ru.admincontrol.util.ModGate.reset(server);
	}

	public static synchronized void shutdown() {
		if (instance != null) {
			ru.admincontrol.util.PrivateService.save();
			ru.admincontrol.util.WeatherLog.shutdown();
			ru.admincontrol.util.AdminStats.save();
			ru.admincontrol.util.ActivityLog.flushAll();
			instance.saveNow();
			instance = null;
		}
		ru.admincontrol.util.ModGate.reset(null);
		ActionLog.close();
	}

	public synchronized Set<String> allNicknames() {
		Set<String> names = new LinkedHashSet<>();
		for (PlayerRecord record : byNick.values()) {
			if (record.nickname != null && !record.nickname.isBlank()) {
				names.add(record.nickname);
			}
		}
		for (BanEntry ban : bans) {
			if (ban.nickname != null && !ban.nickname.isBlank()) {
				names.add(ban.nickname);
			}
		}
		if (settings.adminNicks != null) {
			for (String nick : settings.adminNicks) {
				if (nick != null && !nick.isBlank()) {
					names.add(nick);
				}
			}
		}
		return names;
	}

	public Path getConfigDir() {
		return configDir;
	}

	public Path worldRoot() {
		return worldRoot;
	}

	public AuctionStore auction() {
		return auction;
	}

	public PasswordStore passwords() {
		if (passwords == null) {
			passwords = new PasswordStore();
		}
		if (passwords.byNick == null) {
			passwords.byNick = new java.util.LinkedHashMap<>();
		}
		if (passwords.byUuid == null) {
			passwords.byUuid = new java.util.LinkedHashMap<>();
		}
		return passwords;
	}

	public ModSettings settings() {
		return settings;
	}

	public synchronized java.util.Collection<PlayerRecord> records() {
		return new ArrayList<>(byUuid.values());
	}

	public synchronized void load() {
		byUuid.clear();
		byNick.clear();
		bans.clear();
		settings = readJson(settingsFile, ModSettings.class, new ModSettings());
		settings.extendFurnaceLevels();
		if (settings.adminUuids == null) {
			settings.adminUuids = new ArrayList<>();
		}
		if (settings.adminNicks == null) {
			settings.adminNicks = new ArrayList<>();
		}
		if (settings.headAdminUuid == null) {
			settings.headAdminUuid = "";
		}
		if (settings.headAdminNick == null) {
			settings.headAdminNick = "";
		}
		if (settings.adminLevelsByUuid == null) {
			settings.adminLevelsByUuid = new java.util.LinkedHashMap<>();
		}
		if (settings.adminLevelsByNick == null) {
			settings.adminLevelsByNick = new java.util.LinkedHashMap<>();
		}
		if (settings.developerUuids == null) {
			settings.developerUuids = new ArrayList<>();
		}
		if (settings.developerNicks == null) {
			settings.developerNicks = new ArrayList<>();
		}
		if (settings.itemPrices == null) {
			settings.itemPrices = new java.util.LinkedHashMap<>();
		}
		settings.auctionMinPercent = ru.admincontrol.util.Economy.clampPercent(settings.auctionMinPercent, 0, 1000);
		settings.auctionMaxPercent = ru.admincontrol.util.Economy.clampPercent(settings.auctionMaxPercent, 0, 1000);
		if (settings.auctionMaxPercent < settings.auctionMinPercent) {
			settings.auctionMaxPercent = settings.auctionMinPercent;
		}
		if (settings.casinoChance == null) {
			settings.casinoChance = 50;
		}
		if (settings.casinoMinBet < 0) {
			settings.casinoMinBet = 0;
		}
		if (settings.wightUuids == null) {
			settings.wightUuids = new ArrayList<>();
		}
		if (settings.wightNicks == null) {
			settings.wightNicks = new ArrayList<>();
		}
		if (settings.disabledPlayerCommands == null) {
			settings.disabledPlayerCommands = new ArrayList<>();
		}
		if (settings.disabledEveryoneCommands == null) {
			settings.disabledEveryoneCommands = new ArrayList<>();
		}
		ru.admincontrol.util.TrainingConfig.ensure(settings);
		if (settings.serverLockMode == null || settings.serverLockMode.isBlank()) {
			settings.serverLockMode = settings.serverLocked ? "stop" : "none";
		}
		auction = readJson(auctionFile, AuctionStore.class, new AuctionStore());
		if (auction == null) {
			auction = new AuctionStore();
		}
		if (auction.listings == null) {
			auction.listings = new ArrayList<>();
		}
		try {
			Files.createDirectories(passwordsFile.getParent());
		} catch (IOException ignored) {
		}
		passwords = readJson(passwordsFile, PasswordStore.class, new PasswordStore());
		if (passwords == null) {
			passwords = new PasswordStore();
		}
		passwords();
		List<PlayerRecord> players = readJson(playersFile, PLAYER_LIST, new ArrayList<>());
		for (PlayerRecord record : players) {
			if (record != null && !record.nps) {
				index(record);
			}
		}
		List<BanEntry> loadedBans = readJson(bansFile, BAN_LIST, new ArrayList<>());
		bans.addAll(loadedBans);
		syncAdminsFileIntoSettings();
		migrateAdminLevels();
		if (settings.anticheatClickVersion < 3) {
			if (settings.anticheatClicksPerSec > 0 && settings.anticheatClicksPerSec < 25) {
				settings.anticheatClicksPerSec = 25;
			}
			if (settings.anticheatClickHoldSec < 2) {
				settings.anticheatClickHoldSec = 2;
			}
			if (settings.anticheatXrayHidden < 12) {
				settings.anticheatXrayHidden = 16;
			}
			if (settings.anticheatXrayWindowSec < 30) {
				settings.anticheatXrayWindowSec = 90;
			}
			if (settings.afkMinutes <= 0) {
				settings.afkMinutes = 7;
			}
			settings.anticheatClickVersion = 3;
			markDirty();
		}
		if (settings.anticheatXrayBanDays <= 0 || settings.anticheatXrayBanDays == 7) {
			settings.anticheatXrayBanDays = 30;
			markDirty();
		}
		if (settings.anticheatClickBanDays <= 0) {
			settings.anticheatClickBanDays = 1;
			markDirty();
		}
		settings.firstAdminAssigned = !settings.adminUuids.isEmpty() || !settings.adminNicks.isEmpty();
		expireBans(System.currentTimeMillis());
		clearWarnJoinLocks();
		ru.admincontrol.util.Economy.ensureDefaultSellBans();
		ru.admincontrol.util.CasinoService.ensureHouseOdds();
		if (settings.usedPlayerNicks == null) {
			settings.usedPlayerNicks = new ArrayList<>();
		}
		for (PlayerRecord record : byUuid.values()) {
			if (record != null && !record.nps) {
				rememberPlayerNick(record.nickname);
			}
		}
		for (BanEntry ban : bans) {
			if (ban != null) {
				rememberPlayerNick(ban.nickname);
			}
		}
	}

	public synchronized void markDirty() {
		this.dirty = true;
	}

	public synchronized void saveAll() {
		this.dirty = true;
		flushWrites(false);
		ru.admincontrol.util.ModGate.refresh();
	}

	public synchronized void saveNow() {
		this.dirty = true;
		flushWrites(true);
	}

	public synchronized void saveIfDirty() {
		if (dirty) {
			flushWrites(true);
		}
	}

	public synchronized void saveIfDue(long now) {
		if (dirty && now - lastSaveMillis >= 2_000L) {
			flushWrites(true);
		}
	}

	private void flushWrites(boolean force) {
		if (!dirty && !force) {
			return;
		}
		long now = System.currentTimeMillis();
		if (!force && now - lastSaveMillis < 2_000L) {
			return;
		}
		try {
			Files.createDirectories(configDir);
			writeJson(playersFile, persistPlayers());
			writeJson(bansFile, bans);
			writeJson(settingsFile, settings);
			writeJson(adminsFile, settings);
			writeJson(auctionFile, auction);
			Files.createDirectories(passwordsFile.getParent());
			writeJson(passwordsFile, passwords());
			lastSaveMillis = now;
			dirty = false;
		} catch (IOException e) {
			ActionLog.error("Не удалось сохранить данные Admin Control", e);
		}
	}

	public synchronized PlayerRecord getOrCreate(ServerPlayerEntity player) {
		PlayerRecord record = byUuid.get(player.getUuid().toString());
		if (record == null) {
			record = findByNick(player.getGameProfile().getName()).orElse(null);
			if (record != null && ru.admincontrol.util.NpcService.isNpc(player) != record.nps) {
				record = null;
			}
		}
		if (record == null) {
			record = new PlayerRecord();
		}
		record.uuid = player.getUuid().toString();
		record.nickname = player.getGameProfile().getName();
		if (ru.admincontrol.util.NpcService.isNpc(player)) {
			record.nps = true;
		} else {
			rememberPlayerNick(record.nickname);
		}
		index(record);
		return record;
	}

	/** Игровой прогресс (WC, сила, удача, табло, статистика): при управлении NPC — запись NPC. */
	public PlayerRecord progress(ServerPlayerEntity player) {
		return getOrCreate(ru.admincontrol.util.NpcService.moneyPlayer(player));
	}

	public synchronized void restoreNps(PlayerRecord record) {
		if (record == null || record.uuid == null || record.uuid.isBlank()) {
			return;
		}
		record.nps = true;
		index(record);
	}

	public synchronized void rememberPlayerNick(String nick) {
		if (nick == null || nick.isBlank()) {
			return;
		}
		if (settings.usedPlayerNicks == null) {
			settings.usedPlayerNicks = new ArrayList<>();
		}
		for (String existing : settings.usedPlayerNicks) {
			if (nick.equalsIgnoreCase(existing)) {
				return;
			}
		}
		settings.usedPlayerNicks.add(nick);
		markDirty();
	}

	public synchronized boolean nickTakenByPlayer(String nick) {
		if (nick == null || nick.isBlank()) {
			return false;
		}
		if (findByNick(nick).map(record -> !record.nps).orElse(false)) {
			return true;
		}
		if (settings.usedPlayerNicks != null) {
			for (String existing : settings.usedPlayerNicks) {
				if (nick.equalsIgnoreCase(existing)) {
					return true;
				}
			}
		}
		for (PlayerRecord record : byUuid.values()) {
			if (record != null && !record.nps && record.nickname != null && record.nickname.equalsIgnoreCase(nick)) {
				return true;
			}
		}
		return false;
	}

	public synchronized void forgetNps(ServerPlayerEntity player) {
		if (player == null) {
			return;
		}
		String uuid = player.getUuid().toString();
		String nick = player.getGameProfile().getName();
		PlayerRecord record = byUuid.get(uuid);
		if (record != null && record.nps) {
			byUuid.remove(uuid);
		}
		if (nick != null && !nick.isBlank()) {
			PlayerRecord byName = byNick.get(nick.toLowerCase(Locale.ROOT));
			if (byName != null && byName.nps && (record == null || byName == record || uuid.equals(byName.uuid))) {
				byNick.remove(nick.toLowerCase(Locale.ROOT));
			}
		}
		bans.removeIf(entry -> entry != null && uuid.equals(entry.uuid));
	}

	private List<PlayerRecord> persistPlayers() {
		List<PlayerRecord> list = new ArrayList<>();
		for (PlayerRecord record : byUuid.values()) {
			if (record != null && !record.nps) {
				list.add(record);
			}
		}
		return list;
	}

	public synchronized Optional<PlayerRecord> findByNick(String nick) {
		if (nick == null || nick.isBlank()) {
			return Optional.empty();
		}
		PlayerRecord direct = byNick.get(nick.toLowerCase(Locale.ROOT));
		if (direct != null) {
			return Optional.of(direct);
		}
		return Optional.empty();
	}

	public synchronized Optional<PlayerRecord> findByUuid(UUID uuid) {
		if (uuid == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(byUuid.get(uuid.toString()));
	}

	public synchronized boolean isAdmin(UUID uuid, String nick) {
		if (uuid != null) {
			String id = uuid.toString();
			if (containsIgnoreCase(settings.adminUuids, id) || hasLevelKey(settings.adminLevelsByUuid, id)) {
				return true;
			}
		}
		if (nick != null && !nick.isBlank()) {
			if (settings.adminNicks.stream().anyMatch(n -> n != null && n.equalsIgnoreCase(nick))) {
				return true;
			}
			return hasLevelKey(settings.adminLevelsByNick, nick);
		}
		return false;
	}

	public boolean isAdmin(ServerPlayerEntity player) {
		return isAdmin(player.getUuid(), player.getGameProfile().getName());
	}

	public int getAdminLevel(ServerPlayerEntity player) {
		return player == null ? AdminRanks.NONE : getAdminLevel(player.getUuid(), player.getGameProfile().getName());
	}

	public synchronized int getAdminLevel(UUID uuid, String nick) {
		int stored = AdminRanks.NONE;
		if (uuid != null) {
			Integer byUuid = levelIgnoreCase(settings.adminLevelsByUuid, uuid.toString());
			if (byUuid != null) {
				stored = clampStoredLevel(byUuid);
			}
		}
		if (stored == AdminRanks.NONE && nick != null) {
			Integer byNick = levelIgnoreCase(settings.adminLevelsByNick, nick);
			if (byNick != null) {
				stored = clampStoredLevel(byNick);
			}
		}
		if (isPersistentDeveloper(uuid, nick) || stored >= AdminRanks.DEV) {
			return AdminRanks.DEV;
		}
		if (isHeadAdmin(uuid, nick) && stored < AdminRanks.GA) {
			return AdminRanks.GA;
		}
		if (stored >= AdminRanks.MOD) {
			return stored;
		}
		return isAdmin(uuid, nick) ? AdminRanks.MOD : AdminRanks.NONE;
	}

	private static int clampStoredLevel(Integer raw) {
		if (raw == null) {
			return AdminRanks.MOD;
		}
		return AdminRanks.migrateStored(raw);
	}

	public boolean isHeadAdmin(ServerPlayerEntity player) {
		return player != null && isHeadAdmin(player.getUuid(), player.getGameProfile().getName());
	}

	public synchronized boolean isHeadAdmin(java.util.UUID uuid, String nick) {
		if (settings.headAdminUuid != null && !settings.headAdminUuid.isBlank() && uuid != null
				&& settings.headAdminUuid.equalsIgnoreCase(uuid.toString())) {
			return true;
		}
		return settings.headAdminNick != null && nick != null && settings.headAdminNick.equalsIgnoreCase(nick);
	}

	public synchronized void setHeadAdmin(ServerPlayerEntity player) {
		PlayerRecord record = getOrCreate(player);
		clearPersistentDeveloper(record);
		String oldUuid = settings.headAdminUuid;
		String oldNick = settings.headAdminNick;
		if (oldUuid != null && !oldUuid.isBlank() && !oldUuid.equalsIgnoreCase(player.getUuid().toString())
				&& getAdminLevel(parseUuid(oldUuid), oldNick) < AdminRanks.DEV) {
			settings.adminLevelsByUuid.put(oldUuid, AdminRanks.ZGA);
		}
		if (oldNick != null && !oldNick.isBlank() && !oldNick.equalsIgnoreCase(player.getGameProfile().getName())
				&& getAdminLevel(null, oldNick) < AdminRanks.DEV) {
			putNickLevel(oldNick, AdminRanks.ZGA);
		}
		addAdmin(record, AdminRanks.GA);
		settings.headAdminUuid = player.getUuid().toString();
		settings.headAdminNick = player.getGameProfile().getName();
		saveAll();
	}

	public synchronized boolean hasAnyGa() {
		if (isHeadAdmin(null, settings.headAdminNick) && !settings.headAdminNick.isBlank()) {
			return true;
		}
		for (Integer level : settings.adminLevelsByUuid.values()) {
			if (level != null && AdminRanks.migrateStored(level) >= AdminRanks.GA) {
				return true;
			}
		}
		for (Integer level : settings.adminLevelsByNick.values()) {
			if (level != null && AdminRanks.migrateStored(level) >= AdminRanks.GA) {
				return true;
			}
		}
		return false;
	}

	public synchronized boolean hasAnyDeveloper() {
		for (Integer level : settings.adminLevelsByUuid.values()) {
			if (level != null && AdminRanks.migrateStored(level) >= AdminRanks.DEV) {
				return true;
			}
		}
		for (Integer level : settings.adminLevelsByNick.values()) {
			if (level != null && AdminRanks.migrateStored(level) >= AdminRanks.DEV) {
				return true;
			}
		}
		return false;
	}

	public synchronized void setDeveloper(ServerPlayerEntity player) {
		PlayerRecord record = getOrCreate(player);
		markPersistentDeveloper(record);
		addAdmin(record, AdminRanks.DEV);
	}

	public synchronized boolean hasAnyAdmin() {
		return !settings.adminUuids.isEmpty() || !settings.adminNicks.isEmpty();
	}

	/** Есть ли на сервере ГА или разработчик — без них мод не работает. */
	public synchronized boolean hasHeadStaff() {
		if ((settings.headAdminUuid != null && !settings.headAdminUuid.isBlank())
				|| (settings.headAdminNick != null && !settings.headAdminNick.isBlank())) {
			return true;
		}
		if ((settings.developerUuids != null && !settings.developerUuids.isEmpty())
				|| (settings.developerNicks != null && !settings.developerNicks.isEmpty())) {
			return true;
		}
		for (Integer level : settings.adminLevelsByUuid.values()) {
			if (level != null && AdminRanks.migrateStored(level) >= AdminRanks.GA) {
				return true;
			}
		}
		for (Integer level : settings.adminLevelsByNick.values()) {
			if (level != null && AdminRanks.migrateStored(level) >= AdminRanks.GA) {
				return true;
			}
		}
		return false;
	}

	public synchronized void addAdmin(PlayerRecord record) {
		addAdmin(record, AdminRanks.MOD);
	}

	public synchronized void addAdmin(PlayerRecord record, int level) {
		int rank = AdminRanks.migrateStored(level);
		if (rank < AdminRanks.MOD) {
			rank = AdminRanks.MOD;
		}
		if (record.uuid != null && !record.uuid.isBlank() && !settings.adminUuids.contains(record.uuid)) {
			settings.adminUuids.add(record.uuid);
		}
		if (record.nickname != null && settings.adminNicks.stream().noneMatch(n -> n.equalsIgnoreCase(record.nickname))) {
			settings.adminNicks.add(record.nickname);
		}
		if (record.uuid != null && !record.uuid.isBlank()) {
			settings.adminLevelsByUuid.put(record.uuid, rank);
		}
		if (record.nickname != null && !record.nickname.isBlank()) {
			putNickLevel(record.nickname, rank);
		}
		if (rank >= AdminRanks.DEV) {
			markPersistentDeveloper(record);
		}
		if (rank == AdminRanks.GA) {
			boolean noHead = (settings.headAdminUuid == null || settings.headAdminUuid.isBlank())
					&& (settings.headAdminNick == null || settings.headAdminNick.isBlank());
			if (noHead) {
				settings.headAdminUuid = record.uuid == null ? "" : record.uuid;
				settings.headAdminNick = record.nickname == null ? "" : record.nickname;
			}
			settings.addAdminLockedToStaff = true;
		}
		settings.firstAdminAssigned = true;
		ru.admincontrol.util.AdminStats.granted(record.nickname);
		saveAll();
	}

	public synchronized void setAdminLevel(PlayerRecord record, int level) {
		if (lockedDeveloper(record) && AdminRanks.migrateStored(level) < AdminRanks.DEV) {
			return;
		}
		int rank = AdminRanks.migrateStored(level);
		if (rank < AdminRanks.MOD) {
			rank = AdminRanks.MOD;
		}
		if (record.uuid != null && !record.uuid.isBlank()) {
			settings.adminLevelsByUuid.put(record.uuid, rank);
		}
		if (record.nickname != null && !record.nickname.isBlank()) {
			putNickLevel(record.nickname, rank);
		}
		if (rank >= AdminRanks.DEV) {
			markPersistentDeveloper(record);
		}
		if (rank == AdminRanks.GA) {
			boolean noHead = (settings.headAdminUuid == null || settings.headAdminUuid.isBlank())
					&& (settings.headAdminNick == null || settings.headAdminNick.isBlank());
			if (noHead) {
				settings.headAdminUuid = record.uuid == null ? "" : record.uuid;
				settings.headAdminNick = record.nickname == null ? "" : record.nickname;
			}
		}
		saveAll();
	}

	/**
	 * Снимает админ-права. Возвращает true, если в списке ещё остались администраторы.
	 */
	public synchronized boolean lockedDeveloper(PlayerRecord record) {
		return record != null && (isPersistentDeveloper(record.uuidOrNull(), record.nickname)
				|| getAdminLevel(record.uuidOrNull(), record.nickname) >= AdminRanks.DEV);
	}

	public synchronized void stepDownDeveloper(PlayerRecord record) {
		if (record == null) {
			return;
		}
		clearPersistentDeveloper(record);
		addAdmin(record, AdminRanks.GA);
	}

	public synchronized boolean removeAdmin(PlayerRecord record) {
		if (lockedDeveloper(record)) {
			return !settings.adminUuids.isEmpty() || !settings.adminNicks.isEmpty();
		}
		if (record.uuid != null && !record.uuid.isBlank()) {
			removeIgnoreCase(settings.adminUuids, record.uuid);
			removeLevelKey(settings.adminLevelsByUuid, record.uuid);
		}
		if (record.nickname != null && !record.nickname.isBlank()) {
			removeIgnoreCase(settings.adminNicks, record.nickname);
			removeLevelKey(settings.adminLevelsByNick, record.nickname);
			for (PlayerRecord other : records()) {
				if (other != null && other.nickname != null && other.nickname.equalsIgnoreCase(record.nickname)
						&& other.uuid != null && !other.uuid.isBlank()) {
					removeIgnoreCase(settings.adminUuids, other.uuid);
					removeLevelKey(settings.adminLevelsByUuid, other.uuid);
				}
			}
		}
		if (isHeadAdmin(record.uuidOrNull(), record.nickname)) {
			settings.headAdminUuid = "";
			settings.headAdminNick = "";
		}
		boolean remaining = !settings.adminUuids.isEmpty() || !settings.adminNicks.isEmpty();
		settings.firstAdminAssigned = remaining;
		ru.admincontrol.util.AdminStats.revoked(record.nickname);
		saveAll();
		return remaining;
	}

	public synchronized List<AdminListEntry> listAllAdmins(net.minecraft.server.MinecraftServer server) {
		java.util.LinkedHashMap<String, Integer> byNick = new java.util.LinkedHashMap<>();
		if (settings.adminNicks != null) {
			for (String nick : settings.adminNicks) {
				if (nick != null && !nick.isBlank()) {
					byNick.putIfAbsent(nick, getAdminLevel(null, nick));
				}
			}
		}
		if (settings.adminLevelsByNick != null) {
			for (var entry : settings.adminLevelsByNick.entrySet()) {
				if (entry.getKey() != null && !entry.getKey().isBlank()) {
					byNick.putIfAbsent(entry.getKey(), clampStoredLevel(entry.getValue()));
				}
			}
		}
		if (settings.adminUuids != null) {
			for (String id : settings.adminUuids) {
				findByUuid(parseUuid(id)).ifPresent(record ->
						byNick.putIfAbsent(record.nickname, getAdminLevel(record.uuidOrNull(), record.nickname)));
			}
		}
		if (settings.adminLevelsByUuid != null) {
			for (var entry : settings.adminLevelsByUuid.entrySet()) {
				findByUuid(parseUuid(entry.getKey())).ifPresent(record ->
						byNick.putIfAbsent(record.nickname, getAdminLevel(record.uuidOrNull(), record.nickname)));
			}
		}
		List<AdminListEntry> out = new ArrayList<>();
		java.util.Set<String> online = new java.util.HashSet<>();
		if (server != null) {
			for (net.minecraft.server.network.ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				online.add(player.getGameProfile().getName().toLowerCase(Locale.ROOT));
			}
		}
		for (var entry : byNick.entrySet()) {
			int level = Math.max(AdminRanks.MOD, getAdminLevel(null, entry.getKey()));
			boolean on = online.contains(entry.getKey().toLowerCase(Locale.ROOT));
			out.add(new AdminListEntry(entry.getKey(), level, on));
		}
		out.sort((a, b) -> {
			int cmp = Integer.compare(b.level, a.level);
			return cmp != 0 ? cmp : a.nick.compareToIgnoreCase(b.nick);
		});
		return out;
	}

	public static final class AdminListEntry {
		public final String nick;
		public final int level;
		public final boolean online;

		public AdminListEntry(String nick, int level, boolean online) {
			this.nick = nick;
			this.level = level;
			this.online = online;
		}
	}

	private static boolean containsIgnoreCase(List<String> list, String value) {
		if (list == null || value == null) {
			return false;
		}
		return list.stream().anyMatch(id -> id != null && id.equalsIgnoreCase(value));
	}

	private static boolean hasLevelKey(java.util.Map<String, Integer> map, String value) {
		return levelIgnoreCase(map, value) != null;
	}

	private static Integer levelIgnoreCase(java.util.Map<String, Integer> map, String value) {
		if (map == null || value == null) {
			return null;
		}
		Integer direct = map.get(value);
		if (direct != null) {
			return direct;
		}
		for (var entry : map.entrySet()) {
			if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(value)) {
				return entry.getValue();
			}
		}
		return null;
	}

	private static void removeIgnoreCase(List<String> list, String value) {
		if (list == null || value == null) {
			return;
		}
		list.removeIf(id -> id != null && id.equalsIgnoreCase(value));
	}

	private static void removeLevelKey(java.util.Map<String, Integer> map, String value) {
		if (map == null || value == null) {
			return;
		}
		map.entrySet().removeIf(e -> e.getKey() != null && e.getKey().equalsIgnoreCase(value));
	}

	private void putNickLevel(String nick, int level) {
		settings.adminLevelsByNick.entrySet().removeIf(e -> e.getKey() != null && e.getKey().equalsIgnoreCase(nick));
		settings.adminLevelsByNick.put(nick, level);
	}

	private void migrateAdminLevels() {
		for (String uuid : settings.adminUuids) {
			if (uuid != null && !uuid.isBlank()) {
				settings.adminLevelsByUuid.putIfAbsent(uuid, AdminRanks.MOD);
			}
		}
		for (String nick : settings.adminNicks) {
			if (nick != null && !nick.isBlank()) {
				boolean has = settings.adminLevelsByNick.keySet().stream().anyMatch(n -> n.equalsIgnoreCase(nick));
				if (!has) {
					settings.adminLevelsByNick.put(nick, AdminRanks.MOD);
				}
			}
		}
		for (var entry : new ArrayList<>(settings.adminLevelsByUuid.entrySet())) {
			settings.adminLevelsByUuid.put(entry.getKey(), AdminRanks.migrateStored(entry.getValue() == null ? AdminRanks.MOD : entry.getValue()));
		}
		for (var entry : new ArrayList<>(settings.adminLevelsByNick.entrySet())) {
			settings.adminLevelsByNick.put(entry.getKey(), AdminRanks.migrateStored(entry.getValue() == null ? AdminRanks.MOD : entry.getValue()));
		}
		collectStoredDevelopers();
		if (settings.headAdminUuid != null && !settings.headAdminUuid.isBlank()
				&& !isPersistentDeveloperUuid(settings.headAdminUuid)) {
			settings.adminLevelsByUuid.put(settings.headAdminUuid, AdminRanks.GA);
		}
		if (settings.headAdminNick != null && !settings.headAdminNick.isBlank()
				&& !isPersistentDeveloperNick(settings.headAdminNick)) {
			putNickLevel(settings.headAdminNick, AdminRanks.GA);
		}
		applyPersistentDevelopers();
	}

	private void collectStoredDevelopers() {
		for (var entry : settings.adminLevelsByUuid.entrySet()) {
			if (entry.getValue() != null && AdminRanks.migrateStored(entry.getValue()) >= AdminRanks.DEV) {
				addUniqueIgnoreCase(settings.developerUuids, entry.getKey());
			}
		}
		for (var entry : settings.adminLevelsByNick.entrySet()) {
			if (entry.getValue() != null && AdminRanks.migrateStored(entry.getValue()) >= AdminRanks.DEV) {
				addUniqueIgnoreCase(settings.developerNicks, entry.getKey());
			}
		}
	}

	private void applyPersistentDevelopers() {
		if (settings.developerUuids != null) {
			for (String uuid : settings.developerUuids) {
				if (uuid != null && !uuid.isBlank()) {
					settings.adminLevelsByUuid.put(uuid, AdminRanks.DEV);
					if (!settings.adminUuids.contains(uuid)) {
						settings.adminUuids.add(uuid);
					}
				}
			}
		}
		if (settings.developerNicks != null) {
			for (String nick : settings.developerNicks) {
				if (nick != null && !nick.isBlank()) {
					putNickLevel(nick, AdminRanks.DEV);
					if (settings.adminNicks.stream().noneMatch(n -> n.equalsIgnoreCase(nick))) {
						settings.adminNicks.add(nick);
					}
				}
			}
		}
	}

	public synchronized boolean isPersistentDeveloper(UUID uuid, String nick) {
		return isPersistentDeveloperUuid(uuid == null ? null : uuid.toString())
				|| isPersistentDeveloperNick(nick);
	}

	private boolean isPersistentDeveloperUuid(String uuid) {
		if (uuid == null || uuid.isBlank() || settings.developerUuids == null) {
			return false;
		}
		for (String id : settings.developerUuids) {
			if (id != null && id.equalsIgnoreCase(uuid)) {
				return true;
			}
		}
		return false;
	}

	private boolean isPersistentDeveloperNick(String nick) {
		if (nick == null || nick.isBlank() || settings.developerNicks == null) {
			return false;
		}
		for (String name : settings.developerNicks) {
			if (name != null && name.equalsIgnoreCase(nick)) {
				return true;
			}
		}
		return false;
	}

	private void markPersistentDeveloper(PlayerRecord record) {
		if (record == null) {
			return;
		}
		if (settings.developerUuids == null) {
			settings.developerUuids = new ArrayList<>();
		}
		if (settings.developerNicks == null) {
			settings.developerNicks = new ArrayList<>();
		}
		if (record.uuid != null && !record.uuid.isBlank()) {
			addUniqueIgnoreCase(settings.developerUuids, record.uuid);
		}
		if (record.nickname != null && !record.nickname.isBlank()) {
			addUniqueIgnoreCase(settings.developerNicks, record.nickname);
		}
	}

	private void clearPersistentDeveloper(PlayerRecord record) {
		if (record == null) {
			return;
		}
		if (record.uuid != null && settings.developerUuids != null) {
			settings.developerUuids.removeIf(id -> id != null && id.equalsIgnoreCase(record.uuid));
		}
		if (record.nickname != null && settings.developerNicks != null) {
			settings.developerNicks.removeIf(n -> n != null && n.equalsIgnoreCase(record.nickname));
		}
	}

	private static void addUniqueIgnoreCase(List<String> list, String value) {
		if (list == null || value == null || value.isBlank()) {
			return;
		}
		if (list.stream().noneMatch(v -> v != null && v.equalsIgnoreCase(value))) {
			list.add(value);
		}
	}

	public synchronized Optional<BanEntry> findActiveBan(UUID uuid, String nick) {
		return findBan(uuid, nick, BanKind.BAN);
	}

	public synchronized Optional<BanEntry> findActiveJail(UUID uuid, String nick) {
		return findBan(uuid, nick, BanKind.JAIL);
	}

	public synchronized Optional<BanEntry> findActiveWarn(UUID uuid, String nick) {
		return findBan(uuid, nick, BanKind.WARN);
	}

	private void clearWarnJoinLocks() {
		boolean changed = bans.removeIf(ban -> ban != null && ban.warn);
		if (changed) {
			saveAll();
		}
	}

	private enum BanKind {
		BAN, JAIL, WARN
	}

	private Optional<BanEntry> findBan(UUID uuid, String nick, BanKind kind) {
		long now = System.currentTimeMillis();
		for (BanEntry ban : bans) {
			if (ban == null || !matchesKind(ban, kind)) {
				continue;
			}
			if (!ban.permanent && !ban.warn && ban.expiresAt != null && ban.expiresAt > 0 && now >= ban.expiresAt) {
				continue;
			}
			if (uuid != null && uuid.toString().equalsIgnoreCase(ban.uuid)) {
				return Optional.of(ban);
			}
			if (nick != null && nick.equalsIgnoreCase(ban.nickname)) {
				return Optional.of(ban);
			}
		}
		return Optional.empty();
	}

	private static boolean matchesKind(BanEntry ban, BanKind kind) {
		return switch (kind) {
			case JAIL -> ban.jail;
			case WARN -> ban.warn;
			case BAN -> !ban.jail && !ban.warn;
		};
	}

	public synchronized boolean addBan(BanEntry entry) {
		BanKind kind = entry.warn ? BanKind.WARN : (entry.jail ? BanKind.JAIL : BanKind.BAN);
		if (findBan(parseUuid(entry.uuid), entry.nickname, kind).isPresent()) {
			return false;
		}
		bans.add(entry);
		ru.admincontrol.event.PlayerTracker.clearBanPass();
		saveAll();
		return true;
	}

	/** Копии пермабанов от фильтра ников — для авто-снятия при ослаблении правил. */
	public synchronized java.util.List<BanEntry> nickFilterBans() {
		java.util.List<BanEntry> out = new java.util.ArrayList<>();
		for (BanEntry ban : bans) {
			if (ban != null && ban.nickFilter && ban.permanent && !ban.jail && !ban.warn) {
				out.add(ban);
			}
		}
		return out;
	}

	public synchronized Optional<BanEntry> removeBan(String nick, boolean permanentOnly, boolean temporaryOnly) {
		return removeBanEntry(nick, BanKind.BAN, permanentOnly, temporaryOnly);
	}

	public synchronized Optional<BanEntry> removeJail(String nick) {
		return removeBanEntry(nick, BanKind.JAIL, false, false);
	}

	public synchronized Optional<BanEntry> removeWarn(String nick) {
		return removeBanEntry(nick, BanKind.WARN, false, false);
	}

	private Optional<BanEntry> removeBanEntry(String nick, BanKind kind, boolean permanentOnly, boolean temporaryOnly) {
		PlayerRecord record = findByNick(nick).orElse(null);
		Iterator<BanEntry> iterator = bans.iterator();
		while (iterator.hasNext()) {
			BanEntry ban = iterator.next();
			if (!matchesKind(ban, kind)) {
				continue;
			}
			boolean match = ban.nickname.equalsIgnoreCase(nick)
					|| (record != null && !record.uuid.isBlank() && record.uuid.equalsIgnoreCase(ban.uuid));
			if (!match) {
				continue;
			}
			if (kind == BanKind.BAN) {
				if (permanentOnly && !ban.permanent) {
					continue;
				}
				if (temporaryOnly && ban.permanent) {
					continue;
				}
			}
			iterator.remove();
			saveAll();
			return Optional.of(ban);
		}
		return Optional.empty();
	}

	public synchronized void expireBans(long now) {
		boolean changed = false;
		Iterator<BanEntry> iterator = bans.iterator();
		while (iterator.hasNext()) {
			BanEntry ban = iterator.next();
			if (ban.warn) {
				continue;
			}
			if (!ban.permanent && ban.expiresAt != null && ban.expiresAt > 0 && now >= ban.expiresAt) {
				iterator.remove();
				findByUuid(parseUuid(ban.uuid)).or(() -> findByNick(ban.nickname)).ifPresent(record -> {
					for (PunishmentRecord history : record.history) {
						if (ban.recordId.equals(history.id) && history.active) {
							history.active = false;
							history.reason = history.reason + " (срок истёк)";
						}
					}
				});
				changed = true;
			}
		}
		if (changed) {
			saveAll();
		}
	}

	public boolean cheatsAllowed(ServerPlayerEntity player) {
		if (settings.playerCheats) {
			return true;
		}
		PlayerRecord record = getOrCreate(player);
		if (record.cheatsEnabled) {
			return true;
		}
		return settings.adminCheats && isAdmin(player);
	}

	private void index(PlayerRecord record) {
		if (record.history == null) {
			record.history = new ArrayList<>();
		}
		if (record.points == null) {
			record.points = new ArrayList<>();
		}
		if (record.actionFeedDisabled == null) {
			record.actionFeedDisabled = new ArrayList<>();
		}
		if (record.pendingLapis > 0) {
			record.pendingCoins += record.pendingLapis;
			record.pendingLapis = 0;
		}
		if (record.uuid != null && !record.uuid.isBlank()) {
			byUuid.put(record.uuid, record);
		}
		if (record.nickname != null && !record.nickname.isBlank()) {
			byNick.put(record.nickname.toLowerCase(Locale.ROOT), record);
		}
	}

	private void syncAdminsFileIntoSettings() {
		ModSettings fromAdmins = readJson(adminsFile, ModSettings.class, null);
		if (fromAdmins == null) {
			return;
		}
		if (fromAdmins.adminUuids != null && !fromAdmins.adminUuids.isEmpty()) {
			for (String id : fromAdmins.adminUuids) {
				if (!settings.adminUuids.contains(id)) {
					settings.adminUuids.add(id);
				}
			}
		}
		if (fromAdmins.adminNicks != null && !fromAdmins.adminNicks.isEmpty()) {
			for (String nick : fromAdmins.adminNicks) {
				if (settings.adminNicks.stream().noneMatch(n -> n.equalsIgnoreCase(nick))) {
					settings.adminNicks.add(nick);
				}
			}
		}
		settings.firstAdminAssigned = !settings.adminUuids.isEmpty() || !settings.adminNicks.isEmpty();
	}

	public synchronized void pendingCoins(String uuid, String nick, int amount) {
		PlayerRecord record = uuid != null ? byUuid.get(uuid) : null;
		if (record == null) {
			record = findByNick(nick).orElse(null);
		}
		if (record == null) {
			return;
		}
		record.pendingCoins += Math.max(0, amount);
		saveAll();
	}

	public synchronized void deliverPending(ServerPlayerEntity player) {
		PlayerRecord record = getOrCreate(player);
		int pending = record.pendingCoins + record.pendingLapis;
		if (pending > 0) {
			record.coins += pending;
			record.pendingCoins = 0;
			record.pendingLapis = 0;
			saveAll();
			ru.admincontrol.util.CoinLedger.credit(player, pending, "зачислены отложенные World coin");
		}
	}

	public boolean wantsActionFeed(ServerPlayerEntity player) {
		return wantsActionFeed(player, null);
	}

	public boolean wantsActionFeed(ServerPlayerEntity player, ActionKind kind) {
		if (player == null || !isAdmin(player) || AdminRanks.isBlogger(getAdminLevel(player))) {
			return false;
		}
		return getOrCreate(player).feedEnabled(kind);
	}

	public synchronized boolean isWight(ServerPlayerEntity player) {
		if (player == null) {
			return false;
		}
		if (settings.wightUuids.stream().anyMatch(id -> id.equalsIgnoreCase(player.getUuid().toString()))) {
			return true;
		}
		return settings.wightNicks.stream().anyMatch(n -> n.equalsIgnoreCase(player.getGameProfile().getName()));
	}

	public synchronized void addWight(PlayerRecord record) {
		if (record.uuid != null && settings.wightUuids.stream().noneMatch(id -> id.equalsIgnoreCase(record.uuid))) {
			settings.wightUuids.add(record.uuid);
		}
		if (record.nickname != null && settings.wightNicks.stream().noneMatch(n -> n.equalsIgnoreCase(record.nickname))) {
			settings.wightNicks.add(record.nickname);
		}
		saveAll();
	}

	public synchronized void removeWight(PlayerRecord record) {
		if (record.uuid != null) {
			settings.wightUuids.removeIf(id -> id.equalsIgnoreCase(record.uuid));
		}
		if (record.nickname != null) {
			settings.wightNicks.removeIf(n -> n.equalsIgnoreCase(record.nickname));
		}
		saveAll();
	}

	public synchronized int removeMassPermabans() {
		int removed = 0;
		Iterator<BanEntry> iterator = bans.iterator();
		while (iterator.hasNext()) {
			BanEntry ban = iterator.next();
			if (ban.fromAllPermab && ban.permanent && !ban.jail && !ban.warn) {
				iterator.remove();
				removed++;
			}
		}
		if (removed > 0) {
			saveAll();
		}
		return removed;
	}

	public boolean canJoinWhileLocked(ServerPlayerEntity player) {
		if (player == null) {
			return true;
		}
		int level = getAdminLevel(player);
		String mode = settings.serverLockMode == null ? "none" : settings.serverLockMode;
		if ("kill".equalsIgnoreCase(mode)) {
			return level >= AdminRanks.DEV;
		}
		if ("stop".equalsIgnoreCase(mode) || settings.serverLocked) {
			return level >= AdminRanks.SENIOR || isWight(player);
		}
		return true;
	}

	public boolean isServerLocked() {
		String mode = settings.serverLockMode == null ? "none" : settings.serverLockMode;
		return settings.serverLocked || "stop".equalsIgnoreCase(mode) || "kill".equalsIgnoreCase(mode);
	}

	public String serverLockReason() {
		return settings.serverLockReason == null ? "" : settings.serverLockReason;
	}

	private static UUID parseUuid(String raw) {
		try {
			return raw == null || raw.isBlank() ? null : UUID.fromString(raw);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private <T> T readJson(Path path, Type type, T fallback) {
		if (!Files.exists(path)) {
			return fallback;
		}
		try {
			String raw = Files.readString(path, StandardCharsets.UTF_8).trim();
			if (raw.isEmpty()) {
				return fallback;
			}
			if (SecureJsonStorage.looksEncrypted(raw)) {
				raw = SecureJsonStorage.decrypt(raw, worldRoot);
			}
			T value = GSON.fromJson(raw, type);
			return value != null ? value : fallback;
		} catch (Exception e) {
			ActionLog.error("Ошибка чтения " + path, e);
			return fallback;
		}
	}

	private <T> T readJson(Path path, Class<T> type, T fallback) {
		if (!Files.exists(path)) {
			return fallback;
		}
		try {
			String raw = Files.readString(path, StandardCharsets.UTF_8).trim();
			if (raw.isEmpty()) {
				return fallback;
			}
			if (SecureJsonStorage.looksEncrypted(raw)) {
				raw = SecureJsonStorage.decrypt(raw, worldRoot);
			}
			T value = GSON.fromJson(raw, type);
			return value != null ? value : fallback;
		} catch (Exception e) {
			ActionLog.error("Ошибка чтения " + path, e);
			return fallback;
		}
	}

	private void writeJson(Path path, Object value) throws IOException {
		String json = GSON.toJson(value);
		String encrypted = SecureJsonStorage.encrypt(json, worldRoot);
		Path parent = path.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		Path backup = path.resolveSibling(path.getFileName() + ".bak");
		Files.writeString(temporary, encrypted, StandardCharsets.UTF_8);
		if (Files.isRegularFile(path)) {
			Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.COPY_ATTRIBUTES);
		}
		try {
			Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(temporary);
		}
	}
}
