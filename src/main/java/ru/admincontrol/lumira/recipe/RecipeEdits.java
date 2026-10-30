package ru.admincontrol.lumira.recipe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.ShapelessRecipe;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.command.CommandUtil;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Редактор крафтов: правки администратора хранятся в config/admin_control/recipe_edits.json и накладываются на
 * JSON-рецепты при каждой (пере)загрузке данных (см. RecipeManagerMixin). Правка = заменить рецепт с тем же id,
 * удалить (null) или создать новый. После сохранения сервер выполняет перезагрузку данных, и рецепты уходят клиентам.
 */
public final class RecipeEdits {
	public static final Identifier LIST_REQ = new Identifier(AdminControlMod.MOD_ID, "recipe_ed_req");
	public static final Identifier LIST = new Identifier(AdminControlMod.MOD_ID, "recipe_ed_list");
	public static final Identifier SAVE = new Identifier(AdminControlMod.MOD_ID, "recipe_ed_save");
	public static final Identifier DELETE = new Identifier(AdminControlMod.MOD_ID, "recipe_ed_del");
	public static final Identifier RESTORE = new Identifier(AdminControlMod.MOD_ID, "recipe_ed_restore");

	public static final int SHAPED = 0, SHAPELESS = 1, SMELTING = 2, BLASTING = 3, RUNE = 4, SMOKING = 5, OTHER = 9;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** id -> JSON рецепта, либо null = рецепт удалён. */
	private static final Map<Identifier, JsonObject> EDITS = new LinkedHashMap<>();
	private static boolean loaded;

	private RecipeEdits() {
	}

	/** Строка для редактора: что показать и что изменить. */
	public static final class Entry {
		public String id;
		public int kind;
		public String[] slots = new String[9];
		public String output = "";
		public int count = 1;
		public float xp;
		public int time = 200;
		public boolean edited;
		public boolean custom;
		public boolean removed;

		public void write(PacketByteBuf buf) {
			buf.writeString(id, 128);
			buf.writeVarInt(kind);
			for (String s : slots) {
				buf.writeString(s == null ? "" : s, 128);
			}
			buf.writeString(output == null ? "" : output, 128);
			buf.writeVarInt(count);
			buf.writeFloat(xp);
			buf.writeVarInt(time);
			buf.writeBoolean(edited);
			buf.writeBoolean(custom);
			buf.writeBoolean(removed);
		}

		public static Entry read(PacketByteBuf buf) {
			Entry e = new Entry();
			e.id = buf.readString(128);
			e.kind = buf.readVarInt();
			for (int i = 0; i < 9; i++) {
				String s = buf.readString(128);
				e.slots[i] = s.isEmpty() ? null : s;
			}
			e.output = buf.readString(128);
			e.count = buf.readVarInt();
			e.xp = buf.readFloat();
			e.time = buf.readVarInt();
			e.edited = buf.readBoolean();
			e.custom = buf.readBoolean();
			e.removed = buf.readBoolean();
			return e;
		}
	}

