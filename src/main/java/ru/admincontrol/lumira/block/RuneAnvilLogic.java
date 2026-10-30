package ru.admincontrol.lumira.block;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import ru.admincontrol.lumira.LumiraConfig;
import ru.admincontrol.lumira.item.NecklaceItem;

/** Одна операция рунной наковальни: предмет + 1 ремонтный материал + 1 операция топлива из запаса блока. */
public final class RuneAnvilLogic {
	public enum Result {
		OK("Отремонтировано"),
		NO_ITEM("Положите предмет Люмиры"),
		UNSUPPORTED("Наковальня обслуживает только предметы Люмиры"),
		UNDAMAGED("Предмет в ремонте не нуждается"),
		NO_FUEL("Нет топлива: положите уголь, искроуголь или жаролит"),
		NO_MATERIAL("Нужен ремонтный материал в среднем слоте");

		public final String text;

		Result(String text) {
			this.text = text;
		}
	}

	private RuneAnvilLogic() {
	}

	/** Сколько восстановит одна операция для этого предмета. */
	public static int restore(ItemStack stack) {
		LumiraConfig cfg = LumiraConfig.get();
		return stack.getItem() instanceof NecklaceItem ? cfg.necklaceRepairCharges
				: Math.max(1, stack.getMaxDamage() * cfg.anvilRepairPercent / 100);
	}

	public static Result repairOnce(ItemStack target, ItemStack material, RuneAnvilBlockEntity anvil) {
		if (target.isEmpty()) {
			return Result.NO_ITEM;
		}
		Ingredient need = RuneAnvilBlock.repairMaterial(target);
		if (need == null) {
			return Result.UNSUPPORTED;
		}
		if (!target.isDamageable() || target.getDamage() <= 0) {
			return Result.UNDAMAGED;
		}
		if (anvil.energy() <= 0) {
			return Result.NO_FUEL;
		}
		if (material.isEmpty() || !need.test(material)) {
			return Result.NO_MATERIAL;
		}
		material.decrement(1);
		anvil.addEnergy(-1);
		target.setDamage(Math.max(0, target.getDamage() - restore(target)));
		return Result.OK;
	}
}
