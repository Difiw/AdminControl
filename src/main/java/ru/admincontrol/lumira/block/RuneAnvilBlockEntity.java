package ru.admincontrol.lumira.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import ru.admincontrol.lumira.LumiraContent;

/** Хранит остаток топливной энергии наковальни между операциями. */
public class RuneAnvilBlockEntity extends BlockEntity {
	private int energy;

	public RuneAnvilBlockEntity(BlockPos pos, BlockState state) {
		super(LumiraContent.RUNE_ANVIL_ENTITY, pos, state);
	}

	public int energy() {
		return energy;
	}

	public void addEnergy(int delta) {
		energy = Math.max(0, energy + delta);
		markDirty();
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);
		nbt.putInt("Energy", energy);
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		energy = Math.max(0, nbt.getInt("Energy"));
	}
}
