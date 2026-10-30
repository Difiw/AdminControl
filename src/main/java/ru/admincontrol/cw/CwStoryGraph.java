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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative, revisioned story graph store. */
public final class CwStoryGraph {
	public static final int SCHEMA_VERSION = 2;
	public enum Kind { START, DIALOGUE, CHOICE, CONDITION, TRIGGER, ACTION, NPC, ITEM, BLOCK, EFFECT, AUDIO, CHECKPOINT, TIME, TELEPORT, WAIT, GROUP, END }
	public static final class Node {
		public String id;
		public Kind kind;
		public double x, y;
		public String title = "";
		public Map<String, String> data = new LinkedHashMap<>();
	}
	public static final class Edge {
		public String id;
		public String from;
		public String port = "next";
		public String outputType = "flow";
		public String inputType = "flow";
		public String to;
	}
	public static final class Graph {
		public int schemaVersion = SCHEMA_VERSION;
		public String graphId;
		public String storyId;
		public String templateId;
		public int revision;
		public long modifiedAt;
		public String modifiedBy;
		public Map<String, Node> nodes = new LinkedHashMap<>();
		public List<Edge> edges = new ArrayList<>();
		public Graph copy() { return GSON.fromJson(GSON.toJson(this), Graph.class); }
	}
	public record SaveResult(boolean ok, String message, int revision, List<String> validation) {}
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Map<String, Graph> GRAPHS = new LinkedHashMap<>();
	private static boolean loaded;
	private CwStoryGraph() {}
	private static Path file() { return DataManager.get() == null ? null : DataManager.get().getConfigDir().resolve("cw-story-graphs.json"); }

