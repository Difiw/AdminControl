import gzip, math, os, random, shutil, struct, sys, time, zlib
from collections import defaultdict
from pathlib import Path

DATA_VERSION = 3465  # Java Edition 1.20.1
SKY_SHIFT = 96
SPAWN = (0, 67 + SKY_SHIFT, 0)
VOID_CHUNK_RADIUS = 32  # pre-save a 1024x1024 void envelope around the chapter
WORLD_BORDER_SIZE = 880.0

def b(v): return struct.pack('>b', v)
def h(v): return struct.pack('>h', v)
def i(v): return struct.pack('>i', v)
def q(v): return struct.pack('>q', v)
def f(v): return struct.pack('>f', v)
def d(v): return struct.pack('>d', v)
def s(v):
    x = v.encode('utf-8'); return h(len(x)) + x

def tag(t, name, payload): return b(t) + s(name) + payload
def byte(name,v): return tag(1,name,b(v))
def short(name,v): return tag(2,name,h(v))
def integer(name,v): return tag(3,name,i(v))
def long(name,v): return tag(4,name,q(v))
def float_(name,v): return tag(5,name,f(v))
def double(name,v): return tag(6,name,d(v))
def string(name,v): return tag(8,name,s(v))
def list_(name, typ, vals): return tag(9,name,b(typ)+i(len(vals))+b''.join(vals))
def compound_payload(items): return b''.join(items)+b'\0'
def compound(name, items): return tag(10,name,compound_payload(items))
def long_array(name, vals): return tag(12,name,i(len(vals))+b''.join(q(v if v < 2**63 else v-2**64) for v in vals))
def byte_array(name, vals): return tag(7,name,i(len(vals))+bytes(vals))
def root(items): return b'\x0a\x00\x00'+compound_payload(items)

class World:
    def __init__(self): self.blocks = {}
    def set(self,x,y,z,name,props=None):
        self.blocks[(x,y,z)] = (name, tuple(sorted((props or {}).items())))
    def fill(self,x1,y1,z1,x2,y2,z2,name,props=None):
        for x in range(min(x1,x2),max(x1,x2)+1):
            for y in range(min(y1,y2),max(y1,y2)+1):
                for z in range(min(z1,z2),max(z1,z2)+1): self.set(x,y,z,name,props)
    def hollow(self,x1,y1,z1,x2,y2,z2,wall,roof=None):
        self.fill(x1,y1,z1,x2,y1,z2,wall); self.fill(x1,y2,z1,x2,y2,z2,roof or wall)
        self.fill(x1,y1,z1,x1,y2,z2,wall); self.fill(x2,y1,z1,x2,y2,z2,wall)
        self.fill(x1,y1,z1,x2,y2,z1,wall); self.fill(x1,y1,z2,x2,y2,z2,wall)
        self.fill(x1+1,y1+1,z1+1,x2-1,y2-1,z2-1,'minecraft:air')

W=World(); random.seed(71001)

SECONDARY_PATHS=[[(42,76),(62,91),(84,105),(95,111)],[(78,106),(101,137),(119,161),(135,184)],[(118,218),(139,229),(157,242),(170,246)]]

def surface_y(x,z):
    base=64 + 1.4*math.sin(x/25)+1.1*math.cos(z/31)+0.6*math.sin((x+z)/13)
    # Build pads and the main trail remain level and walkable. The surrounding
    # forest rises into several broad ridges and falls into shallow valleys.
    pads=[(-40,-36,45,43),(56,81,59,61),(114,169,52,55),(91,202,39,39),(141,234,67,68)]
    if any(abs(x-cx)<=rx and abs(z-cz)<=rz for cx,cz,rx,rz in pads):
        return round(base)
    main=[(0,12),(10,45),(42,76),(78,106),(105,145),(132,184),(145,220),(165,256)]
    if path_dist(x,z,main)<5 or any(path_dist(x,z,p)<4 for p in SECONDARY_PATHS):
        return round(base)
    hills=[(-83,105,20,29),(176,96,24,36),(-49,236,18,27),(79,284,17,24),(207,207,16,22)]
    height=sum(amp*math.exp(-((x-cx)**2+(z-cz)**2)/(2*radius**2)) for cx,cz,amp,radius in hills)
    return round(base+height)

