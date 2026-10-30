package ru.admincontrol.util;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * Сериализация инвентаря игрока в NBT-строку для players.json.
 */
public final class InventoryUtil {
	private InventoryUtil() {
	}

	public static String serialize(ServerPlayerEntity player) {
		NbtCompound root = new NbtCompound();
		NbtList items = new NbtList();
		player.getInventory().writeNbt(items);
		root.put("Inventory", items);
		root.putInt("SelectedSlot", player.getInventory().selectedSlot);
		return root.asString();
	}

	public static void snapshotPos(ServerPlayerEntity player, PlayerRecord record) {
		record.x = player.getX();
		record.y = player.getY();
		record.z = player.getZ();
		record.dimension = player.getWorld().getRegistryKey().getValue().toString();
		record.gameMode = player.interactionManager.getGameMode().getName();
		record.lastHealth = player.getHealth();
		record.lastHunger = player.getHungerManager().getFoodLevel();
	}

	public static void snapshot(ServerPlayerEntity player, PlayerRecord record) {
		String nbt = serialize(player);
		boolean invChanged = nbt != null && !nbt.equals(record.inventoryNbt);
		record.inventoryNbt = nbt;
		record.lastKnownLevel = player.experienceLevel;
		snapshotPos(player, record);
		if (invChanged && DataManager.get() != null) {
			DataManager.get().markDirty();
		}
	}

	public static net.minecraft.inventory.SimpleInventory inventoryFromNbt(String nbt) {
		net.minecraft.inventory.SimpleInventory inv = new net.minecraft.inventory.SimpleInventory(41);
		NbtCompound root = parse(nbt);
		if (!root.contains("Inventory", NbtElement.LIST_TYPE)) {
			return inv;
		}
		NbtList list = root.getList("Inventory", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			NbtCompound tag = list.getCompound(i);
			int raw = tag.getByte("Slot") & 255;
			int slot = fromNbtSlot(raw);
			if (slot >= 0 && slot < 41) {
				inv.setStack(slot, ItemStack.fromNbt(tag));
			}
		}
		return inv;
	}

