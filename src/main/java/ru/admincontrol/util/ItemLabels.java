package ru.admincontrol.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import ru.admincontrol.item.ItemEncryption;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class ItemLabels {
	private static Map<String, String> ru;

	private ItemLabels() {
	}

	public static String of(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "воздух";
		}
		if (ItemEncryption.loggedHidden(stack.getItem())) {
			return "***";
		}
		String id = Registries.ITEM.getId(stack.getItem()).toString();
		String name = stack.hasCustomName()
				? stack.getName().getString()
				: translate(stack.getItem().getTranslationKey());
		return name + " (" + id + ")";
	}

	public static String of(Item item) {
		if (item == null) {
			return "воздух";
		}
		ItemStack stack = new ItemStack(item);
		return of(stack);
	}

	public static String of(Block block) {
		if (block == null) {
			return "блок";
		}
		if (ItemEncryption.loggedHidden(block.asItem())) {
			return "***";
		}
		String id = Registries.BLOCK.getId(block).toString();
		return translate(block.getTranslationKey()) + " (" + id + ")";
	}

	private static String translate(String key) {
		if (key == null || key.isBlank()) {
			return "предмет";
		}
		String value = ru().get(key);
		if (value != null && !value.isBlank()) {
			return value;
		}
		int dot = key.lastIndexOf('.');
		return dot >= 0 && dot + 1 < key.length() ? key.substring(dot + 1).replace('_', ' ') : key;
	}

	private static Map<String, String> ru() {
		if (ru != null) {
			return ru;
		}
		ru = new HashMap<>();
		InputStream in = openRu();
		if (in == null) {
			return ru;
		}
		try (in) {
			if (in == null) {
				return ru;
			}
			JsonElement root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			if (!root.isJsonObject()) {
				return ru;
			}
			JsonObject obj = root.getAsJsonObject();
			for (var e : obj.entrySet()) {
				if (e.getValue() != null && e.getValue().isJsonPrimitive()) {
					ru.put(e.getKey(), e.getValue().getAsString());
				}
			}
		} catch (Exception ignored) {
		}
		return ru;
	}

	private static InputStream openRu() {
		InputStream in = ItemLabels.class.getResourceAsStream("/assets/minecraft/lang/ru_ru.json");
		if (in != null) {
			return in;
		}
		return Block.class.getResourceAsStream("/assets/minecraft/lang/ru_ru.json");
	}
}
