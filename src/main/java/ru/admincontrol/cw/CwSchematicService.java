package ru.admincontrol.cw;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Minimal Sponge Schematic v2 reader/writer. Entities and block-entity payloads are deliberately excluded. */
public final class CwSchematicService {
	private static final long MAX_FILE_BYTES = 64L * 1024 * 1024;
	private static final Map<UUID, Schematic> LOADED = new HashMap<>();
	private static final Map<UUID, PasteJob> JOBS = new HashMap<>();
	private static final Map<UUID, PreviewPlacement> PREVIEWS = new HashMap<>();
	private record PreviewPlacement(BlockPos origin,int rotation){}
	private static boolean registered;

	public record Schematic(String name, int width, int height, int length, List<BlockState> blocks, List<String> warnings) {
		public long volume() { return (long)width * height * length; }
	}
	private static final class PasteJob {
		final ServerPlayerEntity owner; final Schematic schematic; final ServerWorld world; final BlockPos origin;final int rotation; int index;
		PasteJob(ServerPlayerEntity o, Schematic s, ServerWorld w, BlockPos p,int r) { owner=o; schematic=s; world=w; origin=p;rotation=r; }
	}
	private CwSchematicService() {}

	public static synchronized void register() {
		if (registered) return; registered=true;
		ServerTickEvents.END_SERVER_TICK.register(CwSchematicService::tick);
	}
	public static Path folder() { return FabricLoader.getInstance().getConfigDir().resolve("admincontrol").resolve("schematics"); }
	public static boolean validName(String name) { return name != null && name.matches("[A-Za-z0-9_-]{1,64}"); }
	public static Path path(String name) { return validName(name) ? folder().resolve(name + ".schem").normalize() : null; }

