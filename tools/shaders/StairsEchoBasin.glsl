#ifndef STAIRS_ECHO_BASIN
#define STAIRS_ECHO_BASIN
uniform float stairsEchoBasin;
uniform float stairsEchoSubmerged;

vec3 StairsEchoBlocklight(vec3 normalColor) {
    float luminance = dot(normalColor, vec3(0.2126, 0.7152, 0.0722));
    return mix(normalColor, vec3(0.40, 0.55, 1.0) * (luminance / 0.55063),
            clamp(stairsEchoBasin, 0.0, 1.0));
}
#endif
