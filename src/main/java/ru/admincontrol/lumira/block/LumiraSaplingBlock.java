package ru.admincontrol.lumira.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.SaplingBlock;
import net.minecraft.block.sapling.SaplingGenerator;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import ru.admincontrol.cw.CwBuilderWorld;
import ru.admincontrol.cw.CwStoryRules;
import ru.admincontrol.lumira.LumiraIds;
import ru.admincontrol.lumira.gen.LumiraTreeGen;

/** Саженец новой породы: растёт только в строительном и личных сюжетных измерениях, по собственным шаблонам. */
public class LumiraSaplingBlock extends SaplingBlock {
	private final LumiraIds.Tree tree;

	public LumiraSaplingBlock(LumiraIds.Tree tree, AbstractBlock.Settings settings) {
		super(new SaplingGenerator() {
			@Override
			protected RegistryKey<ConfiguredFeature<?, ?>> getTreeFeature(Random random, boolean bees) {
				return null;
			}
		}, settings);
		this.tree = tree;
	}

	public static boolean allowedWorld(ServerWorld world) {
		return CwStoryRules.inside(world) || world.getRegistryKey().equals(CwBuilderWorld.KEY);
	}

	@Override
	public boolean isFertilizable(net.minecraft.world.WorldView world, BlockPos pos, BlockState state, boolean client) {
		return world instanceof net.minecraft.world.World w && w.getRegistryKey().getValue().getNamespace().equals("admin_control");
	}

	@Override
	public void generate(ServerWorld world, BlockPos pos, BlockState state, Random random) {
		if (!allowedWorld(world)) {
			return;
		}
		if (state.get(STAGE) == 0) {
			world.setBlockState(pos, state.cycle(STAGE), 4);
			return;
		}
		LumiraTreeGen.growFromSapling(world, pos, tree, random);
	}
}