	public static String save(ServerPlayerEntity player, String name, CwSelection selection) {
		if (!validName(name)) return "Имя схемы: только буквы, цифры, дефис и подчёркивание (до 64).";
		if (selection == null || !selection.complete()) return "Сначала задайте обе точки выделения.";
		if (!selection.sameWorld()) return "Точки находятся в разных измерениях.";
		if (selection.volume() > CwExpansion.DEFAULT_MAX_VOLUME) return "Объём превышает лимит 1 000 000 блоков.";
		Path target=path(name); if (Files.exists(target)) return "Файл уже существует; перезапись запрещена.";
		if (!player.getWorld().getRegistryKey().equals(selection.first().world())) return "Перейдите в измерение выделения.";
		try {
			BlockBox b=selection.box(); int w=b.getBlockCountX(), h=b.getBlockCountY(), l=b.getBlockCountZ();
			Map<String,Integer> palette=new LinkedHashMap<>(); ByteArrayOutputStream data=new ByteArrayOutputStream((int)Math.min(selection.volume(), 1_000_000));
			for(int y=b.getMinY();y<=b.getMaxY();y++) for(int z=b.getMinZ();z<=b.getMaxZ();z++) for(int x=b.getMinX();x<=b.getMaxX();x++) {
				String key=stateKey(player.getWorld().getBlockState(new BlockPos(x,y,z)));
				int id=palette.computeIfAbsent(key,k->palette.size()); writeVarInt(data,id);
			}
			NbtCompound root=new NbtCompound(); root.putInt("Version",2); root.putInt("DataVersion",3465);
			root.putShort("Width",(short)w); root.putShort("Height",(short)h); root.putShort("Length",(short)l);
			NbtCompound meta=new NbtCompound(); meta.putString("Name",name); meta.putString("Author",player.getGameProfile().getName()); root.put("Metadata",meta);
			NbtCompound pal=new NbtCompound(); palette.forEach(pal::putInt); root.put("Palette",pal); root.putInt("PaletteMax",palette.size());
			root.putByteArray("BlockData",data.toByteArray()); root.put("BlockEntities",new net.minecraft.nbt.NbtList()); root.put("Entities",new net.minecraft.nbt.NbtList());
			Files.createDirectories(folder()); Path tmp=target.resolveSibling(target.getFileName()+".tmp"); NbtIo.writeCompressed(root,tmp.toFile());
			try { Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE); } catch(Exception e) { Files.move(tmp,target); }
			return null;
		} catch(Exception e) { return "Не удалось сохранить схему: "+e.getMessage(); }
	}

	public static String load(ServerPlayerEntity player,String name) {
		Path file=path(name); if(file==null) return "Недопустимое имя схемы.";
		try {
			if(!Files.isRegularFile(file)) return "Схема не найдена: "+file;
			if(Files.size(file)>MAX_FILE_BYTES) return "Файл схемы слишком большой.";
			NbtCompound root=NbtIo.readCompressed(file.toFile()); int version=root.getInt("Version");
			if(version!=2 && version!=3) return "Неподдерживаемая версия Sponge Schematic: "+version;
			int w=root.getShort("Width")&0xffff,h=root.getShort("Height")&0xffff,l=root.getShort("Length")&0xffff;
			long volume=(long)w*h*l; if(w<1||h<1||l<1||volume>CwExpansion.DEFAULT_MAX_VOLUME) return "Некорректные размеры или превышен лимит объёма.";
			NbtCompound pal=root.getCompound("Palette"); Map<Integer,BlockState> states=new HashMap<>(); List<String>warnings=new ArrayList<>();
			for(String key:pal.getKeys()) { BlockState state=parseState(key,warnings); states.put(pal.getInt(key),state); }
			byte[] bytes=root.getByteArray("BlockData"); int[] cursor={0}; List<BlockState> blocks=new ArrayList<>((int)volume);
			for(int i=0;i<volume;i++){ int id=readVarInt(bytes,cursor); BlockState state=states.get(id); if(state==null) return "Палитра схемы повреждена (ID "+id+")."; blocks.add(state); }
			if(cursor[0]!=bytes.length) warnings.add("В BlockData есть лишние байты.");
			if(!root.getList("BlockEntities",10).isEmpty()) warnings.add("Block entities проверены, но их NBT не вставляется безопасным режимом.");
			LOADED.put(player.getUuid(),new Schematic(name,w,h,l,List.copyOf(blocks),List.copyOf(warnings))); return null;
		} catch(Exception e){ return "Невалидная или повреждённая .schem: "+e.getMessage(); }
	}
	public static Schematic loaded(ServerPlayerEntity p){return LOADED.get(p.getUuid());}
	public static void unload(ServerPlayerEntity p){LOADED.remove(p.getUuid()); JOBS.remove(p.getUuid());}
	public static String startPaste(ServerPlayerEntity p,BlockPos origin){Schematic s=loaded(p);if(s==null)return "Сначала выполните /cw schem load <имя>.";if(JOBS.containsKey(p.getUuid()))return "Вставка уже выполняется.";PreviewPlacement preview=PREVIEWS.remove(p.getUuid());BlockPos actual=preview==null?origin:preview.origin();int rotation=preview==null?0:preview.rotation();JOBS.put(p.getUuid(),new PasteJob(p,s,p.getServerWorld(),actual.toImmutable(),rotation));hidePreview(p);return null;}
	public static boolean cancel(ServerPlayerEntity p){return JOBS.remove(p.getUuid())!=null;}
	public static void updatePreviewOrigin(ServerPlayerEntity p,BlockPos origin,int rotation){if(p==null||origin==null||loaded(p)==null)return;if(!CwGraphNetwork.allowed(p))return;if(origin.getSquaredDistance(p.getBlockPos())>4096)return;PREVIEWS.put(p.getUuid(),new PreviewPlacement(origin.toImmutable(),Math.floorMod(rotation,4)));}
	public static void preview(ServerPlayerEntity p){Schematic s=loaded(p);if(s==null||!ServerPlayNetworking.canSend(p,ru.admincontrol.net.ModPackets.CW_SCHEM_PREVIEW))return;BlockPos origin=p.getBlockPos().offset(p.getHorizontalFacing(),4);PREVIEWS.put(p.getUuid(),new PreviewPlacement(origin,rotation(p.getHorizontalFacing())));var buf=PacketByteBufs.create();buf.writeBoolean(true);buf.writeVarInt(s.width);buf.writeVarInt(s.height);buf.writeVarInt(s.length);int nonAir=0;for(BlockState state:s.blocks)if(!state.isAir())nonAir++;int cap=10000,step=Math.max(1,(nonAir+cap-1)/cap),seen=0,count=0;for(BlockState state:s.blocks)if(!state.isAir()&&(seen++%step)==0)count++;buf.writeVarInt(count);seen=0;for(int i=0;i<s.blocks.size();i++){BlockState state=s.blocks.get(i);if(state.isAir()||(seen++%step)!=0)continue;int x=i%s.width,z=(i/s.width)%s.length,y=i/(s.width*s.length);buf.writeVarInt(x);buf.writeVarInt(y);buf.writeVarInt(z);buf.writeVarInt(Block.STATE_IDS.getRawId(state));}ServerPlayNetworking.send(p,ru.admincontrol.net.ModPackets.CW_SCHEM_PREVIEW,buf);}
	private static int rotation(net.minecraft.util.math.Direction direction){return switch(direction){case SOUTH->0;case WEST->1;case NORTH->2;case EAST->3;default->0;};}
	private static void hidePreview(ServerPlayerEntity p){if(p!=null&&ServerPlayNetworking.canSend(p,ru.admincontrol.net.ModPackets.CW_SCHEM_PREVIEW)){var buf=PacketByteBufs.create();buf.writeBoolean(false);ServerPlayNetworking.send(p,ru.admincontrol.net.ModPackets.CW_SCHEM_PREVIEW,buf);}}
	private static void tick(MinecraftServer server){for(var it=JOBS.entrySet().iterator();it.hasNext();){PasteJob j=it.next().getValue();int end=Math.min(j.index+4096,j.schematic.blocks.size());for(;j.index<end;j.index++){int x=j.index%j.schematic.width;int z=(j.index/j.schematic.width)%j.schematic.length;int y=j.index/(j.schematic.width*j.schematic.length);int rx=x,rz=z;if(j.rotation==1){rx=j.schematic.length-1-z;rz=x;}else if(j.rotation==2){rx=j.schematic.width-1-x;rz=j.schematic.length-1-z;}else if(j.rotation==3){rx=z;rz=j.schematic.width-1-x;}net.minecraft.util.BlockRotation rot=switch(j.rotation){case 1->net.minecraft.util.BlockRotation.CLOCKWISE_90;case 2->net.minecraft.util.BlockRotation.CLOCKWISE_180;case 3->net.minecraft.util.BlockRotation.COUNTERCLOCKWISE_90;default->net.minecraft.util.BlockRotation.NONE;};j.world.setBlockState(j.origin.add(rx,y,rz),j.schematic.blocks.get(j.index).rotate(rot),Block.NOTIFY_ALL);}if(j.index>=j.schematic.blocks.size()){j.owner.sendMessage(net.minecraft.text.Text.literal("[CW] Вставка завершена: "+j.schematic.name),false);it.remove();}}}

	private static String stateKey(BlockState state){StringBuilder s=new StringBuilder(Registries.BLOCK.getId(state.getBlock()).toString());if(!state.getEntries().isEmpty()){s.append('[');boolean first=true;for(var e:state.getEntries().entrySet()){if(!first)s.append(',');first=false;s.append(e.getKey().getName()).append('=').append(valueName(e.getKey(),e.getValue()));}s.append(']');}return s.toString();}
	@SuppressWarnings({"rawtypes","unchecked"}) private static String valueName(Property p,Comparable v){return p.name(v);}
	@SuppressWarnings({"rawtypes","unchecked"}) private static BlockState parseState(String text,List<String>warnings){String id=text,props="";int at=text.indexOf('[');if(at>=0&&text.endsWith("]")){id=text.substring(0,at);props=text.substring(at+1,text.length()-1);}Identifier key=Identifier.tryParse(id);Block block=key==null?null:Registries.BLOCK.get(key);if(block==null||key==null||!Registries.BLOCK.containsId(key)){warnings.add("Неизвестный блок заменён воздухом: "+id);return net.minecraft.block.Blocks.AIR.getDefaultState();}BlockState state=block.getDefaultState();if(!props.isBlank())for(String pair:props.split(",")){String[]kv=pair.split("=",2);if(kv.length!=2)continue;Property property=block.getStateManager().getProperty(kv[0]);if(property==null){warnings.add("Неизвестное свойство "+pair);continue;}var value=property.parse(kv[1]);if(value.isPresent())state=state.with(property,(Comparable)value.get());else warnings.add("Некорректное свойство "+pair);}return state;}
	private static void writeVarInt(ByteArrayOutputStream out,int value){do{int b=value&127;value>>>=7;if(value!=0)b|=128;out.write(b);}while(value!=0);}
	private static int readVarInt(byte[]data,int[]cursor){int value=0,shift=0;while(true){if(cursor[0]>=data.length||shift>28)throw new IllegalArgumentException("повреждённый VarInt");int b=data[cursor[0]++]&255;value|=(b&127)<<shift;if((b&128)==0)return value;shift+=7;}}
}
