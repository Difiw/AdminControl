package ru.admincontrol.lumira.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/** Story path block: 15/16 high like the vanilla dirt path. */
public final class AlbionPathBlock extends Block {
	private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 15, 16);

	public AlbionPathBlock(Settings settings) { super(settings); }

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPE; }

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPE; }
}
