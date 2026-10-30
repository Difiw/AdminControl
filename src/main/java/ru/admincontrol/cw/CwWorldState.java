package ru.admincontrol.cw;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import ru.admincontrol.AdminControlMod;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The parts of a save that live outside the player's own NBT:
 * <ul>
 *   <li>every block and block entity (chests, signs...) of the story map, run-length encoded;</li>
 *   <li>the entities of the story world (mobs with their current attack target and anger, dropped items, experience
 *   orbs, projectiles, boats, minecarts) with their exact positions and motion;</li>
 *   <li>the player's advancements.</li>
 * </ul>
 * Players and the story NPCs (fake players) are never touched. Restoring puts the saved state back exactly.
 */
public final class CwWorldState {
	private static final int MAX_ENTITIES = 2000;
	/** Карта без ограничения размера: порог только защищает от переполнения int/памяти. */
	private static final long MAX_VOLUME = 2_000_000_000L;
	private static final int FORMAT = 1;
	private static final String TARGET_TAG = "cw_target";

	private CwWorldState() {}

	// ---- blocks ----

	/** Blocks and block entities inside {@code b = {minX,minY,minZ,maxX,maxY,maxZ}}; null if the map is too large or unknown. */
	public static byte[] captureBlocks(ServerWorld world, int[] b) {
		if (b == null || b.length != 6) return null;
		long volume = (long) (b[3] - b[0] + 1) * (b[4] - b[1] + 1) * (b[5] - b[2] + 1);
		if (volume <= 0 || volume > MAX_VOLUME) { AdminControlMod.LOGGER.warn("[CW] block snapshot skipped: map volume {}", volume); return null; }
		try {
			Map<BlockState, Integer> palette = new LinkedHashMap<>();
			IntArrayList runIndex = new IntArrayList(), runLength = new IntArrayList();
			List<Object[]> blockEntities = new ArrayList<>();
			BlockPos.Mutable pos = new BlockPos.Mutable();
			int last = -1, length = 0;
			for (int y = b[1]; y <= b[4]; y++) for (int z = b[2]; z <= b[5]; z++) for (int x = b[0]; x <= b[3]; x++) {
				pos.set(x, y, z);
				BlockState state = world.getBlockState(pos);
				int index = palette.computeIfAbsent(state, s -> palette.size());
				if (index == last) length++;
				else { if (length > 0) { runIndex.add(last); runLength.add(length); } last = index; length = 1; }
				if (state.hasBlockEntity()) {
					BlockEntity be = world.getBlockEntity(pos);
					if (be != null) blockEntities.add(new Object[] {pos.toImmutable(), be.createNbtWithIdentifyingData().asString()});
				}
			}
			if (length > 0) { runIndex.add(last); runLength.add(length); }
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (DataOutputStream out = new DataOutputStream(bytes)) {
				out.writeInt(FORMAT);
				for (int v : b) out.writeInt(v);
				out.writeInt(palette.size());
				for (BlockState s : palette.keySet()) out.writeUTF(NbtHelper.fromBlockState(s).asString());
				out.writeInt(runIndex.size());
				for (int i = 0; i < runIndex.size(); i++) { out.writeInt(runIndex.getInt(i)); out.writeInt(runLength.getInt(i)); }
				out.writeInt(blockEntities.size());
				for (Object[] e : blockEntities) {
					BlockPos p = (BlockPos) e[0];
					out.writeInt(p.getX()); out.writeInt(p.getY()); out.writeInt(p.getZ());
					byte[] text = ((String) e[1]).getBytes(StandardCharsets.UTF_8);
					out.writeInt(text.length); out.write(text);
				}
			}
			return bytes.toByteArray();
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("[CW] block snapshot failed", e);
			return null;
		}
	}

