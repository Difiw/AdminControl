package ru.admincontrol.util;

import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.AbstractGlassBlock;
import net.minecraft.block.AbstractPressurePlateBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.VineBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Карта страйкбола 150×150 — город после боя: промзона с ангаром и цехом (люки, бункер, подземный ход, смотровая яма),
 * улицы с тротуарами, дома в два-три этажа с комнатами, магазин, заправка, школа, площадь и стоянка.
 * Всё побито: пробоины, обрушенные углы и крыши, сгоревший дом, воронки, окопы и ячейки, горящие обломки и дым.
 * По краям лес, базы красных (запад) и синих (восток).
 */
final class StrikeMap {
	static final int SIZE = 150;
	static final int HEIGHT = 26;
	/** Верхний блок земли; игроки стоят на G + 1. */
	static final int G = 3;
	private static final int FLOOR_H = 5;
	private static final BlockState AIR = Blocks.AIR.getDefaultState();

	private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

	private final BlockState[] cells = new BlockState[SIZE * HEIGHT * SIZE];
	private final boolean[] busy = new boolean[SIZE * SIZE];
	/** Клетки, которые взрывы и завалы не трогают: лестницы с опорой, двери, люки. */
	private final boolean[] guard = new boolean[SIZE * HEIGHT * SIZE];
	private final List<int[]> flames = new ArrayList<>();
	private final List<int[]> smoke = new ArrayList<>();
	private final Random rnd;

	private record Pal(BlockState wall, BlockState corner, BlockState band, BlockState floor, BlockState inner,
			Block stairs, BlockState roof, Block door) {
	}

	private StrikeMap(Random rnd) {
		this.rnd = rnd;
	}

	static StrikeMap generate(Random rnd) {
		StrikeMap m = new StrikeMap(rnd);
		m.terrain();
		m.roads();
		m.base(0);
		m.base(1);
		m.industry();
		m.city();
		m.streets();
		m.center();
		m.battle();
		m.forest();
		m.fires();
		m.perimeter();
		m.settle();
		m.connect();
		return m;
	}

	/** Точка появления: локальный X (с зеркалом для синих). */
	static int spawnX(int team, int slot) {
		int x = 7 + 2 * Math.floorMod(slot, 3);
		return team == 0 ? x : SIZE - 1 - x;
	}

	static int spawnZ(int slot) {
		return 69 + 2 * (Math.floorMod(slot, 21) / 3);
	}

	BlockState get(int x, int y, int z) {
		if (!in(x, y, z)) {
			return null;
		}
		return this.cells[(x * HEIGHT + y) * SIZE + z];
	}

	private static boolean in(int x, int y, int z) {
		return x >= 0 && x < SIZE && y >= 0 && y < HEIGHT && z >= 0 && z < SIZE;
	}

	private void set(int x, int y, int z, BlockState s) {
		if (in(x, y, z)) {
			this.cells[(x * HEIGHT + y) * SIZE + z] = s;
		}
	}

	private void setIfAir(int x, int y, int z, BlockState s) {
		BlockState cur = get(x, y, z);
		if (in(x, y, z) && (cur == null || cur.isAir())) {
			set(x, y, z, s);
		}
	}

	private void protect(int x, int y, int z) {
		if (in(x, y, z)) {
			this.guard[(x * HEIGHT + y) * SIZE + z] = true;
		}
	}

	private boolean guarded(int x, int y, int z) {
		return in(x, y, z) && this.guard[(x * HEIGHT + y) * SIZE + z];
	}

	/** Рядом с защищённой клеткой (проход к двери или лестнице) завалы не насыпаются. */
	private boolean nearGuard(int x, int y, int z) {
		if (guarded(x, y, z) || guarded(x, y - 1, z)) {
			return true;
		}
		for (Direction dir : SIDES) {
			int nx = x + dir.getOffsetX();
			int nz = z + dir.getOffsetZ();
			if (guarded(nx, y, nz) || guarded(nx, y - 1, nz)) {
				return true;
			}
		}
		return false;
	}

	private boolean solid(int x, int y, int z) {
		BlockState s = get(x, y, z);
		return s != null && !s.isAir();
	}

