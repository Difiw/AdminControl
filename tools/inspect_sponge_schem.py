import gzip
import json
import sys
from collections import Counter
from pathlib import Path

from verify_cw_expedition import read_nbt


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


def main():
    path = Path(sys.argv[1])
    root = read_nbt(gzip.decompress(path.read_bytes()))
    schem = root.get("Schematic", root)
    palette = schem["Palette"]
    reverse_palette = {value: name for name, value in palette.items()}
    block_data = schem.get("BlockData", schem.get("Blocks", {}).get("Data"))
    values = decode_varints(block_data)
    counts = Counter(reverse_palette.get(value, f"<unknown:{value}>") for value in values)
    block_entities = schem.get("BlockEntities", schem.get("Blocks", {}).get("BlockEntities", []))
    result = {
        "file": str(path.resolve()),
        "version": schem.get("Version"),
        "data_version": schem.get("DataVersion"),
        "dimensions": [schem.get("Width"), schem.get("Height"), schem.get("Length")],
        "offset": schem.get("Offset"),
        "palette_size": len(palette),
        "decoded_blocks": len(values),
        "expected_volume": schem.get("Width") * schem.get("Height") * schem.get("Length"),
        "non_air_blocks": sum(count for block, count in counts.items() if not block.startswith("minecraft:air")),
        "block_entities": len(block_entities),
        "entities": len(schem.get("Entities", [])),
        "top_blocks": counts.most_common(40),
        "feature_blocks": sorted(
            (block, count)
            for block, count in counts.items()
            if any(token in block for token in ("glass", "door", "stairs", "slab", "sign", "ladder", "trapdoor", "redstone"))
        ),
        "block_entity_ids": Counter(entity.get("Id", entity.get("id", "<unknown>")) for entity in block_entities),
    }
    result["block_entity_ids"] = dict(result["block_entity_ids"])
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
