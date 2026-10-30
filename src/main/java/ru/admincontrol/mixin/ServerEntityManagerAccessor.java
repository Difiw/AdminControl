package ru.admincontrol.mixin;

import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.world.storage.ChunkDataAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerEntityManager.class)
public interface ServerEntityManagerAccessor {
	@Accessor("dataAccess")
	ChunkDataAccess<?> adminControl$dataAccess();
}
