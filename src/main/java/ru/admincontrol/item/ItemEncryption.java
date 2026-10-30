package ru.admincontrol.item;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.SynchronizeRecipesS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.ModSettings;
import ru.admincontrol.lumira.LumiraContent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Шифрование предметов до выхода сюжета. Три независимых механизма:
 * <ul>
 *   <li>название и описание: вместо названия переключающиеся символы, подсказка без описания и ID, в логах «***»;</li>
 *   <li>картинка: вместо иконки показывается «неизвестный предмет» (в инвентаре, в руке, на земле);</li>
 *   <li>блокировка крафта: рецепты таких предметов не отправляются клиентам, EMI/REI не покажут как они делаются.</li>
 * </ul>
 * Списки и три переключателя хранятся в настройках сервера и синхронизируются клиентам.
 */
public final class ItemEncryption {
	public static final Identifier SYNC = new Identifier(AdminControlMod.MOD_ID, "enc_sync");
	public static final Identifier ACTION = new Identifier(AdminControlMod.MOD_ID, "enc_action");
	public static final int ACT_REQUEST = 0, ACT_TOGGLE_NAMES = 1, ACT_ADD = 2, ACT_REMOVE_ALL = 3, ACT_RESET = 4,
			ACT_TOGGLE_ICONS = 5, ACT_TOGGLE_RECIPES = 6, ACT_NAME_ON = 7, ACT_NAME_OFF = 8, ACT_ICON_ON = 9, ACT_ICON_OFF = 10,
			ACT_DESC_ON = 11, ACT_DESC_OFF = 12, ACT_USE_BLOCK = 13, ACT_USE_ALLOW = 14;

	private static volatile boolean namesOn = true;
	private static volatile boolean iconsOn = true;
	private static volatile boolean recipesBlocked = true;
	/** Разработчик (9 ур.) на клиенте видит все названия, картинки и крафты расшифрованными. */
	private static volatile boolean viewBypass = false;
	private static final ThreadLocal<Boolean> RECIPE_BYPASS = ThreadLocal.withInitial(() -> false);
	private static volatile Set<Item> nameSet = Set.of();
	private static volatile Set<Item> iconSet = Set.of();
	private static volatile Set<Item> descSet = Set.of();
	private static volatile Set<Item> useBlockedSet = Set.of();
	private static volatile List<String> nameIds = List.of();
	private static volatile List<String> iconIds = List.of();
	private static volatile List<String> descIds = List.of();
	private static volatile List<String> useBlockedIds = List.of();

	private ItemEncryption() {
	}

	public static boolean enabled() {
		return namesOn;
	}

	public static boolean iconsEnabled() {
		return iconsOn;
	}

	public static boolean recipesBlocked() {
		return recipesBlocked;
	}

	public static List<String> ids() {
		return nameIds;
	}

	public static List<String> iconIds() {
		return iconIds;
	}

	public static List<String> descIds() {
		return descIds;
	}

	public static List<String> useBlockedIds() {
		return useBlockedIds;
	}

	/** Все предметы, настроенные хотя бы в одном списке. */
	public static List<String> allIds() {
		LinkedHashSet<String> all = new LinkedHashSet<>(nameIds);
		all.addAll(iconIds);
		all.addAll(descIds);
		all.addAll(useBlockedIds);
		return new ArrayList<>(all);
	}

	/** Название вкладки Люмиры шифруется, только пока шифрование включено и в списке есть её предметы. */
	public static boolean groupHidden() {
		if (!namesOn || viewBypass) {
			return false;
		}
		for (Item item : LumiraContent.ITEMS.values()) {
			if (nameSet.contains(item)) {
				return true;
			}
		}
		return false;
	}

	public static boolean hidden(Item item) {
		return namesOn && !viewBypass && item != null && nameSet.contains(item);
	}

	/** Описание (подсказка под названием) скрывается отдельным списком; переключатель тот же, что у названий. */
	public static boolean descHidden(Item item) {
		return namesOn && !viewBypass && item != null && descSet.contains(item);
	}

	/** Предмет скрыт хотя бы в одном из трёх списков (картинка, название, описание). */
	private static boolean anyListed(Item item) {
		return item != null && (nameSet.contains(item) || iconSet.contains(item) || descSet.contains(item));
	}

