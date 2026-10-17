# Tickstorm — destination 5

Tickstorm uses the vanilla plains biome with Overworld surface rules, caves, structures,
and terrain compressed toward sea level. Its purple haze is provided both by the custom
dimension effects and the optional IterationT shader patch.

The dimension stays clear: Tickstorm does not request vanilla rain or thunder, and it
remains safe to run beside Weather2.

Time continuously alternates between two phases with no calm interval or separate
storm event:

1. **Capture (15 seconds):** players can move, but block breaking, block/item use,
   placement, container/workstation opening, and entity interaction packets are held.
   Mobs are frozen and living-entity damage is queued.
2. **Burst (3 seconds):** recorded actions and damage replay in their original order at
   five times their capture speed. Mobs also simulate five ticks per server tick.

After each burst, another capture begins immediately. Queued player actions are discarded
if their player disconnects, dies, or leaves the dimension before playback.

Use `/stairs spawn tickstorm` to create a destination-5 entrance.
