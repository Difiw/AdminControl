package ru.admincontrol.emi;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ToolItem;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.ShapelessRecipe;
import net.minecraft.recipe.SmithingTransformRecipe;
import net.minecraft.recipe.StonecuttingRecipe;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import ru.admincontrol.lumira.CoinRecipes;
import ru.admincontrol.lumira.LumiraContent;
import ru.admincontrol.lumira.block.RuneAnvilBlock;
import ru.admincontrol.lumira.block.RuneAnvilLogic;
import ru.admincontrol.lumira.item.NecklaceItem;
import ru.admincontrol.lumira.recipe.EditableCoinRecipe;
import ru.admincontrol.lumira.recipe.RuneShapedRecipe;
import ru.admincontrol.mixin.SmithingTransformRecipeAccessor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Перевод рецептов игры в карточки CW EMI. Нестандартные станции подключаются через {@link Adapter}: адаптер
 * получает рецепт и возвращает карточку по ФАКТИЧЕСКИМ данным API мода либо null («не мой рецепт»).
 * Если ни один адаптер не взялся, а у рецепта нет читаемых ингредиентов и результата, он не показывается вовсе.
 */
public final class EmiConvert {
	public interface Adapter {
		EmiRecipe convert(Recipe<?> recipe, DynamicRegistryManager registries);
	}

	private static final List<Adapter> ADAPTERS = new CopyOnWriteArrayList<>();
	/** Итоги последнего преобразования по типам рецептов (для отчёта администратора). */
	public static volatile List<String> LAST_STATS = List.of();

	static {
		register(EmiConvert::pointBlank);
	}

	private EmiConvert() {
	}

	/** Точка расширения для будущих станций; адаптеры опрашиваются раньше встроенной логики. */
	public static void register(Adapter adapter) {
		if (adapter != null) {
			ADAPTERS.add(adapter);
		}
	}

	public static List<EmiRecipe> convertAll(RecipeManager manager, DynamicRegistryManager registries) {
		List<EmiRecipe> out = new ArrayList<>();
		java.util.Map<String, int[]> stats = new java.util.TreeMap<>();
		for (Recipe<?> recipe : manager.values()) {
			Identifier typeId = Registries.RECIPE_TYPE.getId(recipe.getType());
			int[] st = stats.computeIfAbsent(typeId == null ? "unknown" : typeId.toString(), k -> new int[2]);
			try {
				EmiRecipe converted = convert(recipe, registries);
				if (converted != null && !converted.outputs.isEmpty()) {
					out.add(converted);
					st[0]++;
				} else {
					st[1]++;
				}
			} catch (RuntimeException e) {
				// чужой рецепт с неожиданным поведением не должен ронять просмотрщик
				st[1]++;
			}
		}
		List<String> summary = new ArrayList<>();
		for (var e : stats.entrySet()) {
			summary.add(e.getKey() + ": показано " + e.getValue()[0] + ", не удалось прочитать " + e.getValue()[1]);
			if (e.getValue()[1] > 0 && !e.getKey().startsWith("minecraft:")) {
				ru.admincontrol.AdminControlMod.LOGGER.info("[CW EMI] тип рецептов {}: показано {}, не удалось прочитать {}", e.getKey(), e.getValue()[0], e.getValue()[1]);
			}
		}
		LAST_STATS = List.copyOf(summary);
		out.addAll(anvilRecipes());
		return out;
	}

