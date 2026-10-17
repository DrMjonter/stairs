# Forest stairs

A clean, modern smooth-quartz staircase rises eight steps and ends abruptly in
midair. The treads, inclined spine, and buried foundation are one block wide,
with no railings or landing. The
pristine white finish and exposed underside deliberately look out of place in
the woods. It uses vanilla blocks and the built-in jigsaw structure system;
no extra dependencies are needed. Server-side Java code tracks each staircase's lifecycle.

It generates in newly generated jungle, sparse jungle, bamboo jungle, dark forest,
swamp, and mangrove swamp chunks when structures are enabled.
Placement has one candidate per 48-by-48-chunk region, with 24-chunk separation
and random rotation. Candidates outside the allowed biomes are skipped, so this
does not guarantee a staircase every 768 blocks. Terrain adaptation supports
the foundation; steep terrain and later tree growth may affect its surroundings.

With cheats enabled, find a natural structure:

```mcfunction
/locate structure mjontersstairs:forest_stairs
```

To explore another area without creating a new world:

```mcfunction
/stairs rtp
```

This player command requires cheats or operator permission level 2 and works in
the Overworld. It chooses a random destination within a horizontal radius of
10,000 blocks of your current position, inside the world border. It checks for
solid ground and two air blocks above it, avoids water and hazardous footing,
and clears falling momentum on arrival. It tries up to 16 locations, reporting
failure without moving you if none are suitable. Generating distant chunks may
cause a brief pause. Destinations may include previously explored terrain;
repeat the command to keep exploring, then use `/locate` to find nearby stairs.

Or generate one near your current position:

```mcfunction
/place structure mjontersstairs:forest_stairs
```

For an exact-position template preview (the foundation is at the supplied Y):

```mcfunction
/place template mjontersstairs:forest_stairs ~ ~ ~
```

Tune frequency in `worldgen/structure_set/forest_stairs.json` (spacing must be
greater than separation), and biomes in
`tags/worldgen/biome/has_structure/forest_stairs.json`, under
`src/main/resources/data/mjontersstairs`. Restart the world after changes.

The NBT template is an intentional source asset. To change its shape, edit
`tools/generate_forest_stairs.py` and run `python tools/generate_forest_stairs.py`.
Then run `./gradlew.bat build`.

## Staircase lifecycle

Natural staircases and `/stairs spawn` staircases have saved identities. Approaching
within 32 blocks of the first tread marks one encountered. Distances are spherical,
measured from the center of that tread. Only living non-spectator players discover
and activate stairs. An encountered staircase expires when no player remains within
96 blocks. Spectators and players on the death screen still count as physically
present: changing game mode or dying in place does not count as leaving.
Unseen stairs do not expire. Another nearby player prevents disappearance.
Traveling to another dimension counts as leaving.

To activate, walk onto one of the bottom three treads and then reach the second-to-last tread (the top tread also triggers activation).
Flying, riding, or leaving the immediate 16-block vicinity clears ascent progress.
Jumping while climbing is allowed, but detection requires landing on a tread.
Activation permanently prevents automatic disappearance for every player. Tracked quartz blocks are protected from player breaking, explosions, and piston
movement, including in Creative mode.
Unimplemented destinations display a temporary message naming the fixed destination,
`mjontersstairs:dimension_1` through `mjontersstairs:dimension_10`.
Destination 1 now enters the parking garage. Destinations 2?10 remain reserved
IDs and display the temporary activation message. Use `/stairs spawn garage` for
a test entrance linked to destination 1.
The destination derives from the world seed and staircase position and is saved.

Identity, rotation, destination, lifecycle state, and partial removal progress are
stored in the Overworld's `data/mjontersstairs_staircases.dat`. The system does not
force chunks to load. Expired stairs in unloaded chunks are cleaned on a subsequent
lifecycle check after those chunks load. Only remaining template quartz blocks are
removed, without drops; other blocks in the surrounding clearing are preserved.
Same-type replacement quartz at an original template position is indistinguishable
from the original block. Completed cleanup never repeats on player rebuilds.

An empty server pauses lifecycle checks so shutdown/restart does not by itself
expire an encountered staircase. If other players remain online, logging out or
respawning elsewhere can allow an unused staircase to expire. Ascent progress is not saved;
after reconnecting, start from the bottom again.

Vanilla `/place structure` and `/place template` are visual previews only: they do
not save structure starts, so they are not tracked. Use `/stairs spawn` for lifecycle
testing. It requires a player with operator permission level 2 in the Overworld and
places a tracked template beside the player, overwriting its small clearing like
vanilla `/place`. Use a clear test area. It refuses to reuse a recorded origin.
`/locate` uses worldgen records, so it can still point at an expired natural staircase.
Existing natural structures are picked up when approached; changed biome/frequency
settings only affect new generation.

