package ru.admincontrol.lumira.item;

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
import ru.admincontrol.lumira.LumiraConfig;
import ru.admincontrol.lumira.LumiraEconomy;
import ru.admincontrol.util.BalanceBoard;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * «Coin лиры» / «Coin мирты». ПКМ — одна монета из удерживаемого стака, Shift+ПКМ — весь удерживаемый стак.
 * Количество определяет сервер по стеку в руке, монета расходуется только после успешного начисления.
 */
public class StoryCoinItem extends Item {
	public enum Kind {
		LYRA, MYRTLE
	}

	/** Игрок → «время сервера : рука», чтобы один жест не обработался дважды. */
	private static final Map<UUID, Long> LAST_GESTURE = new ConcurrentHashMap<>();

	private final Kind kind;

	public StoryCoinItem(Kind kind, Settings settings) {
		super(settings);
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}

	private long value() {
		LumiraConfig cfg = LumiraConfig.get();
		return Math.max(1, kind == Kind.LYRA ? cfg.coinLyraValue : cfg.coinMyrtleValue);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		if (!world.isClient && user instanceof ServerPlayerEntity player
				&& ru.admincontrol.item.ItemEncryption.useBlocked(player, user.getStackInHand(hand))) {
			ru.admincontrol.item.ItemEncryption.denyUse(player, user.getStackInHand(hand));
			return TypedActionResult.fail(user.getStackInHand(hand));
		}
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient) {
			return TypedActionResult.success(stack);
		}
		if (!(user instanceof ServerPlayerEntity player) || stack.isEmpty() || !(stack.getItem() instanceof StoryCoinItem)) {
			return TypedActionResult.fail(stack);
		}
		long now = world.getTime();
		Long last = LAST_GESTURE.put(player.getUuid(), now);
		if (last != null && last == now) {
			return TypedActionResult.consume(stack);
		}
		int amount = player.isSneaking() ? stack.getCount() : 1;
		long delta = value() * amount;
		boolean ok = kind == Kind.LYRA
				? LumiraEconomy.addLyres(player.getUuid(), delta, "Coin лиры x" + amount)
				: LumiraEconomy.addMyrtles(player.getUuid(), delta, "Coin мирты x" + amount);
		if (!ok) {
			player.sendMessage(Text.literal("Не удалось начислить: монета не потрачена").formatted(Formatting.RED), true);
			return TypedActionResult.fail(stack);
		}
		stack.decrement(amount);
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
				SoundCategory.PLAYERS, 0.6f, kind == Kind.LYRA ? 1.3f : 1.0f);
		String name = kind == Kind.LYRA ? "лир" : "мирт";
		long total = kind == Kind.LYRA ? LumiraEconomy.lyres(player.getUuid()) : LumiraEconomy.myrtles(player.getUuid());
		player.sendMessage(Text.literal("+" + delta + " " + name + "  •  сюжетный счёт: " + total).formatted(Formatting.GREEN), true);
		BalanceBoard.refresh(player, true);
		return TypedActionResult.success(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
		boolean lyra = kind == Kind.LYRA;
		tooltip.add(Text.literal(lyra ? "ПКМ — +" + value() + " к сюжетным лирам за монету"
				: "ПКМ — +" + value() + " к сюжетным миртам за монету").formatted(Formatting.GRAY));
		tooltip.add(Text.literal("Shift + ПКМ — потратить весь стак в руке").formatted(Formatting.GRAY));
	}
}
