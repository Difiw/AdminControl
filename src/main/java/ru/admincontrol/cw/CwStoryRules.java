package ru.admincontrol.cw;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

import java.util.Set;

/** Central, server-authoritative restrictions for personal/test story dimensions. */
public final class CwStoryRules {
	private static final java.util.Map<java.util.UUID,Long> LAST_NOTICE=new java.util.concurrent.ConcurrentHashMap<>();
	private static final Set<String> BLOCKED_COMMANDS = Set.of(
			"skills", "quests", "training", "off", "event", "allevent", "tevent",
			"gevent", "cevent", "eventkick", "ekick", "eventstop", "box", "unbox",
			"agm", "unagm", "banagm", "unbanagm", "time", "weather");
	private CwStoryRules() {}

	public static boolean inside(World world) {
		if (world == null) return false;
		return isStoryWorldId(world.getRegistryKey().getValue().toString());
	}

	public static boolean isStoryWorldId(String id) {
		return id != null && id.matches("admin_control:cw_story_(?:[0-9]|1[0-5])");
	}

	public static boolean inside(ServerPlayerEntity player) {
		return player != null && inside(player.getWorld());
	}

	public static boolean blockedCommand(String name) {
		return name != null && BLOCKED_COMMANDS.contains(name.toLowerCase(java.util.Locale.ROOT));
	}

	/** Интерфейсы обычного сервера (навыки, рюкзак, верстак, печь) закрыты и в сюжетных мирах, и на стройке карты. */
	public static boolean denyUi(ServerPlayerEntity player, String feature) {
		if (!inside(player) && !CwBuilderWorld.inside(player)) return false;
		return deny(player);
	}

	public static boolean denyFeature(ServerPlayerEntity player, String feature) {
		if (!inside(player)) return false;
		return deny(player);
	}

	private static boolean deny(ServerPlayerEntity player) {
		long now=System.currentTimeMillis(),last=LAST_NOTICE.getOrDefault(player.getUuid(),0L);
		if(now-last>=1500L){LAST_NOTICE.put(player.getUuid(),now);player.sendMessage(net.minecraft.text.Text.literal("Это действие здесь недоступно"), true);}
		player.closeHandledScreen();
		return true;
	}
}