	// ---------- файл правок ----------
	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("admin_control").resolve("recipe_edits.json");
	}

	public static synchronized void load() {
		EDITS.clear();
		loaded = true;
		Path f = file();
		if (!Files.exists(f)) {
			return;
		}
		try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
			JsonObject root = GSON.fromJson(r, JsonObject.class);
			if (root == null) {
				return;
			}
			for (Map.Entry<String, JsonElement> e : root.entrySet()) {
				Identifier id = Identifier.tryParse(e.getKey());
				if (id == null) {
					continue;
				}
				EDITS.put(id, e.getValue().isJsonObject() ? e.getValue().getAsJsonObject() : null);
			}
		} catch (Exception ex) {
			AdminControlMod.LOGGER.warn("recipe_edits.json не прочитан: {}", ex.toString());
		}
	}

	private static synchronized void persist() {
		JsonObject root = new JsonObject();
		for (Map.Entry<Identifier, JsonObject> e : EDITS.entrySet()) {
			root.add(e.getKey().toString(), e.getValue() == null ? com.google.gson.JsonNull.INSTANCE : e.getValue());
		}
		try {
			Files.createDirectories(file().getParent());
			try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
				GSON.toJson(root, w);
			}
		} catch (IOException ex) {
			AdminControlMod.LOGGER.warn("recipe_edits.json не записан: {}", ex.toString());
		}
	}

	/** Вызывается из RecipeManagerMixin: накладывает правки на карту JSON-рецептов. */
	public static synchronized Map<Identifier, JsonElement> overlay(Map<Identifier, JsonElement> source) {
		if (!loaded) {
			load();
		}
		if (EDITS.isEmpty()) {
			return source;
		}
		Map<Identifier, JsonElement> out = new LinkedHashMap<>(source);
		for (Map.Entry<Identifier, JsonObject> e : EDITS.entrySet()) {
			if (e.getValue() == null) {
				out.remove(e.getKey());
			} else {
				out.put(e.getKey(), e.getValue());
			}
		}
		return out;
	}

	// ---------- превращение рецепта в строку редактора ----------
	private static String ingredientId(Ingredient ing) {
		JsonElement json = ing.toJson();
		if (json.isJsonArray()) {
			JsonArray arr = json.getAsJsonArray();
			json = arr.size() > 0 ? arr.get(0) : null;
		}
		if (json != null && json.isJsonObject()) {
			JsonObject o = json.getAsJsonObject();
			if (o.has("item")) {
				return o.get("item").getAsString();
			}
			if (o.has("tag")) {
				return "#" + o.get("tag").getAsString();
			}
		}
		return null;
	}

	private static Entry entryOf(Recipe<?> recipe, DynamicRegistryManager registries) {
		Entry e = new Entry();
		e.id = recipe.getId().toString();
		var out = recipe.getOutput(registries);
		e.output = out.isEmpty() ? "" : Registries.ITEM.getId(out.getItem()).toString();
		e.count = Math.max(1, out.getCount());
		if (recipe instanceof RuneShapedRecipe rune) {
			e.kind = RUNE;
			fillShaped(e, rune);
		} else if (recipe instanceof ShapedRecipe shaped) {
			e.kind = SHAPED;
			fillShaped(e, shaped);
		} else if (recipe instanceof ShapelessRecipe shapeless) {
			e.kind = SHAPELESS;
			List<Ingredient> list = shapeless.getIngredients();
			for (int i = 0; i < Math.min(9, list.size()); i++) {
				e.slots[i] = ingredientId(list.get(i));
			}
		} else if (recipe instanceof AbstractCookingRecipe cook) {
			String type = Registries.RECIPE_TYPE.getId(recipe.getType()).getPath();
			e.kind = type.equals("blasting") ? BLASTING : type.equals("smoking") ? SMOKING : type.equals("smelting") ? SMELTING : OTHER;
			e.slots[0] = cook.getIngredients().isEmpty() ? null : ingredientId(cook.getIngredients().get(0));
			e.xp = cook.getExperience();
			e.time = cook.getCookTime();
		} else {
			e.kind = OTHER;
		}
		return e;
	}

	private static void fillShaped(Entry e, ShapedRecipe r) {
		List<Ingredient> ings = r.getIngredients();
		for (int y = 0; y < r.getHeight() && y < 3; y++) {
			for (int x = 0; x < r.getWidth() && x < 3; x++) {
				Ingredient ing = ings.get(x + y * r.getWidth());
				e.slots[x + y * 3] = ing.isEmpty() ? null : ingredientId(ing);
			}
		}
	}

	public static boolean relevant(Recipe<?> recipe, DynamicRegistryManager registries) {
		if (recipe.getId().getNamespace().equals(AdminControlMod.MOD_ID)) {
			return true;
		}
		var out = recipe.getOutput(registries);
		return !out.isEmpty() && Registries.ITEM.getId(out.getItem()).getNamespace().equals(AdminControlMod.MOD_ID);
	}

	public static void sendList(MinecraftServer server, ServerPlayerEntity player) {
		if (player == null || player.networkHandler == null || !ServerPlayNetworking.canSend(player, LIST)) {
			return;
		}
		DynamicRegistryManager reg = server.getRegistryManager();
		List<Entry> entries = new ArrayList<>();
		for (Recipe<?> recipe : server.getRecipeManager().values()) {
			if (!relevant(recipe, reg)) {
				continue;
			}
			Entry e = entryOf(recipe, reg);
			Identifier id = recipe.getId();
			e.edited = EDITS.containsKey(id);
			e.custom = id.getPath().startsWith("custom/");
			entries.add(e);
		}
		// удалённые рецепты показываем отдельно, чтобы их можно было вернуть
		List<String> removed = new ArrayList<>();
		for (Map.Entry<Identifier, JsonObject> ed : EDITS.entrySet()) {
			if (ed.getValue() == null) {
				removed.add(ed.getKey().toString());
			}
		}
		entries.sort((a, b) -> a.id.compareTo(b.id));
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(entries.size());
		for (Entry e : entries) {
			e.write(buf);
		}
		buf.writeVarInt(removed.size());
		for (String id : removed) {
			buf.writeString(id, 128);
		}
		ServerPlayNetworking.send(player, LIST, buf);
	}

	// ---------- превращение строки редактора в JSON ----------
	private static JsonObject ingredientJson(String s) {
		JsonObject o = new JsonObject();
		if (s.startsWith("#")) {
			o.addProperty("tag", s.substring(1));
		} else {
			o.addProperty("item", s);
		}
		return o;
	}

	private static boolean validIngredient(String s) {
		if (s == null || s.isEmpty()) {
			return false;
		}
		if (s.startsWith("#")) {
			return Identifier.tryParse(s.substring(1)) != null;
		}
		Identifier id = Identifier.tryParse(s);
		return id != null && Registries.ITEM.containsId(id);
	}

	/** null — рецепт неверный (нет ингредиентов, нет результата). */
	public static JsonObject toJson(Entry e) {
		Identifier out = Identifier.tryParse(e.output == null ? "" : e.output);
		if (out == null || !Registries.ITEM.containsId(out)) {
			return null;
		}
		JsonObject root = new JsonObject();
		root.addProperty("category", "misc");
		int count = Math.max(1, Math.min(64, e.count));
		if (e.kind == SHAPED || e.kind == RUNE) {
			int minX = 3, minY = 3, maxX = -1, maxY = -1;
			for (int i = 0; i < 9; i++) {
				if (validIngredient(e.slots[i])) {
					minX = Math.min(minX, i % 3);
					maxX = Math.max(maxX, i % 3);
					minY = Math.min(minY, i / 3);
					maxY = Math.max(maxY, i / 3);
				}
			}
			if (maxX < 0) {
				return null;
			}
			root.addProperty("type", e.kind == RUNE ? AdminControlMod.MOD_ID + ":rune_shaped" : "minecraft:crafting_shaped");
			JsonArray pattern = new JsonArray();
			JsonObject key = new JsonObject();
			Map<String, Character> letters = new LinkedHashMap<>();
			for (int y = minY; y <= maxY; y++) {
				StringBuilder row = new StringBuilder();
				for (int x = minX; x <= maxX; x++) {
					String s = e.slots[x + y * 3];
					if (!validIngredient(s)) {
						row.append(' ');
						continue;
					}
					Character ch = letters.get(s);
					if (ch == null) {
						ch = (char) ('A' + letters.size());
						letters.put(s, ch);
						key.add(String.valueOf(ch), ingredientJson(s));
					}
					row.append(ch);
				}
				pattern.add(row.toString());
			}
			root.add("pattern", pattern);
			root.add("key", key);
			JsonObject result = new JsonObject();
			result.addProperty("item", out.toString());
			result.addProperty("count", count);
			root.add("result", result);
		} else if (e.kind == SHAPELESS) {
			JsonArray ings = new JsonArray();
			for (String s : e.slots) {
				if (validIngredient(s)) {
					ings.add(ingredientJson(s));
				}
			}
			if (ings.size() == 0) {
				return null;
			}
			root.addProperty("type", "minecraft:crafting_shapeless");
			root.add("ingredients", ings);
			JsonObject result = new JsonObject();
			result.addProperty("item", out.toString());
			result.addProperty("count", count);
			root.add("result", result);
		} else if (e.kind == SMELTING || e.kind == BLASTING || e.kind == SMOKING) {
			if (!validIngredient(e.slots[0])) {
				return null;
			}
			root.addProperty("type", e.kind == SMELTING ? "minecraft:smelting" : e.kind == BLASTING ? "minecraft:blasting" : "minecraft:smoking");
			root.add("ingredient", ingredientJson(e.slots[0]));
			root.addProperty("result", out.toString());
			root.addProperty("experience", Math.max(0f, e.xp));
			root.addProperty("cookingtime", Math.max(1, Math.min(72000, e.time)));
		} else {
			return null;
		}
		return root;
	}

	// ---------- пакеты ----------
	private static boolean allowed(ServerPlayerEntity player) {
		return CommandUtil.canUse(player.getCommandSource(), "dserver");
	}

	public static void handleRequest(MinecraftServer server, ServerPlayerEntity player) {
		server.execute(() -> {
			if (allowed(player)) {
				sendList(server, player);
			}
		});
	}

	public static void handleSave(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf) {
		Entry entry = Entry.read(buf);
		server.execute(() -> {
			if (!allowed(player)) {
				return;
			}
			Identifier id = Identifier.tryParse(entry.id == null ? "" : entry.id.trim().toLowerCase(java.util.Locale.ROOT));
			if (id == null || !id.getPath().matches("[a-z0-9_./-]+")) {
				player.sendMessage(net.minecraft.text.Text.literal("§cКрафт не сохранён: неверный id."), false);
				return;
			}
			JsonObject json = toJson(entry);
			if (json == null) {
				player.sendMessage(net.minecraft.text.Text.literal("§cКрафт не сохранён: нужен результат и хотя бы один ингредиент."), false);
				return;
			}
			synchronized (RecipeEdits.class) {
				EDITS.put(id, json);
			}
			persist();
			reload(server, player, "§aКрафт сохранён: " + id);
		});
	}

	public static void handleDelete(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf) {
		String raw = buf.readString(128);
		server.execute(() -> {
			Identifier id = Identifier.tryParse(raw);
			if (!allowed(player) || id == null) {
				return;
			}
			synchronized (RecipeEdits.class) {
				EDITS.put(id, null);
			}
			persist();
			reload(server, player, "§eКрафт удалён: " + id);
		});
	}

	/** mode 0 — вернуть один рецепт к исходному, 1 — сбросить все правки. */
	public static void handleRestore(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf) {
		int mode = buf.readVarInt();
		String raw = buf.readString(128);
		server.execute(() -> {
			if (!allowed(player)) {
				return;
			}
			synchronized (RecipeEdits.class) {
				if (mode == 1) {
					EDITS.clear();
				} else {
					Identifier id = Identifier.tryParse(raw);
					if (id != null) {
						EDITS.remove(id);
					}
				}
			}
			persist();
			reload(server, player, mode == 1 ? "§eВсе правки крафтов сброшены." : "§aКрафт возвращён к исходному.");
		});
	}

	private static void reload(MinecraftServer server, ServerPlayerEntity player, String message) {
		try {
			server.reloadResources(new ArrayList<>(server.getDataPackManager().getEnabledNames())).whenCompleteAsync((v, err) -> {
				if (err != null) {
					AdminControlMod.LOGGER.warn("Перезагрузка рецептов не удалась: {}", err.toString());
					player.sendMessage(net.minecraft.text.Text.literal("§cПерезагрузка данных не удалась, см. лог сервера."), false);
					return;
				}
				player.sendMessage(net.minecraft.text.Text.literal(message), false);
				sendList(server, player);
			}, server);
		} catch (RuntimeException ex) {
			AdminControlMod.LOGGER.warn("Перезагрузка рецептов не удалась: {}", ex.toString());
		}
	}
}