## Manual gameplay checks

1. Run `/stairs spawn` in an open area, approach the foot, walk more than 96 blocks
   away, then return. The staircase should be gone.
2. Spawn another staircase at a different location. Walk up from the bottom; check
   the destination message, leave, and return. It should remain.
3. Save and restart after encountering, activating, and expiring separate stairs.
   Verify each state persists, including after traveling far enough to unload chunks.
4. With two players, keep one within 96 blocks while the other leaves. Unused stairs
   should remain until both leave. Activated stairs should remain regardless.
5. Find natural stairs with `/locate structure mjontersstairs:forest_stairs` and check
   activation and removal on each rotation, including stairs crossing chunk borders.

`./gradlew.bat build` includes dependency-free regression checks for state transitions,
NBT persistence, destination assignment, and rotated cleanup coordinates. It does not
replace these in-game movement, worldgen, and multiplayer checks.

## Ascent effects

Standing on a tracked staircase reduces walking speed by 80% using a separate
Staircase Slowness effect. Stepping off removes only this effect, preserving vanilla
potion effects. Flying, riding, spectator mode, death, and leaving the Overworld
clear it. The effect also runs on already activated stairs. The effect detection extends 2.5 blocks above each tread with a 0.4-block
horizontal allowance, keeping it active across risers and normal jumps. Moving
sideways beyond that footprint releases it.

Darkness starts at 30% target intensity on the first tread and reaches full
intensity on the second-to-last tread, with a fast, smooth fade-in.
Height above the staircase origin controls a smoothly interpolated black overlay,
camera sway, and pulsing field of view. Backing down reduces intensity; stepping off
fades it out. Distortion respects the client's Distortion Effects setting. The fade
currently tops out near black, so the temporary activation message remains readable.
The server sends intensity only to the affected player. Missing updates time out,
and disconnecting clears the animation. Client rendering classes are client-only.

Test with `/stairs spawn`: walk slowly up, reverse direction halfway, step sideways
off the stairs, and repeat after activation. Also test vanilla Slowness concurrently,
Spectator/Creative flight, reconnecting, and two players at different stair heights.
Camera rendering and shader-mod compatibility still require in-game verification.

The parking garage transfer pre-generates an arrival area and uses a plain black
receiving screen until Minecraft reports terrain readiness, then fades in. It hides
the normal loading artwork/text; initial chunk generation or Iris shader compilation
may still cause a pause. This does not guarantee a hitch-free transfer on every client.

## Block protection

Tracked staircases cannot be mined (including in Creative), exploded, or moved by
pistons. Block placement is denied within a three-block buffer in each axis around
any solid staircase block, covering the foundation through the top step. Multi-block
placements such as beds are rejected if any part enters the buffer. Piston movement
into the buffer is also blocked. Protection ends when a staircase expires; automatic
lifecycle removal still works. Vanilla `/place` previews are not tracked or protected.
Administrative commands such as `/setblock` remain able to change blocks.

Verify in-game with mining, Creative breaking, TNT, beds spanning the boundary, and
pistons. Ordinary terrain can still be mined nearby. This protection covers block
placement events; it does not prevent every other world interaction, such as buckets.

Vanilla sneak-style edge protection is enabled while Staircase Slowness is active,
preventing ordinary walking off unsupported edges without crouching, lowering the
camera, or adding sneak slowdown. Walking up and down the half-block steps still
works. This uses vanilla support checks: adjacent solid ground remains walkable,
and external pushes are not a hard staircase boundary. Check sideways and diagonal
movement on rotated stairs, descending, and ordinary movement after leaving.

Jumping is disabled while Staircase Slowness is active. Normal walking up the
half-block treads still works. Jump input and jump strength are suppressed, with
Jump Boost's additional upward impulse suppressed separately. Stepping off removes
the effect and restores normal jumping without removing any Jump Boost potion.
Check holding jump, sprint-jumping, auto-jump, and Jump Boost on and off the stairs.

The staircase speed penalty is excluded from vanilla's speed-based FOV calculation.
Instead, a separate smoothed animation gradually narrows FOV by up to 12%, with a
subtle 3.5% pulse. FOV eases in and out more slowly than darkness; FOV Effects and
Distortion Effects settings control zoom and pulse respectively.

## Optional Sound Physics Remastered integration

