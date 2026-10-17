# Parking garage (retired prototype)

Destination 1 is now Minecraft 33.X. See [future-dimension.md](future-dimension.md)
for its current terrain, villages and entry command. The notes below describe the
old garage prototype and do not apply to newly generated destination-1 chunks.

Restart Minecraft after updating. Select **IterationT 3.2.0 - Stairs.zip** in Iris.
In the Overworld, use `/stairs spawn garage` in a clear area, then walk from the
lower steps to the second-to-last step. The staircase anchors permanently and
begins the transfer. `/stairs return` is the operator-only testing exit. Both
commands require permission level 2. Survival escape conditions are not implemented.

## Environment

`mjontersstairs:dimension_1` is one horizontally repeating parking level, with a floor
at Y=48, low ceiling at Y=54, and sealed bedrock backing. There are striped pillars,
yellow lane markings, white parking bays, worn floor patches, partial divider walls,
ceiling beams, and sparse light-level-10 fluorescent fixtures. Some fixtures are dark.
There is no skylight, precipitation, or natural mob spawning. No entities or anomalies
have been added yet. This version has no ramps, parked cars, or additional floors.

The original custom shader treatment is included in the patched Iteration final pass:
neutral, lightly desaturated grading, reduced ambient brightness, dark distance fog, and a vignette.
Slow peripheral stretching and traveling folds persist throughout the garage, leaving
the center clear and reducing distortion on nearby geometry. The Screen Effects
slider controls this distortion.
It preserves fixture highlights and uses the `stairsGarage` uniform to apply only in
this dimension. It works independently of the ascent distortion. The room remains
physically dark without shaders, but Complementary has not received this grading.
Regenerate the local shader copy with `python tools/patch_iteration.py` if needed.

## Travel

Natural stairs already assigned destination 1 now enter this garage; other destinations
remain placeholders. `/stairs spawn garage` explicitly creates a destination-1 entrance.
Previously activated destination-1 stairs can be reused by walking up from the bottom.

The origin deterministically selects an aisle center within its corresponding 32-block
module, so each entrance always arrives at the same place. Several entrances in the
same module can share an arrival. The arrival is Y=49, centered in the aisle. A 3x3 pad
and three blocks of headroom are restored before entry to prevent player-built traps.
The entrance return position is saved per player in `mjontersstairs_staircases.dat`,
including across disconnects and death. The operator return searches for a safe surface
near that entrance; if none exists it reports failure instead of teleporting blindly.

On activation, movement is held while the client fades out. The server pre-generates a
5x5 chunk area one chunk per tick using expiring tickets, then transfers after 35 ticks.
The client displays solid black rather than Minecraft's terrain-loading artwork/text
until the normal readiness signal, then fades in. Chunk-generation and shader-compilation
stalls can still extend the black interval. An aborted transfer releases the fade; the
client also has a timeout. No client-to-server teleport requests are accepted.

## Verification

`./gradlew.bat build` runs the pure arrival, return-data persistence, fade, and existing
staircase regression checks. A dedicated GameTest validates actual generated blocks,
roof, dimension metadata and arrival headroom at positive and negative coordinates:

```powershell
python tools/prepare_garage_tests.py
./gradlew.bat -I build/garage-gametest.gradle runGameTestServer
```

The test-only datapack adds the garage to the flat world preset because vanilla's
GameTest runner otherwise omits additional dimensions. It lives under `build` and
does not modify regular world presets or saves.

Still check in-game: ascent/entry, return, repeated entry, reconnecting in the garage,
two players entering, and the patched shader under your chosen settings. The dedicated
test does not verify client render timing, Iris compilation, or actual player travel.

## Custom music

Add one or more **Ogg Vorbis** files to
`src/main/resources/assets/mjontersstairs/sounds/music/garage/`:

- `garage_01.ogg`
- `garage_02.ogg`
- `garage_03.ogg`

No audio is bundled. These streamed slots are declared in `assets/mjontersstairs/sounds.json`.
They play in order and repeat, skipping absent files. With no files present, vanilla
music remains available. Unused slots can log missing-sound warnings on resource reload.
Rebuild and restart the development client after adding source files; resource packs
can replace the same paths (F3+T reloads installed resource packs).

Entry crossfades the currently playing vanilla music into the garage track over four
seconds. Leaving fades garage music out over four seconds, then returns control to
normal music selection. Tracks fade in when starting; tracks ending naturally advance
to the next available slot. Pausing pauses playback and fades. Music follows the Music
and Master sliders, with no changes to their settings. This does not fade jukebox discs
or ambient sound effects, and is independent of Iris and Sound Physics Remastered.

Actual playback/crossfade needs an in-game check once valid audio files are supplied.
