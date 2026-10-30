import importlib.util
import math
import random
import shutil
import struct
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("cwgen", HERE / "generate_cw_expedition.py")
g = importlib.util.module_from_spec(spec)
spec.loader.exec_module(g)

W = g.W
W.blocks.clear()
g.SKY_SHIFT = 0
g.SPAWN = (0, 66, -49)
g.VOID_CHUNK_RADIUS = 12
g.WORLD_BORDER_SIZE = 300.0
random.seed(1012026)

AIR = "minecraft:air"
Y = 64

def fill(*args, block, props=None):
    W.fill(*args, block, props)

def setb(x, y, z, block, props=None):
    W.set(x, y, z, block, props)

def clear(x1, y1, z1, x2, y2, z2):
    fill(x1, y1, z1, x2, y2, z2, block=AIR)

def lamp_post(x, z, height=4):
    fill(x, Y + 1, z, x, Y + height, z, block="minecraft:polished_blackstone_wall")
    setb(x, Y + height + 1, z, "minecraft:lantern", {"hanging": "false"})

def chair(x, y, z, facing="north"):
    setb(x, y, z, "minecraft:dark_oak_stairs", {"facing": facing, "half": "bottom", "shape": "straight", "waterlogged": "false"})

def desk(x1, z1, x2, z2, y):
    fill(x1, y, z1, x2, y, z2, block="minecraft:spruce_slab", props={"type": "top", "waterlogged": "false"})

def ceiling_light(x, y, z, emergency=False):
    setb(x, y, z, "minecraft:redstone_lamp" if emergency else "minecraft:sea_lantern", {"lit": "true"} if emergency else None)

def window_x(x, y1, z1, y2, z2):
    fill(x, y1, z1, x, y2, z2, block="minecraft:light_blue_stained_glass_pane")

def window_z(x1, y1, z, x2, y2):
    fill(x1, y1, z, x2, y2, z, block="minecraft:light_blue_stained_glass_pane")

# Sculpted 120x120 test site with a reinforced underside and shallow grade changes.
for x in range(-60, 61):
    for z in range(-60, 61):
        edge = max(abs(x), abs(z))
        top = Y + (1 if (x > 42 and z > 32) or (x < -43 and z > 38) else 0)
        fill(x, top - 4, z, x, top - 1, z, block="minecraft:stone")
        setb(x, top, z, "minecraft:grass_block")
        if edge >= 58 and (x + z) % 5 == 0:
            setb(x, top + 1, z, "minecraft:mossy_cobblestone")

# Main asphalt road, turning apron, sidewalks and markings.
fill(-4, Y, -60, 4, Y, -12, block="minecraft:gray_concrete")
fill(-30, Y, -16, 46, Y, -8, block="minecraft:gray_concrete")
fill(-6, Y, -60, -5, Y, -8, block="minecraft:smooth_stone")
fill(5, Y, -60, 6, Y, -8, block="minecraft:smooth_stone")
fill(-30, Y, -18, 46, Y, -17, block="minecraft:smooth_stone")
fill(-30, Y, -7, 46, Y, -6, block="minecraft:smooth_stone")
for z in range(-58, -17, 6):
    setb(0, Y + 1, z, "minecraft:yellow_concrete")
for x in range(-27, 44, 7):
    setb(x, Y + 1, -12, "minecraft:white_concrete")

# Parking with individual bays, wheel stops and two abandoned utility vehicles.
fill(25, Y, -5, 54, Y, 27, block="minecraft:black_concrete")
for x in range(28, 54, 6):
    fill(x, Y + 1, -3, x, Y + 1, 20, block="minecraft:white_concrete")
    fill(x + 1, Y + 1, 20, x + 4, Y + 1, 20, block="minecraft:stone_brick_wall")
