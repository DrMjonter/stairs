"""Create a local Stairs test copy of IterationT 3.2.0 without modifying the original."""
from pathlib import Path
import argparse
import zipfile
import shutil


def patch_echo_basin(original):
    """Dimension-scoped fog/light changes; keep real Nether and other dimensions intact."""
    programs = {}
    def rewrite(path, replacements):
        value = original.read(path).decode("utf-8")
        for before, after in replacements.items():
            if value.count(before) != 1:
                raise ValueError("Unexpected Echo Basin patch target: " + path + ": " + before)
            value = value.replace(before, after, 1)
        programs[path] = value.encode("utf-8")

    rewrite("shaders/Lib/BasicFounctions/NetherColor.glsl", {
        "uniform float BiomeNetherWastesSmooth;":
            '#include "/Lib/StairsEchoBasin.glsl"\n\nuniform float BiomeNetherWastesSmooth;',
        "return netherLighting * NETHER_BRIGHTNESS;":
            "return mix(netherLighting, vec3(0.28, 0.32, 1.0) * 5.5e-4, clamp(stairsEchoBasin, 0.0, 1.0)) * NETHER_BRIGHTNESS;",
        "return fog * vec4(vec3(NETHER_BRIGHTNESS), NETHERFOG_DENSITY);":
            "return mix(fog, vec4(0.12, 0.09, 0.35, 0.018), clamp(stairsEchoBasin, 0.0, 1.0)) * vec4(vec3(NETHER_BRIGHTNESS), NETHERFOG_DENSITY);",
    })
    rewrite("shaders/Lib/BasicFounctions/VanillaComposite.glsl", {
        "void VanillaFog(inout vec3 color, float dist){":
            '#include "/Lib/StairsEchoBasin.glsl"\n\nvoid VanillaFog(inout vec3 color, float dist){',
        "if (isEyeInWater == 2) color = mix(color, vec3(0.3721, 0.0775, 0.0024) * TORCHLIGHT_BRIGHTNESS, smoothstep(0.0, 1.0, dist));":
            "if (stairsEchoSubmerged > 0.5) color = mix(color, vec3(0.025, 0.065, 0.32) * TORCHLIGHT_BRIGHTNESS, smoothstep(0.0, 1.5, dist));\n"
            "\telse if (isEyeInWater == 2) color = mix(color, vec3(0.3721, 0.0775, 0.0024) * TORCHLIGHT_BRIGHTNESS, smoothstep(0.0, 1.0, dist));",
    })
    lighting_path = "shaders/Lib/BasicFounctions/Blocklight.glsl"
    rewrite("shaders/world-1/composite5.fsh", {
        '#include "/Lib/BasicFounctions/NetherColor.glsl"':
            '#include "/Lib/BasicFounctions/NetherColor.glsl"\n#include "/Lib/StairsEchoFog.glsl"',
        "\tcolor += NetherFog(waterDist);":
            "\tif (stairsEchoBasin > 0.001 && isEyeInWater == 0 && stairsEchoSubmerged < 0.5)\n"
            "\t\tStairsEchoVolumetricFog(color, worldDir, waterDist);\n"
            "\telse\n\t\tcolor += NetherFog(waterDist);",
    })
    lighting = original.read(lighting_path).decode("utf-8")
    if "StairsEcho" in lighting or "colorTorchlight" not in lighting:
        raise ValueError("Unexpected block lighting layout")
    programs[lighting_path] = ('#include "/Lib/StairsEchoBasin.glsl"\n' +
            lighting.replace("colorTorchlight", "StairsEchoBlocklight(colorTorchlight)")).encode("utf-8")
    blocks = original.read("shaders/block.properties").decode("utf-8")
    if blocks.count("block.10 = lava") != 2:
        raise ValueError("Unexpected lava block mapping")
    if blocks.count("block.89 = glowstone") != 2:
        raise ValueError("Unexpected glowstone block mapping")
    programs["shaders/block.properties"] = blocks.replace(
            "block.10 = lava", "block.10 = lava mjontersstairs:echo_lava").replace(
            # The normal lightmap handles levels 1-14. Only the maximum-power state
            # receives IterationT's full-bright Glowstone surface material.
            "block.89 = glowstone",
            "block.89 = glowstone mjontersstairs:echo_lantern:power=15").encode("utf-8")
    if "shaders/dimension.properties" in original.namelist():
        raise ValueError("Unexpected preexisting dimension mapping")
    programs["shaders/dimension.properties"] = (
        "# Echo Basin uses the roofed shader pipeline, with its own Stairs uniforms.\n"
        "dimension.world-1 = minecraft:the_nether mjontersstairs:dimension_3\n"
        "dimension.world1 = minecraft:the_end\n"
        "# Unlisted dimensions retain the original root (Overworld) programs.\n"
    ).encode("utf-8")
    programs["shaders/Lib/StairsEchoBasin.glsl"] = (
            Path(__file__).parent / "shaders/StairsEchoBasin.glsl").read_bytes()
    programs["shaders/Lib/StairsEchoFog.glsl"] = (
            Path(__file__).parent / "shaders/StairsEchoFog.glsl").read_bytes()
    return programs


