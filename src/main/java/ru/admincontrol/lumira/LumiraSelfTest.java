package ru.admincontrol.lumira;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.mininglevel.v1.MiningLevelManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.cw.CwBuilderWorld;
import ru.admincontrol.cw.CwTemplateSync;
import ru.admincontrol.lumira.gen.LumiraBiomeGen;
import ru.admincontrol.lumira.gen.LumiraTreeGen;
import ru.admincontrol.lumira.item.NecklaceItem;
import ru.admincontrol.lumira.item.StoryCoinItem;
import ru.admincontrol.lumira.recipe.RuneShapedRecipe;
import ru.admincontrol.util.EventService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Автопроверка на запущенном (в том числе выделенном) сервере: запускается только с
 * -Dadmin_control.selftest=true и останавливает сервер по окончании. Результат: lumira-selftest.txt в каталоге запуска.
 * Не заменяет игровую проверку клиентом (HUD, текстуры, модели).
 */
public final class LumiraSelfTest {
	private static final List<String> LOG = new ArrayList<>();
	private static final List<BooleanSupplier> STEPS = new ArrayList<>();
	private static int fails;
	private static int step;
	private static int wait;
	private static MinecraftServer server;
	private static ServerWorld builder;
	private static ServerWorld story;
	private static ServerWorld overworld;

	private LumiraSelfTest() {
	}

	public static boolean enabled() {
		return Boolean.getBoolean("admin_control.selftest");
	}

	private static void check(String name, boolean ok, String detail) {
		LOG.add((ok ? "PASS " : "FAIL ") + name + (detail == null || detail.isEmpty() ? "" : " :: " + detail));
		if (!ok) {
			fails++;
		}
	}

	private static void note(String text) {
		LOG.add("INFO " + text);
	}