for bx, bz, color in [(33, 7, "minecraft:yellow_concrete"), (45, 14, "minecraft:green_concrete")]:
    fill(bx, Y + 1, bz, bx + 3, Y + 2, bz + 6, block=color)
    fill(bx + 1, Y + 3, bz + 1, bx + 2, Y + 3, bz + 4, block="minecraft:tinted_glass")
    for wx, wz in [(bx, bz + 1), (bx, bz + 5), (bx + 3, bz + 1), (bx + 3, bz + 5)]:
        setb(wx, Y + 1, wz, "minecraft:black_concrete")

# Perimeter fence with guarded southern vehicle gate.
for x in range(-55, 56):
    if -8 <= x <= 8:
        continue
    if x % 2 == 0:
        fill(x, Y + 1, -52, x, Y + 3, -52, block="minecraft:iron_bars")
for z in range(-52, 56):
    for x in (-55, 55):
        if z % 2 == 0:
            fill(x, Y + 1, z, x, Y + 3, z, block="minecraft:iron_bars")
for x in range(-55, 56, 2):
    fill(x, Y + 1, 55, x, Y + 3, 55, block="minecraft:iron_bars")
fill(-9, Y + 1, -53, -7, Y + 6, -51, block="minecraft:deepslate_bricks")
fill(7, Y + 1, -53, 9, Y + 6, -51, block="minecraft:deepslate_bricks")
fill(-9, Y + 6, -53, 9, Y + 7, -51, block="minecraft:polished_andesite")
fill(-6, Y + 1, -52, 6, Y + 3, -52, block="minecraft:iron_bars")
clear(-3, Y + 1, -52, 3, Y + 4, -52)
for x in (-7, 7):
    setb(x, Y + 5, -50, "minecraft:sea_lantern")

# Gatehouse: angled canopy, security desk, barrier controls and floodlights.
fill(11, Y + 1, -49, 21, Y + 1, -38, block="minecraft:polished_andesite")
fill(11, Y + 2, -49, 11, Y + 6, -38, block="minecraft:gray_concrete")
fill(21, Y + 2, -49, 21, Y + 6, -38, block="minecraft:gray_concrete")
fill(11, Y + 2, -49, 21, Y + 6, -49, block="minecraft:gray_concrete")
fill(11, Y + 2, -38, 21, Y + 6, -38, block="minecraft:gray_concrete")
clear(12, Y + 2, -48, 20, Y + 5, -39)
window_x(11, Y + 3, -46, Y + 5, -40); window_z(14, Y + 3, -38, 19, Y + 5)
fill(10, Y + 7, -50, 23, Y + 7, -37, block="minecraft:stone_brick_slab")
desk(14, -43, 18, -42, Y + 2); chair(16, Y + 2, -45, "south")
setb(19, Y + 2, -47, "minecraft:lever", {"face": "wall", "facing": "west", "powered": "false"})
ceiling_light(16, Y + 6, -44)

# Irregular three-storey administrative/research building footprint.
# Central block, western security annex, eastern lab wing and northern stair tower.
footprints = [(-20, -4, 20, 23), (-30, 2, -20, 17), (20, 1, 30, 18), (-8, 23, 7, 30)]
for x1, z1, x2, z2 in footprints:
    fill(x1, Y, z1, x2, Y, z2, block="minecraft:polished_andesite")

def shell(x1, z1, x2, z2, floor_y, wall, roof):
    fill(x1, floor_y, z1, x2, floor_y, z2, block="minecraft:smooth_stone")
    fill(x1, floor_y + 1, z1, x1, floor_y + 5, z2, block=wall)
    fill(x2, floor_y + 1, z1, x2, floor_y + 5, z2, block=wall)
    fill(x1, floor_y + 1, z1, x2, floor_y + 5, z1, block=wall)
    fill(x1, floor_y + 1, z2, x2, floor_y + 5, z2, block=wall)
    clear(x1 + 1, floor_y + 1, z1 + 1, x2 - 1, floor_y + 5, z2 - 1)
    fill(x1, floor_y + 6, z1, x2, floor_y + 6, z2, block=roof)

