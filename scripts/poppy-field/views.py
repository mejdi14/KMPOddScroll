import sys, math, numpy as np
from PIL import Image
import render as R
from scene import load
from common import WORK
OUT=f'{WORK}/out'
def texs():
    return [np.asarray(Image.open(f'{OUT}/museum_poppies_{n}.webp').convert('RGBA')).astype(np.float32)/255 for n in ['far','ground','paint','band','cards']]
if __name__=='__main__':
    sc=load(f'{OUT}/museum_poppies_scene.bin'); T=texs()
    tag=sys.argv[1]
    views=[tuple(map(float,v.split(','))) for v in sys.argv[2:]]
    ims=[]
    for route, yaw, pitch, *seconds in views:
        im,_=R.render(sc,T,W=390,H=844,route=route,yaw=yaw,pitch=pitch,time=seconds[0] if seconds else 0)
        ims.append(Image.fromarray(im))
    W=sum(i.width for i in ims)+10*(len(ims)-1)
    s=Image.new('RGB',(W,ims[0].height),'white'); x=0
    for i in ims: s.paste(i,(x,0)); x+=i.width+10
    s.save(f'{WORK}/{tag}.png')
