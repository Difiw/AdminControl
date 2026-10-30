package ru.admincontrol.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import ru.admincontrol.net.ModPackets;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ResourcePackXrayScan {
	static final String[] BLOCK_PNG = {
			"assets/minecraft/textures/block/stone.png",
			"assets/minecraft/textures/block/deepslate.png",
			"assets/minecraft/textures/block/cobblestone.png",
			"assets/minecraft/textures/block/dirt.png",
			"assets/minecraft/textures/block/gravel.png",
			"assets/minecraft/textures/block/netherrack.png",
			"assets/minecraft/textures/block/blackstone.png",
			"assets/minecraft/textures/block/andesite.png",
			"assets/minecraft/textures/block/diorite.png",
			"assets/minecraft/textures/block/granite.png",
			"assets/minecraft/textures/block/tuff.png",
			"assets/minecraft/textures/block/basalt.png",
			"assets/minecraft/textures/block/end_stone.png"
	};
	static final String[] ORE_PNG = {
			"assets/minecraft/textures/block/diamond_ore.png",
			"assets/minecraft/textures/block/deepslate_diamond_ore.png",
			"assets/minecraft/textures/block/emerald_ore.png",
			"assets/minecraft/textures/block/gold_ore.png",
			"assets/minecraft/textures/block/iron_ore.png",
			"assets/minecraft/textures/block/ancient_debris.png",
			"assets/minecraft/textures/block/lapis_ore.png",
			"assets/minecraft/textures/block/redstone_ore.png"
	};
	static final String[] BLOCK_MODELS = {
			"assets/minecraft/models/block/stone.json",
			"assets/minecraft/models/block/deepslate.json",
			"assets/minecraft/models/block/netherrack.json",
			"assets/minecraft/models/block/dirt.json"
	};
	private static int waitTicks = 20;
	private static long lastScan;
	private static String lastSent = "";
	private static String lastIdleSent = "";
	/** /actest на сервере: сканировать чаще и сразу при включении. */
	private static boolean testMode;

	private ResourcePackXrayScan() {
	}

	public static void reset() {
		waitTicks = 20;
		lastScan = 0L;
		lastSent = "";
		lastIdleSent = "";
		testMode = false;
	}

	/** Включить/выключить быстрый рескан (пакет с сервера при /actest). */
	public static void setTestMode(boolean on) {
		testMode = on;
		lastSent = "";
		lastIdleSent = "";
		lastScan = 0L;
		waitTicks = 0;
	}

	/** Немедленная проверка (после /actest или по запросу сервера). */
	public static void forceNow(MinecraftClient client) {
		lastScan = 0L;
		waitTicks = 0;
		lastSent = "";
		scanAndSend(client, true);
	}

	public static void tick(MinecraftClient client) {
		if (client == null || client.player == null || client.getNetworkHandler() == null) {
			return;
		}
		if (!ClientPlayNetworking.canSend(ModPackets.XRAY_PACK)) {
			return;
		}
		long interval = testMode ? 200L : 2_500L;
		if (waitTicks > 0) {
			waitTicks--;
			if (waitTicks > 0) {
				return;
			}
		} else if (System.currentTimeMillis() - lastScan < interval) {
			return;
		}
		scanAndSend(client, false);
	}

	private static void scanAndSend(MinecraftClient client, boolean force) {
		if (client == null || client.player == null || client.getNetworkHandler() == null) {
			return;
		}
		if (!ClientPlayNetworking.canSend(ModPackets.XRAY_PACK)) {
			return;
		}
		lastScan = System.currentTimeMillis();
		String hit = find(client);
		if (hit != null && !hit.isBlank()) {
			if (!force && hit.equals(lastSent) && !testMode) {
				return;
			}
			// В /actest каждый раз шлём активный чит — сервер сам сбросит флаг и кикнет.
			lastSent = hit;
			var buf = PacketByteBufs.create();
			buf.writeString(hit, 200);
			ClientPlayNetworking.send(ModPackets.XRAY_PACK, buf);
			return;
		}
		lastSent = "";
		if (!testMode) {
			return;
		}
		String idle = findIdle(client);
		if (idle == null || idle.isBlank() || idle.equals(lastIdleSent)) {
			return;
		}
		lastIdleSent = idle;
		var buf = PacketByteBufs.create();
		buf.writeString("выкл: " + idle, 200);
		ClientPlayNetworking.send(ModPackets.XRAY_PACK, buf);
	}

	/** Чит установлен, но сейчас выключен (для /actest — только сообщение). */
	private static String findIdle(MinecraftClient client) {
		String mod = knownModIdle();
		if (mod != null) {
			return mod;
		}
		return scanDisabledPackNames(client);
	}

	private static String knownModIdle() {
		try {
			var loader = net.fabricmc.loader.api.FabricLoader.getInstance();
			for (var mod : loader.getAllMods()) {
				String id = mod.getMetadata().getId().toLowerCase(Locale.ROOT);
				String name = mod.getMetadata().getName().toLowerCase(Locale.ROOT);
				if (id.contains("xray") || id.contains("x-ray") || id.contains("fullbright")
						|| name.contains("xray") || name.contains("fullbright") || name.contains("x-ray")) {
					if (findFullbright(MinecraftClient.getInstance()) == null && !xrayModActive()) {
						return "мод «" + mod.getMetadata().getName() + "» (" + mod.getMetadata().getId()
								+ ") установлен, fullbright/xray выключен";
					}
				}
			}
		} catch (Throwable ignored) {
		}
		for (String className : XRAY_MOD_CLASSES) {
			try {
				Class.forName(className, false, ResourcePackXrayScan.class.getClassLoader());
				if (!xrayModActive() && findFullbright(MinecraftClient.getInstance()) == null) {
					return "xray-mod установлен, выключен (" + className + ")";
				}
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	/** Пытается понять, включён ли X-Ray мод сейчас (Mikey / AteSAbend и т.п.). */
	private static boolean xrayModActive() {
		String[] types = {
				"pro.mikey.xray.xray.Controller",
				"pro.mikey.xray.Controller",
				"pro.mikey.xray.XRay",
				"fr.atesab.xray.XrayMain",
				"fr.atesab.xray.XRay"
		};
		for (String type : types) {
			try {
				Class<?> cl = Class.forName(type, false, ResourcePackXrayScan.class.getClassLoader());
				for (String m : new String[] {"isXRayActive", "isActive", "isEnabled", "isOn", "drawBlocks"}) {
					try {
						var method = cl.getMethod(m);
						Object v = method.invoke(null);
						if (v instanceof Boolean b && b) {
							return true;
						}
					} catch (Throwable ignored) {
					}
				}
				for (var field : cl.getDeclaredFields()) {
					String n = field.getName().toLowerCase(Locale.ROOT);
					if (!n.contains("active") && !n.contains("enable") && !n.contains("on") && !n.contains("xray")) {
						continue;
					}
					if (field.getType() != boolean.class && field.getType() != Boolean.class) {
						continue;
					}
					field.setAccessible(true);
					Object v = java.lang.reflect.Modifier.isStatic(field.getModifiers()) ? field.get(null) : null;
					if (v instanceof Boolean b && b) {
						return true;
					}
				}
			} catch (Throwable ignored) {
			}
		}
		return false;
	}

	private static String scanDisabledPackNames(MinecraftClient client) {
		try {
			var manager = client.getResourcePackManager();
			if (manager == null) {
				return null;
			}
			java.util.Set<String> enabled = new java.util.HashSet<>();
			for (var profile : manager.getEnabledProfiles()) {
				if (profile != null && profile.getName() != null) {
					enabled.add(profile.getName());
				}
			}
			for (var profile : manager.getProfiles()) {
				if (profile == null || profile.getName() == null || enabled.contains(profile.getName())) {
					continue;
				}
				String name = profile.getName();
				String display = profile.getDisplayName() == null ? "" : profile.getDisplayName().getString();
				if (xrayName(name) || xrayName(display) || xrayText(display)) {
					return "пак выключен: " + (display.isBlank() ? name : display);
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private static final String[] XRAY_MOD_CLASSES = {
			"pro.mikey.xray.XRay",
			"pro.mikey.xray.XRayMod",
			"pro.mikey.xray.xray.Controller",
			"fr.atesab.xray.XrayMain",
			"fr.atesab.xray.XRay",
			"fr.atesab.xray.XrayMod",
			"ru.aiefu.xray.XRay",
			"me.srrapero720.watercores.custom.modules.xray.XRayModule"
	};

	private static String find(MinecraftClient client) {
		// Fullbright / завышенная gamma — сравнение с ванилью (max 1.0).
		String bright = findFullbright(client);
		if (bright != null) {
			return bright;
		}
		String overlay = CheatOverlayScan.find();
		if (overlay != null) {
			return overlay;
		}
		String applied = inspectApplied(client);
		if (applied != null) {
			return applied;
		}
		String mod = knownMod();
		if (mod != null) {
			return mod;
		}
		String fabric = fabricXrayActive();
		if (fabric != null) {
			return fabric;
		}
		String enabled = scanEnabledPackFiles(client);
		if (enabled != null) {
			return enabled;
		}
		return scanEnabledNames(client);
	}

	/**
	 * Ваниль: gamma 0.0–1.0. X-Ray моды (fullbright) ставят 12–16+ и часто рисуют «[Fullbright]».
	 */
	private static String findFullbright(MinecraftClient client) {
		if (client == null || client.options == null) {
			return null;
		}
		try {
			double gamma = client.options.getGamma().getValue();
			if (gamma > 1.05) {
				return "fullbright: яркость " + String.format(Locale.ROOT, "%.1f", gamma)
						+ " (ваниль максимум 1.0)";
			}
		} catch (Throwable ignored) {
		}
		try {
			for (String type : new String[] {
					"pro.mikey.xray.xray.Controller",
					"pro.mikey.xray.Configuration",
					"fr.atesab.xray.XrayMain",
					"ru.aiefu.xray.XRay",
					"me.srrapero720.watercore.custom.water_xray.XRay"
			}) {
				Class<?> cl = Class.forName(type, false, ResourcePackXrayScan.class.getClassLoader());
				for (String m : new String[] {"isFullbright", "fullbright", "isFullBright", "isBright", "isGamma"}) {
					try {
						var method = cl.getMethod(m);
						Object v = method.invoke(null);
						if (v instanceof Boolean b && b) {
							return "fullbright: " + type + "." + m;
						}
					} catch (Throwable ignored) {
					}
				}
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	/** Fabric-моды с xray/fullbright в id — если gamma уже поймана выше, сюда не доходим. */
	private static String fabricXrayActive() {
		try {
			var loader = net.fabricmc.loader.api.FabricLoader.getInstance();
			for (var mod : loader.getAllMods()) {
				String id = mod.getMetadata().getId().toLowerCase(Locale.ROOT);
				String name = mod.getMetadata().getName().toLowerCase(Locale.ROOT);
				if (!(id.contains("xray") || id.contains("x-ray") || id.contains("fullbright")
						|| name.contains("xray") || name.contains("fullbright") || name.contains("x-ray"))) {
					continue;
				}
				if (xrayModActive() || findFullbright(MinecraftClient.getInstance()) != null) {
					return "xray-mod fabric: " + mod.getMetadata().getId();
				}
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	private static String knownMod() {
		for (String className : XRAY_MOD_CLASSES) {
			try {
				Class.forName(className, false, ResourcePackXrayScan.class.getClassLoader());
				if (xrayModActive()) {
					return "xray-mod: " + className;
				}
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	private static final Identifier[] TERRAIN_TEX = {
			new Identifier("minecraft", "textures/block/stone.png"),
			new Identifier("minecraft", "textures/block/deepslate.png"),
			new Identifier("minecraft", "textures/block/cobblestone.png"),
			new Identifier("minecraft", "textures/block/dirt.png"),
			new Identifier("minecraft", "textures/block/gravel.png"),
			new Identifier("minecraft", "textures/block/netherrack.png"),
			new Identifier("minecraft", "textures/block/andesite.png"),
			new Identifier("minecraft", "textures/block/diorite.png"),
			new Identifier("minecraft", "textures/block/granite.png"),
			new Identifier("minecraft", "textures/block/tuff.png")
	};
	private static final Identifier[] ORE_TEX = {
			new Identifier("minecraft", "textures/block/diamond_ore.png"),
			new Identifier("minecraft", "textures/block/deepslate_diamond_ore.png"),
			new Identifier("minecraft", "textures/block/gold_ore.png"),
			new Identifier("minecraft", "textures/block/iron_ore.png"),
			new Identifier("minecraft", "textures/block/emerald_ore.png")
	};
	private static final Identifier[] TERRAIN_MODELS = {
			new Identifier("minecraft", "models/block/stone.json"),
			new Identifier("minecraft", "models/block/deepslate.json"),
			new Identifier("minecraft", "models/block/dirt.json"),
			new Identifier("minecraft", "models/block/netherrack.json"),
			new Identifier("minecraft", "models/block/cobblestone.json")
	};
	private static final Block[] TERRAIN_BLOCKS = {
			Blocks.STONE, Blocks.DEEPSLATE, Blocks.DIRT, Blocks.COBBLESTONE,
			Blocks.NETHERRACK, Blocks.ANDESITE, Blocks.GRANITE, Blocks.DIORITE,
			Blocks.TUFF, Blocks.GRAVEL
	};

	private static String inspectApplied(MinecraftClient client) {
		if (client == null || client.getResourceManager() == null) {
			return null;
		}
		try {
			String baked = inspectBaked(client);
			if (baked != null) {
				return baked;
			}
			var rm = client.getResourceManager();
			String glassHit = null;
			int glass = 0;
			for (Identifier id : TERRAIN_TEX) {
				var opt = rm.getResource(id);
				if (opt.isEmpty()) {
					continue;
				}
				Resource res = opt.get();
				if (!seeThroughResource(res)) {
					continue;
				}
				glass++;
				if (glassHit == null) {
					glassHit = "прозрачный " + id.getPath() + " (" + packName(res) + ")";
				}
			}
			boolean oreSolid = false;
			for (Identifier id : ORE_TEX) {
				var opt = rm.getResource(id);
				if (opt.isEmpty()) {
					continue;
				}
				if (!seeThroughResource(opt.get())) {
					oreSolid = true;
					break;
				}
			}
			if (glassHit != null && (oreSolid || glass >= 2)) {
				return glassHit;
			}
			for (Identifier id : TERRAIN_MODELS) {
				var opt = rm.getResource(id);
				if (opt.isEmpty()) {
					continue;
				}
				Resource res = opt.get();
				String json = readStream(res.getInputStream(), 2500);
				if (xrayModel(json)) {
					return "модель " + id.getPath() + " стекло (" + packName(res) + ")";
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private static String inspectBaked(MinecraftClient client) {
		if (client.getBlockRenderManager() == null) {
			return null;
		}
		for (Block block : TERRAIN_BLOCKS) {
			try {
				BakedModel model = client.getBlockRenderManager().getModel(block.getDefaultState());
				if (model == null) {
					continue;
				}
				Sprite sprite = model.getParticleSprite();
				if (sprite != null && sprite.getContents() != null) {
					Identifier sid = sprite.getContents().getId();
					if (sid != null) {
						String path = sid.getPath().toLowerCase(Locale.ROOT);
						if (path.contains("glass") || path.contains("xray") || path.contains("empty")) {
							return "частица " + block.getTranslationKey() + " → " + sid;
						}
					}
				}
			} catch (Exception ignored) {
			}
		}
		return null;
	}

	private static String packName(Resource res) {
		try {
			String name = res.getResourcePackName();
			return name == null || name.isBlank() ? "пак" : name;
		} catch (Exception ignored) {
			return "пак";
		}
	}

	private static boolean seeThroughResource(Resource res) {
		try (InputStream in = res.getInputStream()) {
			return transparentPng(in);
		} catch (Exception ignored) {
			return false;
		}
	}

	private static String readStream(InputStream in, int max) {
		try (in) {
			byte[] data = in.readNBytes(max);
			return new String(data, StandardCharsets.UTF_8);
		} catch (Exception ignored) {
			return "";
		}
	}

	private static String scanEnabledPackFiles(MinecraftClient client) {
		try {
			var manager = client.getResourcePackManager();
			File dir = toFile(client.getResourcePackDir());
			if (manager == null || dir == null) {
				return null;
			}
			for (var profile : manager.getEnabledProfiles()) {
				if (profile == null) {
					continue;
				}
				String name = profile.getName() == null ? "" : profile.getName();
				String display = profile.getDisplayName() == null ? "" : profile.getDisplayName().getString();
				if (xrayName(name) || xrayName(display) || xrayText(display)) {
					return "включён пак: " + (display.isBlank() ? name : display);
				}
				if (!name.startsWith("file/")) {
					continue;
				}
				File pack = new File(dir, name.substring("file/".length()));
				if (!pack.exists()) {
					continue;
				}
				String inner = pack.isDirectory()
						? scanFolderPack(pack, "включён пак/" + pack.getName())
						: scanZip(pack, "включён пак/" + pack.getName());
				if (inner != null) {
					return inner;
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private static String scanEnabledNames(MinecraftClient client) {
		try {
			var manager = client.getResourcePackManager();
			if (manager == null) {
				return null;
			}
			for (var profile : manager.getEnabledProfiles()) {
				if (profile == null) {
					continue;
				}
				String name = profile.getName() == null ? "" : profile.getName();
				String display = profile.getDisplayName() == null ? "" : profile.getDisplayName().getString();
				if (xrayName(name) || xrayName(display) || xrayText(display)) {
					return "включён пак: " + (display.isBlank() ? name : display);
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private static File toFile(Path path) {
		return path == null ? null : path.toFile();
	}

	private static String scanFolderPack(File pack, String label) {
		if (xrayName(pack.getName())) {
			return label;
		}
		File meta = new File(pack, "pack.mcmeta");
		if (meta.isFile() && xrayText(readFile(meta, 4000))) {
			return label + "/pack.mcmeta";
		}
		File fabric = new File(pack, "fabric.mod.json");
		if (fabric.isFile() && xrayText(readFile(fabric, 4000))) {
			return label + "/fabric.mod.json";
		}
		int ores = 0;
		int glass = 0;
		for (String ore : ORE_PNG) {
			if (new File(pack, ore.replace('/', File.separatorChar)).isFile()) {
				ores++;
			}
		}
		for (String png : BLOCK_PNG) {
			File file = new File(pack, png.replace('/', File.separatorChar));
			if (file.isFile() && transparentPng(file)) {
				glass++;
				if (glass >= 1) {
					return label + " :: " + png;
				}
			}
		}
		for (String model : BLOCK_MODELS) {
			File file = new File(pack, model.replace('/', File.separatorChar));
			if (file.isFile() && xrayModel(readFile(file, 2000))) {
				return label + " :: " + model;
			}
		}
		File stone = new File(pack, "assets/minecraft/textures/block/stone.png".replace('/', File.separatorChar));
		if (ores >= 3 && !stone.isFile()) {
			return label + " :: только руды, без камня";
		}
		return scanFolderNames(pack, label, 0);
	}

	private static String scanFolderNames(File dir, String label, int depth) {
		if (depth > 5) {
			return null;
		}
		File[] files = dir.listFiles();
		if (files == null) {
			return null;
		}
		int n = 0;
		for (File file : files) {
			if (file == null || n++ > 300) {
				break;
			}
			if (xrayName(file.getName())) {
				return label + "/" + file.getName();
			}
			if (file.isDirectory()) {
				String inner = scanFolderNames(file, label, depth + 1);
				if (inner != null) {
					return inner;
				}
			}
		}
		return null;
	}

	private static String scanZip(File zip, String label) {
		try (ZipFile archive = new ZipFile(zip)) {
			ZipEntry meta = entry(archive, "pack.mcmeta");
			if (meta != null && xrayText(readZip(archive, meta, 4000))) {
				return label + " :: pack.mcmeta";
			}
			ZipEntry fabric = entry(archive, "fabric.mod.json");
			if (fabric != null && xrayText(readZip(archive, fabric, 4000))) {
				return label + " :: fabric.mod.json";
			}
			int ores = 0;
			for (String ore : ORE_PNG) {
				if (entry(archive, ore) != null) {
					ores++;
				}
			}
			for (String png : BLOCK_PNG) {
				ZipEntry e = entry(archive, png);
				if (e != null && transparentPng(archive.getInputStream(e))) {
					return label + " :: " + png;
				}
			}
			if (ores >= 3 && entry(archive, "assets/minecraft/textures/block/stone.png") == null) {
				return label + " :: только руды, без камня";
			}
			int n = 0;
			var entries = archive.entries();
			while (entries.hasMoreElements() && n++ < 3000) {
				ZipEntry e = entries.nextElement();
				if (e == null) {
					continue;
				}
				String entryName = e.getName();
				if (entryName.endsWith(".class") || entryName.startsWith("ru/admincontrol/")) {
					continue;
				}
				if (xrayName(entryName)) {
					return label + " :: " + entryName;
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private static ZipEntry entry(ZipFile zip, String path) {
		ZipEntry e = zip.getEntry(path);
		if (e != null) {
			return e;
		}
		return zip.getEntry(path.replace('/', '\\'));
	}

	private static boolean transparentPng(File file) {
		try (InputStream in = Files.newInputStream(file.toPath())) {
			return transparentPng(in);
		} catch (Exception ignored) {
			return false;
		}
	}

	static boolean transparentPng(InputStream in) {
		try (in; NativeImage img = NativeImage.read(in)) {
			if (img == null || img.getWidth() <= 0 || img.getHeight() <= 0) {
				return false;
			}
			if (img.getFormat() != NativeImage.Format.RGBA) {
				return false;
			}
			int w = img.getWidth();
			int h = img.getHeight();
			int stepX = Math.max(1, w / 16);
			int stepY = Math.max(1, h / 16);
			int total = 0;
			int see = 0;
			for (int y = 0; y < h; y += stepY) {
				for (int x = 0; x < w; x += stepX) {
					int alpha = (img.getColor(x, y) >>> 24) & 0xFF;
					total++;
					if (alpha < 160) {
						see++;
					}
				}
			}
			int center = (img.getColor(w / 2, h / 2) >>> 24) & 0xFF;
			if (total == 0) {
				return false;
			}
			int pct = see * 100 / total;
			if (center < 80 && pct >= 15) {
				return true;
			}
			return pct >= 35;
		} catch (Exception ignored) {
			return false;
		}
	}

	private static String readFile(File file, int max) {
		try {
			byte[] data = Files.readAllBytes(file.toPath());
			int n = Math.min(max, data.length);
			return new String(data, 0, n, StandardCharsets.UTF_8);
		} catch (Exception ignored) {
			return "";
		}
	}

	private static String readZip(ZipFile zip, ZipEntry entry, int max) {
		try (InputStream in = zip.getInputStream(entry)) {
			byte[] data = in.readNBytes(max);
			return new String(data, StandardCharsets.UTF_8);
		} catch (Exception ignored) {
			return "";
		}
	}

	public static boolean xrayName(String raw) {
		if (raw == null || raw.isBlank()) {
			return false;
		}
		return xrayText(raw.replace('\\', '/'));
	}

	static boolean xrayModel(String json) {
		if (json == null || json.isBlank()) {
			return false;
		}
		String t = json.toLowerCase(Locale.ROOT).replace(" ", "");
		return t.contains("block/glass") || t.contains("minecraft:block/glass")
				|| t.contains("\"elements\":[]") || xrayText(json);
	}

	static boolean xrayText(String raw) {
		if (raw == null || raw.isBlank()) {
			return false;
		}
		String t = raw.toLowerCase(Locale.ROOT).replace(' ', '_');
		return t.contains("x-ray") || t.contains("x_ray") || t.contains("xray")
				|| t.contains("иксрей") || t.contains("икс-рей") || t.contains("рентген")
				|| t.contains("просвеч") || t.contains("wallhack") || t.contains("oreesp")
				|| t.contains("ore_esp") || t.contains("fullbright") || t.contains("full_bright")
				|| t.contains("seethrough") || t.contains("see_through") || t.contains("see-through")
				|| t.contains("cavefinder") || t.contains("blockesp") || t.contains("orefinder");
	}
}
