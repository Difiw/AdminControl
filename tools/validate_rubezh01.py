import gzip, os, re, struct, hashlib

ROOT=os.path.abspath(os.path.join(os.path.dirname(__file__),'..','CW_Expedition_Rubezh01_v2'))
PATH=os.path.join(ROOT,'CW_Expedition_Rubezh01_Complex_v2.schem')
raw=gzip.open(PATH,'rb').read(); p=0
def take(n):
 global p
 b=raw[p:p+n]
 if len(b)!=n: raise EOFError
 p+=n; return b
def u16(): return struct.unpack('>H',take(2))[0]
def name(): return take(u16()).decode('utf8')
def payload(t):
 if t==1:return struct.unpack('>b',take(1))[0]
 if t==2:return struct.unpack('>h',take(2))[0]
 if t==3:return struct.unpack('>i',take(4))[0]
 if t==4:return struct.unpack('>q',take(8))[0]
 if t==7:
  n=struct.unpack('>i',take(4))[0]; return take(n)
 if t==8:return name()
 if t==9:
  et=take(1)[0]; n=struct.unpack('>i',take(4))[0]; return [payload(et) for _ in range(n)]
 if t==10:
  d={}
  while True:
   tt=take(1)[0]
   if tt==0:return d
   key=name(); d[key]=payload(tt)
 if t==11:
  n=struct.unpack('>i',take(4))[0]; return [struct.unpack('>i',take(4))[0] for _ in range(n)]
 raise ValueError(f'unsupported tag {t}')
t=take(1)[0]; rootname=name(); doc=payload(t)
assert p==len(raw) and t==10 and rootname=='Schematic'
assert doc['Version']==2 and doc['DataVersion']==3465
W,H,L=doc['Width'],doc['Height'],doc['Length']; assert (W,H,L)==(150,42,150)
pal=doc['Palette']; assert doc['PaletteMax']==len(pal) and sorted(pal.values())==list(range(len(pal)))
rev={v:k for k,v in pal.items()}; vals=[]; n=0; shift=0
for b in doc['BlockData']:
 n|=(b&127)<<shift
 if b&128: shift+=7; assert shift<35
 else: vals.append(n); n=0; shift=0
assert shift==0 and len(vals)==W*H*L and max(vals)<len(pal)
state_re=re.compile(r'^minecraft:[a-z0-9_]+(?:\[[a-z0-9_]+=[a-z0-9_]+(?:,[a-z0-9_]+=[a-z0-9_]+)*\])?$')
bad=[s for s in pal if not state_re.match(s)]
assert not bad, bad
known={'air','grass_block','iron_bars','gray_concrete','yellow_concrete','smooth_stone','white_concrete','stone','polished_deepslate','light_gray_concrete','light_blue_stained_glass_pane','sea_lantern','smooth_stone_slab','iron_door','lever','stone_brick_stairs','iron_block','lightning_rod','cyan_stained_glass_pane','glass_pane','stone_slab','birch_planks','calcite','blue_bed','light_blue_bed','white_bed','oak_stairs','birch_slab','brick_slab','bricks','polished_andesite','stone_bricks','deepslate_tiles','light_gray_stained_glass','yellow_glazed_terracotta','weathered_cut_copper_slab','weathered_copper','chain','stripped_spruce_wood','gravel','iron_trapdoor','ladder','red_concrete','oak_log','oak_leaves','moss_block'}
ids={s.split(':',1)[1].split('[',1)[0] for s in pal}; unknown=ids-known; assert not unknown,unknown
assert doc['Offset']==[0,0,0] and doc['BlockEntities']==[] and doc['Entities']==[]
counts={rev[i]:vals.count(i) for i in set(vals)}; nonair=sum(v for k,v in counts.items() if k!='minecraft:air')
assert nonair==46420

