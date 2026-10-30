package ru.admincontrol.lumira.gen;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import ru.admincontrol.lumira.LumiraContent;
import ru.admincontrol.lumira.LumiraIds;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Шаблоны семи новых пород: три формы роста (малая, стандартная, крупная) на породу — всего 21 шаблон.
 * Дерево сначала строится как план (стволы и листва), затем проверяется свободное место и только потом ставится.
 */
public final class LumiraTreeGen {
	private LumiraTreeGen() {
	}

	/** План дерева: позиция → состояние. */
	public static final class Plan {
		final Map<BlockPos, BlockState> logs = new LinkedHashMap<>();
		final Map<BlockPos, BlockState> leaves = new LinkedHashMap<>();
		public boolean allInside(java.util.function.Predicate<BlockPos> ok) {
			for (BlockPos p : logs.keySet()) {
				if (!ok.test(p)) {
					return false;
				}
			}
			for (BlockPos p : leaves.keySet()) {
				if (!ok.test(p)) {
					return false;
				}
			}
			return true;
		}

		public java.util.Set<BlockPos> logsView() {
			return logs.keySet();
		}

		final Block log;
		final Block leaf;

		Plan(LumiraIds.Tree tree) {
			this(LumiraContent.block(tree.id() + "_log"), LumiraContent.block(tree.id() + "_leaves"));
		}

		Plan(Block log, Block leaf) {
			this.log = log;
			this.leaf = leaf;
		}

		void log(BlockPos p, Direction.Axis axis) {
			logs.put(p, log.getDefaultState().with(PillarBlock.AXIS, axis));
		}

		void leaf(BlockPos p) {
			if (!logs.containsKey(p)) {
				leaves.put(p, leaf.getDefaultState().with(LeavesBlock.PERSISTENT, false));
			}
		}

		void ball(BlockPos c, double rx, double ry, double rz, Random r, double keep) {
			int ix = (int) Math.ceil(rx), iy = (int) Math.ceil(ry), iz = (int) Math.ceil(rz);
			for (int x = -ix; x <= ix; x++) {
				for (int y = -iy; y <= iy; y++) {
					for (int z = -iz; z <= iz; z++) {
						double d = (x * x) / (rx * rx) + (y * y) / (ry * ry) + (z * z) / (rz * rz);
						if (d <= 1.0 && (d < 0.5 || r.nextDouble() < keep)) {
							leaf(c.add(x, y, z));
						}
					}
				}
			}
		}

		void disc(BlockPos c, double radius, Random r, double keep) {
			int ir = (int) Math.ceil(radius);
			for (int x = -ir; x <= ir; x++) {
				for (int z = -ir; z <= ir; z++) {
					double d = Math.sqrt(x * x + z * z);
					if (d <= radius && (d < 1.5 || r.nextDouble() < keep)) {
						leaf(c.add(x, 0, z));
					}
				}
			}
		}

		void trunk(BlockPos base, int height) {
			for (int i = 0; i < height; i++) {
				log(base.up(i), Direction.Axis.Y);
			}
		}

		/** Диагональная ветка: на каждые rise блоков вверх сдвиг на 1 по горизонтали. */
		BlockPos branch(BlockPos from, int dx, int dz, int length, int rise) {
			BlockPos p = from;
			for (int i = 1; i <= length; i++) {
				p = p.up();
				if (i % rise == 0) {
					p = p.add(dx, 0, dz);
				}
				log(p, Direction.Axis.Y);
			}
			return p;
		}
	}

	public static int heightFor(LumiraIds.Tree t, int size, Random r) {
		int span = t.hMax() - t.hMin();
		return switch (size) {
			case 0 -> t.hMin();
			case 1 -> t.hMin() + span / 2;
			default -> t.hMax();
		} + (span > 2 && size == 1 ? r.nextInt(2) : 0);
	}

