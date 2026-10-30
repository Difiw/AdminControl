package ru.admincontrol.emi.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.emi.EmiAccess;
import ru.admincontrol.emi.EmiConvert;
import ru.admincontrol.emi.EmiFilter;
import ru.admincontrol.emi.EmiIncompat;
import ru.admincontrol.emi.EmiRecipe;
import ru.admincontrol.emi.EmiServer;
import ru.admincontrol.item.ItemEncryption;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Клиентское состояние CW EMI: каталог разрешённых предметов, поиск, избранное, история и кэш ответов сервера.
 * Кэш содержит только то, что сервер разрешил этому игроку, и полностью сбрасывается при смене прав, сервера и выходе.
 */
public final class CwEmiClient {
	public static final class Entry {
		public final Item item;
		public final ItemStack stack;
		public final String id, path, mod;
		/** null, если название или описание скрыто: такой предмет не находится текстовым поиском. */
		public final String nameLc;
		public final int order;
		private Set<String> tags;

		Entry(Item item, int order, boolean nameHidden) {
			this.item = item;
			this.stack = new ItemStack(item);
			Identifier ident = Registries.ITEM.getId(item);
			this.id = ident.toString();
			this.path = ident.getPath();
			this.mod = ident.getNamespace();
			this.order = order;
			this.nameLc = nameHidden ? null : stack.getName().getString().toLowerCase(Locale.ROOT);
		}

		Set<String> tags() {
			if (tags == null) {
				Set<String> set = new HashSet<>();
				item.getRegistryEntry().streamTags().forEach(t -> set.add(t.id().toString().toLowerCase(Locale.ROOT)));
				tags = set;
			}
			return tags;
		}
	}

	public static final class Group {
		public final String key;
		public final ItemStack station;
		public final String typeId;
		public final List<EmiRecipe> recipes;

		Group(List<EmiRecipe> recipes) {
			EmiRecipe first = recipes.get(0);
			this.key = first.stationKey();
			this.station = first.station;
			this.typeId = first.typeId;
			this.recipes = recipes;
		}
	}

	public static final class Result {
		public static final int LOADING = -1, OK = 0, DENIED = 1, TIMEOUT = 2;
		public int status = LOADING;
		public boolean locked;
		public List<Group> groups = List.of();
		long sentAt;
	}

	public static final int CAT_ALL = 0, CAT_FAVORITES = 7, CAT_HISTORY = 8, CAT_MODS = 9, CATEGORIES = 10;
	private static final long TIMEOUT_MS = 4000;

	public static KeyBinding keyRecipes, keyUses, keyFavorite, keyToggle, keyBack;
	/** Версия данных: окна сравнивают её со своей и перезапрашивают рецепты после смены прав/рецептов. */
	public static volatile int version;
	public static String search = "";
	public static int category = CAT_ALL;
	public static int scrollRows;
	/** Выбранный мод во вкладке «Моды» (пусто — все моды кроме minecraft). */
	public static String modFilter = "";

	private static boolean serverSupported;
	private static boolean encSynced;
	private static boolean aborting;
	private static boolean catalogDirty = true;
	private static boolean lastDev, lastOp;
	private static List<Entry> all = List.of();
	private static final Map<Item, Entry> BY_ITEM = new HashMap<>();
	private static final LinkedList<String> HISTORY = new LinkedList<>();
	private static final Map<String, Result> CACHE = new HashMap<>();
	private static final Map<Integer, String> PENDING = new HashMap<>();
	private static final Map<Integer, Set<Item>> CATEGORY_ITEMS = new HashMap<>();
	private static int nextReq = 1;
	private static List<String> conflicts = List.of();

	private static Object localOwner;
	private static List<EmiRecipe> localAll = List.of();

	private static List<Entry> viewCache = List.of();
	private static String viewKey = "";

	private CwEmiClient() {
	}

	public static List<String> conflicts() {
		return conflicts;
	}

	public static boolean serverMode() {
		return serverSupported;
	}

