package ru.admincontrol.cw;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.net.ModPackets;
import ru.admincontrol.util.NpcPlayerEntity;
import ru.admincontrol.util.NpcStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** One reusable, nonce/sequence protected dialogue protocol for every CW NPC. */
public final class CwDialogueService {
	public record Line(boolean player,String speaker,String text){}
	public record Option(String id,String button,String full,String next,String condition,String action){}
	private static final class Session{UUID nonce=UUID.randomUUID();int sequence;int entityId;String npcId,name,role,current;List<Line> history=new ArrayList<>();List<Option> options=new ArrayList<>();long lastChoice;}
	private static final Map<UUID,Session> SESSIONS=new ConcurrentHashMap<>();private CwDialogueService(){}
	public static boolean open(ServerPlayerEntity player,NpcPlayerEntity npc,NpcStore.Saved saved){if(player==null||npc==null||saved==null||saved.dialogue==null||saved.dialogue.isEmpty())return false;Session s=new Session();s.entityId=npc.getId();s.npcId=saved.logicalId==null?saved.nick:saved.logicalId;s.name=saved.displayName==null||saved.displayName.isBlank()?saved.nick:saved.displayName;s.role=saved.dialogue.getOrDefault("role","");s.current=first(saved.dialogue);s.history.add(new Line(false,s.name,s.current));s.options=options(saved.dialogue).stream().filter(o->visible(player,o.condition)).toList();SESSIONS.put(player.getUuid(),s);send(player,s);return true;}
	private static String first(Map<String,String> d){for(String k:List.of("text","default","start"))if(d.containsKey(k)&&!d.get(k).isBlank())return d.get(k);for(var e:d.entrySet())if(!e.getKey().startsWith("option.")&&!"role".equals(e.getKey())&&!e.getValue().isBlank())return e.getValue();return "...";}
	private static List<Option> options(Map<String,String> d){List<Option> out=new ArrayList<>();for(String key:d.keySet())if(key.startsWith("option.")&&key.endsWith(".button")){String id=key.substring(7,key.length()-7);String button=d.get(key),full=d.getOrDefault("option."+id+".full",button),next=d.getOrDefault("option."+id+".next","");out.add(new Option(id,button,full,next,d.getOrDefault("option."+id+".condition",""),d.getOrDefault("option."+id+".action","")));}if(out.isEmpty())out.add(new Option("close","Закрыть","До встречи.","","",""));return out;}
	public static void choose(ServerPlayerEntity player,UUID nonce,int sequence,int index){Session s=SESSIONS.get(player.getUuid());if(index<0){if(s!=null&&s.nonce.equals(nonce))close(player);return;}long now=System.currentTimeMillis();if(s==null||!s.nonce.equals(nonce)||sequence!=s.sequence||index>=s.options.size()||now-s.lastChoice<250L){player.sendMessage(Text.literal("Диалог устарел или ответ уже принят."),true);return;}s.lastChoice=now;Option o=s.options.get(index);if(!visible(player,o.condition)){player.sendMessage(Text.literal("Этот ответ больше недоступен."),true);return;}apply(player,o.action);s.history.add(new Line(true,player.getName().getString(),o.full));s.sequence++;if(o.next==null||o.next.isBlank()||"close".equals(o.id)){close(player);return;}NpcStore.Saved saved=NpcStore.byLogicalId(s.npcId);String next=saved==null?null:saved.dialogue.get(o.next);if(next==null||next.isBlank()){close(player);return;}s.current=next;s.history.add(new Line(false,s.name,next));s.options=options(saved.dialogue).stream().filter(x->visible(player,x.condition)).toList();send(player,s);}
	private static boolean visible(ServerPlayerEntity p,String condition){if(condition==null||condition.isBlank())return true;if(condition.startsWith("flag:")){String[] x=condition.substring(5).split("=",2);boolean expected=x.length<2||Boolean.parseBoolean(x[1]);return CwStoryService.live(p.getUuid()).flags.getOrDefault(x[0],false)==expected;}return false;}
	private static void apply(ServerPlayerEntity p,String action){if(action==null||action.isBlank())return;if(action.startsWith("flag:")){String[] x=action.substring(5).split("=",2);if(x.length==2){var q=CwStoryService.live(p.getUuid());q.flags.put(x[0],Boolean.parseBoolean(x[1]));CwStoryService.touch(q,"dialogue");CwStoryService.save();}}}
	private static void send(ServerPlayerEntity p,Session s){var visible=s.options.stream().limit(12).toList();var b=PacketByteBufs.create();b.writeUuid(s.nonce);b.writeVarInt(s.sequence);b.writeVarInt(s.entityId);b.writeString(s.name,64);b.writeString(s.role,128);b.writeString(s.current,4096);b.writeVarInt(Math.min(100,s.history.size()));for(Line l:s.history.subList(Math.max(0,s.history.size()-100),s.history.size())){b.writeBoolean(l.player);b.writeString(l.speaker,64);b.writeString(l.text,4096);}b.writeVarInt(visible.size());for(Option o:visible){b.writeString(o.button,256);b.writeString(o.full,4096);}ServerPlayNetworking.send(p,ModPackets.CW_DIALOGUE_STATE,b);}
	public static void close(ServerPlayerEntity p){SESSIONS.remove(p.getUuid());var b=PacketByteBufs.create();b.writeUuid(new UUID(0,0));ServerPlayNetworking.send(p,ModPackets.CW_DIALOGUE_STATE,b);}
	public static void clear(ServerPlayerEntity p){if(p!=null)SESSIONS.remove(p.getUuid());}
}
