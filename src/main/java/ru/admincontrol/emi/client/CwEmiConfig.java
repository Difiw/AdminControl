package ru.admincontrol.emi.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import ru.admincontrol.AdminControlMod;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Клиентские настройки CW EMI (config/admin_control/cw_emi_client.json). Они меняют только внешний вид и удобство, но не доступ. */
public final class CwEmiConfig {
	/** Версия формата: при подъёме сбрасываются значения по умолчанию, изменённые в этом релизе. */
	public int configVersion = 2;
	public boolean panelInRecipe = true;
	public int opacity = 100;
	/** Размер иконок в сетке, 12..28. */
	public int iconSize = 16;
	/** Высота панели в процентах от высоты окна, 40..100. */
	public int heightPercent = 100;
	/** 0 = автоматически по свободному месту, иначе предел колонок. */
	public int maxColumns = 0;
	public boolean left = false;
	/** 0 плотно, 1 обычно, 2 свободно. */
	public int density = 1;
	public boolean compact = false;
	/** 0 полные, 1 короткие (только название), 2 выключены. */
	public int tooltips = 0;
	public boolean showModNames = true;
	public boolean historyEnabled = true;
	public boolean favoritesEnabled = true;
	public boolean visible = true;
	/** 0 по названию, 1 по ID, 2 по моду, 3 порядок реестра. */
	public int sort = 3;
	public List<String> favorites = new ArrayList<>();

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static CwEmiConfig instance = new CwEmiConfig();

	public static CwEmiConfig get() {
		return instance;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("admin_control").resolve("cw_emi_client.json");
	}

	public static void load() {
		Path f = file();
		if (!Files.isRegularFile(f)) {
			return;
		}
		try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
			CwEmiConfig loaded = GSON.fromJson(r, CwEmiConfig.class);
			if (loaded != null) {
				if (loaded.configVersion < 2) {
					loaded.configVersion = 2;
					loaded.sort = 3;
					loaded.opacity = 100;
					loaded.panelInRecipe = true;
				}
				loaded.clamp();
				instance = loaded;
			}
		} catch (Exception e) {
			AdminControlMod.LOGGER.warn("cw_emi_client.json не прочитан, используются значения по умолчанию: {}", e.toString());
		}
	}

	public static void save() {
		Path f = file();
		try {
			Files.createDirectories(f.getParent());
			try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, w);
			}
		} catch (Exception e) {
			AdminControlMod.LOGGER.warn("cw_emi_client.json не сохранён: {}", e.toString());
		}
	}

	/** Сброс внешнего вида; избранное сохраняется (это данные пользователя, а не настройки). */
	public static void reset() {
		List<String> fav = instance.favorites;
		instance = new CwEmiConfig();
		instance.favorites = fav;
		save();
	}

	private void clamp() {
		opacity = Math.max(30, Math.min(100, opacity));
		iconSize = Math.max(12, Math.min(28, iconSize));
		heightPercent = Math.max(40, Math.min(100, heightPercent));
		maxColumns = Math.max(0, Math.min(14, maxColumns));
		density = Math.max(0, Math.min(2, density));
		tooltips = Math.max(0, Math.min(2, tooltips));
		sort = Math.max(0, Math.min(3, sort));
		if (favorites == null) {
			favorites = new ArrayList<>();
		}
	}

	public int iconSize() {
		return iconSize;
	}

	public int gap() {
		return density;
	}
}
