// Celestial silhouette for IterationT's deferred sky pass, BEFORE cloud composition.
uniform float stairsOrbOpacity;
uniform float stairsOrbRadius;
uniform float stairsOrbCoverage;

float StairsOrbMask(vec3 direction) {
    vec3 center = normalize(vec3(0.6, 0.22, -0.77));
    float cosine = dot(direction, center);
    float radius = stairsOrbRadius / 100.0;
    float boundary = inversesqrt(1.0 + radius * radius);
    float coverage = clamp(stairsOrbCoverage, 0.0, 1.0);
    if (coverage >= 0.999999) return clamp(stairsOrbOpacity, 0.0, 1.0);
    boundary = mix(boundary, -1.0, coverage);
    float edge = max(fwidth(cosine), 0.000001);
    return smoothstep(boundary - edge, boundary + edge, cosine)
            * clamp(stairsOrbOpacity, 0.0, 1.0);
}
