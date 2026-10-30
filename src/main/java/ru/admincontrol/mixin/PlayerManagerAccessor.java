package ru.admincontrol.mixin;

import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mixin(PlayerManager.class)
public interface PlayerManagerAccessor {
	@Accessor("players")
	List<ServerPlayerEntity> adminControl$players();

	@Accessor("playerMap")
	Map<UUID, ServerPlayerEntity> adminControl$playerMap();
}
