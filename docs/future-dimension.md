# Minecraft 33.X

Destination 1 uses a custom `future_flat` generator and the Overworld dimension
type (normal daylight, sky and weather). Turf is at Y=64, ten gravel layers at
Y=54 through 63, a solid stone roof at Y=41 through 53, then vanilla Overworld
noise caves/aquifers and cave/canyon carvers below that. Stone below Y=0 becomes
deepslate; Bedrock+ seals Y=-64. The roof keeps caves from disrupting the flat
surface or collapsing the gravel. Digging is needed to reach the caves.

Only vanilla villages are enabled as structures. A fixed custom plains biome
retains vanilla plains colors and climate and is tagged for plains villages.
It has the Overworld carvers, but no decoration features or natural mob spawn
lists. A server-side dimension boundary additionally rejects every `Mob` except
normal villagers, regardless of whether it came from village mechanics, a
spawner, a command, chunk loading, or another mod. Players and non-mob entities
such as items, minecarts, and projectiles are unaffected. This currently excludes
cats, iron golems, wandering traders, zombie villagers, animals, and monsters.
Cave shape/aquifers are enabled, not the complete Overworld ore/feature set.
Villages' vanilla layouts, farms, loot, villagers and workstations
remain intact. During world generation in this dimension, oak and cobblestone
families are replaced with the plastic blocks, and grass with Turf. Matching
block-state properties survive replacement. This also covers village decorations;
it does not change Overworld villages or blocks manually placed by players.

Use `/stairs spawn future` in the Overworld and ascend. `/stairs return` remains
the operator exit. Arrival creates a matching staircase sunk into the ground,
with its top tread flush with the surface. X/Z, rotation, the player's exact
position on the tread, yaw and pitch are retained; only height changes. An open
trench provides headroom. Entrances persist, are protected, and are reused without
being sunk farther on later visits. Containers, fluids and non-terrain obstructions
abort placement before edits; placed stone/dirt cannot be distinguished from
natural terrain. The HUD displays Minecraft 33.X; garage shader grading
and the garage playlist are disabled here.

Existing generated destination-1 chunks are not rewritten. Use a new test world
for the raised terrain: existing saves may also retain their previous generator
settings. No save directories are automatically deleted or migrated.

The same arrival placement is used in Mirror World. Its buried staircase saves
the original entrance origin so ascending back preserves footing at the original
height. A new snapshot resets the mirror; the next arrival prepares its entrance
again. Destinations 2-6 remain unavailable until their worlds are implemented.

Generation checks use the isolated GameTest setup in `tools/prepare_garage_tests.py`.
Village-exit progression and custom future shader visuals are not implemented yet.

## Alternating day length

Minecraft 33.X has its own saved solar clock, initially at sunrise. A full normal
day/night cycle lasts 20 minutes; the following full cycle lasts 30 seconds at
40x solar speed. This repeats, switching at sunrise. Durations assume 20 server
ticks per second. The clock is shared by all visitors. Every entry, login in the dimension,
or respawn there restarts it at sunrise on the first normal-speed day and resets the
experienced-midnight counters for everyone currently inside. Active fifth-midnight ascents
are canceled, with temporary Slow Falling for a safe descent. Players can lie in beds in this
dimension, including during daylight, but bed attempts do not change their respawn point and
sleep never skips the night or clears the weather. After three seconds they wake up and see
"Cant sleep..." in the action bar. Bed reach and obstruction checks still apply. Villager sleep is unaffected.
Between arrivals the clock
it advances while the server ticks, even when the dimension is empty, and resumes
its saved phase after restarting.

Only solar time advances faster: simulation time, block ticks, and other
dimensions' solar clocks/rates are unchanged. NeoForge's time-rate synchronization
keeps clients moving at the correct speed between updates. The sun, moon, and orb
follow this local time. `doDaylightCycle=false` and server tick freezing pause it.
The alternating schedule owns this dimension's time rate and reapplies it each
tick; normal time-setting/sleeping can change which cycle it is on. Vanilla
`/time set` affects multiple dimensions, so it is not a dimension-local test command.

## Distant orb (first rendering pass)

