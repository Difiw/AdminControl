package ru.admincontrol.lumira;

import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.cw.CwBuilderWorld;
import ru.admincontrol.cw.CwStoryRules;
import ru.admincontrol.data.AdminRanks;

/**
 * Где показывается магический HUD и сюжетная панель «Лиры/Мирты».
 * <ul>
 *   <li>в сюжетных измерениях (личных и тестовых) — у всех, кто там находится;</li>
 *   <li>в строительном измерении — только у разработчиков (чтобы строитель сразу видел итоговый интерфейс);
 *   приглашённые на стройку остаются со стандартным HUD и обычной панелью;</li>
 *   <li>на основном сервере и в ивентах — никогда.</li>
 * </ul>
 */
public final class LumiraMode {
	private LumiraMode() {
	}

	public static boolean hud(ServerPlayerEntity player) {
		if (player == null) {
			return false;
		}
		if (CwStoryRules.inside(player)) {
			return true;
		}
		return CwBuilderWorld.inside(player) && CommandUtil.hasRank(player.getCommandSource(), AdminRanks.DEV);
	}
}
