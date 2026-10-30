import gzip, struct, sys, zlib
from pathlib import Path

def read_nbt(data):
    p=0
    def take(n):
        nonlocal p; v=data[p:p+n]; p+=n; return v
    def u16(): return struct.unpack('>H',take(2))[0]
    def name(): return take(u16()).decode('utf-8')
    def payload(t):
        if t==1:return struct.unpack('>b',take(1))[0]
        if t==2:return struct.unpack('>h',take(2))[0]
        if t==3:return struct.unpack('>i',take(4))[0]
        if t==4:return struct.unpack('>q',take(8))[0]
        if t==5:return struct.unpack('>f',take(4))[0]
        if t==6:return struct.unpack('>d',take(8))[0]
        if t==7:return take(struct.unpack('>i',take(4))[0])
        if t==8:return name()
        if t==9:
            et=take(1)[0]; return [payload(et) for _ in range(struct.unpack('>i',take(4))[0])]
        if t==10:
            out={}
            while True:
                et=take(1)[0]
                if et==0:return out
                key=name(); out[key]=payload(et)
        if t==11:return [struct.unpack('>i',take(4))[0] for _ in range(struct.unpack('>i',take(4))[0])]
        if t==12:return [struct.unpack('>q',take(8))[0] for _ in range(struct.unpack('>i',take(4))[0])]
        raise ValueError(t)
    typ=take(1)[0]; name(); return payload(typ)

def chunk(world,cx,cz):
    rp=world/'region'/f'r.{cx//32}.{cz//32}.mca'; raw=rp.read_bytes(); idx=(cx%32)+(cz%32)*32
    off=int.from_bytes(raw[idx*4:idx*4+3],'big')*4096
    if not off: raise AssertionError(f'missing chunk {cx},{cz}')
    size=struct.unpack('>I',raw[off:off+4])[0]; kind=raw[off+4]
    assert kind==2
    return read_nbt(zlib.decompress(raw[off+5:off+4+size]))

def block_at(world,x,y,z):
    c=chunk(world,x//16,z//16); sec=next(s for s in c['sections'] if s['Y']==y//16)
    bs=sec['block_states']; pal=bs['palette']; bits=max(4,(len(pal)-1).bit_length())
    n=((y%16)*16+(z%16))*16+(x%16)
    if len(pal)==1: return pal[0]['Name']
    per=64//bits; val=bs['data'][n//per] & ((1<<64)-1); return pal[(val>>((n%per)*bits))&((1<<bits)-1)]['Name']

def main():
    world=Path(sys.argv[1]); assert world.is_dir()
    level=read_nbt(gzip.decompress((world/'level.dat').read_bytes()))['Data']
    assert level['DataVersion']==3465 and level['Version']['Name']=='1.20.1'
    assert (level['SpawnX'],level['SpawnY'],level['SpawnZ'])==(0,163,0)
    settings=level['WorldGenSettings']['dimensions']['minecraft:overworld']['generator']['settings']
    assert settings['biome']=='minecraft:the_void' and settings['layers'][0]['block']=='minecraft:air'
    points={'spawn':(0,163,0),'base':(0,163,0),'warehouse':(85,164,106),'camp':(135,165,187),'relay':(105,164,218),'bunker':(168,166,244)}
    for label,(x,y,z) in points.items():
        found=None
        for yy in range(y-1,y-7,-1):
            block=block_at(world,x,yy,z)
            if block!='minecraft:air': found=(yy,block); break
        assert found,f'{label} has no floor'; print(label,x,y,z,'floor',found)
    regions=list((world/'region').glob('*.mca')); assert regions
    for rp in regions:
        raw=rp.read_bytes(); assert len(raw)>=8192 and len(raw)%4096==0
        for idx in range(1024):
            off=int.from_bytes(raw[idx*4:idx*4+3],'big'); sectors=raw[idx*4+3]
            if off:
                assert off>=2 and off+sectors<=len(raw)//4096
                pos=off*4096; size=struct.unpack('>I',raw[pos:pos+4])[0]
                assert 0<size<=sectors*4096-4 and raw[pos+4]==2
                read_nbt(zlib.decompress(raw[pos+5:pos+4+size]))
    print('VALID',len(regions),'regions')

if __name__=='__main__': main()
