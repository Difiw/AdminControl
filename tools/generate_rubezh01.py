import gzip, math, os, struct
from collections import Counter, deque
from PIL import Image, ImageDraw

W,H,L=150,42,150
AIR='minecraft:air'
blocks=[AIR]*(W*H*L)
def idx(x,y,z): return x+z*W+y*W*L
def setb(x,y,z,b):
    if 0<=x<W and 0<=y<H and 0<=z<L: blocks[idx(x,y,z)]=b
def getb(x,y,z): return blocks[idx(x,y,z)] if 0<=x<W and 0<=y<H and 0<=z<L else AIR
def fill(x1,y1,z1,x2,y2,z2,b):
    for y in range(y1,y2+1):
      for z in range(z1,z2+1):
       for x in range(x1,x2+1): setb(x,y,z,b)
def hollow(x1,y1,z1,x2,y2,z2,wall):
    for y in range(y1,y2+1):
      for z in range(z1,z2+1):
       for x in range(x1,x2+1):
        if x in (x1,x2) or z in (z1,z2) or y in (y1,y2): setb(x,y,z,wall)
def door(x,y,z,facing='south'):
    setb(x,y,z,f'minecraft:iron_door[facing={facing},half=lower,hinge=left,open=false,powered=false]')
    setb(x,y+1,z,f'minecraft:iron_door[facing={facing},half=upper,hinge=left,open=false,powered=false]')
def lamp(x,y,z): setb(x,y,z,'minecraft:sea_lantern')
def sign(x,y,z): setb(x,y,z,'minecraft:yellow_concrete')
def desk(x,y,z,n=3):
    fill(x,y,z,x+n-1,y,z,'minecraft:smooth_stone_slab[type=bottom,waterlogged=false]')
    setb(x,y+1,z,'minecraft:lever[face=floor,facing=north,powered=false]')
def chair(x,y,z): setb(x,y,z,'minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]')
def bed(x,y,z,color='light_blue'):
    setb(x,y,z,f'minecraft:{color}_bed[facing=north,part=foot,occupied=false]'); setb(x,y,z-1,f'minecraft:{color}_bed[facing=north,part=head,occupied=false]')
def window_band(x1,y,z1,x2,z2,b='minecraft:light_blue_stained_glass_pane'):
    for z in range(z1,z2+1):
      for x in range(x1,x2+1): setb(x,y,z,b)

# natural placement surface only: single grass layer, irregular gravel/stone shoulders at edges
fill(0,0,0,W-1,0,L-1,'minecraft:grass_block[snowy=false]')

# perimeter fence with deliberate front gate
for x in range(3,147):
    if not 68<=x<=82: setb(x,1,3,'minecraft:iron_bars')
    setb(x,1,146,'minecraft:iron_bars')
for z in range(3,147): setb(3,1,z,'minecraft:iron_bars'); setb(146,1,z,'minecraft:iron_bars')

# road network, plaza, sidewalks, parking
fill(71,0,3,79,0,81,'minecraft:gray_concrete')
fill(50,0,66,100,0,81,'minecraft:gray_concrete')
fill(76,0,44,104,0,50,'minecraft:gray_concrete')
fill(29,0,75,75,0,80,'minecraft:gray_concrete')
for z in range(5,80,8): fill(74,1,z,76,1,z,'minecraft:yellow_concrete')
for x1,z1,x2,z2 in [(68,3,70,82),(80,3,82,82),(48,64,102,66),(48,82,102,84),(27,73,29,108),(40,106,55,108),(89,23,126,25),(89,53,126,55)]: fill(x1,0,z1,x2,0,z2,'minecraft:smooth_stone')
fill(44,0,53,68,0,64,'minecraft:stone')
for x in range(46,68,4): fill(x,1,54,x,1,62,'minecraft:white_concrete')