	/** Рецепт раскрывает предмет через результат или любой ингредиент, поэтому скрывается, если защищён хотя бы один из них. */
	public static boolean recipeBlocked(Item output, List<Item> ingredients) {
		if (!recipesBlocked || viewBypass || RECIPE_BYPASS.get()) {
			return false;
		}
		if (anyListed(output)) {
			return true;
		}
		for (Item item : ingredients) {
			if (anyListed(item)) {
				return true;
			}
		}
		return false;
	}

	/** Для логов и админ-чата: предмет скрыт, если он в любом списке, независимо от переключателей и режима разработчика. */
	public static boolean loggedHidden(Item item) {
		return anyListed(item);
	}

	public static boolean iconHidden(Item item) {
		return iconsOn && !viewBypass && item != null && iconSet.contains(item);
	}

	/** Разработчик может тесировать сюжет; для остальных запрет проверяется только сервером. */
	public static boolean useBlocked(ServerPlayerEntity player, ItemStack stack) {
		return player != null && stack != null && !stack.isEmpty() && !isDeveloper(player)
				&& useBlockedSet.contains(stack.getItem());
	}

	public static void denyUse(ServerPlayerEntity player, ItemStack stack) {
		if (player != null) player.sendMessage(Text.literal("Этот сюжетный предмет пока нельзя использовать.")
				.formatted(Formatting.RED), true);
	}

	/** Рецепт выпускается клиенту, если его результат не защищён блокировкой крафта. */
	public static boolean recipeBlocked(Item output) {
		return recipesBlocked && !viewBypass && !RECIPE_BYPASS.get() && anyListed(output);
	}

	/** Название вместо настоящего: постоянно меняющиеся символы. */
	public static Text maskedName() {
		return Text.literal("XXXXXXXXXX").formatted(Formatting.LIGHT_PURPLE, Formatting.OBFUSCATED);
	}

	public static List<String> defaults() {
		LinkedHashSet<String> list = new LinkedHashSet<>();
		for (Item item : ModItems.CW_TOOLS) {
			list.add(Registries.ITEM.getId(item).toString());
		}
		for (Item item : LumiraContent.ITEMS.values()) {
			list.add(Registries.ITEM.getId(item).toString());
		}
		return new ArrayList<>(list);
	}

	/** Любой зарегистрированный сюжетный предмет (включая деревянные) скрыт по умолчанию, исключений по материалу нет. */
	private static List<String> clean(List<String> list, Set<Item> out) {
		List<String> ids = new ArrayList<>();
		for (String id : list) {
			Identifier ident = Identifier.tryParse(id);
			if (ident != null && Registries.ITEM.containsId(ident)) {
				out.add(Registries.ITEM.get(ident));
				ids.add(ident.toString());
			}
		}
		return List.copyOf(ids);
	}

	/** Применяет состояние локально (на сервере — из настроек, на клиенте — из пакета). */
	public static void apply(boolean names, List<String> nameList, boolean icons, List<String> iconList, boolean recipes) {
		apply(names, nameList, icons, iconList, recipes, nameList);
	}

	public static void apply(boolean names, List<String> nameList, boolean icons, List<String> iconList, boolean recipes, List<String> descList) {
		apply(names, nameList, icons, iconList, recipes, descList, List.of());
	}

	public static void apply(boolean names, List<String> nameList, boolean icons, List<String> iconList, boolean recipes,
			List<String> descList, List<String> blockedUseList) {
		Set<Item> ns = new HashSet<>();
		Set<Item> is = new HashSet<>();
		Set<Item> ds = new HashSet<>();
		Set<Item> us = new HashSet<>();
		nameIds = clean(nameList, ns);
		iconIds = clean(iconList, is);
		descIds = clean(descList, ds);
		useBlockedIds = clean(blockedUseList, us);
		nameSet = ns;
		iconSet = is;
		descSet = ds;
		useBlockedSet = us;
		namesOn = names;
		iconsOn = icons;
		recipesBlocked = recipes;
	}

	public static void clearLocal() {
		apply(false, List.of(), false, List.of(), false, List.of(), List.of());
		viewBypass = false;
	}

