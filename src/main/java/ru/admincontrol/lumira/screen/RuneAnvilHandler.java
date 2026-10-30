package ru.admincontrol.lumira.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import ru.admincontrol.lumira.LumiraContent;
import ru.admincontrol.lumira.block.RuneAnvilBlock;
import ru.admincontrol.lumira.block.RuneAnvilBlockEntity;
import ru.admincontrol.lumira.block.RuneAnvilLogic;

/**
 * Окно рунной наковальни: слот предмета, слот ремонтного материала, слот топлива (сгорает в запас блока), шкала
 * рунной энергии и кнопки «Ремонтировать» / «До конца».
 */
public class RuneAnvilHandler extends ScreenHandler {
	public static final int ITEM = 0, MATERIAL = 1, FUEL = 2;
	public static final int PLAYER_START = 3;
	public static final int BTN_ONCE = 0, BTN_ALL = 1;

	private final Inventory inventory = new SimpleInventory(3) {
		@Override
		public void markDirty() {
			super.markDirty();
			RuneAnvilHandler.this.onContentChanged(this);
		}
	};
	private final ScreenHandlerContext context;
	private final PropertyDelegate energy;
	private String message = "";

	public RuneAnvilHandler(int syncId, PlayerInventory inv) {
		this(syncId, inv, ScreenHandlerContext.EMPTY, new ArrayPropertyDelegate(1));
	}

	public RuneAnvilHandler(int syncId, PlayerInventory inv, ScreenHandlerContext context, PropertyDelegate energy) {
		super(LumiraScreens.ANVIL, syncId);
		this.context = context;
		this.energy = energy;
		this.addSlot(new Slot(this.inventory, ITEM, 56, 40) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return RuneAnvilBlock.repairMaterial(stack) != null;
			}
		});
		this.addSlot(new Slot(this.inventory, MATERIAL, 104, 40));
		this.addSlot(new Slot(this.inventory, FUEL, 80, 76) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return RuneAnvilBlock.fuelValue(stack.getItem()) > 0;
			}
		});
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 116 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(inv, col, 8 + col * 18, 174));
		}
		this.addProperties(energy);
	}

	public int energy() {
		return this.energy.get(0);
	}

	private RuneAnvilBlockEntity anvil() {
		return this.context.get((world, pos) -> world.getBlockEntity(pos) instanceof RuneAnvilBlockEntity be ? be : null).orElse(null);
	}

	@Override
	public void onContentChanged(Inventory inv) {
		super.onContentChanged(inv);
		// топливо из слота сразу сгорает в запас энергии блока
		RuneAnvilBlockEntity be = anvil();
		ItemStack fuel = this.inventory.getStack(FUEL);
		if (be != null && !fuel.isEmpty()) {
			int value = RuneAnvilBlock.fuelValue(fuel.getItem());
			if (value > 0) {
				be.addEnergy(value * fuel.getCount());
				this.inventory.setStack(FUEL, ItemStack.EMPTY);
			}
		}
		syncEnergy(be);
	}

	private void syncEnergy(RuneAnvilBlockEntity be) {
		if (be != null) {
			this.energy.set(0, be.energy());
		}
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		RuneAnvilBlockEntity be = anvil();
		if (be == null || (id != BTN_ONCE && id != BTN_ALL)) {
			return false;
		}
		ItemStack target = this.inventory.getStack(ITEM);
		RuneAnvilLogic.Result result = RuneAnvilLogic.repairOnce(target, this.inventory.getStack(MATERIAL), be);
		int guard = 0;
		boolean any = result == RuneAnvilLogic.Result.OK;
		while (id == BTN_ALL && result == RuneAnvilLogic.Result.OK && guard++ < 64) {
			result = RuneAnvilLogic.repairOnce(target, this.inventory.getStack(MATERIAL), be);
			any |= result == RuneAnvilLogic.Result.OK;
		}
		if (any) {
			this.context.run((world, pos) -> world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.6f, 1.3f));
			this.inventory.markDirty();
			player.sendMessage(Text.literal("Рунная наковальня: отремонтировано"), true);
		} else {
			player.sendMessage(Text.literal(result.text), true);
		}
		syncEnergy(be);
		sendContentUpdates();
		return true;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		ItemStack moved = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot != null && slot.hasStack()) {
			ItemStack stack = slot.getStack();
			moved = stack.copy();
			if (index < PLAYER_START) {
				if (!this.insertItem(stack, PLAYER_START, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (RuneAnvilBlock.fuelValue(stack.getItem()) > 0) {
				if (!this.insertItem(stack, FUEL, FUEL + 1, false)) {
					return ItemStack.EMPTY;
				}
			} else if (RuneAnvilBlock.repairMaterial(stack) != null) {
				if (!this.insertItem(stack, ITEM, ITEM + 1, false)) {
					return ItemStack.EMPTY;
				}
			} else if (!this.insertItem(stack, MATERIAL, MATERIAL + 1, false)) {
				return ItemStack.EMPTY;
			}
			if (stack.isEmpty()) {
				slot.setStack(ItemStack.EMPTY);
			} else {
				slot.markDirty();
			}
		}
		return moved;
	}

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		this.context.run((world, pos) -> this.dropInventory(player, this.inventory));
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return canUse(this.context, player, LumiraContent.RUNE_ANVIL);
	}

	public String message() {
		return this.message;
	}
}