	public static String inventoryToNbt(net.minecraft.inventory.SimpleInventory inv) {
		NbtCompound root = new NbtCompound();
		NbtList list = new NbtList();
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (stack.isEmpty()) {
				continue;
			}
			NbtCompound tag = new NbtCompound();
			tag.putByte("Slot", (byte) toNbtSlot(i));
			stack.writeNbt(tag);
			list.add(tag);
		}
		root.put("Inventory", list);
		return root.asString();
	}

	public static void restore(ServerPlayerEntity player, String nbt) {
		NbtCompound root = parse(nbt);
		player.getInventory().clear();
		if (root.contains("Inventory", NbtElement.LIST_TYPE)) {
			player.getInventory().readNbt(root.getList("Inventory", NbtElement.COMPOUND_TYPE));
		}
		if (root.contains("SelectedSlot")) {
			player.getInventory().selectedSlot = root.getInt("SelectedSlot");
		}
		player.getInventory().markDirty();
		if (player.playerScreenHandler != null) {
			player.playerScreenHandler.sendContentUpdates();
		}
	}

	public static void copyEnder(ServerPlayerEntity from, ServerPlayerEntity to) {
		if (from == null || to == null) {
			return;
		}
		int size = Math.min(from.getEnderChestInventory().size(), to.getEnderChestInventory().size());
		for (int i = 0; i < size; i++) {
			to.getEnderChestInventory().setStack(i, from.getEnderChestInventory().getStack(i).copy());
			from.getEnderChestInventory().setStack(i, ItemStack.EMPTY);
		}
	}

	public static void copyExperience(ServerPlayerEntity from, ServerPlayerEntity to) {
		if (from == null || to == null) {
			return;
		}
		applyExperience(to, from.experienceLevel, from.experienceProgress, from.totalExperience);
		applyExperience(from, 0, 0f, 0);
	}

	public static void dumpTo(net.minecraft.server.command.ServerCommandSource source, String nick, net.minecraft.inventory.Inventory inv) {
		source.sendFeedback(() -> Chat.gold("=== Инвентарь " + nick + " ==="), false);
		source.sendFeedback(() -> Text.literal("ACINV begin " + nick), false);
		boolean any = false;
		int size = Math.min(inv.size(), 41);
		for (int i = 0; i < size; i++) {
			ItemStack stack = inv.getStack(i);
			if (stack.isEmpty()) {
				continue;
			}
			any = true;
			String id = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).toString();
			String name = stack.getName().getString().replace('\n', ' ').replace('\r', ' ');
			String zone = zoneName(i);
			int slot = i;
			int count = stack.getCount();
			source.sendFeedback(() -> Text.literal("ACINV slot=" + slot + " zone=" + zone + " id=" + id + " count=" + count + " name=" + name), false);
			source.sendFeedback(() -> Chat.gray("  [" + slot + "] " + name + " × " + count + " (" + id + ")"), false);
		}
		if (!any) {
			source.sendFeedback(() -> Text.literal("ACINV empty"), false);
			source.sendFeedback(() -> Chat.gray("Инвентарь пуст."), false);
		}
		source.sendFeedback(() -> Text.literal("ACINV end " + nick), false);
	}

	public static String zoneName(int slot) {
		if (slot >= 0 && slot <= 8) {
			return "hotbar";
		}
		if (slot >= 9 && slot <= 35) {
			return "main";
		}
		if (slot == 36) {
			return "boots";
		}
		if (slot == 37) {
			return "leggings";
		}
		if (slot == 38) {
			return "chest";
		}
		if (slot == 39) {
			return "helmet";
		}
		if (slot == 40) {
			return "offhand";
		}
		return "other";
	}

	public static void applyExperience(ServerPlayerEntity player, int level, float progress, int total) {
		if (player == null) {
			return;
		}
		player.experienceLevel = Math.max(0, level);
		player.experienceProgress = Math.max(0f, Math.min(1f, progress));
		player.totalExperience = Math.max(0, total);
		player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.ExperienceBarUpdateS2CPacket(
				player.experienceProgress, player.totalExperience, player.experienceLevel));
	}

	public static List<Text> describe(ServerPlayerEntity player) {
		List<Text> lines = new ArrayList<>();
		for (int i = 0; i < player.getInventory().size(); i++) {
			ItemStack stack = player.getInventory().getStack(i);
			if (stack.isEmpty()) {
				continue;
			}
			String name = stack.getName().getString();
			String id = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).toString();
			lines.add(Chat.gray("  [" + i + "] " + name + " × " + stack.getCount() + " (" + id + ")"));
		}
		if (lines.isEmpty()) {
			lines.add(Chat.gray("  Инвентарь пуст."));
		}
		return lines;
	}

	private static int fromNbtSlot(int raw) {
		if (raw >= 0 && raw < 36) {
			return raw;
		}
		if (raw >= 100 && raw < 104) {
			return 36 + (raw - 100);
		}
		if (raw >= 150 && raw < 151) {
			return 40;
		}
		if (raw >= 36 && raw < 41) {
			return raw;
		}
		return -1;
	}

	private static int toNbtSlot(int invSlot) {
		if (invSlot >= 0 && invSlot < 36) {
			return invSlot;
		}
		if (invSlot >= 36 && invSlot < 40) {
			return 100 + (invSlot - 36);
		}
		if (invSlot == 40) {
			return 150;
		}
		return invSlot;
	}

	public static NbtCompound parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return new NbtCompound();
		}
		try {
			NbtElement element = StringNbtReader.parse(raw);
			return element instanceof NbtCompound compound ? compound : new NbtCompound();
		} catch (Exception e) {
			return new NbtCompound();
		}
	}
}
