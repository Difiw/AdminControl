package ru.admincontrol.cw;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.net.ModPackets;

/** Server authority boundary for opening, validating and saving the visual graph editor. */
public final class CwGraphNetwork {
	public static final int MAX_JSON = 1_000_000;
	private CwGraphNetwork() {}
	public static void open(ServerPlayerEntity player) {
		if (!allowed(player)) { player.sendMessage(Text.literal("[CW] Редактор графа доступен только ГА+."), true); return; }
		String clientProblem=CwClientGate.problem(player); if(clientProblem!=null){player.sendMessage(Text.literal("[CW] "+clientProblem),false);return;}
		send(player, CwStoryGraph.getOrCreateDefault(player.getName().getString()), "OPEN");
	}
	public static void save(ServerPlayerEntity player, int expectedRevision, String json) {
		if (!allowed(player) || json == null || json.length() > MAX_JSON) return;
		try {
			CwStoryGraph.Graph graph=CwStoryGraph.fromJson(json);
			CwStoryGraph.SaveResult result=CwStoryGraph.save(graph,expectedRevision,player.getName().getString());
			CwAudit.writeSystem(player.getName().getString(),"graph save",graph==null?"-":graph.graphId,result.message());
			CwStoryGraph.Graph authoritative=graph==null?null:CwStoryGraph.get(graph.graphId);
			if(authoritative!=null)send(player,authoritative,result.ok()?"SAVED":"ERROR: "+result.message()+" "+String.join("; ",result.validation()));
			else player.sendMessage(Text.literal("[CW] Граф отклонён: "+result.message()),false);
		} catch(Exception e){player.sendMessage(Text.literal("[CW] Некорректный пакет графа."),false);CwAudit.writeSystem(player.getName().getString(),"graph save","parse","rejected");}
	}
	public static void test(ServerPlayerEntity player,String graphId,String nodeId){
		if(!allowed(player)||graphId==null||nodeId==null||graphId.length()>80||nodeId.length()>64)return;
		CwStoryGraph.Graph graph=CwStoryGraph.get(graphId);if(graph==null||!graph.nodes.containsKey(nodeId)){player.sendMessage(Text.literal("[CW] Узел для теста не найден."),false);return;}
		String error=CwTestEntryService.offer(player,graph.storyId,graph.templateId,1);
		if(error==null){CwStoryService.Progress progress=CwStoryService.live(player.getUuid()).copy();progress.stageId=nodeId;CwStoryService.setPendingTest(player,progress);}
		else player.sendMessage(Text.literal("[CW] "+error),false);
		CwAudit.writeSystem(player.getName().getString(),"graph test",graphId+"/"+nodeId,error==null?"offered":error);
	}
	public static boolean allowed(ServerPlayerEntity p){return p!=null&&CommandUtil.hasRank(p.getCommandSource(),AdminRanks.GA);}
	private static void send(ServerPlayerEntity player,CwStoryGraph.Graph graph,String status){
		if(!ServerPlayNetworking.canSend(player,ModPackets.CW_GRAPH_DATA))return;var buf=PacketByteBufs.create();
		buf.writeString(status,4096);buf.writeString(CwStoryGraph.toJson(graph),MAX_JSON);var chosen=new java.util.ArrayList<java.util.Map.Entry<String,byte[]>>();int total=0;for(String skin:ru.admincontrol.util.CwNpcService.skins()){byte[] png=ru.admincontrol.util.CwNpcService.skinBytes(skin);if(png.length==0||png.length>262144||total+png.length>1_500_000||chosen.size()>=32)continue;chosen.add(java.util.Map.entry(skin,png));total+=png.length;}buf.writeVarInt(chosen.size());for(var skin:chosen){buf.writeString(skin.getKey(),32);buf.writeByteArray(skin.getValue());}ServerPlayNetworking.send(player,ModPackets.CW_GRAPH_DATA,buf);
	}
}
