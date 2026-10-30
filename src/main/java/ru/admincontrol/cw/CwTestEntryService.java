package ru.admincontrol.cw;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.net.ModPackets;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Short-lived launch offers. A client packet alone can never create or enter an instance. */
public final class CwTestEntryService {
	private static final long TTL_MS = 30_000L;
	private record Offer(UUID nonce, long expiresAt, String storyId, String templateId, int templateVersion) {}
	private static final Map<UUID, Offer> OFFERS = new ConcurrentHashMap<>();
	private CwTestEntryService() {}

	public static String offer(ServerPlayerEntity player, String storyId, String templateId, int templateVersion) {
		if (!CommandUtil.hasRank(player.getCommandSource(), AdminRanks.GA)) return "Требуется ранг ГА или выше.";
		if (CwStoryRules.inside(player)) return CwStoryService.inTest(player)
				? "Тестовый сюжет уже запущен. Сначала завершите его."
				: "Вы уже проходите официальный сюжет. Сначала покиньте его командой /storyexit.";
		String clientProblem = CwClientGate.problem(player); if (clientProblem != null) return clientProblem;
		if (!ServerPlayNetworking.canSend(player, ModPackets.CW_TEST_OFFER)) return "Клиент не поддерживает окно тестового запуска.";
		UUID nonce = UUID.randomUUID();
		OFFERS.put(player.getUuid(), new Offer(nonce, System.currentTimeMillis() + TTL_MS, safe(storyId), safe(templateId), Math.max(1, templateVersion)));
		var buf = PacketByteBufs.create();
		buf.writeUuid(nonce);
		buf.writeString("Луи", 64);
		buf.writeString(CwStoryService.hasSavedTest(player.getUuid())?"Рад снова тебя видеть. Продолжим с того места, где ты остановился?":CwStoryGraph.louisOpening(), 512);
		// Technical notes about the isolated test copy are developer information: log only, never shown in the RP dialogue.
		ru.admincontrol.AdminControlMod.LOGGER.debug("CW test offer for {}: map is copied into the player's isolated test world", player.getUuidAsString());
		buf.writeString("", 256);
		buf.writeString("", 256);
		buf.writeBoolean(CwStoryService.hasSavedTest(player.getUuid()));
		buf.writeBoolean(true);
		ServerPlayNetworking.send(player, ModPackets.CW_TEST_OFFER, buf);
		return null;
	}
	public static String finish(ServerPlayerEntity player) {
		if (player == null || !CwStoryService.inTest(player)) return "Активный тест не найден.";
		CwStoryService.exitTest(player);
		return CwStoryWorldService.leaveTest(player);
	}

	public static void answer(ServerPlayerEntity player, UUID nonce, int action) {
		Offer offer = OFFERS.remove(player.getUuid());
		if (offer == null || !offer.nonce.equals(nonce) || offer.expiresAt < System.currentTimeMillis()) {
			CwStoryService.clearPendingTest(player);
			player.sendMessage(Text.literal("[CW] Запрос тестового запуска отсутствует или устарел."), true);
			return;
		}
		if (action == 0) { CwStoryService.clearPendingTest(player); player.sendMessage(Text.literal("[CW] Тестовый запуск отменён."), true); return; }
		if (action != 1 && action != 2) { CwStoryService.clearPendingTest(player); player.sendMessage(Text.literal("[CW] Неизвестное действие тестового запуска."), true); return; }
		if (!CommandUtil.hasRank(player.getCommandSource(), AdminRanks.GA)) {
			CwStoryService.clearPendingTest(player); player.sendMessage(Text.literal("[CW] Недостаточно прав для тестового запуска."), true); return;
		}
		if (CwStoryRules.inside(player)) {
			CwStoryService.clearPendingTest(player);
			player.sendMessage(Text.literal(CwStoryService.inTest(player)
					? "[CW] Тестовый сюжет уже запущен."
					: "[CW] Вы уже в официальном сюжете. Сначала выйдите командой /storyexit."), false);
			return;
		}
		String error = CwStoryWorldService.enterTest(player, offer.storyId, offer.templateId, offer.templateVersion, action == 1);
		if (error == null) {
			CwStoryService.enterTest(player, player.getUuid(), action == 1);
			player.sendMessage(Text.literal(action == 1 ? "[CW] Изолированный тест начат заново." : "[CW] Изолированный тест продолжен."), false);
		} else { CwStoryService.clearPendingTest(player); player.sendMessage(Text.literal("[CW] " + error), false); }
	}

	public static void clear(ServerPlayerEntity player) { if (player != null) OFFERS.remove(player.getUuid()); }
	private static String safe(String value) { return value == null || value.isBlank() ? "default" : value.trim(); }
}
