package ru.admincontrol.emi;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import ru.admincontrol.item.ModItems;
import ru.admincontrol.lumira.LumiraContent;

import java.util.Set;

/** Какие предметы вообще допускаются в каталог CW EMI (до учёта шифрования иконок). Общая логика клиента и сервера. */
public final class EmiAccess {
	private static final Set<Item> OPERATOR_ONLY = Set.of(Items.COMMAND_BLOCK, Items.CHAIN_COMMAND_BLOCK,
			Items.REPEATING_COMMAND_BLOCK, Items.COMMAND_BLOCK_MINECART, Items.BARRIER, Items.LIGHT, Items.STRUCTURE_BLOCK,
			Items.STRUCTURE_VOID, Items.JIGSAW, Items.DEBUG_STICK, Items.KNOWLEDGE_BOOK, Items.SPAWNER);

	private EmiAccess() {
	}

	/** dev — разработчик Admin Control (9 ур.), op — право 2+ (командные блоки и т.п.). */
	public static boolean listed(Item item, boolean dev, boolean op) {
		if (item == null || item == Items.AIR || item == LumiraContent.HIDDEN_ITEM) {
			return false;
		}
		if (!op && OPERATOR_ONLY.contains(item)) {
			return false;
		}
		return dev || !ModItems.CW_TOOLS.contains(item);
	}
}
