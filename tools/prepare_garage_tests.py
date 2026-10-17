"""Prepare an isolated GameTest world whose flat preset includes the garage."""
import json
import argparse
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--directory", default="garage-gametest")
args = parser.parse_args()
if not args.directory.replace("-", "").replace("_", "").isalnum():
    parser.error("directory must be a simple build subdirectory name")

root = Path(__file__).resolve().parents[1]
build = root / "build"
pack = build / args.directory / "world/datapacks/garage-test"
pack.mkdir(parents=True, exist_ok=True)
(pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 48, "description": "Garage test preset"}}))
preset = pack / "data/minecraft/worldgen/world_preset/flat.json"
preset.parent.mkdir(parents=True, exist_ok=True)
garage = json.loads((root / "src/main/resources/data/mjontersstairs/dimension/dimension_1.json").read_text())
preset.write_text(json.dumps({"dimensions": {
    "minecraft:overworld": {"type": "minecraft:overworld", "generator": {
        "type": "minecraft:flat", "settings": {"biome": "minecraft:plains", "lakes": False,
        "features": False, "structure_overrides": [], "layers": [
            {"height": 1, "block": "minecraft:bedrock"}, {"height": 2, "block": "minecraft:dirt"},
            {"height": 1, "block": "minecraft:grass_block"}]}}},
    "mjontersstairs:dimension_1": garage,
    "mjontersstairs:dimension_3": json.loads((root / "src/main/resources/data/mjontersstairs/dimension/dimension_3.json").read_text()),
    "mjontersstairs:dimension_7": json.loads((root / "src/main/resources/data/mjontersstairs/dimension/dimension_7.json").read_text())}}, indent=2))
(build / "garage-gametest.gradle").write_text("""allprojects {
    afterEvaluate {
        neoForge.runs.named('gameTestServer') {
            gameDirectory = project.file('build/garage-gametest')
        }
    }
}
""".replace("build/garage-gametest", "build/" + args.directory))
print("Prepared isolated garage GameTest world.")
