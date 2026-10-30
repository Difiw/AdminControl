package ru.admincontrol.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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

/** Протеиновый коктейль: бонус WC с тренировок; в крафте с батончиком удлиняет баф силы. */
public class ProteinShakeItem extends Item {
	private final String kind;
	private final double trainBonus;
	private final int seconds;

	public ProteinShakeItem(String kind, double trainBonus, int seconds, Settings settings) {
		super(settings);
		this.kind = kind;
		this.trainBonus = trainBonus;
		this.seconds = seconds;
	}

	public String kind() {
		return this.kind;
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return UseAction.DRINK;
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
			Chat.send(player, Chat.red("Протеиновые коктейли отключены на сервере."));
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
		ProteinService.applyTrainPay(player, this.trainBonus, this.seconds, this.kind);
		world.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 0.8f, 1.05f);
		if (!player.getAbilities().creativeMode) {
			stack.decrement(1);
			if (stack.isEmpty()) {
				return new ItemStack(Items.GLASS_BOTTLE);
			}
			player.getInventory().offerOrDrop(new ItemStack(Items.GLASS_BOTTLE));
		}
		return stack;
	}

	@Override
	public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.literal(this.kind).formatted(Formatting.GRAY));
		tooltip.add(Text.literal("+" + (int) Math.round(this.trainBonus * 100)
				+ "% WC с тренировок на " + (this.seconds / 60) + " мин").formatted(Formatting.AQUA));
		tooltip.add(Text.literal("В крафте с батончиком → батончик на 3 мин").formatted(Formatting.DARK_GRAY));
	}
}