def inside(x,z):
    # Hand-shaped expedition landmass; no generated terrain exists outside it.
    blobs=[(10,30,125,105),(75,125,105,100),(130,205,90,115),(160,270,68,65)]
    return any(((x-cx)/rx)**2+((z-cz)/rz)**2 <= 1 for cx,cz,rx,rz in blobs)

def road_dist(x,z):
    pts=[(0,12),(10,45),(42,76),(78,106),(105,145),(132,184),(145,220),(165,256)]
    best=999
    for (ax,az),(bx,bz) in zip(pts,pts[1:]):
        dx,dz=bx-ax,bz-az; t=max(0,min(1,((x-ax)*dx+(z-az)*dz)/(dx*dx+dz*dz)))
        best=min(best,math.hypot(x-(ax+t*dx),z-(az+t*dz)))
    return best

def path_dist(x,z,pts):
    best=999
    for (ax,az),(bx,bz) in zip(pts,pts[1:]):
        dx,dz=bx-ax,bz-az; den=dx*dx+dz*dz
        t=max(0,min(1,((x-ax)*dx+(z-az)*dz)/den))
        best=min(best,math.hypot(x-(ax+t*dx),z-(az+t*dz)))
    return best

# Layered artificial island, with a rugged boundary and a safe main route.
for x in range(-125,225):
  for z in range(-100,326):
    if not inside(x,z): continue
    y=surface_y(x,z)
    # ravines beside (not across) the critical route
    ravine=(35<x<52 and 130<z<176 and abs(x-(43+0.12*(z-150)))<5)
    if ravine: continue
    W.fill(x,y-4,z,x,y-1,z,'minecraft:stone')
    W.set(x,y,z,'minecraft:grass_block')
    rd=road_dist(x,z)
    if rd<3.2:
        W.set(x,y,z,'minecraft:coarse_dirt' if (x+z)%5 else 'minecraft:gravel')
        if rd<2.5:
            for yy in range(y+1,75): W.set(x,yy,z,'minecraft:air')

def post(x,y,z,height=4):
    W.fill(x,y,z,x,y+height,z,'minecraft:stripped_spruce_log',{'axis':'y'})

def lamp(x,z):
    y=surface_y(x,z)+1; post(x,y,z,3); W.set(x,y+3,z,'minecraft:lantern',{'hanging':'false'})

def tree(x,z):
    y=surface_y(x,z)+1; ht=random.randint(4,7)
    W.fill(x,y,z,x,y+ht,z,'minecraft:spruce_log',{'axis':'y'})
    for yy,r in [(y+ht-3,2),(y+ht-2,2),(y+ht-1,1),(y+ht,1)]:
      for dx in range(-r,r+1):
       for dz in range(-r,r+1):
        if abs(dx)+abs(dz)<=r+1 and (dx or dz): W.set(x+dx,yy,z+dz,'minecraft:spruce_leaves',{'distance':'1','persistent':'true','waterlogged':'false'})

# Forest and understory, kept away from buildings and the navigable road.
for _ in range(310):
    x=random.randint(-105,205); z=random.randint(35,305)
    if inside(x,z) and road_dist(x,z)>8 and not (65<x<102 and 90<z<125) and not (118<x<154 and 168<z<204) and not (143<x<193 and 238<z<282):
        tree(x,z)
for _ in range(700):
    x=random.randint(-112,212); z=random.randint(25,312)
    if inside(x,z) and road_dist(x,z)>5:
        y=surface_y(x,z)+1; W.set(x,y,z,random.choice(['minecraft:fern','minecraft:grass','minecraft:dead_bush']))

# Secondary readable routes: east warehouse spur, west L-3 trail and relay-to-gate cable road.
for pts in SECONDARY_PATHS:
  for x in range(-120,220):
   for z in range(-90,320):
    if inside(x,z) and path_dist(x,z,pts)<2.25:
        y=surface_y(x,z); W.set(x,y,z,'minecraft:gravel' if (x+2*z)%4 else 'minecraft:mud')
        W.fill(x,y+1,z,x,y+4,z,'minecraft:air')