# A main research building, asymmetric 38x30, three floors
fill(55,0,83,92,0,112,'minecraft:polished_deepslate')
for fy,cy in [(1,6),(7,12),(13,18)]:
    fill(55,fy,83,92,fy,112,'minecraft:smooth_stone')
    for y in range(fy+1,cy):
      for x in range(55,93): setb(x,y,83 if x<82 else 85,'minecraft:light_gray_concrete')
      fill(55,y,83,55,y,112,'minecraft:light_gray_concrete'); fill(92,y,85,92,y,112,'minecraft:light_gray_concrete'); fill(55,y,112,92,y,112,'minecraft:light_gray_concrete')
    fill(55,cy,83,92,cy,112,'minecraft:smooth_stone')
    for x in list(range(59,68))+list(range(72,81))+list(range(84,90)):
      setb(x,fy+2,83 if x<82 else 85,'minecraft:light_blue_stained_glass_pane'); setb(x,fy+3,83 if x<82 else 85,'minecraft:light_blue_stained_glass_pane')
    for z in range(88,109,4): setb(55,fy+2,z,'minecraft:light_blue_stained_glass_pane'); setb(92,fy+2,z,'minecraft:light_blue_stained_glass_pane')
    # central corridor and four functional rooms
    fill(72,fy+1,86,73,cy-1,109,AIR)
    fill(56,fy+1,97,71,cy-1,97,'minecraft:white_concrete'); fill(74,fy+1,97,91,cy-1,97,'minecraft:white_concrete')
    for x,z in [(70,97),(75,97)]: fill(x,fy+1,z,x,fy+2,z,AIR)
    for x,z in [(62,91),(78,91),(62,104),(80,104)]: desk(x,fy+1,z); chair(x+1,fy+1,z+1)
    for z in (90,100,108): lamp(72,cy-1,z)
door(72,1,83,'south'); fill(68,1,80,77,1,82,'minecraft:smooth_stone'); fill(69,4,81,76,4,83,'minecraft:smooth_stone_slab[type=top,waterlogged=false]')
# accessible stairs through all floors
for base in (1,7):
  for i in range(6): setb(57+i,base+i,108,'minecraft:stone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]')
  fill(57,base+1,107,63,base+6,109,AIR)
# roof parapet and compact mechanical plant
for x in range(54,94): setb(x,20,82,'minecraft:smooth_stone_slab[type=bottom,waterlogged=false]'); setb(x,20,113,'minecraft:smooth_stone_slab[type=bottom,waterlogged=false]')
for z in range(82,114): setb(54,20,z,'minecraft:smooth_stone_slab[type=bottom,waterlogged=false]'); setb(93,20,z,'minecraft:smooth_stone_slab[type=bottom,waterlogged=false]')
fill(61,20,91,67,22,97,'minecraft:iron_block'); fill(79,20,99,85,21,104,'minecraft:light_gray_concrete')
for x in (63,66,81,84): setb(x,23,94 if x<70 else 102,'minecraft:lightning_rod')

# B laboratory 29x24, two floors, long windows, service annex
fill(99,0,83,127,0,106,'minecraft:polished_deepslate')
for fy,cy in [(1,6),(7,12)]:
 fill(99,fy,83,127,fy,106,'minecraft:smooth_stone')
 for y in range(fy+1,cy):
  fill(99,y,83,127,y,83,'minecraft:white_concrete'); fill(99,y,106,127,y,106,'minecraft:white_concrete'); fill(99,y,83,99,y,106,'minecraft:light_gray_concrete'); fill(127,y,83,127,y,106,'minecraft:light_gray_concrete')
 fill(99,cy,83,127,cy,106,'minecraft:smooth_stone')
 for x in range(102,125):
  if x%6!=0: setb(x,fy+2,83,'minecraft:cyan_stained_glass_pane'); setb(x,fy+3,83,'minecraft:cyan_stained_glass_pane')
 # glass partitions: wet lab, analysis, samples, wash/tech
 fill(112,fy+1,84,112,cy-1,105,'minecraft:glass_pane'); fill(100,fy+1,95,126,cy-1,95,'minecraft:glass_pane')
 for x,z in [(111,89),(113,89),(106,95),(120,95)]: fill(x,fy+1,z,x,fy+2,z,AIR)
 for x,z in [(102,89),(115,89),(102,101),(115,101)]: desk(x,fy+1,z,5)
 for x,z in [(105,93),(118,93),(105,104),(118,104)]: lamp(x,cy-1,z)
