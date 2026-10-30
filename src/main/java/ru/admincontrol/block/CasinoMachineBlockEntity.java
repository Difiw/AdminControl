package ru.admincontrol.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import ru.admincontrol.item.RepairTool;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Выбор секторов и ставка «Рулетки удачи» хранятся в самом автомате и общие для всех игроков.
 * Здесь же счётчик прокруток после ремонта и план ремонта: сколько десятых долей процента должен починить каждый инструмент.
 */
public class CasinoMachineBlockEntity extends BlockEntity {
	public static final int DEFAULT_BET = 1000;
	private int mask;
	private int bet = DEFAULT_BET;
	private int spins;
	private boolean broken;
	private final int[] need = new int[RepairTool.COUNT];
	private final int[] done = new int[RepairTool.COUNT];
	private final Set<UUID> helpers = new LinkedHashSet<>();

	public CasinoMachineBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.CASINO_MACHINE_ENTITY, pos, state);
	}

	public int mask() {
		return this.mask;
	}

	public int bet() {
		return this.bet;
	}

	public void set(int mask, int bet) {
		if (mask != this.mask || bet != this.bet) {
			this.mask = mask;
			this.bet = bet;
			this.markDirty();
		}
	}

	public int spins() {
		return this.spins;
	}

	public void countSpin() {
		this.spins++;
		this.markDirty();
	}

	public boolean broken() {
		return this.broken;
	}

	public void breakDown(int[] plan) {
		this.broken = true;
		System.arraycopy(plan, 0, this.need, 0, this.need.length);
		Arrays.fill(this.done, 0);
		this.helpers.clear();
		this.markDirty();
	}

	public void repaired() {
		this.broken = false;
		this.spins = 0;
		Arrays.fill(this.need, 0);
		Arrays.fill(this.done, 0);
		this.helpers.clear();
		this.markDirty();
	}

	/** Сколько десятых долей процента должен починить инструмент (0 — не нужен). */
	public int need(RepairTool tool) {
		return this.need[tool.ordinal()];
	}

	public int done(RepairTool tool) {
		return this.done[tool.ordinal()];
	}

	public int add(RepairTool tool, int tenths, UUID helper) {
		int i = tool.ordinal();
		int before = this.done[i];
		this.done[i] = Math.min(this.need[i], before + Math.max(0, tenths));
		if (helper != null) {
			this.helpers.add(helper);
		}
		this.markDirty();
		return this.done[i] - before;
	}

	public int progress() {
		int sum = 0;
		for (int value : this.done) {
			sum += value;
		}
		return sum;
	}

	public boolean complete() {
		for (int i = 0; i < this.need.length; i++) {
			if (this.done[i] < this.need[i]) {
				return false;
			}
		}
		return true;
	}

	public Set<UUID> helpers() {
		return this.helpers;
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);
		nbt.putInt("Mask", this.mask);
		nbt.putInt("Bet", this.bet);
		nbt.putInt("Spins", this.spins);
		nbt.putBoolean("Broken", this.broken);
		nbt.putIntArray("Need", this.need.clone());
		nbt.putIntArray("Done", this.done.clone());
		NbtList list = new NbtList();
		for (UUID uuid : this.helpers) {
			list.add(NbtHelper.fromUuid(uuid));
		}
		nbt.put("Helpers", list);
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		this.mask = nbt.getInt("Mask");
		this.bet = nbt.contains("Bet") ? nbt.getInt("Bet") : DEFAULT_BET;
		this.spins = nbt.getInt("Spins");
		this.broken = nbt.getBoolean("Broken");
		copy(nbt.getIntArray("Need"), this.need);
		copy(nbt.getIntArray("Done"), this.done);
		this.helpers.clear();
		for (NbtElement element : nbt.getList("Helpers", NbtElement.INT_ARRAY_TYPE)) {
			this.helpers.add(NbtHelper.toUuid(element));
		}
		if (this.broken && Arrays.stream(this.need).sum() <= 0) {
			this.broken = false;
		}
	}

	private static void copy(int[] from, int[] to) {
		Arrays.fill(to, 0);
		System.arraycopy(from, 0, to, 0, Math.min(from.length, to.length));
	}
}
