package ru.admincontrol.util;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;

import java.util.ArrayList;
import java.util.List;

public final class StackIo {
	private StackIo() {
	}

	public static String write(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "";
		}
		return stack.writeNbt(new NbtCompound()).asString();
	}

	public static ItemStack read(String raw) {
		if (raw == null || raw.isBlank()) {
			return ItemStack.EMPTY;
		}
		try {
			return ItemStack.fromNbt(StringNbtReader.parse(raw));
		} catch (Exception ignored) {
			return ItemStack.EMPTY;
		}
	}

	public static List<String> writeInv(Inventory inv, int size) {
		List<String> out = new ArrayList<>();
		int n = Math.max(0, size);
		for (int i = 0; i < n; i++) {
			out.add(inv == null || i >= inv.size() ? "" : write(inv.getStack(i)));
		}
		return out;
	}

	public static void fill(Inventory inv, List<String> stored, int size) {
		if (inv == null) {
			return;
		}
		for (int i = 0; i < Math.min(size, inv.size()); i++) {
			inv.setStack(i, stored != null && i < stored.size() ? read(stored.get(i)) : ItemStack.EMPTY);
		}
	}

	public static void drop(net.minecraft.server.network.ServerPlayerEntity player, List<String> stored) {
		if (player == null || stored == null) {
			return;
		}
		for (String raw : stored) {
			ItemStack stack = read(raw);
			if (!stack.isEmpty()) {
				player.dropItem(stack, true, false);
			}
		}
	}
}
