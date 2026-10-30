package ru.admincontrol.util;

import ru.admincontrol.data.DataManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RulesService {
	private static final String DEFAULT = "Правила ещё не были добавлены Тех. администрацией";

	private RulesService() {
	}

	public static Path file() {
		DataManager data = DataManager.get();
		return data == null ? null : data.getConfigDir().resolve("rules.txt");
	}

	public static boolean createDefault() {
		Path path = file();
		if (path == null) {
			return false;
		}
		try {
			if (!Files.exists(path)) {
				Files.writeString(path, DEFAULT, StandardCharsets.UTF_8);
			}
			DataManager.get().settings().rulesEncrypted = false;
			DataManager.get().saveAll();
			return true;
		} catch (Exception e) {
			ActionLog.error("Не удалось создать rules.txt", e);
			return false;
		}
	}

	public static String read() {
		Path path = file();
		if (path == null || !Files.exists(path)) {
			return DEFAULT;
		}
		try {
			String raw = Files.readString(path, StandardCharsets.UTF_8);
			DataManager data = DataManager.get();
			if (data != null && data.settings().rulesEncrypted && SecureJsonStorage.looksEncrypted(raw)) {
				return SecureJsonStorage.decrypt(raw, data.worldRoot());
			}
			return raw;
		} catch (Exception e) {
			return DEFAULT;
		}
	}

	public static boolean encrypt() {
		Path path = file();
		DataManager data = DataManager.get();
		if (path == null || data == null) {
			return false;
		}
		try {
			if (!Files.exists(path)) {
				createDefault();
			}
			String raw = Files.readString(path, StandardCharsets.UTF_8);
			if (SecureJsonStorage.looksEncrypted(raw)) {
				data.settings().rulesEncrypted = true;
				data.saveAll();
				return true;
			}
			Files.writeString(path, SecureJsonStorage.encrypt(raw, data.worldRoot()), StandardCharsets.UTF_8);
			data.settings().rulesEncrypted = true;
			data.saveAll();
			return true;
		} catch (Exception e) {
			ActionLog.error("Не удалось зашифровать rules.txt", e);
			return false;
		}
	}

	public static boolean decrypt() {
		Path path = file();
		DataManager data = DataManager.get();
		if (path == null || data == null || !Files.exists(path)) {
			return false;
		}
		try {
			String raw = Files.readString(path, StandardCharsets.UTF_8);
			if (SecureJsonStorage.looksEncrypted(raw)) {
				raw = SecureJsonStorage.decrypt(raw, data.worldRoot());
			}
			Files.writeString(path, raw, StandardCharsets.UTF_8);
			data.settings().rulesEncrypted = false;
			data.saveAll();
			return true;
		} catch (Exception e) {
			ActionLog.error("Не удалось расшифровать rules.txt", e);
			return false;
		}
	}
}
