import numpy as np, math
from PIL import Image
from scene import load
from render import textures
from common import SRC, WORK
import render as R
# render the current scene at the painting's own size from the entry pose, with a layer id per pixel
hdr,route,layers=load(f'{SRC}/museum_poppies_scene.bin')
texs=textures(['far','ground','band','cards'])
W,H=1200,800
cx,cy=600,400
focal=600/math.tan(math.radians(hdr['hFov'])/2)
eye=route[0]
tilt=math.radians(hdr['tilt'])
F=np.array([0,math.sin(tilt),-math.cos(tilt)]); Rv=np.array([1,0,0]); U=np.array([0,math.cos(tilt),math.sin(tilt)])
img=np.zeros((H,W,3),np.float32); ids=np.full((H,W),-1,np.int8)
ys,xs=np.mgrid[0:H,0:W]
for li,L in enumerate(layers):
    tex=texs[L['tex']]; sky=L['kind']==0
    e=np.zeros(3) if sky else eye; clip=1e-3 if sky else 0.05
    d=L['pos']-e; zc=d@F; zz=np.maximum(zc,clip)
    sx=cx+focal*(d@Rv)/zz; sy=cy-focal*(d@U)/zz; uv=L['uv']
    for a,b,c in L['idx']:
        if zc[a]<clip or zc[b]<clip or zc[c]<clip: continue
        x0,y0,x1,y1,x2,y2=sx[a],sy[a],sx[b],sy[b],sx[c],sy[c]
        area=(x1-x0)*(y2-y0)-(x2-x0)*(y1-y0)
        if L['kind']==1 and area>0: continue
        if abs(area)<1e-9: continue
        bx0=max(int(math.floor(min(x0,x1,x2))),0); bx1=min(int(math.ceil(max(x0,x1,x2))),W-1)
        by0=max(int(math.floor(min(y0,y1,y2))),0); by1=min(int(math.ceil(max(y0,y1,y2))),H-1)
        if bx0>bx1 or by0>by1: continue
        px=xs[by0:by1+1,bx0:bx1+1]+0.5; py=ys[by0:by1+1,bx0:bx1+1]+0.5
        w1=((px-x0)*(y2-y0)-(x2-x0)*(py-y0))/area; w2=((x1-x0)*(py-y0)-(px-x0)*(y1-y0))/area; w0=1-w1-w2
        m=(w0>=0)&(w1>=0)&(w2>=0)
        if not m.any(): continue
        u=w0*uv[a,0]+w1*uv[b,0]+w2*uv[c,0]; v=w0*uv[a,1]+w1*uv[b,1]+w2*uv[c,1]
        col=R.sample(tex,u[m],v[m])
        al=col[:,3:4]
        reg=img[by0:by1+1,bx0:bx1+1]; reg[m]=reg[m]*(1-al)+col[:,:3]*al
        ir=ids[by0:by1+1,bx0:bx1+1]; sel=ir[m]; sel[al[:,0]>0.5]=li; ir[m]=sel
np.save(f'{WORK}/ids.npy',ids)
Image.fromarray((np.clip(img,0,1)*255).astype(np.uint8)).save(f'{WORK}/entry_render.png')
pal=np.array([[60,120,230],[230,200,40],[90,160,60],[30,90,30],[220,40,200],[240,60,40]],np.uint8)
Image.fromarray(np.where(ids[...,None]>=0,pal[np.maximum(ids,0)],0).astype(np.uint8)).save(f'{WORK}/ids.png')
p=np.asarray(Image.open(f'{SRC}/museum_poppies.jpg').convert('RGB')).astype(np.float32)
print('mean abs diff vs clean painting', np.abs(img*255-p).mean())
