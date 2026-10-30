package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.util.CasinoService;
import ru.admincontrol.util.Economy;

public final class CasinoPlayFactory implements ExtendedScreenHandlerFactory {
	private final int mode;
	private final int bet;

	public CasinoPlayFactory() {
		this(0, -1);
	}

	public CasinoPlayFactory(int mode, int bet) {
		this.mode = mode == 1 ? 1 : 0;
		this.bet = bet;
	}

	@Override
	public Text getDisplayName() {
		return Text.literal("Казино");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity viewer) {
		return new CasinoPlayScreenHandler(syncId, playerInventory, snapshot(viewer instanceof ServerPlayerEntity player ? player : null));
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
		int[] data = snapshot(opener);
		for (int value : data) {
			buf.writeVarInt(value);
		}
	}

	private int[] snapshot(ServerPlayerEntity player) {
		PlayerRecord record = player == null || DataManager.get() == null ? null : DataManager.get().progress(player);
		int coins = Economy.balance(player);
		int luckTenths = record == null ? 0 : (int) Math.round(record.luck() * 10.0);
		int min = CasinoService.minBet();
		int startBet = this.bet >= min ? this.bet : Math.max(min, coins);
		return new int[] {
				coins,
				luckTenths,
				startBet,
				-1,
				0,
				0,
				CasinoService.enabled() ? 1 : 0,
				record != null && record.casinoBanned ? 1 : 0,
				CasinoService.minTenths(),
				CasinoService.maxTenths(),
				this.mode,
				-1
		};
	}
}
