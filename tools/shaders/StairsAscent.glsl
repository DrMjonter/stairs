// Stairs' original IterationT compatibility effect. Applied after exposure/tonemapping.
uniform float stairsAscent;
uniform float stairsGarage;
uniform float stairsMirror;
uniform float stairsTickstorm;
uniform float stairsTickstormSurge;
uniform float stairsMidnight;
uniform float stairsOrbReaction;
uniform float stairsDistortionScale;

float StairsViewDistance(vec2 uv) {
    float depth = textureLod(depthtex0, uv, 0.0).r;
    return (2.0 * near * far) / max(far + near - (depth * 2.0 - 1.0) * (far - near), 0.0001);
}

vec3 StairsSceneSample(vec2 uv) {
#if defined FSR && MC_VERSION >= 11801
    if (MC_RENDER_QUALITY < 1.0) return textureLod(colortex8, uv, 0.0).rgb;
#endif
    return textureLod(colortex1, uv, 0.0).rgb;
}

vec3 StairsTickstormColor(vec3 color, vec2 uv) {
    float dimension = clamp(stairsTickstorm, 0.0, 1.0);
    if (dimension <= 0.0) return color;
    float distanceToSurface = StairsViewDistance(uv);
    float haze = (1.0 - exp(-distanceToSurface * 0.010)) * dimension;
    color = mix(color, vec3(0.115, 0.025, 0.185), min(haze, 0.88));

    float surge = clamp(stairsTickstormSurge * stairsDistortionScale, 0.0, 1.0);
    if (surge <= 0.0) return color;
    float time = frameTimeCounter;
    vec2 centered = uv - 0.5;
    float row = floor(uv.y * 180.0);
    float tear = sin(row * 1.713 + floor(time * 14.0) * 2.17)
            * sin(row * 0.371 - floor(time * 9.0));
    float pulse = 0.55 + 0.45 * sin(time * 24.0 + length(centered) * 30.0);
    vec2 offset = vec2(tear * 0.009, sin(uv.x * 31.0 + time * 19.0) * 0.0025)
            * surge * pulse;
    vec2 warped = clamp(uv + offset, pixelSize, vec2(1.0) - pixelSize);
    vec2 split = vec2(0.0045 * surge * pulse, 0.0);
    vec3 shifted = StairsSceneSample(warped);
    shifted.r = StairsSceneSample(clamp(warped + split, pixelSize, vec2(1.0) - pixelSize)).r;
    shifted.b = StairsSceneSample(clamp(warped - split, pixelSize, vec2(1.0) - pixelSize)).b;
    color = mix(color, shifted, 0.48 * surge);
    color += vec3(0.10, 0.015, 0.16) * surge * (0.04 + 0.04 * pulse);
    return color;
}


vec3 StairsGarageColor(vec3 color, vec2 uv) {
    // Permanent peripheral folds, with a clear center and protected nearby geometry.
    float scale = clamp(stairsDistortionScale, 0.0, 1.0);
    vec2 centered = uv - 0.5;
    float peripheral = smoothstep(0.22, 0.48, max(abs(centered.x), abs(centered.y)));
    float strength = peripheral * scale * smoothstep(1.0, 4.0, StairsViewDistance(uv));
    float time = frameTimeCounter;
    float fold = sin(uv.y * 15.0 + sin(uv.x * 9.0 + time * 0.35) * 2.0 - time * 0.65);
    float tear = fold * fold * fold;
    vec2 offset = -centered * (0.12 + 0.035 * sin(time * 0.6 + length(centered) * 8.0));
    offset += vec2(-centered.y, centered.x) * (0.025 * sin(uv.y * 8.0 - time * 0.45));
    offset += vec2(tear * 0.018, sin(uv.x * 18.0 + time * 0.45) * 0.008);
    vec2 warpedUV = clamp(uv + offset * strength, pixelSize, vec2(1.0) - pixelSize);
    float warpBlend = strength * smoothstep(1.0, 3.0, StairsViewDistance(warpedUV));
    color = mix(color, StairsSceneSample(warpedUV), warpBlend);

    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(color, vec3(luma), 0.18) * 0.90;
    // Neutral grading retains the darkness without adding a blue/green cast.
    color = pow(max(color, vec3(0.0)), vec3(1.35)) * 0.76;
    float viewDistance = mix(StairsViewDistance(uv), StairsViewDistance(warpedUV), warpBlend);
    float distanceFog = 1.0 - exp(-viewDistance * 0.022);
    color = mix(color, vec3(0.016), min(distanceFog, 0.94));
    vec2 edge = (uv - 0.5) * vec2(aspectRatio, 1.0);
    color *= 1.0 - 0.32 * smoothstep(0.2, 0.85, length(edge));
    return color;
}

