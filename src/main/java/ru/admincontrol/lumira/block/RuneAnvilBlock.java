package ru.admincontrol.lumira.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ToolItem;
import net.minecraft.recipe.Ingredient;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import ru.admincontrol.lumira.LumiraConfig;
import ru.admincontrol.lumira.LumiraContent;
import ru.admincontrol.lumira.LumiraMaterials;
import ru.admincontrol.lumira.item.NecklaceItem;

/**
 * Рунная наковальня. Одна операция: удерживаемый предмет + один ремонтный материал из инвентаря + «операция
 * топлива» из запаса блока. Топливо кладётся в блок ПКМ (Shift — вся стопка). Опыта нет, чары сохраняются.
 */
public class RuneAnvilBlock extends BlockWithEntity {
	private static final VoxelShape SHAPE = VoxelShapes.union(
			VoxelShapes.cuboid(0.125, 0.0, 0.125, 0.875, 0.25, 0.875),
			VoxelShapes.cuboid(0.25, 0.25, 0.25, 0.75, 0.625, 0.75),
			VoxelShapes.cuboid(0.0, 0.625, 0.0, 1.0, 1.0, 1.0));

	public RuneAnvilBlock(AbstractBlock.Settings settings) {
		super(settings);
	}

	@Override
	public BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new RuneAnvilBlockEntity(pos, state);
	}

	/** Сколько операций даёт предмет-топливо; 0 — не топливо. */
	public static int fuelValue(Item item) {
		LumiraConfig cfg = LumiraConfig.get();
		if (item == Items.COAL) {
			return cfg.fuelCoal;
		}
		if (item == LumiraContent.item("spark_coal")) {
			return cfg.fuelSparkCoal;
		}
		if (item == LumiraContent.item("spark_briquette")) {
			return cfg.fuelSparkBriquette;
		}
		if (item == LumiraContent.item("jarolite")) {
			return cfg.fuelJarolite;
		}
		if (item == LumiraContent.item("jarolite_briquette")) {
			return cfg.fuelJaroliteBriquette;
		}
		return 0;
	}

	/** Ремонтный материал нового предмета Люмиры или null, если наковальня предмет не обслуживает. */
	public static Ingredient repairMaterial(ItemStack stack) {
		Item item = stack.getItem();
		if (item instanceof ToolItem tool && tool.getMaterial() instanceof LumiraMaterials.Tool) {
			return tool.getMaterial().getRepairIngredient();
		}
		if (item instanceof ArmorItem armor && armor.getMaterial() instanceof LumiraMaterials.Armor) {
			return armor.getMaterial().getRepairIngredient();
		}
		if (item instanceof NecklaceItem necklace) {
			return Ingredient.ofItems(necklace.repairItem());
		}
		return null;
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		if (!(world.getBlockEntity(pos) instanceof RuneAnvilBlockEntity anvil)) {
			return ActionResult.PASS;
		}
		net.minecraft.screen.PropertyDelegate energy = new net.minecraft.screen.PropertyDelegate() {
			@Override
			public int get(int index) {
				return anvil.energy();
			}

			@Override
			public void set(int index, int value) {
			}

			@Override
			public int size() {
				return 1;
			}
		};
		player.openHandledScreen(ru.admincontrol.lumira.screen.LumiraScreens.factory(Text.translatable("block.admin_control.rune_anvil"),
				(syncId, inv, p) -> new ru.admincontrol.lumira.screen.RuneAnvilHandler(syncId, inv,
						net.minecraft.screen.ScreenHandlerContext.create(world, pos), energy)));
		return ActionResult.CONSUME;
	}
}