for fy in (Y + 1, Y + 7, Y + 13):
    shell(-20, -4, 20, 23, fy, "minecraft:light_gray_concrete", "minecraft:smooth_stone")
    shell(-30, 2, -20, 17, fy, "minecraft:deepslate_tiles", "minecraft:polished_deepslate")
    shell(20, 1, 30, 18, fy, "minecraft:white_concrete", "minecraft:oxidized_cut_copper")
    shell(-8, 23, 7, 30, fy, "minecraft:gray_concrete", "minecraft:stone_bricks")

# Reopen all connecting passages after overlapping shells.
for fy in (Y + 1, Y + 7, Y + 13):
    clear(-20, fy + 1, 6, -20, fy + 4, 10)
    clear(20, fy + 1, 6, 20, fy + 4, 10)
    clear(-4, fy + 1, 23, 3, fy + 4, 23)

# Varied windows on every facade.
for fy in (Y + 1, Y + 7, Y + 13):
    for x in (-15, -8, 7, 14):
        window_z(x, fy + 2, -4, x + 3, fy + 4)
    for x in (-14, -5, 5, 13):
        window_z(x, fy + 2, 23, x + 2, fy + 4)
    window_x(-30, fy + 2, 5, fy + 4, 9)
    window_x(-30, fy + 2, 12, fy + 4, 15)
    window_x(30, fy + 2, 4, fy + 4, 8)
    window_x(30, fy + 2, 12, fy + 4, 16)

# Main entrance with porch, canopy, double doors and illuminated signage.
clear(-2, Y + 2, -4, 2, Y + 5, -4)
setb(-1, Y + 2, -4, "minecraft:iron_door", {"facing": "south", "half": "lower", "hinge": "left", "open": "true", "powered": "false"})
setb(1, Y + 2, -4, "minecraft:iron_door", {"facing": "south", "half": "lower", "hinge": "right", "open": "true", "powered": "false"})
fill(-6, Y + 1, -10, 6, Y + 1, -5, block="minecraft:polished_andesite")
for x in (-6, 6): fill(x, Y + 2, -9, x, Y + 5, -9, block="minecraft:quartz_pillar")
fill(-7, Y + 6, -11, 7, Y + 6, -4, block="minecraft:cut_copper_slab")
for x in (-4, 4): ceiling_light(x, Y + 5, -8)

# Interior partitions and accessible corridors on all floors.
for fy in (Y + 1, Y + 7, Y + 13):
    fill(-18, fy + 1, 7, 18, fy + 4, 7, block="minecraft:gray_concrete")
    clear(-2, fy + 1, 7, 2, fy + 3, 7)
    fill(-7, fy + 1, -2, -7, fy + 4, 21, block="minecraft:light_gray_concrete")
    fill(7, fy + 1, -2, 7, fy + 4, 21, block="minecraft:light_gray_concrete")
    for z in (2, 12, 19):
        clear(-7, fy + 1, z, -7, fy + 3, z + 1)
        clear(7, fy + 1, z, 7, fy + 3, z + 1)
    for x, z in [(-13, 1), (0, 1), (13, 1), (-13, 13), (0, 13), (13, 13)]:
        ceiling_light(x, fy + 5, z)
    for x, z in [(-4, 9), (4, 16)]:
        ceiling_light(x, fy + 5, z, emergency=True)

# Broad stairwell spanning all floors: one six-step flight per storey.
for level, fy in enumerate((Y + 1, Y + 7)):
    clear(-6, fy + 1, 24, 5, fy + 6, 29)
    for n in range(6):
        fill(-6 + n, fy + 1 + n, 25, -5 + n, fy + 1 + n, 27, block="minecraft:smooth_stone")
    fill(0, fy + 6, 25, 5, fy + 6, 29, block="minecraft:smooth_stone")

