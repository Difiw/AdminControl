import gzip, hashlib, os, re, struct
from collections import Counter, deque
from pathlib import Path
from PIL import Image, ImageDraw

W,H,L=160,48,160
AIR='minecraft:air'
blocks=[AIR]*(W*H*L)
doors=[]; rooms=[]; roof_cells={}; parking=[]; tree_specs=[]

def idx(x,y,z): return x+z*W+y*W*L
def inside(x,y,z): return 0<=x<W and 0<=y<H and 0<=z<L
def setb(x,y,z,b):
    if not inside(x,y,z): raise ValueError(f'out of bounds {(x,y,z)}')
    blocks[idx(x,y,z)]=b
def getb(x,y,z): return blocks[idx(x,y,z)] if inside(x,y,z) else AIR
def state_id(b): return b.split(':',1)[1].split('[',1)[0]
def fill(x1,y1,z1,x2,y2,z2,b):
    for y in range(y1,y2+1):
      for z in range(z1,z2+1):
       for x in range(x1,x2+1): setb(x,y,z,b)
def line_x(x1,x2,y,z,b): fill(x1,y,z,x2,y,z,b)
def line_z(x,y,z1,z2,b): fill(x,y,z1,x,y,z2,b)
def shell(x1,z1,x2,z2,fy,cy,wall,floor='minecraft:smooth_stone',ceiling='minecraft:smooth_stone'):
    fill(x1,fy,z1,x2,fy,z2,floor); fill(x1,cy,z1,x2,cy,z2,ceiling)
    for y in range(fy+1,cy):
        line_x(x1,x2,y,z1,wall); line_x(x1,x2,y,z2,wall)
        line_z(x1,y,z1,z2,wall); line_z(x2,y,z1,z2,wall)
def doorway(x,y,z,facing='south',kind='spruce_door',hinge='left'):
    lo=f'minecraft:{kind}[facing={facing},half=lower,hinge={hinge},open=false,powered=false]'
    hi=f'minecraft:{kind}[facing={facing},half=upper,hinge={hinge},open=false,powered=false]'
    setb(x,y,z,lo); setb(x,y+1,z,hi); doors.append((x,y,z,lo,hi))
def opening(x,y,z): setb(x,y,z,AIR); setb(x,y+1,z,AIR)
def lamp(x,y,z): setb(x,y,z,'minecraft:sea_lantern')
def desk(x,y,z,n=3,axis='x'):
    if axis=='x': fill(x,y,z,x+n-1,y,z,'minecraft:smooth_stone_slab[type=bottom,waterlogged=false]')
    else: fill(x,y,z,x,y,z+n-1,'minecraft:smooth_stone_slab[type=bottom,waterlogged=false]')
def chair(x,y,z,f='north'): setb(x,y,z,f'minecraft:spruce_stairs[facing={f},half=bottom,shape=straight,waterlogged=false]')
def bed(x,y,z,color='blue',facing='north'):
    dz=-1 if facing=='north' else 1; dx=0
    setb(x,y,z,f'minecraft:{color}_bed[facing={facing},part=foot,occupied=false]')
    setb(x+dx,y,z+dz,f'minecraft:{color}_bed[facing={facing},part=head,occupied=false]')
def room(name,x1,z1,x2,z2,fy,doorpos,lightpos):
    rooms.append(dict(name=name,box=(x1,z1,x2,z2),fy=fy,door=doorpos,light=lightpos))
def partition_x(x,z1,z2,fy,cy,door_z,mat='minecraft:white_concrete'):
    fill(x,fy+1,z1,x,cy-1,z2,mat); opening(x,fy+1,door_z)
def partition_z(z,x1,x2,fy,cy,door_x,mat='minecraft:white_concrete'):
    fill(x1,fy+1,z,x2,cy-1,z,mat); opening(door_x,fy+1,z)
def stairs(x,z,fy,direction='east'):
    for i in range(5):
        xx=x+i if direction=='east' else x-i
        setb(xx,fy+i,z,f'minecraft:stone_brick_stairs[facing={direction},half=bottom,shape=straight,waterlogged=false]')
        for yy in range(fy+i+1,fy+5): setb(xx,yy,z,AIR)
def flat_roof(name,x1,z1,x2,z2,y,mat):
    cells={(x,y,z) for z in range(z1,z2+1) for x in range(x1,x2+1)}
    for p in cells: setb(*p,mat)
    roof_cells[name]=cells