# Rocks, fallen trunks and moss clusters make the forest read as terrain rather than tree rows.
for _ in range(145):
    x=random.randint(-105,210); z=random.randint(35,308)
    if inside(x,z) and road_dist(x,z)>7 and all(path_dist(x,z,p)>5 for p in SECONDARY_PATHS):
        y=surface_y(x,z)+1
        if random.random()<.55:
            W.fill(x,y,z,x+random.randint(0,2),y+random.randint(0,1),z+random.randint(0,2),random.choice(['minecraft:mossy_cobblestone','minecraft:andesite','minecraft:moss_block']))
        else:
            W.fill(x,y,z,x+random.randint(2,5),y,z,'minecraft:spruce_log',{'axis':'x'})

# Base RUBEZH-01: fenced pad, admin block, workshop, storage and comms tower.
W.fill(-35,65,-31,38,90,35,'minecraft:air')
W.fill(-31,64,-27,34,64,31,'minecraft:stone_bricks')
for x in range(-33,37):
    for z in (-29,33):
        if -4<=x<=5 and z==33: continue
        post(x,65,z,2) if x%6==0 else None
for z in range(-29,34):
    for x in (-33,36): post(x,65,z,2) if z%6==0 else None
W.hollow(-25,65,-20,-3,71,-5,'minecraft:gray_concrete','minecraft:stone_bricks')
W.fill(-15,65,-20,-13,68,-20,'minecraft:air'); W.fill(-22,67,-4,-18,68,-4,'minecraft:glass_pane')
W.hollow(5,65,-20,28,72,-4,'minecraft:bricks','minecraft:stone_brick_slab')
W.fill(15,65,-20,17,68,-20,'minecraft:air'); W.fill(8,66,-17,25,66,-16,'minecraft:crafting_table')
W.hollow(-25,65,4,-6,71,24,'minecraft:stone_bricks','minecraft:mossy_stone_bricks')
W.fill(-17,65,4,-15,68,4,'minecraft:air')
for x in (-21,-14,-9): W.fill(x,66,8,x,68,8,'minecraft:barrel')
# future terminal/NPC pad and expedition preparation canopy
W.fill(1,65,5,8,65,12,'minecraft:polished_andesite'); W.set(4,66,8,'minecraft:lodestone')
for x,z in [(11,8),(28,8),(11,25),(28,25)]: post(x,65,z,4)
W.fill(11,69,8,28,69,25,'minecraft:green_wool')
for x in range(11,29):
 for z in range(8,26):
  if (x+z)%7==0: W.set(x,69,z,'minecraft:brown_wool')
# radio tower
for y in range(65,91):
    for x,z in [(31,-25),(35,-25),(31,-21),(35,-21)]: W.set(x,y,z,'minecraft:iron_bars')
    if y%5==0: W.fill(31,y,-25,35,y,-21,'minecraft:iron_bars')
W.fill(29,91,-27,37,91,-19,'minecraft:iron_bars'); W.set(33,92,-23,'minecraft:redstone_torch')
for x,z in [(-28,-24),(-1,-24),(31,1),(-29,28),(33,28),(4,31)]: lamp(x,z)
# Fortified entrance arch and a clearly legible vehicle gate.
W.fill(-7,65,31,-5,70,31,'minecraft:stone_bricks'); W.fill(6,65,31,8,70,31,'minecraft:stone_bricks')
W.fill(-7,70,31,8,71,31,'minecraft:polished_andesite'); W.fill(-4,68,31,5,69,31,'minecraft:iron_bars')
W.fill(-3,65,31,4,67,31,'minecraft:air')

