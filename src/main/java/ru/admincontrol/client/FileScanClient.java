package ru.admincontrol.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import ru.admincontrol.net.ModPackets;
import ru.admincontrol.util.FileScanService;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Проверка файлов игры по запросу сервера: корень .minecraft, mods (имена, код внутри jar и вложенных jar),
 * resourcepacks (X-Ray текстуры/модели/шейдеры, включён ли пак), shaderpacks, config, versions.
 * Ничего не блокирует — только отправляет находки серверу.
 */
public final class FileScanClient {
	private static final int MAX_FINDINGS = 40;
	private static final int PER_PACKET = 6;
	private static final long MAX_ENTRY = 4L * 1024 * 1024;
	private static final long MAX_NESTED = 16L * 1024 * 1024;
	private static final long MAX_JAR_READ = 96L * 1024 * 1024;
	private static final long MAX_TEXT = 512L * 1024;
	private static final long SCAN_TIME_MS = 180_000L;
	private static final int MAX_WALK = 20_000;

	/** Имена файлов/папок/модов (сравниваются без пробелов, дефисов и регистра). */
	/** Легитимные моды: не считать читом по внутренним классам (пули/трассеры и т.п.). */
	private static final String[] SAFE_MODS = {
			"pointblank", "point_blank", "vicspointblank", "create", "sodium", "iris", "lithium", "phosphor",
			"fabricapi", "fabric-api", "modmenu", "clothconfig", "jade", "wthit", "jei", "rei", "emi"
	};
	private static final String[] NAME_TOKENS = {
			"meteorclient", "wurst", "aristois", "liquidbounce", "rusherhack", "thunderhack", "futureclient",
			"konas", "salhack", "kamiblue", "bleachhack", "inertiaclient", "sigmaclient", "novoline", "phobos",
			"forgehax", "impactclient", "lambdaclient", "coffeeclient", "boze", "baritone", "seedcracker",
			"xray", "иксрей", "рентген", "killaura", "aimbot", "aimassist", "triggerbot", "autoclicker", "autoclick",
			"crystalaura", "autocrystal", "wallhack", "chestesp", "playeresp", "storageesp", "oreesp", "blockesp",
			"tracers", "flyhack", "speedhack", "nofall", "nuker", "fastbreak", "antiknockback", "noknockback",
			"elytrafly", "freecam", "cavefinder", "orefinder", "fullbright", "hackclient", "cheatclient", "cheats", "читы"
	};
	/** Пути классов и файлов внутри jar. */
	private static final String[] CLASS_TOKENS = {
			"killaura", "aimbot", "aimassist", "triggerbot", "autoclicker", "crystalaura", "autocrystal", "wallhack",
			"chestesp", "playeresp", "storageesp", "oreesp", "blockesp", "xray", "flyhack", "speedhack", "nuker",
			"fastbreak", "antiknockback", "noknockback", "elytrafly", "freecam", "cavefinder", "orefinder",
			"fullbright", "baritone", "meteorclient", "meteordevelopment", "wurstclient", "liquidbounce", "aristois",
			"rusherhack", "thunderhack", "seedcracker", "tracers"
	};
	/** Строки внутри скомпилированного кода (названия модулей чит-клиентов). */
	private static final String[] STRING_TOKENS = {
			"killaura", "triggerbot", "aimbot", "autoclicker", "crystalaura", "wallhack", "chestesp", "playeresp",
			"storageesp", "meteorclient", "wurstclient", "liquidbounce"
	};
	private static final String[] SHADER_TOKENS = {
			"xray", "x-ray", "x_ray", "wallhack", "oreesp", "ore_esp", "seethrough", "see_through"
	};
	private static final Set<String> SHADER_TEXT = Set.of("fsh", "vsh", "gsh", "csh", "glsl", "properties", "txt", "lang");
	private static final Pattern META_FIELD = Pattern.compile("\"(id|name)\"\\s*:\\s*\"([^\"]{1,120})\"");
	private static final Pattern TOML_FIELD = Pattern.compile("(modId|displayName)\\s*=\\s*\"([^\"]{1,120})\"");

