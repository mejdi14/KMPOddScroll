"""Builds Boys in a Pasture's walk-in scene, every step or the ones named, and puts it in the app's resources.

    python3 scripts/boys-in-a-pasture/make.py                   # all of it
    python3 scripts/boys-in-a-pasture/make.py band pack install # just the tree line again
"""
import os
import shutil
import subprocess
import sys
import time

from common import RES, WORK, NAME

OUTPUTS = {
    f'{NAME}.jpg': 'drawable', f'{NAME}_far.webp': 'drawable', f'{NAME}_ground.webp': 'drawable',
    f'{NAME}_paint.webp': 'drawable', f'{NAME}_band.webp': 'drawable', f'{NAME}_cards.webp': 'drawable',
    f'{NAME}_scene.bin': 'files',
}
HERE = os.path.dirname(os.path.abspath(__file__))


def script(name, *args):
    def run():
        subprocess.run([sys.executable, '-W', 'ignore', os.path.join(HERE, name), *args], cwd=HERE, check=True)
    return run


def install():
    for name, folder in OUTPUTS.items():
        shutil.copyfile(os.path.join(WORK, 'out', name), os.path.join(RES, folder, name))


STEPS = {
    'regions': script('regions.py'),        # the painting at size, and which of it is sky, field, far side, boys
    'ground': script('stage_ground.py'),    # the pasture, about two minutes
    'sky': script('stage_sky.py'),
    'band': script('stage_band.py'),        # the fence, hedges and trees 70 m out, after the ground
    'cards': script('stage_cards.py'),      # the boys and the shade trees
    'decal': script('stage_decal.py'),      # the painting laid back over sky and ground
    'pack': script('build.py', 'tex'),      # webp textures, the painting and the scene file, in work/out
    'install': install,                     # into shared/src/commonMain/composeResources
}

if __name__ == '__main__':
    names = sys.argv[1:] or list(STEPS)
    for n in names:
        if n not in STEPS:
            sys.exit(f'No step {n!r}; the steps are {", ".join(STEPS)}')
    for n in names:
        t = time.time()
        STEPS[n]()
        print(f'{n}: {time.time() - t:.0f}s', flush=True)
