package ru.admincontrol.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import ru.admincontrol.net.ModPackets;
import ru.admincontrol.util.PlayerFileEntry;
import ru.admincontrol.util.PlayerFileService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Клиентская часть проверки файлов: по запросу сервера показывает содержимое папок игры
 * (resourcepacks, mods, shaderpacks, config), ищет по ним и отдаёт файлы. За пределы этих папок не выходит.
 */
public final class PlayerFileClient {
	private static final int PER_PACKET = 60;
	private static final int PACKET_BYTES = 20_000;
	private static final int MAX_LIST = 1500;
	private static final int MAX_SEARCH = 500;
	private static final int MAX_GREP = 300;
	private static final int PER_FILE_HITS = 20;
	private static final long MAX_GREP_FILE = 8L * 1024 * 1024;
	private static final long SEARCH_TIME_MS = 25_000L;
	private static final int MAX_TEXT = 100_000;
	private static final int MAX_TEXT_BYTES = 400_000;
	private static final int CHUNKS_PER_TICK = 4;
	private static final long MAX_FILE = 64L * 1024 * 1024;
	private static final Set<String> TEXT_EXT = Set.of("txt", "json", "json5", "properties", "toml", "cfg", "conf",
			"ini", "yml", "yaml", "mcmeta", "lang", "log", "md", "vsh", "fsh", "gsh", "csh", "glsl", "xml", "js",
			"snbt", "options");
	private static final Set<String> ARCHIVE_EXT = Set.of("zip", "jar");

