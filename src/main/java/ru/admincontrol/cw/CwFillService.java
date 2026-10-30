package ru.admincontrol.cw;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Порционная серверная очередь /afill: не блокирует тик большими областями. */
public final class CwFillService {
	public static final long CONFIRM_VOLUME = 32768;
	public static final long MAX_VOLUME = 1_000_000;
	private static final int PER_TICK = 2048;
	private static final Map<UUID, Job> JOBS = new ConcurrentHashMap<>();
	private static boolean registered;
	public static final class Job {
		UUID owner; ServerWorld world; BlockState state; int minX,minY,minZ,maxX,maxY,maxZ,x,y,z; long total,done; boolean pending;
	}
	private CwFillService() {}
	public static synchronized void register(){if(registered)return;registered=true;ServerTickEvents.END_SERVER_TICK.register(CwFillService::tick);}
	public static String prepare(ServerPlayerEntity p, BlockState state){
		if(!CwBuilderWorld.inside(p))return "Команда /afill работает только в строительном измерении.";
		var s=CwBuilderSelection.get(p);if(!CwBuilderSelection.complete(s))return "Сначала задайте обе точки командами /1 и /2.";
		if(!p.getWorld().getRegistryKey().equals(s.dimension))return "Точки выбраны в другом измерении.";
		int max=(int)MAX_VOLUME, confirm=(int)CONFIRM_VOLUME;if(ru.admincontrol.data.DataManager.get()!=null){var cfg=ru.admincontrol.data.DataManager.get().settings();max=Math.max(1,Math.min(5_000_000,cfg.cwFillMaxVolume));confirm=Math.max(1,Math.min(max,cfg.cwFillConfirmVolume));}
		long volume=CwBuilderSelection.volume(s);if(volume>max)return "Объём "+volume+" превышает лимит "+max+" блоков.";
		int minY=Math.min(s.first.getY(),s.second.getY()),maxY=Math.max(s.first.getY(),s.second.getY());if(minY<p.getServerWorld().getBottomY()||maxY>=p.getServerWorld().getTopY())return "Область выходит за вертикальные границы мира.";
		long chunks=(long)(Math.abs((s.first.getX()>>4)-(s.second.getX()>>4))+1)*(Math.abs((s.first.getZ()>>4)-(s.second.getZ()>>4))+1);if(chunks>1024)return "Область пересекает "+chunks+" чанков; безопасный лимит — 1024.";
		Job j=new Job();j.owner=p.getUuid();j.world=p.getServerWorld();j.state=state;j.minX=Math.min(s.first.getX(),s.second.getX());j.maxX=Math.max(s.first.getX(),s.second.getX());j.minY=Math.min(s.first.getY(),s.second.getY());j.maxY=Math.max(s.first.getY(),s.second.getY());j.minZ=Math.min(s.first.getZ(),s.second.getZ());j.maxZ=Math.max(s.first.getZ(),s.second.getZ());j.x=j.minX;j.y=j.minY;j.z=j.minZ;j.total=volume;j.pending=volume>confirm;JOBS.put(p.getUuid(),j);return j.pending?"Объём "+volume+" блоков. Выполните /afill confirm или /afill cancel.":null;
	}
	public static String confirm(ServerPlayerEntity p){Job j=JOBS.get(p.getUuid());if(j==null)return "Нет подготовленной операции.";j.pending=false;return null;}
	public static boolean cancel(ServerPlayerEntity p){return JOBS.remove(p.getUuid())!=null;}
	public static String status(ServerPlayerEntity p){Job j=JOBS.get(p.getUuid());if(j==null)return "Операция /afill не активна.";return (j.pending?"ожидает подтверждения: ":"выполняется: ")+j.done+"/"+j.total+" ("+(j.total==0?0:j.done*100/j.total)+"%)";}
	private static void tick(MinecraftServer server){
		for(Job j:java.util.List.copyOf(JOBS.values())){if(j.pending)continue;ServerPlayerEntity p=server.getPlayerManager().getPlayer(j.owner);if(p==null||p.getServerWorld()!=j.world){JOBS.remove(j.owner);continue;}int budget=PER_TICK;while(budget-->0&&j.done<j.total){BlockPos pos=new BlockPos(j.x,j.y,j.z);j.world.setBlockState(pos,j.state,3);j.done++;j.x++;if(j.x>j.maxX){j.x=j.minX;j.z++;if(j.z>j.maxZ){j.z=j.minZ;j.y++;}}}if(j.done>=j.total){JOBS.remove(j.owner);CwBuilderSelection.clear(p);p.sendMessage(Text.literal("[CW] Заполнение завершено: "+j.total+" блоков. Точки очищены."),false);}else if(j.done%32768<PER_TICK)p.sendMessage(Text.literal("[CW] Заполнение: "+(j.done*100/j.total)+"% ("+j.done+"/"+j.total+")"),true);}
	}
}
