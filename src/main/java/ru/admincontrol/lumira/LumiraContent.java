package ru.admincontrol.lumira;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.block.type.BlockSetTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.block.type.WoodTypeBuilder;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.fabricmc.fabric.api.registry.StrippableBlockRegistry;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.Blocks;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.ExperienceDroppingBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.GlassBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.PressurePlateBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.WoodType;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.HangingSignItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SignItem;
import net.minecraft.item.SwordItem;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.intprovider.UniformIntProvider;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.lumira.block.LumiraHangingSignBlock;
import ru.admincontrol.lumira.block.AlbionGrassBlock;
import ru.admincontrol.lumira.block.AlbionSoilBlock;
import ru.admincontrol.lumira.block.LumiraSaplingBlock;
import ru.admincontrol.lumira.block.LumiraSignBlock;
import ru.admincontrol.lumira.block.LumiraSignBlockEntity;
import ru.admincontrol.lumira.block.LumiraWallHangingSignBlock;
import ru.admincontrol.lumira.block.LumiraWallSignBlock;
import ru.admincontrol.lumira.block.RuneAnvilBlock;
import ru.admincontrol.lumira.block.RuneAnvilBlockEntity;
import ru.admincontrol.lumira.block.RuneWorkbenchBlock;
import ru.admincontrol.lumira.item.NecklaceItem;
import ru.admincontrol.lumira.item.StoryCoinItem;
import ru.admincontrol.lumira.recipe.RuneShapedRecipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Регистрация всего контента «Рощи Люмиры»: руды, ресурсы, 20 инструментов, 8 частей брони, 7 пород, станции. */
public final class LumiraContent {
	public static final Map<String, Block> BLOCKS = new LinkedHashMap<>();
	public static final Map<String, Item> ITEMS = new LinkedHashMap<>();
	public static final Map<String, LumiraMaterials.Tool> TOOL_MATERIALS = new LinkedHashMap<>();
	public static final Map<String, LumiraMaterials.Armor> ARMOR_MATERIALS = new LinkedHashMap<>();
	public static final List<Block> SIGN_BLOCKS = new ArrayList<>();
	public static final List<Block> MAGIC_GLASS = new ArrayList<>();
	public static final List<Block> MAGIC_GLASS_PANES = new ArrayList<>();
	public static final List<Item> CREATIVE_ORDER = new ArrayList<>();

	public static Item HIDDEN_ITEM;
	public static Block ALBION_GRASS;
	public static Block ALBION_DIRT;
	public static Block ALBION_PATH;
	public static Block ALBION_FARMLAND;
	public static Block ALBION_STONE;
	public static Block ALBION_COBBLESTONE;
	public static Block RUNE_WORKBENCH;
	public static Block RUNE_ANVIL;
	public static BlockEntityType<RuneAnvilBlockEntity> RUNE_ANVIL_ENTITY;
	public static BlockEntityType<LumiraSignBlockEntity> SIGN_ENTITY;
	public static RecipeSerializer<RuneShapedRecipe> RUNE_SHAPED;
	public static net.minecraft.recipe.SpecialRecipeSerializer<ru.admincontrol.lumira.recipe.EditableCoinRecipe> COIN_LYRA_RECIPE;
	public static net.minecraft.recipe.SpecialRecipeSerializer<ru.admincontrol.lumira.recipe.EditableCoinRecipe> COIN_MYRTLE_RECIPE;
	public static ItemGroup GROUP;
	private static boolean done;

	private LumiraContent() {
	}

	private static Identifier id(String path) {
		return new Identifier(AdminControlMod.MOD_ID, path);
	}

	private static Block block(String path, Block block) {
		Registry.register(Registries.BLOCK, id(path), block);
		BLOCKS.put(path, block);
		return block;
	}

	private static Item item(String path, Item item) {
		Registry.register(Registries.ITEM, id(path), item);
		ITEMS.put(path, item);
		CREATIVE_ORDER.add(item);
		return item;
	}