	/** Читает настройки сервера; при первом запуске создаёт списки по умолчанию. */
	public static void reloadFromSettings() {
		DataManager data = DataManager.get();
		if (data == null) {
			return;
		}
		ModSettings s = data.settings();
		boolean changed = false;
		if (s.encryptedItems == null) {
			s.encryptedItems = defaults();
			changed = true;
		}
		if (s.iconEncryptedItems == null) {
			s.iconEncryptedItems = defaults();
			changed = true;
		}
		if (s.descEncryptedItems == null) {
			s.descEncryptedItems = new ArrayList<>(s.encryptedItems);
			changed = true;
		}
		if (s.useBlockedItems == null) {
			s.useBlockedItems = defaults();
			changed = true;
		}
		if (!s.encryptDefaultDenyMigrated) {
			// Раньше деревянные предметы были исключены из скрытия: возвращаем их в списки (старые записи не стираются).
			for (String id : defaults()) {
				if (!s.encryptedItems.contains(id) && !s.iconEncryptedItems.contains(id) && !s.descEncryptedItems.contains(id)) {
					s.encryptedItems.add(id);
					s.iconEncryptedItems.add(id);
					s.descEncryptedItems.add(id);
				}
			}
			s.encryptDefaultDenyMigrated = true;
			changed = true;
		}
		if (changed) {
			data.saveAll();
		}
		apply(s.encryptItems, s.encryptedItems, s.encryptIcons, s.iconEncryptedItems, s.blockRecipeView,
				s.descEncryptedItems, s.useBlockedItems);
	}

	public static void sendTo(ServerPlayerEntity player) {
		if (player == null || player.networkHandler == null || !ServerPlayNetworking.canSend(player, SYNC)) {
			return;
		}
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBoolean(namesOn);
		buf.writeBoolean(iconsOn);
		buf.writeBoolean(recipesBlocked);
		buf.writeVarInt(nameIds.size());
		for (String id : nameIds) {
			buf.writeString(id, 128);
		}
		buf.writeVarInt(iconIds.size());
		for (String id : iconIds) {
			buf.writeString(id, 128);
		}
		buf.writeVarInt(descIds.size());
		for (String id : descIds) {
			buf.writeString(id, 128);
		}
		buf.writeVarInt(useBlockedIds.size());
		for (String id : useBlockedIds) {
			buf.writeString(id, 128);
		}
		boolean dev = isDeveloper(player);
		AdminControlMod.LOGGER.info("[Encryption] sync -> {} dev={}", player.getGameProfile().getName(), dev);
		buf.writeBoolean(dev);
		ServerPlayNetworking.send(player, SYNC, buf);
		if (dev) {
			sendRecipesTo(player, true);
		}
	}