def patch(source, destination):
    source, destination = Path(source), Path(destination)
    if source.resolve() == destination.resolve():
        raise ValueError("The patched pack must have a different path")
    effect = (Path(__file__).parent / "shaders/StairsAscent.glsl").read_bytes()
    orb = (Path(__file__).parent / "shaders/StairsOrb.glsl").read_bytes()
    target = "shaders/Lib/Programs/Final.glsl"
    with zipfile.ZipFile(source) as original:
        program = original.read(target).decode("utf-8")
        marker = "#ifdef PROGRAM_FINAL_2"
        output = "\tcolor += InterleavedGradientNoise(gl_FragCoord.xy) * (1.0 / 255.0);"
        if program.count(marker) != 1 or program.count(output) != 1 or "stairsAscent" in program:
            raise ValueError("Unexpected shader layout; expected unmodified IterationT 3.2.0")
        program = program.replace(marker, marker + '\n#include "/Lib/StairsAscent.glsl"', 1)
        program = program.replace(output, "\tcolor = StairsAscentColor(color, texCoord);\n\n" + output, 1)
        sky_target = "shaders/deferred3.fsh"
        sky = original.read(sky_target).decode("utf-8")
        planar_target = "shaders/Lib/IndividualFounctions/PlanarClouds.glsl"
        planar = original.read(planar_target).decode("utf-8")
        planar_replacements = {
            "void PlanarClouds(inout vec3 color, vec3 worldDir, vec3 camera, float dither, float cloudTransmittance){":
                "void PlanarClouds(inout vec3 color, vec3 worldDir, vec3 camera, float dither, float cloudTransmittance, out float stairsPlanarCoverage){\n\tstairsPlanarCoverage = 0.0;",
            "\t\tcloudTransmittance *= exp2(-(length(centerPosition) + 2e3) * 4e-5);":
                "\t\tcloudTransmittance *= exp2(-(length(centerPosition) + 2e3) * 4e-5);\n\t\tstairsPlanarCoverage = cloudTransmittance;",
        }
        for before, after in planar_replacements.items():
            if planar.count(before) != 1:
                raise ValueError("Unexpected planar-cloud layout; expected unmodified IterationT 3.2.0")
            planar = planar.replace(before, after, 1)
        replacements = {
            '#include "/Lib/IndividualFounctions/PlanarClouds.glsl"':
                '#include "/Lib/IndividualFounctions/PlanarClouds.glsl"\n#include "/Lib/StairsOrb.glsl"',
            '\tif (depth == 1.0){\n\t\tvec3 finalComposite = vec3(0.0);':
                # Compute derivatives outside the non-uniform sky/terrain branch.
            '\tfloat stairsOrbMask = StairsOrbMask(worldDir);\n\n'
                '\tif (depth == 1.0){\n\t\tvec3 finalComposite = vec3(0.0);',
            '\t\tfinalComposite += atmosphere;\n\n\t\tfloat cloudTransmittance = 1.0;':
                '\t\tfinalComposite += atmosphere;\n\n\t\tfloat cloudTransmittance = 1.0;\n\t\tfloat stairsPlanarCoverage = 0.0;',
            '\t\t\t\tPlanarClouds(finalComposite, worldDir, camera, noise_1.y, cloudTransmittance);':
                '\t\t\t\tPlanarClouds(finalComposite, worldDir, camera, noise_1.y, cloudTransmittance, stairsPlanarCoverage);',
            '\t\tskyImage += atmosphere;\n\n\t\tfloat cloudTransmittance = 1.0;':
                '\t\tskyImage += atmosphere;\n\n\t\tfloat cloudTransmittance = 1.0;\n\t\tfloat stairsSkyPlanarCoverage = 0.0;',
            '\t\t\t\tPlanarClouds(skyImage, viewVector, cameraSkyBox, noise_0.x, cloudTransmittance);':
                '\t\t\t\tPlanarClouds(skyImage, viewVector, cameraSkyBox, noise_0.x, cloudTransmittance, stairsSkyPlanarCoverage);',
            '\n\n\t\tvec3 celestial = GammaToLinear':
                # Treat even moderately dense ray-marched clouds as foreground.
                # This is intentionally more decisive than physical transmission:
                # the enormous black disc otherwise reads as being painted over them.
                '\n\n\t\tfloat stairsVolumetricClear = smoothstep(0.90, 0.995, cloudTransmittance);\n'
                '\t\tfloat stairsPlanarClear = 1.0 - smoothstep(0.015, 0.16, stairsPlanarCoverage);\n'
                '\t\tfloat stairsVisibleOrb = stairsOrbMask * stairsVolumetricClear * stairsPlanarClear;\n'
                '\t\tfinalComposite *= 1.0 - stairsVisibleOrb;\n\n'
                '\t\tvec3 celestial = GammaToLinear',
            '\t\tfinalComposite += celestial * transmittance * 200.0;':
                '\t\tfinalComposite += celestial * transmittance * (200.0 * (1.0 - stairsVisibleOrb));',
        }
        for before, after in replacements.items():
            if sky.count(before) != 1:
                raise ValueError("Unexpected sky layout; expected unmodified IterationT 3.2.0")
            sky = sky.replace(before, after, 1)
        # Only the background radiance is attenuated. Both cloud functions keep
        # their original composition; don't darken the combined result again.
        programs = {target: program.encode("utf-8"), sky_target: sky.encode("utf-8"),
                    planar_target: planar.encode("utf-8")}
        programs.update(patch_echo_basin(original))
        destination.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED) as patched:
            for entry in original.infolist():
                data = programs[entry.filename] if entry.filename in programs else original.read(entry.filename)
                patched.writestr(entry, data)
            for name, data in programs.items():
                if name not in original.namelist():
                    patched.writestr(name, data)
            patched.writestr("shaders/Lib/StairsAscent.glsl", effect)
            patched.writestr("shaders/Lib/StairsOrb.glsl", orb)
    settings = Path(str(source) + ".txt")
    if settings.exists() and not Path(str(destination) + ".txt").exists():
        shutil.copyfile(settings, Path(str(destination) + ".txt"))
    print(f"Created {destination}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", nargs="?", default="run/shaderpacks/iterationT 3.2.0.zip")
    parser.add_argument("destination", nargs="?", default="run/shaderpacks/IterationT 3.2.0 - Stairs.zip")
    args = parser.parse_args()
    patch(args.source, args.destination)
