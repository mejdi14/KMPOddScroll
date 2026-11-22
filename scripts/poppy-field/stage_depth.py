import numpy as np, time
from PIL import Image
from common import *
t0=time.time()
H=height_grid(); print('height', H.min(), H.max(), time.time()-t0)
img=np.clip(H/3.0,0,1)
c=lambda v:int((v+HEXT)/HSTEP)
Image.fromarray((img[c(-110):c(110),c(-110):c(110)]*255).astype(np.uint8)).resize((660,660)).save(f'{WORK}/heights.png')
# depth of each painting pixel, every 2 px, from the entry eye
ys,xs=np.mgrid[0:PH:2,0:PW:2]
d=pixel_ray(xs+1.0,ys+1.0).reshape(-1,3)
t0=time.time(); hit=raycast(E0,d); print('raycast',time.time()-t0)
dep=hit.reshape(ys.shape)
np.save(f'{WORK}/paint_depth.npy',dep)
ids=region_ids()[1::2,1::2]
g=ids==ID_GROUND
print('ground px with no hit', (g & ~np.isfinite(dep)).mean()/g.mean(), ' sky px with a hit', ((ids==ID_SKY)&np.isfinite(dep)).mean())
v=np.where(np.isfinite(dep),np.log(dep)/np.log(400),1)
Image.fromarray((np.clip(v,0,1)*255).astype(np.uint8)).save(f'{WORK}/paint_depth.png')
