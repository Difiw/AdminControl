package ru.admincontrol.lumira.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.HangingSignBlock;
import net.minecraft.block.WoodType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import ru.admincontrol.lumira.LumiraContent;

/** Табличка семейства Люмиры: собственный тип блочной сущности, потому что ванильный SIGN не знает наших блоков. */
public class LumiraHangingSignBlock extends HangingSignBlock {
	public LumiraHangingSignBlock(AbstractBlock.Settings settings, WoodType woodType) {
		super(settings, woodType);
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new LumiraSignBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return checkType(type, LumiraContent.SIGN_ENTITY, SignBlockEntity::tick);
	}
}
