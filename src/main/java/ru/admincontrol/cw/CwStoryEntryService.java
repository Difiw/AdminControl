package ru.admincontrol.cw;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.net.ModPackets;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-authoritative Louis start/continue prompt for ordinary story players. */
public final class CwStoryEntryService {
	private record Offer(UUID nonce,long expiresAt,String story,String template,int version,boolean canEnter){}
	private static final Map<UUID,Offer> OFFERS=new ConcurrentHashMap<>();
	/** Neutral in-world wording only: no mention of story, quests, availability or any spoilers. */
	static final String BUSY_TEXT="Сейчас я занята другими делами. Загляни немного позже, хорошо?\n\nУверена, у нас ещё будет время спокойно поговорить.";
	static final String START_TEXT="Привет. Рада тебя видеть.\nУ меня есть, о чём с тобой поговорить. Начнём?";
	static final String CONTINUE_TEXT="Мы уже говорили об этом раньше.\nХочешь продолжить с того места, на котором мы остановились?";
	private static final Map<UUID,Integer> FOCUS=new ConcurrentHashMap<>();
	/** Remembers which NPC entity the player clicked so the prompt can render exactly that NPC. */
	public static void focus(ServerPlayerEntity player,int entityId){if(player!=null)FOCUS.put(player.getUuid(),entityId);}
	private static int focused(ServerPlayerEntity player){return FOCUS.getOrDefault(player.getUuid(),-1);}
	private CwStoryEntryService(){}
	public static String offer(ServerPlayerEntity player,String story,String template,int version){
		if(CwStoryRules.inside(player))return CwStoryService.inTest(player)
				?"Сейчас запущен администраторский тест. Сначала завершите тест."
				:"Вы уже находитесь в Люмире.";
		String problem=CwClientGate.problem(player);if(problem!=null)return problem;
		UUID nonce=UUID.randomUUID();OFFERS.put(player.getUuid(),new Offer(nonce,System.currentTimeMillis()+30_000,story,template,version,true));
		var progress=CwStoryService.live(player.getUuid());boolean started=progress.chapter>0||!"not_started".equals(progress.stageId);
		var buf=PacketByteBufs.create();buf.writeUuid(nonce);buf.writeString("Луи",64);
		buf.writeString(started?CONTINUE_TEXT:START_TEXT,512);
		buf.writeString("",256);buf.writeString("",256);
		buf.writeBoolean(started);buf.writeBoolean(false);buf.writeBoolean(true);buf.writeVarInt(focused(player));ServerPlayNetworking.send(player,ModPackets.CW_TEST_OFFER,buf);return null;
	}
	public static String unavailable(ServerPlayerEntity player){
		if(player==null)return "Игрок недоступен.";
		UUID nonce=UUID.randomUUID();OFFERS.put(player.getUuid(),new Offer(nonce,System.currentTimeMillis()+30_000,"default","builder",1,false));
		var buf=PacketByteBufs.create();buf.writeUuid(nonce);buf.writeString("Луи",64);
		buf.writeString(BUSY_TEXT,512);
		buf.writeString("",256);buf.writeString("",256);buf.writeBoolean(false);buf.writeBoolean(false);buf.writeBoolean(false);buf.writeVarInt(focused(player));
		ServerPlayNetworking.send(player,ModPackets.CW_TEST_OFFER,buf);return null;
	}
	public static void answer(ServerPlayerEntity player,UUID nonce,int action){
		Offer offer=OFFERS.remove(player.getUuid());
		if(offer==null||!offer.nonce.equals(nonce)||offer.expiresAt<System.currentTimeMillis()){player.sendMessage(Text.literal("Предложение Луи устарело."),true);return;}
		if(action==0)return;
		if(!offer.canEnter){player.sendMessage(Text.literal("Загляни позже."),true);return;}
		var progress=CwStoryService.live(player.getUuid());boolean started=progress.chapter>0||!"not_started".equals(progress.stageId);
		if(CwStoryRules.inside(player)){player.sendMessage(Text.literal(CwStoryService.inTest(player)?"Сначала завершите администраторский тест.":"Вы уже находитесь в Люмире."),false);return;}
		if(action==1&&started){player.sendMessage(Text.literal("Ваш путь в Люмире уже начат — выберите «Продолжить»."),true);return;}
		if(action!=1&&action!=2)return;
		// Fade to the Люмира overlay first, then switch worlds, so the vanilla loading screen is never seen.
		CwTransition.play(player,"Люмира",started?"С возвращением":"Добро пожаловать",()->{
			String error=CwStoryWorldService.enterPersonal(player,offer.story,offer.template,offer.version,"main");
			if(error!=null){player.sendMessage(Text.literal(error),false);return;}
			if(!started){progress.paused=false;CwStoryService.touch(progress,"louis:entered");CwStoryService.save();}
		});
	}
	public static void clear(ServerPlayerEntity player){if(player!=null)OFFERS.remove(player.getUuid());}
}