	private static BlockItem blockItem(String path, Block block) {
		return (BlockItem) item(path, new BlockItem(block, new FabricItemSettings()));
	}

	public static synchronized void register() {
		if (done) {
			return;
		}
		done = true;
		ru.admincontrol.lumira.screen.LumiraScreens.register();
		LumiraConfig cfg = LumiraConfig.get();
		HIDDEN_ITEM = Registry.register(Registries.ITEM, id("hidden_item"), new Item(new FabricItemSettings()));
		registerAlbionTerrain();
		registerMagicGlass();
		registerOres();
		registerResources();
		registerTools();
		registerArmor();
		registerStations();
		registerTrees();
		registerNecklaces(cfg);
		item("coin_lyra", new StoryCoinItem(StoryCoinItem.Kind.LYRA, new FabricItemSettings().maxCount(64).rarity(Rarity.UNCOMMON)));
		item("coin_myrtle", new StoryCoinItem(StoryCoinItem.Kind.MYRTLE, new FabricItemSettings().maxCount(64).rarity(Rarity.UNCOMMON)));
		RUNE_SHAPED = Registry.register(Registries.RECIPE_SERIALIZER, id("rune_shaped"), new RuneShapedRecipe.Serializer());
		COIN_LYRA_RECIPE = Registry.register(Registries.RECIPE_SERIALIZER, id("coin_lyra_recipe"),
				new net.minecraft.recipe.SpecialRecipeSerializer<>((rid, cat) -> new ru.admincontrol.lumira.recipe.EditableCoinRecipe(rid, cat, true)));
		COIN_MYRTLE_RECIPE = Registry.register(Registries.RECIPE_SERIALIZER, id("coin_myrtle_recipe"),
				new net.minecraft.recipe.SpecialRecipeSerializer<>((rid, cat) -> new ru.admincontrol.lumira.recipe.EditableCoinRecipe(rid, cat, false)));
		registerFuels();
		GROUP = Registry.register(Registries.ITEM_GROUP, id("lumira"), FabricItemGroup.builder()
				.icon(() -> new ItemStack(ITEMS.get("lumirite_ingot")))
				.displayName(Text.translatable("itemGroup.admin_control.lumira"))
				.entries((context, entries) -> CREATIVE_ORDER.forEach(entries::add))
				.build());
	}

	private static void registerAlbionTerrain() {
		ALBION_DIRT = block("albion_dirt", new AlbionSoilBlock(AbstractBlock.Settings.copy(Blocks.DIRT)));
		ALBION_GRASS = block("albion_grass_block", new AlbionGrassBlock(AbstractBlock.Settings.copy(Blocks.GRASS_BLOCK)));
		ALBION_PATH = block("albion_dirt_path", new ru.admincontrol.lumira.block.AlbionPathBlock(AbstractBlock.Settings.copy(Blocks.DIRT_PATH)));
		ALBION_FARMLAND = block("albion_farmland", new FarmlandBlock(AbstractBlock.Settings.copy(Blocks.FARMLAND)));
		ALBION_STONE = block("albion_stone", new Block(AbstractBlock.Settings.copy(Blocks.STONE).requiresTool()));
		ALBION_COBBLESTONE = block("albion_cobblestone", new Block(AbstractBlock.Settings.copy(Blocks.COBBLESTONE).requiresTool()));
		blockItem("albion_grass_block", ALBION_GRASS);
		blockItem("albion_dirt", ALBION_DIRT);
		blockItem("albion_dirt_path", ALBION_PATH);
		blockItem("albion_farmland", ALBION_FARMLAND);
		blockItem("albion_stone", ALBION_STONE);
		blockItem("albion_cobblestone", ALBION_COBBLESTONE);
	}

