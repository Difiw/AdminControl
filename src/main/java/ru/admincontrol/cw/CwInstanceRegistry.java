package ru.admincontrol.cw;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.data.DataManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persistent, idempotent registry separating templates, test instances and personal story instances. */
public final class CwInstanceRegistry {
	public static final int SCHEMA_VERSION = 1;
	public enum Type { TEMPLATE, TEST, PERSONAL }
	public enum Lifecycle { CREATING, READY, LOADED, UNLOADED, ARCHIVED, DELETING, DELETED, ERROR }
	public static final class Instance {
		public int schemaVersion = SCHEMA_VERSION;
		public String instanceId;
		public Type type;
		public String ownerUuid;
		public String templateId;
		public int templateVersion;
		public String storyId;
		public String slot;
		public Lifecycle lifecycle = Lifecycle.CREATING;
		public long createdAt;
		public long updatedAt;
		public String worldKey;
		public String worldFolder;
		public String activeSessionId;
		public String creationReason;
		/** DELETE or RESET, processed before dimensions are loaded on the next server start. */
		public String pendingOperation;
		/** When the template was copied into this instance (0 = never). A seeded instance is never overwritten by a later entry. */
		public long seededAt;
		/** Template version that was copied, and the block region {minX,minY,minZ,maxX,maxY,maxZ} plus entry point {x,y,z} found while copying. */
		public int seededVersion;
		public int[] seedBounds;
		public double[] seedSpawn;
	}
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Map<String, Instance> BY_ID = new LinkedHashMap<>();
	private static boolean loaded;
	private CwInstanceRegistry() {}