	public static void broadcast(MinecraftServer server) {
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			sendTo(p);
		}
	}

	/** Рецепты зависят от блокировки: после смены настроек их нужно заново отправить всем. */
	public static void resyncRecipes(MinecraftServer server) {
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			sendRecipesTo(p, isDeveloper(p));
		}
	}

	/** Разработчик (9 ур.): всё расшифровано, рецепты без блокировки. */
	public static boolean isDeveloper(ServerPlayerEntity player) {
		return player != null && CommandUtil.hasRank(player.getCommandSource(), ru.admincontrol.data.AdminRanks.DEV);
	}

	private static void sendRecipesTo(ServerPlayerEntity player, boolean full) {
		if (player.networkHandler == null) {
			return;
		}
		RECIPE_BYPASS.set(full);
		try {
			player.networkHandler.sendPacket(new SynchronizeRecipesS2CPacket(player.getServer().getRecipeManager().values()));
		} finally {
			RECIPE_BYPASS.set(false);
		}
	}

	public static void setViewBypass(boolean on) {
		viewBypass = on;
	}

	// ---------- CW EMI: решения принимаются на сервере для конкретного игрока ----------
	public static boolean viewBypass() {
		return viewBypass;
	}

	/** Иконка скрыта для этого игрока (разработчик видит всё). */
	public static boolean iconHiddenFor(ServerPlayerEntity player, Item item) {
		return iconsOn && item != null && iconSet.contains(item) && !isDeveloper(player);
	}

	/** Предмет нельзя показывать внутри рецепта: скрыта иконка либо рецепты заблокированы для скрытого предмета. */
	public static boolean recipeHiddenFor(ServerPlayerEntity player, Item item) {
		if (item == null || isDeveloper(player)) {
			return false;
		}
		return (iconsOn && iconSet.contains(item)) || (recipesBlocked && anyListed(item));
	}

	/** Отдельный список «использование запрещено» скрывает и раздел «Используется в» (консервативное решение). */
	public static boolean useViewBlockedFor(ServerPlayerEntity player, Item item) {
		return item != null && !isDeveloper(player) && useBlockedSet.contains(item);
	}

	/** Клиент: название скрыто (для поиска и сортировки CW EMI). */
	public static boolean nameOrDescHiddenClient(Item item) {
		return hidden(item) || descHidden(item);
	}

	private static void toggleIn(List<String> list, String id, boolean on) {
		list.remove(id);
		if (on) {
			list.add(id);
		}
	}

	/** Действие из окна настройки; право то же, что у /dserver. */
	public static void handle(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf) {
		int action = buf.readVarInt();
		String id = buf.isReadable() ? buf.readString(128) : "";
		server.execute(() -> {
			if (action == ACT_REQUEST) {
				sendTo(player);
				return;
			}
			if (!CommandUtil.canUse(player.getCommandSource(), "dserver") || DataManager.get() == null) {
				return;
			}
			ModSettings s = DataManager.get().settings();
			if (s.encryptedItems == null) {
				s.encryptedItems = defaults();
			}
			if (s.iconEncryptedItems == null) {
				s.iconEncryptedItems = defaults();
			}
			if (s.descEncryptedItems == null) {
				s.descEncryptedItems = new ArrayList<>(s.encryptedItems);
			}
			if (s.useBlockedItems == null) {
				s.useBlockedItems = defaults();
			}
			Identifier ident = Identifier.tryParse(id);
			boolean validItem = ident != null && Registries.ITEM.containsId(ident);
			String norm = validItem ? ident.toString() : id;
			boolean recipesTouched = false;
			switch (action) {
				case ACT_TOGGLE_NAMES -> s.encryptItems = !s.encryptItems;
				case ACT_TOGGLE_ICONS -> s.encryptIcons = !s.encryptIcons;
				case ACT_TOGGLE_RECIPES -> {
					s.blockRecipeView = !s.blockRecipeView;
					recipesTouched = true;
				}
				case ACT_ADD -> {
					if (validItem) {
						toggleIn(s.encryptedItems, norm, true);
						toggleIn(s.iconEncryptedItems, norm, true);
						toggleIn(s.descEncryptedItems, norm, true);
						toggleIn(s.useBlockedItems, norm, true);
						recipesTouched = true;
					}
				}
				case ACT_DESC_ON -> {
					if (validItem) {
						toggleIn(s.descEncryptedItems, norm, true);
						recipesTouched = true;
					}
				}
				case ACT_DESC_OFF -> {
					toggleIn(s.descEncryptedItems, norm, false);
					recipesTouched = true;
				}
				case ACT_NAME_ON -> {
					if (validItem) {
						toggleIn(s.encryptedItems, norm, true);
						recipesTouched = true;
					}
				}
				case ACT_NAME_OFF -> {
					toggleIn(s.encryptedItems, norm, false);
					recipesTouched = true;
				}
				case ACT_ICON_ON -> {
					if (validItem) {
						toggleIn(s.iconEncryptedItems, norm, true);
						recipesTouched = true;
					}
				}
				case ACT_ICON_OFF -> {
					toggleIn(s.iconEncryptedItems, norm, false);
					recipesTouched = true;
				}
				case ACT_USE_BLOCK -> {
					if (validItem) toggleIn(s.useBlockedItems, norm, true);
				}
				case ACT_USE_ALLOW -> toggleIn(s.useBlockedItems, norm, false);
				case ACT_REMOVE_ALL -> {
					toggleIn(s.encryptedItems, norm, false);
					toggleIn(s.iconEncryptedItems, norm, false);
					toggleIn(s.descEncryptedItems, norm, false);
					toggleIn(s.useBlockedItems, norm, false);
					recipesTouched = true;
				}
				case ACT_RESET -> {
					s.encryptedItems = defaults();
					s.iconEncryptedItems = defaults();
					s.descEncryptedItems = defaults();
					s.useBlockedItems = defaults();
					recipesTouched = true;
				}
				default -> {
				}
			}
			if (action != ACT_REQUEST) {
				DataManager.get().saveAll();
				reloadFromSettings();
				broadcast(server);
				if (recipesTouched) {
					resyncRecipes(server);
				}
			} else {
				sendTo(player);
			}
		});
	}
}
