package ru.admincontrol.util;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EventArena {
	public static final int DM_SIZE = 25;
	public static final int DM_HEIGHT = 55;
	public static final int TREASURE_SIZE = 33;
	public static final int TREASURE_HEIGHT = 32;
	public static final int SPLEEF_SIZE = 29;
	public static final int SPLEEF_HEIGHT = 20;
	public static final int HUNT_SIZE = 21;
	public static final int HUNT_HEIGHT = 18;
	public static final int MEAT_INNER = 15;
	public static final int MEAT_HEIGHT = 10;
	public static final int RING_SIZE = 35;
	public static final int RING_HEIGHT = 18;
	private static final int RING_BASE = 3;
	public static final int GEMS_SIZE = 29;
	public static final int GEMS_HEIGHT = 16;
	public static final int KNOCK_SIZE = 41;
	public static final int KNOCK_HEIGHT = 18;
	/** Острова 5×5 на радиусе 10 вокруг центра 9×9: просветы между соседями 1–3 блока, всё перепрыгивается. */
	private static final int KNOCK_RADIUS = 10;
	private static final int KNOCK_CORE = 4;
	public static final int DUEL_SIZE = 23;
	public static final int DUEL_HEIGHT = 14;
	public static final int TNT_MIN = 50;
	public static final int TNT_MAX = 80;
	public static final int TNT_PAD = 2;
	public static final int HILL_SIZE = 41;
	public static final int HILL_HEIGHT = 24;
	/** Полуширина ярусов холма снизу вверх; последний — золотая вершина 5×5. */
	private static final int[] HILL_TIERS = {11, 9, 7, 5, 2};
	public static final int PARTY_SIZE = 31;
	public static final int PARTY_HEIGHT = 22;
	private static final int PARTY_FLOOR = 8;
	public static final String[] PARTY_COLOR_NAMES = {"красный", "оранжевый", "жёлтый", "лаймовый", "голубой", "синий",
			"фиолетовый", "розовый"};
	public static final net.minecraft.util.Formatting[] PARTY_COLOR_FORMATS = {
			net.minecraft.util.Formatting.RED, net.minecraft.util.Formatting.GOLD, net.minecraft.util.Formatting.YELLOW,
			net.minecraft.util.Formatting.GREEN, net.minecraft.util.Formatting.AQUA, net.minecraft.util.Formatting.BLUE,
			net.minecraft.util.Formatting.DARK_PURPLE, net.minecraft.util.Formatting.LIGHT_PURPLE};
	private static final net.minecraft.block.Block[] PARTY_BLOCKS = {
			Blocks.RED_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.YELLOW_CONCRETE, Blocks.LIME_CONCRETE,
			Blocks.LIGHT_BLUE_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.PURPLE_CONCRETE, Blocks.PINK_CONCRETE};

	private final List<Saved> saved = new ArrayList<>();
	private int[][] partyTiles = new int[0][0];
	private int partyTile = 3;
	private ServerWorld world;
	private int kind;
	/** Ключ своей карты из /devent (ставится до build); null — номер типа. */
	public String mapKey;
	private int[] pillarX = new int[0];
	private int[] pillarZ = new int[0];
	private final Set<BlockPos> gems = new HashSet<>();
	public int ringInset;
	public BlockPos stand;
	public int minX;
	public int minY;
	public int minZ;
	public int maxX;
	public int maxY;
	public int maxZ;

	private static final class Saved {
		BlockPos pos;
		BlockState state;
		NbtCompound be;
	}

	public void build(ServerWorld world, BlockPos stand, int type) {
		this.kind = type;
		if (type == 1) {
			buildTreasure(world, stand);
		} else if (type == 2) {
			buildSpleef(world, stand);
		} else if (type == 3) {
			buildHunt(world, stand);
		} else if (type == 4) {
			buildDuel(world, stand, TNT_MIN);
		} else if (type == 5) {
			buildDeathmatch(world, stand);
			placeDuelCover();
		} else if (type == 6) {
			buildRace(world, stand, this.raceBumpers);
		} else if (type == 7) {
			buildMeat(world, stand);
		} else if (type == 8) {
			buildRing(world, stand);
		} else if (type == 9) {
			buildGems(world, stand);
		} else if (type == 10) {
			buildKnock(world, stand);
		} else if (type == 11) {
			buildFootball(world, stand);
		} else if (type == 12) {
			buildStrike(world, stand);
		} else if (type == 13) {
			buildHill(world, stand);
		} else if (type == 14) {
			buildParty(world, stand);
		} else {
			buildDeathmatch(world, stand);
		}
		applyCustom();
		illuminate();
	}

	public ServerWorld world() {
		return this.world;
	}

	public int kind() {
		return this.kind;
	}

	/**
	 * Карта, изменённая в /devent, ставится поверх стандартной, если размер объёма совпадает.
	 * Игровые точки (сокровище, самоцветы, финиш, ворота, базы) остаются на своих местах.
	 */
	private void applyCustom() {
		applySnapshot(EventMaps.get(mapKey()));
	}

	/** Ключ карты: у своих МП из /devent — их id, у стандартных — номер типа. */
	public String mapKey() {
		return this.mapKey == null || this.mapKey.isBlank() ? String.valueOf(this.kind) : this.mapKey;
	}

	public boolean fits(EventMaps.Snapshot map) {
		return map != null && map.fits(this.maxX - this.minX + 1, this.maxY - this.minY + 1, this.maxZ - this.minZ + 1);
	}

	/** Поставить снимок поверх объёма карты. false — снимка нет или он другого размера. */
	public boolean applySnapshot(EventMaps.Snapshot map) {
		if (this.world == null || !fits(map)) {
			return false;
		}
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int i = 0;
		for (int x = 0; x < map.sx; x++) {
			for (int z = 0; z < map.sz; z++) {
				for (int y = 0; y < map.sy; y++, i++) {
					BlockState next = map.state(i);
					pos.set(this.minX + x, this.minY + y, this.minZ + z);
					if (next.isAir() && this.world.getBlockState(pos).isAir()) {
						continue;
					}
					place(pos, next, 18);
					NbtCompound nbt = map.blockEntity(i);
					BlockEntity be = nbt == null ? null : this.world.getBlockEntity(pos);
					if (be != null) {
						be.readNbt(nbt);
						be.markDirty();
					}
				}
			}
		}
		if (this.kind == 9) {
			for (BlockPos gem : this.gems) {
				BlockState s = this.world.getBlockState(gem);
				if (!s.isOf(Blocks.EMERALD_BLOCK) && !s.isOf(Blocks.DIAMOND_BLOCK) && !s.isOf(Blocks.GOLD_BLOCK)
						&& !s.isOf(Blocks.LAPIS_BLOCK)) {
					place(gem, Blocks.EMERALD_BLOCK.getDefaultState());
				}
			}
		}
		this.clearVolume = true;
		return true;
	}

	/** Исходные блоки мира под картой (без повторов) — для отката после падения сервера. */
	public NbtCompound restoreData() {
		NbtCompound root = new NbtCompound();
		if (this.world == null) {
			return root;
		}
		root.putString("dim", this.world.getRegistryKey().getValue().toString());
		root.putInt("minX", this.minX);
		root.putInt("minY", this.minY);
		root.putInt("minZ", this.minZ);
		root.putInt("maxX", this.maxX);
		root.putInt("maxY", this.maxY);
		root.putInt("maxZ", this.maxZ);
		root.putBoolean("clear", this.clearVolume);
		java.util.LinkedHashMap<BlockPos, Saved> first = new java.util.LinkedHashMap<>();
		for (Saved s : this.saved) {
			first.putIfAbsent(s.pos, s);
		}
		java.util.Map<BlockState, Integer> ids = new java.util.HashMap<>();
		net.minecraft.nbt.NbtList palette = new net.minecraft.nbt.NbtList();
		net.minecraft.nbt.NbtList bes = new net.minecraft.nbt.NbtList();
		int[] xs = new int[first.size()];
		int[] ys = new int[first.size()];
		int[] zs = new int[first.size()];
		int[] st = new int[first.size()];
		int i = 0;
		for (Saved s : first.values()) {
			Integer id = ids.get(s.state);
			if (id == null) {
				id = ids.size();
				ids.put(s.state, id);
				palette.add(net.minecraft.nbt.NbtHelper.fromBlockState(s.state));
			}
			xs[i] = s.pos.getX();
			ys[i] = s.pos.getY();
			zs[i] = s.pos.getZ();
			st[i] = id;
			if (s.be != null) {
				NbtCompound e = new NbtCompound();
				e.putInt("i", i);
				e.put("nbt", s.be.copy());
				bes.add(e);
			}
			i++;
		}
		root.put("palette", palette);
		root.putIntArray("x", xs);
		root.putIntArray("y", ys);
		root.putIntArray("z", zs);
		root.putIntArray("s", st);
		root.put("entities", bes);
		return root;
	}

	/** Откат по данным restoreData: объём очищается, исходные блоки ставятся обратно. */
	public static boolean restoreFromData(net.minecraft.server.MinecraftServer server, NbtCompound root) {
		ServerWorld world = server == null ? null : SpawnService.resolveWorld(server, root.getString("dim"));
		if (world == null || !world.getRegistryKey().getValue().toString().equals(root.getString("dim"))) {
			return false;
		}
		EventArena a = new EventArena();
		a.world = world;
		a.minX = root.getInt("minX");
		a.minY = root.getInt("minY");
		a.minZ = root.getInt("minZ");
		a.maxX = root.getInt("maxX");
		a.maxY = root.getInt("maxY");
		a.maxZ = root.getInt("maxZ");
		a.clearVolume = root.getBoolean("clear");
		net.minecraft.nbt.NbtList list = root.getList("palette", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
		BlockState[] palette = new BlockState[list.size()];
		for (int i = 0; i < palette.length; i++) {
			palette[i] = net.minecraft.nbt.NbtHelper.toBlockState(
					net.minecraft.registry.Registries.BLOCK.getReadOnlyWrapper(), list.getCompound(i));
		}
		java.util.Map<Integer, NbtCompound> bes = new java.util.HashMap<>();
		net.minecraft.nbt.NbtList beList = root.getList("entities", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < beList.size(); i++) {
			bes.put(beList.getCompound(i).getInt("i"), beList.getCompound(i).getCompound("nbt"));
		}
		int[] xs = root.getIntArray("x");
		int[] ys = root.getIntArray("y");
		int[] zs = root.getIntArray("z");
		int[] st = root.getIntArray("s");
		int n = Math.min(Math.min(xs.length, ys.length), Math.min(zs.length, st.length));
		for (int i = 0; i < n; i++) {
			Saved s = new Saved();
			s.pos = new BlockPos(xs[i], ys[i], zs[i]);
			s.state = st[i] >= 0 && st[i] < palette.length ? palette[st[i]] : Blocks.AIR.getDefaultState();
			s.be = bes.get(i);
			a.saved.add(s);
		}
		a.sweepDebris();
		a.remove();
		return true;
	}

	/** Правка карты: непустые клетки объёма, которых не коснулась постройка, тоже запоминаются, объём при снятии очищается целиком. */
	public void coverVolume() {
		if (this.world == null) {
			return;
		}
		Set<BlockPos> touched = new HashSet<>();
		for (Saved s : this.saved) {
			touched.add(s.pos);
		}
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int x = this.minX; x <= this.maxX; x++) {
			for (int z = this.minZ; z <= this.maxZ; z++) {
				for (int y = this.minY; y <= this.maxY; y++) {
					pos.set(x, y, z);
					if (touched.contains(pos)) {
						continue;
					}
					BlockState state = this.world.getBlockState(pos);
					if (state.isAir()) {
						continue;
					}
					Saved s = new Saved();
					s.pos = pos.toImmutable();
					s.state = state;
					BlockEntity be = this.world.getBlockEntity(pos);
					if (be != null) {
						s.be = be.createNbt();
					}
					this.saved.add(s);
				}
			}
		}
		this.clearVolume = true;
	}

	public static final int SPAWN_BAN_PAD = 24;
	private static final int LIGHT_STEP = 7;

	/** Невидимые источники света по всему объёму карты, чтобы ночью на ней не появлялись враждебные мобы. */
	private void illuminate() {
		if (this.world == null) {
			return;
		}
		BlockState light = Blocks.LIGHT.getDefaultState();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int y = this.minY + 1; y <= this.maxY; y += LIGHT_STEP) {
			for (int x = this.minX + 1; x <= this.maxX; x += LIGHT_STEP) {
				for (int z = this.minZ + 1; z <= this.maxZ; z += LIGHT_STEP) {
					pos.set(x, y, z);
					if (this.world.getBlockState(pos).isAir()) {
						place(pos, light);
					}
				}
			}
		}
	}

	/** Карта и техническая зона под ней (до дна мира): здесь во время мероприятия мобы не появляются. */
	public boolean inSpawnBan(ServerWorld world, BlockPos pos) {
		return this.world != null && world == this.world && pos != null
				&& pos.getX() >= this.minX - SPAWN_BAN_PAD && pos.getX() <= this.maxX + SPAWN_BAN_PAD
				&& pos.getZ() >= this.minZ - SPAWN_BAN_PAD && pos.getZ() <= this.maxZ + SPAWN_BAN_PAD
				&& pos.getY() <= this.maxY + SPAWN_BAN_PAD;
	}

	public static final int ADMIN_LIFT = 75;

	public static BlockPos adminStand(ServerWorld world, BlockPos adminPos) {
		int top = world.getTopY() - 2;
		int bottom = world.getBottomY() + 8;
		int y = Math.max(bottom, Math.min(adminPos.getY() + ADMIN_LIFT, top - 80));
		return new BlockPos(adminPos.getX(), y, adminPos.getZ());
	}
	public static final int RACE_SIZE = 128;
	public static final int RACE_HEIGHT = 6;
	public static final int RACE_AIR = 50;
	public boolean raceBumpers = true;
	public int finishMinX;
	public int finishMaxX;
	public int finishMinZ;
	public int finishMaxZ;
	public int checkMinX;
	public int checkMaxX;
	public int checkMinZ;
	public int checkMaxZ;
	public double raceStartX;
	public double raceStartZ;
	public float raceStartYaw = -90f;
	private boolean[][] raceSurface;

	private void buildHunt(ServerWorld world, BlockPos stand) {
		begin(world, stand, HUNT_SIZE, HUNT_HEIGHT);
		BlockState floor = Blocks.COBBLESTONE.getDefaultState();
		BlockState wall = Blocks.MOSSY_COBBLESTONE.getDefaultState();
		BlockState lamp = Blocks.SEA_LANTERN.getDefaultState();
		BlockState roof = Blocks.DEEPSLATE_TILES.getDefaultState();
		BlockState hang = Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true);
		BlockState air = Blocks.AIR.getDefaultState();
		for (int x = this.minX; x <= this.maxX; x++) {
			for (int z = this.minZ; z <= this.maxZ; z++) {
				for (int yy = this.minY; yy <= this.maxY; yy++) {
					boolean edge = x == this.minX || x == this.maxX || z == this.minZ || z == this.maxZ;
					boolean light = (x - this.minX) % 4 == 0 && (z - this.minZ) % 4 == 0;
					BlockState next = air;
					if (yy == this.minY) {
						next = light ? lamp : floor;
					} else if (yy == this.maxY) {
						next = roof;
					} else if (yy == this.maxY - 1 && light && !edge) {
						next = hang;
					} else if (edge) {
						next = (yy - this.minY) % 3 == 0 ? lamp : wall;
					}
					place(new BlockPos(x, yy, z), next);
				}
			}
		}
	}

	private void buildMeat(ServerWorld world, BlockPos stand) {
		begin(world, stand, MEAT_INNER + 2, MEAT_HEIGHT + 2);
		BlockState floor = Blocks.NETHER_BRICKS.getDefaultState();
		BlockState wall = Blocks.RED_NETHER_BRICKS.getDefaultState();
		BlockState roof = Blocks.BLACKSTONE.getDefaultState();
		BlockState lamp = Blocks.SEA_LANTERN.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();
		for (int x = this.minX; x <= this.maxX; x++) {
			for (int z = this.minZ; z <= this.maxZ; z++) {
				for (int yy = this.minY; yy <= this.maxY; yy++) {
					boolean edge = x == this.minX || x == this.maxX || z == this.minZ || z == this.maxZ;
					boolean light = (x - this.minX) % 4 == 0 && (z - this.minZ) % 4 == 0;
					BlockState next = air;
					if (yy == this.minY) {
						next = light ? lamp : floor;
					} else if (yy == this.maxY) {
						next = roof;
					} else if (edge) {
						next = (yy - this.minY) % 3 == 0 ? lamp : wall;
					}
					place(new BlockPos(x, yy, z), next);
				}
			}
		}
	}

	private void buildRing(ServerWorld world, BlockPos stand) {
		begin(world, stand, RING_SIZE, RING_HEIGHT);
		int lift = world.getBottomY() + RING_BASE - this.minY;
		if (lift > 0) {
			this.minY += lift;
			this.maxY += lift;
		}
		this.ringInset = 0;
		BlockState bedrock = Blocks.BEDROCK.getDefaultState();
		BlockState obsidian = Blocks.OBSIDIAN.getDefaultState();
		for (int x = this.minX; x <= this.maxX; x++) {
			for (int z = this.minZ; z <= this.maxZ; z++) {
				place(new BlockPos(x, this.minY - 2, z), bedrock);
				place(new BlockPos(x, this.minY - 1, z), obsidian);
			}
		}
		BlockState floor = Blocks.BLACKSTONE.getDefaultState();
		BlockState rim = Blocks.MAGMA_BLOCK.getDefaultState();
		BlockState wall = Blocks.RED_NETHER_BRICKS.getDefaultState();
		BlockState roof = Blocks.BARRIER.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();
		BlockState lamp = Blocks.SHROOMLIGHT.getDefaultState();
		for (int x = this.minX; x <= this.maxX; x++) {
			for (int z = this.minZ; z <= this.maxZ; z++) {
				for (int yy = this.minY; yy <= this.maxY; yy++) {
					boolean edge = x == this.minX || x == this.maxX || z == this.minZ || z == this.maxZ;
					BlockState next = air;
					if (yy == this.minY) {
						next = edge || (x + z) % 5 == 0 ? rim : floor;
					} else if (yy == this.maxY) {
						next = roof;
					} else if (edge) {
						next = (yy - this.minY) % 4 == 0 ? lamp : wall;
					}
					place(new BlockPos(x, yy, z), next);
				}
			}
		}
	}

	/**
	 * Очередное кольцо: пол над ним убирается, лава лежит на блок ниже пола в обсидиановом желобе,
	 * под ним бедрок — растечься по арене или капнуть вниз ей некуда, блоков над полом не ставится.
	 */
	public void shrinkRing() {
		if (this.world == null || this.kind != 8) {
			return;
		}
		this.ringInset++;
		int inset = this.ringInset;
		int inner = 4;
		if (this.minX + inset >= this.maxX - inset - inner) {
			return;
		}
		BlockState air = Blocks.AIR.getDefaultState();
		for (int x = this.minX + 1; x <= this.maxX - 1; x++) {
			for (int z = this.minZ + 1; z <= this.maxZ - 1; z++) {
				int dx = Math.min(x - this.minX, this.maxX - x);
				int dz = Math.min(z - this.minZ, this.maxZ - z);
				int d = Math.min(dx, dz);
				if (d == inset) {
					if (!this.world.getBlockState(new BlockPos(x, this.minY - 2, z)).isOf(Blocks.BEDROCK)) {
						continue;
					}
					placeLava(new BlockPos(x, this.minY - 1, z));
					place(new BlockPos(x, this.minY, z), air, 18);
				}
			}
		}
	}

	private void buildGems(ServerWorld world, BlockPos stand) {
		begin(world, stand, GEMS_SIZE, GEMS_HEIGHT);
		this.gems.clear();
		BlockState floor = Blocks.PRISMARINE.getDefaultState();
		BlockState cut = Blocks.DARK_PRISMARINE.getDefaultState();
		BlockState wall = Blocks.PRISMARINE_BRICKS.getDefaultState();
		BlockState roof = Blocks.BARRIER.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();
		BlockState lamp = Blocks.SEA_LANTERN.getDefaultState();
		for (int x = this.minX; x <= this.maxX; x++) {
			for (int z = this.minZ; z <= this.maxZ; z++) {
				for (int yy = this.minY; yy <= this.maxY; yy++) {
					boolean edge = x == this.minX || x == this.maxX || z == this.minZ || z == this.maxZ;
					BlockState next = air;
					if (yy == this.minY) {
						next = (x + z) % 2 == 0 ? floor : cut;
					} else if (yy == this.maxY) {
						next = roof;
					} else if (edge) {
						next = (yy - this.minY) % 3 == 0 ? lamp : wall;
					}
					place(new BlockPos(x, yy, z), next);
				}
			}
		}
		BlockState[] kinds = {
				Blocks.EMERALD_BLOCK.getDefaultState(),
				Blocks.DIAMOND_BLOCK.getDefaultState(),
				Blocks.GOLD_BLOCK.getDefaultState(),
				Blocks.LAPIS_BLOCK.getDefaultState()
		};
		int n = 0;
		for (int x = this.minX + 3; x <= this.maxX - 3 && n < 16; x += 3) {
			for (int z = this.minZ + 3; z <= this.maxZ - 3 && n < 16; z += 4) {
				BlockPos p = new BlockPos(x, this.minY + 1, z);
				place(p, kinds[n % kinds.length]);
				this.gems.add(p.toImmutable());
				n++;
			}
		}
	}

	public boolean isGem(BlockPos pos) {
		return pos != null && this.gems.contains(pos.toImmutable());
	}

	public void collectGem(BlockPos pos) {
		if (pos != null) {
			this.gems.remove(pos.toImmutable());
		}
	}

	private void buildKnock(ServerWorld world, BlockPos stand) {
		begin(world, stand, KNOCK_SIZE, KNOCK_HEIGHT);
		BlockState air = Blocks.AIR.getDefaultState();
		BlockState barrier = Blocks.BARRIER.getDefaultState();
		BlockState pad = Blocks.POLISHED_BLACKSTONE.getDefaultState();
		BlockState rim = Blocks.GOLD_BLOCK.getDefaultState();
		for (int x = this.minX; x <= this.maxX; x++) {
			for (int z = this.minZ; z <= this.maxZ; z++) {
				for (int yy = this.minY; yy <= this.maxY; yy++) {
					boolean edge = x == this.minX || x == this.maxX || z == this.minZ || z == this.maxZ;
					BlockState next = air;
					if (yy == this.minY) {
						next = Blocks.STRUCTURE_VOID.getDefaultState();
					} else if (yy == this.maxY && edge) {
						next = barrier;
					} else if (edge) {
						next = barrier;
					}
					place(new BlockPos(x, yy, z), next);
				}
			}
		}
		int pads = 8;
		this.pillarX = new int[pads];
		this.pillarZ = new int[pads];
		int cx = (this.minX + this.maxX) / 2;
		int cz = (this.minZ + this.maxZ) / 2;
		int py = this.minY + 4;
		BlockState core = Blocks.POLISHED_DEEPSLATE.getDefaultState();
		BlockState coreRim = Blocks.RAW_GOLD_BLOCK.getDefaultState();
		for (int dx = -KNOCK_CORE; dx <= KNOCK_CORE; dx++) {
			for (int dz = -KNOCK_CORE; dz <= KNOCK_CORE; dz++) {
				boolean edge = Math.abs(dx) == KNOCK_CORE || Math.abs(dz) == KNOCK_CORE;
				place(new BlockPos(cx + dx, py, cz + dz), edge ? coreRim : core);
			}
		}
		for (int i = 0; i < pads; i++) {
			double ang = i * (Math.PI * 2 / pads);
			int px = cx + (int) Math.round(Math.cos(ang) * KNOCK_RADIUS);
			int pz = cz + (int) Math.round(Math.sin(ang) * KNOCK_RADIUS);
			this.pillarX[i] = px;
			this.pillarZ[i] = pz;
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					boolean gold = Math.abs(dx) == 2 || Math.abs(dz) == 2;
					place(new BlockPos(px + dx, py, pz + dz), gold ? rim : pad);
				}
			}
		}
	}

	private void buildHill(ServerWorld world, BlockPos stand) {
		begin(world, stand, HILL_SIZE, HILL_HEIGHT);
		BlockState grass = Blocks.GRASS_BLOCK.getDefaultState();
		BlockState moss = Blocks.MOSS_BLOCK.getDefaultState();
		BlockState path = Blocks.POLISHED_ANDESITE.getDefaultState();
		BlockState rim = Blocks.MOSSY_STONE_BRICKS.getDefaultState();
		BlockState cap = Blocks.CHISELED_STONE_BRICKS.getDefaultState();
		BlockState pillar = Blocks.POLISHED_ANDESITE.getDefaultState();
		BlockState lamp = Blocks.SEA_LANTERN.getDefaultState();
		BlockState bricks = Blocks.STONE_BRICKS.getDefaultState();
		BlockState mossy = Blocks.MOSSY_STONE_BRICKS.getDefaultState();
		BlockState edgeStone = Blocks.POLISHED_DEEPSLATE.getDefaultState();
		BlockState gold = Blocks.GOLD_BLOCK.getDefaultState();
		BlockState rawGold = Blocks.RAW_GOLD_BLOCK.getDefaultState();
		BlockState barrier = Blocks.BARRIER.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();
		int cx = (this.minX + this.maxX) / 2;
		int cz = (this.minZ + this.maxZ) / 2;
		int rimH = 4;
		int tower = HILL_SIZE / 2 - 3;
		for (int x = this.minX; x <= this.maxX; x++) {
			for (int z = this.minZ; z <= this.maxZ; z++) {
				for (int yy = this.minY; yy <= this.maxY; yy++) {
					boolean edge = x == this.minX || x == this.maxX || z == this.minZ || z == this.maxZ;
					int dx = Math.abs(x - cx);
					int dz = Math.abs(z - cz);
					int h = yy - this.minY;
					BlockState next = air;
					if (h == 0) {
						next = edge ? rim : (dx <= 1 || dz <= 1) ? path : Math.floorMod(x * 3 + z * 5, 7) == 0 ? moss : grass;
					} else if (yy == this.maxY) {
						next = barrier;
					} else if (edge) {
						next = h < rimH ? rim : h == rimH ? cap : barrier;
					} else if (dx == tower && dz == tower && h <= 6) {
						next = h == 6 ? lamp : pillar;
					} else if (h - 1 < HILL_TIERS.length) {
						int tier = h - 1;
						int r = HILL_TIERS[tier];
						if (dx <= r && dz <= r) {
							boolean ring = dx == r || dz == r;
							if (tier == HILL_TIERS.length - 1) {
								next = ring ? rawGold : gold;
							} else {
								next = ring ? edgeStone : tier % 2 == 0 ? mossy : bricks;
							}
						}
					}
					place(new BlockPos(x, yy, z), next);
				}
			}
		}
		BlockState lantern = Blocks.LANTERN.getDefaultState();
		for (int tier = 0; tier < HILL_TIERS.length - 1; tier++) {
			int r = HILL_TIERS[tier];
			int y = this.minY + 2 + tier;
			place(new BlockPos(cx - r, y, cz - r), lantern);
			place(new BlockPos(cx + r, y, cz - r), lantern);
			place(new BlockPos(cx - r, y, cz + r), lantern);
			place(new BlockPos(cx + r, y, cz + r), lantern);
		}
	}

	/** Игрок стоит (или подпрыгивает) на золотой вершине холма. */
	public boolean onHillTop(ServerPlayerEntity player) {
		if (this.kind != 13 || player == null) {
			return false;
		}
		int r = HILL_TIERS[HILL_TIERS.length - 1];
		int cx = (this.minX + this.maxX) / 2;
		int cz = (this.minZ + this.maxZ) / 2;
		int bx = net.minecraft.util.math.MathHelper.floor(player.getX());
		int bz = net.minecraft.util.math.MathHelper.floor(player.getZ());
		double top = this.minY + HILL_TIERS.length + 1;
		return Math.abs(bx - cx) <= r && Math.abs(bz - cz) <= r && player.getY() >= top - 0.1 && player.getY() < top + 2.5;
	}

	private void buildParty(ServerWorld world, BlockPos stand) {
		begin(world, stand, PARTY_SIZE, PARTY_HEIGHT);
		BlockState base = Blocks.SMOOTH_STONE.getDefaultState();
		BlockState water = Blocks.WATER.getDefaultState();
		BlockState wall = Blocks.WHITE_CONCRETE.getDefaultState();
		BlockState frame = Blocks.BLACK_CONCRETE.getDefaultState();
		BlockState lamp = Blocks.SEA_LANTERN.getDefaultState();
		BlockState barrier = Blocks.BARRIER.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();
		int floor = this.minY + PARTY_FLOOR;
		for (int x = this.minX; x <= this.maxX; x++) {
			for (int z = this.minZ; z <= this.maxZ; z++) {
				for (int yy = this.minY; yy <= this.maxY; yy++) {
					boolean edge = x == this.minX || x == this.maxX || z == this.minZ || z == this.maxZ;
					int h = yy - this.minY;
					BlockState next = air;
					if (h == 0) {
						next = base;
					} else if (yy == this.maxY) {
						next = barrier;
					} else if (edge) {
						if (h > PARTY_FLOOR + 4) {
							next = barrier;
						} else {
							next = yy == floor ? frame : h % 3 == 0 ? lamp : wall;
						}
					} else if (h == 1) {
						next = water;
					} else if (yy == floor) {
						continue;
					}
					place(new BlockPos(x, yy, z), next);
				}
			}
		}
		paintParty(new java.util.Random(), 3, true);
	}

	/** Новый узор цветного пола из плиток tile×tile; initial — первая укладка, исходные блоки мира запоминаются. */
	public void paintParty(java.util.Random rng, int tile, boolean initial) {
		if (this.world == null || this.kind != 14) {
			return;
		}
		this.partyTile = Math.max(1, tile);
		int inner = this.maxX - this.minX - 1;
		int n = (inner + this.partyTile - 1) / this.partyTile;
		this.partyTiles = new int[n][n];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				this.partyTiles[i][j] = rng.nextInt(PARTY_BLOCKS.length);
			}
		}
		int y = this.minY + PARTY_FLOOR;
		for (int x = this.minX + 1; x <= this.maxX - 1; x++) {
			for (int z = this.minZ + 1; z <= this.maxZ - 1; z++) {
				BlockPos pos = new BlockPos(x, y, z);
				BlockState next = PARTY_BLOCKS[partyColorAt(x, z)].getDefaultState();
				if (initial) {
					place(pos, next);
				} else {
					this.world.setBlockState(pos, next, 18);
				}
			}
		}
	}

	private int partyColorAt(int x, int z) {
		int last = this.partyTiles.length - 1;
		int i = Math.max(0, Math.min(last, (x - this.minX - 1) / this.partyTile));
		int j = Math.max(0, Math.min(last, (z - this.minZ - 1) / this.partyTile));
		return this.partyTiles[i][j];
	}

	/** Цвет случайной плитки — выбранный цвет точно есть на полу. */
	public int partyPickColor(java.util.Random rng) {
		int n = this.partyTiles.length;
		return n == 0 ? 0 : this.partyTiles[rng.nextInt(n)][rng.nextInt(n)];
	}

	/** Убрать с пола все плитки, кроме цвета color. */
	public void dropParty(int color) {
		if (this.world == null || this.kind != 14 || this.partyTiles.length == 0) {
			return;
		}
		BlockState air = Blocks.AIR.getDefaultState();
		int y = this.minY + PARTY_FLOOR;
		for (int x = this.minX + 1; x <= this.maxX - 1; x++) {
			for (int z = this.minZ + 1; z <= this.maxZ - 1; z++) {
				if (partyColorAt(x, z) != color) {
					this.world.setBlockState(new BlockPos(x, y, z), air, 18);
				}
			}
		}
	}

	public boolean partyFallen(ServerPlayerEntity player) {
		return this.kind == 14 && player != null && player.getY() < this.minY + PARTY_FLOOR - 0.5;
	}

	public static int tntSize(int players) {
		int n = Math.max(1, players);
		if (n <= 2) {
			return TNT_MIN;
		}
		return Math.min(TNT_MAX, TNT_MIN + (n - 2) * 5);
	}

	public void rebuildDuel(ServerWorld world, int players) {
		BlockPos origin = this.stand != null ? this.stand : new BlockPos(
				(this.minX + this.maxX) / 2, this.minY, (this.minZ + this.maxZ) / 2);
		remove();
		buildDuel(world, origin, tntSize(players));
		applyCustom();
		illuminate();
		layoutTntPillars(players);
	}

	private void buildDuel(ServerWorld world, BlockPos stand, int size) {
		this.kind = 4;
		int top = world.getTopY() - 2;
		int y = Math.min(top - DUEL_HEIGHT, Math.max(world.getBottomY() + 8, stand.getY()));
		this.world = world;
		this.stand = stand.toImmutable();
		int span = Math.max(TNT_MIN, Math.min(TNT_MAX, size));
		this.minX = stand.getX() - span / 2;
		this.minZ = stand.getZ() - span / 2;
		this.minY = y;
		this.maxX = this.minX + span - 1;
		this.maxZ = this.minZ + span - 1;
		this.maxY = this.minY + DUEL_HEIGHT - 1;
		BlockState black = Blocks.BLACKSTONE.getDefaultState();
		BlockState brick = Blocks.RED_NETHER_BRICKS.getDefaultState();
		BlockState magma = Blocks.MAGMA_BLOCK.getDefaultState();
		BlockState glass = Blocks.RED_STAINED_GLASS.getDefaultState();
		BlockState glow = Blocks.SHROOMLIGHT.getDefaultState();
		BlockState cry = Blocks.CRYING_OBSIDIAN.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();
		int cx = (this.minX + this.maxX) / 2;
		int cz = (this.minZ + this.maxZ) / 2;
		for (int x = this.minX; x <= this.maxX; x++) {
			for (int z = this.minZ; z <= this.maxZ; z++) {
				for (int yy = this.minY; yy <= this.maxY; yy++) {
					boolean edge = x == this.minX || x == this.maxX || z == this.minZ || z == this.maxZ;
					int fromEdge = Math.min(Math.min(x - this.minX, this.maxX - x),
							Math.min(z - this.minZ, this.maxZ - z));
					boolean ring = fromEdge <= 1;
					boolean core = Math.abs(x - cx) <= 1 && Math.abs(z - cz) <= 1;
					int h = yy - this.minY;
					BlockState next = air;
					if (yy == this.minY) {
						if (ring) {
							next = brick;
						} else if (core) {
							next = cry;
						}
					} else if (yy == this.minY + 1 && ring) {
						next = fromEdge == 0 ? black : magma;
					} else if (edge && h <= 4) {
						next = h == 4 && (x + z) % 2 == 0 ? glow : black;
					} else if (edge && h < DUEL_HEIGHT - 1) {
						next = glass;
					} else if (edge) {
						next = black;
					}
					place(new BlockPos(x, yy, z), next);
				}
			}
		}
	}

	public void layoutTntPillars(int players) {
		if (this.world == null || this.kind != 4) {
			return;
		}
		int n = Math.max(1, players);
		this.pillarX = new int[n];
		this.pillarZ = new int[n];
		int cx = (this.minX + this.maxX) / 2;
		int cz = (this.minZ + this.maxZ) / 2;
		int inner = Math.max(10, (this.maxX - this.minX) / 2 - 8);
		int r = n <= 1 ? 0 : inner;
		BlockState tnt = Blocks.TNT.getDefaultState();
		BlockState cry = Blocks.CRYING_OBSIDIAN.getDefaultState();
		BlockState plat = Blocks.OBSIDIAN.getDefaultState();
		int platY = this.minY + 2;
		int padY = this.minY + 3;
		int margin = TNT_PAD + 3;
		for (int i = 0; i < n; i++) {
			int px = cx;
			int pz = cz;
			if (n > 1) {
				double ang = i * (Math.PI * 2 / n);
				px = cx + (int) Math.round(Math.sin(ang) * r);
				pz = cz + (int) Math.round(Math.cos(ang) * r);
				px = Math.max(this.minX + margin, Math.min(this.maxX - margin, px));
				pz = Math.max(this.minZ + margin, Math.min(this.maxZ - margin, pz));
			}
			this.pillarX[i] = px;
			this.pillarZ[i] = pz;
			place(new BlockPos(px, this.minY + 1, pz), cry);
			for (int dx = -TNT_PAD; dx <= TNT_PAD; dx++) {
				for (int dz = -TNT_PAD; dz <= TNT_PAD; dz++) {
					place(new BlockPos(px + dx, platY, pz + dz), plat);
					place(new BlockPos(px + dx, padY, pz + dz), tnt);
				}
			}
		}
	}

	public void sweepDebris() {
		if (this.world == null) {
			return;
		}
		Box box = new Box(this.minX - 4, this.minY - 24, this.minZ - 4,
				this.maxX + 5, this.maxY + 16, this.maxZ + 5);
		for (Entity entity : new ArrayList<>(this.world.getOtherEntities(null, box,
				e -> !(e instanceof PlayerEntity)))) {
			entity.discard();
		}
	}

	private void placeDuelCover() {
		int cx = (this.minX + this.maxX) / 2;
		int cz = (this.minZ + this.maxZ) / 2;
		BlockState brick = Blocks.STONE_BRICKS.getDefaultState();
		BlockState deep = Blocks.POLISHED_DEEPSLATE.getDefaultState();
		coverL(cx - 6, cz - 6, 1, 1, brick);
		coverL(cx + 6, cz - 6, -1, 1, brick);
		coverL(cx - 6, cz + 6, 1, -1, deep);
		coverL(cx + 6, cz + 6, -1, -1, deep);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				coverColumn(cx + dx, cz + dz, dx == 0 || dz == 0 ? 2 : 3, deep);
			}
		}
		for (int i = -4; i <= 4; i++) {
			if (Math.abs(i) <= 1) {
				continue;
			}
			coverColumn(cx + i, cz - 4, 2, brick);
			coverColumn(cx + i, cz + 4, 2, brick);
			coverColumn(cx - 4, cz + i, 2, brick);
			coverColumn(cx + 4, cz + i, 2, brick);
		}
		coverColumn(cx - 2, cz, 3, brick);
		coverColumn(cx + 2, cz, 3, brick);
		coverColumn(cx, cz - 2, 3, deep);
		coverColumn(cx, cz + 2, 3, deep);
	}

	private void coverL(int x, int z, int sx, int sz, BlockState state) {
		for (int i = 0; i < 4; i++) {
			coverColumn(x + i * sx, z, 3, state);
			coverColumn(x, z + i * sz, 2, state);
		}
	}

	private void coverColumn(int x, int z, int height, BlockState state) {
		if (x <= this.minX || x >= this.maxX || z <= this.minZ || z >= this.maxZ) {
			return;
		}
		for (int i = 1; i <= height; i++) {
			place(new BlockPos(x, this.minY + i, z), state);
		}
	}

	public static final int FOOTBALL_LEN = 80;
	public static final int FOOTBALL_WID = 60;
	public static final int FOOTBALL_HEIGHT = 12;

	private void buildFootball(ServerWorld world, BlockPos stand) {
		beginRect(world, stand, FOOTBALL_LEN, FOOTBALL_WID, FOOTBALL_HEIGHT);
		BlockState grass = Blocks.GREEN_CONCRETE.getDefaultState();
		BlockState line = Blocks.WHITE_CONCRETE.getDefaultState();
		BlockState goal = Blocks.BLACK_CONCRETE.getDefaultState();
		BlockState net = Blocks.IRON_BARS.getDefaultState();
		BlockState fence = Blocks.DARK_OAK_FENCE.getDefaultState();
		BlockState wall = Blocks.STONE_BRICKS.getDefaultState();
		BlockState seat = Blocks.SPRUCE_STAIRS.getDefaultState();
		BlockState rail = Blocks.STONE_BRICK_WALL.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();
		int midZ = (this.minZ + this.maxZ) / 2;
		int midX = (this.minX + this.maxX) / 2;
		int goalHalf = 5;
		for (int x = this.minX; x <= this.maxX; x++) {
			for (int z = this.minZ; z <= this.maxZ; z++) {
				for (int yy = this.minY; yy <= this.maxY; yy++) {
					boolean edge = x == this.minX || x == this.maxX || z == this.minZ || z == this.maxZ;
					boolean nearEdge = x <= this.minX + 1 || x >= this.maxX - 1
							|| z <= this.minZ + 1 || z >= this.maxZ - 1;
					BlockState next = air;
					if (yy == this.minY) {
						boolean stripe = x == midX || z == midZ
								|| x == this.minX + 2 || x == this.maxX - 2
								|| z == this.minZ + 2 || z == this.maxZ - 2
								|| (Math.abs(x - midX) <= 4 && Math.abs(z - midZ) <= 4
								&& (Math.abs(x - midX) == 4 || Math.abs(z - midZ) == 4));
						next = stripe ? line : grass;
					} else if (yy == this.minY + 1 && edge) {
						next = fence;
					} else if (yy == this.minY + 2 && edge) {
						next = rail;
					} else if (nearEdge && yy >= this.minY + 1 && yy <= this.minY + 3
							&& !edge && (x == this.minX + 1 || x == this.maxX - 1
							|| z == this.minZ + 1 || z == this.maxZ - 1)) {
						// небольшие трибуны за забором
						int step = yy - this.minY;
						if (step == 1) {
							next = seat;
						} else if (step == 2) {
							next = wall;
						} else {
							next = rail;
						}
					} else if (yy == this.maxY && nearEdge) {
						next = rail;
					}
					place(new BlockPos(x, yy, z), next);
				}
			}
		}
		for (int side = 0; side < 2; side++) {
			int gx = side == 0 ? this.minX + 2 : this.maxX - 2;
			for (int z = midZ - goalHalf; z <= midZ + goalHalf; z++) {
				place(new BlockPos(gx, this.minY, z), goal);
				for (int yy = this.minY + 1; yy <= this.minY + 4; yy++) {
					if (z == midZ - goalHalf || z == midZ + goalHalf || yy == this.minY + 4) {
						place(new BlockPos(gx, yy, z), net);
					} else {
						place(new BlockPos(gx, yy, z), air);
					}
				}
			}
		}
	}

	public double footballSpawnX(int team) {
		return team == 0 ? this.minX + 12.5 : this.maxX - 11.5;
	}

	public double footballSpawnZ(int index, int teamSize) {
		int n = Math.max(1, teamSize);
		int i = Math.floorMod(index, n);
		double span = Math.max(8, (this.maxZ - this.minZ) - 16);
		double step = span / n;
		return this.minZ + 8 + step * i + step / 2.0;
	}

	public int footballGoal(double x, double y, double z) {
		if (y < this.minY || y > this.minY + 5) {
			return -1;
		}
		if (z < this.minZ + 2 || z > this.maxZ - 2) {
			return -1;
		}
		int midZ = (this.minZ + this.maxZ) / 2;
		if (z < midZ - 5.6 || z > midZ + 6.6) {
			return -1;
		}
		if (x <= this.minX + 3.2) {
			return 0;
		}
		if (x >= this.maxX - 2.2) {
			return 1;
		}
		return -1;
	}

	public static final int STRIKE_SIZE = StrikeMap.SIZE;
	public static final int STRIKE_HEIGHT = StrikeMap.HEIGHT;
	/** Объём карты при снятии сначала очищается целиком: пустые клетки не сохраняются при постройке. */
	private boolean clearVolume;

	private void buildStrike(ServerWorld world, BlockPos stand) {
		beginRect(world, stand, STRIKE_SIZE, STRIKE_SIZE, STRIKE_HEIGHT);
		this.clearVolume = true;
		EventMaps.Snapshot custom = EventMaps.get(mapKey());
		if (custom != null && custom.fits(STRIKE_SIZE, STRIKE_HEIGHT, STRIKE_SIZE)) {
			return;
		}
		StrikeMap map = StrikeMap.generate(new java.util.Random());
		BlockState air = Blocks.AIR.getDefaultState();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int lx = 0; lx < STRIKE_SIZE; lx++) {
			for (int lz = 0; lz < STRIKE_SIZE; lz++) {
				for (int ly = 0; ly < STRIKE_HEIGHT; ly++) {
					BlockState next = map.get(lx, ly, lz);
					if (next == null) {
						next = air;
					}
					pos.set(this.minX + lx, this.minY + ly, this.minZ + lz);
					if (next.isAir() && this.world.getBlockState(pos).isAir()) {
						continue;
					}
					place(pos, next);
				}
			}
		}
	}

	public double strikeSpawnX(int team, int slot) {
		return this.minX + StrikeMap.spawnX(team, slot) + 0.5;
	}

	public double strikeSpawnZ(int slot) {
		return this.minZ + StrikeMap.spawnZ(slot) + 0.5;
	}

	private void clearStrikeVolume() {
		BlockPos.Mutable pos = new BlockPos.Mutable();
		BlockState air = Blocks.AIR.getDefaultState();
		for (int x = this.minX; x <= this.maxX; x++) {
			for (int z = this.minZ; z <= this.maxZ; z++) {
				for (int y = this.minY; y <= this.maxY; y++) {
					pos.set(x, y, z);
					if (this.world.getBlockState(pos).isAir()) {
						continue;
					}
					if (this.world.getBlockEntity(pos) instanceof net.minecraft.inventory.Inventory inv) {
						inv.clear();
					}
					this.world.removeBlockEntity(pos);
					this.world.setBlockState(pos, air, 18);
				}
			}
		}
	}

	private void begin(ServerWorld world, BlockPos stand, int size, int height) {
		beginRect(world, stand, size, size, height);
	}

	private void beginRect(ServerWorld world, BlockPos stand, int sizeX, int sizeZ, int height) {
		this.world = world;
		this.stand = stand == null ? null : stand.toImmutable();
		int top = world.getTopY() - 1;
		int bottom = world.getBottomY() + 1;
		int y = Math.max(bottom, Math.min(stand.getY(), top - height));
		this.minX = stand.getX() - sizeX / 2;
		this.minZ = stand.getZ() - sizeZ / 2;
		this.minY = y;
		this.maxX = this.minX + sizeX - 1;
		this.maxZ = this.minZ + sizeZ - 1;
		this.maxY = this.minY + height - 1;
	}

	private void buildDeathmatch(ServerWorld world, BlockPos stand) {
		begin(world, stand, DM_SIZE, DM_HEIGHT);
		BlockState floor = Blocks.STONE_BRICKS.getDefaultState();
		BlockState rim = Blocks.POLISHED_DEEPSLATE.getDefaultState();
		BlockState wall = Blocks.STONE_BRICK_WALL.getDefaultState();
		BlockState barrier = Blocks.BARRIER.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();
		BlockState lamp = Blocks.SEA_LANTERN.getDefaultState();
		int rimH = 5;
		for (int x = this.minX; x <= this.maxX; x++) {
			for (int z = this.minZ; z <= this.maxZ; z++) {
				for (int yy = this.minY; yy <= this.maxY; yy++) {
					boolean edge = x == this.minX || x == this.maxX || z == this.minZ || z == this.maxZ;
					boolean corner = (x == this.minX || x == this.maxX) && (z == this.minZ || z == this.maxZ);
					BlockState next = air;
					if (yy == this.minY) {
						next = (edge || (x - this.minX) % 4 == 0 || (z - this.minZ) % 4 == 0) ? rim : floor;
						if (corner) {
							next = lamp;
						}
					} else if (yy == this.maxY) {
						next = barrier;
					} else if (edge) {
						int h = yy - this.minY;
						if (h <= rimH) {
							next = h == rimH ? wall : rim;
						} else {
							next = barrier;
						}
					}
					place(new BlockPos(x, yy, z), next);
				}
			}
		}
	}

	private void buildTreasure(ServerWorld world, BlockPos stand) {
		begin(world, stand, TREASURE_SIZE, TREASURE_HEIGHT);
		BlockState sand = Blocks.SANDSTONE.getDefaultState();
		BlockState cut = Blocks.CUT_SANDSTONE.getDefaultState();
		BlockState smooth = Blocks.SMOOTH_SANDSTONE.getDefaultState();
		BlockState red = Blocks.RED_SANDSTONE.getDefaultState();
		BlockState gold = Blocks.GOLD_BLOCK.getDefaultState();
		BlockState lamp = Blocks.LANTERN.getDefaultState();
		BlockState barrier = Blocks.BARRIER.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();
		BlockState pillar = Blocks.CUT_SANDSTONE.getDefaultState();
		int cx = (this.minX + this.maxX) / 2;
		int cz = (this.minZ + this.maxZ) / 2;
		int rimH = 4;
		for (int x = this.minX; x <= this.maxX; x++) {
			for (int z = this.minZ; z <= this.maxZ; z++) {
				for (int yy = this.minY; yy <= this.maxY; yy++) {
					boolean edge = x == this.minX || x == this.maxX || z == this.minZ || z == this.maxZ;
					boolean corner = (x == this.minX || x == this.maxX) && (z == this.minZ || z == this.maxZ);
					int dx = Math.abs(x - cx);
					int dz = Math.abs(z - cz);
					boolean podium = dx <= 2 && dz <= 2;
					boolean ring = dx <= 3 && dz <= 3;
					boolean col = (dx == 8 && dz == 8) || (dx == 8 && dz == 0) || (dx == 0 && dz == 8);
					BlockState next = air;
					if (yy == this.minY) {
						if (corner) {
							next = gold;
						} else if (edge || (x + z) % 2 == 0) {
							next = cut;
						} else {
							next = sand;
						}
					} else if (yy == this.maxY) {
						next = barrier;
					} else if (edge) {
						int h = yy - this.minY;
						if (h <= rimH) {
							next = corner ? gold : (h == rimH ? red : smooth);
						} else {
							next = barrier;
						}
					} else if (col && yy <= this.minY + 7) {
						next = yy == this.minY + 7 ? gold : pillar;
					} else if (podium && yy <= this.minY + 3) {
						if (yy == this.minY + 3 && dx == 0 && dz == 0) {
							next = gold;
						} else if (yy < this.minY + 3) {
							next = dx == 0 && dz == 0 ? gold : red;
						}
					} else if (ring && yy == this.minY + 1 && (dx == 3 || dz == 3)) {
						next = cut;
					}
					place(new BlockPos(x, yy, z), next);
					if (col && yy == this.minY + 8) {
						place(new BlockPos(x, yy, z), lamp);
					}
				}
			}
		}
	}

	private void buildSpleef(ServerWorld world, BlockPos stand) {
		begin(world, stand, SPLEEF_SIZE, SPLEEF_HEIGHT);
		BlockState snow = Blocks.SNOW_BLOCK.getDefaultState();
		BlockState stone = Blocks.STONE.getDefaultState();
		BlockState ice = Blocks.PACKED_ICE.getDefaultState();
		BlockState blue = Blocks.BLUE_ICE.getDefaultState();
		BlockState barrier = Blocks.BARRIER.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();
		BlockState lamp = Blocks.SEA_LANTERN.getDefaultState();
		int snowY0 = this.minY + 8;
		int snowY1 = this.minY + 9;
		int snowY2 = this.minY + 10;
		for (int x = this.minX; x <= this.maxX; x++) {
			for (int z = this.minZ; z <= this.maxZ; z++) {
				for (int yy = this.minY; yy <= this.maxY; yy++) {
					boolean edge = x == this.minX || x == this.maxX || z == this.minZ || z == this.maxZ;
					boolean corner = (x == this.minX || x == this.maxX) && (z == this.minZ || z == this.maxZ);
					BlockState next = air;
					if (yy == this.minY) {
						next = stone;
					} else if (yy == this.minY + 1) {
						next = edge ? stone : Blocks.LAVA.getDefaultState();
					} else if (yy == this.maxY) {
						next = barrier;
					} else if (edge) {
						next = corner ? lamp : (yy <= this.minY + 5 ? blue : ice);
					} else if (yy == snowY0 || yy == snowY1 || yy == snowY2) {
						next = snow;
					}
					place(new BlockPos(x, yy, z), next);
				}
			}
		}
	}

	public void remove() {
		if (this.world == null) {
			return;
		}
		purgeLava();
		if (this.clearVolume) {
			clearStrikeVolume();
			this.clearVolume = false;
		}
		for (int i = this.saved.size() - 1; i >= 0; i--) {
			Saved s = this.saved.get(i);
			this.world.setBlockState(s.pos, s.state, 18);
			if (s.be != null) {
				BlockEntity be = this.world.getBlockEntity(s.pos);
				if (be != null) {
					be.readNbt(s.be);
					be.markDirty();
				}
			}
		}
		this.saved.clear();
		purgeLava();
		this.gems.clear();
		this.world = null;
	}

	public double centerX() {
		return (this.minX + this.maxX) / 2.0 + 0.5;
	}

	public double floorY() {
		if (this.kind == 2) {
			return this.minY + 11.0;
		}
		if (this.kind == 4) {
			return this.minY + 4.0;
		}
		if (this.kind == 6) {
			return this.minY + 1.1;
		}
		if (this.kind == 10) {
			return this.minY + 5.1;
		}
		if (this.kind == 11) {
			return this.minY + 1.0;
		}
		if (this.kind == 12) {
			return this.minY + StrikeMap.G + 1.0;
		}
		if (this.kind == 14) {
			return this.minY + PARTY_FLOOR + 1.0;
		}
		return this.minY + 1.0;
	}

	public double centerZ() {
		return (this.minZ + this.maxZ) / 2.0 + 0.5;
	}

	public double spawnRadius() {
		if (this.kind == 4) {
			return 1.5;
		}
		if (this.kind == 3) {
			return Math.max(4, (this.maxX - this.minX) / 2.0 - 4);
		}
		if (this.kind == 7) {
			return Math.max(2, (this.maxX - this.minX) / 2.0 - 3);
		}
		if (this.kind == 10) {
			return 1.5;
		}
		return Math.max(6, (this.maxX - this.minX) / 2.0 - 5);
	}

	public double duelX(int index) {
		if (this.pillarX.length == 0) {
			return centerX();
		}
		int i = Math.floorMod(index, this.pillarX.length);
		return this.pillarX[i] + 0.5;
	}

	public double duelZ(int index) {
		if (this.pillarZ.length == 0) {
			return centerZ();
		}
		int i = Math.floorMod(index, this.pillarZ.length);
		return this.pillarZ[i] + 0.5;
	}

	public float duelYaw(int index) {
		double dx = centerX() - duelX(index);
		double dz = centerZ() - duelZ(index);
		if (Math.abs(dx) < 0.01 && Math.abs(dz) < 0.01) {
			return 0f;
		}
		return (float) (Math.atan2(-dx, dz) * (180.0 / Math.PI));
	}

	public boolean duelFallen(ServerPlayerEntity player) {
		return this.kind == 4 && player != null && player.getY() < this.minY + 2.15;
	}

	public boolean contains(BlockPos pos) {
		return pos != null
				&& pos.getX() >= this.minX && pos.getX() <= this.maxX
				&& pos.getY() >= this.minY && pos.getY() <= this.maxY
				&& pos.getZ() >= this.minZ && pos.getZ() <= this.maxZ;
	}

	public BlockPos treasurePos() {
		int y = this.kind == 1 ? this.minY + 3 : this.minY + 1;
		return new BlockPos((int) Math.floor(centerX()), y, (int) Math.floor(centerZ()));
	}

	public boolean isTreasure(BlockPos pos) {
		return pos != null && pos.equals(treasurePos());
	}

	public void placeTreasure() {
		if (this.world == null) {
			return;
		}
		this.world.setBlockState(treasurePos(), Blocks.GOLD_BLOCK.getDefaultState(), 3);
	}

	public boolean treasurePresent() {
		return this.world != null && this.world.getBlockState(treasurePos()).isOf(Blocks.GOLD_BLOCK);
	}

	public boolean spleefFallen(ServerPlayerEntity player) {
		return this.kind == 2 && player != null && player.getY() < this.minY + 7.2;
	}

	public boolean knockFallen(ServerPlayerEntity player) {
		return this.kind == 10 && player != null && player.getY() < this.minY + 3.2;
	}

	public void keepInside(ServerPlayerEntity player) {
		if (player == null || this.world == null || this.kind == 6 || this.kind == 10) {
			return;
		}
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		double minPx = this.minX + 1.25;
		double maxPx = this.maxX + 1.0 - 1.25;
		double minPz = this.minZ + 1.25;
		double maxPz = this.maxZ + 1.0 - 1.25;
		boolean out = x < minPx || x > maxPx || z < minPz || z > maxPz;
		if (this.kind == 4) {
			if (y < this.minY + 2.0) {
				return;
			}
			if (y > this.maxY - 0.3) {
				out = true;
			}
		} else if (this.kind == 8 && (y < this.minY - 1.2 || y > this.maxY - 0.3)) {
			out = true;
		} else if (this.kind != 2 && this.kind != 8 && (y < this.minY + 0.8 || y > this.maxY - 0.3)) {
			out = true;
		} else if (this.kind == 2 && y > this.maxY - 0.3) {
			out = true;
		}
		if (!out) {
			return;
		}
		double nx = Math.min(maxPx, Math.max(minPx, x));
		double nz = Math.min(maxPz, Math.max(minPz, z));
		double ny = y;
		if (this.kind == 2) {
			ny = Math.min(this.maxY - 1.0, y);
		} else if (this.kind == 4) {
			ny = Math.min(this.maxY - 1.0, Math.max(this.minY + 4.0, y));
		} else if (y < this.minY + 0.8 || y > this.maxY - 0.3) {
			ny = Math.min(this.maxY - 1.0, Math.max(this.minY + 1.0, y));
		}
		double fy = ny;
		TeleportLog.silent(() -> {
			player.networkHandler.requestTeleport(nx, fy, nz, player.getYaw(), player.getPitch());
			player.fallDistance = 0;
		});
	}

	public void keepMobInside(Entity entity) {
		if (entity == null || this.world == null || this.kind != 7) {
			return;
		}
		double minPx = this.minX + 1.4;
		double maxPx = this.maxX + 1.0 - 1.4;
		double minPz = this.minZ + 1.4;
		double maxPz = this.maxZ + 1.0 - 1.4;
		double minPy = this.minY + 1.0;
		double maxPy = this.maxY - 1.2;
		double x = entity.getX();
		double y = entity.getY();
		double z = entity.getZ();
		if (x >= minPx && x <= maxPx && z >= minPz && z <= maxPz && y >= minPy && y <= maxPy) {
			return;
		}
		entity.teleport(Math.min(maxPx, Math.max(minPx, x)), Math.min(maxPy, Math.max(minPy, y)),
				Math.min(maxPz, Math.max(minPz, z)));
	}

	private void buildRace(ServerWorld world, BlockPos stand, boolean bumpers) {
		this.world = world;
		this.stand = stand == null ? null : stand.toImmutable();
		this.raceBumpers = bumpers;
		int top = world.getTopY() - 2;
		int y = Math.min(top - RACE_HEIGHT, stand.getY());
		y = Math.max(world.getBottomY() + 8, y);
		this.minX = stand.getX() - RACE_SIZE / 2;
		this.minZ = stand.getZ() - RACE_SIZE / 2;
		this.minY = y;
		this.maxX = this.minX + RACE_SIZE - 1;
		this.maxZ = this.minZ + RACE_SIZE - 1;
		this.maxY = this.minY + RACE_HEIGHT - 1;
		int w = RACE_SIZE;
		int d = RACE_SIZE;
		boolean[][] track = new boolean[w][d];
		boolean[][] line = new boolean[w][d];
		int[][] path = {
				{10, 90}, {118, 90}, {118, 10}, {10, 10}, {10, 28}, {102, 28},
				{102, 46}, {26, 46}, {26, 64}, {10, 64}, {10, 90}
		};
		for (int i = 0; i < path.length - 1; i++) {
			stroke(track, path[i][0], path[i][1], path[i + 1][0], path[i + 1][1], 2);
			stroke(line, path[i][0], path[i][1], path[i + 1][0], path[i + 1][1], 0);
		}
		this.raceSurface = new boolean[w][d];
		this.finishMinX = this.minX + 20;
		this.finishMaxX = this.minX + 24;
		this.finishMinZ = this.minZ + 88;
		this.finishMaxZ = this.minZ + 92;
		this.checkMinX = this.minX + 10;
		this.checkMaxX = this.maxX - 10;
		this.checkMinZ = this.minZ + 8;
		this.checkMaxZ = this.minZ + 18;
		this.raceStartX = this.minX + 52.5;
		this.raceStartZ = this.minZ + 90.5;
		this.raceStartYaw = -90f;
		clearRaceBarriers(stand);
		BlockState packed = Blocks.PACKED_ICE.getDefaultState();
		BlockState blue = Blocks.BLUE_ICE.getDefaultState();
		BlockState gold = Blocks.GOLD_BLOCK.getDefaultState();
		BlockState black = Blocks.BLACK_CONCRETE.getDefaultState();
		BlockState red = Blocks.RED_CONCRETE.getDefaultState();
		BlockState white = Blocks.WHITE_CONCRETE.getDefaultState();
		BlockState lamp = Blocks.SEA_LANTERN.getDefaultState();
		BlockState wall = Blocks.SMOOTH_QUARTZ.getDefaultState();
		for (int lx = 0; lx < w; lx++) {
			for (int lz = 0; lz < d; lz++) {
				boolean on = track[lx][lz];
				boolean curb = !on && neighborTrack(track, lx, lz);
				if (!on && !curb) {
					continue;
				}
				this.raceSurface[lx][lz] = true;
				int x = this.minX + lx;
				int z = this.minZ + lz;
				boolean fin = on && x >= this.finishMinX && x <= this.finishMaxX
						&& z >= this.finishMinZ && z <= this.finishMaxZ;
				boolean light = curb && ((lx + lz) % 8 == 0);
				BlockState floor;
				if (fin) {
					floor = (lx + lz) % 2 == 0 ? gold : black;
				} else if (on) {
					floor = line[lx][lz] ? blue : packed;
				} else if (!bumpers && light) {
					floor = lamp;
				} else {
					floor = (lx + lz) % 2 == 0 ? red : white;
				}
				place(new BlockPos(x, this.minY, z), floor);
			}
		}
		clearRaceBarriers(stand);
		if (!bumpers) {
			return;
		}
		for (int lx = 0; lx < w; lx++) {
			for (int lz = 0; lz < d; lz++) {
				if (track[lx][lz] || !cardinalTrack(track, lx, lz)) {
					continue;
				}
				int x = this.minX + lx;
				int z = this.minZ + lz;
				place(new BlockPos(x, this.minY + 1, z), wall);
				place(new BlockPos(x, this.minY + 2, z), wall);
				if ((lx + lz) % 8 == 0) {
					place(new BlockPos(x, this.minY + 3, z), lamp);
				}
			}
		}
	}

	private void clearRaceBarriers(BlockPos stand) {
		if (this.world == null) {
			return;
		}
		int x0 = this.minX - 8;
		int x1 = this.maxX + 8;
		int z0 = this.minZ - 8;
		int z1 = this.maxZ + 8;
		scrubBarriers(x0, x1, z0, z1, this.minY, this.minY + 16);
		if (stand != null) {
			int sy = stand.getY();
			if (sy < this.minY - 2 || sy > this.minY + 16) {
				scrubBarriers(x0, x1, z0, z1, sy, sy + 12);
			}
		}
	}

	private void scrubBarriers(int x0, int x1, int z0, int z1, int y0, int y1) {
		BlockState air = Blocks.AIR.getDefaultState();
		int bottom = this.world.getBottomY();
		int top = this.world.getTopY() - 1;
		y0 = Math.max(bottom, y0);
		y1 = Math.min(top, y1);
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				for (int yy = y0; yy <= y1; yy++) {
					BlockPos pos = new BlockPos(x, yy, z);
					if (!this.world.getBlockState(pos).isOf(Blocks.BARRIER)) {
						continue;
					}
					Saved s = new Saved();
					s.pos = pos.toImmutable();
					s.state = air;
					this.saved.add(s);
					this.world.removeBlockEntity(pos);
					this.world.setBlockState(pos, air, 2);
				}
			}
		}
	}

	private void stroke(boolean[][] mask, int x0, int z0, int x1, int z1, int radius) {
		int dx = Math.abs(x1 - x0);
		int dz = Math.abs(z1 - z0);
		int sx = x0 < x1 ? 1 : -1;
		int sz = z0 < z1 ? 1 : -1;
		int err = dx - dz;
		int x = x0;
		int z = z0;
		while (true) {
			stamp(mask, x, z, radius);
			if (x == x1 && z == z1) {
				break;
			}
			int e2 = 2 * err;
			if (e2 > -dz) {
				err -= dz;
				x += sx;
			}
			if (e2 < dx) {
				err += dx;
				z += sz;
			}
		}
	}

	private void stamp(boolean[][] mask, int cx, int cz, int radius) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz > radius * radius + radius) {
					continue;
				}
				int x = cx + dx;
				int z = cz + dz;
				if (x >= 0 && z >= 0 && x < mask.length && z < mask[0].length) {
					mask[x][z] = true;
				}
			}
		}
	}

	private boolean cardinalTrack(boolean[][] track, int lx, int lz) {
		int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] p : d) {
			int x = lx + p[0];
			int z = lz + p[1];
			if (x >= 0 && z >= 0 && x < track.length && z < track[0].length && track[x][z]) {
				return true;
			}
		}
		return false;
	}

	private boolean neighborTrack(boolean[][] track, int lx, int lz) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				int x = lx + dx;
				int z = lz + dz;
				if (x >= 0 && z >= 0 && x < track.length && z < track[0].length && track[x][z]) {
					return true;
				}
			}
		}
		return false;
	}

	public double raceSpawnX(int index) {
		int row = Math.max(0, index) / 2;
		return this.raceStartX - row * 2.4;
	}

	public double raceSpawnZ(int index) {
		int lane = Math.max(0, index) % 2;
		return this.raceStartZ + (lane == 0 ? -1.3 : 1.3);
	}

	public boolean raceOffTrack(ServerPlayerEntity player) {
		if (player == null || this.kind != 6) {
			return false;
		}
		if (player.getY() < this.minY - 0.5) {
			return true;
		}
		int x = (int) Math.floor(player.getX());
		int z = (int) Math.floor(player.getZ());
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (onRaceSurface(x + dx, z + dz)) {
					return false;
				}
			}
		}
		return true;
	}

	private boolean onRaceSurface(int x, int z) {
		if (this.raceSurface == null) {
			return false;
		}
		int lx = x - this.minX;
		int lz = z - this.minZ;
		return lx >= 0 && lz >= 0 && lx < this.raceSurface.length && lz < this.raceSurface[0].length
				&& this.raceSurface[lx][lz];
	}

	public double raceSpectateX() {
		return this.raceStartX;
	}

	public double raceSpectateY() {
		return this.minY + 6.0;
	}

	public double raceSpectateZ() {
		return this.raceStartZ;
	}

	public boolean raceCheckpoint(ServerPlayerEntity player) {
		if (player == null || this.kind != 6) {
			return false;
		}
		int x = player.getBlockX();
		int z = player.getBlockZ();
		return x >= this.checkMinX && x <= this.checkMaxX && z >= this.checkMinZ && z <= this.checkMaxZ;
	}

	public boolean crossedFinish(ServerPlayerEntity player) {
		if (player == null || this.kind != 6) {
			return false;
		}
		int x = player.getBlockX();
		int z = player.getBlockZ();
		int y = player.getBlockY();
		return x >= this.finishMinX && x <= this.finishMaxX && z >= this.finishMinZ && z <= this.finishMaxZ
				&& y >= this.minY && y <= this.minY + 3;
	}

	private void place(BlockPos pos, BlockState next) {
		place(pos, next, 2);
	}

	private void placeLava(BlockPos pos) {
		place(pos, Blocks.LAVA.getDefaultState(), 18);
	}

	private void place(BlockPos pos, BlockState next, int flags) {
		Saved s = new Saved();
		s.pos = pos.toImmutable();
		s.state = this.world.getBlockState(pos);
		BlockEntity be = this.world.getBlockEntity(pos);
		if (be != null) {
			s.be = be.createNbt();
		}
		this.saved.add(s);
		this.world.removeBlockEntity(pos);
		this.world.setBlockState(pos, next, flags);
	}

	private void purgeLava() {
		if (this.world == null) {
			return;
		}
		int y0 = Math.max(this.world.getBottomY(), this.minY - 48);
		int y1 = this.maxY + 4;
		for (int x = this.minX - 8; x <= this.maxX + 8; x++) {
			for (int z = this.minZ - 8; z <= this.maxZ + 8; z++) {
				for (int y = y0; y <= y1; y++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (this.world.getBlockState(pos).getBlock() == Blocks.LAVA
							|| this.world.getFluidState(pos).getFluid() == net.minecraft.fluid.Fluids.LAVA
							|| this.world.getFluidState(pos).getFluid() == net.minecraft.fluid.Fluids.FLOWING_LAVA) {
						this.world.setBlockState(pos, Blocks.AIR.getDefaultState(), 18);
					}
				}
			}
		}
	}
}