	public static Plan plan(LumiraIds.Tree t, BlockPos base, int size, Random r) {
		Plan p = new Plan(t);
		int h = heightFor(t, size, r);
		double crown = t.crown() * (size == 0 ? 0.8 : size == 2 ? 1.1 : 1.0) / 2.0;
		Direction dir = Direction.Type.HORIZONTAL.random(r);
		int dx = dir.getOffsetX(), dz = dir.getOffsetZ();
		switch (t.id()) {
			case "lumira_willow" -> {
				// Наклонный ствол: каждые 3 блока сдвиг; шапка и свисающие пряди.
				BlockPos pos = base;
				for (int i = 0; i < h; i++) {
					p.log(pos, Direction.Axis.Y);
					if (i > 2 && i % 3 == 0) {
						pos = pos.add(dx, 0, dz);
					}
					pos = pos.up();
				}
				BlockPos top = pos;
				p.log(top, Direction.Axis.Y);
				p.ball(top, crown, 2.6, crown, r, 0.85);
				for (int a = 0; a < 14; a++) {
					double ang = r.nextDouble() * Math.PI * 2;
					int sx = (int) Math.round(Math.cos(ang) * (crown - 0.5));
					int sz = (int) Math.round(Math.sin(ang) * (crown - 0.5));
					int len = 2 + r.nextInt(3);
					for (int d = 1; d <= len; d++) {
						p.leaf(top.add(sx, -2 - d, sz));
					}
				}
			}
			case "zarelist" -> {
				int forkAt = Math.max(2, (int) (h * 0.55));
				p.trunk(base, forkAt);
				BlockPos fork = base.up(forkAt - 1);
				BlockPos a = p.branch(fork, dx, dz, h - forkAt + 1, 2);
				BlockPos b = p.branch(fork, -dx, -dz, h - forkAt, 2);
				p.ball(a.up(), crown * 0.65, 2.2, crown * 0.65, r, 0.9);
				p.ball(b.up(), crown * 0.65, 2.2, crown * 0.65, r, 0.9);
				p.ball(fork.up(forkAt / 2).add(dx, 0, dz), crown * 0.5, 1.8, crown * 0.5, r, 0.85);
			}
			case "twilight_cedar" -> {
				p.trunk(base, h);
				int y0 = Math.max(2, h / 4);
				for (int y = y0; y <= h + 1; y++) {
					double frac = (double) (y - y0) / (h + 1 - y0);
					double radius = Math.max(0.0, crown * (1.0 - frac));
					int step = (y - y0) / 2;
					radius = Math.max(0.0, crown * (1.0 - (double) step * 2 / (h + 1 - y0)));
					if (radius > 0.4 || y >= h) {
						p.disc(base.up(y), Math.max(radius, y >= h ? 0.5 : 1.0), r, 0.9);
					}
				}
			}
			case "star_birch" -> {
				int forkAt = Math.max(3, (int) (h * 0.6));
				p.trunk(base, forkAt);
				BlockPos fork = base.up(forkAt - 1);
				BlockPos a = p.branch(fork, dx, dz, h - forkAt + 1, 2);
				BlockPos b = p.branch(fork, -dx, -dz, h - forkAt + 1, 2);
				BlockPos mid = base.up(h - 1);
				p.ball(mid.up(), crown, 2.8, crown, r, 0.88);
				p.ball(a.up(), 1.8, 1.6, 1.8, r, 0.9);
				p.ball(b.up(), 1.8, 1.6, 1.8, r, 0.9);
			}
			case "crimson_elm" -> {
				int trunkH = Math.max(3, (int) (h * 0.6));
				p.trunk(base, trunkH);
				BlockPos top = base.up(trunkH);
				BlockPos arm = p.branch(top.down(), dx, dz, h - trunkH + 1, 2);
				p.ball(arm.up(), crown, 2.5, crown * 0.85, r, 0.88);
				p.ball(top.add(-dx * 2, 1, -dz * 2), crown * 0.6, 1.8, crown * 0.6, r, 0.85);
			}
			case "moon_ash" -> {
				int forkAt = Math.max(3, (int) (h * 0.65));
				p.trunk(base, forkAt);
				BlockPos fork = base.up(forkAt - 1);
				BlockPos a = p.branch(fork, dx, dz, h - forkAt + 1, 1);
				BlockPos b = p.branch(fork, -dx, -dz, h - forkAt + 1, 1);
				for (BlockPos end : new BlockPos[]{a, b}) {
					p.disc(end.up(), crown * 0.75, r, 0.7);
					p.disc(end, crown * 0.55, r, 0.6);
				}
				BlockPos mid = fork.up(2);
				p.disc(mid.up(), crown * 0.6, r, 0.65);
			}
			case "malachite_sequoia" -> {
				p.trunk(base, h);
				int y0 = Math.max(4, (int) (h * 0.4));
				for (int y = y0; y <= h + 1; y++) {
					double frac = (double) (y - y0) / (h + 1 - y0);
					double radius = crown * (1.0 - frac * 0.85);
					boolean tier = (y - y0) % 3 != 2;
					if (tier) {
						p.disc(base.up(y), Math.max(1.2, radius), r, 0.85);
					}
				}
				p.leaf(base.up(h + 2));
			}
			default -> p.trunk(base, h);
		}
		return p;
	}

	/** Обычные дуб, берёза и ель для смешанного леса. */
	public static Plan vanilla(String kind, BlockPos base, Random r) {
		switch (kind) {
			case "birch" -> {
				Plan p = new Plan(Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES);
				int h = 5 + r.nextInt(3);
				p.trunk(base, h);
				p.ball(base.up(h - 1), 2.2, 2.6, 2.2, r, 0.9);
				return p;
			}
			case "spruce" -> {
				Plan p = new Plan(Blocks.SPRUCE_LOG, Blocks.SPRUCE_LEAVES);
				int h = 6 + r.nextInt(4);
				p.trunk(base, h);
				for (int y = 2; y <= h + 1; y++) {
					double radius = 2.4 * (1.0 - (double) (y - 2) / (h + 1));
					if ((y & 1) == 0 || y >= h) {
						p.disc(base.up(y), Math.max(0.6, radius), r, 0.95);
					}
				}
				return p;
			}
			default -> {
				Plan p = new Plan(Blocks.OAK_LOG, Blocks.OAK_LEAVES);
				int h = 4 + r.nextInt(3);
				p.trunk(base, h);
				p.ball(base.up(h), 2.6, 2.3, 2.6, r, 0.9);
				return p;
			}
		}
	}