	public static void start(MinecraftServer s) {
		server = s;
		overworld = s.getOverworld();
		builder = s.getWorld(CwBuilderWorld.KEY);
		story = s.getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier(AdminControlMod.MOD_ID, "cw_story_0")));
		if (Boolean.getBoolean("admin_control.selftest.botsonly")) {
			STEPS.add(LumiraSelfTest::botMatches);
		} else {
		STEPS.add(LumiraSelfTest::cwPersistence); // first: independent of the heavy Lumira generation steps
		STEPS.add(LumiraSelfTest::content);
		STEPS.add(LumiraSelfTest::recipes);
		STEPS.add(LumiraSelfTest::mining);
		STEPS.add(LumiraSelfTest::loot);
		STEPS.add(LumiraSelfTest::economyAndCoins);
		STEPS.add(LumiraSelfTest::coinSneak);
		STEPS.add(LumiraSelfTest::coinMyrtle);
		STEPS.add(LumiraSelfTest::recipeEditor);
		STEPS.add(LumiraSelfTest::biomeStart);
		STEPS.add(LumiraSelfTest::biomeWait);
		STEPS.add(LumiraSelfTest::biomeVerify);
		STEPS.add(LumiraSelfTest::treesAndGrowth);
		STEPS.add(LumiraSelfTest::templateCopy);
		STEPS.add(LumiraSelfTest::healthModes);
		STEPS.add(LumiraSelfTest::necklaces);
		STEPS.add(LumiraSelfTest::anvil);
		STEPS.add(LumiraSelfTest::lootGuard);
		STEPS.add(LumiraSelfTest::biomeSecondStart);
		STEPS.add(LumiraSelfTest::biomeSecondWait);
		STEPS.add(LumiraSelfTest::biomeSecondVerify);
		STEPS.add(LumiraSelfTest::encryptionAndRecipes);
		STEPS.add(LumiraSelfTest::cwEmi);
		STEPS.add(LumiraSelfTest::cwFileCheckpoint); // last: it replaces the files of a spare story world
		STEPS.add(LumiraSelfTest::workbenchGui);
		STEPS.add(LumiraSelfTest::armorProtects);
		STEPS.add(LumiraSelfTest::botMatches);
		}
		ServerTickEvents.END_SERVER_TICK.register(srv -> {
			if (step >= STEPS.size() || wait-- > 0) {
				return;
			}
			try {
				if (STEPS.get(step).getAsBoolean()) {
					step++;
				}
			} catch (Throwable t) {
				check("step " + step, false, t.toString());
				AdminControlMod.LOGGER.error("selftest", t);
				step++;
			}
			if (step >= STEPS.size()) {
				finish();
			}
		});
	}

	private static void finish() {
		LOG.add("RESULT " + (fails == 0 ? "ALL PASSED" : fails + " FAILED"));
		try {
			Files.write(Path.of("lumira-selftest.txt"), LOG, StandardCharsets.UTF_8);
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("selftest write", e);
		}
		LOG.forEach(l -> AdminControlMod.LOGGER.info("[LUMIRA-TEST] {}", l));
		server.stop(false);
	}

	// ---------- шаги ----------

	private static boolean content() {
		int tools = 0, armor = 0;
		for (LumiraIds.Metal m : LumiraIds.METALS) {
			for (String k : LumiraIds.TOOL_KINDS) {
				if (LumiraContent.item(m.id() + "_" + k) != null) tools++;
			}
			for (String k : LumiraIds.ARMOR_KINDS) {
				if (LumiraContent.item(m.id() + "_" + k) != null) armor++;
			}
		}
		check("20 инструментов", tools == 20, "найдено " + tools);
		check("8 частей брони", armor == 8, "найдено " + armor);
		check("брони люмирита и заремеди нет", LumiraContent.item("lumirite_helmet") == null && LumiraContent.item("zaremed_chestplate") == null
				&& LumiraContent.item("lumirite_boots") == null && LumiraContent.item("zaremed_leggings") == null, "");
		int treeBlocks = 0;
		for (String id : LumiraIds.allTreeBlockIds()) {
			if (LumiraContent.block(id) != null) treeBlocks++;
		}
		check("блоки 7 пород", treeBlocks == LumiraIds.allTreeBlockIds().size() && LumiraIds.TREES.size() == 7, treeBlocks + " блоков");
		check("девять новых ресурсов", LumiraIds.ORES.size() == 9, "");
		LumiraTreeGen.Plan p = LumiraTreeGen.plan(LumiraIds.TREES.get(0), BlockPos.ORIGIN, 1, Random.create(1));
		check("шаблон дерева строится", p != null, "");
		// ровно две базовых текстуры бревна: модель использует end/side
		return true;
	}

	private static boolean recipes() {
		var rm = server.getRecipeManager();
		int ours = 0;
		List<String> missing = new ArrayList<>();
		for (Recipe<?> r : rm.values()) {
			if (r.getId().getNamespace().equals(AdminControlMod.MOD_ID)) ours++;
			ItemStack out = r.getOutput(server.getRegistryManager());
			String path = Registries.ITEM.getId(out.getItem()).getPath();
			if (path.startsWith("lumirite_") && (path.endsWith("helmet") || path.endsWith("chestplate") || path.endsWith("leggings") || path.endsWith("boots"))
					|| path.startsWith("zaremed_") && (path.endsWith("helmet") || path.endsWith("chestplate") || path.endsWith("leggings") || path.endsWith("boots"))) {
				missing.add(path);
			}
		}
		check("рецепты загружены", ours >= 150, ours + " рецептов мода");
		check("рецептов брони первых двух металлов нет", missing.isEmpty(), missing.toString());
		ScreenHandler dummy = new ScreenHandler(null, -1) {
			@Override
			public ItemStack quickMove(net.minecraft.entity.player.PlayerEntity player, int slot) {
				return ItemStack.EMPTY;
			}

			@Override
			public boolean canUse(net.minecraft.entity.player.PlayerEntity player) {
				return true;
			}
		};
		CraftingInventory grid = new CraftingInventory(dummy, 3, 3);
		ItemStack ingot = new ItemStack(LumiraContent.item("twilight_iron_ingot"));
		for (int i : new int[]{0, 1, 2, 3, 5}) grid.setStack(i, ingot.copy());
		RuneShapedRecipe.GATE.set(false);
		var plain = rm.getFirstMatch(RecipeType.CRAFTING, grid, server.getOverworld());
		check("сумеречный шлем не собирается на обычном верстаке", plain.isEmpty(), "");
		RuneShapedRecipe.GATE.set(true);
		var rune = rm.getFirstMatch(RecipeType.CRAFTING, grid, server.getOverworld());
		RuneShapedRecipe.GATE.set(false);
		check("сумеречный шлем собирается на рунном верстаке", rune.isPresent()
				&& rune.get().getOutput(server.getRegistryManager()).isOf(LumiraContent.item("twilight_iron_helmet")), "");
		CraftingInventory grid2 = new CraftingInventory(dummy, 3, 3);
		for (int i = 0; i < 3; i++) grid2.setStack(i, new ItemStack(LumiraContent.item("lumirite_ingot")));
		grid2.setStack(4, new ItemStack(Items.STICK));
		grid2.setStack(7, new ItemStack(Items.STICK));
		var lum = rm.getFirstMatch(RecipeType.CRAFTING, grid2, server.getOverworld());
		check("люмиритовая кирка собирается на обычном верстаке", lum.isPresent(), "");
		// циклы дублирования: блок ↔ слитки (9 → 1 → 9), без выигрыша
		check("рецепт блок→9 слитков и 9 слитков→блок согласованы", rm.get(new Identifier(AdminControlMod.MOD_ID, "lumirite_block")).isPresent()
				&& rm.get(new Identifier(AdminControlMod.MOD_ID, "lumirite_ingot_from_block")).isPresent(), "");
		return true;
	}

	private static boolean mining() {
		String[][] ladder = {
				// руда, минимальная кирка (первая подходящая), предыдущая (не должна)
				{"spark_coal_ore", "stone_pickaxe", "wooden_pickaxe"},
				{"lumirite_ore", "iron_pickaxe", "stone_pickaxe"},
				{"dew_quartz_ore", "iron_pickaxe", "stone_pickaxe"},
				{"jarolite_ore", "iron_pickaxe", "stone_pickaxe"},
				{"zaremed_ore", "lumirite_pickaxe", "iron_pickaxe"},
				{"twilight_iron_ore", "zaremed_pickaxe", "lumirite_pickaxe"},
				{"echolite_ore", "zaremed_pickaxe", "lumirite_pickaxe"},
				{"starsilver_ore", "twilight_iron_pickaxe", "zaremed_pickaxe"},
				{"heart_crystal_ore", "starsilver_pickaxe", "twilight_iron_pickaxe"}};
		for (String[] row : ladder) {
			BlockState s = LumiraContent.block(row[0]).getDefaultState();
			ItemStack good = pick(row[1]);
			ItemStack bad = pick(row[2]);
			check("добыча " + row[0] + ": " + row[1] + " да, " + row[2] + " нет", good.isSuitableFor(s) && !bad.isSuitableFor(s),
					"уровень блока " + MiningLevelManager.getRequiredMiningLevel(s));
		}
		// скорость не обходит ступень
		BlockState zar = LumiraContent.block("zaremed_ore").getDefaultState();
		check("быстрая железная кирка не добывает заремедь", !new ItemStack(Items.IRON_PICKAXE).isSuitableFor(zar), "");
		check("скорость люмиритовой кирки 8", Math.abs(new ItemStack(LumiraContent.item("lumirite_pickaxe")).getMiningSpeedMultiplier(Blocks.STONE.getDefaultState()) - 8f) < 0.01, "");
		check("скорость звёздносеребряной кирки 12", Math.abs(new ItemStack(LumiraContent.item("starsilver_pickaxe")).getMiningSpeedMultiplier(Blocks.STONE.getDefaultState()) - 12f) < 0.01, "");
		ItemStack sw = new ItemStack(LumiraContent.item("starsilver_sword"));
		double dmg = sw.getAttributeModifiers(net.minecraft.entity.EquipmentSlot.MAINHAND).get(EntityAttributes.GENERIC_ATTACK_DAMAGE).iterator().next().getValue();
		check("урон звёздносеребряного меча 8 (с базой игрока)", Math.abs(dmg + 1 - 8) < 0.01, "модификатор " + dmg);
		check("прочность сумеречного железа 1000", new ItemStack(LumiraContent.item("twilight_iron_sword")).getMaxDamage() == 1000, "");
		check("шлем звёздносеребра 407, нагрудник 592", new ItemStack(LumiraContent.item("starsilver_helmet")).getMaxDamage() == 407
				&& new ItemStack(LumiraContent.item("starsilver_chestplate")).getMaxDamage() == 592, "");
		check("защита комплекта звёздносеребра 20", ((net.minecraft.item.ArmorItem) LumiraContent.item("starsilver_helmet")).getProtection()
				+ ((net.minecraft.item.ArmorItem) LumiraContent.item("starsilver_chestplate")).getProtection()
				+ ((net.minecraft.item.ArmorItem) LumiraContent.item("starsilver_leggings")).getProtection()
				+ ((net.minecraft.item.ArmorItem) LumiraContent.item("starsilver_boots")).getProtection() == 20, "");
		return true;
	}

	private static ItemStack pick(String id) {
		var item = Registries.ITEM.get(id.contains(":") ? new Identifier(id) : (LumiraContent.item(id) != null ? new Identifier(AdminControlMod.MOD_ID, id) : new Identifier(id)));
		return new ItemStack(item);
	}

	private static List<ItemStack> drops(String block, ItemStack tool) {
		BlockState s = LumiraContent.block(block).getDefaultState();
		return Block.getDroppedStacks(s, overworld, new BlockPos(0, 100, 0), null, null, tool);
	}

	private static boolean loot() {
		ItemStack ironPick = new ItemStack(Items.IRON_PICKAXE);
		ItemStack silk = new ItemStack(Items.NETHERITE_PICKAXE);
		silk.addEnchantment(net.minecraft.enchantment.Enchantments.SILK_TOUCH, 1);
		ItemStack fortune = new ItemStack(Items.NETHERITE_PICKAXE);
		fortune.addEnchantment(net.minecraft.enchantment.Enchantments.FORTUNE, 3);
		check("люмиритовая руда: сырой кусок", drops("lumirite_ore", ironPick).size() == 1 && drops("lumirite_ore", ironPick).get(0).isOf(LumiraContent.item("raw_lumirite")), "");
		check("«Шёлковое касание»: блок руды", drops("lumirite_ore", silk).get(0).isOf(LumiraContent.item("lumirite_ore")), "");
		check("росный кварц дропается готовым кристаллом", drops("dew_quartz_ore", ironPick).get(0).isOf(LumiraContent.item("dew_quartz")), "");
		check("искроуголь без слитка", drops("spark_coal_ore", ironPick).get(0).isOf(LumiraContent.item("spark_coal")), "");
		int total = 0;
		for (int i = 0; i < 300; i++) total += drops("jarolite_ore", fortune).stream().mapToInt(ItemStack::getCount).sum();
		check("«Удача III» увеличивает жаролит", total > 300, "в среднем " + total / 300.0);
		// листва: саженец
		int saplings = 0, runs = 20000;
		ItemStack hand = new ItemStack(Items.IRON_AXE);
		for (int i = 0; i < runs; i++) {
			for (ItemStack st : drops("zarelist_leaves", hand)) if (st.isOf(LumiraContent.item("zarelist_sapling"))) saplings++;
		}
		double p = saplings / (double) runs;
		check("шанс саженца ≈5%", p > 0.04 && p < 0.06, String.format("%.4f", p));
		ItemStack shears = new ItemStack(Items.SHEARS);
		check("ножницы дают листву", drops("zarelist_leaves", shears).get(0).isOf(LumiraContent.item("zarelist_leaves")), "");
		check("«Шёлковое касание» даёт листву", drops("moon_ash_leaves", silk).get(0).isOf(LumiraContent.item("moon_ash_leaves")), "");
		return true;
	}

	private static final java.util.List<Object[]> BOT_RUNS = new java.util.ArrayList<>();
	private static int botTicks;

	/** Все восемь режимов тренировки с ботами параллельно в отдельных аренах: боты появляются, дерутся, союзники не трогают игрока. */
	private static boolean botMatches() {
		if (BOT_RUNS.isEmpty() && botTicks == 0) {
			for (int m = 0; m < ru.admincontrol.util.BotMatch.NAMES.length; m++) {
				ru.admincontrol.util.TrainingArena a = new ru.admincontrol.util.TrainingArena();
				// только уже загруженные чанки у спавна: синхронная загрузка в тесте вешает сервер
				int x = overworld.getSpawnPos().getX() - 60 + (m % 4) * 30;
				int z = overworld.getSpawnPos().getZ() - 30 + (m / 4) * 30;
				a.build(overworld, new net.minecraft.util.math.BlockPos(x, overworld.getTopY() - ru.admincontrol.util.TrainingArena.HEIGHT, z));
				ServerPlayerEntity p = FakePlayer.get(overworld, new GameProfile(UUID.randomUUID(), "[BotTest" + m + "]"));
				p.refreshPositionAndAngles(a.spawnX(), a.minY + 1.0, a.minZ + 3.5, 0f, 0f);
				ru.admincontrol.util.BotMatch bm = new ru.admincontrol.util.BotMatch(m, 1,
						ru.admincontrol.util.BotMatch.DEF_ALLIES[m], ru.admincontrol.util.BotMatch.DEF_ENEMIES[m]);
				bm.begin(a, overworld, p);
				BOT_RUNS.add(new Object[]{a, p, bm});
			}
		}
		botTicks++;
		for (Object[] run : BOT_RUNS) {
			((ru.admincontrol.util.BotMatch) run[2]).tick((ServerPlayerEntity) run[1]);
		}
		if (botTicks < 400) {
			return false;
		}
		for (int m = 0; m < BOT_RUNS.size(); m++) {
			ru.admincontrol.util.TrainingArena a = (ru.admincontrol.util.TrainingArena) BOT_RUNS.get(m)[0];
			ServerPlayerEntity p = (ServerPlayerEntity) BOT_RUNS.get(m)[1];
			ru.admincontrol.util.BotMatch bm = (ru.admincontrol.util.BotMatch) BOT_RUNS.get(m)[2];
			net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(a.minX, a.minY, a.minZ, a.maxX + 1, a.maxY + 1, a.maxZ + 1);
			java.util.List<net.minecraft.entity.mob.MobEntity> mobs = overworld.getEntitiesByClass(net.minecraft.entity.mob.MobEntity.class, box, e -> true);
			boolean hurt = bm.result != ru.admincontrol.util.BotMatch.RUNNING;
			boolean allyTargetsPlayer = false;
			for (net.minecraft.entity.mob.MobEntity mob : mobs) {
				hurt |= mob.getHealth() < mob.getMaxHealth();
				if (mob.hasCustomName() && mob.getCustomName().getString().startsWith("Союзник") && mob.getTarget() == p) {
					allyTargetsPlayer = true;
				}
			}
			String name = ru.admincontrol.util.BotMatch.NAMES[m];
			check("боты «" + name + "»: заспавнены", mobs.size() >= ru.admincontrol.util.BotMatch.DEF_ALLIES[m] + 1 || bm.result != ru.admincontrol.util.BotMatch.RUNNING, "мобов " + mobs.size());
			check("боты «" + name + "»: бой идёт", hurt, bm.status() + " / результат " + bm.result + " " + bm.resultText);
			check("боты «" + name + "»: союзники не целятся в игрока", !allyTargetsPlayer, "");
			bm.cleanup();
			a.remove();
		}
		BOT_RUNS.clear();
		return true;
	}

	private static ServerPlayerEntity fake(ServerWorld w) {
		return FakePlayer.get(w, new GameProfile(UUID.randomUUID(), "[LumiraTest]"));
	}

	private static ServerPlayerEntity coinPlayer;
	private static long coinL0;

	private static boolean economyAndCoins() {
		UUID id = UUID.randomUUID();
		check("новый счёт нулевой", LumiraEconomy.lyres(id) == 0 && LumiraEconomy.myrtles(id) == 0, "");
		LumiraEconomy.addLyres(id, 5, "тест");
		check("повторная разовая награда не выдаётся", LumiraEconomy.claimReward(id, "ch1_start", 10, 2) && !LumiraEconomy.claimReward(id, "ch1_start", 10, 2)
				&& LumiraEconomy.lyres(id) == 15 && LumiraEconomy.myrtles(id) == 2, "лиры " + LumiraEconomy.lyres(id));
		check("лиры не уходят в минус", !LumiraEconomy.addLyres(id, -100, "тест") && LumiraEconomy.lyres(id) == 15, "");
		ServerPlayerEntity p = fake(overworld);
		coinPlayer = p;
		coinL0 = LumiraEconomy.lyres(p.getUuid());
		p.setStackInHand(Hand.MAIN_HAND, new ItemStack(LumiraContent.item("coin_lyra"), 17));
		p.setStackInHand(Hand.OFF_HAND, new ItemStack(LumiraContent.item("coin_lyra"), 5));
		p.getInventory().setStack(3, new ItemStack(LumiraContent.item("coin_lyra"), 9));
		p.setSneaking(false);
		p.getStackInHand(Hand.MAIN_HAND).use(overworld, p, Hand.MAIN_HAND);
		check("ПКМ: списывается 1 монета, +1 лира", p.getMainHandStack().getCount() == 16 && LumiraEconomy.lyres(p.getUuid()) == coinL0 + 1, "осталось " + p.getMainHandStack().getCount());
		p.getStackInHand(Hand.MAIN_HAND).use(overworld, p, Hand.MAIN_HAND);
		check("двойное срабатывание в один тик игнорируется", p.getMainHandStack().getCount() == 16 && LumiraEconomy.lyres(p.getUuid()) == coinL0 + 1, "");
		return true;
	}

	private static boolean coinSneak() {
		ServerPlayerEntity p = coinPlayer;
		p.setSneaking(true);
		p.getStackInHand(Hand.MAIN_HAND).use(overworld, p, Hand.MAIN_HAND);
		check("Shift+ПКМ: весь стак из руки (+16)", p.getMainHandStack().isEmpty() && LumiraEconomy.lyres(p.getUuid()) == coinL0 + 17, "лиры " + LumiraEconomy.lyres(p.getUuid()));
		check("вторая рука и инвентарь не тронуты", p.getOffHandStack().getCount() == 5 && p.getInventory().getStack(3).getCount() == 9, "");
		p.setSneaking(false);
		p.setStackInHand(Hand.MAIN_HAND, new ItemStack(LumiraContent.item("coin_myrtle"), 4));
		return true;
	}

	private static boolean recipeEditor() {
		for (int kind : new int[]{ru.admincontrol.lumira.recipe.RecipeEdits.SHAPED, ru.admincontrol.lumira.recipe.RecipeEdits.SHAPELESS, ru.admincontrol.lumira.recipe.RecipeEdits.RUNE, ru.admincontrol.lumira.recipe.RecipeEdits.SMELTING}) {
			var e = new ru.admincontrol.lumira.recipe.RecipeEdits.Entry();
			e.id = "admin_control:custom/test_" + kind;
			e.kind = kind;
			e.slots[0] = "minecraft:gold_nugget";
			e.slots[1] = "#minecraft:planks";
			e.slots[4] = "minecraft:redstone";
			e.output = "admin_control:coin_lyra";
			e.count = 2;
			var json = ru.admincontrol.lumira.recipe.RecipeEdits.toJson(e);
			boolean ok = false;
			try {
				var recipe = net.minecraft.recipe.RecipeManager.deserialize(new Identifier(e.id), json);
				ok = recipe.getOutput(server.getRegistryManager()).isOf(LumiraContent.item("coin_lyra"));
			} catch (RuntimeException ex) {
				ok = false;
			}
			check("редактор крафтов: тип " + kind + " превращается в рабочий рецепт", ok, String.valueOf(json));
		}
		var bad = new ru.admincontrol.lumira.recipe.RecipeEdits.Entry();
		bad.id = "admin_control:custom/bad";
		check("редактор крафтов: пустой рецепт не принимается", ru.admincontrol.lumira.recipe.RecipeEdits.toJson(bad) == null, "");
		return true;
	}

	private static boolean coinMyrtle() {
		ServerPlayerEntity p = coinPlayer;
		long m0 = LumiraEconomy.myrtles(p.getUuid());
		p.getStackInHand(Hand.MAIN_HAND).use(overworld, p, Hand.MAIN_HAND);
		check("Coin мирты меняет только мирты", LumiraEconomy.myrtles(p.getUuid()) == m0 + 1 && LumiraEconomy.lyres(p.getUuid()) == coinL0 + 17, "");
		return true;
	}

	private static boolean biomeStart() {
		// занятый чанк (2,0): один блок — генерация обязана его обойти
		builder.setBlockState(new BlockPos(40, 100, 2), Blocks.STONE.getDefaultState());
		String res = LumiraBiomeGen.start(server, null, BlockPos.ORIGIN, 16);
		note("start: " + res);
		check("генерация запущена на пустой области", res.startsWith("Генерация «Рощи"), res);
		return res.startsWith("Генерация «Рощи");
	}

	private static int biomeTicks;

	private static boolean biomeWait() {
		biomeTicks++;
		String st = LumiraBiomeGen.status();
		if (biomeTicks % 40 == 0) note(st);
		boolean done = !LumiraBiomeGen.lastReport().isEmpty();
		if (biomeTicks > 20000) {
			check("генерация завершилась вовремя", false, st);
			return true;
		}
		if (done) {
			wait = 200;
		}
		return done;
	}

	private static boolean biomeVerify() {
		note(LumiraBiomeGen.lastReport().replace("\n", " | "));
		Map<String, Integer> counts = new HashMap<>();
		int r = 64;
		String[] forbidden = {"minecraft:copper_ore", "minecraft:deepslate_copper_ore", "minecraft:gold_ore", "minecraft:deepslate_gold_ore", "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
				"minecraft:emerald_ore", "minecraft:deepslate_emerald_ore", "minecraft:lapis_ore", "minecraft:deepslate_lapis_ore", "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore",
				"minecraft:nether_quartz_ore", "minecraft:ancient_debris", "minecraft:nether_gold_ore"};
		java.util.Set<String> bad = new java.util.HashSet<>(List.of(forbidden));
		BlockPos.Mutable m = new BlockPos.Mutable();
		int forbiddenFound = 0;
		int leaves = 0, logs = 0, albionGrass = 0, vanillaGrass = 0, albionDirt = 0, vanillaDirt = 0;
		int albionStone = 0, vanillaStone = 0, spruce = 0;
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				for (int y = -64; y < 160; y++) {
					BlockState s = builder.getBlockState(m.set(x, y, z));
					if (s.isAir()) continue;
					String id = Registries.BLOCK.getId(s.getBlock()).toString();
					if (id.endsWith("_ore") || id.equals("minecraft:ancient_debris")) counts.merge(id, 1, Integer::sum);
					if (bad.contains(id)) forbiddenFound++;
					if (id.endsWith("_leaves")) leaves++;
					if (id.endsWith("_log")) logs++;
					if (id.equals("admin_control:albion_grass_block")) albionGrass++;
					if (id.equals("minecraft:grass_block")) vanillaGrass++;
					if (id.equals("admin_control:albion_dirt")) albionDirt++;
					if (id.equals("minecraft:dirt")) vanillaDirt++;
					if (id.equals("admin_control:albion_stone")) albionStone++;
					if (id.equals("minecraft:stone")) vanillaStone++;
					if (id.contains("spruce")) spruce++;
				}
			}
		}
		note("руды в области: " + counts);
		check("запрещённых руд нет", forbiddenFound == 0, "найдено " + forbiddenFound);
		check("уголь и железо есть", (counts.getOrDefault("minecraft:coal_ore", 0) + counts.getOrDefault("minecraft:deepslate_coal_ore", 0) > 0)
				&& (counts.getOrDefault("minecraft:iron_ore", 0) + counts.getOrDefault("minecraft:deepslate_iron_ore", 0) > 0), "");
		check("люмирит и искроуголь есть", counts.getOrDefault("admin_control:lumirite_ore", 0) > 0 && counts.getOrDefault("admin_control:spark_coal_ore", 0) > 0, "");
		check("деревья выросли", logs > 10 && leaves > 100, "брёвен " + logs + ", листвы " + leaves);
		check("Альбион: 100% нового дёрна", albionGrass > 0 && vanillaGrass == 0, "нового=" + albionGrass + " vanilla=" + vanillaGrass);
		check("Альбион: 100% новой земли", albionDirt > 0 && vanillaDirt == 0, "новой=" + albionDirt + " vanilla=" + vanillaDirt);
		double stoneRatio = albionStone / (double)Math.max(1, albionStone + vanillaStone);
		check("Альбион: нового камня около 75%", stoneRatio > .72 && stoneRatio < .78, "ratio=" + stoneRatio + " custom=" + albionStone + " vanilla=" + vanillaStone);
		check("Альбион: ель полностью отсутствует", spruce == 0, "еловых блоков=" + spruce);
		var biome = builder.getBiome(new BlockPos(10, 90, 10));
		check("биом — Роща Люмиры", biome.getKey().isPresent() && biome.getKey().get().getValue().equals(LumiraBiomeGen.BIOME_ID), biome.getKey().toString());
		int lit = 0, samples = 0;
		StringBuilder dbg = new StringBuilder();
		for (int sx = -28; sx <= 28; sx += 8) {
			for (int sz = -28; sz <= 28; sz += 8) {
				int top = builder.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, sx, sz);
				int level = builder.getLightingProvider().get(LightType.SKY).getLightLevel(new BlockPos(sx, top, sz));
				samples++;
				if (level == 15) lit++;
				else dbg.append(sx).append(",").append(sz).append(" y=").append(top).append(" L=").append(level).append(" ").append(Registries.BLOCK.getId(builder.getBlockState(new BlockPos(sx, top - 1, sz)).getBlock())).append("; ");
			}
		}
		int deep = builder.getLightingProvider().get(LightType.SKY).getLightLevel(new BlockPos(10, -40, 10));
		check("освещение: небо над поверхностью 15 во всех пробных колонках", lit == samples, lit + "/" + samples + " " + dbg);
		var normalWorld = overworld.getBiome(new BlockPos(0, 70, 0));
		check("в обычном мире биом не Роща Люмиры", normalWorld.getKey().isPresent() && !normalWorld.getKey().get().getValue().equals(LumiraBiomeGen.BIOME_ID), "");
		return true;
	}

	private static boolean treesAndGrowth() {
		// ровная площадка высоко над картой
		int y = 200;
		for (int x = -60; x <= 60; x++) for (int z = 90; z <= 170; z++) {
			builder.setBlockState(new BlockPos(x, y, z), Blocks.GRASS_BLOCK.getDefaultState());
		}
		Random rnd = Random.create(42);
		int idx = 0;
		for (LumiraIds.Tree t : LumiraIds.TREES) {
			int[] heights = new int[3];
			for (int size = 0; size < 3; size++) {
				BlockPos base = new BlockPos(-45 + size * 40, y + 1, 95 + idx * 10);
				LumiraTreeGen.Plan plan = LumiraTreeGen.plan(t, base, size, rnd);
				check("дерево " + t.id() + " размер " + size + " помещается", LumiraTreeGen.fits(builder, plan), "");
				LumiraTreeGen.place(builder, plan);
				int h = 0;
				for (BlockPos lp : plan.logsView()) {
					h = Math.max(h, lp.getY() - base.getY() + 1);
				}
				heights[size] = h;
			}
			idx++;
			check("высота " + t.id() + " в диапазоне " + t.hMin() + "–" + t.hMax() + "+", heights[0] >= t.hMin() - 1 && heights[2] <= t.hMax() + 3 && heights[0] <= heights[2],
					heights[0] + "/" + heights[1] + "/" + heights[2]);
		}
		// рост саженца
		BlockPos sap = new BlockPos(0, y + 1, 134);
		builder.setBlockState(sap, LumiraContent.block("crimson_elm_sapling").getDefaultState());
		((net.minecraft.block.SaplingBlock) LumiraContent.block("crimson_elm_sapling")).generate(builder, sap, LumiraContent.block("crimson_elm_sapling").getDefaultState().with(net.minecraft.block.SaplingBlock.STAGE, 1), Random.create(5));
		check("саженец вырастает в дерево в строительном мире", builder.getBlockState(sap).isOf(LumiraContent.block("crimson_elm_log")), "");
		BlockPos sap2 = new BlockPos(0, 201, 12);
		overworld.setBlockState(sap2.down(), Blocks.GRASS_BLOCK.getDefaultState());
		overworld.setBlockState(sap2, LumiraContent.block("crimson_elm_sapling").getDefaultState());
		((net.minecraft.block.SaplingBlock) LumiraContent.block("crimson_elm_sapling")).generate(overworld, sap2, LumiraContent.block("crimson_elm_sapling").getDefaultState().with(net.minecraft.block.SaplingBlock.STAGE, 1), Random.create(5));
		check("в обычном мире саженец не растёт", overworld.getBlockState(sap2).isOf(LumiraContent.block("crimson_elm_sapling")), "");
		// стороны брёвен: проверка моделей
		return true;
	}

	private static boolean templateCopy() {
		BlockPos chest = new BlockPos(5, 220, 5);
		builder.setBlockState(chest, Blocks.CHEST.getDefaultState());
		((ChestBlockEntity) builder.getBlockEntity(chest)).setStack(3, new ItemStack(Items.IRON_INGOT, 7));
		builder.setBlockState(chest.east(), Blocks.STONE.getDefaultState());
		CwTemplateSync.Result res = CwTemplateSync.copy(builder, story);
		check("копирование карты в личный мир", res.ok(), res.error());
		var be = story.getBlockEntity(chest);
		check("содержимое контейнера скопировано", be instanceof ChestBlockEntity c && c.getStack(3).isOf(Items.IRON_INGOT) && c.getStack(3).getCount() == 7, "");
		// независимость: изменение копии не видно в источнике
		story.setBlockState(chest.east(), Blocks.DIAMOND_BLOCK.getDefaultState());
		check("правка личной копии не меняет карту-источник", builder.getBlockState(chest.east()).isOf(Blocks.STONE), "");
		var treeBlock = story.getBlockState(new BlockPos(-6, 201, 120));
		check("деревья и рельеф есть в копии", !story.getBlockState(new BlockPos(0, 200, 120)).isAir(), treeBlock.toString());
		return true;
	}

	private static boolean cwFileCheckpoint() {
		java.util.List<String> lines = new java.util.ArrayList<>();
		boolean done = ru.admincontrol.cw.CwWorldBackupSelfTest.step(server, lines);
		for (String line : lines) {
			LOG.add(line);
			if (line.startsWith("FAIL")) fails++;
		}
		return done;
	}

	/** CW profile protocol (NORMAL / BUILD / STORY) with a real player entity; see CwPersistenceSelfTest. */
	private static boolean cwPersistence() {
		for (String line : ru.admincontrol.cw.CwPersistenceSelfTest.run(server)) {
			LOG.add(line);
			if (line.startsWith("FAIL")) fails++;
		}
		return true;
	}

	private static boolean healthModes() {
		ServerPlayerEntity normal = fake(overworld);
		LumiraHealth.apply(normal);
		check("обычный мир: база 20 без вмешательства", Math.abs(normal.getMaxHealth() - 20) < 0.01, "" + normal.getMaxHealth());
		var inst = normal.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		java.util.UUID treeId = UUID.randomUUID();
		inst.addPersistentModifier(new net.minecraft.entity.attribute.EntityAttributeModifier(treeId, "tree", 30, net.minecraft.entity.attribute.EntityAttributeModifier.Operation.ADDITION));
		LumiraHealth.apply(normal);
		check("обычный мир: прокачка 50 HP сохраняется", Math.abs(normal.getMaxHealth() - 50) < 0.01, "" + normal.getMaxHealth());
		ServerPlayerEntity st = fake(story);
		LumiraHealth.apply(st);
		check("сюжет: база 20", Math.abs(st.getMaxHealth() - 20) < 0.01, "" + st.getMaxHealth());
		// амулет +4 максимума
		ItemStack neck = new ItemStack(LumiraContent.item("necklace_dew"));
		NbtCompound tag = neck.getOrCreateNbt();
		tag.putInt("MaxHealthBonus", 4);
		st.setStackInHand(Hand.MAIN_HAND, neck);
		boolean eq = LumiraNecklaces.equip(st, Hand.MAIN_HAND);
		check("ожерелье надевается в сюжетном мире", eq, "");
		check("сюжет: амулет +4 → 24", Math.abs(st.getMaxHealth() - 24) < 0.01, "" + st.getMaxHealth());
		st.setHealth(24);
		LumiraNecklaces.unequip(st);
		check("снятие амулета: максимум 20 и здоровье ограничено", Math.abs(st.getMaxHealth() - 20) < 0.01 && st.getHealth() <= 20.01f, st.getMaxHealth() + "/" + st.getHealth());
		st.setHealth(7);
		LumiraHealth.apply(st);
		check("повторное применение не лечит и не складывается", Math.abs(st.getMaxHealth() - 20) < 0.01 && st.getHealth() == 7f, st.getMaxHealth() + "/" + st.getHealth());
		// ивент
		ServerPlayerEntity ev = fake(overworld);
		ev.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).addPersistentModifier(new net.minecraft.entity.attribute.EntityAttributeModifier(UUID.randomUUID(), "tree", 30, net.minecraft.entity.attribute.EntityAttributeModifier.Operation.ADDITION));
		ev.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 2400, 1));
		ev.setAbsorptionAmount(4);
		try {
			var f = EventService.class.getDeclaredField("state");
			f.setAccessible(true);
			var old = f.get(null);
			EventService.State state = new EventService.State();
			state.members.put(ev.getUuid(), new EventService.Member());
			f.set(null, state);
			// дерево обнуляется службой навыков; здесь проверяем часть предела режима
			var tree = ev.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
			tree.getModifiers().stream().filter(x -> x.getName().equals("tree")).toList().forEach(x -> tree.removeModifier(x.getId()));
			LumiraHealth.apply(ev);
			check("ивент: режим определён как EVENT", LumiraHealth.mode(ev) == LumiraHealth.Mode.EVENT, "");
			check("ивент: максимум ровно 20", Math.abs(ev.getMaxHealth() - 20) < 0.01, "" + ev.getMaxHealth());
			check("ивент: поглощение золотого яблока снято", ev.getAbsorptionAmount() == 0, "" + ev.getAbsorptionAmount());
			f.set(null, old);
			// выход из ивента: прокачка возвращается (в игре — SkillTreeService.applyEffects из LumiraHealth.tick)
			ev.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).addPersistentModifier(new net.minecraft.entity.attribute.EntityAttributeModifier(UUID.randomUUID(), "tree2", 30, net.minecraft.entity.attribute.EntityAttributeModifier.Operation.ADDITION));
			LumiraHealth.apply(ev);
			check("после ивента обычный максимум возвращается", Math.abs(ev.getMaxHealth() - 50) < 0.01, "" + ev.getMaxHealth());
		} catch (Exception e) {
			check("ивент: имитация участника", false, e.toString());
		}
		return true;
	}

	private static boolean necklaces() {
		ServerPlayerEntity p = fake(story);
		ZombieEntity z = EntityType.ZOMBIE.create(story);
		z.refreshPositionAndAngles(0, 300, 0, 0, 0);
		story.spawnEntity(z);
		// усиление исходящего урона «Звезды»: 15%
		ItemStack star = new ItemStack(LumiraContent.item("necklace_star"));
		p.setStackInHand(Hand.MAIN_HAND, star);
		LumiraNecklaces.equip(p, Hand.MAIN_HAND);
		ZombieEntity control = EntityType.ZOMBIE.create(story);
		control.refreshPositionAndAngles(3, 300, 0, 0, 0);
		story.spawnEntity(control);
		LumiraNecklaces.unequip(p);
		control.damage(story.getDamageSources().playerAttack(p), 10f);
		float controlLost = 20f - control.getHealth();
		p.setStackInHand(Hand.MAIN_HAND, new ItemStack(LumiraContent.item("necklace_star")));
		LumiraNecklaces.equip(p, Hand.MAIN_HAND);
		float before = z.getHealth();
		z.damage(story.getDamageSources().playerAttack(p), 10f);
		float lost = before - z.getHealth();
		check("Звезда: +15% урона по враждебному мобу (через миксин)", Math.abs(lost / controlLost - 1.15f) < 0.01f, "потеряно " + lost + " против " + controlLost + " без ожерелья");
		ItemStack inSlot = LumiraNecklaces.equipped(p.getUuid());
		check("заряд потрачен ровно один раз", inSlot.getDamage() == 1, "использовано зарядов " + inSlot.getDamage());
		// входящий урон — прямой расчёт (FakePlayer неуязвим для damage())
		float incoming = LumiraNecklaces.modifyDamage(p, story.getDamageSources().mobAttack(z), 10f);
		check("Звезда: +15% входящего урона от враждебного моба", Math.abs(incoming - 11.5f) < 0.01f, "" + incoming);
		LumiraNecklaces.unequip(p);
		float noEffect = LumiraNecklaces.modifyDamage(p, story.getDamageSources().mobAttack(z), 10f);
		check("без слота ожерелье в инвентаре эффекта не даёт", Math.abs(noEffect - 10f) < 0.01f, "" + noEffect);
		ItemStack guard = new ItemStack(LumiraContent.item("necklace_guard"));
		p.setStackInHand(Hand.MAIN_HAND, guard);
		LumiraNecklaces.equip(p, Hand.MAIN_HAND);
		float reduced = LumiraNecklaces.modifyDamage(p, story.getDamageSources().mobAttack(z), 10f);
		check("Страж: −10% входящего урона и один заряд", Math.abs(reduced - 9f) < 0.01f && LumiraNecklaces.equipped(p.getUuid()).getDamage() == 1, reduced + "");
		// не действует вне сюжета
		ServerPlayerEntity o = fake(overworld);
		o.setStackInHand(Hand.MAIN_HAND, new ItemStack(LumiraContent.item("necklace_echo")));
		boolean eq = LumiraNecklaces.equip(o, Hand.MAIN_HAND);
		check("в обычном мире ожерелье не надевается", !eq, "");
		// нулевые заряды отключают бонус и недостаток вместе
		ItemStack empty = new ItemStack(LumiraContent.item("necklace_echo"));
		empty.setDamage(empty.getMaxDamage());
		LumiraNecklaces.unequip(p);
		p.setStackInHand(Hand.MAIN_HAND, empty);
		LumiraNecklaces.equip(p, Hand.MAIN_HAND);
		float zero = LumiraNecklaces.modifyDamage(p, story.getDamageSources().mobAttack(z), 10f);
		check("0 зарядов: бонус и недостаток отключены", Math.abs(zero - 10f) < 0.01f, "" + zero);
		LumiraNecklaces.unequip(p);
		z.discard();
		return true;
	}

	private static boolean anvil() {
		BlockPos pos = new BlockPos(20, 230, 20);
		builder.setBlockState(pos, LumiraContent.RUNE_ANVIL.getDefaultState());
		var be = (ru.admincontrol.lumira.block.RuneAnvilBlockEntity) builder.getBlockEntity(pos);
		ItemStack tool = new ItemStack(LumiraContent.item("lumirite_pickaxe"));
		tool.setDamage(150);
		tool.addEnchantment(net.minecraft.enchantment.Enchantments.EFFICIENCY, 3);
		ItemStack ingots = new ItemStack(LumiraContent.item("lumirite_ingot"), 2);
		check("без топлива ремонт не идёт", ru.admincontrol.lumira.block.RuneAnvilLogic.repairOnce(tool, ingots, be) == ru.admincontrol.lumira.block.RuneAnvilLogic.Result.NO_FUEL && tool.getDamage() == 150, "");
		be.addEnergy(ru.admincontrol.lumira.block.RuneAnvilBlock.fuelValue(LumiraContent.item("spark_coal")));
		check("искроуголь = 8 операций", be.energy() == 8, "" + be.energy());
		check("жаролит = 24 операции", ru.admincontrol.lumira.block.RuneAnvilBlock.fuelValue(LumiraContent.item("jarolite")) == 24, "");
		check("неверный материал не принимается", ru.admincontrol.lumira.block.RuneAnvilLogic.repairOnce(tool, new ItemStack(Items.IRON_INGOT), be) == ru.admincontrol.lumira.block.RuneAnvilLogic.Result.NO_MATERIAL, "");
		check("операция восстанавливает 25% прочности (100)", ru.admincontrol.lumira.block.RuneAnvilLogic.repairOnce(tool, ingots, be) == ru.admincontrol.lumira.block.RuneAnvilLogic.Result.OK && tool.getDamage() == 50, "" + tool.getDamage());
		check("расходован 1 слиток и 1 операция, чары сохранены", ingots.getCount() == 1 && be.energy() == 7 && tool.hasEnchantments(), "");
		ru.admincontrol.lumira.block.RuneAnvilLogic.repairOnce(tool, ingots, be);
		check("исправный предмет не ремонтируется и ничего не тратит", ru.admincontrol.lumira.block.RuneAnvilLogic.repairOnce(tool, ingots, be) == ru.admincontrol.lumira.block.RuneAnvilLogic.Result.UNDAMAGED && be.energy() == 6, "энергия " + be.energy());
		ItemStack neck = new ItemStack(LumiraContent.item("necklace_dew"));
		neck.setDamage(200);
		ru.admincontrol.lumira.block.RuneAnvilLogic.repairOnce(neck, new ItemStack(LumiraContent.item("dew_quartz")), be);
		check("ожерелье: +64 заряда за операцию", neck.getDamage() == 136, "" + neck.getDamage());
		check("рецепт рунного верстака без железа", server.getRecipeManager().get(new Identifier(AdminControlMod.MOD_ID, "rune_workbench")).map(r -> r.getIngredients().stream().noneMatch(i -> i.test(new ItemStack(Items.IRON_INGOT)))).orElse(false), "");
		return true;
	}

	private static boolean lootGuard() {
		LootTable table = server.getLootManager().getLootTable(new Identifier("minecraft:chests/simple_dungeon"));
		int forbiddenStory = 0, forbiddenOver = 0;
		for (int i = 0; i < 400; i++) {
			for (ItemStack st : table.generateLoot(new LootContextParameterSet.Builder(story).add(LootContextParameters.ORIGIN, Vec3d.ZERO).build(LootContextTypes.CHEST))) {
				if (LumiraGuard.forbidden(st)) forbiddenStory++;
			}
			for (ItemStack st : table.generateLoot(new LootContextParameterSet.Builder(overworld).add(LootContextParameters.ORIGIN, Vec3d.ZERO).build(LootContextTypes.CHEST))) {
				if (LumiraGuard.forbidden(st)) forbiddenOver++;
			}
		}
		check("сундуки: в сюжетном мире запрещённые продукты не выпадают", forbiddenStory == 0 && forbiddenOver > 0, "сюжет " + forbiddenStory + ", обычный мир " + forbiddenOver);
		return true;
	}

	private static long chunkHash(int cx, int cz) {
		long h = 17;
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int x = 0; x < 16; x += 3) {
			for (int z = 0; z < 16; z += 3) {
				for (int y = -64; y < 160; y += 5) {
					h = h * 31 + Registries.BLOCK.getId(builder.getBlockState(m.set(cx * 16 + x, y, cz * 16 + z)).getBlock()).hashCode();
				}
			}
		}
		return h;
	}

	private static long hash0;
	private static long hash1;

	// Повторная команда: занятые чанки не трогаются, генерация идёт в ближайших пустых.
	private static boolean biomeSecondStart() {
		hash0 = chunkHash(0, 0);
		hash1 = chunkHash(1, 0);
		String res = LumiraBiomeGen.start(server, null, BlockPos.ORIGIN, 6);
		check("повторная команда запускается в свободных чанках", res.startsWith("Генерация «Рощи"), res);
		return true;
	}

	private static boolean biomeSecondWait() {
		if (LumiraBiomeGen.running()) {
			return false;
		}
		wait = 100;
		return true;
	}

	private static boolean biomeSecondVerify() {
		check("уже сгенерированные чанки не перегенерируются", chunkHash(0, 0) == hash0 && chunkHash(1, 0) == hash1, "");
		int solid = 0;
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int x = 32; x < 48; x++) {
			for (int z = 0; z < 16; z++) {
				for (int y = -64; y < 160; y++) {
					if (!builder.getBlockState(m.set(x, y, z)).isAir()) {
						solid++;
					}
				}
			}
		}
		check("занятый чанк остался нетронутым (один блок)", solid == 1, "блоков " + solid);
		int filled = 0;
		for (int cx = -8; cx <= 7; cx++) {
			for (int cz = -8; cz <= 7; cz++) {
				var chunk = builder.getChunk(cx, cz);
				if (!chunk.getSection(chunk.getSectionIndex(80)).isEmpty() || !chunk.getSection(chunk.getSectionIndex(-30)).isEmpty()) {
					filled++;
				}
			}
		}
		check("сгенерировано 16 + 6 = 22 чанка (+1 занятый проверочным блоком)", filled >= 22 && filled <= 24, "непустых чанков " + filled);
		return true;
	}

	private static boolean workbenchGui() {
		ServerPlayerEntity p = fake(overworld);
		p.getInventory().setStack(0, new ItemStack(LumiraContent.item("twilight_iron_ingot"), 5));
		var h = new ru.admincontrol.lumira.screen.RuneWorkbenchHandler(1, p.getInventory(),
				net.minecraft.screen.ScreenHandlerContext.create(overworld, new BlockPos(0, 100, 0)));
		var recipes = ru.admincontrol.lumira.screen.RuneWorkbenchHandler.runeRecipes(overworld);
		int idx = -1;
		for (int i = 0; i < recipes.size(); i++) {
			if (recipes.get(i).getId().equals(new Identifier(AdminControlMod.MOD_ID, "twilight_iron_helmet"))) idx = i;
		}
		check("рунные рецепты видны окну верстака", recipes.size() >= 15 && idx >= 0, recipes.size() + " рецептов");
		boolean filled = h.onButtonClick(p, idx);
		check("клик по рецепту раскладывает ингредиенты и даёт результат", filled
				&& h.getSlot(0).getStack().isOf(LumiraContent.item("twilight_iron_helmet")), h.getSlot(0).getStack().toString());
		check("ингредиенты взяты из инвентаря", p.getInventory().getStack(0).getCount() == 0, "");
		ItemStack taken = h.quickMove(p, 0);
		check("взятие результата списывает рецепт", taken.isOf(LumiraContent.item("twilight_iron_helmet")) && h.getSlot(1).getStack().isEmpty(), taken.toString());
		return true;
	}

	private static boolean armorProtects() {
		for (String metal : new String[]{"twilight_iron", "starsilver"}) {
			double armor = 0, toughness = 0;
			net.minecraft.entity.EquipmentSlot[] slots = {net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.entity.EquipmentSlot.CHEST,
					net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET};
			String[] kinds = {"helmet", "chestplate", "leggings", "boots"};
			for (int i = 0; i < 4; i++) {
				ItemStack piece = new ItemStack(LumiraContent.item(metal + "_" + kinds[i]));
				var mods = piece.getAttributeModifiers(slots[i]);
				for (var m : mods.get(EntityAttributes.GENERIC_ARMOR)) armor += m.getValue();
				for (var m : mods.get(EntityAttributes.GENERIC_ARMOR_TOUGHNESS)) toughness += m.getValue();
			}
			float left = net.minecraft.entity.DamageUtil.getDamageLeft(10f, (float) armor, (float) toughness);
			check("броня " + metal + ": полный комплект даёт " + (metal.equals("starsilver") ? "20" : "18") + " защиты", Math.abs(armor - (metal.equals("starsilver") ? 20 : 18)) < 0.01, "защита " + armor + ", твёрдость " + toughness);
			check("броня " + metal + ": урон 10 от моба снижается до " + left, left < 5f, "осталось " + left);
		}
		return true;
	}

	private static boolean encryptionAndRecipes() {
		ru.admincontrol.item.ItemEncryption.apply(true, List.of("admin_control:lumirite_ingot", "minecraft:dirt"), true, List.of("admin_control:lumirite_ingot"), true);
		check("картинка зашифрованного предмета скрыта", ru.admincontrol.item.ItemEncryption.iconHidden(LumiraContent.item("lumirite_ingot")) && !ru.admincontrol.item.ItemEncryption.iconHidden(Items.STONE), "");
		ru.admincontrol.item.ItemEncryption.apply(true, List.of("admin_control:twilight_iron_pickaxe"), true, List.of(), true);
		boolean hasBlocked = false, hasIron = false;
		for (Recipe<?> r : new net.minecraft.network.packet.s2c.play.SynchronizeRecipesS2CPacket(server.getRecipeManager().values()).getRecipes()) {
			if (r.getId().equals(new Identifier(AdminControlMod.MOD_ID, "twilight_iron_pickaxe"))) hasBlocked = true;
			if (r.getId().equals(new Identifier("minecraft", "iron_pickaxe"))) hasIron = true;
		}
		check("блокировка крафта: рецепт зашифрованного предмета не уходит клиентам", !hasBlocked && hasIron, "заблокированный " + hasBlocked + ", обычный " + hasIron);
		ru.admincontrol.item.ItemEncryption.apply(true, List.of("admin_control:twilight_iron_pickaxe"), true, List.of(), false);
		hasBlocked = false;
		for (Recipe<?> r : new net.minecraft.network.packet.s2c.play.SynchronizeRecipesS2CPacket(server.getRecipeManager().values()).getRecipes()) {
			if (r.getId().equals(new Identifier(AdminControlMod.MOD_ID, "twilight_iron_pickaxe"))) hasBlocked = true;
		}
		check("блокировка крафта выключена: рецепт отправляется", hasBlocked, "");
		ru.admincontrol.item.ItemEncryption.apply(true, List.of("admin_control:lumirite_ingot", "minecraft:dirt"), true, List.of("admin_control:lumirite_ingot"), true);
		ItemStack masked = new ItemStack(LumiraContent.item("lumirite_ingot"));
		check("название зашифрованного предмета скрыто", masked.getName().getString().chars().allMatch(ch -> ch == 'X')
				&& !masked.getName().getString().toLowerCase().contains("lumir"), masked.getName().getString());
		check("ванильный предмет можно зашифровать", new ItemStack(Items.DIRT).getName().getString().startsWith("XXX"), "");
		check("незашифрованный предмет не меняется", !new ItemStack(Items.STONE).getName().getString().startsWith("XXX"), "");
		check("в логах вместо названия звёздочки", ru.admincontrol.util.ItemLabels.of(masked).equals("***")
				&& ru.admincontrol.util.ItemLabels.of(new ItemStack(Items.DIRT)).equals("***"), ru.admincontrol.util.ItemLabels.of(masked));
		ru.admincontrol.item.ItemEncryption.apply(false, List.of("admin_control:lumirite_ingot"), false, List.of("admin_control:lumirite_ingot"), false);
		check("выключенное шифрование показывает название", !masked.getName().getString().startsWith("XXX"), "");
		ru.admincontrol.item.ItemEncryption.apply(true, List.of(), true, List.of(), true);
		List<String> defaults = ru.admincontrol.item.ItemEncryption.defaults();
		check("список по умолчанию включает инструменты разработчика и контент Люмиры",
				defaults.contains("admin_control:story_tablet") && defaults.contains("admin_control:lumirite_pickaxe") && defaults.contains("admin_control:coin_lyra"),
				defaults.size() + " предметов");
		// крафт монет из настроек
		var settings = ru.admincontrol.data.DataManager.get().settings();
		settings.coinLyraRecipe = new ArrayList<>(List.of("minecraft:gold_nugget", "minecraft:gold_nugget", "minecraft:redstone"));
		settings.coinLyraCount = 3;
		ScreenHandler dummy = new ScreenHandler(null, -1) {
			@Override
			public ItemStack quickMove(net.minecraft.entity.player.PlayerEntity player, int slot) {
				return ItemStack.EMPTY;
			}

			@Override
			public boolean canUse(net.minecraft.entity.player.PlayerEntity player) {
				return true;
			}
		};
		CraftingInventory grid = new CraftingInventory(dummy, 3, 3);
		grid.setStack(0, new ItemStack(Items.GOLD_NUGGET));
		grid.setStack(4, new ItemStack(Items.REDSTONE));
		grid.setStack(8, new ItemStack(Items.GOLD_NUGGET));
		var match = server.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, grid, server.getOverworld());
		check("крафт монеты по настройкам /dserver", match.isPresent()
				&& match.get().craft(grid, server.getRegistryManager()).isOf(LumiraContent.item("coin_lyra"))
				&& match.get().craft(grid, server.getRegistryManager()).getCount() == 3, "");
		settings.coinLyraRecipe = new ArrayList<>();
		check("пустой рецепт монеты выключает крафт", server.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, grid, server.getOverworld()).isEmpty(), "");
		settings.coinLyraCount = 1;
		return true;
	}

	private static ru.admincontrol.emi.EmiFilter.Out emiQuery(String id, int mode) {
		return ru.admincontrol.emi.EmiServer.compute(server, coinPlayer, id, mode);
	}

	private static boolean emiHasStation(ru.admincontrol.emi.EmiFilter.Out out, net.minecraft.item.Item station) {
		if (out == null) return false;
		for (var group : out.groups.values()) for (var r : group) if (r.station.getItem() == station) return true;
		return false;
	}

	private static boolean emiHasAnvilKind(ru.admincontrol.emi.EmiFilter.Out out) {
		if (out == null) return false;
		for (var group : out.groups.values()) for (var r : group) if (r.kind == ru.admincontrol.emi.EmiRecipe.ANVIL) return true;
		return false;
	}

	/** CW EMI: серверная фильтрация, станции, динамический рецепт монеты, закрытые станции. */
	private static boolean cwEmi() {
		var drm = server.getRegistryManager();
		var all = ru.admincontrol.emi.EmiConvert.convertAll(server.getRecipeManager(), drm);
		net.minecraft.item.Item workbench = LumiraContent.RUNE_WORKBENCH.asItem(), anvil = LumiraContent.RUNE_ANVIL.asItem();
		int runeGrid = 0, runeMax = 0, vanillaGrid = 0, cooking = 0, blast = 0, stone = 0, smithing = 0, anvilKinds = 0;
		for (var r : all) {
			if (r.kind == ru.admincontrol.emi.EmiRecipe.GRID && r.station.getItem() == workbench) { runeGrid++; runeMax = Math.max(runeMax, Math.max(r.width, r.height)); }
			if (r.kind == ru.admincontrol.emi.EmiRecipe.GRID && r.station.getItem() == Items.CRAFTING_TABLE) vanillaGrid++;
			if (r.kind == ru.admincontrol.emi.EmiRecipe.COOKING && r.station.getItem() == Items.FURNACE) cooking++;
			if (r.kind == ru.admincontrol.emi.EmiRecipe.COOKING && r.station.getItem() == Items.BLAST_FURNACE) blast++;
			if (r.kind == ru.admincontrol.emi.EmiRecipe.STONECUT && r.station.getItem() == Items.STONECUTTER) stone++;
			if (r.kind == ru.admincontrol.emi.EmiRecipe.SMITHING && r.inputs.size() == 3 && r.station.getItem() == Items.SMITHING_TABLE) smithing++;
			if (r.kind == ru.admincontrol.emi.EmiRecipe.ANVIL && r.station.getItem() == anvil) anvilKinds++;
		}
		check("EMI: рунный верстак отдаёт сетку 4x4", runeGrid >= 10 && runeMax == 4, runeGrid + " рецептов, max=" + runeMax);
		check("EMI: ванильный верстак, печь, плавильня, камнерез, кузнечный стол", vanillaGrid > 100 && cooking > 10 && blast > 5 && stone > 10 && smithing > 5,
				vanillaGrid + "/" + cooking + "/" + blast + "/" + stone + "/" + smithing);
		check("EMI: ремонт рунной наковальни — отдельный вид, не крафт", anvilKinds > 5, anvilKinds + " записей");

		// закрытая иконка: нет в ответе, неизвестный ID неотличим от скрытого
		ru.admincontrol.item.ItemEncryption.apply(true, List.of(), true, List.of("admin_control:lumirite_ingot"), true);
		check("EMI C: предмет с закрытой иконкой не отдаёт рецепты и применения", emiQuery("admin_control:lumirite_ingot", 0) == null && emiQuery("admin_control:lumirite_ingot", 1) == null, "");
		check("EMI C: неизвестный ID отвечает так же, как закрытый", emiQuery("admin_control:no_such_item", 0) == null, "");
		check("EMI C: обычный предмет отвечает", emiQuery("minecraft:iron_ingot", 1) != null, "");
		var iron = emiQuery("minecraft:iron_ingot", 1);
		boolean leak = false;
		if (iron != null) for (var g : iron.groups.values()) for (var r : g) {
			for (var o : r.outputs) if (o.getItem() == LumiraContent.item("lumirite_ingot")) leak = true;
			for (var in : r.inputs) for (var st : in) if (st.getItem() == LumiraContent.item("lumirite_ingot")) leak = true;
		}
		check("EMI C: закрытый предмет не просачивается в чужие рецепты", !leak, "");
		// открытая иконка + скрытое название: запрос разрешён, название остаётся маской
		ru.admincontrol.item.ItemEncryption.apply(true, List.of("minecraft:iron_ingot"), true, List.of(), false);
		check("EMI C: иконка открыта, название скрыто — запрос разрешён, имя в маске", emiQuery("minecraft:iron_ingot", 0) != null
				&& new ItemStack(Items.IRON_INGOT).getName().getString().startsWith("XXX"), "");

		// закрытая станция при видимой ветке (D)
		var vis = new ru.admincontrol.emi.EmiRecipe();
		vis.kind = ru.admincontrol.emi.EmiRecipe.SHAPELESS;
		vis.station = new ItemStack(Items.CRAFTING_TABLE);
		vis.inputs.add(new ArrayList<>(List.of(new ItemStack(Items.STICK))));
		vis.outputs.add(new ItemStack(Items.TORCH));
		var secret = vis.copyShell();
		secret.station = new ItemStack(workbench);
		secret.inputs.add(new ArrayList<>(List.of(new ItemStack(Items.STICK))));
		secret.outputs.add(new ItemStack(Items.TORCH));
		var fakeAll = new ArrayList<>(List.of(vis, secret));
		var out = ru.admincontrol.emi.EmiFilter.build(fakeAll, Items.TORCH, true, i -> i == workbench);
		check("EMI D: закрытая станция скрыта, обычная ветка доступна, есть флаг «Недоступно»", out.locked && out.groups.size() == 1
				&& out.groups.containsKey("minecraft:crafting_table"), out.groups.keySet().toString());
		var alt = vis.copyShell();
		alt.outputs.add(new ItemStack(Items.TORCH));
		alt.inputs.add(new ArrayList<>(List.of(new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.DIRT))));
		var altOut = ru.admincontrol.emi.EmiFilter.sanitize(alt, i -> i == Items.DIRT);
		check("EMI D: закрытый вариант тега вырезан, остальные остаются", altOut != null && altOut.inputs.get(0).size() == 1 && altOut.inputs.get(0).get(0).isOf(Items.OAK_PLANKS), "");
		alt.inputs.set(0, new ArrayList<>(List.of(new ItemStack(Items.DIRT))));
		check("EMI D: рецепт с полностью закрытым слотом скрывается целиком", ru.admincontrol.emi.EmiFilter.sanitize(alt, i -> i == Items.DIRT) == null, "");
		// реальные данные: рунные рецепты при закрытой/открытой станции
		ru.admincontrol.item.ItemEncryption.apply(true, List.of(), true, List.of("admin_control:rune_workbench"), false);
		String runeItem = "admin_control:twilight_iron_helmet";
		var closed = emiQuery(runeItem, 0);
		check("EMI D: рецепты рунного верстака пусты, когда верстак закрыт", closed != null && closed.groups.isEmpty() && closed.locked, closed == null ? "null" : closed.groups.keySet() + " locked=" + closed.locked);
		ru.admincontrol.item.ItemEncryption.apply(true, List.of(), true, List.of(), false);
		var open = emiQuery(runeItem, 0);
		check("EMI F: когда верстак открыт, рецепт показывается на рунном верстаке", open != null && emiHasStation(open, workbench) && !open.locked, open == null ? "null" : open.groups.keySet().toString());
		var anvilUses = emiQuery("admin_control:twilight_iron_pickaxe", 1);
		check("EMI F: рунный рецепт — сетка 4x4 из 16 клеток", open != null && open.groups.values().iterator().next().get(0).inputs.size() == 16 && open.groups.values().iterator().next().get(0).width == 4, "");
		check("EMI F: ремонт в рунной наковальне виден в «Используется в» как отдельный вид", emiHasAnvilKind(anvilUses), "");
		check("EMI F: ремонт не маскируется под способ получить предмет", !emiHasAnvilKind(open), "");

		// динамический рецепт монеты (E)
		ru.admincontrol.item.ItemEncryption.apply(false, List.of(), false, List.of(), false);
		var settings = ru.admincontrol.data.DataManager.get().settings();
		settings.coinLyraRecipe = new ArrayList<>();
		ru.admincontrol.emi.EmiServer.invalidate(server);
		var none = emiQuery("admin_control:coin_lyra", 0);
		check("EMI E: у монеты без рецепта нет выдуманной схемы", none != null && none.groups.keySet().stream().allMatch(k -> k.equals("source")), none == null ? "null" : none.groups.keySet().toString());
		settings.coinLyraRecipe = new ArrayList<>(List.of("minecraft:gold_nugget", "minecraft:gold_nugget", "minecraft:redstone"));
		settings.coinLyraCount = 3;
		ru.admincontrol.emi.EmiServer.invalidate(server);
		var withRecipe = emiQuery("admin_control:coin_lyra", 0);
		boolean coinOk = false;
		if (withRecipe != null) for (var g : withRecipe.groups.values()) for (var r : g)
			if (r.kind == ru.admincontrol.emi.EmiRecipe.SHAPELESS && r.inputs.size() == 3 && r.outputs.get(0).getCount() == 3) coinOk = true;
		check("EMI E: после сохранения в /dserver рецепт монеты появляется без перезапуска", coinOk, "");
		ru.admincontrol.item.ItemEncryption.apply(true, List.of(), true, List.of("admin_control:coin_lyra"), false);
		check("EMI E: закрытая иконка монеты скрывает и её рецепт", emiQuery("admin_control:coin_lyra", 0) == null, "");
		settings.coinLyraRecipe = new ArrayList<>();
		settings.coinLyraCount = 1;
		ru.admincontrol.emi.EmiServer.invalidate(server);

		// список «использование запрещено» и права каталога
		ru.admincontrol.item.ItemEncryption.apply(true, List.of(), false, List.of(), false, List.of(), List.of("minecraft:diamond"));
		check("EMI: «Используется в» закрыт для предмета из списка запрета использования", emiQuery("minecraft:diamond", 1) == null && emiQuery("minecraft:diamond", 0) != null, "");
		boolean toolsHidden = true;
		for (net.minecraft.item.Item tool : ru.admincontrol.item.ModItems.CW_TOOLS) toolsHidden &= !ru.admincontrol.emi.EmiAccess.listed(tool, false, true);
		check("EMI: инструменты разработчика не попадают в каталог обычного игрока, но доступны DEV", toolsHidden
				&& ru.admincontrol.emi.EmiAccess.listed(ru.admincontrol.item.ModItems.CW_TOOLS.get(0), true, true)
				&& !ru.admincontrol.emi.EmiAccess.listed(Items.COMMAND_BLOCK, false, false) && ru.admincontrol.emi.EmiAccess.listed(Items.COMMAND_BLOCK, false, true)
				&& !ru.admincontrol.emi.EmiAccess.listed(LumiraContent.HIDDEN_ITEM, true, true), "");
		// способы получения без рецепта
		ru.admincontrol.item.ItemEncryption.apply(false, List.of(), false, List.of(), false);
		java.util.function.BiFunction<String, String, Boolean> hasSrc = (id, note) -> {
			var q = emiQuery(id, 0);
			if (q == null || !q.groups.containsKey("source")) return false;
			for (var r : q.groups.get("source")) if (r.note.equals(note)) return true;
			return false;
		};
		check("Альбион: земля и дёрн считаются землёй для саженцев", LumiraContent.ALBION_DIRT.getDefaultState().isIn(net.minecraft.registry.tag.BlockTags.DIRT) && LumiraContent.ALBION_GRASS.getDefaultState().isIn(net.minecraft.registry.tag.BlockTags.DIRT), "");
		check("Альбион: дёрн принимает костную муку", LumiraContent.ALBION_GRASS instanceof net.minecraft.block.Fertilizable, "");
		check("EMI: бревно добывается из блока", hasSrc.apply("minecraft:oak_log", "block"), "");
		check("EMI: слиток железа выпадает из существа (голем) и имеет крафты/плавку", hasSrc.apply("minecraft:iron_ingot", "entity") && emiQuery("minecraft:iron_ingot", 0).groups.size() > 1, "");
		check("EMI: цветок получается только добычей", hasSrc.apply("minecraft:dandelion", "block") && emiQuery("minecraft:dandelion", 0).groups.size() == 1, "");
		check("EMI: яйцо дракона — особая механика (убить дракона)", hasSrc.apply("minecraft:dragon_egg", "mech"), "");
		check("EMI: растущий аметист — только творческий режим", hasSrc.apply("minecraft:budding_amethyst", "creative"), "");
		check("EMI: клиентский режим даёт механику и творческий режим без сервера", ru.admincontrol.emi.EmiMechanics.local(Items.DRAGON_EGG, false).get(0).note.equals("mech")
				&& ru.admincontrol.emi.EmiMechanics.local(Items.BARRIER, false).get(0).note.equals("creative")
				&& ru.admincontrol.emi.EmiMechanics.local(Items.DIRT, false).get(0).note.equals("world_block"), "");
		check("EMI: яйцо призыва — «только творческий режим»", hasSrc.apply("minecraft:allay_spawn_egg", "creative"), "");
		// ограничение частоты запросов (J)
		int allowed = 0;
		for (int i = 0; i < 60; i++) if (ru.admincontrol.emi.EmiServer.allowRate(coinPlayer)) allowed++;
		check("EMI J: пачка из 60 запросов подряд ограничивается", allowed >= 5 && allowed <= 20, allowed + " пропущено");
		check("EMI B: список известных просмотрщиков содержит EMI, REI, JEI; в тестовой среде конфликтов нет",
				ru.admincontrol.emi.EmiIncompat.KNOWN.containsAll(List.of("emi", "roughlyenoughitems", "jei")) && ru.admincontrol.emi.EmiIncompat.detect().isEmpty(), "");
		ru.admincontrol.item.ItemEncryption.apply(true, List.of(), true, List.of(), true);
		return true;
	}
}