	private static final Deque<Transfer> QUEUE = new ArrayDeque<>();
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "AdminControl-Files");
		t.setDaemon(true);
		return t;
	});

	private PlayerFileClient() {
	}

	private static final class Transfer {
		String folder;
		String path;
		InputStream in;
		int chunk;
		int total;
	}

	private static final class Reply {
		int kind = PlayerFileService.KIND_ENTRIES;
		final List<PlayerFileEntry> entries = new ArrayList<>();
		String text = "";
		boolean truncated;

		static Reply error(String message) {
			Reply r = new Reply();
			r.kind = PlayerFileService.KIND_ERROR;
			r.text = message;
			return r;
		}
	}

	public static void onRequest(MinecraftClient client, int id, int op, String folder, String path, String query) {
		Path run = client.runDirectory.toPath();
		WORKER.execute(() -> {
			Reply reply;
			try {
				reply = handle(run, op, folder, path, query);
			} catch (IOException | RuntimeException ex) {
				reply = Reply.error("Ошибка чтения: " + ex.getMessage());
			}
			Reply done = reply;
			client.execute(() -> send(id, done));
		});
	}

	private static Reply handle(Path run, int op, String folder, String path, String query) throws IOException {
		if (!PlayerFileService.allowedFolder(folder)) {
			return Reply.error("Папка недоступна.");
		}
		Path root = run.resolve(folder).toAbsolutePath().normalize();
		if (!Files.isDirectory(root)) {
			return Reply.error("Папки «" + folder + "» у игрока нет.");
		}
		if (op == PlayerFileService.OP_SEARCH || op == PlayerFileService.OP_GREP) {
			String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
			if (q.isEmpty()) {
				return Reply.error("Введите текст для поиска.");
			}
			return new Search(root, folder, q, op == PlayerFileService.OP_GREP).run(path);
		}
		int bang = path.indexOf("!/");
		if (bang >= 0) {
			return viewArchive(root, folder, path.substring(0, bang), path.substring(bang + 2));
		}
		Path target = inside(root, path);
		if (target == null || !Files.exists(target)) {
			return Reply.error("Не найдено: " + folder + "/" + path);
		}
		if (Files.isDirectory(target)) {
			return list(root, folder, target);
		}
		if (ARCHIVE_EXT.contains(ext(target.getFileName().toString()))) {
			return viewArchive(root, folder, path, "");
		}
		return viewFile(target);
	}

	private static Reply list(Path root, String folder, Path dir) throws IOException {
		Reply reply = new Reply();
		List<Path> children;
		try (Stream<Path> s = Files.list(dir)) {
			children = new ArrayList<>(s.toList());
		}
		children.sort(Comparator.comparing((Path p) -> !Files.isDirectory(p))
				.thenComparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));
		for (Path child : children) {
			if (reply.entries.size() >= MAX_LIST) {
				reply.truncated = true;
				break;
			}
			String rel = rel(root, child);
			if (rel.length() > PlayerFileEntry.PATH_MAX) {
				continue;
			}
			if (Files.isDirectory(child)) {
				long count;
				try (Stream<Path> s = Files.list(child)) {
					count = s.limit(99_999).count();
				} catch (IOException ex) {
					count = 0;
				}
				reply.entries.add(new PlayerFileEntry(folder, rel, true, count));
			} else {
				reply.entries.add(new PlayerFileEntry(folder, rel, false, size(child)));
			}
		}
		return reply;
	}

	/** Поиск только внутри открытого места: папки (с подпапками и архивами в ней) или папки внутри архива. */
	private static final class Search {
		final Path root;
		final String folder;
		final String q;
		final byte[] needle;
		final boolean grep;
		final int limit;
		final long deadline = System.currentTimeMillis() + SEARCH_TIME_MS;
		final Reply reply = new Reply();

		Search(Path root, String folder, String q, boolean grep) {
			this.root = root;
			this.folder = folder;
			this.q = q;
			this.needle = q.getBytes(StandardCharsets.UTF_8);
			this.grep = grep;
			this.limit = grep ? MAX_GREP : MAX_SEARCH;
		}

		boolean full() {
			if (this.reply.entries.size() >= this.limit || System.currentTimeMillis() > this.deadline) {
				this.reply.truncated = true;
				return true;
			}
			return false;
		}

		void add(PlayerFileEntry e) {
			if (e.path.length() <= PlayerFileEntry.PATH_MAX && !full()) {
				this.reply.entries.add(e);
			}
		}

		Reply run(String path) throws IOException {
			int bang = path.indexOf("!/");
			if (bang >= 0) {
				String archiveRel = path.substring(0, bang);
				Path file = inside(this.root, archiveRel);
				if (file == null || !Files.isRegularFile(file)) {
					return Reply.error("Архив не найден: " + archiveRel);
				}
				archive(archiveRel, file, path.substring(bang + 2));
				return this.reply;
			}
			Path start = inside(this.root, path);
			if (start == null || !Files.isDirectory(start)) {
				return Reply.error("Не найдено: " + this.folder + "/" + path);
			}
			List<Path> all;
			try (Stream<Path> walk = Files.walk(start, 24)) {
				all = new ArrayList<>(walk.filter(p -> !p.equals(start)).toList());
			}
			all.sort(Comparator.comparing(p -> rel(this.root, p).toLowerCase(Locale.ROOT)));
			for (Path p : all) {
				if (full()) {
					break;
				}
				if (Files.isSymbolicLink(p)) {
					continue;
				}
				String rel = rel(this.root, p);
				boolean dir = Files.isDirectory(p);
				String name = p.getFileName().toString();
				if (!this.grep && name.toLowerCase(Locale.ROOT).contains(this.q)) {
					add(new PlayerFileEntry(this.folder, rel, dir, dir ? count(p) : size(p)));
				}
				if (dir) {
					continue;
				}
				if (ARCHIVE_EXT.contains(ext(name))) {
					archive(rel, p, "");
				} else if (this.grep && size(p) <= MAX_GREP_FILE) {
					try (InputStream in = Files.newInputStream(p)) {
						content(rel, name, in.readNBytes((int) MAX_GREP_FILE), "");
					} catch (IOException ignored) {
					}
				}
			}
			return this.reply;
		}

		void archive(String archiveRel, Path file, String prefix) {
			try (ZipFile zip = new ZipFile(file.toFile())) {
				List<ZipEntry> list = new ArrayList<>();
				Enumeration<? extends ZipEntry> en = zip.entries();
				while (en.hasMoreElements()) {
					ZipEntry e = en.nextElement();
					if (e.getName().startsWith(prefix) && !e.getName().equals(prefix)) {
						list.add(e);
					}
				}
				list.sort(Comparator.comparing(ZipEntry::getName, String.CASE_INSENSITIVE_ORDER));
				Set<String> seenDirs = new HashSet<>();
				for (ZipEntry e : list) {
					if (full()) {
						return;
					}
					String name = e.getName();
					if (!this.grep) {
						String rest = name.substring(prefix.length());
						int from = 0;
						int slash;
						while ((slash = rest.indexOf('/', from)) >= 0) {
							String dir = rest.substring(0, slash + 1);
							String seg = rest.substring(from, slash);
							if (seenDirs.add(dir) && seg.toLowerCase(Locale.ROOT).contains(this.q)) {
								add(new PlayerFileEntry(this.folder, archiveRel + "!/" + prefix + dir, true, 0));
							}
							from = slash + 1;
						}
						if (!e.isDirectory() && last(name).toLowerCase(Locale.ROOT).contains(this.q)) {
							add(new PlayerFileEntry(this.folder, archiveRel + "!/" + name, false, Math.max(0, e.getSize())));
						}
						continue;
					}
					if (e.isDirectory() || e.getSize() > MAX_GREP_FILE) {
						continue;
					}
					String full = archiveRel + "!/" + name;
					try (InputStream in = zip.getInputStream(e)) {
						if (ARCHIVE_EXT.contains(ext(name))) {
							nested(full, new ByteArrayInputStream(in.readNBytes((int) MAX_GREP_FILE)));
						} else {
							content(full, name, in.readNBytes((int) MAX_GREP_FILE), "");
						}
					} catch (IOException | RuntimeException ignored) {
					}
				}
			} catch (IOException | RuntimeException ignored) {
			}
		}

		/** Архив внутри архива (jar-in-jar): ищем текст в его файлах, результат указывает на сам вложенный архив. */
		void nested(String full, InputStream data) {
			try (ZipInputStream zin = new ZipInputStream(data)) {
				ZipEntry e;
				while ((e = zin.getNextEntry()) != null && !full()) {
					if (!e.isDirectory()) {
						content(full, e.getName(), zin.readNBytes((int) MAX_GREP_FILE), "[" + e.getName() + "] ");
					}
				}
			} catch (IOException | RuntimeException ignored) {
			}
		}

		void content(String path, String name, byte[] data, String inner) {
			if (path.length() > PlayerFileEntry.PATH_MAX || data.length == 0) {
				return;
			}
			if (TEXT_EXT.contains(ext(name)) || !binary(data)) {
				String s = new String(data, StandardCharsets.UTF_8);
				String lower = s.toLowerCase(Locale.ROOT);
				if (lower.length() != s.length()) {
					lower = s;
				}
				int idx = lower.indexOf(this.q);
				int pos = 0;
				int lineNo = 1;
				int hits = 0;
				while (idx >= 0 && hits < PER_FILE_HITS && !full()) {
					for (int i = pos; i < idx; i++) {
						if (s.charAt(i) == '\n') {
							lineNo++;
						}
					}
					pos = idx;
					int ls = s.lastIndexOf('\n', idx) + 1;
					int le = s.indexOf('\n', idx);
					if (le < 0) {
						le = s.length();
					}
					add(new PlayerFileEntry(this.folder, path, false, data.length, lineNo,
							inner + snippet(s.substring(ls, le), idx - ls, this.q.length())));
					hits++;
					idx = lower.indexOf(this.q, le);
				}
				return;
			}
			int at = indexOf(data, this.needle);
			if (at >= 0) {
				int from = Math.max(0, at - 30);
				int to = Math.min(data.length, at + this.needle.length + 60);
				String around = new String(data, from, to - from, StandardCharsets.UTF_8)
						.replaceAll("[\\p{Cntrl}\\uFFFD]+", "·");
				add(new PlayerFileEntry(this.folder, path, false, data.length, 0, inner + "двоичный: " + around));
			}
		}
	}

	private static String snippet(String line, int col, int len) {
		String l = line.replace("\r", "").replace('\t', ' ');
		int start = Math.max(0, col - 40);
		int end = Math.min(l.length(), col + len + 100);
		if (start > l.length()) {
			start = 0;
		}
		return (start > 0 ? "…" : "") + l.substring(start, Math.max(start, end)).trim();
	}

	/** Поиск байтов без учёта регистра латиницы. */
	private static int indexOf(byte[] data, byte[] needle) {
		outer:
		for (int i = 0; i + needle.length <= data.length; i++) {
			for (int j = 0; j < needle.length; j++) {
				byte b = data[i + j];
				if (b >= 'A' && b <= 'Z') {
					b = (byte) (b + 32);
				}
				if (b != needle[j]) {
					continue outer;
				}
			}
			return i;
		}
		return -1;
	}

	private static boolean binary(byte[] data) {
		int zeros = 0;
		for (int i = 0; i < Math.min(data.length, 4096); i++) {
			if (data[i] == 0 && ++zeros > 4) {
				return true;
			}
		}
		return false;
	}

	private static String last(String name) {
		String n = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
		int slash = n.lastIndexOf('/');
		return slash >= 0 ? n.substring(slash + 1) : n;
	}

	private static long count(Path dir) {
		try (Stream<Path> s = Files.list(dir)) {
			return s.limit(99_999).count();
		} catch (IOException | RuntimeException ex) {
			return 0;
		}
	}

	private static Reply viewArchive(Path root, String folder, String archiveRel, String entryName) throws IOException {
		Path file = inside(root, archiveRel);
		if (file == null || !Files.isRegularFile(file)) {
			return Reply.error("Архив не найден: " + archiveRel);
		}
		try (ZipFile zip = new ZipFile(file.toFile())) {
			if (!entryName.isEmpty() && !entryName.endsWith("/")) {
				ZipEntry entry = zip.getEntry(entryName);
				if (entry != null && !entry.isDirectory()) {
					if (!TEXT_EXT.contains(ext(entryName)) && entry.getSize() > 64 * 1024) {
						return Reply.error("Бинарный файл (" + PlayerFileService.human(Math.max(0, entry.getSize()))
								+ ") — посмотреть нельзя, можно скачать.");
					}
					try (InputStream in = zip.getInputStream(entry)) {
						return text(in.readNBytes(MAX_TEXT_BYTES));
					}
				}
				entryName = entryName + "/";
			}
			Map<String, Set<String>> dirs = new HashMap<>();
			List<ZipEntry> files = new ArrayList<>();
			Enumeration<? extends ZipEntry> en = zip.entries();
			while (en.hasMoreElements()) {
				ZipEntry e = en.nextElement();
				String name = e.getName();
				if (!name.startsWith(entryName) || name.equals(entryName)) {
					continue;
				}
				String rest = name.substring(entryName.length());
				int slash = rest.indexOf('/');
				if (slash < 0) {
					files.add(e);
					continue;
				}
				Set<String> kids = dirs.computeIfAbsent(rest.substring(0, slash), k -> new HashSet<>());
				String after = rest.substring(slash + 1);
				if (!after.isEmpty()) {
					int next = after.indexOf('/');
					kids.add(next < 0 ? after : after.substring(0, next));
				}
			}
			if (dirs.isEmpty() && files.isEmpty() && !entryName.isEmpty()) {
				return Reply.error("В архиве нет: " + entryName);
			}
			Reply reply = new Reply();
			List<String> dirNames = new ArrayList<>(dirs.keySet());
			dirNames.sort(String.CASE_INSENSITIVE_ORDER);
			for (String d : dirNames) {
				String full = archiveRel + "!/" + entryName + d + "/";
				if (full.length() <= PlayerFileEntry.PATH_MAX) {
					reply.entries.add(new PlayerFileEntry(folder, full, true, dirs.get(d).size()));
				}
			}
			files.sort(Comparator.comparing(ZipEntry::getName, String.CASE_INSENSITIVE_ORDER));
			for (ZipEntry e : files) {
				if (reply.entries.size() >= MAX_LIST) {
					reply.truncated = true;
					break;
				}
				String full = archiveRel + "!/" + e.getName();
				if (full.length() <= PlayerFileEntry.PATH_MAX) {
					reply.entries.add(new PlayerFileEntry(folder, full, false, Math.max(0, e.getSize())));
				}
			}
			return reply;
		}
	}

	private static Reply viewFile(Path file) throws IOException {
		String name = file.getFileName().toString();
		long size = size(file);
		if (!TEXT_EXT.contains(ext(name)) && size > 64 * 1024) {
			return Reply.error("Бинарный файл (" + PlayerFileService.human(size) + ") — посмотреть нельзя, можно скачать.");
		}
		try (InputStream in = Files.newInputStream(file)) {
			return text(in.readNBytes(MAX_TEXT_BYTES));
		}
	}

	private static Reply text(byte[] data) {
		int zeros = 0;
		for (int i = 0; i < Math.min(data.length, 4096); i++) {
			if (data[i] == 0) {
				zeros++;
			}
		}
		if (zeros > 4) {
			return Reply.error("Бинарный файл — посмотреть нельзя, можно скачать.");
		}
		String s = new String(data, StandardCharsets.UTF_8);
		Reply reply = new Reply();
		reply.kind = PlayerFileService.KIND_TEXT;
		reply.truncated = data.length >= MAX_TEXT_BYTES || s.length() > MAX_TEXT;
		reply.text = s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) : s;
		return reply;
	}

	private static void send(int id, Reply reply) {
		if (reply.kind == PlayerFileService.KIND_ENTRIES) {
			int sent = 0;
			do {
				PacketByteBuf body = PacketByteBufs.create();
				int end = sent;
				while (end < reply.entries.size() && end - sent < PER_PACKET && body.writerIndex() < PACKET_BYTES) {
					reply.entries.get(end).write(body);
					end++;
				}
				PacketByteBuf buf = header(id, end >= reply.entries.size(), reply);
				buf.writeVarInt(end - sent);
				buf.writeBytes(body);
				body.release();
				ClientPlayNetworking.send(ModPackets.FILES_LIST, buf);
				sent = end;
			} while (sent < reply.entries.size());
		} else if (reply.kind == PlayerFileService.KIND_TEXT) {
			String text = reply.text;
			int pos = 0;
			do {
				int end = Math.min(text.length(), pos + PlayerFileService.TEXT_CHUNK);
				PacketByteBuf buf = header(id, end >= text.length(), reply);
				buf.writeString(text.substring(pos, end), PlayerFileService.TEXT_CHUNK);
				ClientPlayNetworking.send(ModPackets.FILES_LIST, buf);
				pos = end;
			} while (pos < text.length());
		} else {
			PacketByteBuf buf = header(id, true, reply);
			String msg = reply.text == null ? "" : reply.text;
			buf.writeString(msg.length() > 290 ? msg.substring(0, 290) : msg, 300);
			ClientPlayNetworking.send(ModPackets.FILES_LIST, buf);
		}
	}

	private static PacketByteBuf header(int id, boolean last, Reply reply) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(id);
		buf.writeBoolean(last);
		buf.writeByte(reply.kind);
		buf.writeBoolean(reply.truncated);
		return buf;
	}

	public static void onPull(MinecraftClient client, String folder, String path) {
		Path run = client.runDirectory.toPath();
		WORKER.execute(() -> {
			Transfer t = new Transfer();
			t.folder = folder;
			t.path = path;
			try {
				if (!PlayerFileService.allowedFolder(folder)) {
					throw new IOException("folder");
				}
				Path root = run.resolve(folder).toAbsolutePath().normalize();
				int bang = path.indexOf("!/");
				long size;
				if (bang >= 0) {
					Path file = inside(root, path.substring(0, bang));
					if (file == null || !Files.isRegularFile(file)) {
						throw new IOException("archive");
					}
					try (ZipFile zip = new ZipFile(file.toFile())) {
						ZipEntry entry = zip.getEntry(path.substring(bang + 2));
						if (entry == null || entry.isDirectory() || entry.getSize() > MAX_FILE) {
							throw new IOException("entry");
						}
						byte[] data;
						try (InputStream in = zip.getInputStream(entry)) {
							data = in.readNBytes((int) MAX_FILE + 1);
						}
						if (data.length > MAX_FILE) {
							throw new IOException("size");
						}
						size = data.length;
						t.in = new ByteArrayInputStream(data);
					}
				} else {
					Path file = inside(root, path);
					if (file == null || !Files.isRegularFile(file) || Files.size(file) > MAX_FILE) {
						throw new IOException("file");
					}
					size = Files.size(file);
					t.in = Files.newInputStream(file);
				}
				t.total = (int) Math.max(1, (size + PlayerFileService.CHUNK - 1) / PlayerFileService.CHUNK);
				client.execute(() -> QUEUE.add(t));
			} catch (IOException | RuntimeException ex) {
				client.execute(() -> sendError(folder, path));
			}
		});
	}

	public static void tick() {
		Transfer t = QUEUE.peek();
		if (t == null) {
			return;
		}
		try {
			for (int n = 0; n < CHUNKS_PER_TICK && t.chunk < t.total; n++) {
				byte[] data = t.in.readNBytes(PlayerFileService.CHUNK);
				PacketByteBuf buf = PacketByteBufs.create();
				buf.writeString(t.folder, 32);
				buf.writeString(t.path, PlayerFileEntry.PATH_MAX);
				buf.writeVarInt(t.chunk);
				buf.writeVarInt(t.total);
				buf.writeByteArray(data);
				ClientPlayNetworking.send(ModPackets.FILE_DATA, buf);
				t.chunk++;
			}
		} catch (IOException ex) {
			sendError(t.folder, t.path);
			t.chunk = t.total;
		}
		if (t.chunk >= t.total) {
			close(t);
			QUEUE.poll();
		}
	}

	private static void sendError(String folder, String path) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeString(folder, 32);
		buf.writeString(path, PlayerFileEntry.PATH_MAX);
		buf.writeVarInt(0);
		buf.writeVarInt(-1);
		buf.writeByteArray(new byte[0]);
		ClientPlayNetworking.send(ModPackets.FILE_DATA, buf);
	}

	private static void close(Transfer t) {
		try {
			if (t.in != null) {
				t.in.close();
			}
		} catch (IOException ignored) {
		}
	}

	public static void reset() {
		for (Transfer t : QUEUE) {
			close(t);
		}
		QUEUE.clear();
	}

	/** Путь внутри корня папки; null, если выходит наружу (в т.ч. через ярлыки). */
	private static Path inside(Path root, String rel) {
		try {
			Path p = root.resolve(rel == null ? "" : rel.replace('\\', '/')).normalize();
			if (!p.startsWith(root)) {
				return null;
			}
			if (Files.exists(p) && !p.toRealPath().startsWith(root.toRealPath())) {
				return null;
			}
			return p;
		} catch (IOException | RuntimeException ex) {
			return null;
		}
	}

	private static String rel(Path root, Path p) {
		return root.relativize(p).toString().replace('\\', '/');
	}

	private static long size(Path p) {
		try {
			return Files.size(p);
		} catch (IOException ex) {
			return 0;
		}
	}

	private static String ext(String name) {
		int dot = name.lastIndexOf('.');
		return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
	}
}