	private static void registerMagicGlass() {
		for (String name : new String[]{"azure", "amethyst", "emerald", "starlight", "blossom", "waterfall", "night"}) {
			String id = "lumira_" + name + "_glass";
			Block glass = block(id, new GlassBlock(AbstractBlock.Settings.copy(Blocks.GLASS).nonOpaque()));
			MAGIC_GLASS.add(glass);
			blockItem(id, glass);
			String paneId = id + "_pane";
			Block pane = block(paneId, new PaneBlock(AbstractBlock.Settings.copy(Blocks.GLASS_PANE).nonOpaque()));
			MAGIC_GLASS_PANES.add(pane);
			blockItem(paneId, pane);
		}
	}

	private static void registerOres() {
		for (LumiraIds.Ore ore : LumiraIds.ORES) {
			AbstractBlock.Settings settings = AbstractBlock.Settings.copy(ore.material().equals("deepslate")
					? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE).requiresTool();
			Block b = block(ore.id(), new ExperienceDroppingBlock(settings, UniformIntProvider.create(ore.xpMin(), ore.xpMax())));
			blockItem(ore.id(), b);
		}
		for (LumiraIds.Metal m : LumiraIds.METALS) {
			Block b = block(m.id() + "_block", new Block(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).requiresTool()
					.mapColor(MapColor.CYAN)));
			blockItem(m.id() + "_block", b);
		}
	}

	private static void registerResources() {
		for (LumiraIds.Ore ore : LumiraIds.ORES) {
			item(ore.drop(), new Item(new FabricItemSettings()));
		}
		for (LumiraIds.Metal m : LumiraIds.METALS) {
			item(m.id() + "_ingot", new Item(new FabricItemSettings()));
		}
		for (String id : new String[]{"spark_briquette", "jarolite_briquette", "dew_lens", "echo_focus", "heart_prism", "rune_thread"}) {
			item(id, new Item(new FabricItemSettings()));
		}
	}

	private static void registerTools() {
		for (LumiraIds.Metal m : LumiraIds.METALS) {
			LumiraMaterials.Tool mat = new LumiraMaterials.Tool(m, LumiraMaterials.ingot(m.id() + "_ingot"));
			TOOL_MATERIALS.put(m.id(), mat);
			String p = m.id() + "_";
			item(p + "sword", new SwordItem(mat, Math.round(m.swordDmg() - 1), m.swordSpeed() - 4f, new FabricItemSettings()));
			item(p + "axe", new AxeItem(mat, m.axeDmg() - 1f, m.axeSpeed() - 4f, new FabricItemSettings()) {
			});
			item(p + "pickaxe", new PickaxeItem(mat, Math.round(m.pickDmg() - 1), m.pickSpeed() - 4f, new FabricItemSettings()));
			item(p + "shovel", new ShovelItem(mat, m.shovelDmg() - 1f, 1f - 4f, new FabricItemSettings()));
			item(p + "hoe", new HoeItem(mat, Math.round(m.hoeDmg() - 1), m.hoeSpeed() - 4f, new FabricItemSettings()) {
			});
		}
	}

	private static void registerArmor() {
		for (LumiraIds.Metal m : LumiraIds.METALS) {
			if (!m.hasArmor()) {
				continue;
			}
			boolean star = m.id().equals("starsilver");
			LumiraMaterials.Armor mat = new LumiraMaterials.Armor(m,
					star ? LumiraIds.STAR_PROT : LumiraIds.TWILIGHT_PROT,
					star ? LumiraIds.STAR_DUR : LumiraIds.TWILIGHT_DUR,
					star ? 2f : 0f, LumiraMaterials.ingot(m.id() + "_ingot"));
			ARMOR_MATERIALS.put(m.id(), mat);
			ArmorItem.Type[] types = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};
			for (int i = 0; i < 4; i++) {
				item(m.id() + "_" + LumiraIds.ARMOR_KINDS[i], new ArmorItem(mat, types[i], new FabricItemSettings()));
			}
		}
	}

	private static void registerStations() {
		RUNE_WORKBENCH = block("rune_workbench", new RuneWorkbenchBlock(AbstractBlock.Settings.copy(Blocks.CRAFTING_TABLE)
				.mapColor(MapColor.CYAN)));
		blockItem("rune_workbench", RUNE_WORKBENCH);
		RUNE_ANVIL = block("rune_anvil", new RuneAnvilBlock(AbstractBlock.Settings.copy(Blocks.ANVIL).nonOpaque()));
		blockItem("rune_anvil", RUNE_ANVIL);
		RUNE_ANVIL_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, id("rune_anvil"),
				FabricBlockEntityTypeBuilder.create(RuneAnvilBlockEntity::new, RUNE_ANVIL).build());
		item("rune_staff", new Item(new FabricItemSettings().maxCount(1)));
	}

	private static void registerTrees() {
		for (LumiraIds.Tree t : LumiraIds.TREES) {
			String s = t.id();
			BlockSetType setType = BlockSetTypeBuilder.copyOf(BlockSetType.OAK).register(id(s));
			WoodType woodType = WoodTypeBuilder.copyOf(WoodType.OAK).register(id(s), setType);
			AbstractBlock.Settings logSettings = AbstractBlock.Settings.copy(Blocks.OAK_LOG);
			Block log = block(s + "_log", new PillarBlock(logSettings));
			Block strippedLog = block("stripped_" + s + "_log", new PillarBlock(AbstractBlock.Settings.copy(Blocks.STRIPPED_OAK_LOG)));
			Block wood = block(s + "_wood", new PillarBlock(AbstractBlock.Settings.copy(Blocks.OAK_WOOD)));
			Block strippedWood = block("stripped_" + s + "_wood", new PillarBlock(AbstractBlock.Settings.copy(Blocks.STRIPPED_OAK_WOOD)));
			Block planks = block(s + "_planks", new Block(AbstractBlock.Settings.copy(Blocks.OAK_PLANKS)));
			Block leaves = block(s + "_leaves", new LeavesBlock(AbstractBlock.Settings.copy(Blocks.OAK_LEAVES)));
			Block sapling = block(s + "_sapling", new LumiraSaplingBlock(t, AbstractBlock.Settings.copy(Blocks.OAK_SAPLING)));
			Block stairs = block(s + "_stairs", new StairsBlock(planks.getDefaultState(), AbstractBlock.Settings.copy(Blocks.OAK_STAIRS)));
			Block slab = block(s + "_slab", new SlabBlock(AbstractBlock.Settings.copy(Blocks.OAK_SLAB)));
			Block fence = block(s + "_fence", new FenceBlock(AbstractBlock.Settings.copy(Blocks.OAK_FENCE)));
			Block gate = block(s + "_fence_gate", new FenceGateBlock(AbstractBlock.Settings.copy(Blocks.OAK_FENCE_GATE), woodType));
			Block door = block(s + "_door", new DoorBlock(AbstractBlock.Settings.copy(Blocks.OAK_DOOR), setType));
			Block closedDoor = block(s + "_closed_door", new DoorBlock(AbstractBlock.Settings.copy(Blocks.OAK_DOOR), setType));
			Block trapdoor = block(s + "_trapdoor", new TrapdoorBlock(AbstractBlock.Settings.copy(Blocks.OAK_TRAPDOOR), setType));
			Block button = block(s + "_button", new ButtonBlock(AbstractBlock.Settings.copy(Blocks.OAK_BUTTON), setType, 30, true));
			Block plate = block(s + "_pressure_plate", new PressurePlateBlock(PressurePlateBlock.ActivationRule.EVERYTHING,
					AbstractBlock.Settings.copy(Blocks.OAK_PRESSURE_PLATE), setType));
			Block sign = block(s + "_sign", new LumiraSignBlock(AbstractBlock.Settings.copy(Blocks.OAK_SIGN), woodType));
			Block wallSign = block(s + "_wall_sign", new LumiraWallSignBlock(AbstractBlock.Settings.copy(Blocks.OAK_WALL_SIGN)
					.dropsLike(sign), woodType));
			Block hanging = block(s + "_hanging_sign", new LumiraHangingSignBlock(AbstractBlock.Settings.copy(Blocks.OAK_HANGING_SIGN), woodType));
			Block wallHanging = block(s + "_wall_hanging_sign", new LumiraWallHangingSignBlock(
					AbstractBlock.Settings.copy(Blocks.OAK_WALL_HANGING_SIGN).dropsLike(hanging), woodType));
			SIGN_BLOCKS.add(sign);
			SIGN_BLOCKS.add(wallSign);
			SIGN_BLOCKS.add(hanging);
			SIGN_BLOCKS.add(wallHanging);

			blockItem(s + "_log", log);
			blockItem("stripped_" + s + "_log", strippedLog);
			blockItem(s + "_wood", wood);
			blockItem("stripped_" + s + "_wood", strippedWood);
			blockItem(s + "_planks", planks);
			blockItem(s + "_leaves", leaves);
			blockItem(s + "_sapling", sapling);
			blockItem(s + "_stairs", stairs);
			blockItem(s + "_slab", slab);
			blockItem(s + "_fence", fence);
			blockItem(s + "_fence_gate", gate);
			blockItem(s + "_door", door);
			blockItem(s + "_closed_door", closedDoor);
			blockItem(s + "_trapdoor", trapdoor);
			blockItem(s + "_button", button);
			blockItem(s + "_pressure_plate", plate);
			item(s + "_sign", new SignItem(new FabricItemSettings().maxCount(16), sign, wallSign));
			item(s + "_hanging_sign", new HangingSignItem(hanging, wallHanging, new FabricItemSettings().maxCount(16)));

			StrippableBlockRegistry.register(log, strippedLog);
			StrippableBlockRegistry.register(wood, strippedWood);
			FlammableBlockRegistry flam = FlammableBlockRegistry.getDefaultInstance();
			for (Block b : new Block[]{log, strippedLog, wood, strippedWood}) {
				flam.add(b, 5, 5);
			}
			for (Block b : new Block[]{planks, stairs, slab, fence, gate}) {
				flam.add(b, 5, 20);
			}
			flam.add(leaves, 30, 60);
		}
		SIGN_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, id("lumira_sign"),
				FabricBlockEntityTypeBuilder.create(LumiraSignBlockEntity::new, SIGN_BLOCKS.toArray(new Block[0])).build());
	}

	private static void registerNecklaces(LumiraConfig cfg) {
		for (int i = 0; i < LumiraIds.NECKLACES.length; i++) {
			item("necklace_" + LumiraIds.NECKLACES[i], new NecklaceItem(LumiraIds.NECKLACES[i],
					new FabricItemSettings().maxDamage(cfg.necklaceCharges).rarity(Rarity.RARE)));
		}
	}

	private static void registerFuels() {
		LumiraConfig cfg = LumiraConfig.get();
		int op = cfg.burnTicksPerOperation;
		FuelRegistry.INSTANCE.add(ITEMS.get("spark_coal"), cfg.fuelSparkCoal * op);
		FuelRegistry.INSTANCE.add(ITEMS.get("spark_briquette"), cfg.fuelSparkBriquette * op);
		FuelRegistry.INSTANCE.add(ITEMS.get("jarolite"), cfg.fuelJarolite * op);
		FuelRegistry.INSTANCE.add(ITEMS.get("jarolite_briquette"), cfg.fuelJaroliteBriquette * op);
	}

	public static Block block(String path) {
		return BLOCKS.get(path);
	}

	public static Item item(String path) {
		return ITEMS.get(path);
	}
}
