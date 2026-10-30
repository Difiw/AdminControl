package ru.admincontrol.lumira.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import ru.admincontrol.lumira.LumiraContent;
import ru.admincontrol.lumira.recipe.RuneShapedRecipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Рунный верстак: сетка 4×4 (в ней помещаются любые рунные рецепты), боковой список рецептов по категориям
 * (инструменты, броня, украшения, прочее) с автозаполнением сетки из инвентаря по клику.
 */
public class RuneWorkbenchHandler extends ScreenHandler {
	public static final int GRID = 4;
	public static final int RESULT_SLOT = 0;
	public static final int GRID_START = 1;
	public static final int GRID_END = 1 + GRID * GRID;
	public static final int INV_END = GRID_END + 36;

	private final RecipeInputInventory input;
	private final CraftingResultInventory result = new CraftingResultInventory();
	private final ScreenHandlerContext context;
	private final PlayerEntity player;

	public RuneWorkbenchHandler(int syncId, PlayerInventory inventory) {
		this(syncId, inventory, ScreenHandlerContext.EMPTY);
	}

	public RuneWorkbenchHandler(int syncId, PlayerInventory inventory, ScreenHandlerContext context) {
		super(LumiraScreens.WORKBENCH, syncId);
		this.context = context;
		this.player = inventory.player;
		this.input = new CraftingInventory(this, GRID, GRID);
		this.addSlot(new CraftingResultSlot(inventory.player, this.input, this.result, 0, 224, 70));
		for (int row = 0; row < GRID; row++) {
			for (int col = 0; col < GRID; col++) {
				this.addSlot(new Slot(this.input, col + row * GRID, 108 + col * 18, 34 + row * 18));
			}
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(inventory, col + row * 9 + 9, 62 + col * 18, 126 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(inventory, col, 62 + col * 18, 184));
		}
	}

	/** Все рунные рецепты в одинаковом порядке на сервере и клиенте: по идентификатору. */
	public static List<RuneShapedRecipe> runeRecipes(World world) {
		List<RuneShapedRecipe> list = new ArrayList<>();
		for (CraftingRecipe r : world.getRecipeManager().listAllOfType(RecipeType.CRAFTING)) {
			if (r instanceof RuneShapedRecipe rune) {
				list.add(rune);
			}
		}
		list.sort(Comparator.comparing(r -> r.getId().toString()));
		return list;
	}

	private static <T> T gated(Supplier<T> action) {
		boolean previous = RuneShapedRecipe.GATE.get();
		RuneShapedRecipe.GATE.set(Boolean.TRUE);
		try {
			return action.get();
		} finally {
			RuneShapedRecipe.GATE.set(previous);
		}
	}

	private void updateResult() {
		this.context.run((world, pos) -> {
			if (world.isClient || !(this.player instanceof ServerPlayerEntity serverPlayer)) {
				return;
			}
			ItemStack out = ItemStack.EMPTY;
			Optional<CraftingRecipe> match = world.getServer().getRecipeManager().getFirstMatch(RecipeType.CRAFTING, this.input, world);
			if (match.isPresent()) {
				CraftingRecipe recipe = match.get();
				if (this.result.shouldCraftRecipe(world, serverPlayer, recipe)) {
					this.result.setLastRecipe(recipe);
					out = recipe.craft(this.input, world.getRegistryManager());
				}
			}
			this.result.setStack(0, out);
			this.setPreviousTrackedSlot(0, out);
			if (serverPlayer.networkHandler != null) {
				serverPlayer.networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(this.syncId, this.nextRevision(), 0, out));
			}
		});
	}

	@Override
	public void onContentChanged(Inventory inventory) {
		gated(() -> {
			updateResult();
			return null;
		});
	}

	@Override
	public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity p) {
		gated(() -> {
			super.onSlotClick(slotIndex, button, actionType, p);
			return null;
		});
	}

	/** Кнопка = номер рецепта в списке: автозаполнение сетки из инвентаря игрока. */
	@Override
	public boolean onButtonClick(PlayerEntity p, int id) {
		return gated(() -> {
			if (!(p instanceof ServerPlayerEntity)) {
				return false;
			}
			List<RuneShapedRecipe> all = runeRecipes(p.getWorld());
			if (id < 0 || id >= all.size()) {
				return false;
			}
			clearGrid(p);
			ShapedRecipe recipe = all.get(id);
			List<Ingredient> ingredients = recipe.getIngredients();
			for (int row = 0; row < recipe.getHeight() && row < GRID; row++) {
				for (int col = 0; col < recipe.getWidth() && col < GRID; col++) {
					Ingredient ing = ingredients.get(col + row * recipe.getWidth());
					if (ing.isEmpty()) {
						continue;
					}
					for (int i = 0; i < p.getInventory().size(); i++) {
						ItemStack candidate = p.getInventory().getStack(i);
						if (!candidate.isEmpty() && ing.test(candidate)) {
							this.input.setStack(col + row * GRID, candidate.split(1));
							break;
						}
					}
				}
			}
			updateResult();
			return true;
		});
	}

	private void clearGrid(PlayerEntity p) {
		for (int i = 0; i < this.input.size(); i++) {
			ItemStack stack = this.input.removeStack(i);
			if (!stack.isEmpty() && !p.getInventory().insertStack(stack)) {
				p.dropItem(stack, false);
			}
		}
	}

	@Override
	public ItemStack quickMove(PlayerEntity p, int index) {
		return gated(() -> {
			ItemStack moved = ItemStack.EMPTY;
			Slot slot = this.slots.get(index);
			if (slot != null && slot.hasStack()) {
				ItemStack stack = slot.getStack();
				moved = stack.copy();
				if (index == RESULT_SLOT) {
					this.context.run((world, pos) -> stack.getItem().onCraft(stack, world, p));
					if (!this.insertItem(stack, GRID_END, INV_END, true)) {
						return ItemStack.EMPTY;
					}
					slot.onQuickTransfer(stack, moved);
				} else if (index >= GRID_END) {
					if (!this.insertItem(stack, GRID_START, GRID_END, false)) {
						if (index < GRID_END + 27) {
							if (!this.insertItem(stack, GRID_END + 27, INV_END, false)) {
								return ItemStack.EMPTY;
							}
						} else if (!this.insertItem(stack, GRID_END, GRID_END + 27, false)) {
							return ItemStack.EMPTY;
						}
					}
				} else if (!this.insertItem(stack, GRID_END, INV_END, false)) {
					return ItemStack.EMPTY;
				}
				if (stack.isEmpty()) {
					slot.setStack(ItemStack.EMPTY);
				} else {
					slot.markDirty();
				}
				if (stack.getCount() == moved.getCount()) {
					return ItemStack.EMPTY;
				}
				slot.onTakeItem(p, stack);
				if (index == RESULT_SLOT) {
					p.dropItem(stack, false);
				}
			}
			return moved;
		});
	}

	@Override
	public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
		return slot.inventory != this.result && super.canInsertIntoSlot(stack, slot);
	}

	@Override
	public void onClosed(PlayerEntity p) {
		super.onClosed(p);
		this.context.run((world, pos) -> this.dropInventory(p, this.input));
	}

	@Override
	public boolean canUse(PlayerEntity p) {
		return canUse(this.context, p, LumiraContent.RUNE_WORKBENCH);
	}
}