vec3 StairsMirrorColor(vec3 color, vec2 uv) {
    float scale = clamp(stairsDistortionScale, 0.0, 1.0);
    vec2 centered = uv - 0.5;
    float edge = smoothstep(0.24, 0.49, max(abs(centered.x), abs(centered.y)));
    float strength = edge * scale * smoothstep(1.0, 4.0, StairsViewDistance(uv));
    float time = frameTimeCounter;
    float fold = sin(uv.y * 15.0 + sin(uv.x * 9.0 + time * 0.3) * 2.0 - time * 0.5);
    vec2 offset = -centered * (0.045 + 0.012 * sin(time * 0.4 + length(centered) * 8.0));
    offset += vec2(fold * fold * fold * 0.006, sin(uv.x * 18.0 + time * 0.35) * 0.003);
    vec2 warpedUV = clamp(uv + offset * strength, pixelSize, vec2(1.0) - pixelSize);
    float blend = strength * smoothstep(1.0, 3.0, StairsViewDistance(warpedUV));
    color = mix(color, StairsSceneSample(warpedUV), blend);

    // World-oriented bands: they stay in the sky when the camera turns.
    float sky = step(0.999999, textureLod(depthtex0, uv, 0.0).r)
            * step(0.999999, textureLod(depthtex0, warpedUV, 0.0).r);
    vec4 view = gbufferProjectionInverse * vec4(uv * 2.0 - 1.0, 1.0, 1.0);
    vec3 direction = normalize(mat3(gbufferModelViewInverse) * view.xyz);
    // Preserve the normal night sky; only a small moving patch is disturbed.
    float drift = time * 0.025;
    vec3 center = normalize(vec3(0.6 + 0.06 * sin(drift), 0.65, -0.45));
    float patch = smoothstep(0.86, 0.98, dot(direction, center));
    float seam = direction.y - 0.58 + 0.045 * sin(direction.x * 10.0 + drift)
            + 0.025 * sin(direction.z * 15.0 - drift * 0.7);
    float rim = exp(-abs(seam) * 180.0) * patch * scale;
    float hollow = exp(-abs(seam + 0.016) * 65.0) * patch * scale;
    color *= 1.0 - 0.25 * hollow * sky;
    color += vec3(0.012, 0.013, 0.015) * rim * sky;
    return color;
}

vec3 StairsMidnightColor(vec3 color, vec2 uv) {
    float amount = clamp(stairsMidnight * stairsDistortionScale, 0.0, 1.0);
    if (amount <= 0.0) return color;
    vec2 centered = uv - 0.5;
    float wave = sin(length(centered * vec2(aspectRatio, 1.0)) * 42.0 - frameTimeCounter * 18.0);
    vec2 bend = centered * (0.10 * wave * amount);
    bend += vec2(-centered.y, centered.x) * (0.045 * amount);
    vec2 warped = clamp(uv + bend, pixelSize, vec2(1.0) - pixelSize);
    vec2 split = normalize(centered + vec2(0.0001)) * 0.018 * amount;
    vec3 torn = StairsSceneSample(warped);
    torn.r = StairsSceneSample(clamp(warped + split, pixelSize, vec2(1.0) - pixelSize)).r;
    torn.b = StairsSceneSample(clamp(warped - split, pixelSize, vec2(1.0) - pixelSize)).b;
    return mix(color, torn, smoothstep(0.0, 0.15, amount));
}

