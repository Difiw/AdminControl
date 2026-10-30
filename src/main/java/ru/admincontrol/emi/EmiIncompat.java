package ru.admincontrol.emi;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import ru.admincontrol.AdminControlMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Централизованный список ИЗВЕСТНЫХ просмотрщиков рецептов, несовместимых с CW EMI. Проверка идёт только по mod id
 * из метаданных Fabric Loader (имя файла не используется). Список можно дополнить строками mod id в
 * config/admin_control/cw_emi_incompatible.txt (по одному в строке, # — комментарий). Неизвестный мод так обнаружить нельзя.
 */
public final class EmiIncompat {
	/** mod id просмотрщиков: EMI, REI (Roughly Enough Items), JEI (Just Enough Items). */
	public static final List<String> KNOWN = List.of("emi", "roughlyenoughitems", "jei");

	private EmiIncompat() {
	}

	private static Set<String> ids() {
		Set<String> ids = new LinkedHashSet<>(KNOWN);
		Path file = FabricLoader.getInstance().getConfigDir().resolve("admin_control").resolve("cw_emi_incompatible.txt");
		if (Files.isRegularFile(file)) {
			try {
				for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
					String id = line.strip();
					if (!id.isEmpty() && !id.startsWith("#") && id.matches("[a-z0-9_.-]{1,64}")) {
						ids.add(id);
					}
				}
			} catch (IOException e) {
				AdminControlMod.LOGGER.warn("cw_emi_incompatible.txt не прочитан: {}", e.toString());
			}
		}
		return ids;
	}

	/** Читаемые имена загруженных несовместимых модов вида «Roughly Enough Items (roughlyenoughitems)»; пусто — конфликта нет. */
	public static List<String> detect() {
		List<String> found = new ArrayList<>();
		for (String id : ids()) {
			if (id.equals(AdminControlMod.MOD_ID)) {
				continue;
			}
			var container = FabricLoader.getInstance().getModContainer(id);
			if (container.isPresent()) {
				ModContainer c = container.get();
				found.add(c.getMetadata().getName() + " (" + id + ")");
			}
		}
		return found;
	}

	public static String instruction() {
		return "Для игры с CW EMI отключите или удалите несовместимый просмотрщик рецептов (EMI / REI / JEI), затем перезапустите Minecraft.";
	}
}