	public static synchronized void load() {
		if (loaded) return; loaded = true; Path p = file(); if (p == null || !Files.isRegularFile(p)) return;
		try {
			Map<String, Graph> read = GSON.fromJson(CwDurable.text(p), new TypeToken<Map<String, Graph>>(){}.getType());
			if (read != null) read.forEach((id, graph) -> { normalize(graph); GRAPHS.put(id, graph); });
		} catch (Exception e) { AdminControlMod.LOGGER.error("CW graph store read failed", e); }
	}
	public static synchronized Graph get(String graphId) { load(); Graph g = GRAPHS.get(graphId); return g == null ? null : g.copy(); }
	public static synchronized Map<String, Graph> all() { load(); Map<String, Graph> out = new LinkedHashMap<>(); GRAPHS.forEach((k,v)->out.put(k,v.copy())); return out; }
	public static synchronized Graph create(String storyId, String templateId, String actor) {
		load(); Graph g = new Graph(); g.graphId = UUID.randomUUID().toString(); g.storyId = storyId; g.templateId = templateId;
		addStartAndEnd(g);
		g.modifiedBy = actor; g.modifiedAt = System.currentTimeMillis(); GRAPHS.put(g.graphId, g); write(); return g.copy();
	}
	public static synchronized Graph getOrCreateDefault(String actor) {
		load();
		for (Graph graph : GRAPHS.values()) if ("default".equals(graph.storyId) && "builder".equals(graph.templateId)){if(ensureLouisDialogue(graph)){graph.modifiedAt=System.currentTimeMillis();graph.modifiedBy=actor;write();}return graph.copy();}
		Graph graph = new Graph(); graph.graphId = UUID.randomUUID().toString(); graph.storyId = "default"; graph.templateId = "builder";
		addStartAndEnd(graph);ensureLouisDialogue(graph);
		graph.modifiedBy = actor; graph.modifiedAt = System.currentTimeMillis(); GRAPHS.put(graph.graphId, graph); write(); return graph.copy();
	}
	public static String toJson(Graph graph) { normalize(graph); return GSON.toJson(graph); }
	public static Graph fromJson(String json) { Graph graph=GSON.fromJson(json, Graph.class); normalize(graph); return graph; }
	public static synchronized SaveResult save(Graph incoming, int expectedRevision, String actor) {
		load(); if (incoming == null || incoming.graphId == null || incoming.graphId.length() > 80) return new SaveResult(false,"invalid graph",-1,List.of());
		Graph current = GRAPHS.get(incoming.graphId);
		if (current != null && current.revision != expectedRevision) return new SaveResult(false,"revision conflict",current.revision,List.of());
		if (incoming.nodes.size() > 1024 || incoming.edges.size() > 4096) return new SaveResult(false,"graph limits exceeded",expectedRevision,List.of());
		List<String> errors = validate(incoming);
		if (!errors.isEmpty()) return new SaveResult(false,"validation failed",expectedRevision,errors);
		Graph stored = incoming.copy(); stored.schemaVersion = SCHEMA_VERSION; stored.revision = expectedRevision + 1;
		stored.modifiedAt = System.currentTimeMillis(); stored.modifiedBy = actor; GRAPHS.put(stored.graphId, stored);
		if (!write()) return new SaveResult(false,"disk write failed",expectedRevision,List.of());
		return new SaveResult(true,"saved",stored.revision,List.of());
	}
	public static List<String> validate(Graph g) {
		List<String> errors = new ArrayList<>(); if (g == null) return List.of("graph is null"); normalize(g);
		long starts = g.nodes.values().stream().filter(n->n.kind==Kind.START).count(); if (starts != 1) errors.add("Требуется ровно один START, найдено: "+starts);
		Set<String> edgeIds = new HashSet<>(); Map<String,List<String>> next = new HashMap<>(), previous = new HashMap<>();
		for (Edge e : g.edges) {
			if (e.id==null || !edgeIds.add(e.id)) errors.add("Дублирующийся edge id: "+e.id);
			if (!g.nodes.containsKey(e.from) || !g.nodes.containsKey(e.to)) errors.add("Ребро "+e.id+" ссылается на отсутствующий узел");
			else { next.computeIfAbsent(e.from,k->new ArrayList<>()).add(e.to); previous.computeIfAbsent(e.to,k->new ArrayList<>()).add(e.from); }
			if(e.outputType!=null&&e.inputType!=null&&!e.outputType.equals(e.inputType))errors.add("Несовместимые порты ребра "+e.id+": "+e.outputType+" → "+e.inputType);
		}
		Node start = g.nodes.values().stream().filter(n->n.kind==Kind.START).findFirst().orElse(null);
		if (start != null) {
			Set<String> reachable = new HashSet<>(); ArrayDeque<String> q = new ArrayDeque<>(); q.add(start.id);
			while(!q.isEmpty()){String id=q.removeFirst();if(!reachable.add(id))continue;for(String n:next.getOrDefault(id,List.of()))q.addLast(n);}
			for(String id:g.nodes.keySet())if(!reachable.contains(id))errors.add("Недостижимый узел: "+id);
			boolean hasEnd = reachable.stream().map(g.nodes::get).anyMatch(n->n!=null&&n.kind==Kind.END); if(!hasEnd)errors.add("Нет достижимого END");
			Set<String> reachesEnd = new HashSet<>(); ArrayDeque<String> reverse = new ArrayDeque<>();
			for (Node node : g.nodes.values()) if (node.kind == Kind.END && reachable.contains(node.id)) reverse.add(node.id);
			while (!reverse.isEmpty()) { String id = reverse.removeFirst(); if (!reachesEnd.add(id)) continue; for (String n : previous.getOrDefault(id, List.of())) reverse.addLast(n); }
			for (String id : reachable) if (!reachesEnd.contains(id)) errors.add("Ветка/цикл без выхода к END: " + id);
		}
		for(Node n:g.nodes.values()){
			if(n.id==null||n.id.isBlank()||n.id.length()>64)errors.add("Некорректный node id");
			if(n.kind==null)errors.add("Не задан тип узла "+n.id);
			for(var e:n.data.entrySet())if(e.getKey().length()>64||e.getValue().length()>4096)errors.add("Слишком длинные данные узла "+n.id);
			if (n.kind == Kind.DIALOGUE && blank(n.data.get("text"))) errors.add("Диалог " + n.id + " не содержит text");
			if (n.kind == Kind.WAIT && !nonNegativeNumber(n.data.get("ticks"))) errors.add("WAIT " + n.id + " требует ticks >= 0");
			if (n.kind == Kind.TELEPORT && (net.minecraft.util.Identifier.tryParse(n.data.get("world")) == null
					|| !number(n.data.get("x")) || !number(n.data.get("y")) || !number(n.data.get("z")))) errors.add("TELEPORT " + n.id + " содержит неверную цель");
			if (n.kind == Kind.ACTION && n.data.containsKey("command") && !safeCommand(n.data.get("command"))) errors.add("Команда ACTION " + n.id + " не в allowlist");
			if (n.kind == Kind.CHOICE && g.edges.stream().filter(e->n.id.equals(e.from)).map(e->e.port).distinct().count()<1) errors.add("Выбор "+n.id+" не имеет выходов");
			if (n.kind == Kind.CONDITION && !validCondition(n.data.get("condition"))) errors.add("Условие "+n.id+" пусто или некорректно");
		}
		return errors;
	}
	private static boolean blank(String value) { return value == null || value.isBlank(); }
	private static void addStartAndEnd(Graph graph) {
		Node start = new Node(); start.id = "start"; start.kind = Kind.START; start.title = "Начало"; start.x = 40; start.y = 80;
		Node end = new Node(); end.id = "end"; end.kind = Kind.END; end.title = "Конец"; end.x = 360; end.y = 80;
		graph.nodes.put(start.id, start); graph.nodes.put(end.id, end);
		Edge edge = new Edge(); edge.id = UUID.randomUUID().toString(); edge.from = start.id; edge.to = end.id; graph.edges.add(edge);
	}
	private static boolean ensureLouisDialogue(Graph graph){if(graph.nodes.containsKey("louis_first_dialogue"))return false;Node louis=new Node();louis.id="louis_first_dialogue";louis.kind=Kind.DIALOGUE;louis.title="Луи: первое знакомство";louis.x=200;louis.y=80;louis.data.put("npc","Луи");louis.data.put("speaker","Луи");louis.data.put("text","Привет. Я Луи. За этой дверью начинается история, в которой каждый твой выбор будет иметь значение. Ты готов начать?");louis.data.put("portrait","Louis");graph.nodes.put(louis.id,louis);graph.edges.removeIf(e->"start".equals(e.from)&&"end".equals(e.to));Edge first=new Edge();first.id=UUID.randomUUID().toString();first.from="start";first.to=louis.id;Edge last=new Edge();last.id=UUID.randomUUID().toString();last.from=louis.id;last.to="end";graph.edges.add(first);graph.edges.add(last);return true;}
	public static synchronized String louisOpening(){Graph graph=getOrCreateDefault("system");Node node=graph.nodes.get("louis_first_dialogue");return node==null?"Привет. Я Луи. Ты готов начать историю?":node.data.getOrDefault("text","Привет. Я Луи. Ты готов начать историю?");}
	/** Repairs legacy/partially written graphs before the client or validator touches them. */
	public static void normalize(Graph graph) {
		if (graph == null) return;
		if (graph.nodes == null) graph.nodes = new LinkedHashMap<>();
		if (graph.edges == null) graph.edges = new ArrayList<>();
		for (Map.Entry<String, Node> entry : graph.nodes.entrySet()) {
			Node node=entry.getValue(); if(node==null){node=new Node();entry.setValue(node);}
			if (blank(node.id)) node.id=entry.getKey();
			if (node.data == null) node.data=new LinkedHashMap<>();
			if (node.title == null) node.title="";
			if (node.kind == null) {
				String id=node.id==null?"":node.id.toLowerCase(java.util.Locale.ROOT);
				if ("start".equals(id)) node.kind=Kind.START;
				else if ("end".equals(id)) node.kind=Kind.END;
				else if (node.data.containsKey("text")) node.kind=Kind.DIALOGUE;
				else if (node.data.containsKey("command")||node.data.containsKey("action")) node.kind=Kind.ACTION;
				else if (node.data.containsKey("condition")) node.kind=Kind.CONDITION;
				else if (node.data.containsKey("ticks")) node.kind=Kind.WAIT;
				else if (node.data.containsKey("world")||node.data.containsKey("marker")) node.kind=Kind.TELEPORT;
				else node.kind=Kind.DIALOGUE;
			}
		}
		graph.edges.removeIf(e -> e == null);
		for (Edge edge : graph.edges) { if(edge.id==null)edge.id=UUID.randomUUID().toString();if(edge.port==null)edge.port="next";if(edge.outputType==null)edge.outputType="flow";if(edge.inputType==null)edge.inputType="flow"; }
	}
	private static boolean validCondition(String json){if(blank(json))return false;try{return validConditionValue(com.google.gson.JsonParser.parseString(json),0);}catch(Exception e){return false;}}
	private static boolean validConditionValue(com.google.gson.JsonElement value,int depth){
		if(depth>16||value==null||!value.isJsonObject())return false;var o=value.getAsJsonObject();String op=o.has("op")?o.get("op").getAsString():"";
		if("NOT".equals(op))return o.has("child")&&validConditionValue(o.get("child"),depth+1);
		if("AND".equals(op)||"OR".equals(op)){if(!o.has("children")||!o.get("children").isJsonArray()||o.getAsJsonArray("children").size()==0)return false;for(var child:o.getAsJsonArray("children"))if(!validConditionValue(child,depth+1))return false;return true;}
		return java.util.Set.of("ITEM","PROGRESS","FLAG","CHOICE","CHAPTER","WORLD","ZONE","TIME","NPC_STATE").contains(op)&&o.has("key");
	}
	private static boolean number(String value) { try { double d=Double.parseDouble(value); return Double.isFinite(d); } catch (Exception e) { return false; } }
	private static boolean nonNegativeNumber(String value) { try { return Long.parseLong(value) >= 0; } catch (Exception e) { return false; } }
	private static boolean safeCommand(String command) {
		if (blank(command) || command.length() > 256 || command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0) return false;
		String normalized = command.startsWith("/") ? command.substring(1) : command;
		String root = normalized.split("\\s+", 2)[0].toLowerCase(java.util.Locale.ROOT);
		return Set.of("say", "tellraw", "title", "playsound", "particle", "effect", "give", "clear", "teleport", "tp").contains(root);
	}
	private static boolean write() {
		Path p=file();if(p==null)return false;return CwDurable.write(p,GSON.toJson(GRAPHS));
	}
}
