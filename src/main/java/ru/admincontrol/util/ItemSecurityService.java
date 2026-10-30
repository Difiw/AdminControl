package ru.admincontrol.util;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.DataManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-authoritative validation of stacks and enchanting outcomes. */
public final class ItemSecurityService {
	private static final int MAX_NBT_CHARS = 128_000;
	private static final int MAX_CONTAINER_DEPTH = 3;
	private static final Map<UUID, Long> LAST_SCAN = new ConcurrentHashMap<>();
	private static final Map<UUID, Deque<Long>> ENCHANTS = new ConcurrentHashMap<>();

	private ItemSecurityService() {}

	public static void tick(MinecraftServer server, int tick) {
		if (server == null || tick % 10 != 0 || DataManager.get() == null
				|| !DataManager.get().settings().anticheatEnabled) return;
		long now = System.currentTimeMillis();
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (NpcService.isNpc(player) || AuthService.pending(player)) continue;
			Long previous = LAST_SCAN.put(player.getUuid(), now);
			if (previous != null && now - previous < 350L) continue;
			for (int slot = 0; slot < player.getInventory().size(); slot++) {
				ItemStack stack = player.getInventory().getStack(slot);
				String violation = violation(stack, 0);
				if (violation != null) {
					String item = ItemLabels.of(stack) + " x" + stack.getCount();
					player.getInventory().setStack(slot, ItemStack.EMPTY);
					player.getInventory().markDirty();
					confirmed(player, item, "inventory slot " + slot, violation);
				}
			}
			for (int slot = 0; slot < player.getEnderChestInventory().size(); slot++) {
				ItemStack stack = player.getEnderChestInventory().getStack(slot);
				String violation = violation(stack, 0);
				if (violation != null) {
					String item = ItemLabels.of(stack) + " x" + stack.getCount();
					player.getEnderChestInventory().setStack(slot, ItemStack.EMPTY);
					player.getEnderChestInventory().markDirty();
					confirmed(player, item, "ender chest slot " + slot, violation);
				}
			}
		}
	}

	public static void onEnchant(ServerPlayerEntity player, ItemStack stack, int cost) {
		if (player == null || stack == null || stack.isEmpty()) return;
		if (DataManager.get() == null || !DataManager.get().settings().anticheatEnabled) return;
		String violation = violation(stack, 0);
		if (violation != null) {
			stack.setCount(0);
			AntiCheatService.confirmed(player, AntiCheatRules.ENCHANT_EXPLOIT,
					"невозможное зачарование", violation,
					List.of("Результат проверен по серверному ItemStack.", "Предмет удалён до дальнейшего использования."));
			return;
		}
		long now = System.currentTimeMillis();
		Deque<Long> history = ENCHANTS.computeIfAbsent(player.getUuid(), k -> new ArrayDeque<>());
		history.addLast(now);
		while (!history.isEmpty() && now - history.peekFirst() > 120_000L) history.removeFirst();
		// A repeated lucky sequence is evidence for review, never a proof or an automatic ban.
		if (history.size() >= 4 && highValue(stack)) {
			history.clear();
			FileScanService.suspect(player, "серия выгодных зачарований",
					"не менее 4 зачарований за 120 с; это эвристика, не доказательство");
		}
	}

	public static void forget(ServerPlayerEntity player) {
		if (player == null) return;
		LAST_SCAN.remove(player.getUuid());
		ENCHANTS.remove(player.getUuid());
	}

	private static void confirmed(ServerPlayerEntity player, String item, String location, String violation) {
		AntiCheatService.confirmed(player, AntiCheatRules.ILLEGAL_ITEM, "невозможный предмет", violation,
				List.of("Предмет: " + item, "Место: " + location,
						"Предмет удалён сервером; клиентское сообщение не использовалось."));
	}

	private static String violation(ItemStack stack, int depth) {
		if (stack == null || stack.isEmpty()) return null;
		if (stack.getCount() < 1 || stack.getCount() > stack.getMaxCount())
			return "количество " + stack.getCount() + " выше максимума " + stack.getMaxCount();
		NbtCompound nbt = stack.getNbt();
		if (nbt != null && nbt.toString().length() > MAX_NBT_CHARS) return "NBT больше " + MAX_NBT_CHARS + " символов";
		Map<Enchantment, Integer> enchantments = EnchantmentHelper.get(stack);
		var itemId = Registries.ITEM.getId(stack.getItem());
		boolean vanillaItem = itemId != null && "minecraft".equals(itemId.getNamespace());
		boolean serverKit = TrainingConfig.allowedKitEnchantments(stack);
		List<Enchantment> seen = new ArrayList<>();
		for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
			Enchantment enchantment = entry.getKey();
			int level = entry.getValue() == null ? 0 : entry.getValue();
			String id = Registries.ENCHANTMENT.getId(enchantment).toString();
			if (level < 1 || level > enchantment.getMaxLevel()) return "уровень " + id + "=" + level + ", max=" + enchantment.getMaxLevel();
			// Модовые предметы (например Point Blank) сами задают свою совместимость.
			// А необычные чары из набора МП уже разрешены самим сервером.
			if (vanillaItem && !serverKit && !(stack.getItem() instanceof EnchantedBookItem)
					&& !enchantment.isAcceptableItem(stack)) return id + " неприменимо к этому предмету";
			for (Enchantment old : seen) if (vanillaItem && !serverKit && !old.canCombine(enchantment))
				return "несовместимые чары " + Registries.ENCHANTMENT.getId(old) + " + " + id;
			seen.add(enchantment);
		}
		if (nbt != null && nbt.contains("BlockEntityTag", NbtElement.COMPOUND_TYPE)) {
			if (depth >= MAX_CONTAINER_DEPTH) return "вложенный контейнер глубже " + MAX_CONTAINER_DEPTH;
			NbtCompound block = nbt.getCompound("BlockEntityTag");
			NbtList items = block.getList("Items", NbtElement.COMPOUND_TYPE);
			if (items.size() > 54) return "во вложенном контейнере " + items.size() + " слотов";
			for (int i = 0; i < items.size(); i++) {
				String nested = violation(ItemStack.fromNbt(items.getCompound(i)), depth + 1);
				if (nested != null) return "контейнер[вложение " + i + "]: " + nested;
			}
		}
		return null;
	}

	private static boolean highValue(ItemStack stack) {
		int score = 0;
		for (Map.Entry<Enchantment, Integer> e : EnchantmentHelper.get(stack).entrySet()) {
			if (e.getValue() >= e.getKey().getMaxLevel()) score++;
		}
		return score >= 2;
	}
}
