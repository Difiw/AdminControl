package ru.admincontrol.cw;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ru.admincontrol.data.DataManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Sequential, repeatable migrations for CW-owned plain JSON stores. */
public final class CwMigrationService {
	private static final Gson GSON=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static volatile boolean healthy=true;
	private static final Map<String,Integer> TARGETS=Map.of(
			"cw-builder-returns.json",2,"cw-instances.json",1,"cw-story-graphs.json",1,"cw-story-progress.json",1,"cw-points.json",1);
	private CwMigrationService(){}
	public static synchronized List<String> run(boolean dryRun){
		Path root=DataManager.get()==null?null:DataManager.get().getConfigDir();if(root==null)return List.of("FAIL: DataManager unavailable");
		List<String> report=new ArrayList<>();Map<Path,String> writes=new LinkedHashMap<>();
		try{
			for(var spec:TARGETS.entrySet()){
				Path file=root.resolve(spec.getKey());if(!Files.isRegularFile(file)){report.add("SKIP: "+spec.getKey()+" отсутствует");continue;}
				CwDurable.Loaded stored=CwDurable.read(file);if(!stored.present())throw new IllegalStateException(spec.getKey()+": файл и резервная копия не читаются ("+stored.note()+")");
				JsonElement parsed=JsonParser.parseString(stored.json());if(!parsed.isJsonObject())throw new IllegalStateException(spec.getKey()+": root должен быть object");
				JsonObject rootObject=parsed.getAsJsonObject();int changed=0;
				for(var entry:rootObject.entrySet())if(entry.getValue().isJsonObject())changed+=migrateRecord(spec.getKey(),entry.getValue().getAsJsonObject(),spec.getValue());
				if(changed>0){String encoded=GSON.toJson(rootObject);JsonParser.parseString(encoded);writes.put(file,encoded);report.add((dryRun?"WOULD MIGRATE: ":"MIGRATE: ")+spec.getKey()+" records="+changed);}
				else report.add("PASS: "+spec.getKey()+" schema current; no-op");
			}
			validateInstances(root,writes,report);
			if(!dryRun&&!writes.isEmpty()){
				Path backup=CwBackupService.backupNow();report.add("BACKUP: "+backup);
				for(var entry:writes.entrySet())if(!CwDurable.write(entry.getKey(),entry.getValue()))throw new IllegalStateException("не удалось записать "+entry.getKey().getFileName());
			}
			report.add((dryRun?"DRY-RUN":"APPLY")+": PASS, changes="+writes.size());
		}catch(Exception e){report.add("FAIL: "+e.getClass().getSimpleName()+": "+e.getMessage());if(!dryRun)healthy=false;}
		if(!dryRun&&report.stream().noneMatch(x->x.startsWith("FAIL")))healthy=true;
		if(!dryRun)writeReport(root,report);return List.copyOf(report);
	}
	public static boolean healthy(){return healthy;}
	static String migrateDocument(String file,String raw){Integer target=TARGETS.get(file);if(target==null)throw new IllegalArgumentException("unknown store");JsonElement parsed=JsonParser.parseString(raw);if(!parsed.isJsonObject())throw new IllegalArgumentException("root is not object");JsonObject root=parsed.getAsJsonObject();for(var e:root.entrySet())if(e.getValue().isJsonObject())migrateRecord(file,e.getValue().getAsJsonObject(),target);return GSON.toJson(root);}
	private static int migrateRecord(String file,JsonObject record,int target){
		String field=file.equals("cw-story-progress.json")?"version":"schemaVersion";int version=record.has(field)&&record.get(field).isJsonPrimitive()?record.get(field).getAsInt():0;int original=version;
		while(version<target){
			if(file.equals("cw-builder-returns.json")&&version==0){if(!record.has("sessionId"))record.addProperty("sessionId",UUID.randomUUID().toString());if(!record.has("phase"))record.addProperty("phase","RECOVERY_REQUIRED");version=1;}
			else if(file.equals("cw-builder-returns.json")&&version==1){if(!record.has("updatedAt"))record.addProperty("updatedAt",System.currentTimeMillis());if(!record.has("failure"))record.addProperty("failure","legacy session migrated; restore required");version=2;}
			else {version++;}
			record.addProperty(field,version);
		}
		return original==version?0:1;
	}
	private static void validateInstances(Path root,Map<Path,String>writes,List<String> report)throws Exception{
		Path file=root.resolve("cw-instances.json");if(!Files.isRegularFile(file)&&!writes.containsKey(file))return;
		String raw=writes.containsKey(file)?writes.get(file):CwDurable.read(file).json();if(raw==null)throw new IllegalStateException("cw-instances.json не читается");JsonObject object=JsonParser.parseString(raw).getAsJsonObject();java.util.Set<String> activeWorlds=new java.util.HashSet<>();
		for(var e:object.entrySet()){if(!e.getValue().isJsonObject())throw new IllegalStateException("instance "+e.getKey()+" не object");JsonObject i=e.getValue().getAsJsonObject();String lifecycle=i.has("lifecycle")?i.get("lifecycle").getAsString():"READY";String world=i.has("worldKey")?i.get("worldKey").getAsString():"";if(!lifecycle.equals("ARCHIVED")&&!lifecycle.equals("DELETING")&&!world.isBlank()&&!activeWorlds.add(world))throw new IllegalStateException("два активных instance используют "+world);}
		report.add("PASS: instance worldKey uniqueness");
	}
	private static void writeReport(Path root,List<String> report){try{List<String> lines=new ArrayList<>();lines.add("CW migration "+Instant.now());lines.addAll(report);Files.write(root.resolve("cw-migration-last.log"),lines,StandardCharsets.UTF_8);}catch(Exception ignored){}}
}
