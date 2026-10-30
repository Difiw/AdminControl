package ru.admincontrol.lumira.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.admincontrol.AdminControlMod;

/** Типы окон рунного верстака и рунной наковальни. */
public final class LumiraScreens {
	public static ScreenHandlerType<RuneWorkbenchHandler> WORKBENCH;
	public static ScreenHandlerType<RuneAnvilHandler> ANVIL;

	private LumiraScreens() {
	}

	public static void register() {
		WORKBENCH = Registry.register(Registries.SCREEN_HANDLER, new Identifier(AdminControlMod.MOD_ID, "rune_workbench"),
				new ExtendedScreenHandlerType<>((syncId, inv, buf) -> new RuneWorkbenchHandler(syncId, inv)));
		ANVIL = Registry.register(Registries.SCREEN_HANDLER, new Identifier(AdminControlMod.MOD_ID, "rune_anvil"),
				new ExtendedScreenHandlerType<>((syncId, inv, buf) -> new RuneAnvilHandler(syncId, inv)));
	}

	public interface Maker {
		ScreenHandler make(int syncId, PlayerInventory inventory, PlayerEntity player);
	}

	/** Фабрика окна: данные при открытии не нужны, клиент строит обработчик сам. */
	public static ExtendedScreenHandlerFactory factory(Text title, Maker maker) {
		return new ExtendedScreenHandlerFactory() {
			@Override
			public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
			}

			@Override
			public Text getDisplayName() {
				return title;
			}

			@Override
			public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity player) {
				return maker.make(syncId, inventory, player);
			}
		};
	}
}