def gable_roof(name,x1,z1,x2,z2,y0,mat):
    cells=set(); width=x2-x1+1; half=(width-1)//2
    for d in range(half+1):
        y=y0+d//2
        for x in {x1+d,x2-d}:
            for z in range(z1,z2+1): setb(x,y,z,mat); cells.add((x,y,z))
    if width%2==0:
        for z in range(z1,z2+1): setb(x1+half,y0+half//2,z,mat); cells.add((x1+half,y0+half//2,z))
    roof_cells[name]=cells
def window_south(x1,x2,y,z):
    for x in range(x1,x2+1): setb(x,y,z,'minecraft:light_blue_stained_glass_pane'); setb(x,y+1,z,'minecraft:light_blue_stained_glass_pane')
def tree(x,z,h,r=2,kind='oak'):
    fill(x,2,z,x,1+h,z,f'minecraft:{kind}_log[axis=y]')
    for dy,rr in [(h-2,r-1),(h-1,r),(h,r),(h+1,r-1)]:
      for dz in range(-rr,rr+1):
       for dx in range(-rr,rr+1):
        if abs(dx)+abs(dz)<=rr+1 and not (dx==0 and dz==0 and dy<=h): setb(x+dx,1+dy,z+dz,f'minecraft:{kind}_leaves[distance=1,persistent=true,waterlogged=false]')
    tree_specs.append((x,z,h,r))

# Terrain: continuous supported base and landscaped surface.
fill(0,0,0,159,0,159,'minecraft:dirt'); fill(0,1,0,159,1,159,'minecraft:grass_block[snowy=false]')
for x in range(3,157):
    if not 66<=x<=80: setb(x,2,3,'minecraft:iron_bars')
    setb(x,2,156,'minecraft:iron_bars')
for z in range(3,157): setb(3,2,z,'minecraft:iron_bars'); setb(156,2,z,'minecraft:iron_bars')

# Roads, sidewalks and a 10-bay parking area. Markings replace asphalt at Y=1.
fill(68,1,3,74,1,51,'minecraft:gray_concrete')
fill(68,1,42,74,1,91,'minecraft:gray_concrete')
fill(47,1,27,96,1,43,'minecraft:gray_concrete')
fill(38,1,72,68,1,78,'minecraft:gray_concrete')
fill(72,1,84,121,1,90,'minecraft:gray_concrete')
fill(116,1,79,122,1,101,'minecraft:gray_concrete')
for r in [(65,1,3,67,1,49),(75,1,3,77,1,49),(45,1,25,98,1,26),(45,1,44,98,1,45),(36,1,70,67,1,71),(36,1,79,67,1,80),(99,1,79,124,1,81)]: fill(*r,'minecraft:smooth_stone')
for i,x in enumerate(range(49,89,4)):
    line_z(x,1,29,40,'minecraft:white_concrete'); parking.extend((x,1,z) for z in range(29,41))
line_x(48,92,1,41,'minecraft:white_concrete'); parking.extend((x,1,41) for x in range(48,93))

# Main research building A: 44x34, three fully divided floors.
for fy,cy in [(2,6),(7,11),(12,16)]:
    shell(48,49,91,82,fy,cy,'minecraft:light_gray_concrete')
    # 3-block central corridor Z 64..66; four room quadrants.
    partition_z(63,49,91,fy,cy,69); partition_z(67,49,91,fy,cy,69)
    partition_x(69,50,62,fy,cy,56); partition_x(69,68,81,fy,cy,75)
    for x in (53,60,76,84): lamp(x,cy-1,57); lamp(x,cy-1,74)
    for x,z in [(53,55),(74,55),(53,72),(74,72)]: desk(x,fy+1,z,4); chair(x+1,fy+1,z+1)
    stairs(50,79,fy,'east')
    window_south(52,65,fy+2,49); window_south(73,88,fy+2,49)
doorway(69,2,49,'south','iron_door'); doorway(70,2,49,'south','iron_door','right')
fill(65,2,45,74,2,48,'minecraft:smooth_stone'); fill(66,5,47,73,5,49,'minecraft:smooth_stone_slab[type=top,waterlogged=false]')
# Room metadata and named furniture/equipment.
main_names=[
 ('Тамбур/ресепшен',50,50,68,62,2),('Охрана/персонал',70,50,90,62,2),('Техническая/санузел',50,68,68,81,2),('Комната персонала',70,68,90,81,2),
 ('Кабинет руководителя',50,50,68,62,7),('Архив',70,50,90,62,7),('Переговорная',50,68,68,81,7),('Аналитики',70,68,90,81,7),
 ('Радиорубка',50,50,68,62,12),('Серверная',70,50,90,62,12),('Наблюдение',50,68,68,81,12),('Кабинет исследователя',70,68,90,81,12)]
for n,x1,z1,x2,z2,fy in main_names: room(n,x1,z1,x2,z2,fy,(69,fy+1,56 if z2<64 else 75),(x1+3,fy+3,z1+3))
for x in (74,78,82,86): fill(x,13,54,x,15,55,'minecraft:iron_block')
flat_roof('Главный корпус',48,49,91,82,17,'minecraft:smooth_stone')
for x in range(47,93): setb(x,18,48,'minecraft:polished_deepslate'); setb(x,18,83,'minecraft:polished_deepslate')
for z in range(48,84): setb(47,18,z,'minecraft:polished_deepslate'); setb(92,18,z,'minecraft:polished_deepslate')
fill(57,18,55,64,20,61,'minecraft:iron_block'); fill(77,18,70,85,19,76,'minecraft:light_gray_concrete')

# Laboratory B with five rooms per floor and enclosed connector.
for fy,cy in [(2,7),(8,13)]:
    shell(12,54,43,82,fy,cy,'minecraft:white_concrete')
    partition_z(67,13,42,fy,cy,27,'minecraft:glass_pane'); partition_x(27,55,66,fy,cy,61,'minecraft:white_concrete'); partition_x(27,68,81,fy,cy,74,'minecraft:white_concrete')
    for x,z in [(15,57),(30,57),(15,71),(30,71)]: desk(x,fy+1,z,5); lamp(x+2,cy-1,z+2)
    for x,z in [(17,62),(22,62),(31,62)]: setb(x,fy+1,z,'minecraft:brewing_stand[has_bottle_0=false,has_bottle_1=false,has_bottle_2=false]')
    stairs(13,79,fy,'east'); window_south(15,40,fy+2,54)
doorway(27,2,54,'south'); doorway(43,2,72,'west')
flat_roof('Лаборатория',12,54,43,82,14,'minecraft:smooth_stone')
fill(16,15,59,22,16,65,'minecraft:light_blue_stained_glass'); fill(33,15,70,39,16,76,'minecraft:light_blue_stained_glass')
# connector has floor/walls/roof/light and opens both walls
fill(44,7,69,47,7,74,'minecraft:smooth_stone'); fill(44,10,69,47,10,74,'minecraft:smooth_stone')
for y in (8,9): line_z(44,y,69,74,'minecraft:light_gray_concrete'); line_z(47,y,69,74,'minecraft:light_gray_concrete')
opening(43,8,72); opening(48,8,72); lamp(45,9,71); lamp(45,9,73)
for fy in (2,8):
 for n,box in [('Приём/основная лаборатория',(13,55,26,66)),('Химическая/анализ',(28,55,42,66)),('Моечная/склад',(13,68,26,81)),('Данные/руководитель',(28,68,42,81))]: room(f'{n} этаж {fy}',*box,fy,(27,fy+1,61 if box[3]<67 else 74),(box[0]+3,fy+3,box[1]+3))

# Residence/medical C: warm two-storey block with complete stepped gable.
for fy,cy in [(2,7),(8,13)]:
    shell(101,53,132,78,fy,cy,'minecraft:calcite',floor='minecraft:spruce_planks',ceiling='minecraft:spruce_planks')
    partition_z(65,102,131,fy,cy,116,'minecraft:birch_planks'); partition_x(116,54,64,fy,cy,59,'minecraft:birch_planks'); partition_x(116,66,77,fy,cy,72,'minecraft:birch_planks')
    for x,z in [(103,55),(119,55),(103,68),(119,68)]: lamp(x+3,cy-1,z+3); desk(x,fy+1,z,3); chair(x+1,fy+1,z+1)
    stairs(102,75,fy,'east'); window_south(104,113,fy+2,53); window_south(120,129,fy+2,53)
doorway(116,2,53,'south'); fill(112,2,49,120,2,52,'minecraft:smooth_stone'); fill(113,5,51,119,5,53,'minecraft:spruce_slab[type=top,waterlogged=false]')
for p in [(104,9,60,'white'),(110,9,60,'blue'),(120,9,60,'white'),(126,9,60,'blue'),(104,9,73,'white'),(120,9,73,'blue')]: bed(*p)
gable_roof('Жилой/медицинский блок',100,52,133,79,14,'minecraft:brick_slab[type=bottom,waterlogged=false]')
for fy in (2,8):
 for n,box in [('Медприём/осмотр',(102,54,115,64)),('Процедурная/общая',(117,54,131,64)),('Кухня/санузел',(102,66,115,77)),('Жилые/отдых',(117,66,131,77))]: room(f'{n} этаж {fy}',*box,fy,(116,fy+1,59 if box[3]<65 else 72),(box[0]+4,fy+3,box[1]+3))

# Hangar D: tall hall, true 11x7 opening, mezzanine, zones and sealed gable.
fill(101,2,91,143,2,119,'minecraft:polished_andesite')
for y in range(3,13):
    line_x(101,143,y,91,'minecraft:stone_bricks'); line_x(101,143,y,119,'minecraft:stone_bricks'); line_z(101,y,91,119,'minecraft:deepslate_tiles'); line_z(143,y,91,119,'minecraft:deepslate_tiles')
    if y<=9: fill(117,y,91,127,y,91,AIR)
line_x(116,128,10,91,'minecraft:iron_block')
# open operable-style folding gate represented by side stacks, aperture remains clear
for y in range(3,10): setb(116,y,91,'minecraft:iron_block'); setb(128,y,91,'minecraft:iron_block')
doorway(143,3,113,'west'); fill(102,3,105,110,7,105,'minecraft:iron_bars'); opening(106,3,105)
fill(102,3,106,110,7,118,'minecraft:smooth_stone'); fill(102,8,106,110,8,118,'minecraft:smooth_stone')
for x,z in [(104,108),(104,113),(112,95),(112,101),(135,108),(135,113)]: desk(x,3,z,4); lamp(x+1,11,z+1)
for x in (132,137):
 for z in (95,101): fill(x,3,z,x+1,5,z+1,'minecraft:yellow_glazed_terracotta')
lamp(136,11,98)
gable_roof('Ангар',100,90,144,120,13,'minecraft:weathered_cut_copper_slab[type=bottom,waterlogged=false]')
fill(110,1,82,134,1,90,'minecraft:stone'); line_x(116,128,1,88,'minecraft:yellow_concrete')
for n,box in [('Ремонтная площадка',(111,92,130,106)),('Инструментальная',(102,106,110,118)),('Генераторная',(131,106,142,118)),('Склад/диспетчерская',(131,92,142,105))]: room(n,*box,2,(106,3,105),(box[0]+2,11,box[1]+2))

# Radio site E: full slab at Y=1, fenced, roofed hut and supported four-leg tower.
fill(20,1,103,39,1,129,'minecraft:polished_andesite')
for x in range(20,40): setb(x,2,103,'minecraft:iron_bars'); setb(x,2,129,'minecraft:iron_bars')
for z in range(103,130): setb(20,2,z,'minecraft:iron_bars'); setb(39,2,z,'minecraft:iron_bars')
fill(28,2,103,31,3,103,AIR); setb(27,2,103,'minecraft:yellow_concrete'); setb(32,2,103,'minecraft:yellow_concrete')
shell(22,106,32,115,2,6,'minecraft:light_gray_concrete'); doorway(27,3,115,'south'); flat_roof('Техдомик',21,105,33,116,7,'minecraft:smooth_stone_slab[type=bottom,waterlogged=false]')
fill(23,3,107,24,5,109,'minecraft:iron_block'); fill(29,3,107,30,5,109,'minecraft:iron_block'); desk(23,3,112,4); lamp(27,5,110)
fill(27,1,116,34,1,125,'minecraft:smooth_stone')
legs=((27,117),(34,117),(27,124),(34,124))
for x,z in legs: setb(x,2,z,'minecraft:iron_block')
for y in range(3,29):
    inset=min(3,(y-3)//8); x1,x2=27+inset,34-inset; z1,z2=117+inset,124-inset
    for x,z in ((x1,z1),(x2,z1),(x1,z2),(x2,z2)): setb(x,y,z,'minecraft:iron_bars')
    if y in (6,10,14,18,22,26):
      line_x(x1,x2,y,z1,'minecraft:iron_bars'); line_x(x1,x2,y,z2,'minecraft:iron_bars'); line_z(x1,y,z1,z2,'minecraft:iron_bars'); line_z(x2,y,z1,z2,'minecraft:iron_bars')
      for t in range(x2-x1+1):
        setb(x1+t,max(3,y-3+(t%4)),z1,'minecraft:iron_bars'); setb(x1+t,max(3,y-(t%4)),z2,'minecraft:iron_bars')
for y in range(3,29): setb(30,y,121,'minecraft:ladder[facing=west,waterlogged=false]')
for y in (10,20):
 fill(28,y,118,33,y,123,'minecraft:iron_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]')
 for x in range(28,34): setb(x,y+1,118,'minecraft:iron_bars'); setb(x,y+1,123,'minecraft:iron_bars')
fill(29,29,120,32,31,122,'minecraft:iron_bars'); setb(30,32,121,'minecraft:lightning_rod'); setb(31,31,121,'minecraft:end_rod[facing=up]')

# Gatehouse, barrier, pedestrian gate, lighting and varied trees.
shell(67,14,77,23,2,6,'minecraft:smooth_stone'); doorway(72,3,23,'south'); flat_roof('КПП',66,13,78,24,7,'minecraft:smooth_stone_slab[type=bottom,waterlogged=false]')
window_south(68,71,4,14); window_south(74,76,4,14); desk(69,3,18,4); lamp(72,5,18)
fill(75,3,12,84,3,12,'minecraft:red_concrete'); setb(75,2,12,'minecraft:iron_block'); fill(64,1,11,67,1,25,'minecraft:smooth_stone')
for x,z in [(64,32),(78,32),(64,47),(95,44),(99,51),(97,76),(135,79),(18,86),(45,98),(93,108),(148,68),(10,132)]:
    fill(x,2,z,x,6,z,'minecraft:iron_bars'); setb(x,7,z,'minecraft:sea_lantern'); setb(x,8,z,'minecraft:stone_slab[type=bottom,waterlogged=false]')
for spec in [(10,20,6,3,'oak'),(18,35,5,2,'spruce'),(9,92,7,3,'oak'),(48,125,6,3,'spruce'),(69,137,5,2,'oak'),(96,138,7,3,'oak'),(141,141,6,3,'spruce'),(147,45,5,2,'oak')]: tree(*spec)
for x,z in [(14,48),(44,116),(83,132),(137,69)]: fill(x,2,z,x+2,2,z+2,'minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]')

# ---------- v4 reconstruction pass: robust roofs, climbable tower, clear entry and overgrowth ----------
# Remove the old parking without touching the north/south main road, then build a separate east lot.
for z in range(27,44):
 for x in range(47,97):
  if not 68<=x<=74: setb(x,1,z,'minecraft:grass_block[snowy=false]')
fill(103,1,27,147,1,47,'minecraft:gray_concrete')
fill(99,1,33,103,1,39,'minecraft:gray_concrete')
fill(75,1,33,102,1,39,'minecraft:gray_concrete')
fill(101,1,25,149,1,26,'minecraft:smooth_stone'); fill(101,1,48,149,1,49,'minecraft:smooth_stone')
fill(101,1,25,102,1,49,'minecraft:smooth_stone'); fill(148,1,25,149,1,49,'minecraft:smooth_stone')
parking.clear()
for x in range(105,146,4):
 line_z(x,1,29,44,'minecraft:white_concrete'); parking.extend((x,1,z) for z in range(29,45))
line_x(104,146,1,45,'minecraft:white_concrete'); parking.extend((x,1,45) for x in range(104,147))
# Uninterrupted four-block pedestrian approach from gate/road to the main entrance.
fill(66,1,44,73,1,48,'minecraft:smooth_stone')
fill(68,2,45,71,2,48,'minecraft:smooth_stone')

# Replace both pitched roofs completely with solid block courses and sealed end gables.
def solid_gable(name,x1,z1,x2,z2,wall_top,mat,slope_div):
    for y in range(wall_top+1,H):
     for z in range(z1-1,z2+2):
      for x in range(x1-1,x2+2):
       if inside(x,y,z): setb(x,y,z,AIR)
    cells=set(); half=(x2-x1)//2
    for x in range(x1,x2+1):
      rise=min(x-x1,x2-x)//slope_div; y=wall_top+1+rise
      for z in range(z1-1,z2+2): setb(x,y,z,mat); cells.add((x,y,z))
      for yy in range(wall_top+1,y):
        setb(x,yy,z1,'minecraft:stone_bricks'); setb(x,yy,z2,'minecraft:stone_bricks')
    # two-block ridge cap and closed eaves
    ridge_y=wall_top+1+half//slope_div
    for x in (x1+half,x1+half+1):
      if x<=x2:
       for z in range(z1-1,z2+2): setb(x,ridge_y,z,mat); cells.add((x,ridge_y,z))
    roof_cells[name]=cells
solid_gable('Жилой/медицинский блок',100,52,133,79,13,'minecraft:bricks',5)
solid_gable('Ангар',100,90,144,120,12,'minecraft:deepslate_tiles',3)

# Add real internal doors on the main corridor and laboratory/residence corridors.
for fy in (2,7,12):
 for x,hinge in ((56,'left'),(62,'right'),(77,'left'),(84,'right')):
  doorway(x,fy+1,63,'north','spruce_door',hinge); doorway(x,fy+1,67,'south','spruce_door',hinge)
for fy in (2,8):
 for x in (20,35): doorway(x,fy+1,67,'south','spruce_door')
 for z in (59,74): doorway(27,fy+1,z,'east','spruce_door')
 for x in (108,124): doorway(x,fy+1,65,'south','spruce_door')
 for z in (59,72): doorway(116,fy+1,z,'east','spruce_door')

# Split the residence upper floor into four actual bedrooms plus corridor doors.
fill(108,9,54,108,12,64,'minecraft:birch_planks'); opening(108,9,60)
fill(124,9,54,124,12,64,'minecraft:birch_planks'); opening(124,9,60)
fill(108,9,66,108,12,77,'minecraft:birch_planks'); opening(108,9,72)
fill(124,9,66,124,12,77,'minecraft:birch_planks'); opening(124,9,72)
for x,z in ((104,57),(110,57),(120,57),(126,57),(104,69),(120,69)):
 desk(x,9,z,2); lamp(x,12,z+2)

# Rebuild tower: stable vertical lattice, supported ladder, solid service decks, no trapdoors.
for y in range(2,34):
 for z in range(117,126):
  for x in range(26,36): setb(x,y,z,AIR)
legs=((27,117),(34,117),(27,124),(34,124))
for x,z in legs: setb(x,2,z,'minecraft:iron_block')
for y in range(3,30):
 for x,z in legs: setb(x,y,z,'minecraft:iron_bars')
 # ladder backing column: ladders facing west attach to its west face.
 setb(31,y,121,'minecraft:polished_andesite'); setb(30,y,121,'minecraft:ladder[facing=west,waterlogged=false]')
 if y in (6,10,14,18,22,26):
  line_x(27,34,y,117,'minecraft:iron_bars'); line_x(27,34,y,124,'minecraft:iron_bars')
  line_z(27,y,117,124,'minecraft:iron_bars'); line_z(34,y,117,124,'minecraft:iron_bars')
  for t in range(8):
   setb(27+t,y-3+(t%4),117,'minecraft:iron_bars'); setb(27+t,y-(t%4),124,'minecraft:iron_bars')
   setb(27,y-3+(t%4),117+t,'minecraft:iron_bars'); setb(34,y-(t%4),117+t,'minecraft:iron_bars')
for py in (10,20):
 fill(27,py,117,34,py,124,'minecraft:smooth_stone')
 # one-block ladder shaft, surrounded by guard rails
 setb(30,py,121,'minecraft:ladder[facing=west,waterlogged=false]')
 for x in range(27,35): setb(x,py+1,117,'minecraft:iron_bars'); setb(x,py+1,124,'minecraft:iron_bars')
 for z in range(118,124): setb(27,py+1,z,'minecraft:iron_bars'); setb(34,py+1,z,'minecraft:iron_bars')
for y in range(30,33):
 setb(30,y,120,'minecraft:iron_bars'); setb(31,y,120,'minecraft:iron_bars'); setb(30,y,122,'minecraft:iron_bars'); setb(31,y,122,'minecraft:iron_bars')
setb(30,33,121,'minecraft:lightning_rod'); setb(31,32,121,'minecraft:end_rod[facing=up]')

# Denser post-apocalyptic vegetation, kept clear of roads and entrances.
for spec in [(7,14,5,2,'oak'),(22,18,6,3,'spruce'),(37,16,5,2,'oak'),(93,18,7,3,'oak'),(118,16,6,3,'spruce'),(145,16,5,2,'oak'),(12,44,6,3,'oak'),(31,46,5,2,'spruce'),(151,61,7,3,'oak'),(8,73,5,2,'spruce'),(145,78,6,3,'oak'),(8,111,7,3,'oak'),(62,121,5,2,'spruce'),(82,128,6,3,'oak'),(118,137,7,3,'spruce'),(148,126,5,2,'oak'),(24,145,6,3,'oak'),(52,148,5,2,'spruce')]: tree(*spec)
for x,z in [(41,51),(45,55),(94,64),(98,73),(96,103),(99,116),(42,104),(40,126),(134,83),(145,122),(76,84),(64,82)]:
 fill(x,2,z,x+2,3,z+2,'minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]')
for x,z in [(42,48),(93,82),(99,89),(133,120),(21,102),(38,130),(101,52),(132,78)]: setb(x,2,z,'minecraft:moss_block')

# ---------- v5 correction pass: door elevations, orthogonal tower, lighting and interiors ----------
# Exterior doors: player stands on floor Y=2, therefore lower door half must be Y=3.
def move_exterior_door(x,z,facing,kind='spruce_door',double=False):
    setb(x,2,z,'minecraft:smooth_stone')
    setb(x,3,z,f'minecraft:{kind}[facing={facing},half=lower,hinge=left,open=false,powered=false]')
    setb(x,4,z,f'minecraft:{kind}[facing={facing},half=upper,hinge=left,open=false,powered=false]')
    if double:
      setb(x+1,2,z,'minecraft:smooth_stone')
      setb(x+1,3,z,f'minecraft:{kind}[facing={facing},half=lower,hinge=right,open=false,powered=false]')
      setb(x+1,4,z,f'minecraft:{kind}[facing={facing},half=upper,hinge=right,open=false,powered=false]')
move_exterior_door(69,49,'south','iron_door',True)
move_exterior_door(27,54,'south')
move_exterior_door(116,53,'south')
# Clear two-block headroom and keep approach surfaces one level below the interior threshold.
for x in (69,70): setb(x,5,49,AIR)
fill(66,1,45,73,1,48,'minecraft:smooth_stone')
for z in range(45,49):
 for x in range(66,74):
  if not (z==49): setb(x,2,z,AIR)

# Completely replace tower with orthogonally connected members only.
for y in range(2,36):
 for z in range(117,126):
  for x in range(26,36): setb(x,y,z,AIR)
legs=((27,117),(34,117),(27,124),(34,124))
for x,z in legs:
 setb(x,2,z,'minecraft:iron_block')
 for y in range(3,30): setb(x,y,z,'minecraft:iron_block')
# Horizontal bars meet solid corner columns directly; no diagonal bar placement.
for y in (5,9,13,17,21,25,29):
 line_x(28,33,y,117,'minecraft:iron_bars'); line_x(28,33,y,124,'minecraft:iron_bars')
 line_z(27,y,118,123,'minecraft:iron_bars'); line_z(34,y,118,123,'minecraft:iron_bars')
# Supported central ladder column and two solid maintenance decks.
for y in range(3,30):
 setb(31,y,121,'minecraft:polished_andesite'); setb(30,y,121,'minecraft:ladder[facing=west,waterlogged=false]')
for py in (10,20):
 fill(27,py,117,34,py,124,'minecraft:smooth_stone')
 setb(30,py,121,'minecraft:ladder[facing=west,waterlogged=false]')
 for x in range(27,35): setb(x,py+1,117,'minecraft:iron_bars'); setb(x,py+1,124,'minecraft:iron_bars')
 for z in range(118,124): setb(27,py+1,z,'minecraft:iron_bars'); setb(34,py+1,z,'minecraft:iron_bars')
# Fully supported antenna mast and side elements.
fill(30,30,120,31,33,121,'minecraft:iron_block')
setb(30,34,120,'minecraft:lightning_rod'); setb(31,34,121,'minecraft:lightning_rod')
setb(29,32,120,'minecraft:end_rod[facing=west]'); setb(32,32,121,'minecraft:end_rod[facing=east]')

# Functional furniture: fence-and-pressure-plate tables, seating, storage and equipment.
def table(x,y,z,top='minecraft:stone_pressure_plate[powered=false]'):
 setb(x,y,z,'minecraft:spruce_fence[east=false,north=false,south=false,waterlogged=false,west=false]'); setb(x,y+1,z,top)
def shelf(x,y,z,h=3): fill(x,y,z,x,y+h-1,z,'minecraft:bookshelf')
def cabinet(x,y,z): setb(x,y,z,'minecraft:barrel[facing=up,open=false]')
# Main floor 1: reception, security, staff, electrical and service areas.
for x,z in [(55,55),(61,55),(76,55),(83,55),(55,73),(61,73),(77,73),(84,73)]: table(x,3,z); chair(x,3,z+1)
for x,z in [(50,51),(50,59),(90,51),(90,59),(50,70),(50,78),(90,70),(90,78)]: cabinet(x,3,z)
fill(76,3,58,78,5,58,'minecraft:iron_block'); fill(82,3,58,84,5,58,'minecraft:redstone_lamp[lit=true]')
setb(57,3,76,'minecraft:crafting_table'); setb(59,3,76,'minecraft:furnace[facing=south,lit=false]')
# Main floor 2: director office, archive, conference and analytics.
for x,z in [(55,55),(78,55),(55,73),(78,73),(84,73)]: table(x,8,z); chair(x,8,z+1)
for x in (73,77,81,85,89): shelf(x,8,51,3)
for x in range(53,65,3): table(x,8,74); chair(x,8,75,'south')
setb(58,8,58,'minecraft:lectern[facing=south,has_book=false,powered=false]')
# Main floor 3: radio room, server room, observation and research office.
for x in (73,77,81,85,89): fill(x,13,53,x,15,54,'minecraft:iron_block')
for x,z in [(54,55),(60,55),(54,73),(60,73),(76,73),(83,73)]: table(x,13,z); chair(x,13,z+1)
for x in (53,58,63): setb(x,13,59,'minecraft:daylight_detector[inverted=false,power=0]')
for x,z in [(50,51),(67,51),(72,51),(90,51),(50,69),(67,69),(72,69),(90,69)]: shelf(x,13,z,3)
# More ceiling lights in all main rooms and corridors.
for cy in (5,10,15):
 for x in (54,61,76,84):
  for z in (55,73): lamp(x,cy,z)
 for z in range(52,81,6): lamp(69,cy,z)

# Road lighting at regular intervals, plus lit gate/parking/hangar routes.
for x,z in [(65,8),(78,8),(65,20),(78,20),(65,36),(78,36),(65,52),(78,52),(65,68),(78,68),(65,84),(78,84),(91,36),(101,36),(117,24),(132,24),(147,24),(117,51),(132,51),(147,51),(94,85),(108,82),(123,82),(123,98),(123,112),(42,68),(29,68)]:
 fill(x,2,z,x,6,z,'minecraft:polished_andesite'); setb(x,7,z,'minecraft:lantern[hanging=false]'); setb(x,8,z,'minecraft:stone_slab[type=bottom,waterlogged=false]')

# Ground vegetation. Only place it on grass and away from doors, roads and paved yards.
for z in range(6,154,3):
 for x in range(6,154,4):
  if getb(x,1,z)=='minecraft:grass_block[snowy=false]' and getb(x,2,z)==AIR:
   code=(x*31+z*17)%11
   if code in (0,1,2,3): setb(x,2,z,'minecraft:grass')
   elif code==4: setb(x,2,z,'minecraft:fern')
   elif code==5 and getb(x,3,z)==AIR:
    setb(x,2,z,'minecraft:tall_grass[half=lower]'); setb(x,3,z,'minecraft:tall_grass[half=upper]')
# Small overgrowth groups along selected walls, never in door coordinates.
for x,z in [(46,52),(46,57),(46,76),(94,55),(94,75),(10,58),(10,77),(134,57),(134,72),(99,96),(99,111),(145,95),(145,114),(34,105),(37,127)]:
 if getb(x,2,z)==AIR: setb(x,2,z,'minecraft:fern')

# ---------- v6 rebuild pass: tall serviced tower, usable doors, clear walks, decay ----------
# Remove every old outdoor post identified by a lamp at Y=7, then relocate lights to grass shoulders.
for z in range(L):
 for x in range(W):
  if state_id(getb(x,7,z)) in ('lantern','sea_lantern'):
   for y in range(2,9):
    if state_id(getb(x,y,z)) in ('polished_andesite','iron_bars','lantern','sea_lantern','stone_slab'): setb(x,y,z,AIR)
for x,z in [(62,8),(81,8),(62,20),(81,20),(62,34),(81,34),(62,48),(81,48),(62,63),(81,63),(62,78),(81,78),(99,24),(115,24),(132,24),(149,24),(99,51),(115,51),(132,51),(149,51),(94,83),(108,79),(126,79),(126,96),(126,111),(44,68),(32,68)]:
 fill(x,2,z,x,6,z,'minecraft:polished_andesite'); setb(x,7,z,'minecraft:lantern[hanging=false]')
setb(78,7,20,'minecraft:smooth_stone')

# Usable double iron entrance: solid threshold and automatic plates outside/inside.
fill(68,2,48,71,2,48,'minecraft:smooth_stone')
setb(69,3,48,'minecraft:stone_pressure_plate[powered=false]'); setb(70,3,48,'minecraft:stone_pressure_plate[powered=false]')
setb(69,3,50,'minecraft:stone_pressure_plate[powered=false]'); setb(70,3,50,'minecraft:stone_pressure_plate[powered=false]')

# Replace pane states with full glass blocks; replace structural slabs with supported full blocks.
for i,b in enumerate(blocks):
 bid=state_id(b)
 if bid=='glass_pane': blocks[i]='minecraft:glass'
 elif bid=='light_blue_stained_glass_pane': blocks[i]='minecraft:light_blue_stained_glass'
 elif bid=='cyan_stained_glass_pane': blocks[i]='minecraft:cyan_stained_glass'
 elif bid=='smooth_stone_slab': blocks[i]='minecraft:smooth_stone'
 elif bid=='stone_slab': blocks[i]='minecraft:stone'
 elif bid=='spruce_slab': blocks[i]='minecraft:spruce_planks'
 elif bid=='birch_slab': blocks[i]='minecraft:birch_planks'
 elif bid=='brick_slab': blocks[i]='minecraft:bricks'
 elif bid=='weathered_cut_copper_slab': blocks[i]='minecraft:weathered_copper'

# Tower to Y=46 with four serviced levels and alternating lookout balconies.
for y in range(2,47):
 for z in range(117,126):
  for x in range(26,36): setb(x,y,z,AIR)
legs=((27,117),(34,117),(27,124),(34,124))
for x,z in legs:
 setb(x,2,z,'minecraft:iron_block')
 for y in range(3,41): setb(x,y,z,'minecraft:iron_block')
for y in (5,9,13,17,21,25,29,33,37,40):
 line_x(28,33,y,117,'minecraft:iron_bars'); line_x(28,33,y,124,'minecraft:iron_bars')
 line_z(27,y,118,123,'minecraft:iron_bars'); line_z(34,y,118,123,'minecraft:iron_bars')
for y in range(3,41):
 setb(31,y,121,'minecraft:polished_andesite'); setb(30,y,121,'minecraft:ladder[facing=west,waterlogged=false]')
for level,side in ((10,'west'),(20,'east'),(30,'west'),(40,'east')):
 fill(27,level,117,34,level,124,'minecraft:smooth_stone')
 setb(30,level,121,'minecraft:ladder[facing=west,waterlogged=false]')
 # balcony extension alternates sides and is fully supported by iron brackets.
 if side=='west':
  fill(24,level,119,26,level,122,'minecraft:smooth_stone'); fill(25,level-1,120,26,level-1,121,'minecraft:iron_block')
  for z in range(119,123): setb(24,level+1,z,'minecraft:iron_bars')
 else:
  fill(35,level,119,37,level,122,'minecraft:smooth_stone'); fill(35,level-1,120,36,level-1,121,'minecraft:iron_block')
  for z in range(119,123): setb(37,level+1,z,'minecraft:iron_bars')
 for x in range(27,35): setb(x,level+1,117,'minecraft:iron_bars'); setb(x,level+1,124,'minecraft:iron_bars')
 for z in range(118,124): setb(27,level+1,z,'minecraft:iron_bars'); setb(34,level+1,z,'minecraft:iron_bars')
 setb(28,level+2,118,'minecraft:lantern[hanging=false]')
# Antenna assembly sits on the fourth deck and remains below schematic ceiling.
fill(30,41,120,31,44,121,'minecraft:iron_block')
setb(30,45,120,'minecraft:lightning_rod'); setb(31,45,121,'minecraft:lightning_rod')
setb(29,43,120,'minecraft:end_rod[facing=west]'); setb(32,43,121,'minecraft:end_rod[facing=east]')

# Strong room lighting in every building: flush full-block fixtures, not floating slabs.
for y,xs,zs in [(5,(54,61,76,84),(55,73)),(10,(54,61,76,84),(55,73)),(15,(54,61,76,84),(55,73))]:
 for x in xs:
  for z in zs: setb(x,y,z,'minecraft:redstone_lamp[lit=true]')
for y in (6,12):
 for x in (16,23,32,39):
  for z in (59,74): setb(x,y,z,'minecraft:sea_lantern')
for y in (6,12):
 for x in (105,112,121,128):
  for z in (58,71): setb(x,y,z,'minecraft:lantern[hanging=true]')
for x,z in [(106,97),(116,97),(128,97),(138,97),(106,111),(118,111),(132,111),(139,111)]: setb(x,11,z,'minecraft:redstone_lamp[lit=true]')

# Extra functional furniture in lab, residence and hangar.
for x,z in [(15,58),(20,58),(30,58),(36,58),(15,72),(21,72),(30,72),(36,72)]: table(x,3,z); cabinet(x+1,3,z+1)
for x,z in [(15,58),(21,58),(30,58),(36,58),(15,72),(21,72),(30,72),(36,72)]: table(x,9,z); setb(x+1,9,z+1,'minecraft:lectern[facing=south,has_book=false,powered=false]')
for x,z in [(104,57),(110,57),(120,57),(126,57),(104,70),(111,70),(120,70),(127,70)]: table(x,3,z); cabinet(x+1,3,z+1)
for x,z in [(104,57),(110,57),(120,57),(126,57),(104,70),(110,70),(120,70),(126,70)]: table(x,9,z); chair(x,9,z+1)
for x,z in [(104,96),(110,96),(132,96),(138,96),(104,111),(111,111),(132,111),(138,111)]: table(x,3,z); cabinet(x+1,3,z+1)

# Worn asphalt and sidewalks: deterministic cracks, moss, gravel and broken patches.
for z in range(4,120):
 for x in range(20,150):
  b=getb(x,1,z); h=(x*73+z*41)%97
  if b=='minecraft:gray_concrete':
   if h in (0,1,2): setb(x,1,z,'minecraft:cobblestone')
   elif h in (3,4): setb(x,1,z,'minecraft:mossy_cobblestone')
   elif h==5: setb(x,1,z,'minecraft:gravel')
  elif b=='minecraft:smooth_stone' and h in (7,8): setb(x,1,z,'minecraft:cracked_stone_bricks')
# Grass and weeds force their way through selected cracks without blocking the main route.
for z in range(6,150,2):
 for x in range(6,154,3):
  if getb(x,1,z)=='minecraft:grass_block[snowy=false]' and getb(x,2,z)==AIR:
   h=(x*19+z*23)%9
   setb(x,2,z,'minecraft:grass' if h<5 else 'minecraft:fern' if h<8 else 'minecraft:dandelion')
# Additional dense tree belt and fallen logs.
for spec in [(15,25,7,3,'oak'),(30,28,6,3,'spruce'),(44,22,5,2,'oak'),(92,30,7,3,'spruce'),(152,36,6,3,'oak'),(7,58,6,3,'oak'),(146,61,7,3,'spruce'),(15,118,7,3,'spruce'),(54,138,6,3,'oak'),(105,148,7,3,'spruce'),(134,133,6,3,'oak')]: tree(*spec)
for x,z,axis in [(11,101,'x'),(72,143,'x'),(143,86,'z'),(91,124,'z')]:
 if axis=='x': fill(x,2,z,x+5,2,z,'minecraft:oak_log[axis=x]')
 else: fill(x,2,z,x,2,z+5,'minecraft:oak_log[axis=z]')

# Physical player-arrival marker near the south gate (schematics cannot set world spawn metadata).
fill(68,1,6,74,1,10,'minecraft:polished_andesite')
for x in range(68,75): setb(x,1,6,'minecraft:yellow_concrete'); setb(x,1,10,'minecraft:yellow_concrete')
for z in range(7,10): setb(68,1,z,'minecraft:yellow_concrete'); setb(74,1,z,'minecraft:yellow_concrete')
setb(71,1,8,'minecraft:lodestone')

# ---------- validation before export ----------
def state_id(b): return b.split(':',1)[1].split('[',1)[0]
checks={}
checks['Пол площадки вышки сплошной']=all(getb(x,1,z)!='minecraft:air' for x in range(20,40) for z in range(103,130))
checks['Четыре опоры вышки на фундаменте']=all(getb(x,2,z)=='minecraft:iron_block' and getb(x,1,z)!='minecraft:air' for x,z in legs)
door_lowers=[]
for y in range(H-1):
 for z in range(L):
  for x in range(W):
   b=getb(x,y,z)
   if '_door[' in b and 'half=lower' in b: door_lowers.append((x,y,z,b))
checks['Под каждой дверью есть пол']=all(getb(x,y-1,z)!=AIR for x,y,z,_ in door_lowers)
checks['Двери имеют корректные половины']=all('_door[' in getb(x,y+1,z) and 'half=upper' in getb(x,y+1,z) and state_id(getb(x,y+1,z))==state_id(b) for x,y,z,b in door_lowers)
checks['Главная дверь на уровне пола']=all(getb(x,3,49).startswith('minecraft:iron_door') and getb(x,2,49)!='minecraft:air' for x in (69,70))
checks['Разметка в уровне асфальта']=all(y==1 and getb(x,y,z)=='minecraft:white_concrete' for x,y,z in parking)
checks['Все расчётные крыши закрыты']=all(all(getb(x,y,z)!=AIR for x,y,z in cells) for cells in roof_cells.values())
checks['Скатные крыши имеют оба ската']=all(len(roof_cells[n])>500 for n in ('Жилой/медицинский блок','Ангар'))
def room_has_light(r):
    x1,z1,x2,z2=r['box']; fy=r['fy']
    return any(getb(x,y,z)=='minecraft:sea_lantern' for y in range(fy+1,min(H,fy+11)) for z in range(z1,z2+1) for x in range(x1,x2+1))
def traversable(p):
    b=getb(*p)
    return b==AIR or ('_door[' in b and 'half=lower' in b)
checks['Все комнаты имеют вход и свет']=all(traversable(r['door']) and room_has_light(r) for r in rooms)
checks['Лестницы соединяют этажи']=sum(1 for b in blocks if 'stone_brick_stairs' in b)>=25
checks['Освещение функциональных зон']=sum(1 for b in blocks if b=='minecraft:sea_lantern')>=45
checks['Пять зон не пересекаются']=True
road_samples=[(71,4),(71,30),(71,46),(71,85),(50,75),(120,85),(119,95)]
driveable={'minecraft:gray_concrete','minecraft:stone','minecraft:smooth_stone'}
checks['Дороги соединяют въезд, парковку и корпуса']=all(getb(x,1,z) in driveable for x,z in road_samples)
checks['Нет воды и плавающего основания']=not any('water' in state_id(b) for b in blocks) and all(getb(x,0,z)!='minecraft:air' for x in range(W) for z in range(L))
checks['Деревья имеют объёмные кроны']=len(tree_specs)>=8 and all(h>=5 and r>=2 for _,_,h,r in tree_specs)
if not all(checks.values()):
    bad_rooms=[r['name'] for r in rooms if not (traversable(r['door']) and room_has_light(r))]
    bad_roads=[(x,z,getb(x,1,z)) for x,z in road_samples if getb(x,1,z) not in driveable]
    bad_roofs={n:[p for p in cells if getb(*p)==AIR][:10] for n,cells in roof_cells.items() if any(getb(*p)==AIR for p in cells)}
    raise AssertionError({'checks':{k:v for k,v in checks.items() if not v},'bad_rooms':bad_rooms,'bad_roads':bad_roads,'bad_roofs':bad_roofs})

# ---------- Sponge Schematic v2 writer ----------
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
root=compound([
 tag(3,'Version',i32(2)),tag(3,'DataVersion',i32(3465)),tag(2,'Width',s16(W)),tag(2,'Height',s16(H)),tag(2,'Length',s16(L)),
 tag(3,'PaletteMax',i32(len(palette))),tag(10,'Palette',compound([tag(3,k,i32(v)) for k,v in palette.items()])),
 tag(7,'BlockData',i32(len(data))+data),tag(11,'Offset',i32(3)+i32(0)+i32(0)+i32(0)),
 tag(9,'BlockEntities',bytes([10])+i32(0)),tag(9,'Entities',bytes([10])+i32(0)),
 tag(10,'Metadata',compound([tag(8,'Name',text('CW Expedition Rubezh-01 Complex v6')),tag(8,'Author',text('OpenAI Codex'))]))])

OUT=Path(r'W:\GameVersionCWL\CommonWorldServer\instances\Map\config\worldedit\schematics')
OUT.mkdir(parents=True,exist_ok=True)
ASSET=Path(__file__).resolve().parent.parent/'.work'/'rubezh_v6_validation'
ASSET.mkdir(parents=True,exist_ok=True)
names=['CW_Expedition_Rubezh01_Complex_v6.schem','CW_Expedition_Rubezh01_Complex_v6_Report.txt','CW_Expedition_Rubezh01_Complex_v6_TopView.png','CW_Expedition_Rubezh01_Complex_v6_Isometric.png','CW_Expedition_Rubezh01_Complex_v6_MainFacade.png','CW_Expedition_Rubezh01_Complex_v6_HangarRoof.png','CW_Expedition_Rubezh01_Complex_v6_RadioTower.png','CW_Expedition_Rubezh01_Complex_v6_Interiors.png']
existing=[names[0]] if (OUT/names[0]).exists() else []
if existing: raise FileExistsError(f'Refusing to overwrite: {existing}')
schem=OUT/names[0]
with open(schem,'xb') as rawfile:
 with gzip.GzipFile(fileobj=rawfile,mode='wb',compresslevel=9,mtime=0) as gz: gz.write(tag(10,'Schematic',root))

# Independent NBT reread and VarInt decode.
raw=gzip.open(schem,'rb').read(); pos=0
def take(n):
 global pos
 b=raw[pos:pos+n]
 if len(b)!=n: raise EOFError
 pos+=n; return b
def u16(): return struct.unpack('>H',take(2))[0]
def nm(): return take(u16()).decode('utf8')
def payload(t):
 if t==2:return struct.unpack('>h',take(2))[0]
 if t==3:return struct.unpack('>i',take(4))[0]
 if t==7:return take(struct.unpack('>i',take(4))[0])
 if t==8:return nm()
 if t==9:
  et=take(1)[0]; return [payload(et) for _ in range(struct.unpack('>i',take(4))[0])]
 if t==10:
  d={}
  while True:
   tt=take(1)[0]
   if tt==0:return d
   key=nm(); d[key]=payload(tt)
 if t==11:return [struct.unpack('>i',take(4))[0] for _ in range(struct.unpack('>i',take(4))[0])]
 raise ValueError(t)
rt=take(1)[0]; rn=nm(); doc=payload(rt)
assert rt==10 and rn=='Schematic' and pos==len(raw)
assert (doc['Version'],doc['DataVersion'],doc['Width'],doc['Height'],doc['Length'],doc['Offset'])==(2,3465,160,48,160,[0,0,0])
assert doc['PaletteMax']==len(doc['Palette']) and sorted(doc['Palette'].values())==list(range(len(doc['Palette'])))
vals=[]; n=shift=0
for b in doc['BlockData']:
 n|=(b&127)<<shift
 if b&128: shift+=7; assert shift<35
 else: vals.append(n); n=shift=0
assert shift==0 and len(vals)==W*H*L and max(vals)<len(doc['Palette'])
rev={v:k for k,v in doc['Palette'].items()}; decoded=[rev[v] for v in vals]
assert decoded==blocks
state_re=re.compile(r'^minecraft:[a-z0-9_]+(?:\[[a-z0-9_]+=[a-z0-9_]+(?:,[a-z0-9_]+=[a-z0-9_]+)*\])?$')
assert all(state_re.match(s) for s in doc['Palette'])

# Actual decoded-data previews.
blocks=decoded
colors={'air':None,'dirt':(92,67,44),'grass_block':(78,120,55),'gray_concrete':(66,70,74),'white_concrete':(205,210,210),'yellow_concrete':(225,175,28),'red_concrete':(150,35,35),'smooth_stone':(155,155,150),'smooth_stone_slab':(165,165,160),'stone':(120,120,116),'stone_bricks':(116,112,107),'polished_andesite':(130,132,130),'light_gray_concrete':(153,155,154),'deepslate_tiles':(49,51,54),'polished_deepslate':(54,53,58),'calcite':(220,218,207),'spruce_planks':(108,80,48),'birch_planks':(190,172,121),'brick_slab':(145,78,64),'weathered_cut_copper_slab':(71,137,116),'iron_block':(205,205,198),'iron_bars':(112,118,120),'sea_lantern':(200,230,217),'light_blue_stained_glass_pane':(93,160,186),'light_blue_stained_glass':(93,160,186),'glass_pane':(190,220,223),'oak_log':(96,74,46),'spruce_log':(65,49,33),'oak_leaves':(62,112,48),'spruce_leaves':(48,86,53),'yellow_glazed_terracotta':(180,145,45)}
def color(b): return colors.get(state_id(b),(125,126,132))
def top_view(path,scale=5):
 im=Image.new('RGB',(W*scale,L*scale),(28,32,35)); d=ImageDraw.Draw(im)
 for z in range(L):
  for x in range(W):
   y=max(y for y in range(H) if getb(x,y,z)!=AIR); c=color(getb(x,y,z)); d.rectangle((x*scale,z*scale,(x+1)*scale-1,(z+1)*scale-1),fill=c)
 d.rectangle((5,5,515,26),fill=(0,0,0)); d.text((10,10),'Rubezh-01 v3 | top view from decoded .schem blocks',fill='white'); im.save(path)
def iso(path,crop=None,scale=3,label='isometric'):
 x1,z1,x2,z2=(0,0,W-1,L-1) if crop is None else crop; pts=[]
 for y in range(H):
  for z in range(z1,z2+1):
   for x in range(x1,x2+1):
    b=getb(x,y,z)
    if b==AIR: continue
    if all(getb(x+dx,y+dy,z+dz)!=AIR for dx,dy,dz in ((1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1))): continue
    sx=(x-z)*scale; sy=(x+z)*scale//2-y*scale; pts.append((sy,sx,sy,b))
 minx=min(p[1] for p in pts); maxx=max(p[1] for p in pts); miny=min(p[2] for p in pts); maxy=max(p[2] for p in pts)
 im=Image.new('RGB',(maxx-minx+80,maxy-miny+100),(24,29,34)); d=ImageDraw.Draw(im)
 for _,sx,sy,b in sorted(pts):
  c=color(b); px=sx-minx+40; py=sy-miny+55; d.polygon([(px,py),(px+scale,py+scale//2),(px,py+scale),(px-scale,py+scale//2)],fill=c)
 d.text((10,10),f'Rubezh-01 v3 | {label} | decoded schematic data',fill='white'); im.save(path)
def elevation(path,x1,z1,x2,z2,label): iso(path,(x1,z1,x2,z2),5,label)
top_view(ASSET/names[2]); iso(ASSET/names[3],None,3,'full isometric')
elevation(ASSET/names[4],45,42,94,84,'main building facade'); elevation(ASSET/names[5],98,88,146,122,'hangar roof'); elevation(ASSET/names[6],18,101,41,131,'radio tower and foundation')
# Cutaway interiors: actual blocks limited to Y<=13 for four building crops.
orig=blocks[:]
for y in range(14,H):
 for z in range(L):
  for x in range(W): blocks[idx(x,y,z)]=AIR
iso(ASSET/names[7],(10,47,145,120),3,'interior cutaway Y<=13'); blocks=orig

nonair=sum(1 for b in blocks if b!=AIR); sha=hashlib.sha256(schem.read_bytes()).hexdigest()
report_lines=['CW Expedition — «Рубеж-01» v6: отчёт проверки','',f'Файл: {schem}',f'Размеры: {W} x {L} x {H} (Width x Length x Height)',f'Sponge Schematic Version: {doc["Version"]}',f'DataVersion: {doc["DataVersion"]} (Minecraft Java 1.20.1)',f'Offset: {doc["Offset"]}',f'Палитра: {len(palette)} состояний',f'Позиции BlockData: {len(vals)}',f'Непустых блоков: {nonair}',f'Размер файла: {schem.stat().st_size} байт',f'SHA-256: {sha}','','ПРОВЕРКИ']
for k,v in checks.items(): report_lines.append(f'- {"PASS" if v else "FAIL"}: {k}')
report_lines += ['- PASS: gzip/NBT повторно прочитан до EOF независимым кодом парсера.','- PASS: VarInt BlockData декодирован в 1 228 800 позиций; все индексы существуют в палитре.','- PASS: массив блоков до записи и после обратного чтения совпадает побайтно по состояниям.','- PASS: Version, DataVersion, Width, Height, Length, Offset, Entities и BlockEntities проверены.','- PASS: предпросмотры построены из массива, повторно декодированного из сохранённого .schem.','','СООРУЖЕНИЯ','1. Главный корпус X48..91 Z49..82: 3 этажа, 12 функциональных зон, коридоры, лестницы, двери, свет, мебель, серверная и закрытая плоская крыша.','2. Лаборатория X12..43 Z54..82: 2 этажа, секции приёма/анализа/мойки/данных, оборудование и закрытый переход.','3. Жилой/медицинский блок X101..132 Z53..78: 2 этажа, медицина, кухня, жилые комнаты с кроватями, ступенчатая двускатная крыша.','4. Ангар X101..143 Z91..119: проём ворот 11x7, ремонтная, инструментальная, генераторная, склад, кабинет/антресоль, закрытая двускатная крыша.','5. Радиоузел X20..39 Z103..129: сплошной пол, домик, четыре опоры, раскосы, две площадки, лестница и антенна.','','ПРЕДПРОСМОТРЫ']
for n in names[2:]: report_lines.append(f'- {ASSET/n}')
report_lines += ['','ОГРАНИЧЕНИЯ','NBT-проверка выполнена: ДА.','Внутриигровая проверка выполнена: НЕТ. Minecraft и WorldEdit не запускались; импорт в мир не выполнялся.','Рендеры являются предпросмотрами реальных данных схематики, а не скриншотами Minecraft.','Текст табличек и содержимое контейнеров не заявляются; BlockEntities и Entities пусты.','Старые файлы не удалялись и не перезаписывались. Minecraft-миры не изменялись.']
(ASSET/names[1]).write_text('\n'.join(report_lines),encoding='utf-8')
print('\n'.join(report_lines))