`FutureOrbRenderer` draws celestial geometry during NeoForge's AFTER_SKY stage,
only in destination 1. It stays southeast, about 13 degrees above the horizon,
with a plain black disc roughly 17 degrees across at noon. Its geometry radius
grows smoothly from 15 at noon to 60 (50% of maximum) at 11:30 PM, then surges
to 120 (8x) at midnight. It shrinks gradually across the entire morning to reach
15 again at noon. The three smooth segments meet with zero slope; frame
interpolation keeps the surge smooth even during accelerated days. The final
half-hour takes 25 real seconds normally, or 0.625 seconds during a 30-second cycle.
Opacity retains its separate dusk/dawn curve. Perspective
makes its midnight angular diameter about 100 degrees, rather than exactly 8x
its daytime angular diameter.
It has no entity, collision,
chunk dependency, or positional parallax. Terrain renders over it without the
orb writing depth. The disc is 8% opaque in full daylight; a broad, smooth
solar-elevation fade starts before sunset and reaches full opacity after twilight,
reversing at dawn. There are no rings, rim or halo in the baseline renderer.
Rain suppresses visibility; immersion, blindness and
darkness suppress rendering. Other dimensions and the moon are unchanged.

The shaders-off baseline uses Minecraft's position/color renderer. IterationT's
basic pass disables blending and forces opaque output, so the patched pack now
draws the orb in the deferred sky pass with explicit background-radiance blending.
The atmosphere is darkened before volumetric and planar clouds are composited;
the sun/moon/stars are masked separately without darkening foreground clouds.
IterationT's clouds are translucent, which made the enormous black background
read as if it were in front. The patch now exports planar-cloud coverage and uses
the volumetric pass's remaining transmittance to suppress the orb beneath either
cloud layer. Soft thresholds preserve cloud edges while moderately dense clouds
fully obscure it. The normal cloud functions and lighting remain otherwise intact.
The pack's existing sky-depth branch keeps terrain in front. Both renderers use
`FutureOrbAppearance` through the `stairsOrbOpacity` and
`stairsOrbRadius` uniforms. The bridge checks for compiled orb support and matches
the active pipeline's frame notifier before suppressing the baseline renderer;
it does not rely on filenames. A two-second last-read signal from the compiled
orb uniform provides a fallback if Iris changes pipeline/notifier identity.

Run `python tools/patch_iteration.py` to rebuild the local patched pack; select
`IterationT 3.2.0 - Stairs.zip` and reload it (restart the client after Java changes).
The original archive and pack settings are preserved. Other packs retain the
geometry fallback and may need their own alpha support. Vanilla already draws
the orb during AFTER_SKY with depth writes disabled, before its clouds. The
IterationT patch leaves the reflection/lighting sky atlas unchanged; reflected
orb rendering is not implemented. Opacity now blends linear sky radiance before
tonemapping, so its apparent contrast can differ from the shaders-off renderer.
No lensing or pack-specific bloom has been added yet.

## Orb audio

The orb emits a client-side positional ambience from the same fixed southeast,
slightly elevated bearing as its rendered disc. Its volume follows the disc's
size curve: 8% source gain at noon, smoothly rising to full volume at midnight.
The source is 48 blocks away with a 192-block attenuation range, so midnight
retains 75% of its source gain rather than losing half of it to distance. The
effective daylight level remains quiet beneath the normal soundscape. A second
loop plays only during the rapid 11:30 PM-to-midnight expansion and is stopped by
solar time, keeping it synchronized during both normal and 40x days. Both use the
Ambient volume slider and stop outside Minecraft 33.X.

One of four directional stinger variants plays after a random 1-3 minute initial
delay and then every 2-5 minutes. Stinger scheduling uses real client ticks rather
than solar time, so fast days do not create bursts; only its volume follows the
orb, from restrained daylight playback to its loudest level at midnight.

Sound Physics Remastered recognizes these four event IDs and gives them a
dedicated distant, dark reverb profile.
Without Sound Physics Remastered they retain vanilla directional attenuation.
The optional files and authoring guidance are in
`assets/mjontersstairs/sounds/future/README.txt`; both must be mono for positional
audio. Missing files are intentionally silent and do not prevent startup.

