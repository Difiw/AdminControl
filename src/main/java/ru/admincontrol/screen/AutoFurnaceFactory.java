package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.util.FurnaceService;

public final class AutoFurnaceFactory implements ExtendedScreenHandlerFactory {
	private final ServerPlayerEntity owner;

	public AutoFurnaceFactory(ServerPlayerEntity owner) {
		this.owner = owner;
	}

	@Override
	public Text getDisplayName() {
		return Text.literal("Автопечь ур. " + FurnaceService.level(this.owner) + "  •  "
				+ FurnaceService.s().furnaceSecondsFor(FurnaceService.level(this.owner)) + " сек/предмет");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity viewer) {
		ServerPlayerEntity player = this.owner;
		PropertyDelegate props = new PropertyDelegate() {
			@Override
			public int get(int index) {
				return switch (index) {
					case 0, 1, 2 -> FurnaceService.progress(player, index);
					case 3 -> FurnaceService.intervalTicks(player);
					default -> FurnaceService.count(player);
				};
			}

			@Override
			public void set(int index, int value) {
			}

			@Override
			public int size() {
				return AutoFurnaceScreenHandler.PROPS;
			}
		};
		return new AutoFurnaceScreenHandler(syncId, inventory, FurnaceService.inventory(player), props,
				FurnaceService.count(player));
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
		buf.writeVarInt(FurnaceService.count(this.owner));
	}
}
