package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.ModSettings;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.util.ClanService;
import ru.admincontrol.util.CoinLedger;
import ru.admincontrol.util.Economy;

import java.util.List;

public final class CoinsFactory implements ExtendedScreenHandlerFactory {
	@Override
	public Text getDisplayName() {
		return Text.literal("Счёт World coin");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity viewer) {
		ServerPlayerEntity player = viewer instanceof ServerPlayerEntity p ? p : null;
		return new CoinsScreenHandler(syncId, snapshot(player), CoinLedger.transferTargets(player), ClanService.names());
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
		int[] data = snapshot(opener);
		for (int v : data) {
			buf.writeVarInt(v);
		}
		writeNames(buf, CoinLedger.transferTargets(opener));
		writeNames(buf, ClanService.names());
	}

	static void writeNames(PacketByteBuf buf, List<String> names) {
		int n = names == null ? 0 : names.size();
		buf.writeVarInt(n);
		if (names != null) {
			for (String name : names) {
				buf.writeString(name == null ? "" : name, 16);
			}
		}
	}

	static int[] snapshot(ServerPlayerEntity player) {
		ModSettings s = DataManager.get() == null ? new ModSettings() : DataManager.get().settings();
		int warns = 0;
		int earned = 0;
		int spent = 0;
		int casinoSpent = 0;
		int casinoWon = 0;
		if (player != null && DataManager.get() != null) {
			PlayerRecord record = DataManager.get().getOrCreate(player);
			warns = record.activeWarns;
			earned = CoinLedger.earned(record);
			spent = CoinLedger.spent(record);
			casinoSpent = Math.max(0, record.casinoSpent);
			casinoWon = Math.max(0, record.casinoWon);
		}
		return new int[] {
				Economy.balance(player),
				s.warnBuyEnabled ? 1 : 0,
				Math.max(1, s.warnBuyPrice),
				Math.max(0, warns),
				s.playerTransfers ? 1 : 0,
				Math.max(0, earned),
				Math.max(0, spent),
				casinoSpent,
				casinoWon
		};
	}
}
