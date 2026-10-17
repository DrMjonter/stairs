"""Generate plastic block assets from the project's Minecraft 1.21.1 resources.

Existing PNGs are never overwritten. Vanilla PNGs provide temporary art until
custom painted-plastic textures are supplied. No changes to village generation.
"""
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
ASSETS = RES / 'assets/mjontersstairs'
MAPPING = {
    'oak_planks': 'plastic_planks',
    'oak_log': 'plastic_log', 'oak_wood': 'plastic_wood',
    'stripped_oak_log': 'stripped_plastic_log', 'stripped_oak_wood': 'stripped_plastic_wood',
    **{'oak_' + part: 'plastic_' + part for part in
       ('stairs', 'slab', 'fence', 'fence_gate', 'button', 'pressure_plate', 'door', 'trapdoor', 'leaves')},
    **{base + suffix: replacement + suffix
       for base, replacement in (('cobblestone', 'plastic_cobblestone'),
                                 ('mossy_cobblestone', 'mossy_plastic_cobblestone'))
       for suffix in ('', '_stairs', '_slab', '_wall')},
}
TEXTURES = {
    'oak_planks': 'plastic_planks',
    'oak_log': 'plastic_log', 'oak_log_top': 'plastic_log_top',
    'stripped_oak_log': 'stripped_plastic_log', 'stripped_oak_log_top': 'stripped_plastic_log_top',
    'cobblestone': 'plastic_cobblestone', 'mossy_cobblestone': 'mossy_plastic_cobblestone',
    'oak_door_bottom': 'plastic_door_bottom', 'oak_door_top': 'plastic_door_top',
    'oak_trapdoor': 'plastic_trapdoor', 'oak_leaves': 'plastic_leaves',
}

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')

with zipfile.ZipFile(ROOT / 'build/moddev/artifacts/neoforge-21.1.250-client-extra-aka-minecraft-resources.jar') as jar:
    def read(path):
        return json.loads(jar.read(path))

    models = set()

    def convert(value):
        if isinstance(value, dict):
            return {key: convert(item) for key, item in value.items()}
        if isinstance(value, list):
            return [convert(item) for item in value]
        if not isinstance(value, str):
            return value
        raw = value.removeprefix('minecraft:')
        if raw in MAPPING:
            return 'mjontersstairs:' + MAPPING[raw]
        if raw.startswith('block/'):
            name = raw[6:]
            # Model references need suffixes such as _inner, _top and _inventory.
            for old in sorted(MAPPING, key=len, reverse=True):
                if name == old or name.startswith(old + '_'):
                    source = 'assets/minecraft/models/block/' + name + '.json'
                    if source in jar.namelist():
                        models.add(name)
                        return 'mjontersstairs:block/' + MAPPING[old] + name[len(old):]
            if name in TEXTURES:
                return 'mjontersstairs:block/' + TEXTURES[name]
        if raw == 'item/oak_door':
            return 'mjontersstairs:item/plastic_door'
        return value

    for old, new in MAPPING.items():
        if old == 'oak_leaves':
            state = {'variants': {'': {'model': 'mjontersstairs:block/plastic_leaves'}}}
            models.add(old)
        else:
            state = convert(read('assets/minecraft/blockstates/' + old + '.json'))
        write(ASSETS / 'blockstates' / (new + '.json'), state)
        write(ASSETS / 'models/item' / (new + '.json'),
              convert(read('assets/minecraft/models/item/' + old + '.json')))
        if old == 'oak_leaves':
            loot = {'type': 'minecraft:block', 'pools': [{'rolls': 1,
                'entries': [{'type': 'minecraft:item', 'name': 'mjontersstairs:plastic_leaves'}],
                'conditions': [{'condition': 'minecraft:survives_explosion'}]}]}
        else:
            loot = convert(read('data/minecraft/loot_table/blocks/' + old + '.json'))
        write(RES / 'data/mjontersstairs/loot_table/blocks' / (new + '.json'), loot)

    done = set()
    while models - done:
        name = sorted(models - done)[0]
        model = read('assets/minecraft/models/block/' + name + '.json')
        # Textures are sprite references, not model references.
        textures = model.pop('textures', None)
        result = convert(model)
        if textures:
            result['textures'] = {key: ('mjontersstairs:block/' + TEXTURES[value.removeprefix('minecraft:').removeprefix('block/')]
                if value.removeprefix('minecraft:').removeprefix('block/') in TEXTURES else value)
                for key, value in textures.items()}
        if name.startswith(('oak_door', 'oak_trapdoor', 'oak_leaves')):
            result['render_type'] = 'minecraft:cutout_mipped'
        target = convert('minecraft:block/' + name).split('block/')[1]
        write(ASSETS / 'models/block' / (target + '.json'), result)
        done.add(name)

    for old, new in TEXTURES.items():
        dest = ASSETS / 'textures/block' / (new + '.png')
        dest.parent.mkdir(parents=True, exist_ok=True)
        if not dest.exists():
            dest.write_bytes(jar.read('assets/minecraft/textures/block/' + old + '.png'))
    dest = ASSETS / 'textures/item/plastic_door.png'
    dest.parent.mkdir(parents=True, exist_ok=True)
    if not dest.exists():
        dest.write_bytes(jar.read('assets/minecraft/textures/item/oak_door.png'))

    def tag_blocks(tag):
        blocks = set()
        for entry in read('data/minecraft/tags/block/' + tag + '.json')['values']:
            entry = entry if isinstance(entry, str) else entry['id']
            if entry.startswith('#minecraft:'):
                blocks.update(tag_blocks(entry.removeprefix('#minecraft:')))
            elif not entry.startswith('#'):
                blocks.add(entry.removeprefix('minecraft:'))
        return blocks

    # Preserve Minecraft's shape/connectivity and mining-tool classification.
    for tag in ('mineable/axe', 'mineable/pickaxe', 'fences', 'wooden_fences',
                'fence_gates', 'walls', 'stairs', 'slabs', 'doors', 'trapdoors',
                'buttons', 'pressure_plates'):
        vanilla = tag_blocks(tag)
        entries = ['mjontersstairs:' + new for old, new in MAPPING.items()
                   if 'minecraft:' + old in vanilla or old in vanilla]
        # Several general tags reference other tags rather than listing oak directly.
        suffix = {'doors': 'door', 'trapdoors': 'trapdoor', 'buttons': 'button',
                  'pressure_plates': 'pressure_plate', 'fences': 'fence'}.get(tag)
        if suffix:
            entries += ['mjontersstairs:plastic_' + suffix]
        if entries:
            path = RES / 'data/minecraft/tags/block' / (tag + '.json')
            existing = json.loads(path.read_text()) if path.exists() else {'replace': False, 'values': []}
            existing['values'] = sorted(set(existing['values'] + entries))
            write(path, existing)

lang_path = ASSETS / 'lang/en_us.json'
lang = json.loads(lang_path.read_text())
for name in MAPPING.values():
    lang['block.mjontersstairs.' + name] = name.replace('_', ' ').title()
write(lang_path, lang)
print(f'Generated {len(MAPPING)} plastic blocks and {len(done)} block models; existing textures preserved.')
