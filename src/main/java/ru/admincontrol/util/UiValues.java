package ru.admincontrol.util;

import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.screen.AuctionOfferScreenHandler;
import ru.admincontrol.screen.CasinoItemScreenHandler;
import ru.admincontrol.screen.CoinsScreenHandler;
import ru.admincontrol.screen.CoinsTransferScreenHandler;
import ru.admincontrol.screen.SellScreenHandler;

public final class UiValues {
	private UiValues() {
	}

	public static void apply(ServerPlayerEntity player, String kind, String value) {
		if (player == null || kind == null) {
			return;
		}
		var handler = player.currentScreenHandler;
		if ("coins".equals(kind) && handler instanceof CoinsScreenHandler coins) {
			coins.setAmountFromText(player, value);
		} else if ("coins_transfer".equals(kind) && handler instanceof CoinsTransferScreenHandler coins) {
			coins.setAmountFromText(player, value);
		} else if ("sell".equals(kind) && handler instanceof SellScreenHandler sell) {
			sell.setCountFromText(player, value);
		} else if ("auction_price".equals(kind) && handler instanceof AuctionOfferScreenHandler offer) {
			offer.setPriceFromText(player, value);
		} else if ("auction_qty".equals(kind) && handler instanceof AuctionOfferScreenHandler offer) {
			offer.setQtyFromText(player, value);
		} else if (kind != null && kind.startsWith("nick_color_")
				&& handler instanceof ru.admincontrol.screen.ServerPanelScreenHandler) {
			if (DataManager.get() == null
					|| DataManager.get().getAdminLevel(player) < ru.admincontrol.data.AdminRanks.DEV) {
				return;
			}
			((ru.admincontrol.screen.ServerPanelScreenHandler) handler).applyNickColor(player, kind, value);
		} else if (kind != null && kind.startsWith("rank_tag_")
				&& handler instanceof ru.admincontrol.screen.ServerPanelScreenHandler) {
			if (DataManager.get() == null
					|| DataManager.get().getAdminLevel(player) < ru.admincontrol.data.AdminRanks.DEV) {
				return;
			}
			((ru.admincontrol.screen.ServerPanelScreenHandler) handler).applyRankTag(player, kind, value);
		}
	}

	public static int parseInt(String raw, int fallback) {
		if (raw == null || raw.isBlank()) {
			return fallback;
		}
		String digits = raw.replace(" ", "").replace(".", "").replace(",", "");
		try {
			return Integer.parseInt(digits);
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}
}
