# Mirror dimension

Restart Minecraft/world after updating and select `IterationT 3.2.0 - Stairs.zip`.
Use `/stairs spawn mirror` in the Overworld and ascend from the bottom. Natural
stairs assigned destination 7 also enter `mjontersstairs:dimension_7`.

The transfer preserves exact position, yaw and pitch, and reproduces the entrance
staircase with its original rotation. You can finish ascending or turn around and
walk down into the mirror landscape. The arrival staircase is permanent and uses
the same mining/explosion/piston/placement protection. It applies the same slowness, jump lock, darkening and ascent distortion on both sides.
Walk down to the lower steps, then climb again to return to the Overworld at the
same position and facing. Arriving near the top does not immediately send you back.
`/stairs return` remains an operator testing shortcut.

## World generation

At server world creation, a common-side mixin clones the actual Overworld chunk
generator through its registry-aware codec into a separate generator instance.
The mirror uses the same world seed and generator settings, with its own fixed-midnight
dimension type (standard Overworld height: Y=-64 through 319). The JSON has vanilla Overworld
noise/biomes as a fallback declaration; the startup clone also supports flat and
other codec-serializable generator configurations. The dedicated flat-preset test
checks the settings match, generator instances differ, and generated columns match.

The mirror is a separate persistent world populated by periodic snapshots of saved
Overworld chunks. Builds and terrain changes appear as they were at capture time;
subsequent Overworld changes do not propagate until the next refresh. Uncaptured
chunks still use the cloned generator when explored.

## Daily live snapshots

- `/stairs mirror snapshot` queues an immediate capture and live refresh (operator level 2).
- `/stairs mirror status` shows progress, active capture timestamp and next automatic time.

One automatic capture is scheduled at a random time each UTC calendar day, using
the system clock rather than Minecraft days. The schedule survives restarts. If the
server was offline or the mirror occupied, one overdue capture runs when possible;
it does not replay each missed day. Manual captures can be repeated and count as
that day's automatic capture. No offline process runs while Minecraft is closed.

Both capture and replacement wait until there are no players in the mirror and no
mirror staircase trips in progress. Entry is temporarily denied during copying and
installation. If a player logs in during copying, replacement waits for them to leave.
The empty mirror is saved, closed, replaced, and recreated without a server restart.
Players reconnecting after a refresh are returned through the existing safe-return
search, rather than trusting their obsolete mirror logout position.

The initial world save/serialization and dimension lifecycle run on the server
thread. Incremental capture, sanitization and directory replacement run on a dedicated worker.
A write gate protects the source region files during copying; queued disk writes
resume afterward. Forced saves or chunk IO may wait, and a large world can still
cause a hitch. This is not a guarantee of completely pause-free copying.

**Only blocks are copied. Containers are empty.** Saved block-entity records retain their type and position so they load and render
correctly; other payload fields are cleared. This removes inventory
contents, loot tables, machine buffers and modded block-entity inventory attachments.
It also resets non-inventory block-entity details such as sign text, custom names,
banner patterns and machine configuration. Dropped items, mobs and other entities
are not copied, nor are player inventories, player saves or global level metadata.
POI files are copied to match the blocks. Ordinary new mob spawning still runs.

Each refresh replaces changes made inside the mirror. The last previous mirror is
retained under `<save>/mjontersstairs_mirror_snapshots/previous-mirror`; the active
copy lives under `dimensions/mjontersstairs/dimension_7`. Complete snapshots publish
through a pending pointer, and interrupted swaps can resume. Partial staging copies
never replace an active world. Retention is limited to the active mirror, previous
mirror and at most one pending complete copy, plus temporary work during capture.
No files under the original Overworld's region directory are replaced.

Protected, activated destination-2 staircase metadata is copied separately. Stair
travel also repairs the paired entrance, so stairs created after a snapshot still
provide a usable route. The snapshot system adds no entities or garage music.

## Visuals and transition

The Iteration patch adds gentler peripheral folds (roughly one third the garage's
offset) and the normal night sky with a faint localized drifting seam. The seam uses world-oriented view
directions and depth masking, so it stays in the sky when turning the camera.
Both respect Screen Effects. Normal landscape colors/lighting are preserved.
Complementary and unpatched shaders have no custom mirror visuals yet.

The existing pre-generation, black transfer cover and readiness fade are reused.
This preserves the location illusion but still includes a blackout; rendering new
chunks or compiling shaders may pause. Fully invisible uninterrupted dimension
streaming is not implemented. Shader appearance and actual travel need in-game testing.

## Checks

`./gradlew.bat build` runs the existing regression suite. For dedicated generation
and rotated staircase arrival checks, plus a live snapshot/empty-container check:

```powershell
python tools/prepare_garage_tests.py
./gradlew.bat -I build/garage-gametest.gradle runGameTestServer
```

The test world is isolated under `build/garage-gametest`.

The mirror sky stays at midnight regardless of Overworld time. Its fixed-time
dimension type also applies without Iris. Terrain lighting stays neutral; the localized seam
is a sky-only visual in the patched Iteration pack.

Mirror audio uses the optional Sound Physics Remastered integration: diffuse reverb,
softened high frequencies and gently reduced direct volume, with smooth entry/exit
fades. It affects environmental sounds, footsteps and mobs; music, records and voice
are excluded. Without Sound Physics Remastered, normal sound playback remains.

## Incremental capture

The first capture after this update builds a separate clean baseline under
`mjontersstairs_mirror_snapshots/baseline`. Subsequent captures compare region-file
size and full-resolution modification times. Unchanged region files are skipped;
changed regions are scanned using per-chunk SHA-256 fingerprints, and only changed
chunks are rewritten and sanitized. This also handles POI chunks, newly generated
chunks and removed chunks/regions. Saved simulation metadata counts as a change,
not only player block edits. External editors must update file modification times.

The baseline and fingerprints persist across restarts. An interrupted baseline update
is discarded and rebuilt on the next attempt. A complete, independent copy of the
baseline is still made for installation, so mirror-side builds and inventories cannot
contaminate the cache. No hard links are used. This reduces source scanning and
sanitization work, but installation still copies the complete cached world and the
baseline uses additional disk space. The server log reports regions reused and chunks
updated for each capture. Existing snapshot commands and the daily schedule are unchanged.
