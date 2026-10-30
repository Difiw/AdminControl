package ru.admincontrol.util;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public final class TrainingArena {
	public static final int SIZE = 20;
	public static final int HEIGHT = 8;

	private final List<Saved> saved = new ArrayList<>();
	public ServerWorld world;
	public int minX;
	public int minY;
	public int minZ;
	public int maxX;
	public int maxY;
	public int maxZ;

	private static final class Saved {
		BlockPos pos;
		BlockState state;
		NbtCompound be;
	}

	public void build(ServerWorld world, BlockPos stand) {
		this.world = world;
		// getTopY() is exclusive: the roof belongs at the last placeable block Y.
		int top = world.getTopY() - 1;
		int bottom = world.getBottomY() + 2;
		int y = Math.max(bottom, top - HEIGHT + 1);
		this.minX = stand.getX();
		this.minZ = stand.getZ();
		this.minY = y;
		this.maxX = this.minX + SIZE - 1;
		this.maxZ = this.minZ + SIZE - 1;
		this.maxY = this.minY + HEIGHT - 1;
		BlockState stone = Blocks.STONE.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();
		BlockState light = Blocks.GLOWSTONE.getDefaultState();
		for (int x = this.minX; x <= this.maxX; x++) {
			for (int z = this.minZ; z <= this.maxZ; z++) {
				for (int yy = this.minY; yy <= this.maxY; yy++) {
					boolean edge = x == this.minX || x == this.maxX || z == this.minZ || z == this.maxZ;
					boolean floor = yy == this.minY;
					boolean ceil = yy == this.maxY;
					BlockState next = air;
					if (floor || edge || ceil) {
						next = stone;
						if (ceil && (x - this.minX) % 4 == 2 && (z - this.minZ) % 4 == 2 && !edge) {
							next = light;
						}
					}
					place(new BlockPos(x, yy, z), next);
				}
			}
		}
	}

	public void remove() {
		if (this.world == null) {
			return;
		}
		for (int i = this.saved.size() - 1; i >= 0; i--) {
			Saved s = this.saved.get(i);
			this.world.setBlockState(s.pos, s.state, 3);
			if (s.be != null) {
				BlockEntity be = this.world.getBlockEntity(s.pos);
				if (be != null) {
					be.readNbt(s.be);
					be.markDirty();
				}
			}
		}
		this.saved.clear();
		this.world = null;
	}

	public boolean contains(BlockPos pos) {
		return pos != null
				&& pos.getX() >= this.minX && pos.getX() <= this.maxX
				&& pos.getY() >= this.minY && pos.getY() <= this.maxY
				&& pos.getZ() >= this.minZ && pos.getZ() <= this.maxZ;
	}

	public boolean isShell(BlockPos pos) {
		if (!contains(pos)) {
			return false;
		}
		return pos.getX() == this.minX || pos.getX() == this.maxX
				|| pos.getZ() == this.minZ || pos.getZ() == this.maxZ
				|| pos.getY() == this.minY || pos.getY() == this.maxY;
	}

	public double spawnX() {
		return (this.minX + this.maxX) / 2.0 + 0.5;
	}

	public double spawnY() {
		return this.minY + 1.0;
	}

	public double spawnZ() {
		return (this.minZ + this.maxZ) / 2.0 + 0.5;
	}

	public void keepInside(net.minecraft.server.network.ServerPlayerEntity player) {
		if (player == null || this.world == null) {
			return;
		}
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		double minPx = this.minX + 1.25;
		double maxPx = this.maxX + 1.0 - 1.25;
		double minPz = this.minZ + 1.25;
		double maxPz = this.maxZ + 1.0 - 1.25;
		double minPy = this.minY + 0.9;
		double maxPy = this.maxY - 0.4;
		boolean out = x < minPx || x > maxPx || z < minPz || z > maxPz || y < minPy || y > maxPy;
		if (!out) {
			return;
		}
		double nx = Math.min(maxPx, Math.max(minPx, x));
		double nz = Math.min(maxPz, Math.max(minPz, z));
		double ny = Math.min(maxPy, Math.max(this.minY + 1.0, y));
		TeleportLog.silent(() -> {
			player.networkHandler.requestTeleport(nx, ny, nz, player.getYaw(), player.getPitch());
			player.fallDistance = 0;
		});
	}

	private void place(BlockPos pos, BlockState next) {
		Saved s = new Saved();
		s.pos = pos.toImmutable();
		s.state = this.world.getBlockState(pos);
		BlockEntity be = this.world.getBlockEntity(pos);
		if (be != null) {
			s.be = be.createNbt();
		}
		this.saved.add(s);
		this.world.removeBlockEntity(pos);
		this.world.setBlockState(pos, next, 2);
	}
}
