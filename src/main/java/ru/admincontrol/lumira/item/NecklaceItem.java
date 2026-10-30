package ru.admincontrol.lumira.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import ru.admincontrol.lumira.LumiraConfig;
import ru.admincontrol.lumira.LumiraContent;
import ru.admincontrol.lumira.LumiraNecklaces;

import java.util.List;

/**
 * Ожерелье: один отдельный слот на игрока. Эффект действует только в слоте и только в сюжетном мире.
 * Заряды хранятся как прочность (256 максимум), при нуле эффект и недостаток отключаются вместе.
 */
public class NecklaceItem extends Item {
	private final String kind;

	public NecklaceItem(String kind, Settings settings) {
		super(settings);
		this.kind = kind;
	}

	public String kind() {
		return kind;
	}

	public Item repairItem() {
		return switch (kind) {
			case "dew" -> LumiraContent.item("dew_quartz");
			case "echo" -> LumiraContent.item("echolite");
			case "guard" -> LumiraContent.item("twilight_iron_ingot");
			default -> LumiraContent.item("heart_crystal");
		};
	}

	public static int charges(ItemStack stack) {
		return stack.isEmpty() ? 0 : stack.getMaxDamage() - stack.getDamage();
	}

	/** Дополнительный максимум здоровья, который может дать конкретный предмет (NBT MaxHealthBonus, по умолчанию 0). */
	public static int maxHealthBonus(ItemStack stack) {
		return stack.isEmpty() || !stack.hasNbt() ? 0 : Math.max(0, stack.getNbt().getInt("MaxHealthBonus"));
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient || !(user instanceof ServerPlayerEntity player)) {
			return TypedActionResult.success(stack);
		}
		return LumiraNecklaces.equip(player, hand) ? TypedActionResult.success(player.getStackInHand(hand))
				: TypedActionResult.fail(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
		LumiraConfig c = LumiraConfig.get();
		String bonus;
		String drawback;
		switch (kind) {
			case "dew" -> {
				bonus = "Вне боя через " + c.dewIdleSeconds + " с лечит " + (int) c.dewHeal + " HP раз в " + c.dewIntervalSeconds + " с";
				drawback = "−" + pct(c.dewSpeedPenalty) + "% скорости передвижения";
			}
			case "echo" -> {
				bonus = "+" + pct(c.echoDealt) + "% прямого урона по врагам";
				drawback = "+" + pct(c.echoTaken) + "% входящего урона от врагов";
			}
			case "guard" -> {
				bonus = "−" + pct(c.guardReduction) + "% прямого урона от врагов";
				drawback = "−" + pct(c.guardSpeedPenalty) + "% скорости передвижения";
			}
			default -> {
				bonus = "+" + pct(c.starDealt) + "% прямого урона по врагам";
				drawback = "+" + pct(c.starTaken) + "% входящего урона от врагов";
			}
		}
		tooltip.add(Text.literal(bonus).formatted(Formatting.GREEN));
		tooltip.add(Text.literal(drawback).formatted(Formatting.RED));
		tooltip.add(Text.literal("Заряды: " + charges(stack) + " / " + stack.getMaxDamage()).formatted(Formatting.AQUA));
		tooltip.add(Text.literal("ПКМ — надеть в слот ожерелья (только в сюжетном мире)").formatted(Formatting.GRAY));
	}

	private static int pct(double v) {
		return (int) Math.round(v * 100);
	}
}