	private static Path file() { return DataManager.get() == null ? null : DataManager.get().getConfigDir().resolve("cw-instances.json"); }
	public static synchronized void load() {
		if (loaded) return;
		loaded = true;
		Path p = file();
		if (p == null) return;
		CwDurable.Loaded loadedFile = CwDurable.read(p);
		if (!loadedFile.present()) { if (loadedFile.source() == CwDurable.Source.UNREADABLE) AdminControlMod.LOGGER.error("CW instance registry and its backup are unreadable: {}", loadedFile.note()); return; }
		try {
			Map<String, Instance> read = GSON.fromJson(loadedFile.json(), new TypeToken<Map<String, Instance>>(){}.getType());
			if (read != null) BY_ID.putAll(read);
		} catch (Exception e) { AdminControlMod.LOGGER.error("CW instance registry read failed", e); }
	}
	public static synchronized Map<String, Instance> all() { load(); return Map.copyOf(BY_ID); }
	public static synchronized Instance get(String id){load();return BY_ID.get(id);}
	public static synchronized Instance getOrCreate(Type type, UUID owner, String templateId, int templateVersion,
			String storyId, String slot, String worldKey, String worldFolder, String reason) {
		load();
		String unique = type + "|" + (owner == null ? "" : owner) + "|" + safe(templateId) + "|" + safe(storyId) + "|" + safe(slot);
		for (Instance existing : BY_ID.values()) {
			String key = existing.type + "|" + safe(existing.ownerUuid) + "|" + safe(existing.templateId) + "|" + safe(existing.storyId) + "|" + safe(existing.slot);
			if (key.equals(unique) && active(existing.lifecycle)) return existing;
		}
		Instance i = new Instance();
		i.instanceId = UUID.randomUUID().toString(); i.type = type; i.ownerUuid = owner == null ? null : owner.toString();
		i.templateId = templateId; i.templateVersion = templateVersion; i.storyId = storyId; i.slot = slot;
		i.worldKey = worldKey; i.worldFolder = worldFolder; i.creationReason = reason;
		i.createdAt = i.updatedAt = System.currentTimeMillis(); i.lifecycle = Lifecycle.READY;
		BY_ID.put(i.instanceId, i); save(); return i;
	}
	public static synchronized Instance allocate(Type type, UUID owner, String templateId, int templateVersion, String storyId, String slot, String reason) {
		load();
		for(Instance i:BY_ID.values())if(i.type==type&&safe(i.ownerUuid).equals(owner==null?"":owner.toString())&&safe(i.templateId).equals(safe(templateId))&&safe(i.storyId).equals(safe(storyId))&&safe(i.slot).equals(safe(slot))&&active(i.lifecycle))return i;
		Set<String> used=new java.util.HashSet<>();for(Instance i:BY_ID.values())if(i.lifecycle!=Lifecycle.DELETED)used.add(i.worldKey);
		for(int n=0;n<16;n++){String key="admin_control:cw_story_"+n;if(!used.contains(key))return getOrCreate(type,owner,templateId,templateVersion,storyId,slot,key,"dimensions/admin_control/cw_story_"+n,reason);}
		return null;
	}
	public static synchronized String archive(String id){load();Instance i=BY_ID.get(id);if(i==null)return "Instance не найден.";if(i.lifecycle==Lifecycle.DELETED||i.lifecycle==Lifecycle.DELETING)return "Instance уже удаляется или удалён.";i.lifecycle=Lifecycle.ARCHIVED;i.updatedAt=System.currentTimeMillis();return save()?null:"Не удалось сохранить registry.";}
	public static synchronized String unarchive(String id){load();Instance i=BY_ID.get(id);if(i==null)return "Instance не найден.";if(i.lifecycle!=Lifecycle.ARCHIVED)return "Instance не архивирован.";i.lifecycle=Lifecycle.READY;i.updatedAt=System.currentTimeMillis();return save()?null:"Не удалось сохранить registry.";}
	public static synchronized String schedule(String id,String operation){load();Instance i=BY_ID.get(id);if(i==null)return "Instance не найден.";if(i.type==Type.TEMPLATE)return "Шаблон нельзя reset/delete этой командой.";if(!"RESET".equals(operation)&&!"DELETE".equals(operation))return "Неизвестная операция.";if(i.lifecycle==Lifecycle.DELETED)return "Instance уже удалён.";i.lifecycle=Lifecycle.DELETING;i.pendingOperation=operation;i.updatedAt=System.currentTimeMillis();return save()?null:"Не удалось сохранить registry.";}
	public static synchronized java.util.List<String> processPending(){
		load();java.util.List<String> out=new java.util.ArrayList<>();if(DataManager.get()==null)return java.util.List.of("FAIL: DataManager unavailable");
		for(Instance i:BY_ID.values()){
			if(i.lifecycle!=Lifecycle.DELETING||i.pendingOperation==null)continue;
			try{
				Path worldRoot=DataManager.get().worldRoot().toAbsolutePath().normalize();Path expectedBase=worldRoot.resolve("dimensions").resolve(AdminControlMod.MOD_ID).normalize();
				if(i.worldKey==null||!i.worldKey.matches("admin_control:cw_story_(?:[0-9]|1[0-5])"))throw new IllegalStateException("unsafe worldKey "+i.worldKey);
				Path target=worldRoot.resolve(i.worldFolder==null?"":i.worldFolder).toAbsolutePath().normalize();
				if(!target.startsWith(expectedBase)||target.equals(expectedBase)||!target.getFileName().toString().matches("cw_story_(?:[0-9]|1[0-5])"))throw new IllegalStateException("unsafe folder "+target);
				if(Files.exists(target)){
					// Never delete a story world outright: it is moved to _cw_trash so an administrator can still recover it.
					Path trash=expectedBase.resolve("_cw_trash");Files.createDirectories(trash);
					Path moved=trash.resolve(target.getFileName()+"-"+DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now()));
					Files.move(target,moved);out.add("TRASH: "+target.getFileName()+" -> "+moved);
				}
				i.seededAt=0;i.seedBounds=null;i.seedSpawn=null;
				String op=i.pendingOperation;i.pendingOperation=null;i.lifecycle="RESET".equals(op)?Lifecycle.READY:Lifecycle.DELETED;i.updatedAt=System.currentTimeMillis();out.add("PASS: "+op+" "+i.instanceId+" folder="+target);
			}catch(Exception e){i.lifecycle=Lifecycle.ERROR;out.add("FAIL: "+i.instanceId+" "+e.getMessage());}
		}
		save();return out;
	}
	public static synchronized boolean save() {
		Path p = file(); if (p == null) return false;
		return CwDurable.write(p, GSON.toJson(BY_ID));
	}
	public static synchronized Path backupNow() throws Exception {
		load(); Path source = file(); if (source == null) throw new IllegalStateException("DataManager unavailable");
		Files.createDirectories(source.getParent().resolve("cw-backups"));
		String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
		Path target = source.getParent().resolve("cw-backups").resolve("cw-instances-" + stamp + ".json");
		Files.writeString(target, GSON.toJson(BY_ID), StandardCharsets.UTF_8); return target;
	}
	private static String safe(String s) { return s == null ? "" : s; }
	private static boolean active(Lifecycle lifecycle){return lifecycle==Lifecycle.CREATING||lifecycle==Lifecycle.READY||lifecycle==Lifecycle.LOADED||lifecycle==Lifecycle.UNLOADED;}
}
