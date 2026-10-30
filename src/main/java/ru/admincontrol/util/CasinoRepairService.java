package ru.admincontrol.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import ru.admincontrol.block.CasinoMachineBlock;
import ru.admincontrol.block.CasinoMachineBlockEntity;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.item.RepairTool;
import ru.admincontrol.item.RepairToolItem;
import ru.admincontrol.skill.SkillEffects;
import ru.admincontrol.skill.SkillTree;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Поломки игрового автомата. После каждой прокрутки (кроме первых {@link #GRACE_SPINS} после ремонта) автомат
 * ломается с шансом {@link #BASE_CHANCE}, который снижает ветка «Казино» у крутившего игрока; в среднем около 10 прокруток.
 * При поломке случайно выбираются 3 инструмента из 5 и случайно делят 100% ремонта, каждому не меньше 5%.
 * Проценты хранятся в десятых долях.
 */
public final class CasinoRepairService {
	public static final int GRACE_SPINS = 2;
	public static final double BASE_CHANCE = 0.11;
	private static final int TOOLS_NEEDED = 3;
	private static final int MIN_SHARE = 50;
	private static final int FULL = 1000;
	/** Сколько чинит одно действие инструментом без улучшений. */
	private static final int STEP = 50;
	private static final int COOLDOWN_TICKS = 10;
	private static final double REACH_SQ = 6 * 6;

	private static final SecureRandom RANDOM = new SecureRandom();

	private CasinoRepairService() {
	}

	/** Бросок поломки после оплаченной прокрутки; при поломке план ремонта уже записан в автомат. */
	public static boolean rollBreak(ServerPlayerEntity player, CasinoMachineBlockEntity machine) {
		machine.countSpin();
		if (machine.broken() || machine.spins() <= GRACE_SPINS) {
			return false;
		}
		double guard = Math.max(0, Math.min(SkillTree.Stat.MACHINE_GUARD.cap,
				SkillEffects.server(player, SkillTree.Stat.MACHINE_GUARD)));
		if (RANDOM.nextDouble() >= BASE_CHANCE * (1.0 - guard / 100.0)) {
			return false;
		}
		machine.breakDown(plan());
		return true;
	}

	/** Три случайных инструмента из всех делят 100%, каждому не меньше 5%. */
	private static int[] plan() {
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < RepairTool.COUNT; i++) {
			order.add(i);
		}
		java.util.Collections.shuffle(order, RANDOM);
		java.util.Set<Integer> used = new java.util.HashSet<>(order.subList(0, TOOLS_NEEDED));
		int free = FULL - MIN_SHARE * TOOLS_NEEDED;
		int a = RANDOM.nextInt(free / 10 + 1) * 10;
		int b = RANDOM.nextInt(free / 10 + 1) * 10;
		int lo = Math.min(a, b);
		int hi = Math.max(a, b);
		int[] shares = {MIN_SHARE + lo, MIN_SHARE + hi - lo, MIN_SHARE + free - hi};
		int[] plan = new int[RepairTool.COUNT];
		int next = 0;
		for (int i = 0; i < plan.length; i++) {
			if (used.contains(i)) {
				plan[i] = shares[next++];
			}
		}
		return plan;
	}

	/** Вид сломанного автомата на обеих половинах: погасший экран, дым, меньше света. */
	public static void showBroken(World world, BlockPos base, boolean broken) {
		for (BlockPos pos : new BlockPos[] {base, base.up()}) {
			BlockState state = world.getBlockState(pos);
			if (state.getBlock() instanceof CasinoMachineBlock && state.get(CasinoMachineBlock.BROKEN) != broken) {
				world.setBlockState(pos, state.with(CasinoMachineBlock.BROKEN, broken), Block.NOTIFY_ALL);
			}
		}
	}

	/** Колесо докрутилось у автомата, который сломался на этой прокрутке. */
	public static void breakEffects(ServerWorld world, BlockPos base) {
		if (!(world.getBlockEntity(base) instanceof CasinoMachineBlockEntity machine) || !machine.broken()) {
			return;
		}
		showBroken(world, base, true);
		double x = base.getX() + 0.5;
		double y = base.getY() + 1.5;
		double z = base.getZ() + 0.5;
		world.playSound(null, base.up(), SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.BLOCKS, 0.9f, 0.6f);
		world.playSound(null, base.up(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.6f, 0.8f);
		world.spawnParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.4, z, 12, 0.25, 0.2, 0.25, 0.02);
		world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 16, 0.35, 0.35, 0.35, 0.15);
	}

	/** ПКМ рукой по сломанному автомату: что нужно для ремонта. */
	public static void explain(ServerPlayerEntity player, CasinoMachineBlockEntity machine) {
		if (machine.getWorld() != null) {
			showBroken(machine.getWorld(), machine.getPos(), true);
		}
		Chat.send(player, Chat.red("Игровой автомат сломан — играть нельзя, пока его не починят."));
		Chat.send(player, Chat.gray("Кликайте ПКМ инструментами: ").append(plan(machine)));
		player.sendMessage(status(machine), true);
	}

	public static void use(ServerPlayerEntity player, BlockPos base, Hand hand) {
		if (!ModGate.on() || DataManager.get() == null || AuthService.pending(player) || NpcService.isIdleNpc(player)
				|| player.isSpectator()) {
			return;
		}
		ServerWorld world = player.getServerWorld();
		ItemStack stack = player.getStackInHand(hand);
		if (!(stack.getItem() instanceof RepairToolItem item)
				|| !(world.getBlockEntity(base) instanceof CasinoMachineBlockEntity machine)) {
			return;
		}
		if (CasinoWheelService.breakPending(world, base)) {
			player.sendMessage(Chat.yellow("Автомат крутится — подождите."), true);
			return;
		}
		if (!machine.broken()) {
			player.sendMessage(Chat.green("Автомат исправен — ремонт не нужен."), true);
			return;
		}
		if (player.squaredDistanceTo(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5) > REACH_SQ) {
			return;
		}
		RepairTool tool = item.tool();
		if (machine.need(tool) <= 0) {
			player.sendMessage(Chat.red(tool.title() + " здесь не нужен. ").append(plan(machine)), true);
			return;
		}
		if (machine.done(tool) >= machine.need(tool)) {
			player.sendMessage(Chat.yellow(tool.title() + ": эта часть уже готова. ").append(plan(machine)), true);
			return;
		}
		if (player.getItemCooldownManager().isCoolingDown(item)) {
			return;
		}
		player.getItemCooldownManager().set(item, COOLDOWN_TICKS);
		double speed = Math.max(0, Math.min(SkillTree.Stat.REPAIR_SPEED.cap,
				SkillEffects.server(player, SkillTree.Stat.REPAIR_SPEED)));
		int step = (int) Math.round(STEP * (1.0 + speed / 100.0));
		machine.add(tool, step, player.getUuid());
		if (!player.isCreative()) {
			stack.damage(1, player, p -> p.sendToolBreakStatus(hand));
		}
		effects(world, base, tool);
		if (!machine.complete()) {
			player.sendMessage(status(machine), true);
			return;
		}
		finish(world, base, machine);
	}

	private static void finish(ServerWorld world, BlockPos base, CasinoMachineBlockEntity machine) {
		List<UUID> helpers = new ArrayList<>(machine.helpers());
		machine.repaired();
		showBroken(world, base, false);
		world.playSound(null, base.up(), SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.7f, 1.3f);
		world.playSound(null, base.up(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.BLOCKS, 0.9f, 1.6f);
		world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, base.getX() + 0.5, base.getY() + 1.4, base.getZ() + 0.5,
				14, 0.35, 0.5, 0.35, 0.0);
		for (UUID id : helpers) {
			ServerPlayerEntity helper = world.getServer().getPlayerManager().getPlayer(id);
			if (helper == null) {
				continue;
			}
			helper.sendMessage(Chat.green("Игровой автомат починен — можно играть!"), true);
			SkillTreeService.onMachineRepaired(helper);
		}
	}

	private static void effects(ServerWorld world, BlockPos base, RepairTool tool) {
		SoundEvent sound;
		ParticleEffect particle;
		float pitch;
		switch (tool) {
			case HAMMER -> {
				sound = SoundEvents.BLOCK_ANVIL_LAND;
				particle = ParticleTypes.CRIT;
				pitch = 1.7f;
			}
			case SOLDERING_IRON -> {
				sound = SoundEvents.BLOCK_FIRE_EXTINGUISH;
				particle = ParticleTypes.ELECTRIC_SPARK;
				pitch = 1.8f;
			}
			case PLIERS -> {
				sound = SoundEvents.BLOCK_CHAIN_PLACE;
				particle = ParticleTypes.CRIT;
				pitch = 1.2f;
			}
			case MULTIMETER -> {
				sound = SoundEvents.BLOCK_COMPARATOR_CLICK;
				particle = ParticleTypes.GLOW;
				pitch = 1.6f;
			}
			default -> {
				sound = SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE;
				particle = ParticleTypes.WAX_OFF;
				pitch = 1.9f;
			}
		}
		world.playSound(null, base.up(), sound, SoundCategory.BLOCKS, tool == RepairTool.HAMMER ? 0.25f : 0.6f, pitch);
		world.spawnParticles(particle, base.getX() + 0.5, base.getY() + 1.2, base.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
	}

	private static String pct(int tenths) {
		return tenths / 10 + "%";
	}

	/** Строка для панели над хотбаром: общий прогресс и каждый нужный инструмент. */
	private static Text status(CasinoMachineBlockEntity machine) {
		MutableText out = Text.literal("Ремонт " + pct(machine.progress())).formatted(Formatting.GOLD);
		for (RepairTool tool : RepairTool.values()) {
			int need = machine.need(tool);
			if (need <= 0) {
				continue;
			}
			int done = machine.done(tool);
			out.append(Text.literal("  ·  ").formatted(Formatting.DARK_GRAY));
			out.append(done >= need
					? Text.literal(tool.title() + " ✔").formatted(Formatting.GREEN)
					: Text.literal(tool.title() + " " + pct(done) + "/" + pct(need)).formatted(Formatting.WHITE));
		}
		return out;
	}

	/** Какие инструменты ещё нужны и сколько им осталось. */
	private static MutableText plan(CasinoMachineBlockEntity machine) {
		List<String> left = new ArrayList<>();
		for (RepairTool tool : RepairTool.values()) {
			int rest = machine.need(tool) - machine.done(tool);
			if (rest > 0) {
				left.add(tool.title() + " " + (rest + 9) / 10 + "%");
			}
		}
		return Chat.white(left.isEmpty() ? "ремонт почти готов." : "нужно " + String.join(", ", left) + ".");
	}
}
