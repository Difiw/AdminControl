package ru.admincontrol.util;

import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PasswordEntry;
import ru.admincontrol.data.PasswordStore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

public final class PasswordService {
	private static final SecureRandom RANDOM = new SecureRandom();

	private PasswordService() {
	}

	public static boolean registered(ServerPlayerEntity player) {
		return find(player) != null;
	}

	public static PasswordEntry find(ServerPlayerEntity player) {
		DataManager data = DataManager.get();
		if (data == null || player == null) {
			return null;
		}
		PasswordStore store = data.passwords();
		PasswordEntry byUuid = store.byUuid.get(player.getUuid().toString().toLowerCase(Locale.ROOT));
		if (byUuid != null) {
			return byUuid;
		}
		return store.byNick.get(player.getGameProfile().getName().toLowerCase(Locale.ROOT));
	}

	public static boolean register(ServerPlayerEntity player, String password, String repeat) {
		if (player == null || password == null || repeat == null) {
			return false;
		}
		if (!password.equals(repeat) || password.isBlank() || password.length() < 3 || password.length() > 32) {
			return false;
		}
		if (registered(player)) {
			return false;
		}
		PasswordEntry entry = new PasswordEntry();
		entry.nick = player.getGameProfile().getName();
		entry.uuid = player.getUuid().toString();
		byte[] salt = new byte[16];
		RANDOM.nextBytes(salt);
		entry.salt = Base64.getEncoder().encodeToString(salt);
		entry.hash = hash(password, entry.salt);
		DataManager data = DataManager.get();
		PasswordStore store = data.passwords();
		store.byNick.put(entry.nick.toLowerCase(Locale.ROOT), entry);
		store.byUuid.put(entry.uuid.toLowerCase(Locale.ROOT), entry);
		data.saveAll();
		return true;
	}

	public static boolean matches(ServerPlayerEntity player, String nick, String password) {
		if (player == null || nick == null || password == null) {
			return false;
		}
		if (!player.getGameProfile().getName().equalsIgnoreCase(nick.trim())) {
			return false;
		}
		PasswordEntry entry = find(player);
		if (entry == null) {
			return false;
		}
		return hash(password, entry.salt).equals(entry.hash);
	}

	public static boolean change(ServerPlayerEntity player, String oldPass, String neu, String repeat) {
		if (player == null || AuthService.pending(player)) {
			return false;
		}
		if (neu == null || !neu.equals(repeat) || neu.length() < 3 || neu.length() > 32) {
			return false;
		}
		PasswordEntry entry = find(player);
		if (entry == null || !hash(oldPass, entry.salt).equals(entry.hash)) {
			return false;
		}
		byte[] salt = new byte[16];
		RANDOM.nextBytes(salt);
		entry.salt = Base64.getEncoder().encodeToString(salt);
		entry.hash = hash(neu, entry.salt);
		DataManager.get().saveAll();
		return true;
	}

	private static String hash(String password, String salt) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] out = digest.digest((salt + "|" + password).getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(out);
		} catch (Exception e) {
			return "";
		}
	}
}
