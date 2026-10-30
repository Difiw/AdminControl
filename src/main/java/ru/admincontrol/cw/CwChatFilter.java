package ru.admincontrol.cw;

import net.minecraft.network.message.MessageType;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server-side chat isolation of story sessions. Story messages and command feedback are sent straight to the player and
 * are never filtered; what is dropped is everything that comes from the shared chat: other players' chat, join/leave,
 * death and advancement broadcasts. Own messages and private messages ({@code /msg}, {@code /tell}, {@code /w}) pass.
 */
public final class CwChatFilter {
	private static final ThreadLocal<Integer> BROADCAST = ThreadLocal.withInitial(() -> 0);

	private CwChatFilter() {}

	public static void enterBroadcast() { BROADCAST.set(BROADCAST.get() + 1); }

	public static void exitBroadcast() { BROADCAST.set(Math.max(0, BROADCAST.get() - 1)); }

	public static boolean broadcasting() { return BROADCAST.get() > 0; }

	/** Story play or test (not the builder world). */
	public static boolean inStorySession(ServerPlayerEntity player) {
		return player != null && CwStoryRules.inside(player);
	}

	public static boolean blocksChat(ServerPlayerEntity receiver, MessageType.Parameters params) {
		if (!inStorySession(receiver) || params == null) return false;
		net.minecraft.util.Identifier id = receiver.getServerWorld().getRegistryManager()
				.get(net.minecraft.registry.RegistryKeys.MESSAGE_TYPE).getId(params.type());
		String type = id == null ? "" : id.getPath();
		return !allowsChatType(type, params.name().getString(), receiver.getName().getString());
	}

	/** Pure rule, unit-testable: private messages always pass, ordinary chat only when the receiver wrote it. */
	public static boolean allowsChatType(String type, String senderName, String receiverName) {
		if (type.equals("msg_command_incoming") || type.equals("msg_command_outgoing")) return true;
		return senderName != null && receiverName != null && senderName.contains(receiverName) && type.equals("chat");
	}
}