door(105,1,83,'south'); door(127,1,101,'west')
for i in range(6): setb(101+i,1+i,103,'minecraft:stone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]')
fill(104,13,87,110,15,94,'minecraft:light_gray_concrete'); fill(116,13,96,123,14,102,'minecraft:iron_block')
for x in range(99,128): setb(x,13,83,'minecraft:stone_slab[type=bottom,waterlogged=false]'); setb(x,13,106,'minecraft:stone_slab[type=bottom,waterlogged=false]')
# enclosed connector
hollow(92,3,91,99,6,96,'minecraft:light_gray_concrete'); fill(93,4,92,98,5,95,AIR); window_band(94,4,91,97,91); window_band(94,4,96,97,96)

# C residence/medical, warm facade, two floors + stepped pitched roof
fill(15,0,87,40,0,107,'minecraft:stone_bricks')
for fy,cy in [(1,6),(7,12)]:
 fill(15,fy,87,40,fy,107,'minecraft:birch_planks')
 for y in range(fy+1,cy):
  fill(15,y,87,40,y,87,'minecraft:calcite'); fill(15,y,107,40,y,107,'minecraft:calcite'); fill(15,y,87,15,y,107,'minecraft:calcite'); fill(40,y,87,40,y,107,'minecraft:calcite')
 fill(15,cy,87,40,cy,107,'minecraft:birch_planks')
 for x in (18,19,24,25,31,32,36,37): setb(x,fy+2,87,'minecraft:glass_pane'); setb(x,fy+3,87,'minecraft:glass_pane')
 fill(27,fy+1,88,27,cy-1,106,'minecraft:birch_planks'); fill(16,fy+1,97,39,cy-1,97,'minecraft:birch_planks')
 for p in [(26,92),(28,92),(21,97),(34,97)]: fill(p[0],fy+1,p[1],p[0],fy+2,p[1],AIR)
 # med exam/rest/common/rooms
 bed(18,fy+1,94,'white'); desk(30,fy+1,92); bed(18,fy+1,104); bed(31,fy+1,104,'blue'); desk(34,fy+1,101)
 for x,z in [(21,92),(34,92),(21,102),(34,102)]: lamp(x,cy-1,z)
