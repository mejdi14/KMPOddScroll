import struct, numpy as np
def load(path):
    b = open(path,'rb').read()
    at = [0]
    def i():
        v = struct.unpack_from('<i', b, at[0]); at[0]+=4; return v[0]
    def f(n=None):
        if n is None:
            v = struct.unpack_from('<f', b, at[0]); at[0]+=4; return v[0]
        v = np.frombuffer(b, '<f4', n, at[0]).copy(); at[0]+=4*n; return v
    assert i() == 0x31435350
    hdr = dict(hFov=f(), tilt=f(), yawMax=f(), pitchDown=f(), pitchUp=f())
    nr = i(); route = f(3*nr).reshape(-1,3)
    layers=[]
    for _ in range(i()):
        kind=i(); tex=i(); n=i(); ni=i()
        pos=f(3*n).reshape(-1,3); uv=f(2*n).reshape(-1,2); w=f(n)
        idx=np.frombuffer(b,'<u2',ni,at[0]).copy(); at[0]+=2*ni
        if ni%2: at[0]+=2
        layers.append(dict(kind=kind,tex=tex,pos=pos,uv=uv,w=w,idx=idx.reshape(-1,3)))
    assert at[0]==len(b), (at[0], len(b))
    return hdr, route, layers
def save(path, hdr, route, layers):
    out = bytearray()
    out += struct.pack('<i', 0x31435350)
    out += struct.pack('<5f', hdr['hFov'],hdr['tilt'],hdr['yawMax'],hdr['pitchDown'],hdr['pitchUp'])
    out += struct.pack('<i', len(route)); out += np.asarray(route,'<f4').tobytes()
    out += struct.pack('<i', len(layers))
    for L in layers:
        n=len(L['pos']); idx=np.asarray(L['idx'],'<u2').ravel()
        assert n <= 65536
        out += struct.pack('<4i', L['kind'], L['tex'], n, len(idx))
        out += np.asarray(L['pos'],'<f4').tobytes()+np.asarray(L['uv'],'<f4').tobytes()+np.asarray(L['w'],'<f4').tobytes()+idx.tobytes()
        if len(idx)%2: out += b'\0\0'
    open(path,'wb').write(out)
if __name__=='__main__':
    import sys
    hdr, route, layers = load(sys.argv[1])
    print(hdr); print('route', route)
    for k,L in enumerate(layers):
        p=L['pos']
        print(k, 'kind',L['kind'],'tex',L['tex'],'n',len(p),'tris',len(L['idx']),'min',p.min(0).round(2),'max',p.max(0).round(2),'uvmin',L['uv'].min(0).round(1),'uvmax',L['uv'].max(0).round(1),'w',L['w'].min().round(2),L['w'].max().round(2))
