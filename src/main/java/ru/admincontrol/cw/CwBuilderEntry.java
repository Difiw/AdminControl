package ru.admincontrol.cw;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.util.Chat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Куда попадёт игрок при следующем входе на стройку. Выбор предлагается при выходе со стройки:
 * место появления игрока в сюжете, место первого появления в строительном мире или место последнего выхода.
 */
public final class CwBuilderEntry {
	public static final String STORY = "STORY", FIRST = "FIRST", LAST = "LAST";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Map<String, Pref> PREFS = new LinkedHashMap<>();
	private static boolean loaded;

	public static final class Pref {
		public String mode = FIRST;
		public boolean hasLast;
		public double x, y, z;
		public float yaw, pitch;
	}

	private CwBuilderEntry() {}

	/** Запоминает, где игрок стоял на стройке перед выходом. Вызывается до восстановления основного профиля. */
	public static synchronized void rememberLast(ServerPlayerEntity player) {
		load();
		Pref pref = PREFS.computeIfAbsent(player.getUuidAsString(), k -> new Pref());
		pref.hasLast = true;
		pref.x = player.getX(); pref.y = player.getY(); pref.z = player.getZ();
		pref.yaw = player.getYaw(); pref.pitch = player.getPitch();
		save();
	}

	/** Точка входа {x, y, z, yaw, pitch} согласно сохранённому выбору. */
	public static synchronized double[] resolve(ServerPlayerEntity player, ServerWorld builder) {
		load();
		double[] first = {0.5, 65.0, 0.5, 0, 0};
		Pref pref = PREFS.get(player.getUuidAsString());
		if (pref == null) return first;
		if (LAST.equals(pref.mode) && pref.hasLast) return new double[] {pref.x, pref.y, pref.z, pref.yaw, pref.pitch};
		if (STORY.equals(pref.mode)) {
			CwPointRegistry.Point newest = null;
			for (CwPointRegistry.Point point : CwPointRegistry.all().values()) {
				if (point != null && "start".equals(point.name) && CwBuilderWorld.KEY.getValue().toString().equals(point.dimension)
						&& (newest == null || point.updatedAt > newest.updatedAt)) newest = point;
			}
			if (newest != null) return new double[] {newest.blockX + 0.5, newest.blockY + 1, newest.blockZ + 0.5, 0, 0};
		}
		return first;
	}

	private static synchronized void choose(ServerPlayerEntity player, String mode) {
		load();
		PREFS.computeIfAbsent(player.getUuidAsString(), k -> new Pref()).mode = mode;
		save();
	}

	/** A full authoring-map reset invalidates every remembered coordinate; all builders return above the sole bedrock. */
	public static synchronized void resetAllToFirst() {
		load();
		for (Pref pref : PREFS.values()) { pref.mode = FIRST; pref.hasLast = false; pref.x = .5; pref.y = 65; pref.z = .5; pref.yaw = 0; pref.pitch = 0; }
		save();
	}

	private static synchronized String current(ServerPlayerEntity player) {
		load();
		Pref pref = PREFS.get(player.getUuidAsString());
		return pref == null ? FIRST : pref.mode;
	}

	/** Открывает окно выбора на следующем тике (после закрытия прежнего интерфейса и переноса игрока). */
	public static void openMenu(ServerPlayerEntity player) {
		if (player == null || player.getServer() == null) return;
		player.getServer().execute(() -> {
			if (player.isRemoved() || player.networkHandler == null) return;
			String cur = current(player);
			SimpleInventory inv = new SimpleInventory(9);
			inv.setStack(2, button(Items.COMPASS, "Место появления игрока в сюжете", "Точка «start» в строительном мире.", STORY.equals(cur)));
			inv.setStack(4, button(Items.GRASS_BLOCK, "Место первого появления в этом мире", "Платформа 0 65 0.", FIRST.equals(cur)));
			inv.setStack(6, button(Items.ENDER_PEARL, "Место последнего выхода", "Там, где вы стояли при выходе.", LAST.equals(cur)));
			player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, pinv, p) -> new Menu(id, pinv, inv),
					Text.literal("Куда вернуться на стройку в следующий раз?")));
		});
	}

	private static ItemStack button(Item item, String name, String lore, boolean selected) {
		ItemStack stack = new ItemStack(item);
		stack.setCustomName(Text.literal((selected ? "✔ " : "") + name).styled(s -> s.withItalic(false).withColor(selected ? Formatting.GREEN : Formatting.WHITE)));
		NbtList list = new NbtList();
		list.add(NbtString.of(Text.Serializer.toJson(Text.literal(lore).styled(s -> s.withItalic(false).withColor(Formatting.GRAY)))));
		if (selected) list.add(NbtString.of(Text.Serializer.toJson(Text.literal("Сейчас выбрано").styled(s -> s.withItalic(false).withColor(Formatting.GREEN)))));
		stack.getOrCreateSubNbt("display").put("Lore", list);
		return stack;
	}

	private static final class Menu extends GenericContainerScreenHandler {
		Menu(int syncId, PlayerInventory playerInventory, Inventory inventory) {
			super(ScreenHandlerType.GENERIC_9X1, syncId, playerInventory, inventory, 1);
		}

		@Override
		public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
			if (!(player instanceof ServerPlayerEntity sp) || slotIndex < 0 || slotIndex >= 9) return;
			String mode = slotIndex == 2 ? STORY : slotIndex == 4 ? FIRST : slotIndex == 6 ? LAST : null;
			if (mode == null) return;
			choose(sp, mode);
			sp.closeHandledScreen();
			Chat.send(sp, Chat.green("Следующий вход на стройку: " + (STORY.equals(mode) ? "место появления игрока в сюжете"
					: FIRST.equals(mode) ? "место первого появления в этом мире" : "место последнего выхода") + "."));
		}

		@Override
		public ItemStack quickMove(PlayerEntity player, int slot) {
			return ItemStack.EMPTY;
		}
	}

	private static Path file() {
		return DataManager.get() == null ? null : DataManager.get().getConfigDir().resolve("cw-builder-entry.json");
	}

	private static synchronized void load() {
		if (loaded) return;
		Path file = file();
		if (file == null) return;
		loaded = true;
		if (!Files.isRegularFile(file)) return;
		try {
			Map<String, Pref> read = GSON.fromJson(CwDurable.text(file),
					new com.google.gson.reflect.TypeToken<Map<String, Pref>>() {}.getType());
			if (read != null) PREFS.putAll(read);
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("Не удалось прочитать точки входа на стройку", e);
		}
	}

	private static synchronized void save() {
		Path file = file();
		if (file == null) return;
		if (!CwDurable.write(file, GSON.toJson(PREFS))) AdminControlMod.LOGGER.error("Не удалось сохранить точки входа на стройку");
	}
}