	private void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
					set(x, y, z, s);
				}
			}
		}
	}

	private void mark(int x0, int z0, int x1, int z1) {
		for (int x = Math.max(0, Math.min(x0, x1)); x <= Math.min(SIZE - 1, Math.max(x0, x1)); x++) {
			for (int z = Math.max(0, Math.min(z0, z1)); z <= Math.min(SIZE - 1, Math.max(z0, z1)); z++) {
				this.busy[x * SIZE + z] = true;
			}
		}
	}

	private boolean isBusy(int x, int z) {
		return x < 0 || z < 0 || x >= SIZE || z >= SIZE || this.busy[x * SIZE + z];
	}

	private boolean free(int x0, int z0, int x1, int z1) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				if (isBusy(x, z)) {
					return false;
				}
			}
		}
		return true;
	}

	@SafeVarargs
	private <T> T pick(T... list) {
		return list[this.rnd.nextInt(list.length)];
	}

	private boolean chance(double p) {
		return this.rnd.nextDouble() < p;
	}

	// ---------- состояния блоков ----------

	private static BlockState d(Block b) {
		return b.getDefaultState();
	}

	private static BlockState facing(Block b, Direction dir) {
		return b.getDefaultState().with(Properties.HORIZONTAL_FACING, dir);
	}

	private static BlockState up(Block b) {
		return b.getDefaultState().with(Properties.FACING, Direction.UP);
	}

	private static BlockState log(Block b, Direction.Axis axis) {
		return b.getDefaultState().with(Properties.AXIS, axis);
	}

	private static BlockState leaves(Block b) {
		return b.getDefaultState().with(Properties.PERSISTENT, true);
	}

	private static BlockState slab(Block b, boolean top) {
		return b.getDefaultState().with(Properties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
	}

	private static BlockState stairs(Block b, Direction dir, boolean top) {
		return b.getDefaultState().with(Properties.HORIZONTAL_FACING, dir)
				.with(Properties.BLOCK_HALF, top ? BlockHalf.TOP : BlockHalf.BOTTOM);
	}

	private static BlockState trapdoor(Block b, Direction dir, boolean top) {
		return b.getDefaultState().with(Properties.HORIZONTAL_FACING, dir)
				.with(Properties.BLOCK_HALF, top ? BlockHalf.TOP : BlockHalf.BOTTOM)
				.with(Properties.OPEN, false);
	}

	private static BlockState lantern(boolean hanging) {
		return Blocks.LANTERN.getDefaultState().with(Properties.HANGING, hanging);
	}

	private static BooleanProperty side(Direction dir) {
		return switch (dir) {
			case NORTH -> Properties.NORTH;
			case SOUTH -> Properties.SOUTH;
			case EAST -> Properties.EAST;
			default -> Properties.WEST;
		};
	}

	private void ladder(int x, int y0, int y1, int z, Direction dir) {
		for (int y = y0; y <= y1; y++) {
			set(x, y, z, facing(Blocks.LADDER, dir));
			protect(x, y, z);
			protect(x - dir.getOffsetX(), y, z - dir.getOffsetZ());
		}
	}

	private void door(int x, int y, int z, Block b, Direction dir) {
		door(x, y, z, b, dir, false);
	}

	private void door(int x, int y, int z, Block b, Direction dir, boolean rightHinge) {
		BlockState s = b.getDefaultState().with(Properties.HORIZONTAL_FACING, dir)
				.with(Properties.DOOR_HINGE, rightHinge ? DoorHinge.RIGHT : DoorHinge.LEFT);
		set(x, y, z, s.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
		set(x, y + 1, z, s.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
		for (int k = -1; k <= 1; k++) {
			protect(x, y + k, z);
		}
	}

	/** Кровать: изголовье в (x, z), ноги со стороны, противоположной dir. */
	private void bed(int x, int y, int z, Direction dir, Block b) {
		set(x, y, z, b.getDefaultState().with(Properties.HORIZONTAL_FACING, dir).with(Properties.BED_PART, BedPart.HEAD));
		set(x - dir.getOffsetX(), y, z - dir.getOffsetZ(),
				b.getDefaultState().with(Properties.HORIZONTAL_FACING, dir).with(Properties.BED_PART, BedPart.FOOT));
	}

	private void tallPlant(int x, int y, int z, Block b) {
		set(x, y, z, b.getDefaultState().with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
		set(x, y + 1, z, b.getDefaultState().with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
	}

	/** Люк: деревянный люк в земле над лестницей, лестница вниз до y = 1. */
	private void hatch(int x, int z, Direction dir) {
		set(x - dir.getOffsetX(), 1, z - dir.getOffsetZ(), d(Blocks.STONE_BRICKS));
		set(x - dir.getOffsetX(), 2, z - dir.getOffsetZ(), d(Blocks.STONE_BRICKS));
		ladder(x, 1, 2, z, dir);
		set(x, G, z, trapdoor(Blocks.SPRUCE_TRAPDOOR, dir, true));
		protect(x, G, z);
		protect(x, G + 1, z);
	}

	// ---------- рельеф, дороги, периметр ----------

	private void terrain() {
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				set(x, 0, z, d(Blocks.DEEPSLATE));
				set(x, 1, z, d(Blocks.DIRT));
				set(x, 2, z, d(Blocks.DIRT));
				double r = this.rnd.nextDouble();
				BlockState top = r < 0.06 ? d(Blocks.COARSE_DIRT) : r < 0.085 ? d(Blocks.MOSS_BLOCK)
						: r < 0.1 ? d(Blocks.PODZOL) : d(Blocks.GRASS_BLOCK);
				set(x, G, z, top);
			}
		}
	}

	private void roads() {
		for (int x = 25; x <= 124; x++) {
			for (int z = 72; z <= 78; z++) {
				road(x, z, z == 72 || z == 78, z == 75 && (x / 3) % 2 == 0);
			}
			for (int z = 108; z <= 112; z++) {
				road(x, z, z == 108 || z == 112, z == 110 && (x / 3) % 2 == 0);
			}
		}
		mark(25, 72, 124, 78);
		mark(25, 108, 124, 112);
		for (int z = 4; z <= 145; z++) {
			for (int x = 72; x <= 78; x++) {
				boolean cross = (z >= 72 && z <= 78) || (z >= 108 && z <= 112);
				if (cross && (x == 72 || x == 78)) {
					continue;
				}
				road(x, z, !cross && (x == 72 || x == 78), !cross && x == 75 && (z / 3) % 2 == 0);
			}
		}
		mark(72, 4, 78, 145);
		for (int x = 73; x <= 77; x += 2) {
			for (int z : new int[] {69, 70, 80, 81, 106, 107, 113, 114}) {
				set(x, G, z, d(Blocks.WHITE_CONCRETE));
			}
		}
		for (int x : new int[] {69, 70, 80, 81}) {
			for (int z = 73; z <= 77; z += 2) {
				set(x, G, z, d(Blocks.WHITE_CONCRETE));
			}
			set(x, G, 109, d(Blocks.WHITE_CONCRETE));
			set(x, G, 111, d(Blocks.WHITE_CONCRETE));
		}
		for (int x = 25; x <= 124; x++) {
			for (int z : new int[] {70, 71, 79, 80, 106, 107, 113, 114}) {
				sidewalk(x, z);
			}
		}
		for (int z = 4; z <= 145; z++) {
			for (int x : new int[] {70, 71, 79, 80}) {
				sidewalk(x, z);
			}
		}
	}

	private static boolean roadCell(int x, int z) {
		return (x >= 72 && x <= 78 && z >= 4 && z <= 145)
				|| (x >= 25 && x <= 124 && ((z >= 72 && z <= 78) || (z >= 108 && z <= 112)));
	}

	private void sidewalk(int x, int z) {
		if (roadCell(x, z)) {
			return;
		}
		BlockState cur = get(x, G, z);
		if (cur != null && cur.isOf(Blocks.WHITE_CONCRETE)) {
			return;
		}
		double r = this.rnd.nextDouble();
		set(x, G, z, r < 0.62 ? d(Blocks.POLISHED_ANDESITE) : r < 0.8 ? d(Blocks.ANDESITE)
				: r < 0.9 ? d(Blocks.STONE_BRICKS) : d(Blocks.CRACKED_STONE_BRICKS));
		set(x, G - 1, z, d(Blocks.STONE));
		mark(x, z, x, z);
	}

	private boolean isWalk(int x, int z) {
		BlockState g = get(x, G, z);
		return g != null && !solid(x, G + 1, z) && !roadCell(x, z) && (g.isOf(Blocks.POLISHED_ANDESITE)
				|| g.isOf(Blocks.ANDESITE) || g.isOf(Blocks.STONE_BRICKS) || g.isOf(Blocks.CRACKED_STONE_BRICKS));
	}

	private static boolean asphalt(BlockState t) {
		return t != null && (t.isOf(Blocks.BLACK_CONCRETE) || t.isOf(Blocks.GRAY_CONCRETE) || t.isOf(Blocks.BLACKSTONE));
	}

	private void road(int x, int z, boolean curb, boolean line) {
		BlockState s;
		if (curb) {
			s = d(Blocks.SMOOTH_STONE);
		} else if (line) {
			s = d(Blocks.YELLOW_CONCRETE);
		} else {
			double r = this.rnd.nextDouble();
			s = r < 0.04 ? d(Blocks.GRAVEL) : r < 0.1 ? d(Blocks.BLACKSTONE) : r < 0.14 ? d(Blocks.GRAY_CONCRETE)
					: d(Blocks.BLACK_CONCRETE);
		}
		set(x, G, z, s);
		set(x, G - 1, z, d(Blocks.STONE));
	}

	private void perimeter() {
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				set(x, HEIGHT - 1, z, d(Blocks.BARRIER));
				boolean edge = x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1;
				if (!edge) {
					continue;
				}
				boolean post = (x + z) % 12 == 0;
				for (int y = 1; y <= G; y++) {
					set(x, y, z, d(Blocks.STONE_BRICKS));
				}
				for (int y = G + 1; y <= G + 3; y++) {
					set(x, y, z, post ? d(Blocks.POLISHED_ANDESITE)
							: pick(d(Blocks.STONE_BRICKS), d(Blocks.STONE_BRICKS), d(Blocks.MOSSY_STONE_BRICKS),
							d(Blocks.CRACKED_STONE_BRICKS)));
				}
				set(x, G + 4, z, post ? d(Blocks.POLISHED_ANDESITE) : d(Blocks.IRON_BARS));
				for (int y = G + 5; y < HEIGHT - 1; y++) {
					set(x, y, z, d(Blocks.BARRIER));
				}
			}
		}
	}

	/** Стёкла, решётки и заборы: связи считаются по соседям, т.к. блоки ставятся без обновлений. */
	private void connect() {
		Direction[] dirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
		for (int x = 0; x < SIZE; x++) {
			for (int y = 1; y < HEIGHT - 1; y++) {
				for (int z = 0; z < SIZE; z++) {
					BlockState s = get(x, y, z);
					if (s == null) {
						continue;
					}
					Block b = s.getBlock();
					boolean pane = b instanceof PaneBlock;
					boolean fence = b instanceof FenceBlock;
					if (!pane && !fence) {
						continue;
					}
					for (Direction dir : dirs) {
						BlockState n = get(x + dir.getOffsetX(), y, z + dir.getOffsetZ());
						boolean link = false;
						if (n != null && !n.isAir()) {
							Block nb = n.getBlock();
							if (pane && nb instanceof PaneBlock) {
								link = true;
							} else if (fence && nb instanceof FenceBlock
									&& (nb == Blocks.NETHER_BRICK_FENCE) == (b == Blocks.NETHER_BRICK_FENCE)) {
								link = true;
							} else if (n.isOpaque() && !(nb instanceof SlabBlock) && !(nb instanceof StairsBlock)
									&& !(nb instanceof LeavesBlock) && !(nb instanceof TrapdoorBlock)
									&& !(nb instanceof DoorBlock) && nb != Blocks.BARRIER) {
								link = true;
							}
						}
						s = s.with(side(dir), link);
					}
					set(x, y, z, s);
				}
			}
		}
	}

	// ---------- базы ----------

	private int bx(int team, int x) {
		return team == 0 ? x : SIZE - 1 - x;
	}

	private Direction bd(int team, Direction dir) {
		return team == 0 || dir.getAxis() != Direction.Axis.X ? dir : dir.getOpposite();
	}

	private void base(int team) {
		BlockState wool = d(team == 0 ? Blocks.RED_WOOL : Blocks.BLUE_WOOL);
		BlockState concrete = d(team == 0 ? Blocks.RED_CONCRETE : Blocks.BLUE_CONCRETE);
		Block bedBlock = team == 0 ? Blocks.RED_BED : Blocks.BLUE_BED;
		mark(bx(team, 1), 55, bx(team, 24), 95);
		for (int x = 2; x <= 23; x++) {
			for (int z = 56; z <= 94; z++) {
				double r = this.rnd.nextDouble();
				set(bx(team, x), G, z, r < 0.35 ? d(Blocks.COARSE_DIRT) : r < 0.5 ? d(Blocks.GRAVEL)
						: r < 0.55 ? d(Blocks.ROOTED_DIRT) : d(Blocks.GRASS_BLOCK));
			}
		}
		for (int x = 4; x <= 14; x++) {
			for (int z = 67; z <= 83; z++) {
				boolean rim = x == 4 || x == 14 || z == 67 || z == 83;
				set(bx(team, x), G, z, rim ? d(Blocks.SMOOTH_STONE) : concrete);
			}
		}
		for (int z = 72; z <= 78; z++) {
			for (int x = 15; x <= 24; x++) {
				set(bx(team, x), G, z, d(z == 72 || z == 78 ? Blocks.SMOOTH_STONE : Blocks.GRAY_CONCRETE));
			}
		}
		for (int z : new int[] {65, 85}) {
			int px = bx(team, 6);
			for (int y = G + 1; y <= G + 7; y++) {
				set(px, y, z, d(Blocks.DARK_OAK_FENCE));
			}
			for (int i = 1; i <= 3; i++) {
				set(bx(team, 6 + i), G + 6, z, wool);
				set(bx(team, 6 + i), G + 7, z, wool);
			}
		}
		// мешки с песком по фронту базы, проход по дороге свободен
		for (int z = 57; z <= 93; z++) {
			if (z >= 71 && z <= 79) {
				continue;
			}
			int seg = Math.floorMod(z - 57, 7);
			if (seg >= 5) {
				continue;
			}
			sandbag(bx(team, 24), z, 2);
		}
		for (int x = 3; x <= 12; x++) {
			if (x % 5 == 4) {
				continue;
			}
			sandbag(bx(team, x), 56, 2);
			sandbag(bx(team, x), 94, 2);
		}
		for (int z : new int[] {59, 87}) {
			tent(team, 14, z, wool, bedBlock);
		}
		campNet(team, 3, 57, 11, 64);
		campNet(team, 3, 86, 11, 93);
		watchtower(team, 17, 71);
		for (int z = 89; z <= 92; z++) {
			set(bx(team, 20), G + 1, z, up(Blocks.BARREL));
		}
		set(bx(team, 21), G + 1, 90, d(Blocks.SPRUCE_PLANKS));
		set(bx(team, 21), G + 2, 90, up(Blocks.BARREL));
		set(bx(team, 21), G + 1, 61, d(Blocks.CRAFTING_TABLE));
		set(bx(team, 20), G + 1, 61, d(Blocks.SMITHING_TABLE));
	}

	private void sandbag(int x, int z, int height) {
		for (int h = 1; h < height; h++) {
			set(x, G + h, z, d(Blocks.SMOOTH_SANDSTONE));
		}
		set(x, G + height, z, slab(Blocks.SANDSTONE_SLAB, false));
	}

	private void tent(int team, int x0, int z0, BlockState wool, Block bedBlock) {
		for (int i = 0; i < 5; i++) {
			int x = bx(team, x0 + i);
			set(x, G + 1, z0, wool);
			set(x, G + 2, z0 + 1, wool);
			set(x, G + 3, z0 + 2, wool);
			set(x, G + 2, z0 + 3, wool);
			set(x, G + 1, z0 + 4, wool);
			if (i == 4) {
				set(x, G + 1, z0 + 1, wool);
				set(x, G + 1, z0 + 2, wool);
				set(x, G + 2, z0 + 2, wool);
				set(x, G + 1, z0 + 3, wool);
			}
		}
		bed(bx(team, x0 + 3), G + 1, z0 + 1, bd(team, Direction.EAST), bedBlock);
		set(bx(team, x0 + 3), G + 1, z0 + 3, up(Blocks.BARREL));
		set(bx(team, x0 + 2), G + 1, z0 + 3, lantern(false));
	}

	private void campNet(int team, int x0, int z0, int x1, int z1) {
		for (int[] c : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
			for (int y = G + 1; y <= G + 3; y++) {
				set(bx(team, c[0]), y, c[1], d(Blocks.SPRUCE_FENCE));
			}
		}
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (chance(0.82)) {
					set(bx(team, x), G + 4, z, leaves(pick(Blocks.OAK_LEAVES, Blocks.DARK_OAK_LEAVES, Blocks.JUNGLE_LEAVES)));
				}
			}
		}
		for (int i = 0; i < 7; i++) {
			int x = x0 + 1 + this.rnd.nextInt(x1 - x0 - 1);
			int z = z0 + 1 + this.rnd.nextInt(z1 - z0 - 1);
			set(bx(team, x), G + 1, z, pick(up(Blocks.BARREL), d(Blocks.SPRUCE_PLANKS), d(Blocks.GREEN_TERRACOTTA)));
			if (chance(0.5)) {
				set(bx(team, x), G + 2, z, pick(up(Blocks.BARREL), d(Blocks.GREEN_TERRACOTTA)));
			}
		}
	}

	private void watchtower(int team, int x0, int z0) {
		int top = G + 8;
		for (int[] c : new int[][] {{x0, z0}, {x0 + 4, z0}, {x0, z0 + 4}, {x0 + 4, z0 + 4}}) {
			for (int y = G + 1; y <= top + 3; y++) {
				set(bx(team, c[0]), y, c[1], log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y));
			}
		}
		for (int x = x0; x <= x0 + 4; x++) {
			for (int z = z0; z <= z0 + 4; z++) {
				boolean corner = (x == x0 || x == x0 + 4) && (z == z0 || z == z0 + 4);
				if (!corner) {
					set(bx(team, x), top, z, d(Blocks.SPRUCE_PLANKS));
					boolean rim = x == x0 || x == x0 + 4 || z == z0 || z == z0 + 4;
					if (rim) {
						set(bx(team, x), top + 1, z, d(Blocks.SPRUCE_FENCE));
					}
				}
				set(bx(team, x), top + 4, z, slab(Blocks.SPRUCE_SLAB, false));
			}
		}
		int cx = x0 + 2;
		int cz = z0 + 2;
		for (int y = G + 1; y < top; y++) {
			set(bx(team, cx), y, cz, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
		}
		ladder(bx(team, cx - 1), G + 1, top, cz, bd(team, Direction.WEST));
	}

	// ---------- промзона ----------

	private void industry() {
		for (int x = 26; x <= 124; x++) {
			for (int z = 5; z <= 66; z++) {
				if (isBusy(x, z)) {
					continue;
				}
				double r = this.rnd.nextDouble();
				BlockState s = r < 0.2 ? d(Blocks.COARSE_DIRT) : r < 0.32 ? d(Blocks.GRAVEL) : r < 0.45 ? d(Blocks.ANDESITE)
						: r < 0.52 ? d(Blocks.STONE) : r < 0.56 ? d(Blocks.CRACKED_STONE_BRICKS) : d(Blocks.GRASS_BLOCK);
				set(x, G, z, s);
			}
		}
		hangar(28, 10, 63, 37);
		workshop(86, 12, 118, 38);
		for (int x = 26; x <= 124; x++) {
			if (x >= 69 && x <= 81) {
				continue;
			}
			if (Math.floorMod(x - 26, 17) >= 14) {
				continue;
			}
			boolean post = x % 8 == 0;
			set(x, G + 1, 67, post ? log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y) : d(Blocks.IRON_BARS));
			set(x, G + 2, 67, post ? log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y) : d(Blocks.IRON_BARS));
			if (post) {
				set(x, G + 3, 67, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y));
			}
		}
		mark(26, 67, 124, 67);
		BlockState[] colors = {d(Blocks.RED_TERRACOTTA), d(Blocks.BLUE_TERRACOTTA), d(Blocks.GREEN_TERRACOTTA),
				d(Blocks.ORANGE_TERRACOTTA), d(Blocks.CYAN_TERRACOTTA), d(Blocks.BROWN_TERRACOTTA)};
		int[][] boxes = {
				{28, 44, 1, 0}, {36, 44, 1, 1}, {48, 46, 1, 0}, {58, 43, 1, 0},
				{30, 52, 0, 0}, {40, 54, 0, 1}, {52, 55, 0, 0}, {62, 52, 0, 0},
				{34, 60, 1, 0}, {46, 61, 1, 0}, {58, 61, 1, 1}, {82, 54, 0, 0}
		};
		for (int[] b : boxes) {
			boolean alongX = b[2] == 1;
			int x1 = b[0] + (alongX ? 5 : 2);
			int z1 = b[1] + (alongX ? 2 : 5);
			if (!free(b[0], b[1], x1, z1)) {
				continue;
			}
			BlockState color = colors[this.rnd.nextInt(colors.length)];
			container(b[0], b[1], alongX, G + 1, color);
			if (b[3] == 1) {
				container(b[0], b[1], alongX, G + 4, colors[this.rnd.nextInt(colors.length)]);
			}
			mark(b[0] - 1, b[1] - 1, x1 + 1, z1 + 1);
		}
		tank(100, 53);
		tank(111, 53);
		pipe(100, 49, 40);
		pipe(111, 49, 40);
		truck(84, 44, d(Blocks.GREEN_CONCRETE));
		guardBooth(82, 61);
		chimney(120, 41);
		for (int i = 0; i < 14; i++) {
			int x = 27 + this.rnd.nextInt(97);
			int z = 41 + this.rnd.nextInt(24);
			crates(x, z);
		}
		for (int i = 0; i < 8; i++) {
			int x = 27 + this.rnd.nextInt(97);
			int z = 41 + this.rnd.nextInt(24);
			if (free(x, z, x + 3, z)) {
				for (int k = 0; k < 4; k++) {
					set(x + k, G + 1, z, d(Blocks.POLISHED_ANDESITE));
					set(x + k, G + 2, z, slab(Blocks.ANDESITE_SLAB, false));
				}
				mark(x, z, x + 3, z);
			}
		}
	}

	private void hangar(int x0, int z0, int x1, int z1) {
		mark(x0 - 2, z0 - 6, x1 + 2, z1 + 2);
		int top = G + 10;
		int midZ = (z0 + z1) / 2;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean lane = (z == midZ - 4 || z == midZ + 4) && x % 2 == 0;
				set(x, G, z, lane ? d(Blocks.YELLOW_CONCRETE) : d(Blocks.POLISHED_ANDESITE));
				fill(x, G + 1, z, x, top + 1, z, AIR);
			}
		}
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean wallX = z == z0 || z == z1;
				boolean wallZ = x == x0 || x == x1;
				if (!wallX && !wallZ) {
					continue;
				}
				boolean rib = wallX ? (x - x0) % 5 == 0 : (z - z0) % 5 == 0;
				for (int y = G + 1; y < top; y++) {
					BlockState s;
					if (y <= G + 2) {
						s = d(Blocks.GRAY_CONCRETE);
					} else if (rib || (wallX && wallZ)) {
						s = d(Blocks.POLISHED_ANDESITE);
					} else if (wallX && (y == top - 3 || y == top - 2)) {
						s = chance(0.12) ? AIR : d(Blocks.GLASS_PANE);
					} else {
						s = chance(0.06) ? d(Blocks.WAXED_WEATHERED_CUT_COPPER) : d(Blocks.LIGHT_GRAY_CONCRETE);
					}
					set(x, y, z, s);
				}
			}
		}
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean sky = Math.abs(z - midZ) <= 1 && (x - x0) % 4 != 0 && x > x0 && x < x1;
				set(x, top, z, sky ? d(Blocks.GLASS) : d(Blocks.WAXED_WEATHERED_CUT_COPPER));
			}
		}
		// ворота с запада и востока, рамка в жёлто-чёрную полосу
		for (int gx : new int[] {x0, x1}) {
			for (int z = midZ - 4; z <= midZ + 4; z++) {
				for (int y = G + 1; y <= G + 7; y++) {
					boolean frame = z == midZ - 4 || z == midZ + 4 || y == G + 7;
					set(gx, y, z, frame ? d((z + y) % 2 == 0 ? Blocks.YELLOW_CONCRETE : Blocks.BLACK_CONCRETE) : AIR);
				}
			}
		}
		fill(x0 + 8, G + 1, z0, x0 + 8, G + 2, z0, AIR);
		door(x0 + 8, G + 1, z0, Blocks.SPRUCE_DOOR, Direction.NORTH);
		fill(x1 - 8, G + 1, z1, x1 - 8, G + 2, z1, AIR);
		// антресоль вдоль северной стены
		int mez = G + 5;
		for (int x = x0 + 1; x <= x1 - 1; x++) {
			for (int z = z0 + 1; z <= z0 + 5; z++) {
				set(x, mez, z, slab(Blocks.SMOOTH_STONE_SLAB, true));
			}
			set(x, mez + 1, z0 + 5, d(Blocks.IRON_BARS));
		}
		int ladderX = x0 + 9;
		for (int px = x0 + 3; px < x1; px += 6) {
			for (int y = G + 1; y < mez; y++) {
				set(px, y, z0 + 5, log(Blocks.POLISHED_BASALT, Direction.Axis.Y));
			}
		}
		ladder(ladderX, G + 1, mez + 1, z0 + 6, Direction.SOUTH);
		set(ladderX, mez + 1, z0 + 5, AIR);
		int ox = x1 - 7;
		for (int x = ox; x <= x1 - 1; x++) {
			for (int z = z0 + 1; z <= z0 + 5; z++) {
				boolean rim = x == ox || z == z0 + 5;
				if (rim) {
					for (int y = mez + 1; y <= mez + 3; y++) {
						set(x, y, z, y == mez + 2 && z == z0 + 5 && x != ox ? d(Blocks.GLASS_PANE) : d(Blocks.WHITE_CONCRETE));
					}
				}
				set(x, mez + 4, z, d(Blocks.SMOOTH_STONE));
			}
		}
		fill(ox, mez + 1, z0 + 3, ox, mez + 2, z0 + 3, AIR);
		set(x1 - 2, mez + 1, z0 + 2, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, true));
		set(x1 - 3, mez + 1, z0 + 2, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, true));
		set(x1 - 2, mez + 1, z0 + 3, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
		set(x1 - 5, mez + 1, z0 + 1, d(Blocks.BOOKSHELF));
		set(x1 - 4, mez + 1, z0 + 1, d(Blocks.CARTOGRAPHY_TABLE));
		set(x1 - 3, mez + 2, z0 + 2, lantern(false));
		for (int x = x0 + 1; x < ox - 1; x++) {
			if (x == ladderX || x == ladderX - 1 || x == ladderX + 1) {
				continue;
			}
			if (chance(0.35)) {
				set(x, mez + 1, z0 + 1, pick(up(Blocks.BARREL), d(Blocks.SPRUCE_PLANKS), d(Blocks.OAK_PLANKS)));
				if (chance(0.4)) {
					set(x, mez + 2, z0 + 1, up(Blocks.BARREL));
				}
			}
		}
		// станки у южной стены
		Block[] machines = {Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.STONECUTTER, Blocks.FURNACE, Blocks.LOOM};
		for (int x = x0 + 2; x <= x1 - 2; x++) {
			if (Math.abs(x - (x1 - 8)) <= 1) {
				continue;
			}
			if (chance(0.55)) {
				Block m = machines[this.rnd.nextInt(machines.length)];
				set(x, G + 1, z1 - 1, facing(m, Direction.NORTH));
			} else if (chance(0.4)) {
				set(x, G + 1, z1 - 1, pick(d(Blocks.SMITHING_TABLE), d(Blocks.CAULDRON), d(Blocks.CRAFTING_TABLE)));
			}
		}
		// кран-балка с крюком
		int craneX = (x0 + x1) / 2;
		for (int z = z0 + 1; z <= z1 - 1; z++) {
			set(craneX, top - 1, z, d(Blocks.YELLOW_CONCRETE));
		}
		for (int y = G + 4; y < top - 1; y++) {
			set(craneX, y, midZ + 2, d(Blocks.CHAIN));
		}
		set(craneX, G + 3, midZ + 2, d(Blocks.CAULDRON));
		// штабели ящиков — укрытия
		for (int i = 0; i < 9; i++) {
			int x = x0 + 3 + this.rnd.nextInt(x1 - x0 - 6);
			int z = z0 + 8 + this.rnd.nextInt(z1 - z0 - 10);
			if (Math.abs(z - midZ) <= 1 && chance(0.6)) {
				continue;
			}
			int w = 1 + this.rnd.nextInt(2);
			int dd = 1 + this.rnd.nextInt(2);
			int h = 1 + this.rnd.nextInt(3);
			for (int a = 0; a <= w; a++) {
				for (int b = 0; b <= dd; b++) {
					int hh = Math.max(1, h - this.rnd.nextInt(2));
					for (int y = G + 1; y <= G + hh; y++) {
						set(x + a, y, z + b, pick(up(Blocks.BARREL), d(Blocks.SPRUCE_PLANKS), d(Blocks.OAK_PLANKS),
								d(Blocks.STRIPPED_SPRUCE_WOOD)));
					}
				}
			}
		}
		// люк в бункер и подземный ход наружу (на север)
		int bx0 = x0 + 4;
		int bz0 = z1 - 9;
		bunker(bx0, bz0, bx0 + 7, bz0 + 5);
		hatch(bx0, bz0, Direction.EAST);
		fill(bx0, G + 1, bz0, bx0 + 1, G + 3, bz0, AIR);
		int tx = bx0 + 3;
		tunnel(tx, z0 - 4, bz0 - 1);
		set(tx, 1, z0 - 5, d(Blocks.STONE_BRICKS));
		set(tx, 2, z0 - 5, d(Blocks.STONE_BRICKS));
		ladder(tx, 1, 2, z0 - 4, Direction.SOUTH);
		set(tx, G, z0 - 4, trapdoor(Blocks.SPRUCE_TRAPDOOR, Direction.SOUTH, true));
		vines(x0, z0, x1, z1, G + 1, top - 1);
	}

	private void bunker(int x0, int z0, int x1, int z1) {
		for (int x = x0 - 1; x <= x1 + 1; x++) {
			for (int z = z0 - 1; z <= z1 + 1; z++) {
				boolean wall = x == x0 - 1 || x == x1 + 1 || z == z0 - 1 || z == z1 + 1;
				set(x, 1, z, wall ? d(Blocks.STONE_BRICKS) : AIR);
				set(x, 2, z, wall ? d(Blocks.STONE_BRICKS) : AIR);
				set(x, 0, z, d(Blocks.POLISHED_DEEPSLATE));
				roofOver(x, z);
			}
		}
		set(x1 + 1, 2, (z0 + z1) / 2, d(Blocks.SHROOMLIGHT));
		bed(x1, 1, z1, Direction.EAST, Blocks.GREEN_BED);
		bed(x1, 1, z1 - 2, Direction.EAST, Blocks.BROWN_BED);
		set(x0 + 2, 1, z1, up(Blocks.BARREL));
		set(x0 + 3, 1, z1, up(Blocks.BARREL));
		set(x0 + 4, 1, z1, d(Blocks.CRAFTING_TABLE));
		set(x0 + 3, 1, z0, lantern(false));
		set(x1, 1, z0, d(Blocks.COBWEB));
	}

	private void tunnel(int x, int za, int zb) {
		for (int z = Math.min(za, zb); z <= Math.max(za, zb); z++) {
			set(x, 1, z, AIR);
			set(x, 2, z, AIR);
			set(x, 0, z, d(Blocks.POLISHED_DEEPSLATE));
			for (int sx : new int[] {x - 1, x + 1}) {
				BlockState cur = get(sx, 1, z);
				if (cur != null && cur.isOf(Blocks.DIRT)) {
					set(sx, 1, z, d(Blocks.STONE_BRICKS));
				}
				cur = get(sx, 2, z);
				if (cur != null && cur.isOf(Blocks.DIRT)) {
					set(sx, 2, z, d(Blocks.STONE_BRICKS));
				}
			}
			roofOver(x, z);
			if (Math.floorMod(z, 6) == 0) {
				set(x - 1, 2, z, d(Blocks.SHROOMLIGHT));
			}
		}
	}

	/** Потолок подземелья: сыпучий или пустой блок над пустотой заменяется кирпичом. */
	private void roofOver(int x, int z) {
		BlockState top = get(x, G, z);
		if (top == null || top.isAir() || top.isOf(Blocks.GRAVEL)) {
			set(x, G, z, d(Blocks.STONE_BRICKS));
		}
	}

	private void workshop(int x0, int z0, int x1, int z1) {
		mark(x0 - 2, z0 - 2, x1 + 2, z1 + 2);
		int wallTop = G + 9;
		int midZ = (z0 + z1) / 2;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				set(x, G, z, (x + z) % 7 == 0 ? d(Blocks.GRAY_CONCRETE) : d(Blocks.SMOOTH_STONE));
				fill(x, G + 1, z, x, wallTop + 3, z, AIR);
			}
		}
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean wallX = z == z0 || z == z1;
				boolean wallZ = x == x0 || x == x1;
				if (!wallX && !wallZ) {
					continue;
				}
				int along = wallX ? x - x0 : z - z0;
				boolean pilaster = along % 5 == 0;
				for (int y = G + 1; y < wallTop; y++) {
					BlockState s;
					if (y == G + 1) {
						s = d(Blocks.STONE_BRICKS);
					} else if (pilaster || (wallX && wallZ)) {
						s = d(Blocks.STONE_BRICKS);
					} else if (y >= G + 3 && y <= G + 6 && (wallX ? along % 5 != 0 && along % 5 != 4 : along % 5 == 2)) {
						s = chance(0.1) ? AIR : d(Blocks.GLASS_PANE);
					} else {
						s = chance(0.08) ? d(Blocks.MOSSY_STONE_BRICKS) : d(Blocks.BRICKS);
					}
					set(x, y, z, s);
				}
			}
		}
		// пилообразная крыша с остеклением
		for (int x = x0; x <= x1; x++) {
			int i = Math.floorMod(x - x0, 6);
			int ry = wallTop + i / 2;
			for (int z = z0; z <= z1; z++) {
				set(x, ry, z, d(Blocks.DEEPSLATE_TILES));
				if (i == 0 && x > x0) {
					set(x, wallTop + 1, z, d(Blocks.GLASS));
					set(x, wallTop + 2, z, d(Blocks.GLASS));
				}
			}
			for (int y = wallTop; y < ry; y++) {
				set(x, y, z0, d(Blocks.BRICKS));
				set(x, y, z1, d(Blocks.BRICKS));
			}
		}
		for (int z = midZ - 3; z <= midZ + 3; z++) {
			for (int y = G + 1; y <= G + 6; y++) {
				boolean frame = z == midZ - 3 || z == midZ + 3 || y == G + 6;
				set(x0, y, z, frame ? d((z + y) % 2 == 0 ? Blocks.YELLOW_CONCRETE : Blocks.BLACK_CONCRETE) : AIR);
			}
		}
		fill(x0 + 18, G + 1, z1, x0 + 18, G + 2, z1, AIR);
		door(x0 + 18, G + 1, z1, Blocks.SPRUCE_DOOR, Direction.SOUTH);
		fill(x1 - 6, G + 1, z0, x1 - 6, G + 2, z0, AIR);
		// смотровая яма, над ней машина
		int px0 = x0 + 5;
		int px1 = x0 + 15;
		int pz = midZ + 1;
		for (int x = px0 - 1; x <= px1 + 1; x++) {
			for (int z = pz - 1; z <= pz + 2; z++) {
				boolean pit = x >= px0 && x <= px1 && z >= pz && z <= pz + 1;
				if (pit) {
					set(x, G, z, AIR);
					set(x, G - 1, z, AIR);
					set(x, G - 2, z, d(Blocks.STONE_BRICKS));
				} else {
					set(x, G - 1, z, d(Blocks.STONE_BRICKS));
					if (z == pz - 1 || z == pz + 2) {
						set(x, G, z, d(Blocks.YELLOW_CONCRETE));
					}
				}
			}
		}
		ladder(px0, G - 1, G, pz, Direction.EAST);
		car(px0 + 4, pz - 1, true, d(Blocks.RED_CONCRETE), false);
		// подвал под люком в северо-восточном углу
		int cx0 = x1 - 9;
		int cz0 = z0 + 3;
		bunker(cx0, cz0, cx0 + 5, cz0 + 3);
		hatch(cx0, cz0, Direction.EAST);
		fill(cx0, G + 1, cz0, cx0 + 1, G + 3, cz0, AIR);
		// пультовая на помосте в юго-восточном углу
		int pfY = G + 4;
		for (int x = x1 - 6; x <= x1 - 1; x++) {
			for (int z = z1 - 6; z <= z1 - 1; z++) {
				set(x, pfY, z, d(Blocks.SPRUCE_PLANKS));
				boolean rim = x == x1 - 6 || z == z1 - 6;
				if (rim) {
					for (int y = pfY + 1; y <= pfY + 3; y++) {
						set(x, y, z, y == pfY + 2 ? d(Blocks.GLASS_PANE) : d(Blocks.WHITE_CONCRETE));
					}
				}
				set(x, pfY + 4, z, d(Blocks.SMOOTH_STONE));
			}
		}
		for (int[] c : new int[][] {{x1 - 6, z1 - 6}, {x1 - 6, z1 - 1}, {x1 - 1, z1 - 6}}) {
			for (int y = G + 1; y < pfY; y++) {
				set(c[0], y, c[1], log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y));
			}
		}
		fill(x1 - 6, pfY + 1, z1 - 4, x1 - 6, pfY + 2, z1 - 4, AIR);
		ladder(x1 - 7, G + 1, pfY, z1 - 4, Direction.WEST);
		set(x1 - 6, G + 1, z1 - 4, d(Blocks.STONE_BRICKS));
		set(x1 - 6, G + 2, z1 - 4, d(Blocks.STONE_BRICKS));
		set(x1 - 6, G + 3, z1 - 4, d(Blocks.STONE_BRICKS));
		set(x1 - 2, pfY + 1, z1 - 2, facing(Blocks.LECTERN, Direction.WEST));
		set(x1 - 3, pfY + 1, z1 - 1, d(Blocks.CARTOGRAPHY_TABLE));
		set(x1 - 4, pfY + 1, z1 - 1, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH, true));
		for (int x = x1 - 5; x <= x1 - 2; x++) {
			set(x, G + 1, z1 - 2, up(Blocks.BARREL));
		}
		// линия станков и стеллажи
		Block[] machines = {Blocks.BLAST_FURNACE, Blocks.FURNACE, Blocks.SMOKER, Blocks.STONECUTTER};
		for (int x = x0 + 3; x <= x0 + 22; x++) {
			if (x % 4 == 0) {
				continue;
			}
			set(x, G + 1, z0 + 4, chance(0.6) ? facing(machines[this.rnd.nextInt(machines.length)], Direction.SOUTH)
					: pick(d(Blocks.SMITHING_TABLE), d(Blocks.CRAFTING_TABLE), d(Blocks.CAULDRON)));
			set(x, G + 1, z0 + 5, d(Blocks.BLACK_CARPET));
		}
		rack(x0 + 2, z1 - 1);
		rack(x0 + 8, z1 - 1);
		for (int i = 0; i < 6; i++) {
			int x = x0 + 3 + this.rnd.nextInt(x1 - x0 - 6);
			int z = z0 + 7 + this.rnd.nextInt(z1 - z0 - 12);
			if (get(x, G, z) == null || get(x, G, z).isAir()) {
				continue;
			}
			for (int y = wallTop - 1; y >= G + 5; y--) {
				set(x, y, z, d(Blocks.CHAIN));
			}
			set(x, G + 4, z, lantern(true));
		}
		vines(x0, z0, x1, z1, G + 1, wallTop - 1);
	}

	private void rack(int x, int z) {
		for (int y = G + 1; y <= G + 5; y++) {
			set(x, y, z, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y));
			set(x + 4, y, z, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y));
		}
		for (int i = 1; i <= 3; i++) {
			set(x + i, G + 2, z, slab(Blocks.SPRUCE_SLAB, true));
			set(x + i, G + 4, z, slab(Blocks.SPRUCE_SLAB, true));
			if (chance(0.6)) {
				set(x + i, G + 1, z, up(Blocks.BARREL));
			}
			if (chance(0.5)) {
				set(x + i, G + 3, z, pick(up(Blocks.BARREL), d(Blocks.POTTED_CACTUS), d(Blocks.COMPOSTER)));
			}
		}
	}

	private void container(int x0, int z0, boolean alongX, int y0, BlockState color) {
		int len = 6;
		int wid = 3;
		boolean openFar = chance(0.5);
		for (int i = 0; i < len; i++) {
			for (int j = 0; j < wid; j++) {
				int x = alongX ? x0 + i : x0 + j;
				int z = alongX ? z0 + j : z0 + i;
				boolean shellSide = j == 0 || j == wid - 1;
				boolean end = i == 0 || i == len - 1;
				for (int y = y0; y <= y0 + 2; y++) {
					boolean roof = y == y0 + 2;
					boolean wall = shellSide || end || roof;
					boolean open = end && !shellSide && !roof && ((i == 0 && !openFar) || (i == len - 1 && openFar));
					boolean openSide = end && !roof && ((i == 0 && !openFar) || (i == len - 1 && openFar));
					BlockState s = wall ? ((i % 2 == 0 && shellSide && !roof) ? d(Blocks.LIGHT_GRAY_TERRACOTTA) : color) : AIR;
					if (open || (openSide && !shellSide)) {
						s = AIR;
					}
					set(x, y, z, s);
				}
			}
		}
		int ix = alongX ? x0 + (openFar ? 1 : 4) : x0 + 1;
		int iz = alongX ? z0 + 1 : z0 + (openFar ? 1 : 4);
		set(ix, y0, iz, up(Blocks.BARREL));
	}

	private void tank(int cx, int cz) {
		mark(cx - 4, cz - 4, cx + 4, cz + 4);
		for (int x = cx - 4; x <= cx + 4; x++) {
			for (int z = cz - 4; z <= cz + 4; z++) {
				double dist = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
				if (dist <= 3.6) {
					set(x, G, z, d(Blocks.SMOOTH_STONE));
				}
				if (dist > 3.2) {
					continue;
				}
				for (int y = G + 1; y <= G + 7; y++) {
					BlockState s = y == G + 1 ? d(Blocks.POLISHED_ANDESITE)
							: y == G + 4 ? d(Blocks.RED_CONCRETE) : d(Blocks.WHITE_CONCRETE);
					set(x, y, z, s);
				}
				if (dist <= 2.3) {
					set(x, G + 8, z, d(Blocks.SMOOTH_QUARTZ));
				}
			}
		}
	}

	private void pipe(int x, int za, int zb) {
		for (int z = Math.min(za, zb); z <= Math.max(za, zb); z++) {
			set(x, G + 5, z, d(Blocks.LIGHT_GRAY_TERRACOTTA));
			if (Math.floorMod(z, 4) == 0) {
				for (int y = G + 1; y <= G + 4; y++) {
					if (!solid(x, y, z)) {
						set(x, y, z, d(Blocks.STONE_BRICKS));
					}
				}
			}
		}
	}

	private void truck(int x0, int z0, BlockState body) {
		mark(x0 - 1, z0 - 1, x0 + 3, z0 + 8);
		for (int i = 0; i < 8; i++) {
			for (int j = 0; j < 3; j++) {
				int x = x0 + j;
				int z = z0 + i;
				boolean wheel = (i == 1 || i == 6) && (j == 0 || j == 2);
				set(x, G + 1, z, wheel ? d(Blocks.BLACK_CONCRETE) : d(Blocks.GRAY_CONCRETE));
				if (i <= 1) {
					set(x, G + 2, z, body);
					set(x, G + 3, z, j == 1 || i == 0 ? d(Blocks.BLACK_STAINED_GLASS) : body);
					set(x, G + 4, z, body);
				} else {
					set(x, G + 2, z, body);
					for (int y = G + 3; y <= G + 4; y++) {
						set(x, y, z, j == 1 ? AIR : d(Blocks.GREEN_WOOL));
					}
					set(x, G + 5, z, d(Blocks.GREEN_WOOL));
				}
			}
		}
	}

	private void car(int x0, int z0, boolean alongX, BlockState body, boolean burned) {
		if (burned) {
			this.flames.add(new int[] {alongX ? x0 + 2 : x0 + 1, G + 1, alongX ? z0 + 1 : z0 + 2});
		}
		for (int i = 0; i < 5; i++) {
			for (int j = 0; j < 3; j++) {
				int x = alongX ? x0 + i : x0 + j;
				int z = alongX ? z0 + j : z0 + i;
				boolean wheel = (i == 0 || i == 4) && (j == 0 || j == 2);
				set(x, G + 1, z, wheel ? d(Blocks.BLACK_CONCRETE) : burned ? d(Blocks.COAL_BLOCK) : body);
				set(x, G + 2, z, burned && chance(0.3) ? d(Blocks.BLACKSTONE) : body);
				if (i >= 1 && i <= 3) {
					boolean glass = i == 1 || i == 3 || j != 1;
					set(x, G + 3, z, glass ? (burned ? AIR : d(Blocks.BLACK_STAINED_GLASS)) : AIR);
					if (!burned || chance(0.5)) {
						set(x, G + 4, z, burned ? d(Blocks.BLACKSTONE) : body);
					}
				}
			}
		}
	}

	private void guardBooth(int x0, int z0) {
		mark(x0 - 1, z0 - 1, x0 + 4, z0 + 4);
		for (int x = x0; x <= x0 + 3; x++) {
			for (int z = z0; z <= z0 + 3; z++) {
				set(x, G, z, d(Blocks.SMOOTH_STONE));
				boolean rim = x == x0 || x == x0 + 3 || z == z0 || z == z0 + 3;
				for (int y = G + 1; y <= G + 3; y++) {
					set(x, y, z, rim ? (y == G + 2 && (x == x0 + 1 || x == x0 + 2 || z == z0 + 1 || z == z0 + 2)
							? d(Blocks.GLASS_PANE) : d(Blocks.WHITE_CONCRETE)) : AIR);
				}
				set(x, G + 4, z, slab(Blocks.SMOOTH_STONE_SLAB, false));
			}
		}
		fill(x0, G + 1, z0 + 2, x0, G + 2, z0 + 2, AIR);
		set(x0 + 2, G + 1, z0 + 1, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST, false));
		set(x0 + 2, G + 1, z0 + 2, slab(Blocks.SPRUCE_SLAB, true));
	}

	private void chimney(int x0, int z0) {
		mark(x0 - 1, z0 - 1, x0 + 3, z0 + 3);
		for (int x = x0; x <= x0 + 2; x++) {
			for (int z = z0; z <= z0 + 2; z++) {
				boolean core = x == x0 + 1 && z == z0 + 1;
				for (int y = G + 1; y <= G + 19; y++) {
					set(x, y, z, core && y > G + 1 ? AIR : (y % 6 == 0 ? d(Blocks.STONE_BRICKS) : d(Blocks.BRICKS)));
				}
			}
		}
	}

	private void crates(int x, int z) {
		if (!free(x - 1, z - 1, x + 2, z + 2)) {
			return;
		}
		for (int a = 0; a < 2; a++) {
			for (int b = 0; b < 2; b++) {
				if (chance(0.25)) {
					continue;
				}
				int h = 1 + this.rnd.nextInt(2);
				for (int y = G + 1; y <= G + h; y++) {
					set(x + a, y, z + b, pick(up(Blocks.BARREL), d(Blocks.SPRUCE_PLANKS), d(Blocks.STRIPPED_SPRUCE_WOOD)));
				}
			}
		}
		mark(x - 1, z - 1, x + 2, z + 2);
	}

	private void vines(int x0, int z0, int x1, int z1, int y0, int y1) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				if (!edge || !chance(0.12)) {
					continue;
				}
				Direction out = z == z0 ? Direction.NORTH : z == z1 ? Direction.SOUTH : x == x0 ? Direction.WEST : Direction.EAST;
				int vx = x + out.getOffsetX();
				int vz = z + out.getOffsetZ();
				int start = y0 + this.rnd.nextInt(Math.max(1, y1 - y0));
				int len = 2 + this.rnd.nextInt(4);
				for (int y = start; y > start - len && y >= y0; y--) {
					if (!solid(x, y, z) || solid(vx, y, vz)) {
						break;
					}
					set(vx, y, vz, Blocks.VINE.getDefaultState().with(side(out.getOpposite()), true));
				}
			}
		}
	}

	// ---------- город ----------

	private void city() {
		Pal brick = new Pal(d(Blocks.BRICKS), log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y), d(Blocks.STONE_BRICKS),
				d(Blocks.SPRUCE_PLANKS), d(Blocks.WHITE_TERRACOTTA), Blocks.STONE_BRICK_STAIRS, d(Blocks.GRAY_CONCRETE),
				Blocks.SPRUCE_DOOR);
		Pal white = new Pal(d(Blocks.WHITE_TERRACOTTA), log(Blocks.DARK_OAK_LOG, Direction.Axis.Y), d(Blocks.DARK_OAK_PLANKS),
				d(Blocks.DARK_OAK_PLANKS), d(Blocks.SMOOTH_SANDSTONE), Blocks.DEEPSLATE_TILE_STAIRS, d(Blocks.DEEPSLATE_TILES),
				Blocks.DARK_OAK_DOOR);
		Pal panel = new Pal(d(Blocks.LIGHT_GRAY_CONCRETE), d(Blocks.GRAY_CONCRETE), d(Blocks.GRAY_CONCRETE),
				d(Blocks.BIRCH_PLANKS), d(Blocks.WHITE_CONCRETE), Blocks.STONE_STAIRS, d(Blocks.SMOOTH_STONE),
				Blocks.BIRCH_DOOR);
		Pal wood = new Pal(d(Blocks.SPRUCE_PLANKS), log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y), d(Blocks.COBBLESTONE),
				d(Blocks.OAK_PLANKS), d(Blocks.BIRCH_PLANKS), Blocks.SPRUCE_STAIRS, d(Blocks.SPRUCE_PLANKS),
				Blocks.OAK_DOOR);
		Pal mud = new Pal(d(Blocks.MUD_BRICKS), log(Blocks.OAK_LOG, Direction.Axis.Y), d(Blocks.PACKED_MUD),
				d(Blocks.OAK_PLANKS), d(Blocks.SMOOTH_SANDSTONE), Blocks.BRICK_STAIRS, d(Blocks.BRICKS),
				Blocks.JUNGLE_DOOR);
		Pal stone = new Pal(d(Blocks.STONE_BRICKS), d(Blocks.POLISHED_ANDESITE), d(Blocks.MOSSY_STONE_BRICKS),
				d(Blocks.SPRUCE_PLANKS), d(Blocks.WHITE_TERRACOTTA), Blocks.COBBLESTONE_STAIRS, d(Blocks.ANDESITE),
				Blocks.SPRUCE_DOOR);
		Pal plaster = new Pal(d(Blocks.SMOOTH_SANDSTONE), log(Blocks.STRIPPED_OAK_LOG, Direction.Axis.Y), d(Blocks.CUT_SANDSTONE),
				d(Blocks.OAK_PLANKS), d(Blocks.WHITE_TERRACOTTA), Blocks.BRICK_STAIRS, d(Blocks.BRICKS),
				Blocks.ACACIA_DOOR);
		Pal cyan = new Pal(d(Blocks.CYAN_TERRACOTTA), log(Blocks.DARK_OAK_LOG, Direction.Axis.Y), d(Blocks.STONE_BRICKS),
				d(Blocks.SPRUCE_PLANKS), d(Blocks.WHITE_TERRACOTTA), Blocks.DEEPSLATE_TILE_STAIRS, d(Blocks.DEEPSLATE_TILES),
				Blocks.SPRUCE_DOOR);
		Pal school = new Pal(d(Blocks.TERRACOTTA), d(Blocks.WHITE_CONCRETE), d(Blocks.WHITE_CONCRETE),
				d(Blocks.OAK_PLANKS), d(Blocks.WHITE_TERRACOTTA), Blocks.STONE_BRICK_STAIRS, d(Blocks.GRAY_CONCRETE),
				Blocks.BIRCH_DOOR);
		// квартал у главной дороги, запад
		int[] d1 = house(28, 84, 44, 98, 3, brick, true, Direction.NORTH);
		path(d1[0], d1[1], d1[0], 81);
		damage(28, 84, 44, 98, 3, 1, brick.wall());
		shop(50, 83, 66, 95);
		int[] d2 = house(50, 98, 62, 104, 2, plaster, true, Direction.SOUTH);
		path(d2[0], d2[1], d2[0], 105);
		damage(50, 98, 62, 104, 2, 2, plaster.wall());
		shed(30, 101, Direction.NORTH);
		garden(38, 101, 46, 105);
		// квартал у главной дороги, восток
		int[] d3 = house(84, 84, 98, 100, 3, panel, true, Direction.WEST);
		path(d3[0], d3[1], 81, d3[1]);
		damage(84, 84, 98, 100, 3, 2, panel.wall());
		gasStation(103, 81, 123, 104);
		// за переулком, запад
		int[] d4 = house(28, 117, 40, 128, 2, white, false, Direction.NORTH);
		path(d4[0], d4[1], d4[0], 115);
		damage(28, 117, 40, 128, 2, 0, white.wall());
		int[] d5 = house(48, 117, 60, 128, 2, mud, false, Direction.NORTH);
		path(d5[0], d5[1], d5[0], 115);
		damage(48, 117, 60, 128, 2, 1, mud.wall());
		int[] d6 = house(28, 133, 40, 144, 2, wood, false, Direction.EAST);
		path(d6[0], d6[1], 45, d6[1]);
		burn(28, 133, 40, 144, 2);
		square(46, 133, 68, 145);
		shed(64, 117, Direction.WEST);
		garden(63, 124, 69, 130);
		yardFence(26, 115, 42, 130, d4[0]);
		// за переулком, восток
		int[] d7 = house(84, 117, 96, 128, 2, stone, true, Direction.WEST);
		path(d7[0], d7[1], 81, d7[1]);
		damage(84, 117, 96, 128, 2, 1, stone.wall());
		int[] d8 = house(108, 117, 121, 128, 2, cyan, false, Direction.NORTH);
		path(d8[0], d8[1], d8[0], 115);
		damage(108, 117, 121, 128, 2, 0, cyan.wall());
		int[] d9 = house(84, 133, 104, 144, 2, school, true, Direction.NORTH);
		path(d9[0], d9[1], 82, d9[1]);
		path(82, d9[1], 82, 115);
		damage(84, 133, 104, 144, 2, 1, school.wall());
		parking(108, 133, 122, 145);
		kiosk(100, 119);
		yardFence(106, 115, 123, 130, d8[0]);
	}

	// ---------- разрушения построек ----------

	/** Повреждения дома: 0 — пробоины, 1 — ещё и дыра в крыше, 2 — обрушенные углы. */
	private void damage(int x0, int z0, int x1, int z1, int floors, int level, BlockState wall) {
		int roofY = G + floors * FLOOR_H;
		wallHoles(x0, z0, x1, z1, floors, level + 1, wall);
		if (level >= 1) {
			roofHole(x0, z0, x1, z1, floors, wall);
		}
		if (level >= 2) {
			corner(x0, z0, x1, z1, roofY, wall);
			if (chance(0.6)) {
				corner(x0, z0, x1, z1, roofY, wall);
			}
		}
		shatter(x0, z0, x1, z1, roofY, level == 0 ? 0.3 : 0.55);
	}

	private void wallHoles(int x0, int z0, int x1, int z1, int floors, int count, BlockState wall) {
		for (int i = 0; i < count; i++) {
			int side = this.rnd.nextInt(4);
			int x = side < 2 ? x0 + 2 + this.rnd.nextInt(Math.max(1, x1 - x0 - 3)) : (side == 2 ? x0 : x1);
			int z = side < 2 ? (side == 0 ? z0 : z1) : z0 + 2 + this.rnd.nextInt(Math.max(1, z1 - z0 - 3));
			Direction out = side == 0 ? Direction.NORTH : side == 1 ? Direction.SOUTH : side == 2 ? Direction.WEST : Direction.EAST;
			int k = this.rnd.nextInt(floors);
			int y = G + k * FLOOR_H + 2 + this.rnd.nextInt(2);
			blast(x + 0.5, y + 0.5, z + 0.5, 1.3 + this.rnd.nextDouble() * 0.9);
			rubble(x + out.getOffsetX() * 2, z + out.getOffsetZ() * 2, k == 0 ? 2 : 1, G + 1, wall);
			rubble(x - out.getOffsetX() * 2, z - out.getOffsetZ() * 2, 1, G + k * FLOOR_H + 1, wall);
		}
	}

	private void roofHole(int x0, int z0, int x1, int z1, int floors, BlockState wall) {
		int cx = x0 + 3 + this.rnd.nextInt(Math.max(1, x1 - x0 - 5));
		int cz = z0 + 3 + this.rnd.nextInt(Math.max(1, z1 - z0 - 5));
		int roofY = G + floors * FLOOR_H;
		blast(cx + 0.5, roofY + 1.0, cz + 0.5, 2.6 + this.rnd.nextDouble());
		int floorY = G + (floors - 1) * FLOOR_H + 1;
		rubble(cx, cz, 2, floorY, wall);
		this.smoke.add(new int[] {cx, floorY, cz});
	}

	private void corner(int x0, int z0, int x1, int z1, int roofY, BlockState wall) {
		int c = this.rnd.nextInt(3);
		int cx = c == 1 ? x0 : x1;
		int cz = c == 0 ? z0 : z1;
		blast(cx + 0.5, roofY - 1.5, cz + 0.5, 4.2 + this.rnd.nextDouble() * 0.8);
		int ox = cx == x0 ? -1 : 1;
		int oz = cz == z0 ? -1 : 1;
		rubble(cx + ox, cz + oz, 3, G + 1, wall);
		rubble(cx - ox * 2, cz - oz * 2, 2, G + 1, wall);
		this.smoke.add(new int[] {cx - ox * 2, G + 1, cz - oz * 2});
	}

	private void shatter(int x0, int z0, int x1, int z1, int top, double p) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (x != x0 && x != x1 && z != z0 && z != z1) {
					continue;
				}
				for (int y = G + 1; y <= Math.min(HEIGHT - 2, top + 8); y++) {
					BlockState s = get(x, y, z);
					if (s != null && s.isOf(Blocks.GLASS_PANE) && chance(p)) {
						set(x, y, z, AIR);
					}
				}
			}
		}
	}

	/** Сгоревший дом: копоть на стенах, обугленное дерево, выбитые окна, дым внутри. */
	private void burn(int x0, int z0, int x1, int z1, int floors) {
		int top = Math.min(HEIGHT - 2, G + floors * FLOOR_H + Math.max(x1 - x0, z1 - z0) / 2 + 3);
		for (int x = x0 - 2; x <= x1 + 2; x++) {
			for (int z = z0 - 2; z <= z1 + 2; z++) {
				for (int y = G; y <= top; y++) {
					BlockState s = get(x, y, z);
					if (s == null || s.isAir() || guarded(x, y, z)) {
						continue;
					}
					if (y == G && !s.isOf(Blocks.GRASS_BLOCK) && !burnable(s)) {
						continue;
					}
					if (chance(burnable(s) || s.getBlock() instanceof PaneBlock ? 0.9 : 0.45)) {
						set(x, y, z, charred(s));
					}
				}
			}
		}
		wallHoles(x0, z0, x1, z1, floors, 1, d(Blocks.BLACKSTONE));
		roofHole(x0, z0, x1, z1, floors, d(Blocks.BLACKSTONE));
		for (int k = 0; k < floors; k++) {
			this.smoke.add(new int[] {x0 + 2 + this.rnd.nextInt(x1 - x0 - 3), G + k * FLOOR_H + 1,
					z0 + 2 + this.rnd.nextInt(z1 - z0 - 3)});
		}
	}

	/** Взрыв по постройке: выбивает блоки выше земли, вокруг — копоть. */
	private void blast(double cx, double cy, double cz, double r) {
		int ri = (int) Math.ceil(r + 1.5);
		int bx = (int) Math.floor(cx);
		int by = (int) Math.floor(cy);
		int bz = (int) Math.floor(cz);
		for (int x = bx - ri; x <= bx + ri; x++) {
			for (int z = bz - ri; z <= bz + ri; z++) {
				if (x <= 0 || z <= 0 || x >= SIZE - 1 || z >= SIZE - 1) {
					continue;
				}
				for (int y = Math.max(G + 1, by - ri); y <= Math.min(HEIGHT - 2, by + ri); y++) {
					BlockState s = get(x, y, z);
					if (s == null || s.isAir() || guarded(x, y, z) || s.isOf(Blocks.BARRIER)) {
						continue;
					}
					double dx = x + 0.5 - cx;
					double dy = (y + 0.5 - cy) * 1.15;
					double dz = z + 0.5 - cz;
					double dist = Math.sqrt(dx * dx + dy * dy + dz * dz) + (this.rnd.nextDouble() - 0.5) * 0.9;
					if (dist <= r) {
						set(x, y, z, AIR);
					} else if (dist <= r + 1.4 && chance(0.55)) {
						set(x, y, z, charred(s));
					}
				}
			}
		}
	}

	/** Куча обломков: выше в центре, по краям плиты и ступени. */
	private void rubble(int cx, int cz, int r, int baseY, BlockState main) {
		for (int x = cx - r; x <= cx + r; x++) {
			for (int z = cz - r; z <= cz + r; z++) {
				if (x < 1 || z < 1 || x > SIZE - 2 || z > SIZE - 2) {
					continue;
				}
				double dist = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
				if (dist > r + 0.4) {
					continue;
				}
				int h = (int) Math.floor((r - dist) * 0.8 + this.rnd.nextDouble() * 0.9);
				if (h <= 0 && !chance(0.4)) {
					continue;
				}
				int y = baseY;
				while (y < HEIGHT - 3 && solid(x, y, z)) {
					y++;
				}
				BlockState below = get(x, y - 1, z);
				if (below == null || below.isAir() || thin(below) || nearGuard(x, y, z)) {
					continue;
				}
				for (int k = 0; k < h && y + k < HEIGHT - 2; k++) {
					set(x, y + k, z, pick(main, main, d(Blocks.COBBLESTONE), d(Blocks.CRACKED_STONE_BRICKS), d(Blocks.ANDESITE),
							d(Blocks.MOSSY_COBBLESTONE)));
				}
				int ty = y + Math.max(0, h);
				if (ty < HEIGHT - 2 && chance(0.65)) {
					set(x, ty, z, chance(0.5) ? slab(Blocks.COBBLESTONE_SLAB, false)
							: stairs(Blocks.STONE_BRICK_STAIRS, pick(SIDES), false));
				}
			}
		}
	}

	/** Тонкие и навесные блоки: на них ничего не кладём. */
	private static boolean thin(BlockState s) {
		Block b = s.getBlock();
		return b instanceof PaneBlock || b instanceof FenceBlock || b instanceof DoorBlock || b instanceof LadderBlock
				|| b instanceof LanternBlock || b instanceof PlantBlock || b instanceof CarpetBlock || b instanceof BedBlock
				|| b instanceof TrapdoorBlock || b instanceof WallBlock || b instanceof VineBlock || b == Blocks.CHAIN
				|| b == Blocks.COBWEB || b instanceof CampfireBlock || b instanceof AbstractFireBlock;
	}

	private static boolean burnable(BlockState s) {
		Block b = s.getBlock();
		return s.isBurnable() || b instanceof PlantBlock || b instanceof VineBlock || b instanceof CarpetBlock
				|| b instanceof LeavesBlock || b == Blocks.COAL_BLOCK || b == Blocks.HAY_BLOCK || b == Blocks.MOSS_BLOCK
				|| b == Blocks.BOOKSHELF || b == Blocks.TARGET || b == Blocks.DRIED_KELP_BLOCK || b == Blocks.TNT;
	}

	/** Обгоревший вариант блока. */
	private BlockState charred(BlockState s) {
		if (s == null || s.isAir()) {
			return s;
		}
		Block b = s.getBlock();
		if (b == Blocks.BARRIER || b instanceof LadderBlock || b instanceof DoorBlock || b instanceof TrapdoorBlock
				|| b instanceof LanternBlock || b == Blocks.CHAIN || b == Blocks.IRON_BARS) {
			return s;
		}
		if (b instanceof PaneBlock || b instanceof AbstractGlassBlock || b instanceof LeavesBlock || b instanceof CarpetBlock
				|| b instanceof PlantBlock || b instanceof BedBlock || b instanceof VineBlock || b == Blocks.COBWEB) {
			return AIR;
		}
		if (b == Blocks.GRASS_BLOCK || b == Blocks.PODZOL || b == Blocks.MOSS_BLOCK || b == Blocks.DIRT_PATH) {
			return d(Blocks.COARSE_DIRT);
		}
		if (b instanceof StairsBlock) {
			return Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.getDefaultState()
					.with(Properties.HORIZONTAL_FACING, s.get(Properties.HORIZONTAL_FACING))
					.with(Properties.BLOCK_HALF, s.get(Properties.BLOCK_HALF));
		}
		if (b instanceof SlabBlock) {
			return Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.getDefaultState().with(Properties.SLAB_TYPE, s.get(Properties.SLAB_TYPE));
		}
		if (b instanceof FenceBlock) {
			return d(Blocks.NETHER_BRICK_FENCE);
		}
		if (b instanceof WallBlock) {
			return d(Blocks.BLACKSTONE_WALL);
		}
		if (burnable(s)) {
			return pick(d(Blocks.BLACKSTONE), d(Blocks.POLISHED_BLACKSTONE), d(Blocks.BLACK_TERRACOTTA), d(Blocks.GRAY_TERRACOTTA),
					d(Blocks.BASALT));
		}
		if (s.isOpaque()) {
			return pick(d(Blocks.BLACKSTONE), d(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS), d(Blocks.COBBLED_DEEPSLATE),
					d(Blocks.GRAY_CONCRETE), s, s);
		}
		return s;
	}

	// ---------- магазин, заправка, площадь, стоянка, киоск ----------

	/** Магазин: витрины, двойная дверь, навес, прилавок, стеллажи, холодильники, склад и выход на крышу. */
	private void shop(int x0, int z0, int x1, int z1) {
		mark(x0 - 1, z0 - 2, x1 + 1, z1 + 1);
		int roofY = G + 6;
		fill(x0, G + 1, z0, x1, roofY + 3, z1, AIR);
		for (int x = x0 - 1; x <= x1 + 1; x++) {
			for (int z = z0 - 2; z < z0; z++) {
				set(x, G, z, chance(0.8) ? d(Blocks.POLISHED_ANDESITE) : d(Blocks.ANDESITE));
				fill(x, G + 1, z, x, G + 3, z, AIR);
			}
		}
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean rim = x == x0 || x == x1 || z == z0 || z == z1;
				set(x, G, z, rim ? d(Blocks.STONE_BRICKS)
						: ((x + z) % 2 == 0 ? d(Blocks.WHITE_CONCRETE) : d(Blocks.LIGHT_GRAY_CONCRETE)));
				set(x, G - 1, z, d(Blocks.STONE_BRICKS));
				if (!rim) {
					continue;
				}
				boolean front = z == z0;
				boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
				boolean pillar = corner || (front && (x - x0) % 4 == 0);
				for (int y = G + 1; y < roofY; y++) {
					BlockState s;
					if (pillar) {
						s = d(Blocks.GRAY_CONCRETE);
					} else if (front) {
						if (y <= G + 3) {
							s = chance(0.55) ? AIR : d(Blocks.GLASS_PANE);
						} else if (y == G + 4) {
							s = x % 3 == 0 ? d(Blocks.WHITE_CONCRETE) : d(Blocks.RED_CONCRETE);
						} else {
							s = d(Blocks.GRAY_CONCRETE);
						}
					} else if (y == G + 1) {
						s = d(Blocks.GRAY_CONCRETE);
					} else if (y == G + 3 && (z == z1 ? (x - x0) % 4 == 2 : (z - z0) % 4 == 2)) {
						s = d(Blocks.GLASS_PANE);
					} else {
						s = d(Blocks.WHITE_CONCRETE);
					}
					set(x, y, z, s);
				}
			}
		}
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean rim = x == x0 || x == x1 || z == z0 || z == z1;
				set(x, roofY, z, rim ? d(Blocks.GRAY_CONCRETE) : d(Blocks.SMOOTH_STONE));
				if (rim) {
					set(x, roofY + 1, z, d(Blocks.LIGHT_GRAY_CONCRETE));
				}
			}
			if (chance(0.8)) {
				set(x, G + 4, z0 - 1, x % 2 == 0 ? d(Blocks.RED_WOOL) : d(Blocks.WHITE_WOOL));
			}
		}
		int dx = x0 + 4 * ((x1 - x0) / 8) + 1;
		door(dx, G + 1, z0, Blocks.BIRCH_DOOR, Direction.NORTH, false);
		door(dx + 1, G + 1, z0, Blocks.BIRCH_DOOR, Direction.NORTH, true);
		set(dx, G + 3, z0, d(Blocks.GLASS_PANE));
		set(dx + 1, G + 3, z0, d(Blocks.GLASS_PANE));
		int backZ = z1 - 4;
		for (int x = x0 + 1; x <= x1 - 1; x++) {
			for (int y = G + 1; y < roofY; y++) {
				set(x, y, backZ, d(Blocks.WHITE_TERRACOTTA));
			}
		}
		door(x0 + 3, G + 1, backZ, Blocks.BIRCH_DOOR, Direction.SOUTH, false);
		door((x0 + x1) / 2, G + 1, z1, Blocks.BIRCH_DOOR, Direction.SOUTH, false);
		// прилавок с кассой
		for (int x = x0 + 1; x <= x0 + 4; x++) {
			set(x, G + 1, z0 + 3, d(Blocks.POLISHED_ANDESITE));
		}
		set(x0 + 2, G + 2, z0 + 3, d(Blocks.DAYLIGHT_DETECTOR));
		set(x0 + 4, G + 2, z0 + 3, slab(Blocks.SMOOTH_QUARTZ_SLAB, false));
		// стеллажи: двусторонние ряды, часть повалена
		if (x1 - x0 >= 12) {
			for (int z = z0 + 5; z + 1 < backZ - 1; z += 4) {
				for (int x = x0 + 6; x <= x1 - 3; x++) {
					if ((x - x0) % 6 == 0) {
						continue;
					}
					for (int k = 0; k < 2; k++) {
						Direction face = k == 0 ? Direction.NORTH : Direction.SOUTH;
						if (chance(0.2)) {
							set(x, G + 1, z + k, up(Blocks.BARREL));
							continue;
						}
						set(x, G + 1, z + k, facing(Blocks.CHISELED_BOOKSHELF, face));
						set(x, G + 2, z + k, facing(Blocks.CHISELED_BOOKSHELF, face));
						if (chance(0.25)) {
							set(x, G + 3, z + k, pick(up(Blocks.BARREL), d(Blocks.POTTED_FERN), d(Blocks.COMPOSTER)));
						}
					}
				}
			}
		}
		// холодильники у восточной стены
		for (int z = z0 + 2; z < backZ - 1; z++) {
			set(x1 - 1, G + 1, z, d(Blocks.WHITE_CONCRETE));
			set(x1 - 1, G + 2, z, chance(0.4) ? AIR : d(Blocks.LIGHT_BLUE_STAINED_GLASS));
			set(x1 - 1, G + 3, z, d(Blocks.WHITE_CONCRETE));
		}
		// склад
		ladder(x1 - 1, G + 1, roofY - 1, z1 - 1, Direction.WEST);
		set(x1 - 1, roofY, z1 - 1, trapdoor(Blocks.SPRUCE_TRAPDOOR, Direction.WEST, true));
		protect(x1 - 1, roofY, z1 - 1);
		for (int x = x0 + 1; x <= x1 - 2; x++) {
			for (int z = backZ + 1; z <= z1 - 1; z++) {
				if (Math.abs(x - (x0 + 3)) <= 1 && z == backZ + 1 || Math.abs(x - (x0 + x1) / 2) <= 0 && z == z1 - 1) {
					continue;
				}
				if (chance(0.4)) {
					set(x, G + 1, z, pick(up(Blocks.BARREL), d(Blocks.SPRUCE_PLANKS), d(Blocks.STRIPPED_OAK_WOOD)));
					if (chance(0.35)) {
						set(x, G + 2, z, pick(up(Blocks.BARREL), d(Blocks.STRIPPED_OAK_WOOD)));
					}
				}
			}
		}
		for (int x = x0 + 3; x < x1; x += 5) {
			for (int z = z0 + 2; z < backZ; z += 4) {
				if (!solid(x, roofY - 1, z)) {
					set(x, roofY - 1, z, lantern(true));
				}
			}
		}
		for (int x = x0 + 1; x < x1; x++) {
			for (int z = z0 + 1; z < backZ; z++) {
				if (!solid(x, G + 1, z) && chance(0.05)) {
					set(x, G + 1, z, d(Blocks.WHITE_CARPET));
				}
			}
		}
		// крыша: кондиционеры и вывеска
		set(x0 + 3, roofY + 1, z1 - 3, d(Blocks.IRON_BLOCK));
		set(x0 + 4, roofY + 1, z1 - 3, d(Blocks.IRON_BLOCK));
		set(x0 + 3, roofY + 2, z1 - 3, trapdoor(Blocks.IRON_TRAPDOOR, Direction.NORTH, false));
		for (int x = x0 + 2; x <= x1 - 2; x++) {
			set(x, roofY + 2, z0 + 1, (x - x0) % 2 == 0 ? d(Blocks.WHITE_CONCRETE) : d(Blocks.RED_CONCRETE));
			set(x, roofY + 1, z0 + 1, d(Blocks.RED_CONCRETE));
		}
		// пробоина и разгром внутри
		blast(x1 + 0.5, G + 3.5, z0 + 6.5, 2.0);
		rubble(x1 - 2, z0 + 6, 1, G + 1, d(Blocks.WHITE_CONCRETE));
		rubble(x1 + 2, z0 + 6, 1, G + 1, d(Blocks.WHITE_CONCRETE));
	}

	/** Заправка: навес на опорах, колонки, киоск, стела с ценами; одна колонка взорвана и горит. */
	private void gasStation(int x0, int z0, int x1, int z1) {
		mark(x0, z0, x1, z1);
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				road(x, z, false, false);
			}
		}
		int cx0 = x0 + 1;
		int cx1 = x0 + 11;
		int cz0 = z0 + 2;
		int cz1 = z0 + 13;
		int cy = G + 6;
		for (int x = cx0; x <= cx1; x++) {
			for (int z = cz0; z <= cz1; z++) {
				boolean rim = x == cx0 || x == cx1 || z == cz0 || z == cz1;
				set(x, cy, z, rim ? d(Blocks.RED_CONCRETE) : ((x + z) % 4 == 0 ? d(Blocks.SEA_LANTERN) : d(Blocks.WHITE_CONCRETE)));
				if (rim) {
					set(x, cy + 1, z, d(Blocks.WHITE_CONCRETE));
				}
			}
		}
		for (int px : new int[] {cx0 + 2, cx1 - 2}) {
			for (int pz : new int[] {cz0 + 2, cz1 - 2}) {
				set(px, G + 1, pz, d(Blocks.GRAY_CONCRETE));
				for (int y = G + 2; y < cy; y++) {
					set(px, y, pz, d(Blocks.WHITE_CONCRETE));
				}
			}
		}
		for (int iz : new int[] {cz0 + 4, cz0 + 8}) {
			for (int x = cx0 + 3; x <= cx1 - 3; x++) {
				set(x, G + 1, iz, slab(Blocks.SMOOTH_STONE_SLAB, false));
			}
			set(cx0 + 2, G + 1, iz, d(Blocks.ANDESITE_WALL));
			set(cx1 - 2, G + 1, iz, d(Blocks.ANDESITE_WALL));
			for (int px : new int[] {cx0 + 4, cx1 - 4}) {
				set(px, G + 1, iz, d(Blocks.LIGHT_GRAY_CONCRETE));
				set(px, G + 2, iz, d(Blocks.RED_CONCRETE));
				set(px, G + 3, iz, d(Blocks.BLACK_STAINED_GLASS));
			}
		}
		car(cx0 + 3, cz0 + 5, true, d(Blocks.LIGHT_BLUE_CONCRETE), false);
		shop(x0 + 13, z0 + 4, x1 - 1, z1 - 7);
		int px = x1 - 1;
		int pz = z0 + 1;
		for (int y = G + 1; y <= G + 6; y++) {
			set(px, y, pz, d(Blocks.GRAY_CONCRETE));
		}
		for (int x = px - 2; x <= px; x++) {
			set(x, G + 7, pz, d(Blocks.RED_CONCRETE));
			set(x, G + 6, pz, (x % 2 == 0) ? d(Blocks.BLACK_CONCRETE) : d(Blocks.WHITE_CONCRETE));
			set(x, G + 5, pz, (x % 2 == 0) ? d(Blocks.WHITE_CONCRETE) : d(Blocks.BLACK_CONCRETE));
		}
		for (int y = G + 1; y <= G + 3; y++) {
			set(x0 + 1, y, z1 - 1, d(Blocks.BLACK_CONCRETE));
		}
		set(x0 + 2, G + 1, z1 - 1, d(Blocks.BLACK_CONCRETE));
		set(x0 + 2, G + 2, z1 - 1, d(Blocks.BLACK_CONCRETE));
		for (int x = x0 + 13; x <= x0 + 16; x++) {
			set(x, G + 1, z1 - 2, up(Blocks.BARREL));
		}
		for (int x = x0 + 4; x <= x0 + 10; x += 3) {
			for (int z = z1 - 5; z <= z1 - 1; z++) {
				set(x, G, z, d(Blocks.WHITE_CONCRETE));
			}
		}
		car(x0 + 5, z1 - 5, false, d(Blocks.ORANGE_CONCRETE), true);
		// навес частично обрушен, дальняя колонка взорвана
		blast(cx1 + 0.5, cy + 0.5, cz1 + 0.5, 3.0);
		rubble(cx1 - 1, cz1 - 1, 2, G + 1, d(Blocks.WHITE_CONCRETE));
		blast(cx1 - 4 + 0.5, G + 2.5, cz0 + 8 + 0.5, 1.6);
		this.flames.add(new int[] {cx1 - 4, G + 1, cz0 + 8});
	}

	/** Площадь: мощение, клумбы с деревьями, разбитый памятник, скамейки, фонари и баррикада. */
	private void square(int x0, int z0, int x1, int z1) {
		mark(x0, z0, x1, z1);
		int cx = (x0 + x1) / 2;
		int cz = (z0 + z1) / 2;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean rim = x == x0 || x == x1 || z == z0 || z == z1;
				set(x, G, z, rim ? d(Blocks.SMOOTH_STONE) : (x + z) % 2 == 0 ? d(Blocks.STONE_BRICKS) : d(Blocks.POLISHED_ANDESITE));
			}
		}
		int[][] beds = {{x0 + 2, z0 + 2}, {x1 - 6, z0 + 2}, {x0 + 2, z1 - 5}, {x1 - 6, z1 - 5}};
		for (int[] b : beds) {
			for (int a = 0; a <= 4; a++) {
				for (int c = 0; c <= 3; c++) {
					int x = b[0] + a;
					int z = b[1] + c;
					set(x, G, z, d(Blocks.GRASS_BLOCK));
					double r = this.rnd.nextDouble();
					if (r < 0.35) {
						set(x, G + 1, z, d(pick(Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.ALLIUM, Blocks.OXEYE_DAISY,
								Blocks.RED_TULIP, Blocks.WHITE_TULIP)));
					} else if (r < 0.55) {
						set(x, G + 1, z, d(Blocks.GRASS));
					} else if (r < 0.62) {
						tallPlant(x, G + 1, z, pick(Blocks.ROSE_BUSH, Blocks.PEONY, Blocks.TALL_GRASS));
					}
				}
			}
			int tx = b[0] + 2;
			int tz = b[1] + 1 + this.rnd.nextInt(2);
			fill(tx, G + 1, tz, tx, G + 2, tz, AIR);
			if (chance(0.5)) {
				oak(tx, tz);
			} else {
				birch(tx, tz);
			}
		}
		// памятник: постамент и статуя, верх отбит
		for (int x = cx - 1; x <= cx + 1; x++) {
			for (int z = cz - 1; z <= cz + 1; z++) {
				set(x, G + 1, z, d(Blocks.POLISHED_ANDESITE));
			}
		}
		set(cx, G + 2, cz, d(Blocks.CHISELED_STONE_BRICKS));
		set(cx, G + 3, cz, log(Blocks.QUARTZ_PILLAR, Direction.Axis.Y));
		set(cx, G + 4, cz, log(Blocks.QUARTZ_PILLAR, Direction.Axis.Y));
		if (chance(0.5)) {
			set(cx, G + 5, cz, d(Blocks.CHISELED_QUARTZ_BLOCK));
		} else {
			set(cx + 2, G + 1, cz + 1, d(Blocks.CHISELED_QUARTZ_BLOCK));
			set(cx - 2, G + 1, cz, slab(Blocks.QUARTZ_SLAB, false));
		}
		bench(cx - 4, cz - 1, Direction.EAST, 3);
		bench(cx + 4, cz + 1, Direction.WEST, 3);
		bench(cx + 1, cz - 4, Direction.SOUTH, 3);
		for (int[] c : new int[][] {{x0 + 1, z0 + 1}, {x1 - 1, z0 + 1}, {x0 + 1, z1 - 1}, {x1 - 1, z1 - 1}}) {
			set(c[0], G + 1, c[1], d(Blocks.STONE_BRICKS));
			for (int y = G + 2; y <= G + 4; y++) {
				set(c[0], y, c[1], d(Blocks.DARK_OAK_FENCE));
			}
			if (chance(0.7)) {
				set(c[0], G + 5, c[1], lantern(false));
			}
		}
		for (int x = cx - 7; x <= cx - 3; x++) {
			sandbag(x, cz + 3, 2);
		}
		for (int x = cx + 3; x <= cx + 5; x++) {
			sandbag(x, cz + 4, 2);
		}
	}

	/** Скамейка: спинка с противоположной взгляду стороны. */
	private void bench(int x, int z, Direction look, int len) {
		Direction along = look.rotateYClockwise();
		for (int k = 0; k < len; k++) {
			int bx = x + along.getOffsetX() * k;
			int bz = z + along.getOffsetZ() * k;
			if (!solid(bx, G + 1, bz)) {
				set(bx, G + 1, bz, stairs(Blocks.SPRUCE_STAIRS, look.getOpposite(), false));
			}
		}
	}

	/** Стоянка у школы: разметка, машины (часть сгорела, одна перевёрнута), фонарь. */
	private void parking(int x0, int z0, int x1, int z1) {
		mark(x0, z0, x1, z1);
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				road(x, z, x == x0 || x == x1 || z == z0 || z == z1, false);
				boolean row = (z >= z0 + 1 && z <= z0 + 5) || (z >= z1 - 5 && z <= z1 - 1);
				if (row && (x - x0) % 4 == 0 && x != x0 && x != x1) {
					set(x, G, z, d(Blocks.WHITE_CONCRETE));
				}
			}
		}
		BlockState[] colors = {d(Blocks.WHITE_CONCRETE), d(Blocks.BLACK_CONCRETE), d(Blocks.RED_CONCRETE), d(Blocks.BLUE_CONCRETE),
				d(Blocks.LIGHT_GRAY_CONCRETE), d(Blocks.GREEN_CONCRETE), d(Blocks.YELLOW_CONCRETE)};
		for (int x = x0 + 1; x + 2 < x1; x += 4) {
			for (int z : new int[] {z0 + 1, z1 - 5}) {
				double r = this.rnd.nextDouble();
				BlockState body = colors[this.rnd.nextInt(colors.length)];
				if (r < 0.15) {
					carFlipped(x, z, false, body);
				} else if (r < 0.75) {
					car(x, z, false, body, chance(0.35));
				}
			}
		}
		int lx = (x0 + x1) / 2;
		int lz = (z0 + z1) / 2;
		set(lx, G + 1, lz, d(Blocks.ANDESITE_WALL));
		for (int y = G + 2; y <= G + 6; y++) {
			set(lx, y, lz, d(Blocks.IRON_BARS));
		}
		set(lx + 1, G + 6, lz, slab(Blocks.SMOOTH_STONE_SLAB, true));
		set(lx + 1, G + 5, lz, lantern(true));
	}

	/** Киоск: стеклянная будка с прилавком, стёкла почти все выбиты. */
	private void kiosk(int x0, int z0) {
		if (!free(x0 - 1, z0 - 1, x0 + 5, z0 + 4)) {
			return;
		}
		mark(x0 - 1, z0 - 1, x0 + 5, z0 + 4);
		for (int x = x0; x <= x0 + 4; x++) {
			for (int z = z0; z <= z0 + 3; z++) {
				set(x, G, z, d(Blocks.SMOOTH_STONE));
				boolean rim = x == x0 || x == x0 + 4 || z == z0 || z == z0 + 3;
				boolean corner = (x == x0 || x == x0 + 4) && (z == z0 || z == z0 + 3);
				for (int y = G + 1; y <= G + 3; y++) {
					BlockState s = AIR;
					if (rim) {
						s = corner || y == G + 1 ? d(Blocks.GREEN_CONCRETE) : (chance(0.6) ? AIR : d(Blocks.GLASS_PANE));
					}
					set(x, y, z, s);
				}
				set(x, G + 4, z, d(Blocks.GREEN_CONCRETE));
			}
			set(x, G + 4, z0 - 1, slab(Blocks.SMOOTH_STONE_SLAB, true));
			set(x, G + 5, z0, x % 2 == 0 ? d(Blocks.WHITE_CONCRETE) : d(Blocks.YELLOW_CONCRETE));
		}
		door(x0 + 2, G + 1, z0 + 3, Blocks.BIRCH_DOOR, Direction.SOUTH, false);
		for (int x = x0 + 1; x <= x0 + 3; x++) {
			set(x, G + 1, z0 + 1, d(Blocks.POLISHED_ANDESITE));
		}
		set(x0 + 1, G + 2, z0 + 1, d(Blocks.DAYLIGHT_DETECTOR));
		set(x0 + 3, G + 1, z0 + 2, up(Blocks.BARREL));
	}

	/** Возвращает клетку снаружи перед входной дверью {x, z}. */
	private int[] house(int x0, int z0, int x1, int z1, int floors, Pal p, boolean flat, Direction front) {
		mark(x0 - 2, z0 - 2, x1 + 2, z1 + 2);
		int roofY = G + floors * FLOOR_H;
		int top = flat ? roofY + 2 : roofY + Math.max(x1 - x0, z1 - z0) / 2 + 2;
		fill(x0 - 1, G + 1, z0 - 1, x1 + 1, Math.min(HEIGHT - 2, top), z1 + 1, AIR);
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean rim = x == x0 || x == x1 || z == z0 || z == z1;
				set(x, G, z, rim ? d(Blocks.STONE_BRICKS) : p.floor());
				if (rim) {
					set(x, G - 1, z, d(Blocks.STONE_BRICKS));
				}
			}
		}
		int midX = (x0 + x1) / 2;
		int midZ = (z0 + z1) / 2;
		int dx;
		int dz;
		int ix;
		int iz;
		switch (front) {
			case NORTH -> {
				dx = midX;
				dz = z0;
				ix = midX;
				iz = z0 + 1;
			}
			case SOUTH -> {
				dx = midX;
				dz = z1;
				ix = midX;
				iz = z1 - 1;
			}
			case EAST -> {
				dx = x1;
				dz = midZ;
				ix = x1 - 1;
				iz = midZ;
			}
			default -> {
				dx = x0;
				dz = midZ;
				ix = x0 + 1;
				iz = midZ;
			}
		}
		int bdx = front.getAxis() == Direction.Axis.Z ? midX : (front == Direction.EAST ? x0 : x1);
		int bdz = front.getAxis() == Direction.Axis.Z ? (front == Direction.NORTH ? z1 : z0) : midZ;
		Direction back = front.getOpposite();
		int bix = bdx - back.getOffsetX();
		int biz = bdz - back.getOffsetZ();
		int lx = x0 + 1;
		int lz = z0 + 1;
		for (int k = 0; k < floors; k++) {
			int fy = G + k * FLOOR_H;
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					boolean rim = x == x0 || x == x1 || z == z0 || z == z1;
					boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
					if (k > 0) {
						set(x, fy, z, rim ? p.band() : p.floor());
					}
					for (int y = fy + 1; y <= fy + 4; y++) {
						set(x, y, z, rim ? (corner ? p.corner() : p.wall()) : AIR);
					}
				}
			}
			List<int[]> keep = new ArrayList<>();
			keep.add(new int[] {lx, lz});
			keep.add(new int[] {lx + 1, lz});
			if (k == 0) {
				keep.add(new int[] {ix, iz});
				keep.add(new int[] {bix, biz});
			} else if (floors == 3) {
				keep.add(new int[] {ix, iz});
			}
			List<int[]> rooms = new ArrayList<>();
			split(x0 + 1, z0 + 1, x1 - 1, z1 - 1, fy, p, rooms, keep, 0);
			if (k == 0) {
				fill(dx, fy + 1, dz, dx, fy + 2, dz, AIR);
				door(dx, fy + 1, dz, p.door(), front);
				fill(bdx, fy + 1, bdz, bdx, fy + 2, bdz, AIR);
				if (chance(0.5)) {
					door(bdx, fy + 1, bdz, p.door(), front.getOpposite());
				}
			} else if (floors == 3) {
				fill(dx, fy + 1, dz, dx, fy + 2, dz, AIR);
				balcony(dx, fy, dz, front, p);
			}
			windows(x0, z0, x1, z1, fy, k == 0, keep, dx, dz, bdx, bdz);
			for (int[] r : rooms) {
				decorate(r[0], r[1], r[2], r[3], fy, keep);
			}
		}
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean rim = x == x0 || x == x1 || z == z0 || z == z1;
				set(x, roofY, z, rim ? p.band() : (flat ? p.roof() : p.floor()));
				if (flat && rim) {
					set(x, roofY + 1, z, p.wall());
				}
			}
		}
		int ladderTop = flat ? roofY - 1 : G + (floors - 1) * FLOOR_H;
		ladder(lx, G + 1, ladderTop, lz, Direction.EAST);
		if (flat) {
			set(lx, roofY, lz, trapdoor(Blocks.SPRUCE_TRAPDOOR, Direction.EAST, true));
			roofProps(x0, z0, x1, z1, roofY + 1, lx, lz);
		} else {
			gable(x0, z0, x1, z1, roofY, p);
		}
		if (front.getAxis() == Direction.Axis.Z && floors < 3) {
			int oz = dz + front.getOffsetZ();
			for (int x = dx - 1; x <= dx + 1; x++) {
				set(x, G + 4, oz, slab(Blocks.SMOOTH_STONE_SLAB, true));
			}
		}
		vines(x0, z0, x1, z1, G + 1, roofY - 1);
		return new int[] {dx + front.getOffsetX(), dz + front.getOffsetZ()};
	}

	private void split(int ax, int az, int bx, int bz, int fy, Pal p, List<int[]> rooms, List<int[]> keep, int depth) {
		int w = bx - ax + 1;
		int dd = bz - az + 1;
		boolean canX = w >= 8;
		boolean canZ = dd >= 8;
		if ((!canX && !canZ) || (depth >= 2 && w <= 9 && dd <= 9 && chance(0.4))) {
			rooms.add(new int[] {ax, az, bx, bz});
			return;
		}
		boolean byX = canX && (!canZ || w > dd || (w == dd && this.rnd.nextBoolean()));
		for (int tries = 0; tries < 8; tries++) {
			int s = byX ? ax + 3 + this.rnd.nextInt(w - 6) : az + 3 + this.rnd.nextInt(dd - 6);
			if (!wallOk(byX, s, byX ? az : ax, byX ? bz : bx, keep)) {
				continue;
			}
			int len = byX ? dd : w;
			int open = (byX ? az : ax) + 1 + this.rnd.nextInt(Math.max(1, len - 2));
			for (int i = byX ? az : ax; i <= (byX ? bz : bx); i++) {
				int x = byX ? s : i;
				int z = byX ? i : s;
				for (int y = fy + 1; y <= fy + 4; y++) {
					set(x, y, z, p.inner());
				}
			}
			int ox = byX ? s : open;
			int oz = byX ? open : s;
			set(ox, fy + 1, oz, AIR);
			set(ox, fy + 2, oz, AIR);
			if (chance(0.35)) {
				door(ox, fy + 1, oz, p.door(), byX ? Direction.EAST : Direction.SOUTH);
			}
			keep.add(new int[] {ox, oz});
			if (byX) {
				split(ax, az, s - 1, bz, fy, p, rooms, keep, depth + 1);
				split(s + 1, az, bx, bz, fy, p, rooms, keep, depth + 1);
			} else {
				split(ax, az, bx, s - 1, fy, p, rooms, keep, depth + 1);
				split(ax, s + 1, bx, bz, fy, p, rooms, keep, depth + 1);
			}
			return;
		}
		rooms.add(new int[] {ax, az, bx, bz});
	}

	private boolean wallOk(boolean byX, int s, int from, int to, List<int[]> keep) {
		for (int i = from; i <= to; i++) {
			int x = byX ? s : i;
			int z = byX ? i : s;
			for (int[] k : keep) {
				if (Math.abs(k[0] - x) + Math.abs(k[1] - z) <= 1) {
					return false;
				}
			}
		}
		// стена не должна упираться в дверной проём соседней стены
		for (int[] k : keep) {
			if (byX && (k[0] == s) && (k[1] == from - 1 || k[1] == to + 1)) {
				return false;
			}
			if (!byX && (k[1] == s) && (k[0] == from - 1 || k[0] == to + 1)) {
				return false;
			}
		}
		return true;
	}

	private void windows(int x0, int z0, int x1, int z1, int fy, boolean ground, List<int[]> keep,
			int dx, int dz, int bdx, int bdz) {
		int off = this.rnd.nextInt(2);
		for (int side = 0; side < 4; side++) {
			boolean alongX = side < 2;
			int fixed = side == 0 ? z0 : side == 1 ? z1 : side == 2 ? x0 : x1;
			int from = alongX ? x0 : z0;
			int to = alongX ? x1 : z1;
			Direction out = side == 0 ? Direction.NORTH : side == 1 ? Direction.SOUTH : side == 2 ? Direction.WEST : Direction.EAST;
			for (int i = from + 2 + off; i <= to - 2; i += 3) {
				int x = alongX ? i : fixed;
				int z = alongX ? fixed : i;
				if ((Math.abs(x - dx) + Math.abs(z - dz) <= 1) || (ground && Math.abs(x - bdx) + Math.abs(z - bdz) <= 1)) {
					continue;
				}
				if (!solid(x, fy + 2, z) || get(x, fy + 2, z).getBlock() instanceof DoorBlock) {
					continue;
				}
				int inX = x - out.getOffsetX();
				int inZ = z - out.getOffsetZ();
				if (solid(inX, fy + 2, inZ)) {
					continue;
				}
				boolean broken = chance(0.1);
				for (int y = fy + 2; y <= fy + 3; y++) {
					set(x, y, z, broken ? AIR : d(Blocks.GLASS_PANE));
				}
				double barChance = ground ? 0.45 : 0.15;
				int ox = x + out.getOffsetX();
				int oz = z + out.getOffsetZ();
				if (chance(barChance) && !solid(ox, fy + 2, oz)) {
					set(ox, fy + 2, oz, d(Blocks.IRON_BARS));
					set(ox, fy + 3, oz, d(Blocks.IRON_BARS));
				} else if (!ground && chance(0.12) && !solid(ox, fy + 1, oz)) {
					set(ox, fy + 1, oz, trapdoor(Blocks.SPRUCE_TRAPDOOR, out, true));
					set(ox, fy + 2, oz, pick(d(Blocks.POTTED_FERN), d(Blocks.POTTED_POPPY), d(Blocks.POTTED_CORNFLOWER)));
				}
			}
		}
	}

	private void balcony(int dx, int fy, int dz, Direction front, Pal p) {
		Direction along = front.rotateYClockwise();
		for (int out = 1; out <= 2; out++) {
			for (int a = -1; a <= 1; a++) {
				int x = dx + front.getOffsetX() * out + along.getOffsetX() * a;
				int z = dz + front.getOffsetZ() * out + along.getOffsetZ() * a;
				set(x, fy, z, p.band());
				boolean rail = out == 2 || a != 0;
				if (rail && (out == 2 || Math.abs(a) == 1)) {
					set(x, fy + 1, z, d(Blocks.DARK_OAK_FENCE));
				}
			}
		}
		int sx = dx + front.getOffsetX();
		int sz = dz + front.getOffsetZ();
		set(sx, fy + 1, sz, AIR);
	}

	private void roofProps(int x0, int z0, int x1, int z1, int y, int lx, int lz) {
		for (int i = 0; i < 3; i++) {
			int x = x0 + 2 + this.rnd.nextInt(Math.max(1, x1 - x0 - 4));
			int z = z0 + 2 + this.rnd.nextInt(Math.max(1, z1 - z0 - 4));
			if (Math.abs(x - lx) + Math.abs(z - lz) <= 2) {
				continue;
			}
			if (i == 0) {
				set(x, y, z, d(Blocks.LIGHT_GRAY_CONCRETE));
				set(x + 1, y, z, d(Blocks.LIGHT_GRAY_CONCRETE));
				set(x, y + 1, z, d(Blocks.IRON_BARS));
			} else if (i == 1) {
				for (int h = 0; h < 3; h++) {
					set(x, y + h, z, d(Blocks.BRICKS));
				}
			} else {
				sandbagAt(x, y, z);
				sandbagAt(x + 1, y, z);
			}
		}
	}

	private void sandbagAt(int x, int y, int z) {
		set(x, y, z, d(Blocks.SMOOTH_SANDSTONE));
		set(x, y + 1, z, slab(Blocks.SANDSTONE_SLAB, false));
	}

	private void gable(int x0, int z0, int x1, int z1, int roofY, Pal p) {
		boolean ridgeX = (x1 - x0) >= (z1 - z0);
		int a0 = ridgeX ? z0 - 1 : x0 - 1;
		int a1 = ridgeX ? z1 + 1 : x1 + 1;
		int b0 = ridgeX ? x0 - 1 : z0 - 1;
		int b1 = ridgeX ? x1 + 1 : z1 + 1;
		Direction up0 = ridgeX ? Direction.SOUTH : Direction.EAST;
		for (int i = 0; ; i++) {
			int lo = a0 + i;
			int hi = a1 - i;
			int y = roofY + i;
			if (lo > hi || y >= HEIGHT - 2) {
				break;
			}
			for (int b = b0; b <= b1; b++) {
				if (lo == hi) {
					setRoof(ridgeX, b, y, lo, p.roof());
				} else {
					setRoof(ridgeX, b, y, lo, stairs(p.stairs(), up0, false));
					setRoof(ridgeX, b, y, hi, stairs(p.stairs(), up0.getOpposite(), false));
				}
			}
			if (i >= 1) {
				for (int a = lo + 1; a <= hi - 1; a++) {
					int gx0 = ridgeX ? x0 : a;
					int gz0 = ridgeX ? a : z0;
					int gx1 = ridgeX ? x1 : a;
					int gz1 = ridgeX ? a : z1;
					boolean window = (hi - lo) > 4 && a == (lo + hi) / 2 && i == 2;
					set(gx0, y, gz0, window ? d(Blocks.GLASS_PANE) : p.wall());
					set(gx1, y, gz1, window ? d(Blocks.GLASS_PANE) : p.wall());
				}
			}
		}
	}

	private void setRoof(boolean ridgeX, int b, int y, int a, BlockState s) {
		if (ridgeX) {
			set(b, y, a, s);
		} else {
			set(a, y, b, s);
		}
	}

	private void decorate(int ax, int az, int bx, int bz, int fy, List<int[]> keep) {
		int w = bx - ax + 1;
		int dd = bz - az + 1;
		int y = fy + 1;
		int type = this.rnd.nextInt(7);
		if (w * dd <= 12 && type < 2) {
			type = 4;
		}
		List<int[]> spots = new ArrayList<>();
		for (int x = ax; x <= bx; x++) {
			for (int z = az; z <= bz; z++) {
				boolean border = x == ax || x == bx || z == az || z == bz;
				if (!border || blocked(x, z, y, keep)) {
					continue;
				}
				Direction wall = x == ax ? Direction.WEST : x == bx ? Direction.EAST : z == az ? Direction.NORTH : Direction.SOUTH;
				spots.add(new int[] {x, z, wall.getHorizontal()});
			}
		}
		java.util.Collections.shuffle(spots, this.rnd);
		int budget = Math.max(2, spots.size() / 2);
		boolean bedDone = false;
		for (int[] s : spots) {
			if (budget-- <= 0) {
				break;
			}
			int x = s[0];
			int z = s[1];
			if (solid(x, y, z)) {
				continue;
			}
			Direction wall = Direction.fromHorizontal(s[2]);
			Direction in = wall.getOpposite();
			switch (type) {
				case 0 -> {
					int fx = x + in.getOffsetX();
					int fz = z + in.getOffsetZ();
					if (!bedDone && fx >= ax && fx <= bx && fz >= az && fz <= bz && !solid(fx, y, fz) && !blocked(fx, fz, y, keep)) {
						bed(x, y, z, wall, pick(Blocks.WHITE_BED, Blocks.GRAY_BED, Blocks.BROWN_BED, Blocks.LIGHT_BLUE_BED, Blocks.GREEN_BED));
						bedDone = true;
					} else if (chance(0.5)) {
						set(x, y, z, up(Blocks.BARREL));
						set(x, y + 1, z, pick(d(Blocks.POTTED_FERN), d(Blocks.POTTED_SPRUCE_SAPLING), lantern(false)));
					} else {
						set(x, y, z, d(Blocks.DARK_OAK_PLANKS));
						set(x, y + 1, z, d(Blocks.DARK_OAK_PLANKS));
					}
				}
				case 1 -> {
					double r = this.rnd.nextDouble();
					if (r < 0.2) {
						set(x, y, z, facing(Blocks.SMOKER, in));
					} else if (r < 0.35) {
						set(x, y, z, facing(Blocks.FURNACE, in));
					} else if (r < 0.5) {
						set(x, y, z, Blocks.WATER_CAULDRON.getDefaultState().with(Properties.LEVEL_3, 3));
					} else if (r < 0.65) {
						set(x, y, z, d(Blocks.IRON_BLOCK));
						set(x, y + 1, z, d(Blocks.IRON_BLOCK));
					} else {
						set(x, y, z, up(Blocks.BARREL));
					}
				}
				case 2 -> {
					double r = this.rnd.nextDouble();
					if (r < 0.4) {
						set(x, y, z, stairs(Blocks.SPRUCE_STAIRS, wall, false));
					} else if (r < 0.6) {
						set(x, y, z, d(Blocks.BOOKSHELF));
						set(x, y + 1, z, d(Blocks.BOOKSHELF));
					} else if (r < 0.75) {
						set(x, y, z, d(Blocks.DARK_OAK_PLANKS));
						set(x, y + 1, z, d(Blocks.BLACK_CONCRETE));
					} else {
						set(x, y, z, pick(d(Blocks.POTTED_FERN), d(Blocks.POTTED_POPPY), d(Blocks.POTTED_AZALEA_BUSH)));
					}
				}
				case 3 -> {
					int cx = x + in.getOffsetX();
					int cz = z + in.getOffsetZ();
					double r = this.rnd.nextDouble();
					if (r < 0.4 && cx >= ax && cx <= bx && cz >= az && cz <= bz && !solid(cx, y, cz) && !blocked(cx, cz, y, keep)) {
						set(x, y, z, slab(Blocks.SPRUCE_SLAB, true));
						set(cx, y, cz, stairs(Blocks.SPRUCE_STAIRS, in, false));
					} else if (r < 0.65) {
						set(x, y, z, d(Blocks.BOOKSHELF));
					} else if (r < 0.8) {
						set(x, y, z, facing(Blocks.LECTERN, in));
					} else {
						set(x, y, z, d(Blocks.CARTOGRAPHY_TABLE));
					}
				}
				case 4 -> {
					double r = this.rnd.nextDouble();
					if (r < 0.35) {
						set(x, y, z, Blocks.WATER_CAULDRON.getDefaultState().with(Properties.LEVEL_3, 3));
					} else if (r < 0.55) {
						set(x, y, z, d(Blocks.HOPPER));
					} else if (r < 0.75) {
						set(x, y, z, d(Blocks.QUARTZ_BLOCK));
					} else {
						set(x, y, z, d(Blocks.WHITE_CARPET));
					}
				}
				case 5 -> {
					int h = 1 + this.rnd.nextInt(2);
					for (int k = 0; k < h; k++) {
						set(x, y + k, z, pick(up(Blocks.BARREL), d(Blocks.SPRUCE_PLANKS), log(Blocks.HAY_BLOCK, Direction.Axis.Y),
								d(Blocks.COMPOSTER)));
					}
				}
				default -> {
					double r = this.rnd.nextDouble();
					if (r < 0.3) {
						set(x, y, z, d(Blocks.COBWEB));
					} else if (r < 0.6) {
						set(x, y, z, slab(Blocks.COBBLESTONE_SLAB, false));
					} else {
						set(x, y, z, d(Blocks.COARSE_DIRT));
					}
				}
			}
		}
		if (type == 1 && w >= 5 && dd >= 5) {
			int cx = (ax + bx) / 2;
			int cz = (az + bz) / 2;
			if (!blocked(cx, cz, y, keep) && !solid(cx, y, cz)) {
				set(cx, y, cz, d(Blocks.OAK_FENCE));
				set(cx, y + 1, cz, d(Blocks.OAK_PRESSURE_PLATE));
				for (Direction dir : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
					int sx = cx + dir.getOffsetX();
					int sz = cz + dir.getOffsetZ();
					if (chance(0.7) && !solid(sx, y, sz) && !blocked(sx, sz, y, keep)) {
						set(sx, y, sz, stairs(Blocks.OAK_STAIRS, dir, false));
					}
				}
			}
		}
		if ((type == 2 || type == 0) && w >= 5 && dd >= 5) {
			Block carpet = pick(Blocks.RED_CARPET, Blocks.GREEN_CARPET, Blocks.BROWN_CARPET, Blocks.GRAY_CARPET, Blocks.CYAN_CARPET);
			for (int x = ax + 1; x <= bx - 1; x++) {
				for (int z = az + 1; z <= bz - 1; z++) {
					if (!solid(x, y, z)) {
						set(x, y, z, d(carpet));
					}
				}
			}
		}
		int cx = (ax + bx) / 2;
		int cz = (az + bz) / 2;
		if (!solid(cx, fy + 4, cz)) {
			set(cx, fy + 4, cz, lantern(true));
		}
	}

	private boolean blocked(int x, int z, int y, List<int[]> keep) {
		for (int[] k : keep) {
			if (Math.abs(k[0] - x) + Math.abs(k[1] - z) <= 1) {
				return true;
			}
		}
		for (Direction dir : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
			BlockState n = get(x + dir.getOffsetX(), y, z + dir.getOffsetZ());
			if (n == null || n.isAir() || n.getBlock() instanceof DoorBlock) {
				BlockState wall = get(x + dir.getOffsetX(), y + 2, z + dir.getOffsetZ());
				if (wall != null && !wall.isAir() && !(wall.getBlock() instanceof DoorBlock)) {
					return true;
				}
			}
		}
		return false;
	}

	private void path(int x0, int z0, int x1, int z1) {
		int x = x0;
		int z = z0;
		while (z != z1) {
			pathCell(x, z);
			z += Integer.signum(z1 - z);
		}
		while (x != x1) {
			pathCell(x, z);
			x += Integer.signum(x1 - x);
		}
		pathCell(x, z);
	}

	private void pathCell(int x, int z) {
		for (int dx = 0; dx <= 1; dx++) {
			BlockState cur = get(x + dx, G, z);
			if (cur != null && (cur.isOf(Blocks.GRASS_BLOCK) || cur.isOf(Blocks.COARSE_DIRT) || cur.isOf(Blocks.PODZOL)
					|| cur.isOf(Blocks.MOSS_BLOCK))) {
				set(x + dx, G, z, d(Blocks.DIRT_PATH));
				if (!solid(x + dx, G + 2, z)) {
					set(x + dx, G + 1, z, AIR);
				}
			}
			mark(x + dx, z, x + dx, z);
		}
	}

	private void shed(int x0, int z0, Direction front) {
		if (!free(x0, z0, x0 + 4, z0 + 4)) {
			return;
		}
		mark(x0 - 1, z0 - 1, x0 + 5, z0 + 5);
		for (int x = x0; x <= x0 + 4; x++) {
			for (int z = z0; z <= z0 + 4; z++) {
				boolean rim = x == x0 || x == x0 + 4 || z == z0 || z == z0 + 4;
				set(x, G, z, d(Blocks.SPRUCE_PLANKS));
				for (int y = G + 1; y <= G + 3; y++) {
					set(x, y, z, rim ? d(Blocks.SPRUCE_PLANKS) : AIR);
				}
				set(x, G + 4, z, slab(Blocks.SPRUCE_SLAB, false));
			}
		}
		int dx = front == Direction.WEST ? x0 : front == Direction.EAST ? x0 + 4 : x0 + 2;
		int dz = front == Direction.NORTH ? z0 : front == Direction.SOUTH ? z0 + 4 : z0 + 2;
		fill(dx, G + 1, dz, dx, G + 2, dz, AIR);
		door(dx, G + 1, dz, Blocks.SPRUCE_DOOR, front);
		set(x0 + 1, G + 1, z0 + 3, up(Blocks.BARREL));
		set(x0 + 3, G + 1, z0 + 3, log(Blocks.HAY_BLOCK, Direction.Axis.Y));
		set(x0 + 3, G + 1, z0 + 1, d(Blocks.COMPOSTER));
		if (front.getAxis() == Direction.Axis.Z) {
			set(x0, G + 2, z0 + 2, d(Blocks.GLASS_PANE));
		} else {
			set(x0 + 2, G + 2, z0 + 4, d(Blocks.GLASS_PANE));
		}
	}

	private void yardFence(int x0, int z0, int x1, int z1, int gateX) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean rim = x == x0 || x == x1 || z == z0 || z == z1;
				if (!rim || Math.abs(x - gateX) <= 1 || (x + z) % 9 == 0) {
					continue;
				}
				BlockState cur = get(x, G + 1, z);
				BlockState ground = get(x, G, z);
				if ((cur == null || cur.isAir()) && ground != null && !ground.isOf(Blocks.DIRT_PATH)
						&& !ground.isOf(Blocks.BLACK_CONCRETE) && !ground.isOf(Blocks.SMOOTH_STONE)) {
					set(x, G + 1, z, d(Blocks.OAK_FENCE));
				}
			}
		}
	}

	// ---------- улицы ----------

	private void streets() {
		for (int x = 30; x <= 120; x += 12) {
			if (Math.abs(x - 75) < 9) {
				continue;
			}
			streetLamp(x, 70, Direction.SOUTH);
			streetLamp(x + 6, 80, Direction.NORTH);
			streetLamp(x + 3, 114, Direction.NORTH);
		}
		for (int z = 10; z <= 140; z += 12) {
			if (Math.abs(z - 75) < 9 || Math.abs(z - 110) < 7) {
				continue;
			}
			streetLamp(70, z, Direction.EAST);
			streetLamp(80, z + 6, Direction.WEST);
		}
		trafficLight(71, 71, false);
		trafficLight(79, 71, false);
		trafficLight(71, 79, false);
		trafficLight(79, 79, true);
		busStop(100, 79);
		billboard(36, 68, 7);
		billboard(104, 68, 7);
		powerLine();
		// скамейки, урны и гидранты вдоль тротуаров
		for (int i = 0; i < 26; i++) {
			int x = 26 + this.rnd.nextInt(98);
			int[] rows = {70, 80, 107, 113};
			int z = rows[this.rnd.nextInt(rows.length)];
			Direction look = z == 70 || z == 107 ? Direction.SOUTH : Direction.NORTH;
			if (!isWalk(x, z) || !isWalk(x + 1, z) || !isWalk(x + 2, z)) {
				continue;
			}
			double r = this.rnd.nextDouble();
			if (r < 0.5) {
				bench(x, z, look, 2);
				set(x + 2, G + 1, z, chance(0.5) ? d(Blocks.CAULDRON) : d(Blocks.COMPOSTER));
			} else if (r < 0.75) {
				set(x, G + 1, z, d(Blocks.RED_NETHER_BRICK_WALL));
			} else {
				set(x, G + 1, z, up(Blocks.BARREL));
				if (chance(0.5)) {
					set(x + 1, G + 1, z, d(Blocks.BLACK_WOOL));
				}
			}
			mark(x, z, x + 2, z);
		}
		// люки и выбоины на асфальте
		int hatches = 0;
		int holes = 0;
		for (int i = 0; i < 600 && (hatches < 8 || holes < 16); i++) {
			int x = 25 + this.rnd.nextInt(100);
			int z = 4 + this.rnd.nextInt(142);
			if (!asphalt(get(x, G, z)) || solid(x, G + 1, z)) {
				continue;
			}
			if (hatches < 8) {
				set(x, G, z, trapdoor(Blocks.IRON_TRAPDOOR, Direction.NORTH, true));
				hatches++;
				continue;
			}
			set(x, G, z, AIR);
			Direction dir = pick(SIDES);
			if (chance(0.5) && asphalt(get(x + dir.getOffsetX(), G, z + dir.getOffsetZ()))
					&& !solid(x + dir.getOffsetX(), G + 1, z + dir.getOffsetZ())) {
				set(x + dir.getOffsetX(), G, z + dir.getOffsetZ(), AIR);
			}
			holes++;
		}
		// мусор на тротуарах
		for (int x = 25; x <= 124; x++) {
			for (int z = 4; z <= 145; z++) {
				if (isWalk(x, z) && chance(0.025)) {
					set(x, G + 1, z, chance(0.8) ? d(Blocks.WHITE_CARPET) : d(Blocks.LIGHT_GRAY_CARPET));
				}
			}
		}
	}

	/** Уличный фонарь с выносом к дороге; часть разбита, часть повалена. */
	private void streetLamp(int x, int z, Direction arm) {
		if (!isWalk(x, z)) {
			return;
		}
		int ax = x + arm.getOffsetX();
		int az = z + arm.getOffsetZ();
		if (solid(x, G + 2, z) || solid(ax, G + 6, az) || solid(ax, G + 5, az)) {
			return;
		}
		double r = this.rnd.nextDouble();
		set(x, G + 1, z, d(Blocks.ANDESITE_WALL));
		mark(x, z, x, z);
		if (r < 0.12) {
			Direction along = arm.rotateYClockwise();
			for (int k = 1; k <= 5; k++) {
				int fx = x + along.getOffsetX() * k;
				int fz = z + along.getOffsetZ() * k;
				if (!solid(fx, G + 1, fz) && solid(fx, G, fz)) {
					set(fx, G + 1, fz, d(Blocks.IRON_BARS));
				}
			}
			return;
		}
		for (int y = G + 2; y <= G + 6; y++) {
			set(x, y, z, d(Blocks.IRON_BARS));
		}
		set(ax, G + 6, az, slab(Blocks.SMOOTH_STONE_SLAB, true));
		if (r > 0.35) {
			set(ax, G + 5, az, lantern(true));
		}
	}

	/** Светофор на углу перекрёстка; один сбит и лежит. */
	private void trafficLight(int x, int z, boolean fallen) {
		if (fallen) {
			int[] cells = {1, 2, 3};
			for (int k : cells) {
				if (!solid(x, G + 1, z + k)) {
					set(x, G + 1, z + k, d(Blocks.IRON_BARS));
				}
			}
			set(x, G + 1, z + 4, d(Blocks.RED_STAINED_GLASS));
			set(x + 1, G + 1, z + 4, d(Blocks.YELLOW_STAINED_GLASS));
			set(x, G + 1, z, d(Blocks.ANDESITE_WALL));
			return;
		}
		set(x, G + 1, z, d(Blocks.ANDESITE_WALL));
		for (int y = G + 2; y <= G + 3; y++) {
			set(x, y, z, d(Blocks.IRON_BARS));
		}
		set(x, G + 4, z, d(Blocks.LIME_STAINED_GLASS));
		set(x, G + 5, z, d(Blocks.YELLOW_STAINED_GLASS));
		set(x, G + 6, z, d(Blocks.RED_STAINED_GLASS));
		set(x, G + 7, z, slab(Blocks.SMOOTH_STONE_SLAB, false));
		mark(x, z, x, z);
	}

	/** Остановка: навес, стеклянная задняя стенка, скамья к дороге. */
	private void busStop(int x0, int z0) {
		for (int x = x0; x <= x0 + 4; x++) {
			set(x, G + 4, z0, slab(Blocks.SMOOTH_STONE_SLAB, false));
			set(x, G + 4, z0 + 1, slab(Blocks.SMOOTH_STONE_SLAB, false));
			boolean post = x == x0 || x == x0 + 4;
			for (int y = G + 1; y <= G + 3; y++) {
				set(x, y, z0 + 1, post ? d(Blocks.IRON_BARS) : (chance(0.5) ? AIR : d(Blocks.GLASS_PANE)));
			}
			if (!post) {
				set(x, G + 1, z0, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
			}
		}
		set(x0 + 4, G + 2, z0, d(Blocks.LIGHT_BLUE_CONCRETE));
		mark(x0, z0, x0 + 4, z0 + 1);
	}

	/** Рекламный щит на двух стойках, полотно порвано. */
	private void billboard(int x0, int z, int w) {
		BlockState[] ad = {d(Blocks.RED_CONCRETE), d(Blocks.YELLOW_CONCRETE), d(Blocks.BLUE_CONCRETE)};
		for (int y = G + 1; y <= G + 7; y++) {
			set(x0, y, z, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
			set(x0 + w - 1, y, z, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
		}
		for (int x = x0 + 1; x < x0 + w - 1; x++) {
			for (int y = G + 4; y <= G + 7; y++) {
				if (chance(0.2)) {
					continue;
				}
				boolean frame = y == G + 4 || y == G + 7;
				set(x, y, z, frame ? d(Blocks.WHITE_CONCRETE) : ad[Math.min(2, (x - x0 - 1) * 3 / (w - 2))]);
			}
		}
		mark(x0, z, x0 + w - 1, z);
	}

	/** Столбы с проводами вдоль переулка; один столб повален, часть проводов оборвана. */
	private void powerLine() {
		int z = 106;
		int[] poles = {28, 38, 48, 58, 68, 82, 92, 102, 112, 122};
		int fallen = 1 + this.rnd.nextInt(poles.length - 2);
		for (int i = 0; i < poles.length; i++) {
			int px = poles[i];
			if (!isWalk(px, z)) {
				continue;
			}
			mark(px, z, px, z);
			if (i == fallen) {
				for (int k = 0; k < 7; k++) {
					if (!solid(px + k, G + 1, z) && solid(px + k, G, z)) {
						set(px + k, G + 1, z, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
					}
				}
				continue;
			}
			for (int y = G + 1; y <= G + 8; y++) {
				set(px, y, z, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y));
			}
			set(px, G + 8, z - 1, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
			set(px, G + 8, z + 1, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
		}
		BlockState wire = Blocks.CHAIN.getDefaultState().with(Properties.AXIS, Direction.Axis.X);
		for (int i = 0; i + 1 < poles.length; i++) {
			int a = poles[i];
			int b = poles[i + 1];
			boolean broken = i == fallen || i + 1 == fallen || chance(0.15);
			for (int wz : new int[] {z - 1, z + 1}) {
				if (broken) {
					int from = i == fallen ? b : a;
					int dir = i == fallen ? -1 : 1;
					if (i + 1 == fallen || i == fallen || chance(0.5)) {
						for (int y = G + 7; y >= G + 5; y--) {
							if (!solid(from + dir, y, wz)) {
								set(from + dir, y, wz, d(Blocks.CHAIN));
							}
						}
					}
					continue;
				}
				for (int x = a + 1; x < b; x++) {
					int y = (x - a == 1 || b - x == 1) ? G + 8 : G + 7;
					if (!solid(x, y, wz)) {
						set(x, y, wz, wire);
					}
				}
			}
		}
	}

	private void garden(int x0, int z0, int x1, int z1) {
		if (!free(x0, z0, x1, z1)) {
			return;
		}
		mark(x0 - 1, z0 - 1, x1 + 1, z1 + 1);
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean rim = x == x0 || x == x1 || z == z0 || z == z1;
				if (rim) {
					if ((x + z) % 5 != 0) {
						set(x, G + 1, z, d(Blocks.OAK_FENCE));
					}
					continue;
				}
				set(x, G, z, d(Blocks.ROOTED_DIRT));
				double r = this.rnd.nextDouble();
				if (r < 0.15) {
					set(x, G + 1, z, d(Blocks.PUMPKIN));
				} else if (r < 0.25) {
					set(x, G + 1, z, log(Blocks.HAY_BLOCK, Direction.Axis.Y));
				} else if (r < 0.5) {
					set(x, G + 1, z, leaves(Blocks.AZALEA_LEAVES));
				}
			}
		}
	}

	// ---------- центр ----------

	private void center() {
		nest(67, 68, Direction.WEST);
		nest(81, 68, Direction.EAST);
		nest(67, 80, Direction.WEST);
		nest(81, 80, Direction.EAST);
		for (int[] n : new int[][] {{66, 60}, {90, 60}}) {
			if (free(n[0] - 1, n[1] - 1, n[0] + 3, n[1] + 3)) {
				nest(n[0], n[1], n[0] < 75 ? Direction.EAST : Direction.WEST);
			}
		}
		tankWreck(73, 82);
		busWreck(86, 75);
		car(73, 58, false, d(Blocks.GRAY_CONCRETE), true);
		car(48, 73, true, d(Blocks.WHITE_CONCRETE), true);
		car(98, 75, true, d(Blocks.LIGHT_BLUE_CONCRETE), false);
		car(75, 100, false, d(Blocks.ORANGE_CONCRETE), true);
		car(30, 73, true, d(Blocks.RED_CONCRETE), false);
		car(56, 73, true, d(Blocks.BLUE_CONCRETE), true);
		car(116, 75, true, d(Blocks.BLACK_CONCRETE), true);
		car(73, 30, false, d(Blocks.YELLOW_CONCRETE), false);
		car(75, 120, false, d(Blocks.GREEN_CONCRETE), true);
		car(73, 136, false, d(Blocks.LIGHT_GRAY_CONCRETE), false);
		car(40, 109, true, d(Blocks.WHITE_CONCRETE), false);
		carFlipped(100, 109, true, d(Blocks.RED_CONCRETE));
		// «ежи» на перекрёстке
		for (int i = 0, n = 0; i < 120 && n < 8; i++) {
			int x = 66 + this.rnd.nextInt(19);
			int z = 66 + this.rnd.nextInt(19);
			BlockState g = get(x, G, z);
			if (g == null || g.isAir() || solid(x, G + 1, z) || !(roadCell(x, z) || isWalk(x, z)) || nearGuard(x, G + 1, z)) {
				continue;
			}
			set(x, G + 1, z, facing(chance(0.5) ? Blocks.DAMAGED_ANVIL : Blocks.CHIPPED_ANVIL, pick(SIDES)));
			n++;
		}
		// заслоны в концах переулка
		for (int z = 108; z <= 112; z++) {
			if (z == 110) {
				continue;
			}
			sandbag(25, z, 2);
			sandbag(124, z, 2);
		}
		set(26, G + 1, 110, up(Blocks.BARREL));
		set(123, G + 1, 110, up(Blocks.BARREL));
		for (int[] b : new int[][] {{76, 95}, {76, 112}, {62, 75}, {110, 73}, {73, 40}, {76, 24}}) {
			for (int k = 0; k < 2; k++) {
				boolean alongX = b[0] < 72 || b[0] > 78;
				int x = alongX ? b[0] : b[0] + k;
				int z = alongX ? b[1] + k : b[1];
				set(x, G + 1, z, d(Blocks.POLISHED_ANDESITE));
				set(x, G + 2, z, slab(Blocks.ANDESITE_SLAB, false));
			}
		}
		// развалины хуторов в лесу
		ruin(8, 16);
		ruin(135, 16);
		ruin(8, 128);
		ruin(135, 128);
	}

	/** Пулемётное гнездо из мешков 3×3, вход с одной стороны. */
	private void nest(int cx, int cz, Direction open) {
		for (int x = cx; x <= cx + 2; x++) {
			for (int z = cz; z <= cz + 2; z++) {
				boolean rim = x == cx || x == cx + 2 || z == cz || z == cz + 2;
				boolean gap = switch (open) {
					case WEST -> x == cx && z == cz + 1;
					case EAST -> x == cx + 2 && z == cz + 1;
					case NORTH -> z == cz && x == cx + 1;
					default -> z == cz + 2 && x == cx + 1;
				};
				if (rim && !gap) {
					sandbag(x, z, 2);
				} else {
					fill(x, G + 1, z, x, G + 2, z, AIR);
				}
			}
		}
		mark(cx - 1, cz - 1, cx + 3, cz + 3);
	}

	/** Подбитый танк вдоль дороги: гусеницы, корпус, башня с пушкой; горит. */
	private void tankWreck(int x0, int z0) {
		BlockState hull = d(Blocks.GREEN_TERRACOTTA);
		for (int i = 0; i <= 8; i++) {
			for (int j = 0; j <= 4; j++) {
				int x = x0 + j;
				int z = z0 + i;
				boolean track = j == 0 || j == 4;
				boolean end = i == 0 || i == 8;
				set(x, G + 1, z, track ? d(Blocks.POLISHED_BLACKSTONE) : (end ? AIR : hull));
				if (!end) {
					set(x, G + 2, z, chance(0.25) ? charred(hull) : (track ? d(Blocks.BLACKSTONE) : hull));
				}
			}
		}
		for (int i = 2; i <= 5; i++) {
			for (int j = 1; j <= 3; j++) {
				set(x0 + j, G + 3, z0 + i, chance(0.2) ? d(Blocks.BLACKSTONE) : hull);
			}
		}
		set(x0 + 2, G + 4, z0 + 3, hull);
		set(x0 + 2, G + 4, z0 + 4, trapdoor(Blocks.IRON_TRAPDOOR, Direction.SOUTH, false));
		for (int z = z0 + 6; z <= z0 + 10; z++) {
			set(x0 + 2, G + 3, z, log(Blocks.POLISHED_BASALT, Direction.Axis.Z));
		}
		for (int k = 0; k < 3; k++) {
			set(x0 - 1, G + 1, z0 + 2 + k * 2, d(Blocks.POLISHED_BLACKSTONE));
		}
		mark(x0 - 1, z0 - 1, x0 + 5, z0 + 11);
		this.flames.add(new int[] {x0 + 2, G + 3, z0 + 3});
	}

	/** Сгоревший автобус: обугленный кузов, окна выбиты, крыша прогорела. */
	private void busWreck(int x0, int z0) {
		for (int i = 0; i <= 10; i++) {
			for (int j = 0; j <= 2; j++) {
				int x = x0 + i;
				int z = z0 + j;
				boolean wheel = (i == 1 || i == 2 || i == 8 || i == 9) && j != 1;
				boolean shell = j != 1 || i == 0 || i == 10;
				set(x, G + 1, z, wheel ? d(Blocks.BLACK_CONCRETE) : pick(d(Blocks.BLACKSTONE), d(Blocks.GRAY_CONCRETE)));
				set(x, G + 2, z, shell ? pick(d(Blocks.YELLOW_TERRACOTTA), d(Blocks.BLACKSTONE), d(Blocks.BLACK_TERRACOTTA)) : AIR);
				set(x, G + 3, z, shell && i % 3 == 0 ? d(Blocks.BLACKSTONE) : AIR);
				set(x, G + 4, z, chance(0.7) ? pick(d(Blocks.BLACKSTONE), d(Blocks.YELLOW_TERRACOTTA)) : AIR);
			}
		}
		fill(x0 + 2, G + 2, z0, x0 + 2, G + 3, z0, AIR);
		mark(x0 - 1, z0 - 1, x0 + 11, z0 + 3);
		this.flames.add(new int[] {x0 + 5, G + 2, z0 + 1});
		this.smoke.add(new int[] {x0 + 8, G + 2, z0 + 1});
	}

	/** Перевёрнутая машина: колёса кверху, кабина смята. */
	private void carFlipped(int x0, int z0, boolean alongX, BlockState body) {
		for (int i = 0; i < 5; i++) {
			for (int j = 0; j < 3; j++) {
				int x = alongX ? x0 + i : x0 + j;
				int z = alongX ? z0 + j : z0 + i;
				boolean wheel = (i == 0 || i == 4) && (j == 0 || j == 2);
				set(x, G + 1, z, i >= 1 && i <= 3 ? (j == 1 ? d(Blocks.BLACK_STAINED_GLASS) : body) : AIR);
				set(x, G + 2, z, body);
				set(x, G + 3, z, wheel ? d(Blocks.BLACK_CONCRETE) : (i == 0 || i == 4 ? AIR : d(Blocks.GRAY_CONCRETE)));
			}
		}
	}

	private void ruin(int x0, int z0) {
		if (!free(x0, z0, x0 + 6, z0 + 5)) {
			return;
		}
		mark(x0 - 1, z0 - 1, x0 + 7, z0 + 6);
		for (int x = x0; x <= x0 + 6; x++) {
			for (int z = z0; z <= z0 + 5; z++) {
				boolean rim = x == x0 || x == x0 + 6 || z == z0 || z == z0 + 5;
				set(x, G, z, chance(0.3) ? d(Blocks.GRAVEL) : d(Blocks.COBBLESTONE));
				if (!rim) {
					continue;
				}
				int h = this.rnd.nextInt(4);
				for (int y = G + 1; y <= G + h; y++) {
					set(x, y, z, pick(d(Blocks.COBBLESTONE), d(Blocks.MOSSY_COBBLESTONE), d(Blocks.CRACKED_STONE_BRICKS),
							d(Blocks.MOSSY_STONE_BRICKS)));
				}
			}
		}
		set(x0 + 3, G + 1, z0 + 2, d(Blocks.COBWEB));
		this.smoke.add(new int[] {x0 + 1, G + 1, z0 + 1});
	}

	// ---------- следы боя: промзона, воронки, окопы, ячейки ----------

	private void battle() {
		// дыра в крыше ангара и пробоина в его восточной стене
		blast(45.5, G + 10.5, 22.5, 3.4);
		rubble(45, 22, 2, G + 1, d(Blocks.WAXED_WEATHERED_CUT_COPPER));
		this.smoke.add(new int[] {44, G + 1, 21});
		blast(63.5, G + 2.5, 31.5, 2.2);
		rubble(66, 31, 1, G + 1, d(Blocks.LIGHT_GRAY_CONCRETE));
		rubble(61, 31, 1, G + 1, d(Blocks.LIGHT_GRAY_CONCRETE));
		// цех: пробоина в северной стене и провал пилообразной крыши
		blast(100.5, G + 3.5, 12.5, 2.0);
		rubble(100, 10, 1, G + 1, d(Blocks.BRICKS));
		blast(93.5, G + 10.5, 31.5, 2.6);
		rubble(93, 31, 1, G + 1, d(Blocks.DEEPSLATE_TILES));
		// взорванная цистерна горит, контейнер разворочен, у трубы снесён верх
		blast(111.5, G + 6.5, 53.5, 3.0);
		this.flames.add(new int[] {111, G + 1, 53});
		blast(51.5, G + 2.5, 47.5, 2.2);
		blast(121.5, G + 19.5, 42.5, 2.2);
		// окопы перед базами: зигзаг с выходами, бруствером и настилом
		for (int team = 0; team < 2; team++) {
			trench(team, new int[][] {{3, 46}, {9, 46}, {9, 49}, {15, 49}, {15, 46}, {22, 46}});
			trench(team, new int[][] {{3, 104}, {9, 104}, {9, 101}, {15, 101}, {15, 104}, {22, 104}});
			trench(team, new int[][] {{5, 30}, {12, 30}, {12, 33}, {20, 33}});
			trench(team, new int[][] {{5, 122}, {12, 122}, {12, 119}, {20, 119}});
		}
		// стрелковые ячейки
		for (int attempt = 0, made = 0; attempt < 500 && made < 22; attempt++) {
			int s = 2 + this.rnd.nextInt(2);
			int x = 3 + this.rnd.nextInt(SIZE - 8);
			int z = 3 + this.rnd.nextInt(SIZE - 8);
			if (foxhole(x, z, s)) {
				made++;
			}
		}
		// воронки: половина — в городе и промзоне, остальные где угодно
		List<int[]> craters = new ArrayList<>();
		for (int attempt = 0; attempt < 1500 && craters.size() < 34; attempt++) {
			boolean town = craters.size() < 18;
			int r = town ? 2 + this.rnd.nextInt(2) : 2 + this.rnd.nextInt(3);
			int cx = town ? 26 + this.rnd.nextInt(99) : 4 + this.rnd.nextInt(SIZE - 8);
			int cz = 4 + this.rnd.nextInt(SIZE - 8);
			boolean near = false;
			for (int[] c : craters) {
				int dx = c[0] - cx;
				int dz = c[1] - cz;
				if (dx * dx + dz * dz < (c[2] + r + 4) * (c[2] + r + 4)) {
					near = true;
					break;
				}
			}
			if (near || !craterOk(cx, cz, r)) {
				continue;
			}
			crater(cx, cz, r);
			craters.add(new int[] {cx, cz, r});
		}
	}

	private static boolean naturalGround(BlockState t) {
		return t != null && (t.isOf(Blocks.GRASS_BLOCK) || t.isOf(Blocks.COARSE_DIRT) || t.isOf(Blocks.PODZOL)
				|| t.isOf(Blocks.MOSS_BLOCK) || t.isOf(Blocks.ROOTED_DIRT) || t.isOf(Blocks.DIRT) || t.isOf(Blocks.GRAVEL));
	}

	private static boolean craterGround(BlockState t) {
		return naturalGround(t) || asphalt(t) || t.isOf(Blocks.DIRT_PATH) || t.isOf(Blocks.ANDESITE) || t.isOf(Blocks.STONE)
				|| t.isOf(Blocks.CRACKED_STONE_BRICKS) || t.isOf(Blocks.POLISHED_ANDESITE) || t.isOf(Blocks.STONE_BRICKS)
				|| t.isOf(Blocks.SMOOTH_STONE) || t.isOf(Blocks.YELLOW_CONCRETE) || t.isOf(Blocks.WHITE_CONCRETE);
	}

	private static boolean inBase(int x, int z) {
		return (x <= 26 || x >= SIZE - 27) && z >= 52 && z <= 98;
	}

	/** Воронка возможна только под открытым небом, на земле или асфальте, без подземелий снизу. */
	private boolean craterOk(int cx, int cz, int r) {
		for (int x = cx - r - 1; x <= cx + r + 1; x++) {
			for (int z = cz - r - 1; z <= cz + r + 1; z++) {
				if (x < 3 || z < 3 || x > SIZE - 4 || z > SIZE - 4 || inBase(x, z)) {
					return false;
				}
				if ((x - cx) * (x - cx) + (z - cz) * (z - cz) > (r + 0.5) * (r + 0.5)) {
					continue;
				}
				BlockState top = get(x, G, z);
				if (top == null || !craterGround(top) || guarded(x, G, z)) {
					return false;
				}
				for (int y = G + 1; y < HEIGHT - 1; y++) {
					if (solid(x, y, z)) {
						return false;
					}
				}
				BlockState a = get(x, 1, z);
				BlockState b = get(x, 2, z);
				if (a == null || !a.isOf(Blocks.DIRT) || b == null || !(b.isOf(Blocks.DIRT) || b.isOf(Blocks.STONE))) {
					return false;
				}
			}
		}
		return true;
	}

	/** Воронка: чаша со ступенями в один блок (из неё можно выбраться), выброс грунта и копоть вокруг. */
	private void crater(int cx, int cz, int r) {
		boolean paved = !naturalGround(get(cx, G, cz));
		int maxD = r >= 3 ? 2 : 1;
		double rr = r + 0.3;
		for (int x = cx - r - 3; x <= cx + r + 3; x++) {
			for (int z = cz - r - 3; z <= cz + r + 3; z++) {
				if (x < 1 || z < 1 || x > SIZE - 2 || z > SIZE - 2) {
					continue;
				}
				double dist = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
				if (dist > rr + 2.2) {
					continue;
				}
				BlockState t = get(x, G, z);
				int depth = dist > rr ? 0 : Math.max(0, Math.min(maxD, (int) Math.floor(maxD * (1 - dist / rr) + 0.7)));
				if (depth == 0) {
					if (t != null && naturalGround(t) && chance(0.55)) {
						set(x, G, z, pick(d(Blocks.COARSE_DIRT), d(Blocks.PODZOL), d(Blocks.ROOTED_DIRT)));
					}
					if (dist > rr && dist <= rr + 1.4 && solid(x, G, z) && !solid(x, G + 1, z) && !nearGuard(x, G + 1, z)
							&& chance(0.4)) {
						set(x, G + 1, z, paved
								? pick(d(Blocks.BLACKSTONE), slab(Blocks.BLACKSTONE_SLAB, false), d(Blocks.COBBLESTONE),
								slab(Blocks.COBBLESTONE_SLAB, false))
								: pick(d(Blocks.COARSE_DIRT), d(Blocks.ROOTED_DIRT), slab(Blocks.MUD_BRICK_SLAB, false)));
					}
					continue;
				}
				for (int y = G; y > G - depth; y--) {
					set(x, y, z, AIR);
				}
				double k = dist / rr;
				BlockState floor;
				if (k < 0.35 && chance(0.07)) {
					floor = d(Blocks.MAGMA_BLOCK);
				} else if (paved) {
					floor = k < 0.5 ? pick(d(Blocks.BLACKSTONE), d(Blocks.GRAVEL), d(Blocks.COBBLESTONE), d(Blocks.STONE))
							: pick(d(Blocks.GRAVEL), d(Blocks.STONE), d(Blocks.BLACKSTONE), d(Blocks.ANDESITE));
				} else {
					floor = k < 0.45 ? pick(d(Blocks.BLACKSTONE), d(Blocks.COARSE_DIRT), d(Blocks.PODZOL), d(Blocks.COARSE_DIRT))
							: pick(d(Blocks.COARSE_DIRT), d(Blocks.ROOTED_DIRT), d(Blocks.DIRT), d(Blocks.PODZOL));
				}
				set(x, G - depth, z, floor);
				mark(x, z, x, z);
			}
		}
		if (r >= 3 || chance(0.5)) {
			this.flames.add(new int[] {cx, 1, cz});
		}
	}

	private boolean diggable(int x, int z) {
		if (x < 2 || z < 2 || x > SIZE - 3 || z > SIZE - 3 || isBusy(x, z) || solid(x, G + 1, z) || inBase(x, z)) {
			return false;
		}
		BlockState a = get(x, 1, z);
		BlockState b = get(x, 2, z);
		return a != null && a.isOf(Blocks.DIRT) && b != null && b.isOf(Blocks.DIRT) && naturalGround(get(x, G, z));
	}

	/** Окоп шириной 2 по ломаной (отрезки вдоль осей); точки задаются для красных и зеркалятся для синих. */
	private void trench(int team, int[][] pts) {
		List<int[]> cells = new ArrayList<>();
		boolean[] seen = new boolean[SIZE * SIZE];
		for (int i = 0; i + 1 < pts.length; i++) {
			int ax = bx(team, pts[i][0]);
			int az = pts[i][1];
			int bxx = bx(team, pts[i + 1][0]);
			int bz = pts[i + 1][1];
			int sx = Integer.signum(bxx - ax);
			int sz = Integer.signum(bz - az);
			int x = ax;
			int z = az;
			while (true) {
				for (int[] c : new int[][] {{x, z}, {x + (sz != 0 ? 1 : 0), z + (sx != 0 ? 1 : 0)}}) {
					if (c[0] >= 0 && c[1] >= 0 && c[0] < SIZE && c[1] < SIZE && !seen[c[0] * SIZE + c[1]]) {
						seen[c[0] * SIZE + c[1]] = true;
						cells.add(c);
					}
				}
				if (x == bxx && z == bz) {
					break;
				}
				x += sx;
				z += sz;
			}
		}
		for (int[] c : cells) {
			if (!diggable(c[0], c[1])) {
				return;
			}
		}
		boolean[] exits = dig(cells, 9);
		for (int[] c : cells) {
			for (Direction dir : SIDES) {
				int nx = c[0] + dir.getOffsetX() * 3;
				int nz = c[1] + dir.getOffsetZ() * 3;
				if (in(nx, 0, nz) && !seen[nx * SIZE + nz] && !exits[nx * SIZE + nz] && solid(nx, G, nz)
						&& !solid(nx, G + 1, nz) && !isBusy(nx, nz) && chance(0.06)) {
					set(nx, G + 1, nz, d(Blocks.COBWEB));
				}
			}
		}
	}

	/** Стрелковая ячейка s×s: выход по ступеням, мешки по кругу. */
	private boolean foxhole(int x0, int z0, int s) {
		List<int[]> cells = new ArrayList<>();
		for (int a = 0; a < s; a++) {
			for (int b = 0; b < s; b++) {
				if (!diggable(x0 + a, z0 + b)) {
					return false;
				}
				cells.add(new int[] {x0 + a, z0 + b});
			}
		}
		if (!free(x0 - 1, z0 - 1, x0 + s, z0 + s)) {
			return false;
		}
		boolean[] exits = dig(cells, 100);
		for (int x = x0 - 1; x <= x0 + s; x++) {
			for (int z = z0 - 1; z <= z0 + s; z++) {
				boolean ring = x == x0 - 1 || x == x0 + s || z == z0 - 1 || z == z0 + s;
				if (ring && !exits[x * SIZE + z] && solid(x, G, z) && !solid(x, G + 1, z) && chance(0.7)) {
					set(x, G + 1, z, chance(0.6) ? slab(Blocks.SANDSTONE_SLAB, false) : d(Blocks.SMOOTH_SANDSTONE));
				}
			}
		}
		mark(x0 - 1, z0 - 1, x0 + s, z0 + s);
		return true;
	}

	/** Копает клетки на два блока вниз; возвращает клетки выходов (ступени наверх). */
	private boolean[] dig(List<int[]> cells, int exitStep) {
		boolean[] dug = new boolean[SIZE * SIZE];
		boolean[] exits = new boolean[SIZE * SIZE];
		for (int[] c : cells) {
			dug[c[0] * SIZE + c[1]] = true;
		}
		for (int[] c : cells) {
			set(c[0], G, c[1], AIR);
			set(c[0], 2, c[1], AIR);
			set(c[0], 1, c[1], pick(d(Blocks.PACKED_MUD), d(Blocks.COARSE_DIRT), d(Blocks.COARSE_DIRT), d(Blocks.ROOTED_DIRT)));
			mark(c[0], c[1], c[0], c[1]);
		}
		for (int[] c : cells) {
			for (Direction dir : SIDES) {
				int nx = c[0] + dir.getOffsetX();
				int nz = c[1] + dir.getOffsetZ();
				if (!in(nx, 0, nz) || dug[nx * SIZE + nz]) {
					continue;
				}
				BlockState w = get(nx, 2, nz);
				if (w != null && w.isOf(Blocks.DIRT) && chance(0.55)) {
					set(nx, 2, nz, chance(0.75) ? d(Blocks.SPRUCE_PLANKS) : log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y));
				}
			}
		}
		for (int i = 0; i < cells.size(); i++) {
			if (i != 0 && i != cells.size() - 1 && i % exitStep != 0) {
				continue;
			}
			int[] c = cells.get(i);
			List<Direction> dirs = new ArrayList<>(List.of(SIDES));
			java.util.Collections.shuffle(dirs, this.rnd);
			for (Direction dir : dirs) {
				int nx = c[0] + dir.getOffsetX();
				int nz = c[1] + dir.getOffsetZ();
				if (!in(nx, 0, nz) || dug[nx * SIZE + nz]) {
					continue;
				}
				if (!solid(nx, G, nz) || !solid(nx, 2, nz) || solid(nx, G + 1, nz) || solid(nx, G + 2, nz)) {
					continue;
				}
				set(c[0], 2, c[1], stairs(Blocks.SPRUCE_STAIRS, dir, false));
				set(nx, G, nz, stairs(Blocks.COBBLESTONE_STAIRS, dir, false));
				exits[nx * SIZE + nz] = true;
				exits[c[0] * SIZE + c[1]] = true;
				break;
			}
		}
		for (int[] c : cells) {
			if (solid(c[0], 2, c[1])) {
				continue;
			}
			double r = this.rnd.nextDouble();
			if (r < 0.35) {
				set(c[0], 2, c[1], trapdoor(Blocks.SPRUCE_TRAPDOOR, pick(SIDES), false));
			} else if (r < 0.45) {
				set(c[0], 2, c[1], slab(Blocks.SPRUCE_SLAB, false));
			}
		}
		for (int[] c : cells) {
			for (Direction dir : SIDES) {
				int nx = c[0] + dir.getOffsetX();
				int nz = c[1] + dir.getOffsetZ();
				if (!in(nx, 0, nz) || dug[nx * SIZE + nz] || exits[nx * SIZE + nz]) {
					continue;
				}
				if (solid(nx, G, nz) && !solid(nx, G + 1, nz) && chance(0.3)) {
					set(nx, G + 1, nz, chance(0.7) ? slab(Blocks.SANDSTONE_SLAB, false) : d(Blocks.SMOOTH_SANDSTONE));
				}
			}
		}
		return exits;
	}

	// ---------- огонь, дым, проверка опор ----------

	/** Костры на негорючем основании и дым из развалин; рядом с огнём не должно быть горючего. */
	private void fires() {
		java.util.Collections.shuffle(this.flames, this.rnd);
		List<int[]> lit = new ArrayList<>();
		for (int[] f : this.flames) {
			if (lit.size() >= 20) {
				break;
			}
			int x = f[0];
			int z = f[2];
			int y = f[1];
			while (y < HEIGHT - 3 && solid(x, y, z)) {
				y++;
			}
			BlockState base = get(x, y - 1, z);
			if (base == null || base.isAir() || thin(base) || base.getBlock() instanceof SlabBlock
					|| base.getBlock() instanceof StairsBlock || base.getBlock() instanceof LeavesBlock || guarded(x, y - 1, z)
					|| nearGuard(x, y, z) || !fireSafe(x, y, z)) {
				continue;
			}
			set(x, y - 1, z, d(Blocks.NETHERRACK));
			set(x, y, z, Blocks.FIRE.getDefaultState());
			lit.add(new int[] {x, y, z});
		}
		int smokes = 0;
		for (int[] s : this.smoke) {
			if (smokes >= 14) {
				break;
			}
			int x = s[0];
			int z = s[2];
			int y = s[1];
			while (y < HEIGHT - 3 && solid(x, y, z)) {
				y++;
			}
			BlockState base = get(x, y - 1, z);
			if (base == null || base.isAir() || thin(base) || guarded(x, y - 1, z) || nearGuard(x, y, z)) {
				continue;
			}
			boolean close = false;
			for (int[] f : lit) {
				if (Math.abs(f[0] - x) <= 4 && Math.abs(f[2] - z) <= 4 && Math.abs(f[1] - y) <= 6) {
					close = true;
					break;
				}
			}
			if (close) {
				continue;
			}
			set(x, y - 1, z, log(Blocks.HAY_BLOCK, Direction.Axis.Y));
			set(x, y, z, Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.LIT, true).with(CampfireBlock.SIGNAL_FIRE, true));
			smokes++;
		}
	}

	/** Огонь распространяется в пределах ±2 по горизонтали и до +4 вверх: там не должно быть горючих блоков. */
	private boolean fireSafe(int fx, int fy, int fz) {
		List<int[]> plants = new ArrayList<>();
		for (int x = fx - 3; x <= fx + 3; x++) {
			for (int z = fz - 3; z <= fz + 3; z++) {
				for (int y = fy - 2; y <= fy + 6; y++) {
					BlockState s = get(x, y, z);
					if (s == null || s.isAir() || !burnable(s)) {
						continue;
					}
					Block b = s.getBlock();
					if (b instanceof PlantBlock || b instanceof CarpetBlock) {
						plants.add(new int[] {x, y, z});
					} else {
						return false;
					}
				}
			}
		}
		for (int[] p : plants) {
			set(p[0], p[1], p[2], AIR);
		}
		return true;
	}

	/** Убирает блоки, которые в мире сразу бы отвалились: без опоры, без второй половины, растения не на земле. */
	private void settle() {
		for (int pass = 0; pass < 6; pass++) {
			boolean changed = false;
			for (int x = 0; x < SIZE; x++) {
				for (int z = 0; z < SIZE; z++) {
					for (int y = 1; y < HEIGHT - 1; y++) {
						BlockState s = get(x, y, z);
						if (s == null || s.isAir() || supported(s, x, y, z)) {
							continue;
						}
						set(x, y, z, AIR);
						changed = true;
					}
				}
			}
			if (!changed) {
				return;
			}
		}
	}

	boolean supported(BlockState s, int x, int y, int z) {
		Block b = s.getBlock();
		BlockState below = get(x, y - 1, z);
		BlockState above = get(x, y + 1, z);
		boolean under = below != null && !below.isAir();
		if (b instanceof TallPlantBlock) {
			if (s.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
				return below != null && below.isOf(b);
			}
			return above != null && above.isOf(b) && soil(below);
		}
		if (b instanceof PlantBlock) {
			return soil(below);
		}
		if (b instanceof DoorBlock) {
			if (s.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
				return below != null && below.isOf(b);
			}
			return under && above != null && above.isOf(b);
		}
		if (b instanceof BedBlock) {
			Direction f = s.get(Properties.HORIZONTAL_FACING);
			Direction to = s.get(Properties.BED_PART) == BedPart.HEAD ? f.getOpposite() : f;
			BlockState o = get(x + to.getOffsetX(), y, z + to.getOffsetZ());
			return o != null && o.isOf(b);
		}
		if (b instanceof LadderBlock) {
			Direction f = s.get(Properties.HORIZONTAL_FACING);
			BlockState w = get(x - f.getOffsetX(), y, z - f.getOffsetZ());
			return w != null && !w.isAir();
		}
		if (b instanceof LanternBlock) {
			BlockState o = s.get(Properties.HANGING) ? above : below;
			return o != null && !o.isAir();
		}
		if (b instanceof CarpetBlock || b instanceof AbstractPressurePlateBlock || b instanceof AbstractFireBlock
				|| b instanceof FallingBlock) {
			return under;
		}
		if (b instanceof VineBlock) {
			for (Direction dir : SIDES) {
				if (s.get(side(dir))) {
					BlockState n = get(x + dir.getOffsetX(), y, z + dir.getOffsetZ());
					if (n != null && !n.isAir()) {
						return true;
					}
				}
			}
			return false;
		}
		return true;
	}

	private static boolean soil(BlockState s) {
		return s != null && (s.isOf(Blocks.GRASS_BLOCK) || s.isOf(Blocks.DIRT) || s.isOf(Blocks.COARSE_DIRT)
				|| s.isOf(Blocks.PODZOL) || s.isOf(Blocks.MOSS_BLOCK) || s.isOf(Blocks.ROOTED_DIRT) || s.isOf(Blocks.MUD)
				|| s.isOf(Blocks.FARMLAND));
	}

	// ---------- лес ----------

	private void forest() {
		int step = 12;
		int n = SIZE / step + 2;
		double[][] grid = new double[n][n];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				grid[i][j] = this.rnd.nextDouble();
			}
		}
		boolean[] trunk = new boolean[SIZE * SIZE];
		for (int attempt = 0; attempt < 5200; attempt++) {
			int x = 3 + this.rnd.nextInt(SIZE - 6);
			int z = 3 + this.rnd.nextInt(SIZE - 6);
			double dens = density(grid, step, x, z);
			if (this.rnd.nextDouble() > dens || !free(x - 2, z - 2, x + 2, z + 2) || !grassy(x, z)) {
				continue;
			}
			boolean near = false;
			for (int ax = x - 3; ax <= x + 3 && !near; ax++) {
				for (int az = z - 3; az <= z + 3; az++) {
					if (ax >= 0 && az >= 0 && ax < SIZE && az < SIZE && trunk[ax * SIZE + az]) {
						near = true;
						break;
					}
				}
			}
			if (near) {
				continue;
			}
			trunk[x * SIZE + z] = true;
			double r = this.rnd.nextDouble();
			if (r < 0.32) {
				oak(x, z);
			} else if (r < 0.52) {
				birch(x, z);
			} else if (r < 0.82) {
				spruce(x, z);
			} else if (r < 0.92 && free(x - 3, z - 3, x + 4, z + 4)) {
				darkOak(x, z);
			} else {
				deadTree(x, z);
			}
		}
		for (int x = 2; x < SIZE - 2; x++) {
			for (int z = 2; z < SIZE - 2; z++) {
				if (isBusy(x, z) || !grassy(x, z) || solid(x, G + 1, z)) {
					continue;
				}
				double dens = density(grid, step, x, z);
				double r = this.rnd.nextDouble();
				if (r < 0.14 * (0.5 + dens)) {
					set(x, G + 1, z, d(Blocks.GRASS));
				} else if (r < 0.19 * (0.5 + dens)) {
					set(x, G + 1, z, d(Blocks.FERN));
				} else if (r < 0.21 * (0.5 + dens) && !solid(x, G + 2, z)) {
					tallPlant(x, G + 1, z, pick(Blocks.LARGE_FERN, Blocks.TALL_GRASS));
				} else if (r < 0.225 * (0.5 + dens)) {
					set(x, G + 1, z, d(pick(Blocks.DANDELION, Blocks.POPPY, Blocks.CORNFLOWER, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY)));
				} else if (r < 0.245 * (0.5 + dens)) {
					set(x, G + 1, z, leaves(pick(Blocks.OAK_LEAVES, Blocks.AZALEA_LEAVES, Blocks.DARK_OAK_LEAVES)));
					if (chance(0.35)) {
						setIfAir(x, G + 2, z, leaves(Blocks.OAK_LEAVES));
					}
				} else if (r < 0.249 * (0.5 + dens) && free(x - 1, z - 1, x + 2, z + 2)) {
					for (int a = 0; a <= 1; a++) {
						for (int b = 0; b <= 1; b++) {
							setIfAir(x + a, G + 1, z + b, pick(d(Blocks.MOSSY_COBBLESTONE), d(Blocks.COBBLESTONE), d(Blocks.MOSS_BLOCK)));
						}
					}
					setIfAir(x, G + 2, z, d(Blocks.MOSSY_COBBLESTONE));
				} else if (r < 0.252 * (0.5 + dens) && free(x - 1, z - 1, x + 5, z + 1)) {
					boolean alongX = this.rnd.nextBoolean();
					int len = 3 + this.rnd.nextInt(3);
					for (int k = 0; k < len; k++) {
						int lx = alongX ? x + k : x;
						int lz = alongX ? z : z + k;
						if (!isBusy(lx, lz)) {
							setIfAir(lx, G + 1, lz, log(Blocks.OAK_LOG, alongX ? Direction.Axis.X : Direction.Axis.Z));
						}
					}
				}
			}
		}
	}

	private double density(double[][] grid, int step, int x, int z) {
		double gx = x / (double) step;
		double gz = z / (double) step;
		int i = (int) gx;
		int j = (int) gz;
		double fx = gx - i;
		double fz = gz - j;
		double a = grid[i][j] * (1 - fx) + grid[i + 1][j] * fx;
		double b = grid[i][j + 1] * (1 - fx) + grid[i + 1][j + 1] * fx;
		double v = a * (1 - fz) + b * fz;
		double dens = Math.max(0.05, Math.min(1.0, (v - 0.2) * 1.3));
		if (z <= 66 && x >= 26 && x <= 124) {
			dens *= 0.3;
		} else if (z >= 80 && x >= 25 && x <= 124) {
			dens *= 0.45;
		}
		return dens * 0.55;
	}

	private boolean grassy(int x, int z) {
		BlockState s = get(x, G, z);
		return s != null && (s.isOf(Blocks.GRASS_BLOCK) || s.isOf(Blocks.PODZOL) || s.isOf(Blocks.MOSS_BLOCK)
				|| s.isOf(Blocks.COARSE_DIRT));
	}

	private void trunk(int x, int z, int h, BlockState wood) {
		set(x, G, z, d(Blocks.DIRT));
		for (int y = G + 1; y <= G + h; y++) {
			set(x, y, z, wood);
		}
	}

	private void blob(int cx, int cy, int cz, int r, BlockState leaf, double sparse) {
		for (int x = cx - r; x <= cx + r; x++) {
			for (int z = cz - r; z <= cz + r; z++) {
				int dx = Math.abs(x - cx);
				int dz = Math.abs(z - cz);
				if (dx == r && dz == r && chance(0.7)) {
					continue;
				}
				if (chance(sparse)) {
					continue;
				}
				setIfAir(x, cy, z, leaf);
			}
		}
	}

	private void oak(int x, int z) {
		int h = 4 + this.rnd.nextInt(3);
		trunk(x, z, h, log(Blocks.OAK_LOG, Direction.Axis.Y));
		BlockState leaf = leaves(Blocks.OAK_LEAVES);
		int top = G + h;
		blob(x, top - 2, z, 2, leaf, 0.05);
		blob(x, top - 1, z, 2, leaf, 0.08);
		blob(x, top, z, 1, leaf, 0.0);
		blob(x, top + 1, z, 1, leaf, 0.3);
	}

	private void birch(int x, int z) {
		int h = 5 + this.rnd.nextInt(3);
		trunk(x, z, h, log(Blocks.BIRCH_LOG, Direction.Axis.Y));
		BlockState leaf = leaves(Blocks.BIRCH_LEAVES);
		int top = G + h;
		blob(x, top - 2, z, 2, leaf, 0.1);
		blob(x, top - 1, z, 1, leaf, 0.0);
		blob(x, top, z, 1, leaf, 0.0);
		setIfAir(x, top + 1, z, leaf);
	}

	private void spruce(int x, int z) {
		int h = 6 + this.rnd.nextInt(4);
		trunk(x, z, h, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
		BlockState leaf = leaves(Blocks.SPRUCE_LEAVES);
		int top = G + h;
		int r = 1;
		for (int y = top; y >= G + 3; y--) {
			blob(x, y, z, r, leaf, 0.0);
			r = r == 1 ? 2 : 1;
			if (y >= top - 1) {
				r = 1;
			}
		}
		setIfAir(x, top + 1, z, leaf);
		setIfAir(x, top + 2, z, leaf);
	}

	private void darkOak(int x, int z) {
		int h = 4 + this.rnd.nextInt(3);
		BlockState wood = log(Blocks.DARK_OAK_LOG, Direction.Axis.Y);
		trunk(x, z, h, wood);
		trunk(x + 1, z, h, wood);
		trunk(x, z + 1, h, wood);
		trunk(x + 1, z + 1, h, wood);
		BlockState leaf = leaves(Blocks.DARK_OAK_LEAVES);
		int top = G + h;
		for (int y = top - 1; y <= top + 1; y++) {
			int r = y == top + 1 ? 2 : 3;
			for (int ax = x - r; ax <= x + 1 + r; ax++) {
				for (int az = z - r; az <= z + 1 + r; az++) {
					double dx = ax - (x + 0.5);
					double dz = az - (z + 0.5);
					if (dx * dx + dz * dz <= (r + 0.6) * (r + 0.6) && !chance(0.06)) {
						setIfAir(ax, y, az, leaf);
					}
				}
			}
		}
		mark(x - 1, z - 1, x + 2, z + 2);
	}

	private void deadTree(int x, int z) {
		int h = 4 + this.rnd.nextInt(3);
		BlockState wood = log(Blocks.OAK_LOG, Direction.Axis.Y);
		trunk(x, z, h, wood);
		int y = G + h - 1;
		if (chance(0.7)) {
			setIfAir(x + 1, y, z, log(Blocks.OAK_LOG, Direction.Axis.X));
			setIfAir(x + 2, y + 1, z, log(Blocks.OAK_LOG, Direction.Axis.X));
		}
		if (chance(0.7)) {
			setIfAir(x, y - 1, z - 1, log(Blocks.OAK_LOG, Direction.Axis.Z));
		}
	}
}
