package ru.admincontrol.item;

import net.minecraft.block.BlockState;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import ru.admincontrol.block.CasinoMachineBlock;
import ru.admincontrol.util.CasinoRepairService;

import java.util.List;

/** Инструмент ремонта игрового автомата: ПКМ по сломанному автомату чинит свою часть поломки. */
public class RepairToolItem extends Item {
	private final RepairTool tool;

	public RepairToolItem(RepairTool tool, Settings settings) {
		super(settings);
		this.tool = tool;
	}

	public RepairTool tool() {
		return this.tool;
	}

	/** С зажатым Shift ПКМ по блоку не доходит до автомата, поэтому ремонт обрабатывается и здесь. */
	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		BlockPos pos = context.getBlockPos();
		BlockState state = world.getBlockState(pos);
		if (!(state.getBlock() instanceof CasinoMachineBlock)) {
			return ActionResult.PASS;
		}
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		if (context.getPlayer() instanceof ServerPlayerEntity player) {
			BlockPos base = state.get(CasinoMachineBlock.HALF) == DoubleBlockHalf.UPPER ? pos.down() : pos;
			CasinoRepairService.use(player, base, context.getHand());
		}
		return ActionResult.CONSUME;
	}

	@Override
	public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.literal("Ремонт игрового автомата").formatted(Formatting.GOLD));
		tooltip.add(Text.literal("ПКМ по сломанному автомату — чинить свою часть").formatted(Formatting.GRAY));
		tooltip.add(Text.literal("ПКМ рукой по автомату — какие инструменты нужны").formatted(Formatting.GRAY));
	}
}
