# Garbage Collector — destination 4

Each 16×16 cell receives a separate seed derived from the world seed and chunk
coordinates. Terrain is stable across reloads and independent of generation order.
This generator assembles fragments itself rather than reseeding shared vanilla noise.

Cells independently select plains, forest, desert, mountain, or snowy terrain.
Surface heights vary abruptly; one-block void seams outline the grid, and
approximately one in nineteen cells is entirely missing. Former ocean cells generate
as plains; no water or containment walls are generated. Trees and cave pockets are clipped to their own cells.
The initial biome has a pale gray-green sky, fixed noon, no precipitation, and no
natural mob spawns. Vanilla structures and decoration are disabled so they cannot
spill across the grid or fill missing cells.

Use `/stairs spawn garbage_collector` in the Overworld, then ascend the staircase.
Arrival exits are centered on a solid, dry cell. The arrival staircase can return
the player to their entrance; `/stairs return` also works for testing.

This is the initial terrain implementation; collection events, threats, and
progression objectives have not been added.