vec3 StairsAscentColor(vec3 color, vec2 uv) {
    color = StairsTickstormColor(color, uv);
    color = StairsMidnightColor(color, uv);
    float reaction = clamp(stairsOrbReaction * stairsDistortionScale, 0.0, 1.0);
    if (reaction > 0.0) {
        vec2 centered = uv - 0.5;
        float edge = smoothstep(0.24, 0.49, max(abs(centered.x), abs(centered.y)));
        float pulse = reaction * edge * (0.65 + 0.35 * cos(frameTimeCounter * 18.0));
        vec2 offset = centered * (sin(length(centered) * 24.0 - frameTimeCounter * 18.0) * 0.035 * pulse);
        vec2 warped = clamp(uv + offset, pixelSize, vec2(1.0) - pixelSize);
        vec2 split = centered * 0.006 * pulse;
        vec3 torn = StairsSceneSample(warped);
        torn.r = StairsSceneSample(clamp(warped + split, pixelSize, vec2(1.0) - pixelSize)).r;
        torn.b = StairsSceneSample(clamp(warped - split, pixelSize, vec2(1.0) - pixelSize)).b;
        color = mix(color, torn, pulse);
    }
    if (stairsGarage > 0.5) return StairsGarageColor(color, uv);
    if (stairsMirror > 0.5) color = StairsMirrorColor(color, uv);
    float amount = clamp(stairsAscent, 0.0, 1.0);
    if (amount <= 0.0) return color;
    float distortion = smoothstep(0.05, 0.70, amount) * clamp(stairsDistortionScale, 0.0, 1.0);
    vec2 centered = uv - 0.5;
    float radius = length(centered * vec2(aspectRatio, 1.0));
    float peripheral = smoothstep(0.08, 0.38, radius);
    float distant = smoothstep(3.0, 12.0, StairsViewDistance(uv));
    float strength = distortion * peripheral * distant;
    // Pull the periphery toward the center, with uneven shear instead of camera sway.
    // Smooth traveling folds give the scene a stretched, tearing appearance.
    float time = frameTimeCounter;
    float fold = sin(uv.y * 15.0 + sin(uv.x * 9.0 + time * 0.55) * 2.0 - time * 1.1);
    float tear = fold * fold * fold;
    float pull = 0.52 + 0.08 * sin(time * 0.9 + radius * 8.0);
    vec2 offset = -centered * pull;
    offset += vec2(-centered.y, centered.x) * (0.12 * sin(radius * 10.0 - time));
    offset += vec2(tear * 0.065, sin(uv.x * 21.0 + time * 0.7) * 0.025);
    vec2 warpedUV = clamp(uv + offset * strength, pixelSize, vec2(1.0) - pixelSize);
    // Keep immediate foreground silhouettes out of the background tear.
    float safeSample = smoothstep(3.0, 7.0, StairsViewDistance(warpedUV));
    float warpBlend = smoothstep(0.0, 0.10, strength) * safeSample;
    if (warpBlend > 0.0) {
        vec2 split = (centered * 0.022 + vec2(tear * 0.009, 0.0)) * strength;
        vec2 redUV = clamp(warpedUV + split, pixelSize, vec2(1.0) - pixelSize);
        vec2 blueUV = clamp(warpedUV - split, pixelSize, vec2(1.0) - pixelSize);
        vec3 torn = StairsSceneSample(warpedUV);
        float splitSafe = smoothstep(3.0, 7.0,
                min(StairsViewDistance(redUV), StairsViewDistance(blueUV)));
        torn.r = mix(torn.r, StairsSceneSample(redUV).r, splitSafe);
        torn.b = mix(torn.b, StairsSceneSample(blueUV).b, splitSafe);
        color = mix(color, torn, warpBlend);
    }
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    vec3 cold = mix(color, vec3(luma), 0.55 * amount);
    cold *= mix(vec3(1.0), vec3(0.78, 0.90, 1.02), amount);
    // The depth buffer distinguishes distant sky from nearby solid surfaces.
    // No world time, weather, block light, or other players' lighting is changed.
    float depth = mix(textureLod(depthtex0, uv, 0.0).r, textureLod(depthtex0, warpedUV, 0.0).r, warpBlend);
    float sky = step(0.999999, depth);
    float skyFade = smoothstep(0.15, 0.9, amount);
    cold *= mix(1.0 - 0.08 * amount, 0.10, sky * skyFade);
    return max(cold, vec3(0.0));
}
