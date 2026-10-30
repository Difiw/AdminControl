package ru.admincontrol.util;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySetHeadYawS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerSpawnS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.mixin.PlayerManagerAccessor;
import ru.admincontrol.net.ModPackets;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class NpcService {
	private static final Pattern NICK = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
	private static final ConcurrentHashMap<UUID, NpcPlayerEntity> BY_ID = new ConcurrentHashMap<>();
	private static final Map<UUID, Control> CONTROLS = new ConcurrentHashMap<>();

	private NpcService() {
	}

	private static final class Control {
		UUID npc;
		GameMode mode;
		double x;
		double y;
		double z;
		float yaw;
		float pitch;
		String dimension;
		boolean invisible;
		boolean invulnerable;
		boolean noClip;
		String inventory = "";
		int selectedSlot;
		float health;
		int food;
		float saturation;
		int xpLevel;
		int xpTotal;
		float xpProgress;
		List<StatusEffectInstance> effects = new ArrayList<>();
	}

	public static boolean isNpc(ServerPlayerEntity player) {
		return player instanceof NpcPlayerEntity;
	}

	public static boolean isControlling(ServerPlayerEntity admin) {
		return admin != null && CONTROLS.containsKey(admin.getUuid());
	}

	/** Больше не используется: админ сам ходит, NPC только копирует позу. */
	public static boolean isParked(ServerPlayerEntity player) {
		return false;
	}

	/** Клиент больше не подключается к сущности NPC. */
	public static boolean isClientDriven(ServerPlayerEntity player) {
		return false;
	}

	public static boolean isIdleNpc(ServerPlayerEntity player) {
		return isNpc(player) && !isControlled(player);
	}

	public static ServerPlayerEntity moneyPlayer(ServerPlayerEntity player) {
		NpcPlayerEntity npc = controlledNpc(player);
		return npc != null ? npc : player;
	}

	public static boolean shouldHideNpcFrom(Entity entity, ServerPlayerEntity viewer) {
		return entity instanceof NpcPlayerEntity npc && viewer != null && viewer == controllerOf(npc);
	}

	public static boolean shouldHideControllerFrom(Entity entity, ServerPlayerEntity viewer) {
		if (!(entity instanceof ServerPlayerEntity admin) || viewer == null || viewer == admin || isNpc(admin)) {
			return false;
		}
		return isControlling(admin);
	}

	/** Игрок, чьё соединение реально за пакетом (админ, даже если handler.player = NPC). */
	public static ServerPlayerEntity sessionAdmin(ServerPlayerEntity packetPlayer) {
		if (packetPlayer == null) {
			return null;
		}
		if (isControlling(packetPlayer)) {
			return packetPlayer;
		}
		ServerPlayerEntity admin = controllerOf(packetPlayer);
		return admin != null ? admin : packetPlayer;
	}

	public static boolean isControlled(ServerPlayerEntity npc) {
		if (npc == null) {
			return false;
		}
		for (Control control : CONTROLS.values()) {
			if (npc.getUuid().equals(control.npc)) {
				return true;
			}
		}
		return false;
	}

	public static NpcPlayerEntity controlledNpc(ServerPlayerEntity admin) {
		if (admin == null || admin.getServer() == null) {
			return null;
		}
		Control control = CONTROLS.get(admin.getUuid());
		if (control == null) {
			return null;
		}
		ServerPlayerEntity player = admin.getServer().getPlayerManager().getPlayer(control.npc);
		return player instanceof NpcPlayerEntity npc ? npc : null;
	}

	public static ServerPlayerEntity controllerOf(ServerPlayerEntity npc) {
		if (npc == null || npc.getServer() == null) {
			return null;
		}
		for (Map.Entry<UUID, Control> entry : CONTROLS.entrySet()) {
			if (npc.getUuid().equals(entry.getValue().npc)) {
				return npc.getServer().getPlayerManager().getPlayer(entry.getKey());
			}
		}
		return null;
	}

	public static NpcPlayerEntity find(MinecraftServer server, String nick) {
		if (server == null || nick == null || nick.isBlank()) {
			return null;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (player instanceof NpcPlayerEntity npc
					&& npc.getGameProfile().getName().equalsIgnoreCase(nick)) {
				return npc;
			}
		}
		return null;
	}

	public static List<String> names(MinecraftServer server) {
		List<String> names = new ArrayList<>();
		if (server == null) {
			return names;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (player instanceof NpcPlayerEntity) {
				names.add(player.getGameProfile().getName());
			}
		}
		return names;
	}

	public static String spawn(ServerPlayerEntity admin, String nick) {
		if (admin == null || admin.getServer() == null) {
			return "Команду может выполнить только игрок.";
		}
		if (nick == null || !NICK.matcher(nick).matches()) {
			return "Ник NPC: 1–16 символов, латиница, цифры и _.";
		}
		MinecraftServer server = admin.getServer();
		if (CommandUtil.findOnline(server, nick).isPresent() && find(server, nick) == null) {
			return "Игрок с ником «" + nick + "» уже в сети.";
		}
		if (find(server, nick) != null) {
			return "NPC «" + nick + "» уже существует.";
		}
		if (DataManager.get() != null && DataManager.get().nickTakenByPlayer(nick)) {
			return "Ник «" + nick + "» уже занят игроком.";
		}
		Vec3d look = admin.getRotationVec(1.0F);
		return create(server, admin.getServerWorld(), nick,
				admin.getX() + look.x * 2.5, admin.getY(), admin.getZ() + look.z * 2.5,
				admin.getYaw(), admin.getPitch(), NpcStore.get(nick));
	}

	public static String spawnSkinned(ServerPlayerEntity admin, String skinId, String npcName) {
		if (admin == null || admin.getServer() == null) return "Команду может выполнить только игрок.";
		if (CommandUtil.findOnline(admin.getServer(), npcName).isPresent() || find(admin.getServer(), npcName) != null)
			return "ID NPC уже занят: " + npcName;
		Vec3d look = admin.getRotationVec(1.0F);
		NpcStore.Saved saved = new NpcStore.Saved();
		saved.skinId = skinId;
		NpcStore.setSkin(npcName, skinId);
		String error = create(admin.getServer(), admin.getServerWorld(), npcName,
				admin.getX() + look.x * 2.5, admin.getY(), admin.getZ() + look.z * 2.5,
				0.0F, 0.0F, saved);
		if (error == null) {
			NpcPlayerEntity npc = find(admin.getServer(), npcName);
			npc.setBodyYaw(0.0F);
			npc.setHeadYaw(0.0F);
			npc.setPitch(0.0F);
			NpcStore.capture(npc);
			NpcStore.save();
			CwNpcService.syncSkinToAll(admin.getServer(), npc.getUuid(), skinId);
		} else {
			NpcStore.forget(npcName);
		}
		return error;
	}

	public static NpcPlayerEntity findById(MinecraftServer server, String id) {
		if (server == null || id == null) return null;
		try {
			UUID uuid = UUID.fromString(id);
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
			return player instanceof NpcPlayerEntity npc ? npc : null;
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	/** Вернуть в мир все сохранённые NPC с их собственным состоянием. */
	public static void restoreAll(MinecraftServer server) {
		if (server == null) {
			return;
		}
		for (NpcStore.Saved saved : NpcStore.all()) {
			if (saved == null || saved.nick == null || !NICK.matcher(saved.nick).matches() || find(server, saved.nick) != null) {
				continue;
			}
			if (CommandUtil.findOnline(server, saved.nick).isPresent()) {
				continue;
			}
			ServerWorld world = SpawnService.resolveWorld(server, saved.dimension);
			String error = create(server, world, saved.nick, saved.x, saved.y, saved.z, saved.yaw, saved.pitch, saved);
			if (error != null) {
				AdminControlMod.LOGGER.warn("NPC «{}» не восстановлен: {}", saved.nick, error);
			}
		}
	}

	public static String create(MinecraftServer server, ServerWorld world, String nick, double x, double y, double z,
			float yaw, float pitch, NpcStore.Saved saved) {
		evictName(server, nick);
		ServerPlayerEntity mapped = server.getPlayerManager().getPlayer(uuidFor(nick));
		if (mapped instanceof NpcPlayerEntity leftover) {
			detach(leftover);
		}
		NpcStore.adoptRecord(nick);
		GameProfile profile = new GameProfile(uuidFor(nick), nick);
		NpcPlayerEntity npc = new NpcPlayerEntity(server, world, profile);
		BY_ID.put(npc.getUuid(), npc);
		try {
			npc.prepare();
			NpcStore.apply(npc, saved);
			joinNpc(server, world, npc);
			NpcStore.applyAfterJoin(npc, saved);
			final NpcPlayerEntity spawned = npc;
			WorldGate.allow(() -> spawned.teleport(world, x, y, z, yaw, pitch));
			npc.setVelocity(0, 0, 0);
			npc.velocityModified = true;
			if (npc.networkHandler != null) {
				npc.networkHandler.syncWithPlayerPosition();
			}
			world.getChunkManager().updatePosition(npc);
			broadcastNpc(npc);
			TabListColors.refresh(server);
			if (DataManager.get() != null) {
				DataManager.get().getOrCreate(npc);
			}
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("Не удалось создать NPC «{}»", nick, e);
			BY_ID.remove(npc.getUuid());
			detach(npc);
			String detail = e.getMessage() == null || e.getMessage().isBlank()
					? e.getClass().getSimpleName() : e.getMessage();
			return "Не удалось создать NPC: " + detail;
		}
		if (find(server, nick) == null) {
			BY_ID.remove(npc.getUuid());
			detach(npc);
			return "Не удалось создать NPC: игрок не появился в списке сервера.";
		}
		persist(npc);
		return null;
	}

	private static void persist(NpcPlayerEntity npc) {
		NpcStore.capture(npc);
		NpcStore.save();
	}

	public static String bringHere(ServerPlayerEntity admin, String nick) {
		if (admin == null || admin.getServer() == null) {
			return "Команду может выполнить только игрок.";
		}
		NpcPlayerEntity npc = find(admin.getServer(), nick);
		if (npc == null) {
			return "NPC «" + nick + "» не найден.";
		}
		if (isControlled(npc) && controlledNpc(admin) != npc) {
			return "Этим NPC уже управляет другой администратор.";
		}
		Vec3d look = admin.getRotationVec(1.0F);
		double x = admin.getX() + look.x * 2.5;
		double y = admin.getY();
		double z = admin.getZ() + look.z * 2.5;
		WorldGate.allow(() -> npc.teleport(admin.getServerWorld(), x, y, z, admin.getYaw(), admin.getPitch()));
		npc.setVelocity(0, 0, 0);
		npc.velocityModified = true;
		broadcastNpc(npc);
		if (!isControlled(npc)) {
			persist(npc);
		}
		return null;
	}

	public static String remove(MinecraftServer server, String nick) {
		NpcPlayerEntity npc = findById(server, nick);
		if (npc == null) npc = find(server, nick);
		if (npc == null) {
			return "NPC «" + nick + "» не найден.";
		}
		String actualName = npc.getGameProfile().getName();
		detach(npc);
		NpcStore.forget(actualName);
		return null;
	}

	public static String act(ServerPlayerEntity admin, String nick, String raw) {
		if (admin == null || admin.getServer() == null) {
			return "Команду может выполнить только игрок.";
		}
		NpcPlayerEntity npc = find(admin.getServer(), nick);
		if (npc == null) {
			return "NPC «" + nick + "» не найден.";
		}
		String text = raw == null ? "" : raw.trim();
		if (text.isEmpty()) {
			return "Укажите команду (с /) или текст в чат.";
		}
		if (text.startsWith("/")) {
			String cmd = text.substring(1).trim();
			if (cmd.isEmpty()) {
				return "Укажите команду после /.";
			}
			String name = cmd.split("\\s+")[0].toLowerCase(Locale.ROOT);
			if (name.equals("npc") || name.equals("control") || name.equals("uncontrol")
					|| name.equals("addnpc") || name.equals("delnpc")) {
				return "Эту команду нельзя выполнять от имени NPC.";
			}
			try {
				ServerCommandSource source = new ServerCommandSource(
						admin,
						npc.getPos(),
						new Vec2f(npc.getPitch(), npc.getYaw()),
						npc.getServerWorld(),
						4,
						npc.getGameProfile().getName(),
						npc.getDisplayName(),
						admin.getServer(),
						npc);
				admin.getServer().getCommandManager().getDispatcher().execute(cmd, source);
			} catch (Exception e) {
				return e.getMessage() == null ? "Не удалось выполнить команду." : e.getMessage();
			}
			return null;
		}
		if (text.length() > 256) {
			return "Сообщение слишком длинное.";
		}
		Chat.broadcast(admin.getServer(), Text.translatable("chat.type.text", npc.getDisplayName(), text));
		return null;
	}

	public static String startControl(ServerPlayerEntity admin, String nick) {
		if (admin == null || admin.getServer() == null) {
			return "Команду может выполнить только игрок.";
		}
		if (isNpc(admin)) {
			return "Сначала выйдите из NPC: /uncontrol";
		}
		NpcPlayerEntity npc = find(admin.getServer(), nick);
		if (npc == null) {
			return "NPC «" + nick + "» не найден.";
		}
		for (Map.Entry<UUID, Control> entry : CONTROLS.entrySet()) {
			if (npc.getUuid().equals(entry.getValue().npc) && !entry.getKey().equals(admin.getUuid())) {
				return "Этим NPC уже управляет другой администратор.";
			}
		}
		if (SpectateService.isSpectating(admin)) {
			SpectateService.stop(admin);
		}
		if (isControlling(admin)) {
			stopControl(admin, true);
		}
		Control saved = snapshotAdmin(admin);
		saved.npc = npc.getUuid();
		CONTROLS.put(admin.getUuid(), saved);
		admin.closeHandledScreen();
		npc.closeHandledScreen();
		moveEffects(npc, admin);
		SkillTreeService.applyEffects(admin);
		InvButtons.sync(admin);
		copyLiving(npc, admin);
		InventoryUtil.restore(admin, InventoryUtil.serialize(npc));
		admin.getInventory().selectedSlot = npc.getInventory().selectedSlot;
		admin.changeGameMode(GameMode.SURVIVAL);
		admin.getAbilities().flying = false;
		admin.getAbilities().allowFlying = false;
		admin.getAbilities().invulnerable = false;
		admin.sendAbilitiesUpdate();
		WorldGate.allow(() -> admin.teleport(npc.getServerWorld(), npc.getX(), npc.getY(), npc.getZ(),
				npc.getYaw(), npc.getPitch()));
		admin.setVelocity(Vec3d.ZERO);
		admin.velocityModified = true;
		admin.setInvisible(false);
		admin.setInvulnerable(false);
		admin.noClip = false;
		admin.setCameraEntity(admin);
		npc.noClip = true;
		npc.setInvisible(false);
		npc.setNoGravity(false);
		driveNpc(npc, admin);
		hideNpcFromController(admin, npc);
		BalanceBoard.refresh(admin, true);
		syncControlHud(admin, true);
		Chat.send(admin, Chat.gold("Вы управляете NPC «" + npc.getGameProfile().getName() + "»."));
		Chat.send(admin, Chat.gray("Ходьба, ломание блоков, удары и инвентарь работают как в выживании."));
		Chat.send(admin, Chat.gray("Чат, WC, сила, удача, /board, HP, голод, опыт, инвентарь, эффекты, дерево навыков и достижения — у NPC и сохраняются для следующего админа. Ваши эффекты вернутся после выхода: /uncontrol"));
		return null;
	}

	public static boolean stopControl(ServerPlayerEntity admin) {
		return stopControl(admin, true);
	}

	public static boolean stopControl(ServerPlayerEntity admin, boolean restore) {
		if (admin == null) {
			return false;
		}
		Control saved = CONTROLS.remove(admin.getUuid());
		if (saved == null) {
			syncControlHud(admin, false);
			return false;
		}
		NpcPlayerEntity npc = null;
		if (admin.getServer() != null) {
			ServerPlayerEntity found = admin.getServer().getPlayerManager().getPlayer(saved.npc);
			if (found instanceof NpcPlayerEntity controlled) {
				npc = controlled;
			}
		}
		syncControlHud(admin, false);
		BalanceBoard.hide(admin);
		if (npc != null) {
			SkillTreeService.applyEffects(npc);
			moveEffects(admin, npc);
			copyLiving(admin, npc);
			InventoryUtil.restore(npc, InventoryUtil.serialize(admin));
			npc.getInventory().selectedSlot = admin.getInventory().selectedSlot;
			if (DataManager.get() != null) {
				InventoryUtil.snapshot(npc, DataManager.get().getOrCreate(npc));
			}
			npc.noClip = false;
			npc.setInvisible(false);
			npc.setNoGravity(false);
			npc.setCameraEntity(npc);
			showNpcTo(admin, npc);
			broadcastNpc(npc);
			persist(npc);
		}
		if (saved.inventory != null) {
			InventoryUtil.restore(admin, saved.inventory);
			admin.getInventory().selectedSlot = saved.selectedSlot;
		}
		if (npc == null) {
			admin.clearStatusEffects();
		}
		for (StatusEffectInstance effect : saved.effects) {
			admin.addStatusEffect(effect);
		}
		SkillTreeService.applyEffects(admin);
		InvButtons.sync(admin);
		admin.closeHandledScreen();
		admin.setCameraEntity(admin);
		admin.setSilent(false);
		admin.setInvisible(saved.invisible);
		admin.setInvulnerable(saved.invulnerable);
		admin.noClip = saved.noClip;
		admin.getAbilities().invulnerable = saved.invulnerable;
		if (!restore) {
			return true;
		}
		restoreVitals(admin, saved);
		admin.sendAbilitiesUpdate();
		ServerWorld world = SpawnService.resolveWorld(admin.getServer(), saved.dimension);
		WorldGate.allow(() -> admin.teleport(world, saved.x, saved.y, saved.z, saved.yaw, saved.pitch));
		if (saved.mode != null) {
			admin.changeGameMode(saved.mode);
		}
		BalanceBoard.refresh(admin, true);
		return true;
	}

	public static void forget(ServerPlayerEntity player) {
		if (player != null) {
			CONTROLS.remove(player.getUuid());
		}
	}

	public static void tick(MinecraftServer server) {
		if (server == null) {
			return;
		}
		if (server.getTicks() % 1200 == 0) {
			persistAll(server);
		}
		for (UUID adminId : List.copyOf(CONTROLS.keySet())) {
			ServerPlayerEntity admin = server.getPlayerManager().getPlayer(adminId);
			Control control = CONTROLS.get(adminId);
			if (admin == null || control == null) {
				CONTROLS.remove(adminId);
				continue;
			}
			ServerPlayerEntity target = server.getPlayerManager().getPlayer(control.npc);
			if (!(target instanceof NpcPlayerEntity npc) || npc.isRemoved()) {
				stopControl(admin, true);
				Chat.send(admin, Chat.yellow("NPC исчез. Управление прекращено."));
				continue;
			}
			if (admin.isDead() || npc.isDead() || npc.getHealth() <= 0.0F) {
				continue;
			}
			if (admin.interactionManager.getGameMode() != GameMode.SURVIVAL) {
				admin.changeGameMode(GameMode.SURVIVAL);
			}
			admin.getAbilities().flying = false;
			admin.getAbilities().allowFlying = false;
			admin.getAbilities().invulnerable = false;
			admin.setInvisible(false);
			admin.setInvulnerable(false);
			admin.noClip = false;
			admin.setCameraEntity(admin);
			driveNpc(npc, admin);
			hideNpcFromController(admin, npc);
			if (server.getTicks() % 20 == 0) {
				syncControlHud(admin, true);
				BalanceBoard.refresh(admin, true);
			}
		}
	}

	public static void evictName(MinecraftServer server, String nick) {
		NpcPlayerEntity npc = find(server, nick);
		if (npc != null) {
			detach(npc);
		}
	}

	private static void persistAll(MinecraftServer server) {
		boolean any = false;
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (player instanceof NpcPlayerEntity npc && !npc.isRemoved()) {
				NpcStore.capture(npc);
				any = true;
			}
		}
		if (any) {
			NpcStore.save();
		}
	}

	public static void removeAll(MinecraftServer server) {
		if (server != null) {
			for (UUID adminId : List.copyOf(CONTROLS.keySet())) {
				ServerPlayerEntity admin = server.getPlayerManager().getPlayer(adminId);
				if (admin != null) {
					stopControl(admin, true);
				} else {
					CONTROLS.remove(adminId);
				}
			}
			persistAll(server);
			for (String name : names(server)) {
				NpcPlayerEntity npc = find(server, name);
				if (npc != null) {
					detach(npc);
				}
			}
		}
		BY_ID.clear();
		CONTROLS.clear();
	}

	public static void possessedDied(ServerPlayerEntity admin, DamageSource source) {
		if (admin == null || !isControlling(admin)) {
			return;
		}
		stopControl(admin, true);
	}

	public static void respawn(NpcPlayerEntity npc) {
		if (npc == null || npc.getServer() == null) {
			return;
		}
		ServerWorld world = npc.getServer().getOverworld();
		if (world == null) {
			world = npc.getServerWorld();
		}
		var pos = world.getSpawnPos();
		npc.prepare();
		final ServerWorld dest = world;
		WorldGate.allow(() -> npc.teleport(dest, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F));
		npc.setVelocity(Vec3d.ZERO);
		npc.velocityModified = true;
		npc.fallDistance = 0.0F;
		broadcastNpc(npc);
		persist(npc);
	}

	static void detach(NpcPlayerEntity npc) {
		if (npc == null) {
			return;
		}
		MinecraftServer server = npc.getServer();
		if (server != null) {
			for (UUID adminId : List.copyOf(CONTROLS.keySet())) {
				Control control = CONTROLS.get(adminId);
				if (control != null && npc.getUuid().equals(control.npc)) {
					ServerPlayerEntity admin = server.getPlayerManager().getPlayer(adminId);
					if (admin != null) {
						stopControl(admin, true);
						Chat.send(admin, Chat.yellow("NPC удалён. Управление прекращено."));
					} else {
						CONTROLS.remove(adminId);
					}
				}
			}
		}
		BY_ID.remove(npc.getUuid());
		if (DataManager.get() != null) {
			DataManager.get().forgetNps(npc);
		}
		if (server != null) {
			try {
				server.getPlayerManager().remove(npc);
			} catch (RuntimeException ignored) {
			}
		}
		if (!npc.isRemoved()) {
			npc.discard();
		}
	}

	private static void joinNpc(MinecraftServer server, ServerWorld world, NpcPlayerEntity npc) {
		PlayerManager manager = server.getPlayerManager();
		PlayerManagerAccessor lists = (PlayerManagerAccessor) manager;
		NpcClientConnection connection = new NpcClientConnection();
		new NpcNetworkHandler(server, connection, npc);
		if (!lists.adminControl$players().contains(npc)) {
			lists.adminControl$players().add(npc);
		}
		lists.adminControl$playerMap().put(npc.getUuid(), npc);
		world.onPlayerConnected(npc);
		world.getChunkManager().updatePosition(npc);
		manager.sendToAll(new PlayerListS2CPacket(EnumSet.of(
				PlayerListS2CPacket.Action.ADD_PLAYER,
				PlayerListS2CPacket.Action.UPDATE_LISTED,
				PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME,
				PlayerListS2CPacket.Action.UPDATE_GAME_MODE), List.of(npc)));
		PlayerSpawnS2CPacket spawn = new PlayerSpawnS2CPacket(npc);
		for (ServerPlayerEntity viewer : manager.getPlayerList()) {
			if (viewer != npc && viewer.getWorld() == world && viewer.networkHandler != null) {
				viewer.networkHandler.sendPacket(spawn);
			}
		}
	}

	private static void broadcastNpc(NpcPlayerEntity npc) {
		if (npc == null || npc.getServer() == null) {
			return;
		}
		var manager = npc.getServerWorld().getChunkManager();
		manager.updatePosition(npc);
		manager.sendToNearbyPlayers(npc, new EntityPositionS2CPacket(npc));
		manager.sendToNearbyPlayers(npc,
				new EntitySetHeadYawS2CPacket(npc, (byte) (npc.getHeadYaw() * 256.0F / 360.0F)));
		manager.sendToNearbyPlayers(npc, new EntityVelocityUpdateS2CPacket(npc));
	}

	private static Control snapshotAdmin(ServerPlayerEntity admin) {
		Control saved = new Control();
		saved.mode = admin.interactionManager.getGameMode();
		saved.x = admin.getX();
		saved.y = admin.getY();
		saved.z = admin.getZ();
		saved.yaw = admin.getYaw();
		saved.pitch = admin.getPitch();
		saved.dimension = admin.getServerWorld().getRegistryKey().getValue().toString();
		saved.invisible = admin.isInvisible();
		saved.invulnerable = admin.getAbilities().invulnerable;
		saved.noClip = admin.noClip;
		saved.inventory = InventoryUtil.serialize(admin);
		saved.selectedSlot = admin.getInventory().selectedSlot;
		saved.health = admin.getHealth();
		saved.food = admin.getHungerManager().getFoodLevel();
		saved.saturation = admin.getHungerManager().getSaturationLevel();
		saved.xpLevel = admin.experienceLevel;
		saved.xpTotal = admin.totalExperience;
		saved.xpProgress = admin.experienceProgress;
		for (StatusEffectInstance effect : admin.getStatusEffects()) {
			saved.effects.add(new StatusEffectInstance(effect));
		}
		return saved;
	}

	/** Эффекты принадлежат телу: все эффекты {@code from} переходят на {@code to}, свои у {@code to} снимаются. */
	private static void moveEffects(ServerPlayerEntity from, ServerPlayerEntity to) {
		List<StatusEffectInstance> moving = new ArrayList<>();
		for (StatusEffectInstance effect : from.getStatusEffects()) {
			moving.add(new StatusEffectInstance(effect));
		}
		from.clearStatusEffects();
		to.clearStatusEffects();
		for (StatusEffectInstance effect : moving) {
			to.addStatusEffect(effect);
		}
	}

	public static void holdParked(ServerPlayerEntity admin) {
	}

	private static void copyLiving(ServerPlayerEntity from, ServerPlayerEntity to) {
		if (from == null || to == null) {
			return;
		}
		to.setHealth(Math.max(0.1F, from.getHealth()));
		to.getHungerManager().setFoodLevel(from.getHungerManager().getFoodLevel());
		to.getHungerManager().setSaturationLevel(from.getHungerManager().getSaturationLevel());
		to.experienceLevel = from.experienceLevel;
		to.totalExperience = from.totalExperience;
		to.experienceProgress = from.experienceProgress;
	}

	private static void driveNpc(NpcPlayerEntity npc, ServerPlayerEntity admin) {
		if (npc == null || admin == null) {
			return;
		}
		npc.noClip = true;
		npc.setInvisible(false);
		npc.setNoGravity(false);
		npc.refreshPositionAndAngles(admin.getX(), admin.getY(), admin.getZ(), admin.getYaw(), admin.getPitch());
		npc.setHeadYaw(admin.getHeadYaw());
		npc.setBodyYaw(admin.getBodyYaw());
		npc.setVelocity(admin.getVelocity());
		npc.velocityModified = true;
		copyLiving(admin, npc);
		InventoryUtil.restore(npc, InventoryUtil.serialize(admin));
		npc.getInventory().selectedSlot = admin.getInventory().selectedSlot;
		broadcastNpc(npc);
	}

	private static void restoreVitals(ServerPlayerEntity player, Control saved) {
		player.setHealth(Math.max(1.0F, saved.health));
		player.getHungerManager().setFoodLevel(saved.food);
		player.getHungerManager().setSaturationLevel(saved.saturation);
		player.experienceLevel = saved.xpLevel;
		player.totalExperience = saved.xpTotal;
		player.experienceProgress = saved.xpProgress;
	}

	private static void hideNpcFromController(ServerPlayerEntity admin, NpcPlayerEntity npc) {
		if (admin == null || npc == null || admin.networkHandler == null) {
			return;
		}
		admin.networkHandler.sendPacket(new EntitiesDestroyS2CPacket(npc.getId()));
	}

	private static void showNpcTo(ServerPlayerEntity admin, NpcPlayerEntity npc) {
		if (admin == null || npc == null || admin.networkHandler == null || admin.getWorld() != npc.getWorld()) {
			return;
		}
		admin.networkHandler.sendPacket(new PlayerSpawnS2CPacket(npc));
		admin.networkHandler.sendPacket(new EntitySetHeadYawS2CPacket(npc, (byte) (npc.getHeadYaw() * 256.0F / 360.0F)));
	}

	public static void onDisconnect(ServerPlayNetworkHandler handler) {
		if (handler == null || handler.player == null) {
			return;
		}
		try {
			ServerPlayerEntity admin = sessionAdmin(handler.player);
			if (admin != null && isControlling(admin)) {
				stopControl(admin, true);
			}
		} catch (RuntimeException ignored) {
		}
	}

	private static void syncControlHud(ServerPlayerEntity admin, boolean on) {
		if (admin == null || admin.networkHandler == null) {
			return;
		}
		NpcPlayerEntity npc = on ? controlledNpc(admin) : null;
		var buf = PacketByteBufs.create();
		buf.writeBoolean(on);
		buf.writeString(npc == null ? "" : npc.getGameProfile().getName(), 16);
		buf.writeFloat(on && npc != null ? npc.getHealth() : 0f);
		buf.writeVarInt(on && npc != null ? npc.getHungerManager().getFoodLevel() : 0);
		ServerPlayNetworking.send(admin, ModPackets.NPC_CONTROL, buf);
	}

	private static UUID uuidFor(String nick) {
		return UUID.nameUUIDFromBytes(("AdminControlNps:" + nick.toLowerCase(Locale.ROOT))
				.getBytes(StandardCharsets.UTF_8));
	}
}
