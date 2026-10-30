package ru.admincontrol.cw;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.lumira.gen.LumiraBiomeGen;
import ru.admincontrol.util.NpcService;
import ru.admincontrol.util.NpcStore;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Server-thread, bounded reset of exactly one CW map selected by the executor's current dimension. */
public final class CwMapResetService {
	private static Job job;
	private static boolean registered;
	private record Job(ServerWorld world, String worldId, ServerCommandSource source, List<ChunkPos> chunks, int index) {}

	private CwMapResetService() {}

	public static synchronized void register() {
		if (registered) return;
		registered = true;
		ServerTickEvents.END_SERVER_TICK.register(CwMapResetService::tick);
	}

	public static synchronized String start(ServerCommandSource source) {
		if (job != null) return "Уже выполняется сброс карты " + job.worldId + ".";
		ServerPlayerEntity executor;
		try { executor = source.getPlayer(); } catch (Exception e) { return "Команду нужно выполнить внутри конкретной CW-карты; консоль не задаёт однозначную цель."; }
		ServerWorld target = executor.getServerWorld();
		boolean builder = CwBuilderWorld.inside(executor);
		if (!builder) return "Команда сбрасывает только текущую карту-шаблон: войдите в admin_control:cw_builder. Персональные инстансы этим вызовом не затрагиваются.";
		if (LumiraBiomeGen.running()) return "Сначала остановите или дождитесь генерации Альбиона.";
		try { CwBackupService.backupNow(); }
		catch (Exception e) { return "Не удалось создать обязательную резервную копию; сброс отменён: " + e.getMessage(); }

		String id = target.getRegistryKey().getValue().toString();
		for (NpcStore.Saved saved : NpcStore.all()) {
			if (saved != null && id.equals(saved.dimension) && saved.nick != null) NpcService.remove(source.getServer(), saved.nick);
		}
		List<ServerPlayerEntity> occupants = new ArrayList<>(target.getPlayers().stream().filter(p -> !NpcService.isNpc(p)).toList());
		for (ServerPlayerEntity player : occupants) {
			String error = CwBuilderWorld.leave(player, false);
			if (error != null) return "Сброс отменён: не удалось безопасно вывести " + player.getName().getString() + ": " + error;
		}
		if (!target.getPlayers().isEmpty()) return "Сброс отменён: в целевой карте остались игроки.";
		for (Entity entity : target.iterateEntities()) if (!(entity instanceof ServerPlayerEntity)) entity.discard();

		List<ChunkPos> chunks = new ArrayList<>();
		for (int cx = LumiraBiomeGen.GRID_MIN; cx <= LumiraBiomeGen.GRID_MAX; cx++)
			for (int cz = LumiraBiomeGen.GRID_MIN; cz <= LumiraBiomeGen.GRID_MAX; cz++) chunks.add(new ChunkPos(cx, cz));
		job = new Job(target, id, source, chunks, 0);
		CwAudit.write(source, "cwe del", id, "START chunks=" + chunks.size());
		return null;
	}

	private static synchronized void tick(MinecraftServer server) {
		if (job == null) return;
		try {
			int end = Math.min(job.index + 1, job.chunks.size());
			for (int n = job.index; n < end; n++) clear(job.world, job.chunks.get(n));
			job = new Job(job.world, job.worldId, job.source, job.chunks, end);
			if (end < job.chunks.size()) return;

			BlockPos bedrock = CwBuilderWorld.ORIGIN;
			job.world.setBlockState(bedrock, Blocks.BEDROCK.getDefaultState(), Block.NOTIFY_ALL);
			CwBuilderEntry.resetAllToFirst();
			job.world.getChunkManager().save(true);
			String report = "Карта " + job.worldId + " полностью сброшена: очищено " + job.chunks.size()
					+ " чанков, оставлен один bedrock " + bedrock.toShortString() + "; безопасная позиция появления " + bedrock.up().toShortString() + ".";
			job.source.sendFeedback(() -> Text.literal("[CWE] " + report), true);
			CwAudit.write(job.source, "cwe del", job.worldId, "OK single_bedrock=" + bedrock.toShortString());
			AdminControlMod.LOGGER.warn("[CWE] {}", report);
			job = null;
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("[CWE] map reset failed world={}", job.worldId, e);
			job.source.sendError(Text.literal("[CWE] Сброс остановлен с ошибкой; успех не зафиксирован: " + e.getMessage()));
			CwAudit.write(job.source, "cwe del", job.worldId, "FAILED " + e);
			job = null;
		}
	}

	private static void clear(ServerWorld world, ChunkPos pos) {
		WorldChunk chunk = world.getChunk(pos.x, pos.z);
		for (BlockPos be : List.copyOf(chunk.getBlockEntityPositions())) chunk.removeBlockEntity(be);
		for (ChunkSection section : chunk.getSectionArray()) {
			if (section == null || section.isEmpty()) continue;
			for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++)
				section.setBlockState(x, y, z, Blocks.AIR.getDefaultState(), false);
		}
		Heightmap.populateHeightmaps(chunk, EnumSet.allOf(Heightmap.Type.class));
		chunk.setNeedsSaving(true);
	}
}
