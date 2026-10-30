package ru.admincontrol.mixin;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.storage.EntityChunkDataAccess;
import net.minecraft.world.storage.StorageIoWorker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityChunkDataAccess.class)
public interface EntityChunkDataAccessAccessor {
	@Accessor("dataLoadWorker")
	StorageIoWorker adminControl$worker();

	/** Chunks remembered as "no entities": stale after the entity files have been replaced. */
	@Accessor("emptyChunks")
	LongSet adminControl$emptyChunks();
}