Install Sound Physics Remastered **NeoForge 1.21.1-1.5.1** on the client. This is an
optional, version-pinned compatibility integration; it is not bundled in the Stairs
JAR and is not needed on a dedicated server. Other SPR versions require checking the
internal hooks before changing the metadata version range. Keep SPR enabled and use
an audio device with OpenAL EFX support.

As the staircase visual effect intensifies, world sounds gradually lose high
frequencies and gain a diffuse reverberant tail. AMBIENT, WEATHER, BLOCKS, HOSTILE,
NEUTRAL, and PLAYERS sound categories are included. MUSIC, RECORDS, MASTER/UI,
VOICE, and SPR auxiliary-only voice-chat processing are excluded. This affects all
included sound sources heard by the local player, not just sounds near the stairs.

Two client-only mixins hook SPR's processSound/setEnvironment methods and the
Minecraft audio channel update. Original per-source environments are cached on the
sound thread and blended with the staircase intensity. Existing loops update every
audio tick without additional raycasts. Stepping off restores the cached normal
settings as intensity fades; later SPR updates continue to supply current acoustic
settings. Channel destruction and sound-engine initialization clear cached sources.
No SPR config values or user volume sliders are changed. Without SPR, audio hooks
have no cached sources and the staircase still works without muffling/reverb.

Reference inspected: https://github.com/henkelmax/sound-physics-remastered/tree/1.21.1
Compatible release: https://modrinth.com/mod/sound-physics-remastered/version/Dd2tmpsk

Listening checks: play a long ambient sound before approaching stairs, ascend and
back off, then verify its normal sound returns. Repeat with rain, footsteps and mobs;
check music/UI remain unchanged. Test reconnect and F3+T resource reload. If using
AmbientSounds too, install its required CreativeCore dependency separately; sounds
are included according to the sound category that mod uses.

## IterationT 3.2.0 ascent shader preview

Run `python tools/patch_iteration.py` to create
`run/shaderpacks/IterationT 3.2.0 - Stairs.zip` from the locally installed original.
The script also copies its settings. Select **IterationT 3.2.0 - Stairs.zip** in
Iris's shader selection screen. Restart Minecraft after installing this Java change
so Iris loads the new uniform hook. The original pack is unchanged. Only the patch
script and our original GLSL include belong in source control, not the pack archive.

An optional client mixin registers `uniform float stairsAscent` through the installed
Iris 1.8.14-beta.1 uniform interfaces. It supplies interpolated ascent intensity each
frame. Packs that do not use this uniform retain the usual screen fade. The Java
bridge uses reflection to avoid a required Iris dependency and logs a warning if
its uniform registration API is incompatible.

The Iteration patch runs in the Overworld final pass after exposure/tonemapping.
It gradually desaturates/cools the scene and strongly darkens sky-depth pixels while
keeping nearby terrain brighter. It does not alter actual block light, weather or
world time. The mod delays its fullscreen blackout until the last 20% of intensity
when the custom uniform is actively read, preventing the overlay from masking the
shader effect too early. At zero intensity the patch returns the original color.

This is depth-based final-image grading, not a rewrite of volumetric lighting.
Clouds and transparent surfaces may need visual tuning. Complementary has not yet
been patched. Test outdoors in daylight: ascend halfway, look at sky and steps, back
off, and repeat near activation. Test shader switching and reload as well. Java
build/regressions and archive integrity were checked; GPU shader compilation and
in-game appearance still need testing in Iris.

The Iteration preview now adds peripheral stretching and slow distant-scene shimmer.
The center stays stable, and depth masking fades the warp in between 3 and 12 blocks
from the camera. Nearby foreground samples are excluded to reduce silhouette smearing.
Distortion Effects controls the new warp through `stairsDistortionScale`; set it to
zero to keep color/sky grading without the warp. Restart after the Java update and
reload the patched shader. In-game appearance and shader compilation need checking.
Rebuilding the preview preserves any existing settings for the patched copy.

Stair effect detection extends 0.4 blocks past each tread's horizontal edges, including
rotated stairs, so standing partly over an edge keeps slowness, audio and visuals active.
Moving farther off the tread footprint still releases the effects.

Peripheral stretch uses a 52% inward radial sampling offset with a slow 8% variation at full strength,
with an earlier ascent ramp and stronger shimmer. The central area and immediate
foreground remain protected. Reload the patched pack after rebuilding it.

The stronger reality-tear preview adds traveling horizontal folds, peripheral twist,
and red/blue separation. It reaches full warp by 70% ascent, before the late blackout.
The center and immediate foreground stay masked; Distortion Effects still scales all
warping and color separation. GPU appearance needs in-game verification.
