"""Rebuild the committed structure template with Python 3 (no dependencies)."""

import gzip
from pathlib import Path
import struct


def string(value):
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def tag(kind, name, payload):
    return bytes([kind]) + string(name) + payload


def integer(value):
    return struct.pack(">i", value)


def sequence(kind, values):
    return bytes([kind]) + integer(len(values)) + b"".join(values)


def compound(*tags):
    return b"".join(tags) + b"\0"


def state(name, **properties):
    tags = [tag(8, "Name", string("minecraft:" + name))]
    if properties:
        tags.append(tag(10, "Properties", compound(*[
            tag(8, key, string(value)) for key, value in properties.items()
        ])))
    return compound(*tags)


palette = [
    state("air"),
    state("smooth_quartz"),
    state("smooth_quartz_stairs", facing="south", half="bottom", shape="straight", waterlogged="false"),
    state("smooth_quartz_stairs", facing="north", half="top", shape="straight", waterlogged="false"),
]

# A small clearing with a buried foundation. Local Y=1 is ground level
# because single pool elements have a ground-level delta of one block.
size = (5, 11, 11)
blocks = {(x, y, z): 0 for x in range(size[0])
          for y in range(1, size[1]) for z in range(size[2])}


# One-block-wide treads on a thin inclined spine, anchored only at the foot.
# Upside-down stairs shape the underside instead of filling a solid wedge.
blocks[2, 0, 2] = 1
for step in range(8):
    z = step + 2
    blocks[2, step + 1, z] = 2
    if step > 0:
        blocks[2, step, z] = 3

entries = [compound(
    tag(9, "pos", sequence(3, [integer(v) for v in pos])),
    tag(3, "state", integer(index)),
) for pos, index in sorted(blocks.items())]

root = tag(10, "", compound(
    tag(3, "DataVersion", integer(3955)),  # Minecraft 1.21.1
    tag(9, "size", sequence(3, [integer(v) for v in size])),
    tag(9, "palette", sequence(10, palette)),
    tag(9, "blocks", sequence(10, entries)),
    tag(9, "entities", sequence(10, [])),
))
destination = (Path(__file__).resolve().parents[1] / "src/main/resources/data"
               / "mjontersstairs/structure/forest_stairs.nbt")
destination.parent.mkdir(parents=True, exist_ok=True)
destination.write_bytes(gzip.compress(root, mtime=0))
print(f"Wrote {destination} ({len(entries)} blocks)")
