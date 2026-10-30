package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.util.CoinLedger;
import ru.admincontrol.util.Economy;

import java.util.List;

public final class CoinsTransferFactory implements ExtendedScreenHandlerFactory {
	@Override
	public Text getDisplayName() {
		return Text.literal("Перевод World coin");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity viewer) {
		ServerPlayerEntity player = viewer instanceof ServerPlayerEntity serverPlayer ? serverPlayer : null;
		return new CoinsTransferScreenHandler(syncId, CoinLedger.transferTargets(player), Economy.balance(player), 0, -1);
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
		List<String> nicks = CoinLedger.transferTargets(opener);
		buf.writeVarInt(nicks.size());
		for (String nick : nicks) {
			buf.writeString(nick == null ? "" : nick, 16);
		}
		buf.writeVarInt(Economy.balance(opener));
	}
}
