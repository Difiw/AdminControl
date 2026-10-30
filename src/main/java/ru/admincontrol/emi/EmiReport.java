package ru.admincontrol.emi;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import ru.admincontrol.AdminControlMod;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Отчёт администратору: для КАЖДОГО предмета всех установленных модов — как он получается (рецепт и станция либо способ
 * добычи). Строится теми же данными, что видит игрок в CW EMI, но без фильтра скрытых предметов. Запуск: сервер с флагом
 * -Dcw.emi.report=1 пишет cw-emi-report.md в папку сервера и останавливается.
 */
public final class EmiReport {
	private static final String[] CONTROL = {
			"ad_astra:tier_1_rocket", "ad_astra:tier_2_rocket", "ad_astra:tier_3_rocket", "ad_astra:tier_4_rocket",
			"ad_astra:rocket_fin", "ad_astra:steel_ingot", "ad_astra:desh_ingot", "ad_astra:compressor", "ad_astra:nasa_workbench",
			"minecraft:oak_leaves", "minecraft:oak_sapling", "minecraft:apple", "minecraft:grass_block", "minecraft:dirt",
			"minecraft:diamond", "minecraft:iron_ore", "minecraft:obsidian", "minecraft:cobweb", "minecraft:dragon_egg",
			"minecraft:barrier", "minecraft:bedrock", "minecraft:command_block", "minecraft:spawner", "minecraft:netherite_ingot",
			"minecraft:enchanted_golden_apple", "minecraft:elytra", "minecraft:nether_star", "minecraft:wither_skeleton_skull",
			"minecraft:trident", "minecraft:name_tag", "minecraft:zombie_spawn_egg", "minecraft:debug_stick"};

	private EmiReport() {
	}

	public static boolean requested() {
		return System.getProperty("cw.emi.report") != null;
	}

	public static Path run(MinecraftServer server) throws java.io.IOException {
		List<EmiRecipe> all = EmiServer.allForReport(server);
		Map<Item, List<EmiRecipe>> crafts = new HashMap<>();
		Map<Item, List<EmiRecipe>> sources = new HashMap<>();
		for (EmiRecipe r : all) {
			if (r.kind == EmiRecipe.ANVIL) {
				continue;
			}
			for (ItemStack o : r.outputs) {
				(r.kind == EmiRecipe.SOURCE ? sources : crafts).computeIfAbsent(o.getItem(), k -> new ArrayList<>()).add(r);
			}
		}
		Map<String, int[]> perMod = new TreeMap<>();
		Map<String, List<String>> lines = new TreeMap<>();
		int[] total = new int[5];
		for (Item item : Registries.ITEM) {
			if (item == Items.AIR) {
				continue;
			}
			Identifier id = Registries.ITEM.getId(item);
			List<EmiRecipe> c = crafts.getOrDefault(item, List.of());
			List<EmiRecipe> s = new ArrayList<>(sources.getOrDefault(item, List.of()));
			s.addAll(EmiSources.boxFor(item));
			int[] counts = perMod.computeIfAbsent(id.getNamespace(), k -> new int[5]);
			int cat;
			if (!c.isEmpty()) {
				cat = 1;
			} else if (s.stream().anyMatch(r -> !isFallback(r))) {
				cat = 2;
			} else if (s.stream().anyMatch(r -> r.note.equals("command"))) {
				cat = 4;
			} else if (!s.isEmpty()) {
				cat = 3;
			} else {
				cat = 0;
			}
			counts[0]++;
			total[0]++;
			if (cat != 0) {
				counts[cat]++;
				total[cat]++;
			}
			lines.computeIfAbsent(id.getNamespace(), k -> new ArrayList<>()).add(describeItem(id.toString(), c, s));
		}
		StringBuilder sb = new StringBuilder();
		sb.append("# Отчёт CW EMI: способы получения предметов\n\n");
		sb.append("Всего предметов: ").append(total[0]).append("\n\n");
		sb.append("- с рецептом (крафт на станции): ").append(total[1]).append('\n');
		sb.append("- без рецепта, но есть способ добычи (блок/моб/сундук/торговля/бокс/механика): ").append(total[2]).append('\n');
		sb.append("- только творческий режим: ").append(total[3]).append('\n');
		sb.append("- только команда /give: ").append(total[4]).append('\n');
		sb.append("- вообще без данных (ошибка): ").append(total[0] - total[1] - total[2] - total[3] - total[4]).append("\n\n");
		sb.append("## По модам\n\n| Мод | Предметов | Рецепт | Добыча | Creative | Команда |\n|---|---|---|---|---|---|\n");
		for (var e : perMod.entrySet()) {
			int[] v = e.getValue();
			sb.append("| ").append(e.getKey()).append(" | ").append(v[0]).append(" | ").append(v[1]).append(" | ").append(v[2]).append(" | ")
					.append(v[3]).append(" | ").append(v[4]).append(" |\n");
		}
		sb.append("\n## Типы рецептов (прочитано / не прочитано)\n\n");
		for (String line : EmiConvert.LAST_STATS) {
			sb.append("- ").append(line).append('\n');
		}
		sb.append("\n## Контрольные предметы\n\n");
		for (String ctrl : CONTROL) {
			Identifier ident = Identifier.tryParse(ctrl);
			if (ident == null || !Registries.ITEM.containsId(ident)) {
				sb.append("- ").append(ctrl).append(": предмета нет в этой сборке\n");
				continue;
			}
			Item item = Registries.ITEM.get(ident);
			List<EmiRecipe> s = new ArrayList<>(sources.getOrDefault(item, List.of()));
			s.addAll(EmiSources.boxFor(item));
			sb.append(describeItem(ctrl, crafts.getOrDefault(item, List.of()), s));
		}
		sb.append("\n## Все предметы по модам\n");
		for (var e : lines.entrySet()) {
			sb.append("\n### ").append(e.getKey()).append("\n\n");
			for (String line : e.getValue()) {
				sb.append(line);
			}
		}
		Path out = server.getRunDirectory().toPath().resolve("cw-emi-report.md");
		Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
		AdminControlMod.LOGGER.info("[CW EMI] отчёт записан: {}", out.toAbsolutePath());
		return out;
	}

