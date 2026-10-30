package ru.admincontrol.util;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class NpcNetworkHandler extends ServerPlayNetworkHandler {
	public NpcNetworkHandler(MinecraftServer server, ClientConnection connection, ServerPlayerEntity player) {
		super(server, connection, player);
	}

	@Override
	public void tick() {
	}

	@Override
	public void sendPacket(Packet<?> packet) {
	}

	@Override
	public void disconnect(Text reason) {
	}

	@Override
	public void onDisconnected(Text reason) {
	}
}