# Floor 1: reception, security, staff room and technical plant.
desk(-4, 1, 4, 3, Y + 2)
for x in (-3, 0, 3): chair(x, Y + 2, 4, "north")
setb(0, Y + 3, 2, "minecraft:cartography_table")
fill(-28, Y + 2, 4, -22, Y + 4, 5, block="minecraft:bookshelf")
desk(-27, 11, -22, 12, Y + 2); chair(-24, Y + 2, 14, "north")
for x in (22, 25, 28):
    fill(x, Y + 2, 4, x, Y + 4, 4, block="minecraft:barrel")
    setb(x, Y + 2, 12, "minecraft:furnace", {"facing": "west", "lit": "false"})
fill(9, Y + 2, 10, 18, Y + 2, 10, block="minecraft:spruce_slab", props={"type": "top", "waterlogged": "false"})
for x in (10, 14, 18): chair(x, Y + 2, 12, "north")

# Floor 2: laboratory, radio room, director office and archive.
fy = Y + 7
for z in (0, 4, 14, 18):
    desk(9, z, 18, z + 1, fy + 1)
    for x in (10, 14, 18): setb(x, fy + 2, z, "minecraft:brewing_stand")
for x in (-27, -24, -21): fill(x, fy + 1, 4, x, fy + 4, 15, block="minecraft:bookshelf")
desk(-5, 12, 5, 14, fy + 1); setb(0, fy + 2, 13, "minecraft:lectern", {"facing": "south", "has_book": "false", "powered": "false"})
fill(22, fy + 1, 4, 28, fy + 3, 4, block="minecraft:deepslate_tiles")
for x in (23, 25, 27): setb(x, fy + 4, 4, "minecraft:redstone_lamp", {"lit": "true"})
for z in (7, 11, 15): setb(27, fy + 1, z, "minecraft:note_block")

# Floor 3: bedrooms, lounge, server room and observation room.
fy = Y + 13
for x, z, facing in [(-17, 0, "south"), (-11, 0, "south"), (-17, 14, "north"), (-11, 14, "north")]:
    setb(x, fy + 1, z, "minecraft:gray_bed", {"facing": facing, "occupied": "false", "part": "foot"})
    setb(x, fy + 1, z + (1 if facing == "south" else -1), "minecraft:gray_bed", {"facing": facing, "occupied": "false", "part": "head"})
desk(-4, 12, 4, 14, fy + 1)
for x in (-3, 0, 3): chair(x, fy + 1, 16, "north")
for x in (22, 25, 28):
    for z in (4, 8, 12, 16):
        fill(x, fy + 1, z, x, fy + 4, z, block="minecraft:black_concrete")
        setb(x, fy + 3, z, "minecraft:redstone_lamp", {"lit": "true"})
