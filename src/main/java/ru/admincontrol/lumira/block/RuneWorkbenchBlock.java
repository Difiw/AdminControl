package ru.admincontrol.lumira.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Рунный верстак: сетка 3×3, но только он открывает рецепты типа admin_control:rune_shaped. */
public class RuneWorkbenchBlock extends Block {
	public RuneWorkbenchBlock(AbstractBlock.Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		player.openHandledScreen(createScreenHandlerFactory(state, world, pos));
		return ActionResult.CONSUME;
	}

	@Override
	public NamedScreenHandlerFactory createScreenHandlerFactory(BlockState state, World world, BlockPos pos) {
		return ru.admincontrol.lumira.screen.LumiraScreens.factory(Text.translatable("block.admin_control.rune_workbench"),
				(syncId, inv, p) -> new ru.admincontrol.lumira.screen.RuneWorkbenchHandler(syncId, inv, ScreenHandlerContext.create(world, pos)));
	}
}
