package ru.admincontrol.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;

public final class AuthLock {
	private static boolean locked;

	private AuthLock() {
	}

	private static boolean registered = true;

	public static void set(boolean value) {
		locked = value;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) {
			return;
		}
		if (value) {
			if (blocksScreen(client.currentScreen)) {
				client.setScreen(null);
			}
		} else if (client.currentScreen instanceof AuthScreen) {
			client.setScreen(null);
		}
	}

	public static void set(boolean value, boolean isRegistered) {
		registered = isRegistered;
		set(value);
	}

	/** Пока вход не выполнен, держим открытым экран входа (в том числе после загрузки мира). */
	public static void tick(MinecraftClient client) {
		if (!locked || client.world == null || client.player == null) {
			return;
		}
		Screen screen = client.currentScreen;
		if (screen instanceof AuthScreen) {
			return;
		}
		if (screen == null || blocksScreen(screen)) {
			client.setScreen(new AuthScreen(registered));
		}
	}

	public static boolean active() {
		return locked;
	}

	/** Пока идёт ввод пароля, открыть можно только чат (для /l и /password) и служебные экраны соединения/паузы. */
	public static boolean blocksScreen(Screen screen) {
		if (!locked || screen == null) {
			return false;
		}
		String name = screen.getClass().getName();
		return name.startsWith("net.minecraft.client.gui.screen.ingame.")
				|| name.startsWith("net.minecraft.client.gui.screen.advancement.")
				|| name.startsWith("net.minecraft.client.gui.screen.achievement.")
				|| name.startsWith("net.minecraft.client.gui.screen.multiplayer.Social")
				|| name.startsWith("net.minecraft.client.gui.screen.option.")
				|| (name.startsWith("ru.admincontrol.") && !name.endsWith("PunishmentScreen") && !name.endsWith("AuthScreen"));
	}

	/** Гасит все игровые клавиши, кроме открытия чата/команды: хотбар, атака, использование, дроп, смена рук, инвентарь и т.д. */
	public static void suppressKeys(MinecraftClient client) {
		GameOptions o = client.options;
		if (o == null) {
			return;
		}
		KeyBinding[] keys = {o.attackKey, o.useKey, o.dropKey, o.swapHandsKey, o.inventoryKey, o.pickItemKey,
				o.advancementsKey, o.socialInteractionsKey, o.togglePerspectiveKey, o.smoothCameraKey,
				o.saveToolbarActivatorKey, o.loadToolbarActivatorKey, o.sprintKey};
		for (KeyBinding key : keys) {
			drain(key);
		}
		for (KeyBinding key : o.hotbarKeys) {
			drain(key);
		}
	}

	private static void drain(KeyBinding key) {
		while (key.wasPressed()) {
			// сбрасываем накопленные нажатия
		}
		key.setPressed(false);
	}
}
