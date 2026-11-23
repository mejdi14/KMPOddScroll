import numpy as np, math, sys
from PIL import Image
from scene import load
from common import WORK, NAME
NEAR=0.05
def textures(names, root=None):
    root = root or f'{WORK}/out'
    return [np.asarray(Image.open(f'{root}/{NAME}_{n}.webp').convert('RGBA')).astype(np.float32)/255 for n in names]

def path(points, t):
    cnt=len(points)
    if cnt==1: return points[0].copy()
    seg=cnt-1; s=min(max(t,0),1)*seg; i=min(int(s),seg-1); f=s-i
    i0=max(i-1,0); i3=min(i+2,seg)
    p0,p1,p2,p3=points[i0],points[i],points[i+1],points[i3]
    return 0.5*(2*p1+(-p0+p2)*f+(2*p0-5*p1+4*p2-p3)*f*f+(-p0+3*p1-3*p2+p3)*f*f*f)

def sample(tex, u, v, repeat=False):
    h,w,_=tex.shape
    x=u-0.5; y=v-0.5
    if repeat:
        x0=np.floor(x).astype(int); y0=np.floor(y).astype(int)
        fx=x-x0; fy=y-y0
        x0m=x0%w; x1m=(x0+1)%w; y0m=y0%h; y1m=(y0+1)%h
    else:
        x=np.clip(x,0,w-1); y=np.clip(y,0,h-1)
        x0=np.floor(x).astype(int); y0=np.floor(y).astype(int)
        fx=x-x0; fy=y-y0
        x0m=x0; x1m=np.minimum(x0+1,w-1); y0m=y0; y1m=np.minimum(y0+1,h-1)
    fx=fx[...,None]; fy=fy[...,None]
    return (tex[y0m,x0m]*(1-fx)*(1-fy)+tex[y0m,x1m]*fx*(1-fy)+tex[y1m,x0m]*(1-fx)*fy+tex[y1m,x1m]*fx*fy)

def render(scene, texs, W=780, H=1688, route=1.0, yaw=0.0, pitch=0.0, canvas=None, clipframe=False):
    hdr, rpts, layers = scene
    if canvas is None:
        cw=0.86*W; ch=cw/1.5
        if ch>0.7*H: ch=0.7*H; cw=ch*1.5
        ccx=W/2; ccy=0.45*H
        canvas=(ccx-cw/2, ccy-ch/2, ccx+cw/2, ccy+ch/2)
    left,top,right,bottom=canvas
    cx=(left+right)/2; cy=(top+bottom)/2
    focal=(right-left)/2/math.tan(math.radians(hdr['hFov'])/2)
    t=route; t=t*t*(3-2*t)
    eye=path(rpts, t)
    turn=math.radians(yaw); tilt=math.radians(hdr['tilt'])+math.radians(pitch)
    cy0,sy0,cp,sp=math.cos(turn),math.sin(turn),math.cos(tilt),math.sin(tilt)
    F=np.array([sy0*cp, sp, -cy0*cp]); R=np.array([cy0,0,sy0]); U=np.array([-sy0*sp, cp, cy0*sp])
    img=np.zeros((H,W,3),np.float32); img[:]=(0.05,0.05,0.05)
    ys,xs=np.mgrid[0:H,0:W]
    for L in layers:
        tex=texs[L['tex']]
        sky=L['kind']==0
        e=np.zeros(3) if sky else eye
        clip=1e-3 if sky else NEAR
        d=L['pos']-e
        zc=d@F; zz=np.maximum(zc,clip)
        sx=cx+focal*(d@R)/zz; sy=cy-focal*(d@U)/zz
        uv=L['uv']
        for a,b,c in L['idx']:
            if zc[a]<clip or zc[b]<clip or zc[c]<clip: continue
            x0,y0,x1,y1,x2,y2=sx[a],sy[a],sx[b],sy[b],sx[c],sy[c]
            area=(x1-x0)*(y2-y0)-(x2-x0)*(y1-y0)
            if L['kind'] in (1,4) and area>0: continue
            if abs(area)<1e-9: continue
            bx0=max(int(math.floor(min(x0,x1,x2))),0); bx1=min(int(math.ceil(max(x0,x1,x2))),W-1)
            by0=max(int(math.floor(min(y0,y1,y2))),0); by1=min(int(math.ceil(max(y0,y1,y2))),H-1)
            if bx0>bx1 or by0>by1: continue
            px=xs[by0:by1+1,bx0:bx1+1]+0.5; py=ys[by0:by1+1,bx0:bx1+1]+0.5
            w1=((px-x0)*(y2-y0)-(x2-x0)*(py-y0))/area
            w2=((x1-x0)*(py-y0)-(px-x0)*(y1-y0))/area
            w0=1-w1-w2
            m=(w0>=0)&(w1>=0)&(w2>=0)
            if not m.any(): continue
            u=w0*uv[a,0]+w1*uv[b,0]+w2*uv[c,0]; v=w0*uv[a,1]+w1*uv[b,1]+w2*uv[c,1]
            col=sample(tex,u[m],v[m],L['kind']==3)
            if L['kind']==4:
                fade=min(max((route-0.1)/0.6,0),1); fade=fade*fade*(3-2*fade)
                va=1-fade*(w0[m]*L['w'][a]+w1[m]*L['w'][b]+w2[m]*L['w'][c])
                col=col.copy(); col[:,3]*=va
            reg=img[by0:by1+1,bx0:bx1+1]
            al=col[:,3:4]
            reg[m]=reg[m]*(1-al)+col[:,:3]*al
    return (np.clip(img,0,1)*255).astype(np.uint8), canvas