	private static final AtomicBoolean RUNNING = new AtomicBoolean();
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "AdminControl-FileScan");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	private FileScanClient() {
	}

	private record Finding(String path, String reason, String state) {
	}

	public static void onRequest(MinecraftClient client, int scanId) {
		if (client == null || !RUNNING.compareAndSet(false, true)) {
			return;
		}
		Path run = client.runDirectory.toPath().toAbsolutePath().normalize();
		Set<String> enabled = enabledPacks(client);
		WORKER.execute(() -> {
			List<Finding> found = new ArrayList<>();
			try {
				new Scan(run, enabled, found).run();
			} catch (Throwable ignored) {
			} finally {
				RUNNING.set(false);
			}
			client.execute(() -> send(scanId, found));
		});
	}

	private static Set<String> enabledPacks(MinecraftClient client) {
		Set<String> out = new HashSet<>();
		try {
			for (var profile : client.getResourcePackManager().getEnabledProfiles()) {
				String name = profile == null ? null : profile.getName();
				if (name != null && name.startsWith("file/")) {
					out.add(name.substring("file/".length()));
				}
			}
		} catch (RuntimeException ignored) {
		}
		return out;
	}

	private static void send(int scanId, List<Finding> found) {
		if (!ClientPlayNetworking.canSend(ModPackets.FILE_SCAN_RESULT)) {
			return;
		}
		int sent = 0;
		do {
			int end = Math.min(found.size(), sent + PER_PACKET);
			PacketByteBuf buf = PacketByteBufs.create();
			buf.writeVarInt(scanId);
			buf.writeBoolean(end >= found.size());
			buf.writeVarInt(end - sent);
			for (int i = sent; i < end; i++) {
				Finding f = found.get(i);
				buf.writeString(cut(f.path(), FileScanService.PATH_MAX), FileScanService.PATH_MAX);
				buf.writeString(cut(f.reason(), FileScanService.REASON_MAX), FileScanService.REASON_MAX);
				buf.writeString(cut(f.state(), FileScanService.STATE_MAX), FileScanService.STATE_MAX);
			}
			ClientPlayNetworking.send(ModPackets.FILE_SCAN_RESULT, buf);
			sent = end;
		} while (sent < found.size());
	}

	private static String cut(String s, int max) {
		String v = s == null ? "" : s;
		return v.length() > max ? v.substring(0, max - 1) + "…" : v;
	}

	/** Что нашлось внутри одного jar: токен → пример пути. */
	private static final class Hits {
		final Map<String, String> classes = new LinkedHashMap<>();
		final Map<String, String> strings = new LinkedHashMap<>();
		long budget = MAX_JAR_READ;

		boolean enough() {
			return this.classes.size() + this.strings.size() >= 6;
		}
	}

	@FunctionalInterface
	private interface Bytes {
		byte[] get() throws IOException;
	}

	/** Файлы пака: zip или папка. */
	private interface Source extends AutoCloseable {
		List<String> names();

		InputStream open(String name) throws IOException;

		@Override
		void close();
	}

	private static final class ZipSource implements Source {
		final ZipFile zip;

		ZipSource(Path file) throws IOException {
			this.zip = new ZipFile(file.toFile());
		}

		@Override
		public List<String> names() {
			List<String> out = new ArrayList<>();
			Enumeration<? extends ZipEntry> en = this.zip.entries();
			while (en.hasMoreElements() && out.size() < MAX_WALK) {
				ZipEntry e = en.nextElement();
				if (!e.isDirectory()) {
					out.add(e.getName().replace('\\', '/'));
				}
			}
			return out;
		}

		@Override
		public InputStream open(String name) throws IOException {
			ZipEntry e = this.zip.getEntry(name);
			return e == null || e.isDirectory() ? null : this.zip.getInputStream(e);
		}

		@Override
		public void close() {
			try {
				this.zip.close();
			} catch (IOException ignored) {
			}
		}
	}

	private static final class DirSource implements Source {
		final Path root;

		DirSource(Path root) {
			this.root = root;
		}

		@Override
		public List<String> names() {
			try (Stream<Path> walk = Files.walk(this.root, 12)) {
				return walk.filter(Files::isRegularFile).limit(MAX_WALK)
						.map(p -> this.root.relativize(p).toString().replace('\\', '/')).toList();
			} catch (IOException | RuntimeException ex) {
				return List.of();
			}
		}

		@Override
		public InputStream open(String name) throws IOException {
			Path p = this.root.resolve(name).normalize();
			return p.startsWith(this.root) && Files.isRegularFile(p) && !Files.isSymbolicLink(p) ? Files.newInputStream(p) : null;
		}

		@Override
		public void close() {
		}
	}

	private static final class Scan {
		final Path run;
		final Set<String> enabled;
		final List<Finding> found;
		final long deadline = System.currentTimeMillis() + SCAN_TIME_MS;
		String activeShader = "";
		boolean shadersOn;

		Scan(Path run, Set<String> enabled, List<Finding> found) {
			this.run = run;
			this.enabled = enabled;
			this.found = found;
		}

		boolean stop() {
			return this.found.size() >= MAX_FINDINGS || System.currentTimeMillis() > this.deadline;
		}

		void add(String path, List<String> reasons, String state) {
			if (!reasons.isEmpty() && this.found.size() < MAX_FINDINGS) {
				this.found.add(new Finding(".minecraft/" + path, String.join("; ", reasons), state));
			}
		}

		void run() {
			readIris();
			root();
			mods();
			packs();
			shaders();
			names("config", 4);
			names("versions", 1);
		}

		/** Папки и файлы прямо в .minecraft (чит-клиенты часто кладут сюда свои папки). */
		void root() {
			try (Stream<Path> s = Files.list(this.run)) {
				for (Path p : s.limit(2000).toList()) {
					if (stop()) {
						return;
					}
					String name = p.getFileName().toString();
					String t = token(name);
					if (t != null) {
						add(name + (Files.isDirectory(p) ? "/" : ""), List.of("имя похоже на чит «" + t + "»"), "");
					}
				}
			} catch (IOException | RuntimeException ignored) {
			}
		}

		void names(String folder, int depth) {
			Path dir = this.run.resolve(folder);
			if (!Files.isDirectory(dir)) {
				return;
			}
			try (Stream<Path> walk = Files.walk(dir, depth)) {
				for (Path p : walk.limit(MAX_WALK).toList()) {
					if (stop()) {
						return;
					}
					if (p.equals(dir)) {
						continue;
					}
					String t = token(p.getFileName().toString());
					if (t != null) {
						add(rel(p) + (Files.isDirectory(p) ? "/" : ""), List.of("имя похоже на чит «" + t + "»"), "");
					}
				}
			} catch (IOException | RuntimeException ignored) {
			}
		}

		void mods() {
			Path dir = this.run.resolve("mods");
			if (!Files.isDirectory(dir)) {
				return;
			}
			List<Path> files;
			try (Stream<Path> walk = Files.walk(dir, 3)) {
				files = walk.filter(Files::isRegularFile).limit(MAX_WALK).toList();
			} catch (IOException | RuntimeException ex) {
				return;
			}
			for (Path p : files) {
				if (stop()) {
					return;
				}
				String name = p.getFileName().toString();
				String lower = name.toLowerCase(Locale.ROOT);
				if (lower.contains("admin-control") || lower.contains("admin_control") || Files.isSymbolicLink(p)) {
					continue;
				}
				if (safeMod(name)) {
					continue;
				}
				List<String> reasons = new ArrayList<>();
				String t = token(name);
				if (t != null) {
					reasons.add("имя файла похоже на чит «" + t + "»");
				}
				if (lower.contains(".jar") || lower.endsWith(".zip")) {
					jar(p, reasons);
				}
				add(rel(p), reasons, lower.endsWith(".disabled") ? "мод отключён (.disabled)" : "");
			}
		}

		void jar(Path file, List<String> reasons) {
			try (ZipFile zip = new ZipFile(file.toFile())) {
				String fabric = read(zip, "fabric.mod.json");
				if (fabric.contains("\"admin_control\"")) {
					reasons.clear();
					return;
				}
				if (safeMod(file.getFileName().toString()) || safeModMeta(fabric)) {
					reasons.clear();
					return;
				}
				String meta = metaToken(fabric + read(zip, "quilt.mod.json"), META_FIELD);
				if (meta == null) {
					meta = metaToken(read(zip, "META-INF/mods.toml"), TOML_FIELD);
				}
				if (meta != null) {
					reasons.add("мод называется как чит: " + meta);
				}
				Hits hits = new Hits();
				Enumeration<? extends ZipEntry> en = zip.entries();
				while (en.hasMoreElements() && !hits.enough() && hits.budget > 0 && !stop()) {
					ZipEntry e = en.nextElement();
					if (e.isDirectory()) {
						continue;
					}
					String name = e.getName();
					String lower = name.toLowerCase(Locale.ROOT);
					if (lower.startsWith("ru/admincontrol/")) {
						continue;
					}
					if (lower.endsWith(".jar") && e.getSize() <= MAX_NESTED) {
						try (InputStream in = zip.getInputStream(e)) {
							nested(name, in, hits);
						} catch (IOException | RuntimeException ignored) {
						}
						continue;
					}
					entry(name, e.getSize(), () -> {
						try (InputStream in = zip.getInputStream(e)) {
							return in.readNBytes((int) MAX_ENTRY);
						}
					}, hits, "");
				}
				describe(hits, reasons);
			} catch (IOException | RuntimeException ignored) {
			}
		}

		/** jar внутри jar (так прячут чит в обычном на вид моде). */
		void nested(String outer, InputStream data, Hits hits) {
			try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(data))) {
				ZipEntry e;
				while ((e = zin.getNextEntry()) != null && !hits.enough() && hits.budget > 0) {
					if (e.isDirectory()) {
						continue;
					}
					entry(e.getName(), -1, () -> zin.readNBytes((int) MAX_ENTRY), hits, outer + " → ");
				}
			} catch (IOException | RuntimeException ignored) {
			}
		}

		void entry(String name, long size, Bytes bytes, Hits hits, String prefix) {
			String lower = name.toLowerCase(Locale.ROOT);
			String t = contains(lower, CLASS_TOKENS);
			if (t != null) {
				hits.classes.putIfAbsent(t, prefix + name);
			}
			if (!lower.endsWith(".class") || size > MAX_ENTRY) {
				return;
			}
			try {
				byte[] data = bytes.get();
				hits.budget -= data.length;
				String text = new String(data, StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
				for (String token : STRING_TOKENS) {
					if (!hits.strings.containsKey(token) && text.contains(token)) {
						hits.strings.put(token, prefix + name);
					}
				}
			} catch (IOException | RuntimeException ignored) {
			}
		}

		void describe(Hits hits, List<String> reasons) {
			if (!hits.classes.isEmpty()) {
				Map.Entry<String, String> first = hits.classes.entrySet().iterator().next();
				reasons.add("в коде модули чита: " + String.join(", ", hits.classes.keySet())
						+ " (например " + first.getValue() + ")");
			}
			if (!hits.strings.isEmpty()) {
				Map.Entry<String, String> first = hits.strings.entrySet().iterator().next();
				reasons.add("в коде строки чита: " + String.join(", ", hits.strings.keySet())
						+ " (в " + first.getValue() + ")");
			}
		}

		void packs() {
			Path dir = this.run.resolve("resourcepacks");
			if (!Files.isDirectory(dir)) {
				return;
			}
			List<Path> packs;
			try (Stream<Path> s = Files.list(dir)) {
				packs = s.limit(500).toList();
			} catch (IOException | RuntimeException ex) {
				return;
			}
			for (Path p : packs) {
				if (stop()) {
					return;
				}
				String name = p.getFileName().toString();
				List<String> reasons = new ArrayList<>();
				String t = token(name);
				if (t != null) {
					reasons.add("имя пака похоже на чит «" + t + "»");
				}
				try (Source src = open(p)) {
					if (src != null) {
						pack(src, reasons);
					}
				} catch (RuntimeException ignored) {
				}
				add(rel(p), reasons, this.enabled.contains(name) ? "пак ВКЛЮЧЁН" : "пак выключен");
			}
		}

		void pack(Source src, List<String> reasons) {
			String mcmeta = text(src, "pack.mcmeta");
			if (ResourcePackXrayScan.xrayText(mcmeta)) {
				reasons.add("в описании pack.mcmeta написано про X-Ray");
			}
			for (String png : ResourcePackXrayScan.BLOCK_PNG) {
				try {
					InputStream in = src.open(png);
					if (in != null && ResourcePackXrayScan.transparentPng(in)) {
						reasons.add("прозрачная текстура " + png + " — камень/земля просвечивают (X-Ray)");
						break;
					}
				} catch (IOException | RuntimeException ignored) {
				}
			}
			for (String model : ResourcePackXrayScan.BLOCK_MODELS) {
				if (ResourcePackXrayScan.xrayModel(text(src, model))) {
					reasons.add("модель " + model + " заменена на стекло/пустую (X-Ray)");
					break;
				}
			}
			List<String> names = src.names();
			Set<String> set = new HashSet<>(names);
			int ores = 0;
			for (String ore : ResourcePackXrayScan.ORE_PNG) {
				if (set.contains(ore)) {
					ores++;
				}
			}
			if (ores >= 3 && !set.contains("assets/minecraft/textures/block/stone.png")) {
				reasons.add("в паке только текстуры руд, без камня");
			}
			boolean nameHit = false;
			for (String n : names) {
				String lower = n.toLowerCase(Locale.ROOT);
				if (lower.startsWith("assets/minecraft/shaders/core/rendertype_solid") && lower.endsWith(".fsh")
						&& text(src, n).contains("discard")) {
					reasons.add("шейдер ландшафта " + n + " вырезает блоки (X-Ray через шейдер)");
				}
				if (!nameHit && ResourcePackXrayScan.xrayName(n)) {
					reasons.add("файл в паке: " + n);
					nameHit = true;
				}
			}
		}

		void readIris() {
			for (String cfg : new String[] {"iris.properties", "oculus.properties"}) {
				Path p = this.run.resolve("config").resolve(cfg);
				if (!Files.isRegularFile(p)) {
					continue;
				}
				try (InputStream in = Files.newInputStream(p)) {
					Properties props = new Properties();
					props.load(in);
					this.activeShader = props.getProperty("shaderPack", "").trim();
					this.shadersOn = Boolean.parseBoolean(props.getProperty("enableShaders", "false").trim());
					return;
				} catch (IOException | RuntimeException ignored) {
				}
			}
		}

		void shaders() {
			Path dir = this.run.resolve("shaderpacks");
			if (!Files.isDirectory(dir)) {
				return;
			}
			List<Path> packs;
			try (Stream<Path> s = Files.list(dir)) {
				packs = s.limit(300).toList();
			} catch (IOException | RuntimeException ex) {
				return;
			}
			for (Path p : packs) {
				if (stop()) {
					return;
				}
				String name = p.getFileName().toString();
				List<String> reasons = new ArrayList<>();
				String t = token(name);
				if (t != null) {
					reasons.add("имя шейдера похоже на чит «" + t + "»");
				}
				try (Source src = open(p)) {
					if (src != null) {
						shader(src, reasons);
					}
				} catch (RuntimeException ignored) {
				}
				String state = !name.equals(this.activeShader) ? "шейдер выключен"
						: this.shadersOn ? "шейдер ВКЛЮЧЁН" : "шейдер выбран, но шейдеры выключены";
				add(rel(p), reasons, state);
			}
		}

		void shader(Source src, List<String> reasons) {
			int texts = 0;
			boolean nameHit = false;
			for (String n : src.names()) {
				if (stop() || reasons.size() >= 3) {
					return;
				}
				String lower = n.toLowerCase(Locale.ROOT);
				if (!nameHit && contains(lower, SHADER_TOKENS) != null) {
					reasons.add("файл в шейдере: " + n);
					nameHit = true;
				}
				if (texts < 400 && SHADER_TEXT.contains(ext(lower))) {
					texts++;
					String hit = contains(text(src, n).toLowerCase(Locale.ROOT), SHADER_TOKENS);
					if (hit != null) {
						reasons.add("в коде шейдера «" + hit + "»: " + n);
					}
				}
			}
		}

		Source open(Path p) {
			try {
				if (Files.isDirectory(p)) {
					return new DirSource(p.toAbsolutePath().normalize());
				}
				String lower = p.getFileName().toString().toLowerCase(Locale.ROOT);
				if (lower.endsWith(".zip") || lower.endsWith(".jar")) {
					return new ZipSource(p);
				}
			} catch (IOException | RuntimeException ignored) {
			}
			return null;
		}

		String rel(Path p) {
			return this.run.relativize(p.toAbsolutePath().normalize()).toString().replace('\\', '/');
		}
	}

	private static String text(Source src, String name) {
		try (InputStream in = src.open(name)) {
			return in == null ? "" : new String(in.readNBytes((int) MAX_TEXT), StandardCharsets.UTF_8);
		} catch (IOException | RuntimeException ex) {
			return "";
		}
	}

	private static String read(ZipFile zip, String name) {
		ZipEntry e = zip.getEntry(name);
		if (e == null) {
			return "";
		}
		try (InputStream in = zip.getInputStream(e)) {
			return new String(in.readNBytes(64_000), StandardCharsets.UTF_8);
		} catch (IOException | RuntimeException ex) {
			return "";
		}
	}

	private static String metaToken(String text, Pattern pattern) {
		Matcher m = pattern.matcher(text);
		while (m.find()) {
			String value = m.group(2);
			String t = token(value);
			if (t != null) {
				return "«" + value + "» (" + t + ")";
			}
		}
		return null;
	}

	/** Токен чита в имени (без регистра, пробелов, дефисов и точек) или null. */
	private static String token(String name) {
		if (name == null || name.isBlank() || safeMod(name)) {
			return null;
		}
		String n = name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
		return contains(n, NAME_TOKENS);
	}

	private static boolean safeMod(String name) {
		if (name == null || name.isBlank()) {
			return false;
		}
		String n = name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
		return contains(n, SAFE_MODS) != null;
	}

	private static boolean safeModMeta(String fabricJson) {
		if (fabricJson == null || fabricJson.isBlank()) {
			return false;
		}
		Matcher m = META_FIELD.matcher(fabricJson);
		while (m.find()) {
			if (safeMod(m.group(2))) {
				return true;
			}
		}
		return false;
	}

	private static String contains(String text, String[] tokens) {
		for (String t : tokens) {
			if (text.contains(t)) {
				return t;
			}
		}
		return null;
	}

	private static String ext(String name) {
		int dot = name.lastIndexOf('.');
		return dot < 0 ? "" : name.substring(dot + 1);
	}
}