	/** Puts every block and block entity back; returns the number of blocks that had to change, or -1 on failure. */
	public static int restoreBlocks(ServerWorld world, byte[] data) {
		if (data == null) return 0;
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
			if (in.readInt() != FORMAT) return -1;
			int[] b = new int[6];
			for (int i = 0; i < 6; i++) b[i] = in.readInt();
			BlockState[] palette = new BlockState[in.readInt()];
			for (int i = 0; i < palette.length; i++) palette[i] = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), StringNbtReader.parse(in.readUTF()));
			int runs = in.readInt(), changed = 0, x = b[0], y = b[1], z = b[2];
			BlockPos.Mutable pos = new BlockPos.Mutable();
			for (int r = 0; r < runs; r++) {
				BlockState wanted = palette[in.readInt()];
				for (int n = in.readInt(); n > 0; n--) {
					pos.set(x, y, z);
					if (world.getBlockState(pos) != wanted) {
						if (world.getBlockEntity(pos) instanceof Inventory inventory) inventory.clear(); // never spill contents when a block is replaced
						world.setBlockState(pos, wanted, Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS);
						changed++;
					}
					if (++x > b[3]) { x = b[0]; if (++z > b[5]) { z = b[2]; y++; } }
				}
			}
			int count = in.readInt();
			for (int i = 0; i < count; i++) {
				BlockPos p = new BlockPos(in.readInt(), in.readInt(), in.readInt());
				byte[] text = new byte[in.readInt()];
				in.readFully(text);
				BlockEntity be = world.getBlockEntity(p);
				if (be == null) continue;
				be.readNbt(StringNbtReader.parse(new String(text, StandardCharsets.UTF_8)));
				be.markDirty();
				BlockState state = world.getBlockState(p);
				world.updateListeners(p, state, state, Block.NOTIFY_ALL);
			}
			return changed;
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("[CW] block restore failed", e);
			return -1;
		}
	}

	// ---- entities ----

	private static boolean tracked(Entity e) {
		if (e == null || e.isRemoved() || e instanceof PlayerEntity) return false;
		return e instanceof MobEntity || e instanceof ItemEntity || e instanceof ExperienceOrbEntity || e instanceof ProjectileEntity
				|| e instanceof AbstractMinecartEntity || e instanceof BoatEntity;
	}

	/** NBT strings of every tracked top-level entity (riders are saved together with their vehicle), incl. who a mob is attacking. */
	public static List<String> captureEntities(ServerWorld world) {
		List<String> out = new ArrayList<>();
		for (Entity e : world.iterateEntities()) {
			if (!tracked(e) || e.hasVehicle()) continue;
			NbtCompound nbt = new NbtCompound();
			if (!e.saveNbt(nbt)) continue;
			if (e instanceof MobEntity mob && mob.getTarget() != null && mob.getTarget().isAlive()) nbt.putUuid(TARGET_TAG, mob.getTarget().getUuid());
			out.add(nbt.asString());
			if (out.size() >= MAX_ENTITIES) { AdminControlMod.LOGGER.warn("[CW] entity snapshot truncated at {}", MAX_ENTITIES); break; }
		}
		return out;
	}

	/** Replaces the tracked entities of the world with the saved ones. Returns how many were restored. */
	public static int restoreEntities(ServerWorld world, List<String> saved) {
		List<Entity> old = new ArrayList<>();
		for (Entity e : world.iterateEntities()) if (tracked(e)) old.add(e);
		for (Entity e : old) e.discard();
		Map<MobEntity, UUID> targets = new LinkedHashMap<>();
		int restored = 0;
		for (String text : saved) {
			try {
				NbtCompound nbt = StringNbtReader.parse(text);
				UUID target = nbt.containsUuid(TARGET_TAG) ? nbt.getUuid(TARGET_TAG) : null;
				Entity entity = EntityType.loadEntityWithPassengers(nbt, world, e -> e);
				if (entity == null) continue;
				if (!world.spawnNewEntityAndPassengers(entity)) { entity.setUuid(UUID.randomUUID()); if (!world.spawnNewEntityAndPassengers(entity)) continue; }
				restored++;
				if (entity instanceof MobEntity mob) { mob.setTarget(null); if (target != null) targets.put(mob, target); }
			} catch (Exception ex) {
				AdminControlMod.LOGGER.warn("[CW] one saved entity could not be restored: {}", ex.toString());
			}
		}
		targets.forEach((mob, id) -> { if (world.getEntity(id) instanceof LivingEntity living && living.isAlive()) mob.setTarget(living); });
		return restored;
	}

	// ---- advancements ----

	private static Path advancementFile(ServerPlayerEntity p) { return p.getServer().getSavePath(WorldSavePath.ADVANCEMENTS).resolve(p.getUuid() + ".json"); }

	/** The player's advancement file content right now (the tracker is flushed first); null if there is none. */
	public static String captureAdvancements(ServerPlayerEntity p) {
		try {
			p.getAdvancementTracker().save();
			Path file = advancementFile(p);
			return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
		} catch (Exception e) {
			AdminControlMod.LOGGER.warn("[CW] advancements could not be captured player={}: {}", p.getUuidAsString(), e.toString());
			return null;
		}
	}

	/** Puts the given advancements in place by rewriting the player's file and reloading the tracker (no rewards are granted). An empty value clears them. */
	public static void restoreAdvancements(ServerPlayerEntity p, String json) {
		if (json == null) return;
		try {
			Files.writeString(advancementFile(p), json.isBlank() ? "{}" : json, StandardCharsets.UTF_8);
			p.getAdvancementTracker().reload(p.getServer().getAdvancementLoader());
		} catch (Exception e) {
			AdminControlMod.LOGGER.warn("[CW] advancements could not be restored player={}: {}", p.getUuidAsString(), e.toString());
		}
	}
}
