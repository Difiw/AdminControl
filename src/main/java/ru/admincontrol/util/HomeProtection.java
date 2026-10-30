package ru.admincontrol.util;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;

import java.util.ArrayList;
import java.util.List;

public final class HomeProtection {
	public static final int MAX_SIZE = 150;
	private static volatile List<Guard> guards = List.of();

	private HomeProtection() {
	}

	private static final class Guard {
		final java.util.Set<String> build;
		final java.util.Set<String> use;
		final Box box;

		Guard(java.util.Set<String> allowed, Box box) {
			this(allowed, allowed, box);
		}

		Guard(java.util.Set<String> build, java.util.Set<String> use, Box box) {
			this.build = build == null ? java.util.Set.of() : build;
			this.use = use == null ? java.util.Set.of() : use;
			this.box = box;
		}

		boolean allows(java.util.Set<String> set, String uuid, String nick) {
			return set.contains(uuid == null ? "" : uuid.toLowerCase(java.util.Locale.ROOT))
					|| set.contains(nick == null ? "" : nick.toLowerCase(java.util.Locale.ROOT));
		}
	}

	private static void add(String uuid, String nick, java.util.Set<String> set) {
		if (uuid != null && !uuid.isBlank()) {
			set.add(uuid.toLowerCase(java.util.Locale.ROOT));
		}
		if (nick != null && !nick.isBlank()) {
			set.add(nick.toLowerCase(java.util.Locale.ROOT));
		}
	}

	public static final class Box {
		public final int minX;
		public final int minY;
		public final int minZ;
		public final int maxX;
		public final int maxY;
		public final int maxZ;
		public final String dimension;

		public Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, String dimension) {
			this.minX = minX;
			this.minY = minY;
			this.minZ = minZ;
			this.maxX = maxX;
			this.maxY = maxY;
			this.maxZ = maxZ;
			this.dimension = dimension == null ? "" : dimension;
		}
	}

	public static Box box(PlayerRecord record) {
		return box(record, MAX_SIZE);
	}

	public static Box box(PlayerRecord record, int maxSize) {
		if (record == null || record.home == null) {
			return null;
		}
		int size = Math.max(2, Math.min(Math.max(2, maxSize), record.homeSize <= 0 ? 5 : record.homeSize));
		int half = size / 2;
		int minX = (int) Math.floor(record.home.x) - half;
		int minY = (int) Math.floor(record.home.y) - half;
		int minZ = (int) Math.floor(record.home.z) - half;
		return new Box(minX, minY, minZ, minX + size - 1, minY + size - 1, minZ + size - 1, record.home.dimension);
	}

	public static void refresh() {
		DataManager data = DataManager.get();
		if (data == null) {
			guards = List.of();
			return;
		}
		List<Guard> next = new ArrayList<>();
		for (PrivateService.Area area : PrivateService.areas()) {
			java.util.Set<String> allowed = new java.util.HashSet<>();
			add(area.owner(), area.ownerNick(), allowed);
			next.add(new Guard(allowed, area.box()));
		}
		if (PlayFeatures.s().clansEnabled) {
			for (ru.admincontrol.data.ClanRecord clan : ClanService.clans()) {
				if (clan == null || clan.home == null || !clan.homePrivate) {
					continue;
				}
				if (ClanWarService.privateSuppressed(clan.id)) {
					continue;
				}
				PlayerRecord fake = new PlayerRecord();
				fake.home = clan.home;
				fake.homeSize = ClanBuildings.territorySize(clan);
				Box box = box(fake, 512);
				if (box == null) {
					continue;
				}
				java.util.Set<String> build = new java.util.HashSet<>();
				java.util.Set<String> use = new java.util.HashSet<>();
				add(clan.ownerUuid, clan.ownerNick, build);
				add(clan.ownerUuid, clan.ownerNick, use);
				if (clan.memberUuids != null) {
					for (int i = 0; i < clan.memberUuids.size(); i++) {
						String id = clan.memberUuids.get(i);
						String nick = clan.memberNicks != null && i < clan.memberNicks.size() ? clan.memberNicks.get(i) : "";
						int flags = ClanService.memberFlags(clan, id, nick);
						if ((flags & ru.admincontrol.data.ClanRecord.BUILD) != 0) {
							add(id, nick, build);
						}
						if ((flags & ru.admincontrol.data.ClanRecord.USE) != 0
								|| (flags & ru.admincontrol.data.ClanRecord.BUILD) != 0) {
							add(id, nick, use);
						}
					}
				}
				next.add(new Guard(build, use, box));
			}
		}
		guards = next;
	}

	public static boolean blocked(ServerPlayerEntity player, BlockPos pos) {
		return blocked(player, pos, true);
	}

	public static boolean blockedUse(ServerPlayerEntity player, BlockPos pos) {
		return blocked(player, pos, false);
	}

	public static boolean blocked(ServerPlayerEntity player, BlockPos pos, boolean build) {
		if (player == null || pos == null || EventMapEditor.allows(player, pos)) {
			return false;
		}
		String dim = player.getWorld().getRegistryKey().getValue().toString();
		String uuid = player.getUuid().toString();
		String nick = player.getGameProfile().getName();
		for (Guard guard : guards) {
			if (guard.allows(build ? guard.build : guard.use, uuid, nick)) {
				continue;
			}
			if (inside(guard.box, dim, pos)) {
				return true;
			}
		}
		return false;
	}

	public static boolean protectedAt(World world, BlockPos pos) {
		if (world == null || pos == null) {
			return false;
		}
		return protectedAt(world.getRegistryKey().getValue().toString(), pos);
	}

	public static boolean protectedAt(String dimension, BlockPos pos) {
		if (pos == null) {
			return false;
		}
		String dim = dimension == null ? "" : dimension;
		for (Guard guard : guards) {
			if (inside(guard.box, dim, pos)) {
				return true;
			}
		}
		return false;
	}

	public static void protectExplosions(World world, List<BlockPos> blocks) {
		if (world == null || blocks == null || blocks.isEmpty()) {
			return;
		}
		String dim = world.getRegistryKey().getValue().toString();
		blocks.removeIf(pos -> protectedAt(dim, pos));
	}

	private static boolean inside(Box box, String dim, BlockPos pos) {
		if (box == null || pos == null) {
			return false;
		}
		if (box.dimension != null && !box.dimension.isBlank()
				&& !dim.equalsIgnoreCase(box.dimension)) {
			return false;
		}
		return pos.getX() >= box.minX && pos.getX() <= box.maxX
				&& pos.getY() >= box.minY && pos.getY() <= box.maxY
				&& pos.getZ() >= box.minZ && pos.getZ() <= box.maxZ;
	}
}
