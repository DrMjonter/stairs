Add these two Vorbis .ogg files here:

orb_ambience.ogg
  - Mono (required for directional spatial audio)
  - Seamless loop
  - Recommended length: 45-60 seconds
  - Keep the file itself at a healthy level; code makes it nearly inaudible at noon
    and smoothly raises it to maximum at midnight.

orb_expansion.ogg
  - Mono (required for directional spatial audio)
  - Seamless loop with an immediate audible onset (no long fade-in)
  - Recommended length: 4-6 seconds
  - Code plays only from 11:30 PM to midnight: 25 seconds on a normal day and
    0.625 seconds on a 40x day, stopping the loop exactly at midnight.

orb_stinger_01.ogg through orb_stinger_04.ogg
  - Mono (required for directional spatial audio)
  - One-shot sounds; do not make them loop seamlessly
  - Recommended length: 2-8 seconds each
  - Give each file an immediate onset and leave any desired reverb tail in the file
  - One random variant plays after an initial 1-3 minute delay, then every 2-5
    minutes. Its code-driven volume is restrained during the day and strongest
    at midnight. Supply all four files so random selection never chooses a gap.

orb_boom.ogg
  - Mono, one-shot sound
  - Plays from the orb on the exact client tick that midnight distortion begins
  - Uses a 192-block attenuation range and the dedicated orb reverb profile
