package ru.admincontrol.cw;

import com.google.gson.JsonParser;
import ru.admincontrol.data.DataManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Timestamped CW backups with hashes and non-mutating migration checks. */
public final class CwBackupService {
	private static final String[] FILES = {"cw-builder-returns.json", "cw-instances.json", "cw-story-graphs.json", "cw-story-progress.json", "cw-points.json", "settings.json"};
	private CwBackupService() {}
	private static Path root(){return DataManager.get()==null?null:DataManager.get().getConfigDir();}
	public static Path backupNow() throws Exception {
		Path root=root();if(root==null)throw new IllegalStateException("DataManager unavailable");
		String stamp=DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC).format(Instant.now());
		Path dir=root.resolve("cw-backups").resolve(stamp);Files.createDirectories(dir);List<String> manifest=new ArrayList<>();
		for(String name:FILES){Path src=root.resolve(name);if(!Files.isRegularFile(src))continue;Path dst=dir.resolve(name);Files.copy(src,dst,StandardCopyOption.COPY_ATTRIBUTES);manifest.add(name+" "+sha(dst));}
		Files.write(dir.resolve("manifest.sha256"),manifest,StandardCharsets.UTF_8);
		List<String> checked=verify(dir);if(checked.stream().anyMatch(x->x.startsWith("FAIL")))throw new IllegalStateException("backup verification failed: "+checked);
		prune(root.resolve("cw-backups"));return dir;
	}
	public static List<String> verify(Path dir) throws Exception {
		List<String> out=new ArrayList<>();Path manifest=dir.resolve("manifest.sha256");if(!Files.isRegularFile(manifest))return List.of("FAIL: manifest отсутствует");
		for(String line:Files.readAllLines(manifest,StandardCharsets.UTF_8)){int p=line.indexOf(' ');if(p<1)continue;String name=line.substring(0,p),expected=line.substring(p+1);Path f=dir.resolve(name);out.add((Files.isRegularFile(f)&&sha(f).equals(expected)?"PASS: ":"FAIL: ")+name);}
		return out;
	}
	public static List<String> verifyLatest() throws Exception {
		Path base=root().resolve("cw-backups");if(!Files.isDirectory(base))return List.of("FAIL: backups отсутствуют");
		try(var stream=Files.list(base)){Path latest=stream.filter(Files::isDirectory).max(Path::compareTo).orElse(null);return latest==null?List.of("FAIL: backups отсутствуют"):verify(latest);}
	}
	public static List<String> dryRunMigration() {
		return CwMigrationService.run(true);
	}
	private static void prune(Path base)throws Exception{
		int configured=DataManager.get()==null?10:DataManager.get().settings().cwBackupRetention;int keep=Math.max(2,Math.min(100,configured));
		if(!Files.isDirectory(base))return;List<Path> dirs;try(var stream=Files.list(base)){dirs=stream.filter(Files::isDirectory).sorted().toList();}
		for(int i=0;i<dirs.size()-keep;i++){Path old=dirs.get(i);if(verify(old).stream().noneMatch(x->x.startsWith("FAIL")))deleteTree(old);}
	}
	private static void deleteTree(Path root)throws Exception{try(var stream=Files.walk(root)){for(Path p:stream.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
	private static String sha(Path p)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
}
