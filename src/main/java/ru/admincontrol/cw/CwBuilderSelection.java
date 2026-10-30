package ru.admincontrol.cw;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Последний клик и две независимые точки /1 и /2 для каждого разработчика. */
public final class CwBuilderSelection {
	public static final class State {
		public RegistryKey<World> dimension;
		public BlockPos clicked, first, second;
		public Vec3d exactHit;
		public Direction face;
	}
	private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
	private CwBuilderSelection() {}
	public static State get(ServerPlayerEntity p) { return STATES.computeIfAbsent(p.getUuid(), k -> new State()); }
	public static void click(ServerPlayerEntity p, BlockPos pos, Vec3d exactHit, Direction face) { State s=get(p); s.dimension=p.getWorld().getRegistryKey(); s.clicked=pos.toImmutable(); s.exactHit=exactHit; s.face=face; }
	public static int selectNext(ServerPlayerEntity p, BlockPos pos, Vec3d exactHit, Direction face) {
		State s=get(p);s.dimension=p.getWorld().getRegistryKey();s.clicked=pos.toImmutable();s.exactHit=exactHit;s.face=face;
		if(s.first==null){s.first=s.clicked;return 1;}if(s.second==null){s.second=s.clicked;return 2;}s.first=s.clicked;s.second=null;return 1;
	}
	public static String fix(ServerPlayerEntity p, int point) {
		State s=get(p); if(s.clicked==null)return "Сначала кликните Маркером строителя по блоку.";
		if(!p.getWorld().getRegistryKey().equals(s.dimension))return "Последний блок выбран в другом измерении.";
		if(point==1)s.first=s.clicked;else s.second=s.clicked; return null;
	}
	public static boolean complete(State s){return s!=null&&s.first!=null&&s.second!=null;}
	public static long volume(State s){return complete(s)?(long)(Math.abs(s.first.getX()-s.second.getX())+1)*(Math.abs(s.first.getY()-s.second.getY())+1)*(Math.abs(s.first.getZ()-s.second.getZ())+1):0;}
	public static Box box(State s){return new Box(Math.min(s.first.getX(),s.second.getX()),Math.min(s.first.getY(),s.second.getY()),Math.min(s.first.getZ(),s.second.getZ()),Math.max(s.first.getX(),s.second.getX())+1,Math.max(s.first.getY(),s.second.getY())+1,Math.max(s.first.getZ(),s.second.getZ())+1);}
	public static void clear(ServerPlayerEntity p){STATES.remove(p.getUuid());}
}
