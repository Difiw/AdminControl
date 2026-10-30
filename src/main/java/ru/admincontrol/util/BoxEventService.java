package ru.admincontrol.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.ModSettings;
import ru.admincontrol.data.TrainingKitItem;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BoxEventService {
	private static State state;

	private BoxEventService() {
	}

	public enum Phase {
		WAIT, LIVE, CLAIMED
	}

	public static final class State {
		public Phase phase = Phase.WAIT;
		public UUID host;
		public String hostName = "";
		public String result = "";
		public long spawnAt;
		public long removeAt;
		public long expireAt;
		public ServerWorld world;
		public BlockPos center;
		public BlockPos chest;
		public UUID winner;
		public final List<Saved> saved = new ArrayList<>();
	}

	private static final class Saved {
		BlockPos pos;
		net.minecraft.block.BlockState block;
		NbtCompound be;
	}

	public static boolean active() {
		return state != null;
	}

	public static State current() {
		return state;
	}

	public static boolean inCube(BlockPos pos) {
		if (state == null || state.center == null || pos == null) {
			return false;
		}
		return Math.abs(pos.getX() - state.center.getX()) <= 3
				&& Math.abs(pos.getY() - state.center.getY()) <= 3
				&& Math.abs(pos.getZ() - state.center.getZ()) <= 3;
	}

	public static boolean isChest(BlockPos pos) {
		return state != null && state.chest != null && state.chest.equals(pos);
	}

	public static String start(MinecraftServer server, ServerPlayerEntity host) {
		if (active()) {
			return "Бокс уже идёт.";
		}
		if (server == null) {
			return "Сервер недоступен.";
		}
		long cooldown = cooldownRemainingMs();
		if (cooldown > 0L) {
			long seconds = (cooldown + 999L) / 1000L;
			return "Пауза между боксами: осталось " + FullStopLock.formatSec((int) Math.min(Integer.MAX_VALUE, seconds)) + ".";
		}
		if (host != null ? ServerStopService.blocks(host) : ServerStopService.active()) {
			return "Сервер останавливается — бокс недоступен.";
		}
		ModSettings s = settings();
		state = new State();
		state.host = host == null ? null : host.getUuid();
		state.phase = Phase.WAIT;
		int minutes = Math.max(1, s.boxWarnMinutes);
		state.spawnAt = System.currentTimeMillis() + minutes * 60_000L;
		int radius = Math.max(16, s.boxRadius);
		Chat.broadcast(server, Chat.gold("Через " + minutes + " мин начнётся бокс: в радиусе "
				+ radius + " блоков от глобального спавна выпадет контейнер."));
		state.hostName = host == null ? "авто-бокс" : host.getGameProfile().getName();
		JournalLog.add(JournalLog.BOX, "Запуск бокса · организатор " + state.hostName + " · появится через "
				+ minutes + " мин · радиус " + radius + " от спавна");
		if (host != null) {
			AdminNotify.playerAction(host, ru.admincontrol.data.ActionKind.ADMIN, "запустил бокс (появится через "
					+ minutes + " мин, радиус " + radius + ")");
			AdminStats.onBox(host);
		}
		if (DataManager.get() != null) {
			DataManager.get().markDirty();
		}
		return null;
	}

	public static String forceCancel(MinecraftServer server) {
		if (!active()) {
			return "Сейчас бокса нет.";
		}
		state.result = "отменён: полная остановка / остановка сервера";
		finish(server, true);
		if (server != null) {
			Chat.broadcast(server, Chat.yellow("Мероприятие «Бокс» отменено (/fullstop)."));
		}
		return null;
	}

	public static String statusLine() {
		if (!active()) {
			return "не идёт";
		}
		if (state.phase == Phase.WAIT) {
			long left = Math.max(0L, state.spawnAt - System.currentTimeMillis());
			int min = (int) ((left + 59_999L) / 60_000L);
			return "ожидание, появится через ~" + Math.max(1, min) + " мин.";
		}
		String where = state.center == null ? "?" : (state.center.getX() + " ~ " + state.center.getZ());
		long left = Math.max(0L, state.expireAt - System.currentTimeMillis());
		int min = (int) ((left + 59_999L) / 60_000L);
		if (state.phase == Phase.CLAIMED) {
			return "лутают на " + where + ", исчезнет через ~" + Math.max(1, min) + " мин.";
		}
		return "стоит на " + where + ", исчезнет через ~" + Math.max(1, min) + " мин.";
	}

	public static String cancel(ServerPlayerEntity actor) {
		if (!active()) {
			return "Сейчас бокса нет.";
		}
		int rank = actor == null ? AdminRanks.NONE : DataManager.get().getAdminLevel(actor);
		boolean host = actor != null && state.host != null && actor.getUuid().equals(state.host);
		if (state.phase == Phase.WAIT) {
			if (!host && rank < AdminRanks.TECH) {
				return "Отменить ожидание может только организатор или тех. админ+.";
			}
		} else if (rank < AdminRanks.GA) {
			return "После появления бокса его может снять только ГА+.";
		}
		MinecraftServer server = actor == null ? null : actor.getServer();
		state.result = "отменил администратор " + (actor == null ? "?" : actor.getGameProfile().getName());
		finish(server, true);
		if (server != null) {
			Chat.broadcast(server, Chat.yellow("Мероприятие «Бокс» отменено."));
		}
		return null;
	}

	public static void tick(MinecraftServer server) {
		if (!active() || server == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (state.phase == Phase.WAIT && now >= state.spawnAt) {
			String err = spawn(server);
			if (err != null) {
				Chat.broadcast(server, Chat.red("Бокс не появился: " + err));
				state.result = "не появился: " + err;
				finish(server, false);
			}
			return;
		}
		if (state.phase == Phase.LIVE || state.phase == Phase.CLAIMED) {
			if (state.expireAt <= 0L) {
				state.expireAt = now + Math.max(1, settings().boxLifetimeMinutes) * 60_000L;
			}
			long limit = state.expireAt;
			if (state.phase == Phase.CLAIMED && state.removeAt > 0L) {
				limit = limit <= 0L ? state.removeAt : Math.min(limit, state.removeAt);
			}
			if (limit > 0L && now >= limit) {
				state.result = state.phase == Phase.CLAIMED ? "завершён, сундук залутан" : "исчез, сундук никто не открыл";
				finish(server, true);
				Chat.broadcast(server, Chat.yellow("Бокс исчез."));
			}
		}
	}

	public static boolean allowBreak(ServerPlayerEntity player, BlockPos pos) {
		if (!active() || !inCube(pos)) {
			return true;
		}
		if (isChest(pos)) {
			markOpened(player);
			return true;
		}
		BlockState block = state.world == null ? null : state.world.getBlockState(pos);
		if (isWoolWall(block)) {
			if (holdingShears(player)) {
				return true;
			}
			if (player != null) {
				player.sendMessage(Chat.red("Стенки бокса можно ломать только ножницами."), true);
			}
			return false;
		}
		return block != null && block.isOf(Blocks.COBBLESTONE);
	}

	private static boolean isWoolWall(BlockState block) {
		return block != null && block.isOf(Blocks.WHITE_WOOL);
	}

	private static boolean holdingShears(ServerPlayerEntity player) {
		if (player == null) {
			return false;
		}
		return player.getMainHandStack().isOf(Items.SHEARS)
				|| player.getOffHandStack().isOf(Items.SHEARS);
	}

	public static boolean tryClaimUse(ServerPlayerEntity player, BlockPos pos) {
		if (!active() || player == null || !isChest(pos)) {
			return false;
		}
		markOpened(player);
		return false;
	}

	private static void markOpened(ServerPlayerEntity player) {
		if (!active() || player == null || state.phase != Phase.LIVE) {
			return;
		}
		state.phase = Phase.CLAIMED;
		state.winner = player.getUuid();
		int sec = Math.max(15, settings().boxClearSeconds);
		long lootGone = System.currentTimeMillis() + sec * 1000L;
		state.removeAt = state.expireAt > 0L ? Math.min(lootGone, state.expireAt) : lootGone;
		JournalLog.add(JournalLog.BOX, "Бокс (организатор " + state.hostName + "): победитель — первым открыл сундук "
				+ player.getGameProfile().getName());
		Chat.broadcast(player.getServer(), Chat.gold(player.getGameProfile().getName()
				+ " первым открыл сундук бокса. Куб исчезнет через "
				+ String.format(java.util.Locale.US, "%.1f", sec / 60.0) + " мин."));
	}

	private static String spawn(MinecraftServer server) {
		ServerWorld world = server.getOverworld();
		if (world == null) {
			return "нет обычного мира";
		}
		BlockPos spawn = world.getSpawnPos();
		int radius = Math.max(16, settings().boxRadius);
		BlockPos center = pickCenter(world, spawn, radius);
		if (center == null) {
			return "не удалось найти место";
		}
		state.world = world;
		state.center = center;
		state.chest = center;
		placeCube(world, center);
		fillChest(world, center);
		state.phase = Phase.LIVE;
		int life = Math.max(1, settings().boxLifetimeMinutes);
		state.expireAt = System.currentTimeMillis() + life * 60_000L;
		Chat.broadcast(server, Chat.gold("Бокс появился на координатах: " + center.getX() + " ~ " + center.getZ()
				+ ". Исчезнет через " + life + " мин."));
		JournalLog.add(JournalLog.BOX, "Бокс (организатор " + state.hostName + ") появился: "
				+ world.getRegistryKey().getValue() + " " + center.getX() + ", " + center.getY() + ", " + center.getZ());
		return null;
	}

	private static BlockPos pickCenter(ServerWorld world, BlockPos spawn, int radius) {
		for (int i = 0; i < 48; i++) {
			double angle = world.random.nextDouble() * Math.PI * 2.0;
			double dist = world.random.nextDouble() * radius;
			int x = spawn.getX() + (int) Math.round(Math.cos(angle) * dist);
			int z = spawn.getZ() + (int) Math.round(Math.sin(angle) * dist);
			world.getChunk(x >> 4, z >> 4);
			BlockPos surface = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
					new BlockPos(x, world.getBottomY(), z));
			int bottom = surface.getY();
			int cy = bottom + 3;
			if (cy - 3 <= world.getBottomY() + 1 || cy + 3 >= world.getTopY() - 2) {
				continue;
			}
			return new BlockPos(x, cy, z);
		}
		int x = spawn.getX();
		int z = spawn.getZ();
		BlockPos surface = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawn);
		return new BlockPos(x, Math.max(world.getBottomY() + 4, surface.getY() + 3), z);
	}

	private static void placeCube(ServerWorld world, BlockPos center) {
		for (int dx = -3; dx <= 3; dx++) {
			for (int dy = -3; dy <= 3; dy++) {
				for (int dz = -3; dz <= 3; dz++) {
					BlockPos pos = center.add(dx, dy, dz);
					boolean shell = Math.abs(dx) == 3 || Math.abs(dy) == 3 || Math.abs(dz) == 3;
					boolean chest = dx == 0 && dy == 0 && dz == 0;
					boolean lid = dx == 0 && dy == 1 && dz == 0;
					place(world, pos, chest ? Blocks.CHEST.getDefaultState()
							: (lid ? Blocks.AIR.getDefaultState()
							: (shell ? Blocks.WHITE_WOOL.getDefaultState() : Blocks.COBBLESTONE.getDefaultState())));
				}
			}
		}
	}

	private static void place(ServerWorld world, BlockPos pos, net.minecraft.block.BlockState next) {
		Saved s = new Saved();
		s.pos = pos.toImmutable();
		s.block = world.getBlockState(pos);
		BlockEntity be = world.getBlockEntity(pos);
		if (be != null) {
			s.be = be.createNbt();
		}
		state.saved.add(s);
		world.removeBlockEntity(pos);
		world.setBlockState(pos, next, 3);
	}

	private static void fillChest(ServerWorld world, BlockPos pos) {
		if (!(world.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
			return;
		}
		List<TrainingKitItem> kit = TrainingConfig.randomBoxKit();
		int slot = 0;
		for (TrainingKitItem spec : kit) {
			if (slot >= chest.size()) {
				break;
			}
			ItemStack stack = TrainingConfig.stackOf(spec);
			if (!stack.isEmpty()) {
				chest.setStack(slot++, stack);
			}
		}
		chest.markDirty();
	}

	private static void finish(MinecraftServer server, boolean restore) {
		if (state == null) {
			return;
		}
		JournalLog.add(JournalLog.BOX, "Итог бокса (организатор " + state.hostName + "): "
				+ (state.result == null || state.result.isBlank() ? "завершён" : state.result));
		if (restore && state.world != null) {
			clearChestSilent();
			ServerWorld world = state.world;
			BlockPos center = state.center;
			int flags = Block.NOTIFY_ALL | Block.SKIP_DROPS;
			for (int i = state.saved.size() - 1; i >= 0; i--) {
				Saved s = state.saved.get(i);
				world.removeBlockEntity(s.pos);
				world.setBlockState(s.pos, s.block, flags);
				if (s.be != null) {
					BlockEntity be = world.getBlockEntity(s.pos);
					if (be != null) {
						be.readNbt(s.be);
						be.markDirty();
					}
				}
			}
			if (center != null) {
				Box area = new Box(center.getX() - 4, center.getY() - 4, center.getZ() - 4,
						center.getX() + 5, center.getY() + 5, center.getZ() + 5);
				for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, area, entity -> true)) {
					item.discard();
				}
			}
		}
		state = null;
		ModSettings settings = settings();
		settings.boxNextAllowedAt = System.currentTimeMillis() + Math.max(0, settings.boxCooldownMinutes) * 60_000L;
		if (DataManager.get() != null) DataManager.get().saveAll();
	}

	public static long cooldownRemainingMs() {
		return Math.max(0L, settings().boxNextAllowedAt - System.currentTimeMillis());
	}

	private static void clearChestSilent() {
		if (state == null || state.world == null || state.chest == null) {
			return;
		}
		if (state.world.getBlockEntity(state.chest) instanceof ChestBlockEntity chest) {
			chest.clear();
			chest.markDirty();
		}
	}

	private static ModSettings settings() {
		DataManager data = DataManager.get();
		return data == null ? new ModSettings() : data.settings();
	}
}
