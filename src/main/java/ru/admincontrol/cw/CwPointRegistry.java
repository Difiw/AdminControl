package ru.admincontrol.cw;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.data.DataManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Exact, persistent server-side marker hits; block selection remains separate. */
public final class CwPointRegistry {
	public static final int SCHEMA_VERSION = 1;
	public static final class Point {
		public int schemaVersion = SCHEMA_VERSION;
		public String name = "start";
		public String ownerUuid, dimension, face;
		public int blockX, blockY, blockZ;
		public double x, y, z;
		public float yaw, pitch;
		public long updatedAt;
	}
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Map<String, Point> POINTS = new LinkedHashMap<>();
	private static boolean loaded;
	private CwPointRegistry() {}

	private static Path file() { return DataManager.get() == null ? null : DataManager.get().getConfigDir().resolve("cw-points.json"); }
	private static synchronized void load() {
		if (loaded) return; loaded = true; Path path = file();
		if (path == null || !Files.isRegularFile(path)) return;
		try {
			Map<String, Point> read = GSON.fromJson(CwDurable.text(path), new TypeToken<Map<String, Point>>(){}.getType());
			if (read != null) POINTS.putAll(read);
		} catch (Exception e) { AdminControlMod.LOGGER.error("CW point registry read failed", e); }
	}
	public static synchronized Point record(ServerPlayerEntity player, BlockHitResult hit) {
		return record(player, "start", hit);
	}
	public static synchronized Point record(ServerPlayerEntity player, String name, BlockHitResult hit) {
		load(); Point p = new Point(); p.ownerUuid = player.getUuidAsString();
		p.name = validName(name) ? name : "start";
		p.dimension = player.getWorld().getRegistryKey().getValue().toString(); p.face = hit.getSide().asString();
		p.blockX = hit.getBlockPos().getX(); p.blockY = hit.getBlockPos().getY(); p.blockZ = hit.getBlockPos().getZ();
		p.x = hit.getPos().x; p.y = hit.getPos().y; p.z = hit.getPos().z;
		p.yaw = player.getYaw(); p.pitch = player.getPitch(); p.updatedAt = System.currentTimeMillis();
		POINTS.put(key(p.ownerUuid, p.name), p); save(); return p;
	}
	public static synchronized Point get(String ownerUuid) { return get(ownerUuid, "start"); }
	public static synchronized Point get(String ownerUuid, String name) { load(); Point p=POINTS.get(key(ownerUuid,name)); return p==null&&"start".equals(name)?POINTS.get(ownerUuid):p; }
	public static synchronized Map<String, Point> all() { load(); return Map.copyOf(POINTS); }
	public static synchronized String rename(String ownerUuid,String oldName,String newName){load();if(!validName(newName))return "Имя: a-z, 0-9, _.-; максимум 48 символов.";String oldKey=key(ownerUuid,oldName),newKey=key(ownerUuid,newName);Point p=POINTS.get(oldKey);if(p==null&&"start".equals(oldName))p=POINTS.get(ownerUuid);if(p==null)return "Метка не найдена.";if(POINTS.containsKey(newKey))return "Метка с таким именем уже существует.";POINTS.remove(oldKey);POINTS.remove(ownerUuid);p.name=newName;p.updatedAt=System.currentTimeMillis();POINTS.put(newKey,p);return save()?null:"Не удалось сохранить метки.";}
	public static synchronized String remove(String ownerUuid,String name){load();Point removed=POINTS.remove(key(ownerUuid,name));if(removed==null&&"start".equals(name))removed=POINTS.remove(ownerUuid);if(removed==null)return "Метка не найдена.";return save()?null:"Не удалось сохранить метки.";}
	private static String key(String ownerUuid,String name){return ownerUuid+"|"+(name==null?"start":name);}
	public static boolean validName(String name){return name!=null&&name.matches("[a-z0-9_.-]{1,48}");}
	public static synchronized boolean save() {
		Path path = file(); if (path == null) return false;
		return CwDurable.write(path, GSON.toJson(POINTS));
	}
}
