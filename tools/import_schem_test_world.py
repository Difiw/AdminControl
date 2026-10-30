import gzip
import re
import struct
import sys
import time
from pathlib import Path

import generate_cw_expedition as gen
from verify_cw_expedition import read_nbt


STATE_RE = re.compile(r"^([^\[]+)(?:\[(.*)\])?$")


def decode_varints(data):
    values = []
    value = 0
    shift = 0
    for byte in data:
        value |= (byte & 0x7F) << shift
        if byte & 0x80:
            shift += 7
            if shift > 35:
                raise ValueError("VarInt exceeds five bytes")
        else:
            values.append(value)
            value = 0
            shift = 0
    if shift:
        raise ValueError("Truncated VarInt stream")
    return values


def parse_state(text):
    match = STATE_RE.match(text)
    if not match:
        raise ValueError(f"Invalid block state: {text}")
    name, properties_text = match.groups()
    properties = {}
    if properties_text:
        for item in properties_text.split(","):
            key, value = item.split("=", 1)
            properties[key] = value
    return name, properties


def make_level_dat(spawn):
    flat = gen.compound("generator", [gen.string("type", "minecraft:flat"), gen.compound("settings", [
        gen.string("biome", "minecraft:the_void"), gen.byte("features", 0), gen.byte("lakes", 0),
        gen.list_("layers", 10, [gen.compound_payload([gen.integer("height", 1), gen.string("block", "minecraft:air")])]),
        gen.list_("structure_overrides", 8, []),
    ])])
    overworld = gen.compound_payload([gen.string("type", "minecraft:overworld"), flat])
    nether = gen.compound_payload([gen.string("type", "minecraft:the_nether"), gen.compound("generator", [
        gen.string("type", "minecraft:noise"), gen.string("settings", "minecraft:nether"), gen.long("seed", 20261017)
    ])])
    end = gen.compound_payload([gen.string("type", "minecraft:the_end"), gen.compound("generator", [
        gen.string("type", "minecraft:noise"), gen.string("settings", "minecraft:end"), gen.long("seed", 20261017)
    ])])
    dimensions = gen.compound("dimensions", [
        gen.tag(10, "minecraft:overworld", overworld),
        gen.tag(10, "minecraft:the_nether", nether),
        gen.tag(10, "minecraft:the_end", end),
    ])
    data = [
        gen.integer("DataVersion", gen.DATA_VERSION),
        gen.string("LevelName", "Codex Visual Check - Ultimate Redstone Laboratory"),
        gen.long("RandomSeed", 20261017),
        gen.integer("GameType", 1),
        gen.byte("hardcore", 0), gen.byte("allowCommands", 1), gen.byte("initialized", 1),
        gen.integer("SpawnX", spawn[0]), gen.integer("SpawnY", spawn[1]), gen.integer("SpawnZ", spawn[2]),
        gen.float_("SpawnAngle", 180.0),
        gen.long("Time", 6000), gen.long("DayTime", 6000),
        gen.long("LastPlayed", int(time.time() * 1000)), gen.integer("version", 19133),
        gen.compound("Version", [gen.string("Name", "1.20.1"), gen.integer("Id", gen.DATA_VERSION), gen.byte("Snapshot", 0), gen.string("Series", "main")]),
        gen.compound("WorldGenSettings", [gen.long("seed", 20261017), gen.byte("generate_features", 0), gen.byte("bonus_chest", 0), dimensions]),
        gen.compound("GameRules", [gen.string("doDaylightCycle", "false"), gen.string("doWeatherCycle", "false"), gen.string("doMobSpawning", "false")]),
        gen.double("BorderCenterX", 0.0), gen.double("BorderCenterZ", 0.0), gen.double("BorderSize", 512.0),
        gen.double("BorderSafeZone", 5.0), gen.double("BorderDamagePerBlock", 0.2),
        gen.long("BorderSizeLerpTime", 0), gen.double("BorderSizeLerpTarget", 512.0),
        gen.integer("BorderWarningBlocks", 5), gen.integer("BorderWarningTime", 15),
        gen.compound("DragonFight", []), gen.list_("ScheduledEvents", 10, []),
    ]
    return gzip.compress(gen.root([gen.compound("Data", data)]), compresslevel=9)


def main():
    if len(sys.argv) != 3:
        raise SystemExit("usage: import_schem_test_world.py INPUT.schem TARGET_WORLD")
    source = Path(sys.argv[1])
    target = Path(sys.argv[2])
    if target.exists():
        raise SystemExit(f"REFUSING: target already exists: {target}")

    root = read_nbt(gzip.decompress(source.read_bytes()))
    schematic = root.get("Schematic", root)
    width, height, length = (schematic[key] for key in ("Width", "Height", "Length"))
    palette = {value: name for name, value in schematic["Palette"].items()}
    block_data = schematic.get("BlockData", schematic.get("Blocks", {}).get("Data"))
    blocks = decode_varints(block_data)
    expected = width * height * length
    if len(blocks) != expected:
        raise ValueError(f"Decoded {len(blocks)} blocks, expected {expected}")

    gen.W = gen.World()
    gen.VOID_CHUNK_RADIUS = 6
    x0, y0, z0 = -width // 2, 64, -length // 2
    parsed_palette = {index: parse_state(state) for index, state in palette.items()}
    for index, palette_index in enumerate(blocks):
        state = parsed_palette[palette_index]
        if state[0] == "minecraft:air":
            continue
        x = index % width
        z = (index // width) % length
        y = index // (width * length)
        gen.W.set(x0 + x, y0 + y, z0 + z, state[0], state[1])

    spawn = (0, 74, 84)
    target.mkdir(parents=True)
    (target / "level.dat").write_bytes(make_level_dat(spawn))
    (target / "session.lock").write_bytes(struct.pack(">q", int(time.time() * 1000)))
    for folder in ("datapacks", "data", "playerdata", "advancements", "stats"):
        (target / folder).mkdir()
    chunks, regions = gen.write_regions(target)
    print(f"CREATED {target.resolve()}")
    print(f"SOURCE {source.resolve()}")
    print(f"DIMENSIONS {width}x{height}x{length}")
    print(f"NON_AIR_BLOCKS {len(gen.W.blocks)} CHUNKS {chunks} REGIONS {regions} SPAWN {spawn}")


if __name__ == "__main__":
    main()