# Detailed interiors: administration, radio room, workshop and living quarters.
W.fill(-23,66,-18,-5,66,-7,'minecraft:spruce_planks')
W.fill(-22,67,-17,-18,67,-15,'minecraft:dark_oak_slab',{'type':'bottom','waterlogged':'false'})
W.set(-20,68,-16,'minecraft:cartography_table'); W.set(-7,67,-17,'minecraft:lectern',{'facing':'south','has_book':'false','powered':'false'})
W.fill(-24,67,-10,-24,69,-7,'minecraft:bookshelf'); W.set(-10,70,-18,'minecraft:lantern',{'hanging':'true'})
W.fill(7,66,-18,26,66,-6,'minecraft:smooth_stone')
for x in (9,14,21,25): W.set(x,67,-16,'minecraft:crafting_table')
W.fill(8,67,-8,13,68,-8,'minecraft:anvil'); W.set(19,70,-12,'minecraft:lantern',{'hanging':'true'})
W.hollow(5,65,13,28,71,28,'minecraft:spruce_planks','minecraft:dark_oak_planks')
W.fill(15,65,13,17,68,13,'minecraft:air'); W.fill(7,67,16,7,68,23,'minecraft:glass_pane')
for x,z,facing in [(9,18,'south'),(14,18,'south'),(20,18,'south'),(25,18,'south')]:
    W.set(x,66,z,'minecraft:white_bed',{'facing':facing,'occupied':'false','part':'foot'})
    W.set(x,66,z+1,'minecraft:white_bed',{'facing':facing,'occupied':'false','part':'head'})
W.fill(9,66,25,24,66,25,'minecraft:spruce_slab',{'type':'bottom','waterlogged':'false'}); W.set(16,70,20,'minecraft:lantern',{'hanging':'true'})
# Dedicated radio cabin below the antenna, with a visible lit control wall.
W.hollow(29,65,-17,42,72,-2,'minecraft:polished_andesite','minecraft:cut_copper')
W.fill(34,65,-17,36,68,-17,'minecraft:air'); W.fill(31,68,-16,40,70,-16,'minecraft:tinted_glass')
W.fill(31,66,-5,40,67,-5,'minecraft:deepslate_tiles')
for x in (32,35,38): W.set(x,68,-5,'minecraft:redstone_lamp',{'lit':'true'})
W.set(35,71,-9,'minecraft:sea_lantern')

# Ruined checkpoints / road storytelling.
for cx,cz in [(22,55),(55,80),(111,151)]:
    y=surface_y(cx,cz)+1; W.fill(cx-4,y,cz-3,cx+4,y,cz+3,'minecraft:cobblestone')
    post(cx-4,y+1,cz-3,3); post(cx+4,y+1,cz-3,2)
    W.fill(cx-4,y+1,cz-3,cx+4,y+1,cz-3,'minecraft:dark_oak_planks')
    W.set(cx+2,y+1,cz+1,'minecraft:campfire',{'lit':'false','signal_fire':'false','waterlogged':'false'})

# Abandoned warehouse and breached perimeter.
W.fill(60,65,84,110,84,133,'minecraft:air')
W.fill(64,64,88,105,64,129,'minecraft:gravel')
for x in range(62,108,4):
    if 78<=x<=86: continue
    W.fill(x,65,86,x,67,86,'minecraft:iron_bars')
for z in range(86,132,4): W.fill(62,65,z,62,67,z,'minecraft:iron_bars'); W.fill(108,65,z,108,67,z,'minecraft:iron_bars')
W.hollow(69,65,94,101,74,124,'minecraft:gray_concrete','minecraft:light_gray_concrete')
W.fill(81,65,94,88,70,94,'minecraft:air'); W.fill(69,70,108,69,72,115,'minecraft:glass_pane')
for x in (74,80,94):
 for z in (101,108,117): W.fill(x,65,z,x,67,z,'minecraft:barrel')
W.fill(92,65,98,98,65,104,'minecraft:yellow_concrete'); W.set(95,66,101,'minecraft:lectern',{'facing':'west','has_book':'false','powered':'false'})
# Loading dock, office partition, inspection desk and abandoned pallet stacks.
W.fill(73,65,91,97,66,93,'minecraft:polished_andesite')
for x in (74,82,90,98): W.fill(x,67,92,x,69,92,'minecraft:dark_oak_fence')
W.fill(88,65,95,88,71,106,'minecraft:light_gray_concrete'); W.fill(88,67,99,88,69,103,'minecraft:glass_pane')
W.fill(90,66,98,97,66,103,'minecraft:spruce_planks'); W.set(93,67,100,'minecraft:cartography_table')
for x,z in [(73,99),(76,99),(99,110),(96,118)]: W.fill(x,65,z,x+1,67,z+1,'minecraft:barrel')
# collapsed corner
W.fill(97,72,119,101,74,124,'minecraft:air'); W.fill(99,65,122,104,67,127,'minecraft:cobblestone')

