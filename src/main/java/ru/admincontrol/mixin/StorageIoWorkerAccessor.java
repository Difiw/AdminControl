package ru.admincontrol.mixin;

import net.minecraft.world.storage.RegionBasedStorage;
import net.minecraft.world.storage.StorageIoWorker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Needed to close and re-open region files of a story world when a full file checkpoint is loaded. */
@Mixin(StorageIoWorker.class)
public interface StorageIoWorkerAccessor {
	@Accessor("storage")
	RegionBasedStorage adminControl$storage();
}