desk(-5, 1, 5, 3, fy + 1); setb(0, fy + 2, 2, "minecraft:lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})

# Roof plant: vents, pipes, antenna, maintenance catwalk and warning lights.
roof = Y + 20
fill(-17, roof + 1, 4, -10, roof + 3, 10, block="minecraft:iron_block")
for x in (-15, -12): setb(x, roof + 4, 7, "minecraft:iron_trapdoor", {"facing": "north", "half": "top", "open": "true", "powered": "false", "waterlogged": "false"})
fill(10, roof + 1, 5, 10, roof + 4, 16, block="minecraft:cut_copper")
fill(14, roof + 1, 5, 14, roof + 4, 16, block="minecraft:cut_copper")
for y in range(roof + 1, roof + 15):
    setb(2, y, 12, "minecraft:iron_bars")
    if y % 3 == 0: fill(0, y, 12, 4, y, 12, block="minecraft:iron_bars")
setb(2, roof + 15, 12, "minecraft:redstone_torch")
fill(-6, roof + 1, 25, 5, roof + 1, 29, block="minecraft:iron_trapdoor", props={"facing": "north", "half": "bottom", "open": "false", "powered": "false", "waterlogged": "false"})

# Landscaping, rocks, shrubs, signs and light network.
exclusion = lambda x, z: (-33 <= x <= 33 and -20 <= z <= 33) or (-8 <= x <= 8 and z < -15) or (23 <= x <= 56 and -8 <= z <= 30)
for _ in range(55):
    x, z = random.randint(-50, 50), random.randint(-45, 50)
    if exclusion(x, z): continue
    h = random.randint(4, 7)
    fill(x, Y + 1, z, x, Y + h, z, block="minecraft:spruce_log")
    for yy, r in [(Y+h-2, 2), (Y+h-1, 2), (Y+h, 1)]:
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if abs(dx) + abs(dz) <= r + 1:
                    setb(x + dx, yy, z + dz, "minecraft:spruce_leaves", {"distance": "1", "persistent": "true", "waterlogged": "false"})
for _ in range(130):
    x, z = random.randint(-52, 52), random.randint(-48, 52)
    if not exclusion(x, z): setb(x, Y + 1, z, random.choice(["minecraft:fern", "minecraft:grass", "minecraft:mossy_cobblestone"]))
for x, z in [(-5,-45),(5,-34),(-25,-14),(22,-14),(24,25),(-35,25),(-48,2),(48,34)]: lamp_post(x,z)
fill(-3, Y + 1, -38, 3, Y + 3, -38, block="minecraft:dark_oak_fence")
setb(0, Y + 4, -38, "minecraft:yellow_concrete")

# Rebuild lighting indexes consumed by the shared Anvil writer.
g.HIGHEST.clear(); g.LIGHT_SOURCES.clear()
for (x, y, z), state in W.blocks.items():
    if not state[0].startswith(g.TRANSPARENT_PREFIX):
        g.HIGHEST[(x, z)] = max(y, g.HIGHEST.get((x, z), -999))
    level = g.LIGHT_LEVEL.get(state[0], 0)
    if level: g.LIGHT_SOURCES.append((x, y, z, level))

NOTES = """CW EXPEDITION — тестовая зона «Рубеж-01»
Minecraft Java 1.20.1 / DataVersion 3465

SPAWN: 0 66 -49 — подход от КПП, смотреть на север.
BEST_VIEW: 0 70 -35 — фронтальный обзор главного входа и корпуса.
BASE_CENTER: 0 66 9
MAIN_ENTRANCE: 0 66 -4
GATEHOUSE: 16 66 -44
PARKING: 39 65 11
STAIR_TOWER: 0 66 27
ROOF_ANTENNA: 2 100 12

Зона: 121×121 блок. Главный корпус: три этажа плюс техническая крыша.
Этаж 1: ресепшен, охрана, персонал, техническая зона.
Этаж 2: лаборатория, радиорубка, кабинет, архив.
Этаж 3: жилые комнаты, отдых, серверная, наблюдательная комната.
"""

def main():
    if len(sys.argv) != 2:
        raise SystemExit("usage: generate_rubezh_test.py TARGET_WORLD")
    target = Path(sys.argv[1])
    if target.exists():
        raise SystemExit(f"REFUSING: target already exists: {target}")
    target.mkdir(parents=True)
    (target / "level.dat").write_bytes(g.level_dat())
    (target / "session.lock").write_bytes(struct.pack(">q", int(time.time() * 1000)))
    for folder in ("datapacks", "data", "playerdata", "advancements", "stats"):
        (target / folder).mkdir()
    (target / "CW_Rubezh01_Test_Notes.txt").write_text(NOTES, encoding="utf-8")
    chunks, regions = g.write_regions(target)
    print(f"CREATED {target}")
    print(f"BLOCKS {len(W.blocks)} CHUNKS {chunks} REGIONS {regions} SPAWN {g.SPAWN}")

if __name__ == "__main__":
    main()
