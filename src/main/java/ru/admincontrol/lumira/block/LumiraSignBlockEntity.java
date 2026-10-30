package ru.admincontrol.lumira.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.HangingSignBlock;
import net.minecraft.block.WallHangingSignBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.util.math.BlockPos;
import ru.admincontrol.lumira.LumiraContent;

public class LumiraSignBlockEntity extends SignBlockEntity {
	public LumiraSignBlockEntity(BlockPos pos, BlockState state) {
		super(LumiraContent.SIGN_ENTITY, pos, state);
	}

	private boolean hanging() {
		return getCachedState().getBlock() instanceof HangingSignBlock
				|| getCachedState().getBlock() instanceof WallHangingSignBlock;
	}

	@Override
	public int getTextLineHeight() {
		return hanging() ? 9 : 10;
	}

	@Override
	public int getMaxTextWidth() {
		return hanging() ? 60 : 90;
	}
}
