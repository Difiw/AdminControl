package ru.admincontrol.cw;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.net.ModPackets;
import ru.admincontrol.util.NpcPlayerEntity;
import ru.admincontrol.util.NpcService;
import ru.admincontrol.util.NpcStore;

/** Stores the animation cycle bound to each NPC and keeps every client's copy in sync. */
public final class CwAnimationService {
	private CwAnimationService() {}

	/** Admin bound a cycle (or cleared it with a blank string) from the animation editor. */
	public static void bind(ServerPlayerEntity player, String npcName, String json) {
		if (player == null || npcName == null || npcName.length() > 32 || json == null || json.length() > CwAnimation.MAX_JSON) return;
		if (!CwGraphNetwork.allowed(player)) { player.sendMessage(Text.literal("[CW] Привязка анимаций доступна только ГА+."), true); return; }
		MinecraftServer server = player.getServer();
		NpcPlayerEntity npc = server == null ? null : NpcService.find(server, npcName);
		if (npc == null || NpcStore.get(npcName) == null) { player.sendMessage(Text.literal("[CW] NPC не найден: " + npcName), true); return; }
		String clean = json.isBlank() ? "" : CwAnimation.toJson(CwAnimation.parse(json));
		boolean clear = clean.isEmpty() || CwAnimation.parse(clean).frames.isEmpty();
		NpcStore.setAnimJson(npcName, clear ? "" : clean);
		broadcast(server, npcName, clear ? "" : clean);
		CwAudit.writeSystem(player.getName().getString(), "npc animation", npcName, clear ? "cleared" : "bound");
		player.sendMessage(Text.literal(clear ? "[CW] Анимация NPC «" + npcName + "» снята." : "[CW] Анимация привязана к NPC «" + npcName + "»."), true);
	}

	/** Pose tool on an NPC: open the animation editor locked to that NPC with its current cycle. */
	public static void openEditor(ServerPlayerEntity player, NpcPlayerEntity npc) {
		if (player == null || npc == null || !ServerPlayNetworking.canSend(player, ModPackets.CW_ANIM_OPEN)) return;
		String nick = npc.getGameProfile().getName();
		NpcStore.Saved saved = NpcStore.get(nick);
		var buf = PacketByteBufs.create();
		buf.writeString(nick, 32);
		buf.writeString(saved == null || saved.animJson == null ? "" : saved.animJson, CwAnimation.MAX_JSON);
		ServerPlayNetworking.send(player, ModPackets.CW_ANIM_OPEN, buf);
	}

	public static void broadcast(MinecraftServer server, String npcName, String json) {
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) send(p, npcName, json);
	}

	private static void send(ServerPlayerEntity p, String npcName, String json) {
		if (!ServerPlayNetworking.canSend(p, ModPackets.CW_ANIM_SYNC)) return;
		var buf = PacketByteBufs.create();
		buf.writeString(npcName, 32);
		buf.writeString(json, CwAnimation.MAX_JSON);
		ServerPlayNetworking.send(p, ModPackets.CW_ANIM_SYNC, buf);
	}

	/** On join: give the player every stored NPC animation. */
	public static void syncAll(ServerPlayerEntity player) {
		for (NpcStore.Saved saved : NpcStore.all())
			if (saved != null && saved.animJson != null && !saved.animJson.isBlank() && saved.nick != null) send(player, saved.nick, saved.animJson);
	}
}
