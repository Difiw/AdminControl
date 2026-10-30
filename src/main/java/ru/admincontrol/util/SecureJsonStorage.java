package ru.admincontrol.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Шифрование JSON-файлов мода: для человека нечитаемо, мод расшифровывает по ключу мира.
 */
public final class SecureJsonStorage {
	private static final String PREFIX = "ACENC1:";
	private static final String SALT = "AdminControl-WorldKey-v1";
	private static final int GCM_TAG = 128;
	private static final int NONCE_LEN = 12;
	private static final SecureRandom RANDOM = new SecureRandom();

	private SecureJsonStorage() {
	}

	public static boolean looksEncrypted(String text) {
		return text != null && text.startsWith(PREFIX);
	}

	public static String encrypt(String plainJson, Path worldRoot) {
		try {
			byte[] nonce = new byte[NONCE_LEN];
			RANDOM.nextBytes(nonce);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, deriveKey(worldRoot), new GCMParameterSpec(GCM_TAG, nonce));
			byte[] encrypted = cipher.doFinal(plainJson.getBytes(StandardCharsets.UTF_8));
			byte[] payload = new byte[nonce.length + encrypted.length];
			System.arraycopy(nonce, 0, payload, 0, nonce.length);
			System.arraycopy(encrypted, 0, payload, nonce.length, encrypted.length);
			return PREFIX + Base64.getEncoder().encodeToString(payload);
		} catch (Exception e) {
			throw new IllegalStateException("Не удалось зашифровать данные Admin Control", e);
		}
	}

	public static String decrypt(String stored, Path worldRoot) {
		if (!looksEncrypted(stored)) {
			return stored;
		}
		try {
			byte[] payload = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
			if (payload.length <= NONCE_LEN) {
				throw new IllegalStateException("Повреждённый файл данных");
			}
			byte[] nonce = new byte[NONCE_LEN];
			byte[] cipherBytes = new byte[payload.length - NONCE_LEN];
			System.arraycopy(payload, 0, nonce, 0, NONCE_LEN);
			System.arraycopy(payload, NONCE_LEN, cipherBytes, 0, cipherBytes.length);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, deriveKey(worldRoot), new GCMParameterSpec(GCM_TAG, nonce));
			return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new IllegalStateException("Не удалось расшифровать данные Admin Control", e);
		}
	}

	private static SecretKeySpec deriveKey(Path worldRoot) throws Exception {
		String material = worldRoot.toAbsolutePath().normalize().toString() + "|" + SALT;
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		byte[] hash = digest.digest(material.getBytes(StandardCharsets.UTF_8));
		return new SecretKeySpec(hash, "AES");
	}
}