	public static EmiRecipe convert(Recipe<?> recipe, DynamicRegistryManager registries) {
		for (Adapter adapter : ADAPTERS) {
			try {
				EmiRecipe custom = adapter.convert(recipe, registries);
				if (custom != null) {
					return custom;
				}
			} catch (RuntimeException ignored) {
			}
		}
		if (recipe instanceof EditableCoinRecipe) {
			return coin(recipe, registries);
		}
		ItemStack output = recipe.getOutput(registries);
		if (output == null || output.isEmpty()) {
			// Рецепты на CodecRecipe (Ad Astra и др.) не переопределяют getOutput(): результат лежит в поле/аксессоре записи.
			output = reflectOutput(recipe);
			if (output == null || output.isEmpty()) {
				return null;
			}
		}
		if (recipe instanceof RuneShapedRecipe rune) {
			EmiRecipe r = grid(rune, output);
			r.station = new ItemStack(LumiraContent.RUNE_WORKBENCH.asItem());
			r.note = runeCategory(output);
			return toRuneGrid(r);
		}
		if (recipe instanceof ShapedRecipe shaped) {
			EmiRecipe r = grid(shaped, output);
			r.station = new ItemStack(Items.CRAFTING_TABLE);
			return r;
		}
		if (recipe instanceof ShapelessRecipe) {
			EmiRecipe r = new EmiRecipe();
			r.kind = EmiRecipe.SHAPELESS;
			addIngredients(r, recipe.getIngredients());
			r.outputs.add(output.copy());
			r.station = new ItemStack(Items.CRAFTING_TABLE);
			return r.inputs.isEmpty() ? null : r;
		}
		if (recipe instanceof AbstractCookingRecipe cooking) {
			EmiRecipe r = new EmiRecipe();
			r.kind = EmiRecipe.COOKING;
			addIngredients(r, recipe.getIngredients());
			r.outputs.add(output.copy());
			r.time = cooking.getCookTime();
			r.xp = cooking.getExperience();
			r.station = recipe.createIcon();
			return r.inputs.isEmpty() ? null : r;
		}
		if (recipe instanceof StonecuttingRecipe) {
			EmiRecipe r = new EmiRecipe();
			r.kind = EmiRecipe.STONECUT;
			addIngredients(r, recipe.getIngredients());
			r.outputs.add(output.copy());
			r.station = recipe.createIcon();
			return r.inputs.isEmpty() ? null : r;
		}
		if (recipe instanceof SmithingTransformRecipe smithing) {
			SmithingTransformRecipeAccessor acc = (SmithingTransformRecipeAccessor) smithing;
			EmiRecipe r = new EmiRecipe();
			r.kind = EmiRecipe.SMITHING;
			List<Ingredient> parts = List.of(acc.adminControl$template(), acc.adminControl$base(), acc.adminControl$addition());
			addIngredients(r, parts);
			r.outputs.add(output.copy());
			r.station = recipe.createIcon();
			return r.inputs.isEmpty() ? null : r;
		}
		// Неизвестный тип: показываем то, что честно отдаёт API, а если оно пусто — читаем поля рецепта.
		EmiRecipe r = new EmiRecipe();
		r.kind = EmiRecipe.GENERIC;
		addIngredients(r, recipe.getIngredients());
		if (r.inputs.isEmpty()) {
			reflectInputs(recipe, r);
		}
		if (r.inputs.isEmpty()) {
			return null;
		}
		r.outputs.add(output.copy());
		applyStation(recipe, r);
		r.note = machineNote(recipe);
		return r;
	}

	/** Известные станции модов: тип рецепта → предмет-станция (данные взяты из самих модов, проверены по их классам). */
	private static final java.util.Map<String, String> KNOWN_STATIONS = java.util.Map.of(
			"ad_astra:nasa_workbench", "ad_astra:nasa_workbench",
			"ad_astra:alloying", "ad_astra:etrionic_blast_furnace",
			"ad_astra:compressing", "ad_astra:compressor",
			"ad_astra:cryo_freezing", "ad_astra:cryo_freezer",
			"ad_astra:oxygen_loading", "ad_astra:oxygen_loader",
			"ad_astra:refining", "ad_astra:fuel_refinery");