# Old expedition camp.
def tent(cx,cz,color):
    y=surface_y(cx,cz)+1
    for dz in range(-4,5):
      r=max(0,3-abs(dz)//2)
      for dx in (-r,r): W.set(cx+dx,y+max(1,r),cz+dz,color)
    W.fill(cx-2,y,cz-3,cx+2,y,cz+3,'minecraft:brown_wool')
tent(126,178,'minecraft:green_wool'); tent(144,184,'minecraft:gray_wool'); tent(135,198,'minecraft:brown_wool')
tent(121,194,'minecraft:green_wool'); tent(150,199,'minecraft:gray_wool')
cy=surface_y(135,187)+1; W.set(135,cy,187,'minecraft:campfire',{'lit':'false','signal_fire':'false','waterlogged':'false'})
for x,z in [(131,187),(139,189),(123,191),(147,195)]: W.set(x,surface_y(x,z)+1,z,'minecraft:barrel')
W.set(143,surface_y(143,193)+1,193,'minecraft:lectern',{'facing':'north','has_book':'false','powered':'false'})
# Central planning table, field canopy and scattered equipment.
W.fill(130,65,184,140,65,190,'minecraft:coarse_dirt')
W.fill(132,66,185,138,66,189,'minecraft:spruce_slab',{'type':'bottom','waterlogged':'false'})
for x,z in [(128,182),(142,182),(128,192),(142,192)]: post(x,65,z,3)
W.fill(128,69,182,142,69,192,'minecraft:green_wool')

# Northern signal relay: technical hut, mast, cable trench and damaged fence.
W.fill(96,64,207,123,64,230,'minecraft:stone_bricks')
W.hollow(99,65,211,112,72,226,'minecraft:deepslate_tiles','minecraft:oxidized_cut_copper')
W.fill(104,65,211,106,68,211,'minecraft:air'); W.fill(101,68,225,110,70,225,'minecraft:glass_pane')
W.fill(101,66,214,110,67,214,'minecraft:polished_blackstone'); W.set(104,68,214,'minecraft:redstone_lamp',{'lit':'true'})
for y in range(65,91):
    for x,z in [(116,214),(120,214),(116,218),(120,218)]: W.set(x,y,z,'minecraft:iron_bars')
    if y%5==0: W.fill(116,y,214,120,y,218,'minecraft:iron_bars')
W.fill(114,91,212,122,91,220,'minecraft:iron_bars'); W.set(118,92,216,'minecraft:redstone_torch')
for z in range(220,247):
    W.set(122,65,z,'minecraft:black_concrete'); W.set(123,65,z,'minecraft:yellow_concrete' if z%6==0 else 'minecraft:gray_concrete')
for x,z in [(96,207),(123,207),(96,230),(123,230)]: lamp(x,z)

# Bunker approach and sealed underground-complex entrance.
W.fill(145,63,238,190,66,282,'minecraft:stone')
for x in range(145,191):
 for z in range(238,283):
  if ((x-168)/25)**2+((z-259)/25)**2<1: W.set(x,67,z,'minecraft:moss_block')
W.hollow(151,67,247,185,78,275,'minecraft:deepslate_bricks','minecraft:reinforced_deepslate')
W.fill(158,67,247,177,74,247,'minecraft:polished_deepslate')
W.fill(164,68,247,171,73,247,'minecraft:iron_block')
for x in (163,172): W.fill(x,68,245,x,75,245,'minecraft:yellow_concrete')
W.fill(154,67,239,181,67,246,'minecraft:polished_andesite')
W.set(177,68,244,'minecraft:lodestone') # future story event marker
for x,z in [(151,242),(185,242),(151,277),(185,277)]: lamp(x,z)

# Monumental retaining wall, guarded approach, ventilation stacks and warning chevrons.
W.fill(146,67,247,150,80,275,'minecraft:deepslate_bricks'); W.fill(186,67,247,190,80,275,'minecraft:deepslate_bricks')
for x in range(151,186,4): W.fill(x,79,249,x+1,82,273,'minecraft:stone')
for x in (154,181):
    W.fill(x,68,240,x+2,76,242,'minecraft:iron_block'); W.fill(x-1,76,239,x+3,77,243,'minecraft:polished_deepslate')
for x in range(156,181,4):
    W.set(x,68,245,'minecraft:yellow_concrete'); W.set(x+1,69,245,'minecraft:black_concrete')

# Security office, antechamber teaser and three optional secrets.
W.hollow(181,67,250,193,74,268,'minecraft:polished_deepslate','minecraft:iron_block')
W.fill(181,68,256,181,71,259,'minecraft:glass_pane'); W.fill(185,67,250,188,70,250,'minecraft:air')
W.set(188,68,264,'minecraft:lectern',{'facing':'west','has_book':'false','powered':'false'}); W.set(187,73,258,'minecraft:sea_lantern')
# Secret A: lost callsign cache near the first ruined checkpoint.
W.fill(27,62,59,31,64,63,'minecraft:mossy_cobblestone'); W.set(29,65,61,'minecraft:barrel')
# Secret B: the last campfire in a concealed hollow beyond L-3.
W.fill(151,63,199,158,64,207,'minecraft:podzol'); W.set(154,65,203,'minecraft:campfire',{'lit':'false','signal_fire':'false','waterlogged':'false'}); W.set(157,65,205,'minecraft:barrel')
# Secret C: short service niche beside the complex.
W.fill(188,64,270,199,70,276,'minecraft:stone'); W.fill(189,65,271,198,68,275,'minecraft:air')
W.fill(198,65,272,198,67,274,'minecraft:iron_bars'); W.set(196,65,273,'minecraft:barrel'); W.set(191,68,273,'minecraft:sea_lantern')

# Edge markers warn players before the void, without enclosing exploration.
for a in range(0,360,8):
    rad=math.radians(a); x=round(15+212*math.cos(rad)); z=round(120+220*math.sin(rad))
    if inside(x,z):
        y=surface_y(x,z)+1; W.fill(x,y,z,x,y+2,z,'minecraft:red_concrete')

# Lift the complete chapter above any terrain a mod may generate, then sculpt a
# deep, irregular stone underside so it reads as a real floating landmass rather
# than a thin platform.
W.blocks={(x,y+SKY_SHIFT,z):state for (x,y,z),state in W.blocks.items()}
column_min={}
for x,y,z in W.blocks:
    if inside(x,z): column_min[(x,z)]=min(y,column_min.get((x,z),y))
for (x,z),bottom in column_min.items():
    edge=min(1.0, max(0.0, min(
        1-(((x-cx)/rx)**2+((z-cz)/rz)**2) for cx,cz,rx,rz in [(10,30,125,105),(75,125,105,100),(130,205,90,115),(160,270,68,65)]
    )))
    depth=5+int(18*edge)+((x*17+z*31)&7)
    for yy in range(bottom-1,bottom-depth-1,-1):
        noise=(x*734287+z*912271+yy*31)&15
        block='minecraft:deepslate' if yy<bottom-depth//2 else ('minecraft:tuff' if noise<4 else 'minecraft:stone')
        W.set(x,yy,z,block)
    if edge>.55 and ((x*13+z*7)&31)==0:
        for yy in range(bottom-depth-1,bottom-depth-5-((x+z)&7),-1): W.set(x,yy,z,'minecraft:pointed_dripstone',{'thickness':'base','vertical_direction':'down','waterlogged':'false'})

TRANSPARENT_PREFIX=('minecraft:air','minecraft:glass','minecraft:tinted_glass','minecraft:iron_bars','minecraft:lantern','minecraft:redstone_torch','minecraft:spruce_leaves')
LIGHT_LEVEL={'minecraft:sea_lantern':15,'minecraft:glowstone':15,'minecraft:redstone_lamp':15,'minecraft:lantern':14,'minecraft:redstone_torch':7,'minecraft:campfire':15}
HIGHEST={}
LIGHT_SOURCES=[]
for (lx0,ly0,lz0),lst in W.blocks.items():
    if not lst[0].startswith(TRANSPARENT_PREFIX): HIGHEST[(lx0,lz0)]=max(ly0,HIGHEST.get((lx0,lz0),-999))
    if LIGHT_LEVEL.get(lst[0],0): LIGHT_SOURCES.append((lx0,ly0,lz0,LIGHT_LEVEL[lst[0]]))

def light_arrays(cx,cz,sy,arr):
    sky=[0]*4096; block=[0]*4096
    for lz in range(16):
      for lx in range(16):
        x,z=cx*16+lx,cz*16+lz; highest=HIGHEST.get((x,z),-999)
        for ly in range(16):
            y=sy*16+ly; idx=(ly*16+lz)*16+lx
            if y>highest: sky[idx]=15
    sources=[]
    for x,y,z,lvl in LIGHT_SOURCES:
        if abs(x-(cx*16+8))<=24 and abs(z-(cz*16+8))<=24 and abs(y-(sy*16+8))<=24: sources.append((x,y,z,lvl))
    for ly in range(16):
      for lz in range(16):
       for lx in range(16):
        x,y,z=cx*16+lx,sy*16+ly,cz*16+lz; idx=(ly*16+lz)*16+lx
        block[idx]=max([max(0,lvl-(abs(x-sx)+abs(y-sy0)+abs(z-sz))) for sx,sy0,sz,lvl in sources] or [0])
    def nib(vals):
        return [(vals[n]&15)|((vals[n+1]&15)<<4) for n in range(0,4096,2)]
    return nib(sky),nib(block)

def palette_entry(state):
    name, props=state; items=[string('Name',name)]
    if props: items.append(compound('Properties',[string(k,v) for k,v in props]))
    return compound_payload(items)

def pack_indices(vals,bits):
    per=64//bits; out=[]
    for p in range(0,len(vals),per):
        v=0
        for j,n in enumerate(vals[p:p+per]): v |= n << (j*bits)
        out.append(v)
    return out

def chunk_nbt(cx,cz,bysec):
    sections=[]
    air=('minecraft:air',())
    for sy in sorted(bysec):
        arr=[air]*4096
        for (lx,ly,lz),state in bysec[sy].items(): arr[(ly*16+lz)*16+lx]=state
        pal=[]; ids={}
        for st in arr:
            if st not in ids: ids[st]=len(pal); pal.append(st)
        bs=[list_('palette',10,[palette_entry(st) for st in pal])]
        if len(pal)>1: bs.append(long_array('data',pack_indices([ids[x] for x in arr],max(4,(len(pal)-1).bit_length()))))
        biome=compound('biomes',[list_('palette',8,[s('minecraft:dark_forest')])])
        sky,blocklight=light_arrays(cx,cz,sy,arr)
        sections.append(compound_payload([byte('Y',sy),compound('block_states',bs),biome,byte_array('SkyLight',sky),byte_array('BlockLight',blocklight)]))
    return root([
        integer('DataVersion',DATA_VERSION),integer('xPos',cx),integer('zPos',cz),integer('yPos',0),
        string('Status','minecraft:full'),long('LastUpdate',0),long('InhabitedTime',0),byte('isLightOn',1),
        list_('sections',10,sections),list_('block_entities',10,[]),list_('block_ticks',10,[]),list_('fluid_ticks',10,[]),
        list_('PostProcessing',9,[]),compound('structures',[compound('starts',[]),compound('References',[])])
    ])

def write_regions(world_dir):
    chunks=defaultdict(lambda:defaultdict(dict))
    for (x,y,z),state in W.blocks.items():
        cx,cz=x//16,z//16; sy=y//16
        chunks[(cx,cz)][sy][(x%16,y%16,z%16)]=state
    # Materialize every chunk inside the border as an already-generated empty
    # chunk. This keeps the expedition surrounded by void even in modpacks that
    # replace the world preset after the save is first opened.
    for cx in range(-VOID_CHUNK_RADIUS,VOID_CHUNK_RADIUS+1):
        for cz in range(-VOID_CHUNK_RADIUS,VOID_CHUNK_RADIUS+1):
            chunks[(cx,cz)]
    grouped=defaultdict(dict)
    for (cx,cz),sec in chunks.items(): grouped[(cx//32,cz//32)][(cx,cz)]=chunk_nbt(cx,cz,sec)
    regdir=world_dir/'region'; regdir.mkdir(parents=True,exist_ok=True)
    now=int(time.time())
    for (rx,rz),items in grouped.items():
        header=bytearray(8192); sectors=[]; next_sector=2
        for (cx,cz),raw in sorted(items.items()):
            payload=zlib.compress(raw,9); record=i(len(payload)+1)+b'\x02'+payload
            count=(len(record)+4095)//4096; idx=(cx%32)+(cz%32)*32
            header[idx*4:idx*4+4]=bytes([(next_sector>>16)&255,(next_sector>>8)&255,next_sector&255,count])
            header[4096+idx*4:4100+idx*4]=struct.pack('>I',now)
            sectors.append(record+b'\0'*(count*4096-len(record))); next_sector+=count
        (regdir/f'r.{rx}.{rz}.mca').write_bytes(bytes(header)+b''.join(sectors))
    return len(chunks),len(grouped)

def level_dat():
    flat=compound('generator',[string('type','minecraft:flat'),compound('settings',[
        string('biome','minecraft:the_void'),byte('features',0),byte('lakes',0),
        list_('layers',10,[compound_payload([integer('height',1),string('block','minecraft:air')])]),
        list_('structure_overrides',8,[])
    ])])
    overworld=compound_payload([string('type','minecraft:overworld'),flat])
    nether=compound_payload([string('type','minecraft:the_nether'),compound('generator',[string('type','minecraft:noise'),string('settings','minecraft:nether'),long('seed',71001)])])
    end=compound_payload([string('type','minecraft:the_end'),compound('generator',[string('type','minecraft:noise'),string('settings','minecraft:end'),long('seed',71001)])])
    dims=compound('dimensions',[tag(10,'minecraft:overworld',overworld),tag(10,'minecraft:the_nether',nether),tag(10,'minecraft:the_end',end)])
    data=[integer('DataVersion',DATA_VERSION),string('LevelName','CW Expedition - Chapter I'),long('RandomSeed',71001),
          integer('GameType',2),byte('hardcore',0),byte('allowCommands',1),byte('initialized',1),
          integer('SpawnX',SPAWN[0]),integer('SpawnY',SPAWN[1]),integer('SpawnZ',SPAWN[2]),float_('SpawnAngle',180.0),
          long('Time',6000),long('DayTime',18000),long('LastPlayed',int(time.time()*1000)),integer('version',19133),
          compound('Version',[string('Name','1.20.1'),integer('Id',DATA_VERSION),byte('Snapshot',0),string('Series','main')]),
          compound('WorldGenSettings',[long('seed',71001),byte('generate_features',0),byte('bonus_chest',0),dims]),
          compound('GameRules',[string('doDaylightCycle','false'),string('doWeatherCycle','false'),string('doMobSpawning','false'),string('keepInventory','true'),string('spawnRadius','0')]),
          double('BorderCenterX',40.0),double('BorderCenterZ',120.0),double('BorderSize',WORLD_BORDER_SIZE),
          double('BorderSafeZone',5.0),double('BorderDamagePerBlock',0.2),long('BorderSizeLerpTime',0),double('BorderSizeLerpTarget',WORLD_BORDER_SIZE),
          integer('BorderWarningBlocks',12),integer('BorderWarningTime',15),
          compound('DragonFight',[]),list_('ScheduledEvents',10,[])]
    return gzip.compress(root([compound('Data',data)]),compresslevel=9)

def main():
    if len(sys.argv)!=2: raise SystemExit('usage: generate_cw_expedition.py TARGET_WORLD')
    target=Path(sys.argv[1])
    if target.exists(): raise SystemExit(f'REFUSING: target already exists: {target}')
    target.mkdir(parents=True)
    (target/'level.dat').write_bytes(level_dat())
    (target/'session.lock').write_bytes(struct.pack('>q',int(time.time()*1000)))
    (target/'datapacks').mkdir(); (target/'data').mkdir(); (target/'playerdata').mkdir(); (target/'advancements').mkdir(); (target/'stats').mkdir()
    notes=Path(__file__).with_name('CW_Expedition_Chapter1_Map_Notes.txt')
    if notes.is_file(): shutil.copy2(notes,target/'CW_Expedition_Chapter1_Map_Notes.txt')
    chunks,regions=write_regions(target)
    print(f'CREATED {target}')
    print(f'BLOCKS {len(W.blocks)} CHUNKS {chunks} REGIONS {regions} SPAWN {SPAWN}')

if __name__=='__main__': main()