	private static boolean isFallback(EmiRecipe r) {
		return r.note.equals("creative") || r.note.equals("creative_op") || r.note.equals("command");
	}

	private static String describeItem(String id, List<EmiRecipe> crafts, List<EmiRecipe> sources) {
		StringBuilder sb = new StringBuilder("- **").append(id).append("**\n");
		if (crafts.isEmpty() && sources.isEmpty()) {
			return sb.append("  - НЕТ ДАННЫХ\n").toString();
		}
		Map<String, List<EmiRecipe>> byStation = new LinkedHashMap<>();
		for (EmiRecipe r : crafts) {
			byStation.computeIfAbsent(stationName(r), k -> new ArrayList<>()).add(r);
		}
		for (var e : byStation.entrySet()) {
			sb.append("  - крафт на «").append(e.getKey()).append("»: ").append(e.getValue().size()).append(" рец.; пример: ")
					.append(recipeText(e.getValue().get(0))).append('\n');
		}
		int shown = 0;
		for (EmiRecipe r : sources) {
			if (shown++ >= 8) {
				sb.append("  - … и ещё ").append(sources.size() - 8).append(" способов\n");
				break;
			}
			sb.append("  - ").append(sourceText(r)).append('\n');
		}
		return sb.toString();
	}

	private static String stationName(EmiRecipe r) {
		return r.station.isEmpty() ? "тип " + r.typeId : Registries.ITEM.getId(r.station.getItem()).toString();
	}

	private static String recipeText(EmiRecipe r) {
		List<String> parts = new ArrayList<>();
		for (List<ItemStack> slot : r.inputs) {
			if (slot.isEmpty()) {
				continue;
			}
			ItemStack first = slot.get(0);
			parts.add(Registries.ITEM.getId(first.getItem()) + (first.getCount() > 1 ? "×" + first.getCount() : "") + (slot.size() > 1 ? "(+" + (slot.size() - 1) + " вар.)" : ""));
		}
		String text = String.join(", ", parts.size() > 8 ? parts.subList(0, 8) : parts) + (parts.size() > 8 ? ", … (" + parts.size() + " слотов)" : "");
		return text + " → " + r.outputs.get(0).getCount() + "×" + Registries.ITEM.getId(r.outputs.get(0).getItem())
				+ (r.note.isEmpty() ? "" : " [" + r.note + "]");
	}

	private static String sourceText(EmiRecipe r) {
		String d = r.typeId;
		return switch (r.note) {
			case "block", "block_silk", "block_shears", "block_silk_shears" -> {
				String block = d.contains("|") ? d.substring(0, d.indexOf('|')) : d;
				String how = switch (r.note) {
					case "block_silk" -> "добыть шёлковым касанием";
					case "block_silk_shears" -> "срезать ножницами или добыть шёлковым касанием";
					case "block_shears" -> "срезать ножницами";
					default -> r.station.isEmpty() ? "сломать любым способом (в т.ч. рукой)"
							: "сломать инструментом " + Registries.ITEM.getId(r.station.getItem()) + " или лучше";
				};
				yield "добыча из блока " + block + ": " + how;
			}
			case "entity" -> "выпадает с моба " + d;
			case "chest" -> "находка в сундуке " + d;
			case "fishing" -> "рыбалка";
			case "trade" -> "торговля: " + d.replace("|", ", уровень ");
			case "gameplay" -> "игровая механика: " + d;
			case "mech" -> "особая механика: " + d;
			case "box" -> "бокс админов: в " + d.split("/")[0] + " из " + d.split("/")[1] + " наборов (шанс ≈ " + Math.round(r.xp) + "%)";
			case "creative" -> "только творческий режим" + (d.contains("|") ? ", вкладка " + d.substring(d.indexOf('|') + 1) : "") + " или /give";
			case "creative_op" -> "только вкладка операторских предметов или /give";
			case "command" -> "только команда /give";
			default -> r.note + " " + d;
		};
	}
}
