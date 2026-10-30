package ru.admincontrol.emi;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Одна «карточка» рецепта CW EMI: только то, что нужно для отрисовки, без идентификаторов рецептов.
 * Хранится в сети и на клиенте уже ПОСЛЕ серверной фильтрации: закрытые варианты ингредиентов в ней отсутствуют.
 */
public final class EmiRecipe {
	public static final int GRID = 0, SHAPELESS = 1, COOKING = 2, STONECUT = 3, SMITHING = 4, ANVIL = 5, GENERIC = 6, SOURCE = 7;
	public static final int MAX_SLOTS = 25, MAX_ALTS = 16, MAX_OUTPUTS = 4;

	public int kind;
	public int width = 3, height = 3;
	/** Слоты ингредиентов; пустой список = пустая клетка; несколько стеков = варианты по тегу. */
	public List<List<ItemStack>> inputs = new ArrayList<>();
	public List<ItemStack> outputs = new ArrayList<>();
	public int time;
	public float xp;
	/** Короткая подпись (категория рунного верстака, объём ремонта наковальни); не раскрывает скрытого. */
	public String note = "";
	/** Станция, если известна как предмет. Пустой стек + typeId = нестандартный тип, вид которого мы честно не знаем. */
	public ItemStack station = ItemStack.EMPTY;
	public String typeId = "";

	public String stationKey() {
		if (kind == SOURCE) {
			return "source";
		}
		return station.isEmpty() ? "type:" + typeId : net.minecraft.registry.Registries.ITEM.getId(station.getItem()).toString();
	}

	public boolean outputsContain(Item item) {
		for (ItemStack s : outputs) {
			if (s.getItem() == item) {
				return true;
			}
		}
		return false;
	}

	public boolean inputsContain(Item item) {
		for (List<ItemStack> slot : inputs) {
			for (ItemStack s : slot) {
				if (s.getItem() == item) {
					return true;
				}
			}
		}
		return false;
	}

	public EmiRecipe copyShell() {
		EmiRecipe r = new EmiRecipe();
		r.kind = kind;
		r.width = width;
		r.height = height;
		r.time = time;
		r.xp = xp;
		r.note = note;
		r.station = station;
		r.typeId = typeId;
		return r;
	}

	public void write(PacketByteBuf buf) {
		buf.writeVarInt(kind);
		buf.writeVarInt(width);
		buf.writeVarInt(height);
		buf.writeVarInt(inputs.size());
		for (List<ItemStack> slot : inputs) {
			buf.writeVarInt(slot.size());
			for (ItemStack s : slot) {
				buf.writeItemStack(s);
			}
		}
		buf.writeVarInt(outputs.size());
		for (ItemStack s : outputs) {
			buf.writeItemStack(s);
		}
		buf.writeVarInt(time);
		buf.writeFloat(xp);
		buf.writeString(note, 96);
		buf.writeItemStack(station);
		buf.writeString(typeId, 96);
	}

	public static EmiRecipe read(PacketByteBuf buf) {
		EmiRecipe r = new EmiRecipe();
		r.kind = buf.readVarInt();
		r.width = Math.max(1, Math.min(5, buf.readVarInt()));
		r.height = Math.max(1, Math.min(5, buf.readVarInt()));
		int n = Math.min(MAX_SLOTS, buf.readVarInt());
		for (int i = 0; i < n; i++) {
			int alts = Math.min(MAX_ALTS, buf.readVarInt());
			List<ItemStack> slot = new ArrayList<>(alts);
			for (int j = 0; j < alts; j++) {
				slot.add(buf.readItemStack());
			}
			r.inputs.add(slot);
		}
		int o = Math.min(MAX_OUTPUTS, buf.readVarInt());
		for (int i = 0; i < o; i++) {
			r.outputs.add(buf.readItemStack());
		}
		r.time = buf.readVarInt();
		r.xp = buf.readFloat();
		r.note = buf.readString(96);
		r.station = buf.readItemStack();
		r.typeId = buf.readString(96);
		return r;
	}
}