# Structural sampling checks against decoded actual data.
def at(x,y,z): return rev[vals[x+z*W+y*W*L]]
checks={
 'main_three_floors':all(at(60,y,90)!='minecraft:air' for y in (1,7,13)),
 'lab_two_floors':all(at(102,y,90)!='minecraft:air' for y in (1,7)),
 'residence_two_floors':all(at(18,y,90)!='minecraft:air' for y in (1,7)),
 'hangar_gabled_roof':any('weathered' in at(x,y,30) for x in range(90,126) for y in range(11,16)),
 'tower_four_supports':all(at(x,4,z)=='minecraft:iron_bars' for x,z in ((25,28),(35,28),(25,38),(35,38))),
 'main_road':all(at(75,0,z)!='minecraft:grass_block[snowy=false]' for z in range(4,65)),
 'fence_and_gate':at(10,1,3)=='minecraft:iron_bars' and at(75,1,3)=='minecraft:air',
 'interior_lighting':sum(1 for v in vals if rev[v]=='minecraft:sea_lantern')>=20,
}
assert all(checks.values()),checks
sha=hashlib.sha256(open(PATH,'rb').read()).hexdigest()
report=f'''CW Expedition — Rubezh-01 v2 validation report
Generated: local offline build; no Minecraft world was modified.

FORMAT
- Sponge Schematic Version: {doc['Version']}
- Minecraft DataVersion: {doc['DataVersion']} (Java 1.20.1)
- Dimensions (Width x Height x Length): {W} x {H} x {L}
- Offset: {doc['Offset']}
- Palette entries / block states: {len(pal)}
- Decoded positions: {len(vals)} (expected {W*H*L})
- Non-air blocks: {nonair}
- BlockData bytes: {len(doc['BlockData'])}
- BlockEntities: {len(doc['BlockEntities'])}; Entities: {len(doc['Entities'])}
- SHA-256: {sha}

STRUCTURES
1. Main research building — 38x30, three floors, asymmetric wing, reception/security/corridors/offices/archive/staff and technical areas, stairs, modern parapet roof and two compact rooftop plants.
2. Laboratory — 29x24, two floors, long cyan window bands, four-section glass-partition lab layout, analysis/sample/wash areas, service entry, rooftop units, enclosed connector.
3. Residence/medical — 26x21, two floors, warm calcite/birch facade, exam/rest/common/sleeping/service areas, beds and desks, veranda and stepped pitched brick roof.
4. Hangar/technical — 34x29, tall glazed vehicle door, side personnel door, workshop/racks/generator/service zones, trusses and two-slope copper roof, loading apron.
5. Radio/communications — four-leg tapered lattice tower to Y33 with braces, platforms, ladder and antenna; fenced gravel hazard pad and furnished equipment hut.

SITE
- 7–9 block main road, two branches, central plaza, sidewalks/curbs, six marked parking bays, gatehouse and barrier, perimeter fence, street lighting, drains, sparse landscaping and controlled neglect details.
- Ground is one flat grass layer intended for a level natural site. No perimeter water, water walls, terrain sides or floating island geometry.

CHECKS PASSED
- Independent NBT parser reread the gzip file to EOF.
- Header, dimensions, offset, lists and DataVersion validated.
- VarInt BlockData decoded to exactly Width*Height*Length positions; every palette index is in range.
- Palette IDs use minecraft namespace and were checked against the generator's vanilla 1.20.1 allowlist; block-state syntax validated.
- Export non-air count equals generation count: {nonair}.
- Sampled floor levels, road continuity, fence/gate, tower four supports, hangar roof and lighting passed: {checks}.
- Top view and four isometric views were rendered from the decoded source block array used to write the schematic, not from concept art.

SAFETY / LIMITATIONS
- Found WorldEdit: W:\\GameVersionCWL\\CommonWorldServer\\instances\\Map\\mods\\worldedit-mod-7.2.15.jar.
- The Map instance was not proven to be a disposable test world; no world files were opened for writing and no in-game paste was attempted.
- No signs with text, inventories, block entities or entities are claimed; their lists are intentionally empty.
- Preview is a schematic-data renderer, not an in-game Minecraft screenshot. Lighting propagation, neighbor-shape updates and visual resource-pack behavior require a safe test-world import.

WORLDEDIT IMPORT (safe test world only)
1. Copy the .schem into that test instance's config/worldedit/schematics folder.
2. Start Minecraft 1.20.1 Fabric with compatible WorldEdit 7.2.15 and open only the disposable test world.
3. Stand at the intended southwest-bottom origin on level terrain.
4. Run //schem load CW_Expedition_Rubezh01_Complex_v2
5. Run //paste -a (air is omitted; grass foundation is pasted at the origin level).
6. Inspect roofs, doors, stairs and lighting; use //undo if placement is unsuitable.
'''
open(os.path.join(ROOT,'CW_Expedition_Rubezh01_Complex_v2_Report.txt'),'w',encoding='utf-8').write(report)
print(report)
