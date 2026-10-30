package ru.admincontrol.lumira.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.HoeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShovelItem;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import ru.admincontrol.lumira.LumiraContent;

/** Story soil that supports the normal shovel-path and hoe-farmland interactions. */
public final class AlbionSoilBlock extends Block {
	public AlbionSoilBlock(Settings settings) { super(settings); }

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		ItemStack held = player.getStackInHand(hand);
		BlockState replacement = held.getItem() instanceof ShovelItem ? LumiraContent.ALBION_PATH.getDefaultState()
				: held.getItem() instanceof HoeItem ? LumiraContent.ALBION_FARMLAND.getDefaultState() : null;
		if (replacement == null || !world.getBlockState(pos.up()).isAir()) return ActionResult.PASS;
		world.playSound(player, pos, SoundEvents.ITEM_SHOVEL_FLATTEN, SoundCategory.BLOCKS, 1.0f, 1.0f);
		if (!world.isClient) {
			world.setBlockState(pos, replacement, Block.NOTIFY_ALL);
			held.damage(1, player, p -> p.sendToolBreakStatus(hand));
		}
		return ActionResult.success(world.isClient);
	}
}