	private static boolean replaceable(BlockState s) {
		return s.isAir() || s.isReplaceable() || s.isIn(BlockTags.LEAVES) || s.isOf(Blocks.SNOW)
				|| s.getBlock() instanceof net.minecraft.block.SaplingBlock;
	}

	/** Строгая проверка для генерации леса: кроны и стволы разных деревьев не врастают друг в друга. */
	public static boolean fitsStrict(World world, Plan plan) {
		if (!fits(world, plan)) {
			return false;
		}
		int overlap = 0;
		for (BlockPos pos : plan.leaves.keySet()) {
			BlockState s = world.getBlockState(pos);
			if (s.isIn(BlockTags.LOGS)) {
				return false;
			}
			if (s.isIn(BlockTags.LEAVES) && ++overlap > 2) {
				return false;
			}
		}
		for (BlockPos pos : plan.logs.keySet()) {
			for (Direction d : Direction.Type.HORIZONTAL) {
				if (world.getBlockState(pos.offset(d)).isIn(BlockTags.LOGS) && !plan.logs.containsKey(pos.offset(d))) {
					return false;
				}
			}
		}
		return true;
	}

	public static boolean fits(World world, Plan plan) {
		for (BlockPos pos : plan.logs.keySet()) {
			if (!replaceable(world.getBlockState(pos))) {
				return false;
			}
		}
		return true;
	}

	/** Ставит план: стволы, затем листва с корректным расстоянием до бревна. */
	public static void place(ServerWorld world, Plan plan) {
		int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
		for (Map.Entry<BlockPos, BlockState> e : plan.logs.entrySet()) {
			world.setBlockState(e.getKey(), e.getValue(), flags);
		}
		Map<BlockPos, Integer> dist = new HashMap<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		for (BlockPos log : plan.logs.keySet()) {
			dist.put(log, 0);
			queue.add(log);
		}
		while (!queue.isEmpty()) {
			BlockPos cur = queue.poll();
			int d = dist.get(cur);
			if (d >= 6) {
				continue;
			}
			for (Direction dir : Direction.values()) {
				BlockPos n = cur.offset(dir);
				if (plan.leaves.containsKey(n) && !dist.containsKey(n)) {
					dist.put(n, d + 1);
					queue.add(n);
				}
			}
		}
		for (Map.Entry<BlockPos, BlockState> e : plan.leaves.entrySet()) {
			BlockState current = world.getBlockState(e.getKey());
			if (!replaceable(current)) {
				continue;
			}
			int d = dist.getOrDefault(e.getKey(), 7);
			if (d > 6) {
				continue;
			}
			world.setBlockState(e.getKey(), e.getValue().with(LeavesBlock.DISTANCE, Math.max(1, d)), flags);
		}
	}

	private static int rollSize(Random r) {
		int v = r.nextInt(100);
		return v < 25 ? 0 : v < 80 ? 1 : 2;
	}

	/** Рост саженца: пытается три формы от крупной к малой, чтобы дерево нашло место. */
	public static boolean growFromSapling(ServerWorld world, BlockPos pos, LumiraIds.Tree tree, Random random) {
		if (!world.getBlockState(pos.down()).isIn(BlockTags.DIRT)) {
			return false;
		}
		int first = rollSize(random);
		world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
		for (int size = first; size >= 0; size--) {
			Plan plan = plan(tree, pos, size, random);
			if (fits(world, plan)) {
				place(world, plan);
				return true;
			}
		}
		world.setBlockState(pos, LumiraContent.block(tree.id() + "_sapling").getDefaultState(), Block.NOTIFY_ALL);
		return false;
	}

	/** Для генератора биома: дерево на заданной земле. */
	public static boolean generate(ServerWorld world, BlockPos pos, LumiraIds.Tree tree, Random random, java.util.function.Predicate<BlockPos> allowed) {
		int size = rollSize(random);
		for (int s = size; s >= 0; s--) {
			Plan plan = plan(tree, pos, s, random);
			if (plan.allInside(allowed) && fitsStrict(world, plan)) {
				place(world, plan);
				return true;
			}
		}
		return false;
	}

	public static LumiraIds.Tree find(String id) {
		for (LumiraIds.Tree t : LumiraIds.TREES) {
			if (t.id().equals(id)) {
				return t;
			}
		}
		return null;
	}
}
