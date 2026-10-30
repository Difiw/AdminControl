package ru.admincontrol.cw;

import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Tick-driven dedicated-server check of the full file checkpoint, run as the last selftest step: save, damage detection,
 * the real load job (world emptied, files replaced, region files re-opened) and crash-journal recovery.
 */
public final class CwWorldBackupSelfTest {
	private static int phase, ticks;
	private static ServerWorld world;
	private static UUID owner;
	private static Path base, backup;
	private static final BlockPos POS = new BlockPos(200, 70, 200);
	private static String jobResult;

	private CwWorldBackupSelfTest() {}

	private static void check(List<String> out, String name, boolean ok, String detail) {
		out.add((ok ? "PASS " : "FAIL ") + "CW file checkpoint " + name + (detail == null || detail.isEmpty() ? "" : " :: " + detail));
	}

	/** Returns true when finished. */
	public static boolean step(MinecraftServer server, List<String> out) {
		try {
			return inner(server, out);
		} catch (Throwable t) {
			check(out, "selftest crashed", false, t.toString());
			ru.admincontrol.AdminControlMod.LOGGER.error("file checkpoint selftest", t);
			return true;
		}
	}

	private static boolean inner(MinecraftServer server, List<String> out) throws Exception {
		if (phase == 0) {
			world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier("admin_control", "cw_story_5")));
			check(out, "story world exists", world != null, "");
			if (world == null) return true;
			owner = UUID.randomUUID();
			world.getChunk(POS);
			world.setBlockState(POS, Blocks.CHEST.getDefaultState());
			((ChestBlockEntity) world.getBlockEntity(POS)).setStack(0, new ItemStack(Items.DIAMOND, 7));
			String id = CwWorldBackupService.snapshot(server, world, owner, "selftest", "manual");
			check(out, "snapshot written", id != null, "");
			if (id == null) return true;
			base = CwWorldBackupService.baseDir(server, owner, "selftest");
			backup = base.resolve(id);
			check(out, "snapshot verifies", CwWorldBackupService.verifyDir(backup).isEmpty(), String.valueOf(CwWorldBackupService.verifyDir(backup)));
			check(out, "no partial folder is left behind", !Files.exists(base.resolve(id + ".partial")), "");

			String id2 = CwWorldBackupService.snapshot(server, world, owner, "selftest", "manual");
			Path victim;
			try (Stream<Path> s = Files.walk(base.resolve(id2).resolve("world"))) { victim = s.filter(Files::isRegularFile).findFirst().orElse(null); }
			if (victim != null) {
				byte[] bytes = Files.readAllBytes(victim);
				bytes[bytes.length / 2] ^= 0x5A;
				Files.write(victim, bytes);
				check(out, "a damaged copy is rejected", !CwWorldBackupService.verifyDir(base.resolve(id2)).isEmpty(), "");
			}

			// destroy the chest in the live world, then load the checkpoint like a real restore (wait for an empty world, swap files)
			world.setBlockState(POS, Blocks.AIR.getDefaultState());
			check(out, "chest is gone before the load", world.getBlockState(POS).isAir(), "");
			CwWorldBackupService.startJob(world, backup, r -> jobResult = r == null ? "ok" : r);
			phase = 1;
			return false;
		}
		if (phase == 1) {
			if (jobResult == null) {
				if (++ticks > 800) { check(out, "load job finished", false, "timeout, loaded chunks=" + world.getChunkManager().getLoadedChunkCount()); return true; }
				return false;
			}
			check(out, "load job finished without error", "ok".equals(jobResult), jobResult);
			// reading the block loads the chunk from the freshly replaced region files
			boolean chest = world.getBlockState(POS).isOf(Blocks.CHEST);
			boolean items = chest && world.getBlockEntity(POS) instanceof ChestBlockEntity be && be.getStack(0).isOf(Items.DIAMOND) && be.getStack(0).getCount() == 7;
			check(out, "chest with its items is back in the live world after the load", chest && items, "chest=" + chest);

			// interrupted swap: the journal points to a scratch folder, recovery completes it from the same backup
			Path scratch = base.resolve("scratch-world");
			Files.createDirectories(scratch);
			Files.writeString(scratch.resolve("garbage.txt"), "x");
			Files.writeString(base.resolve("journal.json"), backup.getFileName() + "\n" + scratch);
			CwWorldBackupService.recover(server);
			check(out, "recovery removes the journal and rebuilds the folder from the backup",
					!Files.exists(base.resolve("journal.json")) && !Files.exists(scratch.resolve("garbage.txt")) && Files.isDirectory(scratch.resolve("region")), "");
			CwWorldBackupService.deleteTree(base.getParent());
			return true;
		}
		return true;
	}
}
