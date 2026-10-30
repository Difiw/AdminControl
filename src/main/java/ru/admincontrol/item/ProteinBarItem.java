package ru.admincontrol.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.util.AuthService;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.ProteinService;

import java.util.List;
import java.util.Locale;

/** Протеиновый батончик: временная прибавка к силе. Смесь с коктейлем — 3 минуты вместо 2. */
public class ProteinBarItem extends Item {
	public static final String NBT_MIXED = "ProteinMixed";

	private final String flavor;
	private final double power;

	public ProteinBarItem(String flavor, double power, Settings settings) {
		super(settings);
		this.flavor = flavor;
		this.power = power;
	}

	public String flavor() {
		return this.flavor;
	}

	public double power() {
		return this.power;
	}

	public static boolean mixed(ItemStack stack) {
		NbtCompound nbt = stack == null ? null : stack.getNbt();
		return nbt != null && nbt.getBoolean(NBT_MIXED);
	}

	public static ItemStack markMixed(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return stack;
		}
		stack.getOrCreateNbt().putBoolean(NBT_MIXED, true);
		return stack;
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return UseAction.EAT;
	}

	@Override
	public int getMaxUseTime(ItemStack stack) {
		return 32;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient) {
			user.setCurrentHand(hand);
			return TypedActionResult.consume(stack);
		}
		if (!(user instanceof ServerPlayerEntity player) || AuthService.pending(player)
				|| DataManager.get() == null) {
			return TypedActionResult.fail(stack);
		}
		if (!DataManager.get().settings().proteinEnabled) {
			Chat.send(player, Chat.red("Протеиновые батончики отключены на сервере."));
			return TypedActionResult.fail(stack);
		}
		user.setCurrentHand(hand);
		return TypedActionResult.consume(stack);
	}

	@Override
	public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
		if (world.isClient || !(user instanceof ServerPlayerEntity player)) {
			return stack;
		}
		boolean mixed = mixed(stack);
		int sec = ProteinService.barDurationSec(player, mixed);
		ProteinService.applyPower(player, this.power, sec, this.flavor);
		world.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS, 0.8f, 1.0f);
		if (!player.getAbilities().creativeMode) {
			stack.decrement(1);
		}
		return stack;
	}

	@Override
	public Text getName(ItemStack stack) {
		Text base = super.getName(stack);
		return mixed(stack) ? Text.literal(base.getString() + " + коктейль").formatted(Formatting.AQUA) : base;
	}

	@Override
	public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
		boolean mixed = mixed(stack);
		tooltip.add(Text.literal("Вкус: " + this.flavor).formatted(Formatting.GRAY));
		tooltip.add(Text.literal("Сила +" + String.format(Locale.US, "%.1f", this.power)
				+ " на " + (mixed ? "3" : "2") + " мин"
				+ (mixed ? " (смесь)" : "")).formatted(Formatting.GOLD));
		if (!mixed) {
			tooltip.add(Text.literal("Скрафтите с коктейлем → 3 минуты").formatted(Formatting.DARK_GRAY));
		}
	}
}
