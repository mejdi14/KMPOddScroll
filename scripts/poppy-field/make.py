"""Builds Poppy Field's walk-in scene, every step or the ones named, and puts it in the app's resources.

    python3 scripts/poppy-field/make.py                  # all of it
    python3 scripts/poppy-field/make.py sky pack install # just the sky again
"""
import os
import shutil
import subprocess
import sys
import time

from common import REPO, RES, SRC, WORK

# The first walk-in Poppy Field, and the painting before its edges were worn: what this starts from.
FROM = '80f1ea1'
SOURCES = {
    'museum_poppies.jpg': 'drawable/museum_poppies.jpg',
    'museum_poppies_scene.bin': 'files/museum_poppies_scene.bin',
    'museum_poppies_far.webp': 'drawable/museum_poppies_far.webp',
    'museum_poppies_ground.webp': 'drawable/museum_poppies_ground.webp',
    'museum_poppies_band.webp': 'drawable/museum_poppies_band.webp',
    'museum_poppies_cards.webp': 'drawable/museum_poppies_cards.webp',
}
OUTPUTS = {
    'museum_poppies_far.webp': 'drawable', 'museum_poppies_ground.webp': 'drawable',
    'museum_poppies_paint.webp': 'drawable', 'museum_poppies_band.webp': 'drawable',
    'museum_poppies_cards.webp': 'drawable', 'museum_poppies_scene.bin': 'files',
}
HERE = os.path.dirname(os.path.abspath(__file__))


def fetch():
    for name, path in SOURCES.items():
        with open(os.path.join(SRC, name), 'wb') as out:
            subprocess.run(['git', '-C', REPO, 'show', f'{FROM}:shared/src/commonMain/composeResources/{path}'], stdout=out, check=True)


def script(name, *args):
    def run():
        subprocess.run([sys.executable, '-W', 'ignore', os.path.join(HERE, name), *args], cwd=HERE, check=True)
    return run


def height():
    cached = os.path.join(WORK, 'height.npy')
    if os.path.exists(cached): os.remove(cached)
    script('height.py')()


def install():
    for name, folder in OUTPUTS.items():
        shutil.copyfile(os.path.join(WORK, 'out', name), os.path.join(RES, folder, name))


STEPS = {
    'fetch': fetch,                         # the sources, out of git
    'regions': script('regions.py'),        # which of the painting is sky, ground, trees and figures
    'height': height,                       # the first scene's ground, then the new one
    'depth': script('stage_depth.py'),      # how far off each of the painting's pixels is
    'ground': script('stage_ground.py'),    # the field, about two and a half minutes
    'sky': script('stage_sky.py'),
    'band': script('stage_band.py'),        # the far tree line
    'cards': script('stage_cards.py'),      # trees, figures and the mill with its sails
    'decal': script('stage_decal.py'),      # the painting laid back over sky and ground
    'pack': script('build.py', 'tex'),      # webp textures and the scene file, in work/out
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
