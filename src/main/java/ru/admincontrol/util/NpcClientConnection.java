package ru.admincontrol.util;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/** Фиктивное соединение для NPC. */
public final class NpcClientConnection extends ClientConnection {
	public NpcClientConnection() {
		super(NetworkSide.SERVERBOUND);
	}

	@Override
	public boolean isOpen() {
		return true;
	}

	@Override
	public SocketAddress getAddress() {
		return InetSocketAddress.createUnresolved("127.0.0.1", 0);
	}

	@Override
	public void send(Packet<?> packet) {
	}

	@Override
	public void send(Packet<?> packet, PacketCallbacks callbacks) {
	}

	@Override
	public void handleDisconnection() {
	}

	@Override
	public void tick() {
	}

	@Override
	public boolean isEncrypted() {
		return false;
	}
}
