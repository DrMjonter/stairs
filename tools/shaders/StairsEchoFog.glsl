#ifndef STAIRS_ECHO_FOG
#define STAIRS_ECHO_FOG
#include "/Lib/StairsEchoBasin.glsl"

// World-space cloud pockets: camera movement does not move the fog with the player.
float StairsEchoFogNoise(vec3 position) {
    vec3 cell = floor(position);
    vec3 blend = fract(position);
    blend = blend * blend * (3.0 - 2.0 * blend);
    vec2 uv = cell.xy + vec2(17.0 * cell.z) + blend.xy;
    vec2 slices = textureLod(noisetex, (uv + 0.5) / 64.0, 0.0).zw;
    return mix(slices.x, slices.y, blend.z);
}

float StairsEchoFogDensity(vec3 position) {
    vec3 drift = vec3(-1.0, -0.08, 0.6) * frameTimeCounter * 0.003;
    vec3 samplePos = position * 0.035 + drift;
    float cloud = StairsEchoFogNoise(samplePos) * 0.75;
    cloud += StairsEchoFogNoise(samplePos * 2.8 - drift) * 0.25;
    cloud = smoothstep(0.20, 0.75, cloud);
    return 0.0055 + 0.030 * cloud;
}

void StairsEchoVolumetricFog(inout vec3 color, vec3 worldDir, float distanceToSurface) {
    float strength = clamp(stairsEchoBasin, 0.0, 1.0);
    if (strength < 0.001 || distanceToSurface <= 0.0) return;
    // Fog is already opaque at long range; bounded marching avoids wasted distant work.
    const int steps = 12;
    float rayLength = min(distanceToSurface, 128.0);
    float stepLength = rayLength / float(steps);
    #ifdef TAA
        float jitter = BlueNoiseTemproal().y;
    #else
        float jitter = BlueNoise().y;
    #endif
    vec3 origin = cameraPosition + gbufferModelViewInverse[3].xyz;
    float transmission = 1.0;
    for (int sampleIndex = 0; sampleIndex < steps; sampleIndex++) {
        float sampleDistance = (float(sampleIndex) + jitter) * stepLength;
        float density = StairsEchoFogDensity(origin + worldDir * sampleDistance);
        transmission *= exp(-density * stepLength * strength);
    }
    vec3 scatterColor = NetherFogColor().rgb * 0.016;
    color = color * transmission + scatterColor * (1.0 - transmission);
}
#endif
