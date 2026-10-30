package ru.admincontrol.lumira.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.block.SpreadableBlock;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.HoeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShovelItem;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.cw.CwBuilderWorld;
import ru.admincontrol.cw.CwStoryRules;
import ru.admincontrol.lumira.LumiraContent;
import ru.admincontrol.lumira.gen.LumiraBiomeGen;

/** Albion turf with vanilla-like death/spread, constrained to Albion in CW dimensions. */
public final class AlbionGrassBlock extends SpreadableBlock implements net.minecraft.block.Fertilizable {
	private static final RegistryKey<Biome> ALBION = RegistryKey.of(RegistryKeys.BIOME, LumiraBiomeGen.BIOME_ID);

	public AlbionGrassBlock(Settings settings) { super(settings); }

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

	private static boolean survives(BlockState state, WorldView world, BlockPos pos) {
		BlockPos up = pos.up();
		BlockState cover = world.getBlockState(up);
		if (cover.isOf(Blocks.SNOW) && cover.get(SnowBlock.LAYERS) == 1) return true;
		if (cover.getFluidState().getLevel() == 8) return false;
		return ChunkLightProvider.getRealisticOpacity(world, state, pos, cover, up, Direction.UP, cover.getOpacity(world, up)) < 15;
	}

	private static boolean albion(ServerWorld world, BlockPos pos) {
		String id = world.getRegistryKey().getValue().toString();
		return (CwStoryRules.isStoryWorldId(id) || CwBuilderWorld.KEY.equals(world.getRegistryKey()))
				&& world.getBiome(pos).matchesKey(ALBION);
	}

	@Override
	public void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		if (!survives(state, world, pos)) {
			world.setBlockState(pos, LumiraContent.ALBION_DIRT.getDefaultState());
			return;
		}
		if (!albion(world, pos) || world.getLightLevel(pos.up()) < 9) return;
		for (int i = 0; i < 4; i++) {
			BlockPos target = pos.add(random.nextInt(3) - 1, random.nextInt(5) - 3, random.nextInt(3) - 1);
			if (world.getBlockState(target).isOf(LumiraContent.ALBION_DIRT) && survives(state, world, target) && albion(world, target)) {
				world.setBlockState(target, getDefaultState(), Block.NOTIFY_ALL);
			}
		}
	}

	// ---- bone meal: same behaviour as the vanilla grass block ----
	@Override
	public boolean isFertilizable(WorldView world, BlockPos pos, BlockState state, boolean isClient) {
		return world.getBlockState(pos.up()).isAir();
	}

	@Override
	public boolean canGrow(World world, Random random, BlockPos pos, BlockState state) {
		return true;
	}

	@Override
	public void grow(ServerWorld world, Random random, BlockPos pos, BlockState state) {
		BlockPos up = pos.up();
		BlockState grass = Blocks.GRASS.getDefaultState();
		java.util.Optional<net.minecraft.registry.entry.RegistryEntry.Reference<net.minecraft.world.gen.feature.PlacedFeature>> grassFeature =
				world.getRegistryManager().get(RegistryKeys.PLACED_FEATURE).getEntry(net.minecraft.world.gen.feature.VegetationPlacedFeatures.GRASS_BONEMEAL);
		for (int i = 0; i < 128; i++) {
			BlockPos p = up;
			boolean ok = true;
			for (int j = 0; j < i / 16; j++) {
				p = p.add(random.nextInt(3) - 1, (random.nextInt(3) - 1) * random.nextInt(3) / 2, random.nextInt(3) - 1);
				if (!world.getBlockState(p.down()).isOf(this) || world.getBlockState(p).isFullCube(world, p)) { ok = false; break; }
			}
			if (!ok) continue;
			BlockState at = world.getBlockState(p);
			if (at.isOf(grass.getBlock()) && random.nextInt(10) == 0) {
				((net.minecraft.block.Fertilizable) grass.getBlock()).grow(world, random, p, at);
			}
			if (!at.isAir()) continue;
			net.minecraft.registry.entry.RegistryEntry<net.minecraft.world.gen.feature.PlacedFeature> feature;
			if (random.nextInt(8) == 0) {
				java.util.List<net.minecraft.world.gen.feature.ConfiguredFeature<?, ?>> flowers = world.getBiome(p).value().getGenerationSettings().getFlowerFeatures();
				if (flowers.isEmpty()) continue;
				feature = ((net.minecraft.world.gen.feature.RandomPatchFeatureConfig) flowers.get(0).config()).feature();
			} else {
				if (grassFeature.isEmpty()) continue;
				feature = grassFeature.get();
			}
			feature.value().generateUnregistered(world, world.getChunkManager().getChunkGenerator(), random, p);
		}
	}
}