door(27,1,87,'south'); fill(23,1,84,31,1,86,'minecraft:birch_planks'); fill(24,4,85,30,4,87,'minecraft:birch_slab[type=top,waterlogged=false]')
for i in range(6): setb(16+i,1+i,105,'minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]')
# pitched/stepped roof with eaves
for d in range(0,11):
 fill(14+d,13+d//2,86,14+d,13+d//2,108,'minecraft:brick_slab[type=bottom,waterlogged=false]')
 fill(41-d,13+d//2,86,41-d,13+d//2,108,'minecraft:brick_slab[type=bottom,waterlogged=false]')
fill(25,18,86,30,18,108,'minecraft:bricks')

# D hangar/technical building with tall doors and gabled roof
fill(91,0,25,124,0,53,'minecraft:polished_andesite')
for y in range(1,11):
 fill(91,y,25,124,y,25,'minecraft:stone_bricks'); fill(91,y,53,124,y,53,'minecraft:stone_bricks'); fill(91,y,25,91,y,53,'minecraft:deepslate_tiles'); fill(124,y,25,124,y,53,'minecraft:deepslate_tiles')
 # front vehicle opening
 if y<=7: fill(101,y,25,113,y,25,AIR)
for x in (92,123): fill(x,1,26,x,10,52,'minecraft:iron_block')
for x in range(101,114): setb(x,8,25,'minecraft:iron_block')
# translucent segmented shutter and side door
for y in range(1,8):
 for x in range(101,114): setb(x,y,25,'minecraft:light_gray_stained_glass')
door(124,1,47,'west')
# internal four zones: workshop, racks, generator, service bay
fill(92,1,39,100,5,39,'minecraft:iron_bars'); fill(115,1,39,123,5,39,'minecraft:iron_bars')
for z in (30,34,43,48): desk(93,1,z,5)
for x in (116,120):
 for z in (42,47): fill(x,1,z,x+1,3,z+1,'minecraft:yellow_glazed_terracotta')
for z in range(29,51,5): lamp(107,9,z)
# roof slopes, ridge, trusses
for d in range(0,9):
 fill(90+d,11+d//2,24,90+d,11+d//2,54,'minecraft:weathered_cut_copper_slab[type=bottom,waterlogged=false]')
 fill(125-d,11+d//2,24,125-d,11+d//2,54,'minecraft:weathered_cut_copper_slab[type=bottom,waterlogged=false]')
fill(99,15,24,116,15,54,'minecraft:weathered_copper')
for z in (29,39,49):
 fill(93,10,z,122,10,z,'minecraft:iron_block'); setb(107,14,z,'minecraft:chain')
# loading apron/crates
fill(96,0,18,120,0,24,'minecraft:stone');
for x,z in [(94,20),(97,20),(121,21),(88,45)]: fill(x,1,z,x+2,2,z+2,'minecraft:stripped_spruce_wood[axis=y]')

# E radio tower: fenced pad, hut, four legs, braces, platforms, antenna
fill(20,0,23,52,0,44,'minecraft:gravel')
for x in range(20,53): setb(x,1,23,'minecraft:iron_bars'); setb(x,1,44,'minecraft:iron_bars')
for z in range(23,45): setb(20,1,z,'minecraft:iron_bars'); setb(52,1,z,'minecraft:iron_bars')
fill(34,1,44,37,2,44,AIR); sign(32,1,44); sign(39,1,44)
# tech hut
hollow(40,1,28,49,7,38,'minecraft:light_gray_concrete'); fill(41,2,29,48,6,37,AIR); door(44,1,38,'south')
fill(39,8,27,50,8,39,'minecraft:smooth_stone_slab[type=bottom,waterlogged=false]')
for x,z in [(42,31),(46,31),(42,35),(46,35)]: fill(x,2,z,x+1,4,z+1,'minecraft:iron_block')
lamp(44,6,33)
# tower tapering square with four legs and diagonal braces
levels=[(25,28,35,38),(26,29,34,37),(27,30,33,36),(28,31,32,35)]
for y in range(1,29):
 q=min(3,y//8); x1,z1,x2,z2=levels[q]
 for x,z in [(x1,z1),(x2,z1),(x1,z2),(x2,z2)]: setb(x,y,z,'minecraft:iron_bars')
 if y%4==0:
  fill(x1,y,z1,x2,y,z1,'minecraft:iron_bars'); fill(x1,y,z2,x2,y,z2,'minecraft:iron_bars'); fill(x1,y,z1,x1,y,z2,'minecraft:iron_bars'); fill(x2,y,z1,x2,y,z2,'minecraft:iron_bars')
  for t in range(0,x2-x1+1):
   setb(x1+t,y-3+(t%4),z1,'minecraft:iron_bars'); setb(x1+t,y-(t%4),z2,'minecraft:iron_bars')
for y in range(2,28): setb(25 if y<8 else 26 if y<16 else 27 if y<24 else 28,y,33,'minecraft:ladder[facing=west,waterlogged=false]')
for y in (8,16,24): fill(26 if y<16 else 27,y,30,34 if y<16 else 33,y,37,'minecraft:iron_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]')
fill(29,29,32,31,32,34,'minecraft:iron_bars'); setb(30,33,33,'minecraft:lightning_rod')

# gatehouse, barrier, road and exterior lighting
hollow(59,1,5,67,6,13,'minecraft:smooth_stone'); fill(60,2,6,66,5,12,AIR); door(63,1,13,'south')
for x in range(60,67): setb(x,3,5,'minecraft:glass_pane')
fill(58,7,4,68,7,14,'minecraft:smooth_stone_slab[type=bottom,waterlogged=false]'); lamp(63,5,9)
fill(68,2,8,81,2,8,'minecraft:red_concrete'); setb(68,1,8,'minecraft:iron_block')
# street lights
for x,z in [(68,20),(82,20),(68,38),(82,38),(68,58),(82,58),(47,72),(103,72),(45,104),(88,117),(130,70),(17,76)]:
 fill(x,1,z,x,5,z,'minecraft:iron_bars'); setb(x,6,z,'minecraft:sea_lantern'); setb(x,7,z,'minecraft:stone_slab[type=bottom,waterlogged=false]')
# controlled vegetation and damaged corner
for x,z in [(10,20),(13,32),(8,60),(12,127),(36,128),(133,120),(138,97),(137,18),(55,132)]:
 fill(x,1,z,x,4,z,'minecraft:oak_log[axis=y]'); fill(x-2,4,z-2,x+2,6,z+2,'minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]')
for x,z in [(7,138),(11,141),(17,139),(133,137),(139,132),(45,120)]: setb(x,1,z,'minecraft:moss_block')
# drains/manholes
for x,z in [(69,30),(80,60),(54,70),(97,47),(33,78)]: setb(x,0,z,'minecraft:iron_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]')

# ---------- Sponge schematic v2 NBT writer ----------
used=Counter(blocks); palette={b:i for i,b in enumerate(sorted(used))}
def varints(vals):
 out=bytearray()
 for v in vals:
  while True:
   b=v&127; v>>=7; out.append(b|128 if v else b)
   if not v: break
 return bytes(out)
data=varints(palette[b] for b in blocks)
def s16(n): return struct.pack('>h',n)
def i32(n): return struct.pack('>i',n)
def text(s):
 b=s.encode('utf8'); return struct.pack('>H',len(b))+b
def tag(t,name,payload): return bytes([t])+text(name)+payload
def compound(entries): return b''.join(entries)+b'\0'
pal_entries=[tag(3,k,i32(v)) for k,v in palette.items()]
root=compound([
 tag(3,'Version',i32(2)),tag(3,'DataVersion',i32(3465)),
 tag(2,'Width',s16(W)),tag(2,'Height',s16(H)),tag(2,'Length',s16(L)),
 tag(3,'PaletteMax',i32(len(palette))),tag(10,'Palette',compound(pal_entries)),
 tag(7,'BlockData',i32(len(data))+data),tag(11,'Offset',i32(3)+i32(0)+i32(0)+i32(0)),
 tag(9,'BlockEntities',bytes([10])+i32(0)),tag(9,'Entities',bytes([10])+i32(0)),
 tag(10,'Metadata',compound([tag(8,'Name',text('CW Expedition Rubezh-01 Complex v2')),tag(8,'Author',text('OpenAI Codex'))]))
])

OUT=os.path.abspath(os.path.join(os.path.dirname(__file__),'..','CW_Expedition_Rubezh01_v2'))
if os.path.exists(OUT):
    if os.listdir(OUT): raise FileExistsError(f'Refusing to overwrite non-empty output directory: {OUT}')
else: os.makedirs(OUT)
schem=os.path.join(OUT,'CW_Expedition_Rubezh01_Complex_v2.schem')
with open(schem,'wb') as raw:
    with gzip.GzipFile(fileobj=raw,mode='wb',compresslevel=9,mtime=0) as f: f.write(tag(10,'Schematic',root))

# ---------- previews rendered directly from the actual block array ----------
colors={
 'air':None,'grass_block':(91,130,62),'gray_concrete':(70,73,78),'light_gray_concrete':(155,157,156),'white_concrete':(207,213,214),
 'smooth_stone':(158,158,151),'smooth_stone_slab':(165,165,158),'stone':(125,125,120),'stone_bricks':(120,116,110),'polished_andesite':(132,134,133),
 'polished_deepslate':(55,54,59),'deepslate_tiles':(50,52,55),'glass_pane':(185,220,225),'light_blue_stained_glass_pane':(95,165,190),'cyan_stained_glass_pane':(55,130,145),
 'light_gray_stained_glass':(140,150,150),'iron_block':(205,205,198),'iron_bars':(115,120,120),'birch_planks':(194,175,125),'calcite':(220,218,207),
 'brick_slab':(145,78,64),'bricks':(145,78,64),'weathered_cut_copper_slab':(75,140,118),'weathered_copper':(72,135,112),'gravel':(125,120,115),
 'yellow_concrete':(230,180,25),'red_concrete':(150,35,35),'sea_lantern':(205,235,220),'oak_log':(95,75,48),'oak_leaves':(65,115,50),'moss_block':(85,115,55),
 'stripped_spruce_wood':(110,80,50),'yellow_glazed_terracotta':(180,145,45),'smooth_stone_slab':(155,155,150),'stone_slab':(125,125,120),'birch_slab':(194,175,125)
}
def cname(b): return b.split(':',1)[1].split('[',1)[0]
def col(b): return colors.get(cname(b),(125,125,135))
def render_top(path,scale=5):
 im=Image.new('RGB',(W*scale,L*scale),(40,50,45)); d=ImageDraw.Draw(im)
 for z in range(L):
  for x in range(W):
   y=max((yy for yy in range(H) if getb(x,yy,z)!=AIR),default=0); c=col(getb(x,y,z));
   if c: d.rectangle((x*scale,z*scale,(x+1)*scale-1,(z+1)*scale-1),fill=c)
 d.text((10,10),'CW Expedition: Rubezh-01 | actual schematic top view',fill='white'); im.save(path)
def render_iso(path,rot=0,scale=4):
 pts=[]
 for y in range(H):
  for z in range(L):
   for x in range(W):
    b=getb(x,y,z)
    if b==AIR: continue
    if all(getb(x+dx,y+dy,z+dz)!=AIR for dx,dy,dz in ((1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1))): continue
    xx,zz=x,z
    for _ in range(rot): xx,zz=zz,W-1-xx
    sx=(xx-zz)*scale; sy=(xx+zz)*scale//2-y*scale
    pts.append((sy,sx,sy,b))
 minx=min(p[1] for p in pts); maxx=max(p[1] for p in pts); miny=min(p[2] for p in pts); maxy=max(p[2] for p in pts)
 im=Image.new('RGB',(maxx-minx+80,maxy-miny+100),(25,31,36)); d=ImageDraw.Draw(im)
 for _,sx,sy,b in sorted(pts):
  c=col(b); px=sx-minx+40; py=sy-miny+55
  d.polygon([(px,py),(px+scale,py+scale//2),(px,py+scale),(px-scale,py+scale//2)],fill=c)
 d.text((10,10),f'Rubezh-01 actual blocks | isometric {rot+1}/4',fill='white'); im.save(path)

top=os.path.join(OUT,'CW_Expedition_Rubezh01_Complex_v2_TopView.png'); render_top(top)
for r in range(4): render_iso(os.path.join(OUT,f'CW_Expedition_Rubezh01_Complex_v2_View_{r+1}.png'),r)
# compose four actual views into requested preview
views=[Image.open(os.path.join(OUT,f'CW_Expedition_Rubezh01_Complex_v2_View_{r+1}.png')) for r in range(4)]
thumbs=[]
for v in views:
 v.thumbnail((700,500)); thumbs.append(v.copy())
prev=Image.new('RGB',(1400,1000),(20,24,28))
for i,v in enumerate(thumbs): prev.paste(v,((i%2)*700,(i//2)*500))
prev.save(os.path.join(OUT,'CW_Expedition_Rubezh01_Complex_v2_Preview.png'))

nonair=sum(n for b,n in used.items() if b!=AIR)
plan=f'''CW Expedition — Rubezh-01 coordinate plan\nSchematic axes: X east, Y up, Z south. Origin/Offset: 0,0,0. Intended paste: level natural/flat terrain, ground at Y=0.\nOverall bounds: {W} x {H} x {L}\n\nA Main research: X55..92, Z83..112, floors Y1..18, roof/mechanical Y19..23.\nB Laboratory: X99..127, Z83..106, floors Y1..12, roof plant Y13..15; connector X92..99 Z91..96.\nC Residence/medical: X15..40, Z87..107, floors Y1..12, pitched roof Y13..18.\nD Hangar/technical: X91..124, Z25..53, clear hall Y1..10, gabled roof Y11..15.\nE Radio/communications: tower X25..35 Z28..38 Y1..33; equipment hut X40..49 Z28..38 Y1..8; fenced pad X20..52 Z23..44.\nGatehouse: X59..68 Z4..14. Main gate opening: X68..82 Z3.\nMain road: X71..79 Z3..81. Plaza: X50..100 Z66..81. Parking: X44..68 Z53..64 (6 marked bays).\nRoutes: branch to hangar X76..104 Z44..50; west route X29..75 Z75..80; sidewalks border roads and building approaches.\nNo building bounding boxes intersect; laboratory connector is intentional.\n'''
open(os.path.join(OUT,'CW_Expedition_Rubezh01_Complex_v2_Plan.txt'),'w',encoding='utf-8').write(plan)
stats=dict(width=W,height=H,length=L,palette=len(palette),nonair=nonair,blockdata=len(data),file=os.path.getsize(schem))
open(os.path.join(OUT,'generation_stats.txt'),'w',encoding='utf-8').write('\n'.join(f'{k}={v}' for k,v in stats.items()))
print(OUT); print(stats)