	// ---------- инициализация ----------
	public static void init() {
		CwEmiConfig.load();
		conflicts = EmiIncompat.detect();
		if (!conflicts.isEmpty()) {
			AdminControlMod.LOGGER.error("[CW EMI] Обнаружены несовместимые просмотрщики рецептов: {}. Вход в мир будет заблокирован.", conflicts);
		}
		keyRecipes = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cw_emi.recipes", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.cw_emi"));
		keyUses = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cw_emi.uses", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_U, "key.categories.cw_emi"));
		keyFavorite = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cw_emi.favorite", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_A, "key.categories.cw_emi"));
		keyToggle = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cw_emi.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, "key.categories.cw_emi"));
		keyBack = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cw_emi.back", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_BACKSPACE, "key.categories.cw_emi"));

		ClientPlayNetworking.registerGlobalReceiver(EmiServer.HELLO, (client, handler, buf, sender) -> {
			int protocol = buf.readVarInt();
			long nonce = buf.readLong();
			PacketByteBuf ack = PacketByteBufs.create();
			ack.writeLong(nonce);
			ack.writeVarInt(EmiServer.PROTOCOL);
			List<String> found = conflicts;
			ack.writeVarInt(Math.min(8, found.size()));
			for (int i = 0; i < Math.min(8, found.size()); i++) {
				ack.writeString(found.get(i), 96);
			}
			sender.sendPacket(EmiServer.ACK, ack);
			client.execute(() -> {
				if (!found.isEmpty()) {
					abort(client);
					return;
				}
				serverSupported = protocol == EmiServer.PROTOCOL;
				if (!serverSupported && client.player != null) {
					client.player.sendMessage(Text.translatable("cw_emi.protocol_mismatch"), false);
				}
				clearData();
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(EmiServer.RESULT, (client, handler, buf, sender) -> {
			int req = buf.readVarInt();
			buf.readUnsignedByte();
			buf.readString(128);
			int status = buf.readUnsignedByte();
			boolean locked = buf.readBoolean();
			int groups = Math.min(64, buf.readVarInt());
			List<List<EmiRecipe>> parsed = new ArrayList<>();
			for (int g = 0; g < groups; g++) {
				int n = Math.min(EmiFilter.MAX_PER_GROUP, buf.readVarInt());
				List<EmiRecipe> list = new ArrayList<>(n);
				for (int i = 0; i < n; i++) {
					list.add(EmiRecipe.read(buf));
				}
				if (!list.isEmpty()) {
					parsed.add(list);
				}
			}
			client.execute(() -> {
				String key = PENDING.remove(req);
				if (key == null) {
					return; // устаревший ответ (права изменились или сервер сменился)
				}
				Result r = new Result();
				r.status = status == EmiServer.STATUS_OK ? Result.OK : Result.DENIED;
				r.locked = locked;
				List<Group> out = new ArrayList<>();
				for (List<EmiRecipe> list : parsed) {
					out.add(new Group(list));
				}
				r.groups = out;
				CACHE.put(key, r);
				version++;
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(EmiServer.INVALIDATE, (client, handler, buf, sender) -> client.execute(() -> {
			clearData();
		}));

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
			serverSupported = false;
			encSynced = false;
			aborting = false;
			clearData();
			if (!conflicts.isEmpty() && client.isInSingleplayer()) {
				abort(client);
			}
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			serverSupported = false;
			encSynced = false;
			HISTORY.clear();
			clearData();
			all = List.of();
			BY_ITEM.clear();
			catalogDirty = true;
		}));
		CwEmiOverlay.register();
	}

	/** Единый безопасный выход при конфликте: обычное «Сохранить и выйти», затем экран с объяснением. */
	static void abort(MinecraftClient client) {
		if (aborting) {
			return;
		}
		aborting = true;
		List<String> names = conflicts;
		client.execute(() -> {
			boolean sp = client.isInSingleplayer();
			if (client.world != null) {
				client.world.disconnect();
			}
			if (sp) {
				client.disconnect(new MessageScreen(Text.translatable("menu.savingLevel")));
			} else {
				client.disconnect();
			}
			client.setScreen(new CwEmiConflictScreen(new TitleScreen(), names));
		});
	}

	// ---------- сброс/обновление ----------
	private static void clearData() {
		CACHE.clear();
		PENDING.clear();
		CATEGORY_ITEMS.clear();
		viewKey = "";
		localOwner = null;
		version++;
	}

	/** Вызывается при каждой синхронизации шифрования (enc_sync): права изменились, скрытое надо немедленно убрать отовсюду. */
	public static void onAccessChanged() {
		encSynced = true;
		catalogDirty = true;
		clearData();
		catalog();
		HISTORY.removeIf(id -> !BY_ITEM.containsKey(itemOf(id)));
		CwEmiConfig cfg = CwEmiConfig.get();
		if (cfg.favorites.removeIf(id -> !BY_ITEM.containsKey(itemOf(id)))) {
			CwEmiConfig.save();
		}
	}

	private static Item itemOf(String id) {
		Identifier ident = Identifier.tryParse(id);
		return ident != null && Registries.ITEM.containsId(ident) ? Registries.ITEM.get(ident) : null;
	}

	// ---------- каталог ----------
	/** Пока сервер с CW EMI не прислал шифрование, каталог пуст: скрытые предметы не должны мелькнуть. */
	public static boolean ready() {
		return !serverSupported || encSynced;
	}

	private static boolean dev() {
		return ItemEncryption.viewBypass();
	}

	private static boolean op() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.player != null && mc.player.hasPermissionLevel(2);
	}

	public static synchronized List<Entry> catalog() {
		boolean dev = dev(), op = op();
		if (catalogDirty || dev != lastDev || op != lastOp) {
			List<Entry> list = new ArrayList<>();
			BY_ITEM.clear();
			int order = 0;
			for (Item item : Registries.ITEM) {
				order++;
				if (!EmiAccess.listed(item, dev, op) || ItemEncryption.iconHidden(item)) {
					continue; // скрытая иконка: нет ни в сетке, ни в индексе поиска
				}
				Entry e = new Entry(item, order, ItemEncryption.nameOrDescHiddenClient(item));
				list.add(e);
				BY_ITEM.put(item, e);
			}
			all = list;
			lastDev = dev;
			lastOp = op;
			catalogDirty = false;
			viewKey = "";
			CATEGORY_ITEMS.clear();
		}
		return all;
	}

	public static boolean canOpen(Item item) {
		catalog();
		return item != null && BY_ITEM.containsKey(item);
	}

	public static Entry entry(Item item) {
		catalog();
		return BY_ITEM.get(item);
	}

	private static Set<Item> categoryItems(int cat) {
		return CATEGORY_ITEMS.computeIfAbsent(cat, c -> {
			Set<Item> set = new HashSet<>();
			for (ItemStack s : ru.admincontrol.util.CreativeCatalog.items(c)) {
				set.add(s.getItem());
			}
			return set;
		});
	}

	private static boolean matches(Entry e, String query) {
		if (query.isEmpty()) {
			return true;
		}
		if (e.nameLc == null) {
			return false; // скрытое название/описание: никаких совпадений по тексту, ID и тегам
		}
		for (String token : query.split("\\s+")) {
			if (token.isEmpty()) {
				continue;
			}
			boolean ok;
			if (token.startsWith("@")) {
				ok = e.mod.contains(token.substring(1));
			} else if (token.startsWith("#")) {
				String t = token.substring(1);
				ok = e.tags().stream().anyMatch(x -> x.contains(t));
			} else {
				ok = e.nameLc.contains(token) || e.path.contains(token);
			}
			if (!ok) {
				return false;
			}
		}
		return true;
	}

	/** Отфильтрованный и отсортированный список для сетки; результат кэшируется до смены параметров. */
	public static List<Entry> view(String query, int cat) {
		List<Entry> base = catalog();
		CwEmiConfig cfg = CwEmiConfig.get();
		String q = query.trim().toLowerCase(Locale.ROOT);
		String key = q + "|" + cat + "|" + modFilter + "|" + cfg.sort + "|" + version + "|" + HISTORY.size() + "|" + cfg.favorites.size();
		if (key.equals(viewKey)) {
			return viewCache;
		}
		List<Entry> out = new ArrayList<>();
		if (cat == CAT_FAVORITES) {
			for (String id : cfg.favorites) {
				Entry e = BY_ITEM.get(itemOf(id));
				if (e != null && matches(e, q)) {
					out.add(e);
				}
			}
		} else if (cat == CAT_HISTORY) {
			for (String id : HISTORY) {
				Entry e = BY_ITEM.get(itemOf(id));
				if (e != null && matches(e, q)) {
					out.add(e);
				}
			}
		} else if (cat == CAT_MODS) {
			for (Entry e : base) {
				if (!e.mod.equals("minecraft") && (modFilter.isEmpty() || e.mod.equals(modFilter)) && matches(e, q)) {
					out.add(e);
				}
			}
			switch (cfg.sort) {
				case 1 -> out.sort(Comparator.comparing((Entry e) -> e.id));
				case 2 -> out.sort(Comparator.comparing((Entry e) -> e.mod).thenComparing(e -> e.path));
				case 0 -> out.sort(Comparator.comparing((Entry e) -> e.nameLc == null ? "￿" + e.order : e.nameLc));
				default -> out.sort(Comparator.comparing((Entry e) -> e.mod).thenComparingInt(e -> e.order));
			}
		} else {
			Set<Item> inCat = cat == CAT_ALL ? null : categoryItems(cat);
			for (Entry e : base) {
				if ((inCat == null || inCat.contains(e.item)) && matches(e, q)) {
					out.add(e);
				}
			}
			Comparator<Entry> cmp = switch (cfg.sort) {
				case 1 -> Comparator.comparing((Entry e) -> e.id);
				case 2 -> Comparator.comparing((Entry e) -> e.mod).thenComparing(e -> e.path);
				case 3 -> Comparator.comparingInt((Entry e) -> e.order);
				default -> Comparator.comparing((Entry e) -> e.nameLc == null ? "￿" + e.order : e.nameLc);
			};
			out.sort(cmp);
		}
		viewCache = out;
		viewKey = key;
		return out;
	}

	/** Установленные моды, чьи предметы есть в каталоге (кроме minecraft). */
	public static List<String> modList() {
		java.util.TreeSet<String> mods = new java.util.TreeSet<>();
		for (Entry e : catalog()) {
			if (!e.mod.equals("minecraft")) {
				mods.add(e.mod);
			}
		}
		return new ArrayList<>(mods);
	}

	/** Переключение фильтра мода: пусто (все) → первый → ... → последний → пусто. */
	public static void cycleMod(int dir) {
		List<String> mods = modList();
		int i = modFilter.isEmpty() ? -1 : mods.indexOf(modFilter);
		i += dir;
		if (i < -1) {
			i = mods.size() - 1;
		}
		modFilter = i < 0 || i >= mods.size() ? "" : mods.get(i);
		scrollRows = 0;
		viewKey = "";
	}

	public static String modName(String id) {
		return id.isEmpty() ? "" : net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(id).map(m -> m.getMetadata().getName()).orElse(id);
	}

	// ---------- избранное и история ----------
	public static boolean isFavorite(Item item) {
		return CwEmiConfig.get().favorites.contains(Registries.ITEM.getId(item).toString());
	}

	public static void toggleFavorite(Item item) {
		if (!canOpen(item)) {
			return;
		}
		String id = Registries.ITEM.getId(item).toString();
		List<String> fav = CwEmiConfig.get().favorites;
		if (!fav.remove(id)) {
			fav.add(id);
		}
		CwEmiConfig.save();
		viewKey = "";
	}

	public static void pushHistory(Item item) {
		if (!CwEmiConfig.get().historyEnabled || !canOpen(item)) {
			return;
		}
		String id = Registries.ITEM.getId(item).toString();
		HISTORY.remove(id);
		HISTORY.addFirst(id);
		while (HISTORY.size() > 60) {
			HISTORY.removeLast();
		}
		viewKey = "";
	}

	public static void clearHistory() {
		HISTORY.clear();
		viewKey = "";
	}

	// ---------- рецепты ----------
	/** Результат запроса; при отсутствии в кэше отправляет запрос серверу (или считает локально). Никогда не возвращает null. */
	public static Result result(Item item, boolean obtain) {
		String key = Registries.ITEM.getId(item) + "|" + (obtain ? 0 : 1);
		Result cached = CACHE.get(key);
		if (cached != null) {
			return cached;
		}
		if (!canOpen(item)) {
			Result denied = new Result();
			denied.status = Result.DENIED;
			return denied;
		}
		if (serverSupported) {
			Result loading = new Result();
			loading.sentAt = System.currentTimeMillis();
			CACHE.put(key, loading);
			int req = nextReq++;
			PENDING.put(req, key);
			PacketByteBuf buf = PacketByteBufs.create();
			buf.writeVarInt(req);
			buf.writeByte(obtain ? EmiServer.MODE_OBTAIN : EmiServer.MODE_USES);
			buf.writeString(Registries.ITEM.getId(item).toString(), 128);
			ClientPlayNetworking.send(EmiServer.QUERY, buf);
			return loading;
		}
		Result local = localResult(item, obtain);
		CACHE.put(key, local);
		return local;
	}

	/** Вызывается окнами каждый кадр: зависший запрос превращается в понятную ошибку, а не в вечную загрузку. */
	public static void expire(Item item, boolean obtain) {
		String key = Registries.ITEM.getId(item) + "|" + (obtain ? 0 : 1);
		Result r = CACHE.get(key);
		if (r != null && r.status == Result.LOADING && System.currentTimeMillis() - r.sentAt > TIMEOUT_MS) {
			r.status = Result.TIMEOUT;
			PENDING.values().remove(key);
		}
	}

	/** Режим без серверной поддержки: рецепты из данных клиента (они уже отфильтрованы сервером, если он есть). */
	private static Result localResult(Item item, boolean obtain) {
		Result r = new Result();
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) {
			r.status = Result.TIMEOUT;
			return r;
		}
		RecipeManager manager = mc.world.getRecipeManager();
		if (manager != localOwner) {
			localAll = List.copyOf(EmiConvert.convertAll(manager, mc.world.getRegistryManager()));
			localOwner = manager;
		}
		boolean dev = dev(), op = op();
		EmiFilter.Out out = EmiFilter.build(localAll, item, obtain, i -> ItemEncryption.iconHidden(i)
				|| ItemEncryption.recipeBlocked(i) || !EmiAccess.listed(i, dev, op));
		List<Group> groups = new ArrayList<>();
		for (List<EmiRecipe> list : out.groups.values()) {
			groups.add(new Group(list));
		}
		if (obtain && !out.groups.containsKey("source")) {
			List<EmiRecipe> src = new ArrayList<>();
			for (EmiRecipe e : ru.admincontrol.emi.EmiMechanics.local(item, !out.groups.isEmpty())) {
				EmiRecipe safe = EmiFilter.sanitize(e, i -> ItemEncryption.iconHidden(i) || !EmiAccess.listed(i, dev, op));
				if (safe != null) {
					src.add(safe);
				}
			}
			if (!src.isEmpty()) {
				groups.add(new Group(src));
			}
		}
		r.status = Result.OK;
		r.locked = out.locked;
		r.groups = groups;
		return r;
	}

	/** Для проверки в тестах и диагностике. */
	public static List<String> historyIds() {
		return Collections.unmodifiableList(new ArrayList<>(HISTORY));
	}
}
