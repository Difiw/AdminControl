package ru.admincontrol.util;

import net.minecraft.network.PacketByteBuf;

/**
 * Элемент в папке игры игрока: папка верхнего уровня (mods, resourcepacks…), путь внутри неё,
 * признак папки и размер (для папки — число элементов). Путь внутри архива: "pack.zip!/assets/…".
 * Для результатов поиска по тексту: номер строки (с 1, 0 — нет) и найденный фрагмент.
 */
public final class PlayerFileEntry {
	public static final int PATH_MAX = 512;
	public static final int NOTE_MAX = 200;

	public final String folder;
	public final String path;
	public final boolean dir;
	public final long size;
	public final int line;
	public final String note;

	public PlayerFileEntry(String folder, String path, boolean dir, long size) {
		this(folder, path, dir, size, 0, "");
	}

	public PlayerFileEntry(String folder, String path, boolean dir, long size, int line, String note) {
		this.folder = folder == null ? "" : folder;
		this.path = path == null ? "" : path;
		this.dir = dir;
		this.size = size;
		this.line = Math.max(0, line);
		String n = note == null ? "" : note;
		this.note = n.length() > NOTE_MAX ? n.substring(0, NOTE_MAX) : n;
	}

	public void write(PacketByteBuf buf) {
		buf.writeString(this.folder, 32);
		buf.writeString(this.path, PATH_MAX);
		buf.writeBoolean(this.dir);
		buf.writeVarLong(this.size);
		buf.writeVarInt(this.line);
		buf.writeString(this.note, NOTE_MAX);
	}

	public static PlayerFileEntry read(PacketByteBuf buf) {
		return new PlayerFileEntry(buf.readString(32), buf.readString(PATH_MAX), buf.readBoolean(), buf.readVarLong(),
				buf.readVarInt(), buf.readString(NOTE_MAX));
	}

	public String name() {
		String p = this.path;
		int bang = p.lastIndexOf("!/");
		String tail = bang >= 0 ? p.substring(bang + 2) : p;
		if (tail.endsWith("/")) {
			tail = tail.substring(0, tail.length() - 1);
		}
		if (tail.isEmpty() && bang >= 0) {
			tail = p.substring(0, bang);
		}
		int slash = tail.lastIndexOf('/');
		return slash >= 0 ? tail.substring(slash + 1) : tail;
	}
}
