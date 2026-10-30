package ru.admincontrol.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.util.AuthService;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.Economy;
import ru.admincontrol.util.Money;

import java.util.List;

/** Физическая монета World coin: ПКМ — 1 монета в баланс, Shift+ПКМ — вся стопка в руке. */
public class WorldCoinItem extends Item {
	public WorldCoinItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient) {
			return TypedActionResult.success(stack);
		}
		if (!(user instanceof ServerPlayerEntity player) || stack.isEmpty() || AuthService.pending(player)
				|| DataManager.get() == null) {
			return TypedActionResult.fail(stack);
		}
		int amount = player.isSneaking() ? stack.getCount() : 1;
		stack.decrement(amount);
		Economy.giveCurrency(player, amount, "обмен монет World coin");
		DataManager.get().saveIfDirty();
		world.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.6f, 1.3f);
		player.sendMessage(Chat.green("+" + Money.wc(amount) + "  •  баланс: " + Money.wc(Economy.balance(player))), true);
		return TypedActionResult.success(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.literal("ПКМ — обменять 1 монету на 1 World coin").formatted(Formatting.GRAY));
		tooltip.add(Text.literal("Shift + ПКМ — обменять все монеты в руке").formatted(Formatting.GRAY));
	}
}