Sound Physics Remastered also gives all in-world sounds in Minecraft 33.X a
restrained open-space echo floor. Block interactions, footsteps and player
sounds, villagers, ambient audio, and weather retain their normal positional dry
signal while gaining a broad reverb tail. Jukebox records are included; background
music, UI sounds, and voice chat are excluded. The profile fades in on entry and
is reduced to 65% during daylight, eases up to full strength around dusk, remains
at full strength through the night, and eases down again around dawn. It is cleared immediately on exit; the
orb's stronger dedicated profile is layered on top.

## Recorded residents

Real visitors are recorded throughout their stay at six regular samples per second, independent of solar speed.
Takeoff and landing add timestamped keyframes so repeated sprint-jumps retain brief ground contacts.
Position, yaw/pitch, crouching and ground state are saved in the dimension's `mjontersstairs_future_visits`
SavedData archive. Leaving, disconnecting or dying finishes that visit; completed recordings persist
across server restarts. Each visit becomes a separate resident with a stable Steve or Alex appearance
and no nametag, inventory, AI or block/inventory interactions. Replays interpolate positions and gaze and loop.
Client entity interpolation is reduced to one tick to avoid smoothing away short landings a second time.
Residents carry the scoreboard tag `recorded_player:<name>` plus UUID and name in NeoForge persistent data.
Hitting a resident redirects the damage source and amount to its original player by UUID, even if that
player is in another dimension. The owner must be online and alive; normal player armor, shields,
damage cooldowns, creative immunity and PvP rules apply. Residents retain their own health and replay.

Six optional color variants use standard 64x64 PNG Minecraft skins with classic Steve arms (four pixels wide).
Place `clone_1.png` through `clone_6.png` in
`assets/mjontersstairs/textures/entity/future_replay/`. Each visit selects a stable variant from its UUID;
the server synchronizes this slot to every client. All custom skins share the classic model. Missing slots
fall back to the visit's Steve/Alex appearance. Texture existence is checked through the resource manager,
so resource packs and resource reloads work without a registry change. Nametags stay hidden.

Whenever the dimension is occupied, completed visits play from the beginning. Another arrival dissolves
the current residents into enchantment glyphs, waits one second, and starts every replay over. Ongoing
recordings are unaffected. When nobody remains, replay entities are removed. Residents do not force-load
chunks: the timeline continues while distant sections are unloaded, with entities restored at the correct
position when their recorded location is loaded again. No archive retention limit is imposed yet;
recordings and the number of residents accumulate with visits.

## Fifth-midnight ejection

Midnights are counted per player only while that player is present in Minecraft 33.X, and the count is
saved with the world but reset for current visitors whenever anyone enters. On the fifth experienced midnight, the orb smoothly expands to 100 times its
normal rendered scale over six seconds and then holds at that size. Darkness is applied while the server
locks horizontal velocity and accelerates the player upward. After eight seconds, the player is returned
to the recorded Overworld staircase entrance through the normal protected dimension transition. If the
sequence is interrupted by death, disconnect, or an external dimension change, it is safely canceled and
the fifth night can trigger again later.

## Villager prayer state

While visitors are present, an occasional resident spawns on an unoccupied bed: one attempt every
60-120 real seconds, never within twelve blocks of any player. Only bed heads in already loaded chunks
within four chunks of active visitors are considered, with intact foot halves, collision clearance,
world-border checks, no nearby villagers within four blocks, and a five-minute reuse cooldown per bed.
Attempts without an eligible bed are skipped. Beds become the new villager's home memory. These timers
are independent of the accelerated solar clock and pause while empty or during containment.

From noon (tick 6000) until nightfall (tick 13000), loaded villagers replace their normal brain tasks
with surveillance of the nearest alive, non-spectator player. They path toward that player at speed 0.65,
stop inside a four-block radius, and keep looking at the player's eyes. Targets are reconsidered every
AI tick; paths update every half second or when the nearest player changes. With no eligible player,
villagers stop navigation. At nightfall or after a clock reset, vanilla brain tasks resume, leaving
the later prayer schedule intact. Clones are not player targets. Solar speed changes shorten this
window but do not speed up movement, and unreachable players are not reached by teleporting.
Plastic doors are tagged as wooden and mob-interactable doors. Surveillance explicitly runs vanilla
door interaction against its navigation path, so suspending the rest of the brain does not trap
villagers inside houses.

