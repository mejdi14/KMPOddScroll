import numpy as np, math
from scene import load
from common import SRC, WORK
# old ground rasterised top-down into a grid: x,z in [-EXT,EXT] at STEP metres
EXT=200.0; STEP=0.25
N=int(2*EXT/STEP)+1
def build():
    hdr,route,L=load(f'{SRC}/museum_poppies_scene.bin')
    g=L[2]; p=g['pos']
    Hm=np.full((N,N),np.nan,np.float32)
    gx=lambda x:(x+EXT)/STEP
    for a,b,c in g['idx']:
        P=p[[a,b,c]]
        X=gx(P[:,0]); Z=gx(P[:,2])
        i0=max(int(math.floor(X.min())),0); i1=min(int(math.ceil(X.max())),N-1)
        j0=max(int(math.floor(Z.min())),0); j1=min(int(math.ceil(Z.max())),N-1)
        if i0>i1 or j0>j1: continue
        I,J=np.meshgrid(np.arange(i0,i1+1),np.arange(j0,j1+1))
        x0,z0,x1,z1,x2,z2=X[0],Z[0],X[1],Z[1],X[2],Z[2]
        den=(z1-z2)*(x0-x2)+(x2-x1)*(z0-z2)
        if abs(den)<1e-12: continue
        w0=((z1-z2)*(I-x2)+(x2-x1)*(J-z2))/den; w1=((z2-z0)*(I-x2)+(x0-x2)*(J-z2))/den; w2=1-w0-w1
        m=(w0>=-1e-4)&(w1>=-1e-4)&(w2>=-1e-4)
        Hm[J[m],I[m]]=w0[m]*P[0,1]+w1[m]*P[1,1]+w2[m]*P[2,1]
    return Hm
if __name__=='__main__':
    Hm=build(); np.save(f'{WORK}/old_height.npy',Hm)
    from PIL import Image
    v=np.nan_to_num(Hm,nan=-1)
    img=np.where(v<0,0,40+v/2.7*215).astype(np.uint8)
    # crop x -80..40, z -60..10
    def cx(x): return int((x+EXT)/STEP)
    Image.fromarray(img[cx(-60):cx(10),cx(-80):cx(40)]).resize((960,560)).save(f'{WORK}/heights_old.png')
    print(np.nanmax(Hm), np.isnan(Hm).mean())
    # ridge profile
    for x in [-150,-100,-80,-60,-40,-20,-5,0,3]:
        col=Hm[:,cx(x)]; zs=np.arange(N)*STEP-EXT
        m=~np.isnan(col)&(col>1.3)
        print('x',x,'hill z range', (zs[m].min(),zs[m].max()) if m.any() else None)
