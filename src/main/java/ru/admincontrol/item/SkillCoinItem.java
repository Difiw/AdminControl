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
import ru.admincontrol.skill.SkillTree;
import ru.admincontrol.util.AuthService;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.SkillTreeService;

import java.util.List;

/** Физическая монета валюты дерева навыков: ПКМ — 1 коин в дерево, Shift+ПКМ — вся стопка в руке. */
public class SkillCoinItem extends Item {
	private final SkillTree.Currency currency;

	public SkillCoinItem(SkillTree.Currency currency, Settings settings) {
		super(settings);
		this.currency = currency;
	}

	public SkillTree.Currency currency() {
		return this.currency;
	}

	@Override
	public Text getName(ItemStack stack) {
		return Text.translatable(this.getTranslationKey(stack)).styled(s -> s.withColor(this.currency.color()));
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
		int want = player.isSneaking() ? stack.getCount() : 1;
		int taken = SkillTreeService.redeemCoins(player, this.currency, want);
		if (taken <= 0) {
			player.sendMessage(Chat.red("Лимит " + SkillTreeService.MAX_COINS + " " + this.currency.coin() + " — монеты не приняты."), true);
			return TypedActionResult.fail(stack);
		}
		stack.decrement(taken);
		world.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.6f, 1.1f);
		player.sendMessage(Chat.gold("+" + taken + " " + this.currency.coin() + "  •  теперь: "
				+ SkillTreeService.coins(player, this.currency)), true);
		return TypedActionResult.success(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.literal("Валюта ветки дерева навыков «" + this.currency.title() + "»")
				.styled(s -> s.withColor(this.currency.color())));
		tooltip.add(Text.literal("ПКМ — обменять 1 монету на 1 коин").formatted(Formatting.GRAY));
		tooltip.add(Text.literal("Shift + ПКМ — обменять все монеты в руке").formatted(Formatting.GRAY));
	}
}
