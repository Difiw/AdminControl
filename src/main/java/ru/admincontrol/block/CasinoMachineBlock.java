package ru.admincontrol.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldEvents;
import net.minecraft.world.WorldView;
import ru.admincontrol.item.RepairToolItem;
import ru.admincontrol.util.CasinoRepairService;
import ru.admincontrol.util.CasinoWheelService;

import java.util.EnumMap;
import java.util.Map;

/**
 * Игровой автомат казино: занимает один блок в ширину и два в высоту; ПКМ по любой половине открывает «Рулетку удачи».
 * Выбор секторов, ставка и поломка хранятся в нижней половине ({@link CasinoMachineBlockEntity});
 * {@link #BROKEN} на обеих половинах только для вида: погасший экран, дым и меньше света.
 */
public class CasinoMachineBlock extends Block implements BlockEntityProvider {
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	public static final EnumProperty<DoubleBlockHalf> HALF = Properties.DOUBLE_BLOCK_HALF;
	public static final BooleanProperty BROKEN = BooleanProperty.of("broken");

	private static final Map<Direction, VoxelShape> LOWER = new EnumMap<>(Direction.class);
	private static final Map<Direction, VoxelShape> UPPER = new EnumMap<>(Direction.class);

	static {
		for (Direction dir : Direction.Type.HORIZONTAL) {
			LOWER.put(dir, VoxelShapes.union(
					box(dir, 0.5, 0, 0.5, 15.5, 2, 15.5),
					box(dir, 1.5, 2, 2, 14.5, 16, 15),
					box(dir, 1, 11, 0, 15, 16, 7)));
			UPPER.put(dir, VoxelShapes.union(
					box(dir, 1.5, 0, 3.5, 14.5, 12, 15),
					box(dir, 1, 12, 3, 15, 15, 15.5),
					box(dir, 14, 2, 8, 16, 13, 11)));
		}
	}

	public CasinoMachineBlock(Settings settings) {
		super(settings);
		this.setDefaultState(this.getStateManager().getDefaultState()
				.with(FACING, Direction.NORTH).with(HALF, DoubleBlockHalf.LOWER).with(BROKEN, false));
	}

	/** Коробка в координатах модели «лицом на север», повёрнутая к стороне {@code dir}. */
	private static VoxelShape box(Direction dir, double x1, double y1, double z1, double x2, double y2, double z2) {
		return switch (dir) {
			case EAST -> Block.createCuboidShape(16 - z2, y1, x1, 16 - z1, y2, x2);
			case SOUTH -> Block.createCuboidShape(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
			case WEST -> Block.createCuboidShape(z1, y1, 16 - x2, z2, y2, 16 - x1);
			default -> Block.createCuboidShape(x1, y1, z1, x2, y2, z2);
		};
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return state.get(HALF) == DoubleBlockHalf.LOWER ? new CasinoMachineBlockEntity(pos, state) : null;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, HALF, BROKEN);
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return (state.get(HALF) == DoubleBlockHalf.UPPER ? UPPER : LOWER).get(state.get(FACING));
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		BlockPos pos = ctx.getBlockPos();
		World world = ctx.getWorld();
		if (pos.getY() >= world.getTopY() - 1 || !world.getBlockState(pos.up()).canReplace(ctx)) {
			return null;
		}
		return this.getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
		world.setBlockState(pos.up(), state.with(HALF, DoubleBlockHalf.UPPER), Block.NOTIFY_ALL);
		if (!world.isClient && world.getBlockEntity(pos) instanceof CasinoMachineBlockEntity machine && machine.broken()) {
			CasinoRepairService.showBroken(world, pos, true);
		}
	}

	@Override
	public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		if (state.get(HALF) == DoubleBlockHalf.LOWER) {
			return true;
		}
		BlockState below = world.getBlockState(pos.down());
		return below.isOf(this) && below.get(HALF) == DoubleBlockHalf.LOWER;
	}

	@Override
	public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
			WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		DoubleBlockHalf half = state.get(HALF);
		boolean pairSide = direction == (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN);
		if (pairSide && !(neighborState.isOf(this) && neighborState.get(HALF) != half)) {
			return Blocks.AIR.getDefaultState();
		}
		return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
	}

	@Override
	public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
		if (!world.isClient && player.isCreative() && state.get(HALF) == DoubleBlockHalf.UPPER) {
			BlockPos below = pos.down();
			BlockState lower = world.getBlockState(below);
			if (lower.isOf(this) && lower.get(HALF) == DoubleBlockHalf.LOWER) {
				world.setBlockState(below, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
				world.syncWorldEvent(player, WorldEvents.BLOCK_BROKEN, below, Block.getRawIdFromState(lower));
			}
		}
		super.onBreak(world, pos, state, player);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		if (player instanceof ServerPlayerEntity serverPlayer) {
			BlockPos base = state.get(HALF) == DoubleBlockHalf.UPPER ? pos.down() : pos;
			if (player.getStackInHand(hand).getItem() instanceof RepairToolItem) {
				CasinoRepairService.use(serverPlayer, base, hand);
			} else {
				CasinoWheelService.open(serverPlayer, base);
			}
		}
		return ActionResult.CONSUME;
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (!state.get(BROKEN) || state.get(HALF) != DoubleBlockHalf.UPPER) {
			return;
		}
		if (random.nextInt(3) == 0) {
			world.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.35 + random.nextDouble() * 0.3, pos.getY() + 0.95,
					pos.getZ() + 0.35 + random.nextDouble() * 0.3, 0.0, 0.03, 0.0);
		}
		if (random.nextInt(14) == 0) {
			Direction front = state.get(FACING).getOpposite();
			world.addParticle(ParticleTypes.ELECTRIC_SPARK,
					pos.getX() + 0.5 + front.getOffsetX() * 0.3 + (random.nextDouble() - 0.5) * 0.5,
					pos.getY() + 0.3 + random.nextDouble() * 0.4,
					pos.getZ() + 0.5 + front.getOffsetZ() * 0.3 + (random.nextDouble() - 0.5) * 0.5,
					0.0, 0.0, 0.0);
		}
	}

	@Override
	public BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}
}
