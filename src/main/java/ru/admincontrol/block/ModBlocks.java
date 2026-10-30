package ru.admincontrol.block;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import ru.admincontrol.AdminControlMod;

public final class ModBlocks {
	public static final Block CASINO_MACHINE = Registry.register(Registries.BLOCK,
			new Identifier(AdminControlMod.MOD_ID, "casino_machine"),
			new CasinoMachineBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.BLACK)
					.requiresTool()
					.strength(3.0f, 6.0f)
					.sounds(BlockSoundGroup.METAL)
					.luminance(state -> state.get(CasinoMachineBlock.BROKEN) ? 2 : 7)
					.nonOpaque()
					.pistonBehavior(PistonBehavior.BLOCK)));

	public static final BlockEntityType<CasinoMachineBlockEntity> CASINO_MACHINE_ENTITY = Registry.register(
			Registries.BLOCK_ENTITY_TYPE, new Identifier(AdminControlMod.MOD_ID, "casino_machine"),
			FabricBlockEntityTypeBuilder.create(CasinoMachineBlockEntity::new, CASINO_MACHINE).build());

	private ModBlocks() {
	}
}