	/** Результат рецепта, не отданный через getOutput(): аксессоры/поля вида result, output, results. */
	private static ItemStack reflectOutput(Recipe<?> recipe) {
		for (String name : new String[]{"result", "output", "getResult", "getOutput", "getResultItem", "resultItem", "results", "outputs", "getResults"}) {
			try {
				java.lang.reflect.Method m = recipe.getClass().getMethod(name);
				if (m.getParameterCount() != 0) {
					continue;
				}
				ItemStack s = firstStack(m.invoke(recipe));
				if (s != null && !s.isEmpty()) {
					return s.copy();
				}
			} catch (ReflectiveOperationException | RuntimeException ignored) {
			}
		}
		for (Class<?> cls = recipe.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
			for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
				if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
					continue;
				}
				try {
					f.setAccessible(true);
					ItemStack s = firstStack(f.get(recipe));
					if (s != null && !s.isEmpty()) {
						return s.copy();
					}
				} catch (ReflectiveOperationException | RuntimeException ignored) {
				}
			}
		}
		return null;
	}

	private static ItemStack firstStack(Object v) {
		if (v instanceof ItemStack s) {
			return s;
		}
		if (v instanceof java.util.Collection<?> col) {
			for (Object e : col) {
				if (e instanceof ItemStack s && !s.isEmpty()) {
					return s;
				}
			}
		}
		if (v instanceof ItemStack[] arr) {
			for (ItemStack s : arr) {
				if (s != null && !s.isEmpty()) {
					return s;
				}
			}
		}
		return null;
	}

	/** Время и энергия машинных рецептов (cookingTime()/energy() у записей модов), если мод их отдаёт. */
	private static String machineNote(Recipe<?> recipe) {
		StringBuilder sb = new StringBuilder();
		try {
			Object t = recipe.getClass().getMethod("cookingTime").invoke(recipe);
			if (t instanceof Integer n && n > 0) {
				sb.append("Время: ").append(String.format(java.util.Locale.US, "%.1f", n / 20f)).append(" с");
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
		}
		try {
			Object e = recipe.getClass().getMethod("energy").invoke(recipe);
			if (e instanceof Integer n && n > 0) {
				sb.append(sb.length() > 0 ? ", " : "").append("энергия: ").append(n);
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
		}
		return sb.toString();
	}

	/** Станция неизвестного типа: createIcon(), иначе предмет-блок с id типа рецепта; иначе честно «другой тип». */
	private static void applyStation(Recipe<?> recipe, EmiRecipe r) {
		Identifier known = Registries.RECIPE_TYPE.getId(recipe.getType());
		String knownItem = known == null ? null : KNOWN_STATIONS.get(known.toString());
		if (knownItem != null) {
			Identifier ident = Identifier.tryParse(knownItem);
			if (ident != null && Registries.ITEM.containsId(ident)) {
				r.station = new ItemStack(Registries.ITEM.get(ident));
				return;
			}
		}
		ItemStack icon = recipe.createIcon();
		boolean defaultIcon = icon.isOf(Items.CRAFTING_TABLE) && recipe.getType() != RecipeType.CRAFTING;
		if (!icon.isEmpty() && !defaultIcon) {
			r.station = icon;
			return;
		}
		Identifier type = Registries.RECIPE_TYPE.getId(recipe.getType());
		if (type != null) {
			Item guess = stationByName(type);
			if (guess != null) {
				r.station = new ItemStack(guess);
				return;
			}
		}
		r.typeId = type == null ? "unknown" : type.toString();
	}

	private static Item stationByName(Identifier type) {
		String path = type.getPath();
		List<String> candidates = new ArrayList<>(List.of(path, path + "_machine", path + "_station", path + "_table", path + "_block"));
		for (String suffix : new String[]{"_recipe", "_crafting", "_type"}) {
			if (path.endsWith(suffix)) {
				candidates.add(path.substring(0, path.length() - suffix.length()));
			}
		}
		if (path.endsWith("ing")) {
			String stem = path.substring(0, path.length() - 3);
			candidates.add(stem + "er");
			candidates.add(stem + "or");
			candidates.add(stem + "e" + "r");
		}
		if (path.endsWith("s")) {
			candidates.add(path.substring(0, path.length() - 1));
		}
		for (String c : candidates) {
			Identifier id = new Identifier(type.getNamespace(), c);
			if (Registries.ITEM.containsId(id)) {
				return Registries.ITEM.get(id);
			}
		}
		return null;
	}

	// ---------- чтение полей чужих рецептов ----------
	/** Слот из произвольного объекта: Ingredient, ItemStack или «ингредиент с количеством» (getItemStacks/getStacks + getCount). */
	private static List<ItemStack> slotOf(Object o) {
		try {
			if (o instanceof Ingredient ing) {
				List<ItemStack> alts = new ArrayList<>();
				for (ItemStack s : ing.getMatchingStacks()) {
					if (alts.size() < EmiRecipe.MAX_ALTS) {
						alts.add(s.copy());
					}
				}
				return alts;
			}
			if (o instanceof ItemStack stack) {
				return stack.isEmpty() ? null : new ArrayList<>(List.of(stack.copy()));
			}
			if (o == null) {
				return null;
			}
			for (String name : new String[]{"getItemStacks", "getStacks", "getMatchingStacks"}) {
				java.lang.reflect.Method m;
				try {
					m = o.getClass().getMethod(name);
				} catch (NoSuchMethodException e) {
					continue;
				}
				Object res = m.invoke(o);
				List<ItemStack> alts = new ArrayList<>();
				if (res instanceof java.util.Collection<?> col) {
					for (Object e : col) {
						if (e instanceof ItemStack s && alts.size() < EmiRecipe.MAX_ALTS) {
							alts.add(s.copy());
						}
					}
				} else if (res instanceof ItemStack[] arr) {
					for (ItemStack s : arr) {
						if (alts.size() < EmiRecipe.MAX_ALTS) {
							alts.add(s.copy());
						}
					}
				}
				int count = 1;
				try {
					Object c = o.getClass().getMethod("getCount").invoke(o);
					if (c instanceof Integer n) {
						count = Math.max(1, n);
					}
				} catch (NoSuchMethodException ignored) {
				}
				for (ItemStack s : alts) {
					s.setCount(count);
				}
				return alts.isEmpty() ? null : alts;
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
		}
		return null;
	}

	private static void reflectInputs(Recipe<?> recipe, EmiRecipe out) {
		java.util.Set<Object> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		for (Class<?> cls = recipe.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
			for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
				if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || out.inputs.size() >= EmiRecipe.MAX_SLOTS) {
					continue;
				}
				try {
					f.setAccessible(true);
					Object v = f.get(recipe);
					if (v == null || !seen.add(v) || v instanceof Identifier || v instanceof String) {
						continue;
					}
					if (v instanceof java.util.Collection<?> col) {
						for (Object e : col) {
							List<ItemStack> slot = e instanceof ItemStack ? null : slotOf(e);
							if (slot != null && out.inputs.size() < EmiRecipe.MAX_SLOTS) {
								out.inputs.add(slot);
							}
						}
					} else if (v instanceof Object[] arr) {
						for (Object e : arr) {
							List<ItemStack> slot = e instanceof ItemStack ? null : slotOf(e);
							if (slot != null && out.inputs.size() < EmiRecipe.MAX_SLOTS) {
								out.inputs.add(slot);
							}
						}
					} else if (v instanceof Ingredient) {
						List<ItemStack> slot = slotOf(v);
						if (slot != null && !slot.isEmpty()) {
							out.inputs.add(slot);
						}
					}
				} catch (ReflectiveOperationException | RuntimeException ignored) {
				}
			}
		}
	}

	/** Point Blank: ингредиенты лежат в getPointBlankIngredients() (с количеством), станция — «Принтер». Данные читаются только из API мода. */
	private static EmiRecipe pointBlank(Recipe<?> recipe, DynamicRegistryManager registries) {
		if (!recipe.getClass().getName().equals("com.vicmatskiv.pointblank.crafting.PointBlankRecipe")) {
			return null;
		}
		try {
			Object list = recipe.getClass().getMethod("getPointBlankIngredients").invoke(recipe);
			EmiRecipe r = new EmiRecipe();
			r.kind = EmiRecipe.GENERIC;
			if (list instanceof java.util.Collection<?> col) {
				for (Object o : col) {
					List<ItemStack> slot = slotOf(o);
					if (slot != null && r.inputs.size() < EmiRecipe.MAX_SLOTS) {
						r.inputs.add(slot);
					}
				}
			}
			ItemStack out = recipe.getOutput(registries);
			if (r.inputs.isEmpty() || out == null || out.isEmpty()) {
				return null;
			}
			r.outputs.add(out.copy());
			Identifier printer = new Identifier("pointblank", "printer");
			if (Registries.ITEM.containsId(printer)) {
				r.station = new ItemStack(Registries.ITEM.get(printer));
			} else {
				r.typeId = "pointblank:default";
			}
			return r;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	/** Рунный верстак всегда 4×4: рецепт меньшего размера раскладывается от верхнего левого угла, как в окне верстака. */
	private static EmiRecipe toRuneGrid(EmiRecipe r) {
		int size = 4;
		List<List<ItemStack>> cells = new ArrayList<>();
		for (int i = 0; i < size * size; i++) {
			cells.add(List.of());
		}
		for (int row = 0; row < r.height; row++) {
			for (int col = 0; col < r.width; col++) {
				int from = row * r.width + col;
				if (from < r.inputs.size() && row < size && col < size) {
					cells.set(row * size + col, r.inputs.get(from));
				}
			}
		}
		r.inputs = cells;
		r.width = size;
		r.height = size;
		return r;
	}

	private static EmiRecipe grid(ShapedRecipe recipe, ItemStack output) {
		EmiRecipe r = new EmiRecipe();
		r.kind = EmiRecipe.GRID;
		r.width = recipe.getWidth();
		r.height = recipe.getHeight();
		addIngredients(r, recipe.getIngredients());
		r.outputs.add(output.copy());
		return r;
	}

	private static EmiRecipe coin(Recipe<?> recipe, DynamicRegistryManager registries) {
		boolean lyra = recipe.getOutput(registries).isOf(LumiraContent.item("coin_lyra"));
		List<String> need = CoinRecipes.recipe(lyra);
		if (need.isEmpty()) {
			return null; // рецепт монеты выключен: ничего не придумываем
		}
		EmiRecipe r = new EmiRecipe();
		r.kind = EmiRecipe.SHAPELESS;
		for (String id : need) {
			net.minecraft.util.Identifier ident = net.minecraft.util.Identifier.tryParse(id);
			if (ident != null && Registries.ITEM.containsId(ident) && r.inputs.size() < 9) {
				r.inputs.add(new ArrayList<>(List.of(new ItemStack(Registries.ITEM.get(ident)))));
			}
		}
		r.outputs.add(new ItemStack(LumiraContent.item(lyra ? "coin_lyra" : "coin_myrtle"), CoinRecipes.count(lyra)));
		r.station = new ItemStack(Items.CRAFTING_TABLE);
		return r.inputs.size() == need.size() ? r : null;
	}

	private static void addIngredients(EmiRecipe r, List<Ingredient> ingredients) {
		for (Ingredient ingredient : ingredients) {
			if (r.inputs.size() >= EmiRecipe.MAX_SLOTS) {
				break;
			}
			List<ItemStack> alts = new ArrayList<>();
			for (ItemStack s : ingredient.getMatchingStacks()) {
				if (alts.size() >= EmiRecipe.MAX_ALTS) {
					break;
				}
				alts.add(s.copy());
			}
			r.inputs.add(alts);
		}
	}

	public static String runeCategory(ItemStack out) {
		if (out.getItem() instanceof ArmorItem) {
			return "Броня";
		}
		if (out.getItem() instanceof NecklaceItem) {
			return "Украшения";
		}
		if (out.getItem() instanceof ToolItem) {
			return "Инструменты";
		}
		return "Прочее";
	}

	/** Ремонт в рунной наковальне — отдельный вид «рецепта»: предмет + материал + топливо, а не обычный крафт. */
	public static List<EmiRecipe> anvilRecipes() {
		List<EmiRecipe> out = new ArrayList<>();
		if (LumiraContent.RUNE_ANVIL == null) {
			return out;
		}
		List<ItemStack> fuels = new ArrayList<>();
		for (Item item : Registries.ITEM) {
			if (RuneAnvilBlock.fuelValue(item) > 0 && fuels.size() < EmiRecipe.MAX_ALTS) {
				fuels.add(new ItemStack(item));
			}
		}
		for (Item item : Registries.ITEM) {
			ItemStack target = new ItemStack(item);
			if (!target.isDamageable() && !(item instanceof NecklaceItem)) {
				continue;
			}
			Ingredient material = RuneAnvilBlock.repairMaterial(target);
			if (material == null) {
				continue;
			}
			EmiRecipe r = new EmiRecipe();
			r.kind = EmiRecipe.ANVIL;
			r.inputs.add(new ArrayList<>(List.of(target)));
			List<ItemStack> mats = new ArrayList<>();
			for (ItemStack s : material.getMatchingStacks()) {
				if (mats.size() < EmiRecipe.MAX_ALTS) {
					mats.add(s.copy());
				}
			}
			r.inputs.add(mats);
			r.inputs.add(new ArrayList<>(fuels));
			r.outputs.add(target.copy());
			r.station = new ItemStack(LumiraContent.RUNE_ANVIL.asItem());
			r.note = item instanceof NecklaceItem ? RuneAnvilLogic.restore(target) + " зарядов"
					: RuneAnvilLogic.restore(target) + " ед. прочности";
			if (!mats.isEmpty()) {
				out.add(r);
			}
		}
		return out;
	}
}
