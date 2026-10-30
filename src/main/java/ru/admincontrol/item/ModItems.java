package ru.admincontrol.item;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.world.World;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.block.ModBlocks;
import ru.admincontrol.skill.SkillTree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ModItems {
	private static Item cwTool(String id) {
		return Registry.register(Registries.ITEM, new Identifier(AdminControlMod.MOD_ID, id),
				new Item(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)) {
					@Override
					public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
						tooltip.add(Text.translatable("item.admin_control." + id + ".tooltip").formatted(Formatting.AQUA));
						tooltip.add(Text.literal("GA+ • проверка выполняется сервером").formatted(Formatting.DARK_GRAY));
					}
				});
	}

	public static final Item SELECTION_BRUSH = Registry.register(Registries.ITEM,
			new Identifier(AdminControlMod.MOD_ID, "selection_brush"),
			new Item(new Item.Settings().maxCount(1).rarity(Rarity.RARE)) {
				@Override
				public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
					tooltip.add(Text.translatable("item.admin_control.selection_brush.tooltip")
							.formatted(Formatting.AQUA));
				}
			});
	public static final Item PLAYER_MARKER = cwTool("player_marker");
	public static final Item NPC_MARKER = cwTool("npc_marker");
	public static final Item STORY_TABLET = cwTool("story_tablet");
	public static final Item NPC_PATH_TOOL = cwTool("npc_path_tool");
	public static final Item TEMPLATE_TELEPORTER = cwTool("template_teleporter");
	public static final Item TEST_TELEPORTER = cwTool("test_teleporter");
	public static final Item NPC_POSE_TOOL = cwTool("npc_pose_tool");
	public static final Item BUILDER_MARKER = cwTool("builder_marker");
	public static final Item STORY_SAVE_TOKEN = Registry.register(Registries.ITEM,new Identifier(AdminControlMod.MOD_ID,"story_save_token"),new Item(new Item.Settings().maxCount(16).rarity(Rarity.RARE)));
	public static final Item STORY_RESPAWN_TOKEN = Registry.register(Registries.ITEM,new Identifier(AdminControlMod.MOD_ID,"story_respawn_token"),new Item(new Item.Settings().maxCount(16).rarity(Rarity.RARE)));
	public static final List<Item> CW_TOOLS = List.of(SELECTION_BRUSH, PLAYER_MARKER, NPC_MARKER,
			STORY_TABLET, NPC_PATH_TOOL, TEMPLATE_TELEPORTER, TEST_TELEPORTER, NPC_POSE_TOOL, BUILDER_MARKER, STORY_SAVE_TOKEN, STORY_RESPAWN_TOKEN);
	public static final Item WORLD_COIN = Registry.register(Registries.ITEM,
			new Identifier(AdminControlMod.MOD_ID, "world_coin"),
			new WorldCoinItem(new Item.Settings().maxCount(64).rarity(Rarity.UNCOMMON)));

	/** Монеты валют дерева навыков: admin_control:skill_coin_&lt;id валюты&gt;. */
	public static final List<SkillCoinItem> SKILL_COINS;

	static {
		List<SkillCoinItem> coins = new ArrayList<>();
		for (SkillTree.Currency currency : SkillTree.CURRENCIES) {
			coins.add(Registry.register(Registries.ITEM,
					new Identifier(AdminControlMod.MOD_ID, "skill_coin_" + currency.id()),
					new SkillCoinItem(currency, new Item.Settings().maxCount(64).rarity(Rarity.UNCOMMON))));
		}
		SKILL_COINS = Collections.unmodifiableList(coins);
	}

	public static final Item CASINO_MACHINE = Registry.register(Registries.ITEM,
			new Identifier(AdminControlMod.MOD_ID, "casino_machine"),
			new BlockItem(ModBlocks.CASINO_MACHINE, new Item.Settings().maxCount(1).rarity(Rarity.RARE)) {
				@Override
				public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
					NbtCompound nbt = BlockItem.getBlockEntityNbt(stack);
					if (nbt != null && nbt.getBoolean("Broken")) {
						tooltip.add(Text.literal("Сломан — нужен ремонт").formatted(Formatting.RED));
					}
				}
			});

	/** Инструменты ремонта автомата; ключ — вид инструмента. */
	public static final Map<RepairTool, RepairToolItem> REPAIR_TOOLS;

	static {
		Map<RepairTool, RepairToolItem> tools = new EnumMap<>(RepairTool.class);
		for (RepairTool tool : RepairTool.values()) {
			tools.put(tool, Registry.register(Registries.ITEM,
					new Identifier(AdminControlMod.MOD_ID, tool.id()),
					new RepairToolItem(tool, new Item.Settings().maxDamage(128))));
		}
		REPAIR_TOOLS = Collections.unmodifiableMap(tools);
	}

	public static final ProteinBarItem PROTEIN_BAR_CHOCOLATE = bar("protein_bar_chocolate", "Шоколад", 0.1);
	public static final ProteinBarItem PROTEIN_BAR_VANILLA = bar("protein_bar_vanilla", "Ваниль", 0.2);
	public static final ProteinBarItem PROTEIN_BAR_BERRY = bar("protein_bar_berry", "Ягоды", 0.3);
	public static final ProteinBarItem PROTEIN_BAR_CARAMEL = bar("protein_bar_caramel", "Карамель", 0.4);
	public static final ProteinBarItem PROTEIN_BAR_PEANUT = bar("protein_bar_peanut", "Арахис", 0.5);

	public static final ProteinShakeItem PROTEIN_SHAKE_MILK = shake("protein_shake_milk", "Молочный", 0.25, 180);
	public static final ProteinShakeItem PROTEIN_SHAKE_BANANA = shake("protein_shake_banana", "Банановый", 0.35, 180);
	public static final ProteinShakeItem PROTEIN_SHAKE_COFFEE = shake("protein_shake_coffee", "Кофейный", 0.50, 240);

	public static final List<ProteinBarItem> PROTEIN_BARS = List.of(
			PROTEIN_BAR_CHOCOLATE, PROTEIN_BAR_VANILLA, PROTEIN_BAR_BERRY, PROTEIN_BAR_CARAMEL, PROTEIN_BAR_PEANUT);
	public static final List<ProteinShakeItem> PROTEIN_SHAKES = List.of(
			PROTEIN_SHAKE_MILK, PROTEIN_SHAKE_BANANA, PROTEIN_SHAKE_COFFEE);

	public static final SpecialRecipeSerializer<ProteinMixRecipe> PROTEIN_MIX_SERIALIZER = Registry.register(
			Registries.RECIPE_SERIALIZER, new Identifier(AdminControlMod.MOD_ID, "protein_mix"),
			new SpecialRecipeSerializer<>(ProteinMixRecipe::new));

	/** Вкладка креатива «WorldCoin»: World coin, коины дерева навыков, игровой автомат и инструменты ремонта. */
	public static final ItemGroup WORLDCOIN_GROUP = Registry.register(Registries.ITEM_GROUP,
			new Identifier(AdminControlMod.MOD_ID, "worldcoin"),
			FabricItemGroup.builder()
					.icon(() -> new ItemStack(WORLD_COIN))
					.displayName(Text.translatable("itemGroup.admin_control.worldcoin"))
					.entries((context, entries) -> {
						entries.add(WORLD_COIN);
						for (SkillCoinItem coin : SKILL_COINS) {
							entries.add(coin);
						}
						entries.add(CASINO_MACHINE);
						for (RepairToolItem tool : REPAIR_TOOLS.values()) {
							entries.add(tool);
						}
						for (ProteinBarItem bar : PROTEIN_BARS) {
							entries.add(bar);
						}
						for (ProteinShakeItem shake : PROTEIN_SHAKES) {
							entries.add(shake);
						}
					})
					.build());

	/** Отдельная вкладка инструментов разработки CW. Сервер всё равно повторно проверяет доступ. */
	public static final ItemGroup CW_EXPEDITION_GROUP = Registry.register(Registries.ITEM_GROUP,
			new Identifier(AdminControlMod.MOD_ID, "cw_expedition"),
			FabricItemGroup.builder()
					.icon(() -> new ItemStack(STORY_TABLET))
					.displayName(Text.translatable("itemGroup.admin_control.cw_expedition"))
					.entries((context, entries) -> CW_TOOLS.forEach(entries::add))
					.build());

	private ModItems() {
	}

	public static boolean isCwTool(ItemStack stack) {
		return stack != null && !stack.isEmpty() && CW_TOOLS.contains(stack.getItem());
	}

	private static ProteinBarItem bar(String id, String flavor, double power) {
		return Registry.register(Registries.ITEM, new Identifier(AdminControlMod.MOD_ID, id),
				new ProteinBarItem(flavor, power, new Item.Settings().maxCount(16).rarity(Rarity.UNCOMMON)));
	}

	private static ProteinShakeItem shake(String id, String kind, double bonus, int seconds) {
		return Registry.register(Registries.ITEM, new Identifier(AdminControlMod.MOD_ID, id),
				new ProteinShakeItem(kind, bonus, seconds, new Item.Settings().maxCount(8).rarity(Rarity.UNCOMMON)));
	}

	public static void register() {
	}
}
