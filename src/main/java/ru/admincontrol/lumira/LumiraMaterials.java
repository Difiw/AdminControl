package ru.admincontrol.lumira;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import ru.admincontrol.AdminControlMod;

import java.util.function.Supplier;

/** Материалы инструментов и брони новых металлов (значения — из каталога v2, см. LumiraIds). */
public final class LumiraMaterials {
	private LumiraMaterials() {
	}

	/** Инструменты: полный урон задаётся самим предметом, поэтому бонус материала 0. */
	public static final class Tool implements ToolMaterial {
		private final LumiraIds.Metal metal;
		private final Supplier<Ingredient> repair;

		public Tool(LumiraIds.Metal metal, Supplier<Ingredient> repair) {
			this.metal = metal;
			this.repair = repair;
		}

		public LumiraIds.Metal metal() {
			return metal;
		}

		@Override
		public int getDurability() {
			return metal.durability();
		}

		@Override
		public float getMiningSpeedMultiplier() {
			return metal.speed();
		}

		@Override
		public float getAttackDamage() {
			return 0f;
		}

		@Override
		public int getMiningLevel() {
			return metal.level();
		}

		@Override
		public int getEnchantability() {
			return metal.enchant();
		}

		@Override
		public Ingredient getRepairIngredient() {
			return repair.get();
		}
	}

	/** Броня только у сумеречного железа и звёздносеребра. */
	public static final class Armor implements ArmorMaterial {
		private final LumiraIds.Metal metal;
		private final int[] protection;
		private final int[] durability;
		private final float toughness;
		private final Supplier<Ingredient> repair;

		public Armor(LumiraIds.Metal metal, int[] protection, int[] durability, float toughness,
					 Supplier<Ingredient> repair) {
			this.metal = metal;
			this.protection = protection;
			this.durability = durability;
			this.toughness = toughness;
			this.repair = repair;
		}

		private static int slot(ArmorItem.Type type) {
			return switch (type) {
				case HELMET -> 0;
				case CHESTPLATE -> 1;
				case LEGGINGS -> 2;
				case BOOTS -> 3;
			};
		}

		@Override
		public int getDurability(ArmorItem.Type type) {
			return durability[slot(type)];
		}

		@Override
		public int getProtection(ArmorItem.Type type) {
			return protection[slot(type)];
		}

		@Override
		public int getEnchantability() {
			return metal.enchant();
		}

		@Override
		public SoundEvent getEquipSound() {
			return SoundEvents.ITEM_ARMOR_EQUIP_IRON;
		}

		@Override
		public Ingredient getRepairIngredient() {
			return repair.get();
		}

		@Override
		public String getName() {
			return AdminControlMod.MOD_ID + ":" + metal.id();
		}

		@Override
		public float getToughness() {
			return toughness;
		}

		@Override
		public float getKnockbackResistance() {
			return 0f;
		}
	}

	public static Supplier<Ingredient> ingot(String id) {
		return () -> Ingredient.ofItems(item(id));
	}

	public static Item item(String id) {
		return Registries.ITEM.get(new Identifier(AdminControlMod.MOD_ID, id));
	}
}
