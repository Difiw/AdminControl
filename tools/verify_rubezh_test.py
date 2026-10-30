import gzip
import importlib.util
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("verify", HERE / "verify_cw_expedition.py")
v = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v)

def main():
    world = Path(sys.argv[1])
    level = v.read_nbt(gzip.decompress((world / "level.dat").read_bytes()))["Data"]
    assert level["DataVersion"] == 3465
    assert level["Version"]["Name"] == "1.20.1"
    assert (level["SpawnX"], level["SpawnY"], level["SpawnZ"]) == (0, 66, -49)
    assert level["WorldGenSettings"]["dimensions"]["minecraft:overworld"]["generator"]["settings"]["biome"] == "minecraft:the_void"
    checks = {
        "spawn_floor": (0, 64, -49, "minecraft:gray_concrete"),
        "entrance_floor": (0, 65, -8, "minecraft:polished_andesite"),
        "floor_1": (0, 65, 9, "minecraft:smooth_stone"),
        "floor_2": (0, 71, 9, "minecraft:smooth_stone"),
        "floor_3": (0, 77, 9, "minecraft:smooth_stone"),
        "roof": (0, 83, 9, "minecraft:smooth_stone"),
        "parking": (39, 64, 11, "minecraft:black_concrete"),
        "gatehouse": (16, 65, -44, "minecraft:polished_andesite"),
        "antenna": (2, 99, 12, "minecraft:redstone_torch"),
    }
    for label, (x, y, z, expected) in checks.items():
        actual = v.block_at(world, x, y, z)
        assert actual == expected, f"{label}: expected {expected}, got {actual}"
        print(label, x, y, z, actual)
    regions = list((world / "region").glob("*.mca"))
    assert regions
    for rp in regions:
        raw = rp.read_bytes()
        assert len(raw) >= 8192 and len(raw) % 4096 == 0
    print("VALID", len(regions), "regions", "all architectural checkpoints present")

if __name__ == "__main__":
    main()
