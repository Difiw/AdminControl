package ru.admincontrol.util;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ScreenNav {
	private static final Map<UUID, Deque<ExtendedScreenHandlerFactory>> STACK = new ConcurrentHashMap<>();
	private static final Map<UUID, ExtendedScreenHandlerFactory> CURRENT = new ConcurrentHashMap<>();
	private static final ThreadLocal<Boolean> NAVIGATING = ThreadLocal.withInitial(() -> false);

	private ScreenNav() {
	}

	public static boolean navigating() {
		return Boolean.TRUE.equals(NAVIGATING.get());
	}

	public static boolean hasBack(ServerPlayerEntity player) {
		if (player == null) {
			return false;
		}
		Deque<ExtendedScreenHandlerFactory> stack = STACK.get(player.getUuid());
		return stack != null && !stack.isEmpty();
	}

	public static void open(ServerPlayerEntity player, ExtendedScreenHandlerFactory factory) {
		open(player, factory, false);
	}

	public static void openRoot(ServerPlayerEntity player, ExtendedScreenHandlerFactory factory) {
		if (player == null) {
			return;
		}
		UUID id = player.getUuid();
		STACK.remove(id);
		CURRENT.remove(id);
		open(player, factory, true);
	}

	public static void replace(ServerPlayerEntity player, ExtendedScreenHandlerFactory factory) {
		open(player, factory, true);
	}

	private static void open(ServerPlayerEntity player, ExtendedScreenHandlerFactory factory, boolean replace) {
		if (player == null || factory == null) {
			return;
		}
		UUID id = player.getUuid();
		if (!replace) {
			ExtendedScreenHandlerFactory previous = CURRENT.get(id);
			if (previous != null && player.currentScreenHandler != null
					&& player.currentScreenHandler != player.playerScreenHandler) {
				STACK.computeIfAbsent(id, u -> new ArrayDeque<>()).addLast(previous);
			} else {
				STACK.remove(id);
			}
		}
		CURRENT.put(id, factory);
		NAVIGATING.set(true);
		try {
			player.openHandledScreen(wrap(player, factory));
		} finally {
			NAVIGATING.set(false);
		}
	}

	public static boolean back(ServerPlayerEntity player) {
		if (player == null) {
			return false;
		}
		Deque<ExtendedScreenHandlerFactory> stack = STACK.get(player.getUuid());
		ExtendedScreenHandlerFactory previous = stack == null ? null : stack.pollLast();
		if (previous == null) {
			return false;
		}
		if (stack != null && stack.isEmpty()) {
			STACK.remove(player.getUuid());
		}
		replace(player, previous);
		return true;
	}

	public static boolean reopen(ServerPlayerEntity player) {
		if (player == null) {
			return false;
		}
		ExtendedScreenHandlerFactory current = CURRENT.get(player.getUuid());
		if (current == null) {
			return false;
		}
		replace(player, current);
		return true;
	}

	public static void forget(ServerPlayerEntity player) {
		if (player == null || navigating()) {
			return;
		}
		UUID id = player.getUuid();
		STACK.remove(id);
		CURRENT.remove(id);
	}

	private static ExtendedScreenHandlerFactory wrap(ServerPlayerEntity player, ExtendedScreenHandlerFactory inner) {
		boolean back = hasBack(player);
		return new ExtendedScreenHandlerFactory() {
			@Override
			public Text getDisplayName() {
				return inner.getDisplayName();
			}

			@Override
			public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity viewer) {
				return applyFlag(inner.createMenu(syncId, playerInventory, viewer), back);
			}

			@Override
			public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
				buf.writeBoolean(back);
				inner.writeScreenOpeningData(opener, buf);
			}
		};
	}

	public static <T extends ScreenHandler> T applyFlag(T handler, boolean hasBack) {
		if (handler instanceof NavAccess access) {
			access.adminControl$setHasBack(hasBack);
		}
		return handler;
	}
}