Player-caused damage to a villager and successful bed-click evictions produce a distant warning from
the orb. The server chooses one of three variants and sends the same choice to all current visitors.
Warnings share a two-second cooldown across the dimension; a new warning replaces the previous one
rather than layering. They use full source gain even during daylight, the Ambient slider, the existing
48-block southeast source, 192-block attenuation range, and the dedicated Sound Physics orb reverb.
Natural wake-ups and environmental damage do not trigger warnings. Missing audio is safely silent.
Each warning also grows the orb to 3x its current daily radius in 0.2 seconds, holds for one second,
then smoothly returns it to the daily curve over two seconds. Its opacity rises toward at least 85% during the pulse, so daytime
warnings remain visible. Slight camera shake, FOV oscillation and a pulsing edge vignette use the same
real-tick envelope and respect Screen Effects. The patched IterationT pack adds peripheral warp and
chromatic splitting through `stairsOrbReaction`; the center remains clear. The visual pulse still plays
when warning audio files are missing, and clears on exit. Finale expansion takes precedence over the
warning multiplier.
Accepted warnings are shared across the dimension, including projectile hits and successful bed evictions
by any player. On the fifth warning, a global containment sequence replaces the ordinary reaction:
the orb expands immediately to 8x its starting radius over 0.4 seconds, ignoring accelerated solar time;
red action-bar diagnostics appear at three seconds; screen distortion builds smoothly from six to ten seconds.
During those same six-to-ten seconds, the orb becomes an expanding spherical sky cap. Its angular
boundary reaches every sky direction at ten seconds, including behind the player; a flat disc alone
cannot achieve this. Vanilla and patched IterationT share the same coverage envelope.
The action bar displays "ERROR: Hostility toward residents is not tolerated." in red from three until
ten seconds, then clears when the blackout begins. No sky text renderer is used.
At ten seconds the screen goes black, every current visitor is returned to the bottom of their recorded
arrival staircase inside Minecraft 33.X (or nearby safe ground if obstructed), the local solar clock resets to day one
sunrise, warning/midnight counters reset, and clones are removed with playback rewound. Saved recordings
are retained for subsequent visits. This is a failed attempt and local simulation reset, not an escape;
no dimension-change teleport or exit completion is performed. New arrivals during containment join its current timeline instead
of canceling it. The ordinary fifth-midnight finale is suspended during this sequence.

Supply a 13-second mono Ogg Vorbis file named `orb_containment.ogg` in
`assets/mjontersstairs/sounds/future/`, preferably 48 kHz. The positional sound starts at the orb immediately
and continues through the return transition; the blackout holds until thirteen seconds and fades away
over two seconds. The visual sequence works without the audio file. Both vanilla camera/FOV effects and
the patched IterationT midnight distortion use the escalating envelope and respect Screen Effects.
The server controls ejection timing; client visual timing is synchronized on start and transfer.
Supply `orb_warning_01.ogg`, `orb_warning_02.ogg`, and `orb_warning_03.ogg` in
`assets/mjontersstairs/sounds/future/`: mono Ogg Vorbis, preferably 48 kHz, recommended length 2-5 seconds
with a short fade at both ends. Stereo would defeat the intended positional effect.

At approximately 11:50 PM (solar tick 17833), every loaded villager wakes and paths toward a reachable nearby
position beneath open sky. At midnight the client receives a two-second
distortion pulse; shader-off rendering uses camera and FOV displacement, while
the patched IterationT pack also adds a radial image warp and chromatic split.
The directional `orb_boom` one-shot begins on the same client tick as the pulse
and comes from the orb's bearing with its dedicated long-range reverb.
At midnight, villagers enter **prayer state**. Villagers that are outdoors stop,
face southeast toward the orb at a 45-degree upward angle, and remain motionless.
They refuse player interaction and cannot trade during prayer. Indoor stragglers
continue toward open sky before joining them, but likewise refuse trading. At
1:00 AM prayer ends, their transient movement lock is removed, and their normal
REST activity is requested so they return to bed. Prayer follows dimension solar
time, so it is deliberately compressed during a 40x day.

Visual checks in destination 1: compare `/time set noon`, `/time set sunset`
and `/time set midnight`; look southeast, then turn/walk to check its fixed
bearing. Check occlusion behind village buildings, clouds and underwater.
Repeat with shaders off and the patched IterationT pack, and confirm the orb
disappears when returning to the Overworld. These are manual checks, not covered
by the headless regression suite.
