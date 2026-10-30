package ru.admincontrol.emi;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Общий фильтр рецептов. Сервер вызывает его с правилами конкретного игрока (авторитетно), клиент — тем же кодом
 * для режима без серверной поддержки. Закрытые варианты не просто скрываются, а вырезаются из данных.
 */
public final class EmiFilter {
	public static final int MAX_RECIPES = 160, MAX_PER_GROUP = 48;

	public static final class Out {
		public final Map<String, List<EmiRecipe>> groups = new LinkedHashMap<>();
		public boolean locked;
	}

	private EmiFilter() {
	}

	/** Копия рецепта без закрытых вариантов либо null, если рецепт целиком закрыт (результат, станция или целый слот). */
	public static EmiRecipe sanitize(EmiRecipe src, Predicate<Item> hidden) {
		for (ItemStack out : src.outputs) {
			if (hidden.test(out.getItem())) {
				return null;
			}
		}
		if (!src.station.isEmpty() && hidden.test(src.station.getItem())) {
			return null;
		}
		EmiRecipe copy = src.copyShell();
		copy.outputs = new ArrayList<>(src.outputs);
		for (List<ItemStack> slot : src.inputs) {
			if (slot.isEmpty()) {
				copy.inputs.add(List.of());
				continue;
			}
			List<ItemStack> kept = new ArrayList<>(slot.size());
			for (ItemStack s : slot) {
				if (!hidden.test(s.getItem())) {
					kept.add(s);
				}
			}
			if (kept.isEmpty()) {
				return null;
			}
			copy.inputs.add(kept);
		}
		return copy;
	}

	public static Out build(List<EmiRecipe> all, Item item, boolean obtain, Predicate<Item> hidden) {
		Out out = new Out();
		int total = 0;
		for (EmiRecipe candidate : all) {
			boolean match = obtain ? candidate.kind != EmiRecipe.ANVIL && candidate.outputsContain(item)
					: candidate.kind != EmiRecipe.SOURCE && candidate.inputsContain(item);
			if (!match) {
				continue;
			}
			EmiRecipe safe = sanitize(candidate, hidden);
			if (safe == null) {
				out.locked = true;
				continue;
			}
			List<EmiRecipe> group = out.groups.computeIfAbsent(safe.stationKey(), k -> new ArrayList<>());
			if (group.size() < MAX_PER_GROUP && total < MAX_RECIPES) {
				group.add(safe);
				total++;
			}
		}
		List<EmiRecipe> sources = out.groups.remove("source");
		if (sources != null) {
			out.groups.put("source", sources); // способы получения всегда последними
		}
		return out;
	}
}
