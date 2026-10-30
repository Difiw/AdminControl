package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.HomeProtection;
import ru.admincontrol.util.SpawnService;

public class HomeScreenHandler extends ScreenHandler {
	public static final int SIZE_MIN = 2;
	public static final int SIZE_MAX = 150;
	public static final int GO = 0;
	public static final int DELETE = 1;
	public static final int TOGGLE_PRIV = 2;
	public static final int SIZE_M = 3;
	public static final int SIZE_P = 4;
	public static final int SIZE_M10 = 5;
	public static final int SIZE_P10 = 6;
	public static final int SIZE_APPLY = 7;

	private final PropertyDelegate props = new ArrayPropertyDelegate(4);
	public final boolean hasHome;
	public final boolean priv;

	public static HomeScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		return new HomeScreenHandler(syncId, buf.readBoolean(), buf.readBoolean(), buf.readInt(), buf.readInt());
	}

	public HomeScreenHandler(int syncId, boolean hasHome, boolean priv, int size) {
		this(syncId, hasHome, priv, size, Math.max(3, size));
	}

	public HomeScreenHandler(int syncId, boolean hasHome, boolean priv, int size, int maxSize) {
		super(ModScreenHandlers.HOME, syncId);
		this.hasHome = hasHome;
		this.priv = priv;
		this.props.set(0, clamp(size));
		this.props.set(1, Math.max(SIZE_MIN, Math.min(SIZE_MAX, maxSize)));
		this.props.set(2, hasHome ? 1 : 0);
		this.props.set(3, priv ? 1 : 0);
		this.addProperties(this.props);
	}

	public int size() {
		return this.props.get(0);
	}

	public int maxSize() {
		return this.props.get(1);
	}

	private static int clamp(int size) {
		return Math.max(SIZE_MIN, Math.min(SIZE_MAX, size <= 0 ? 5 : size));
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!(player instanceof ServerPlayerEntity serverPlayer) || DataManager.get() == null) {
			return false;
		}
		PlayerRecord record = DataManager.get().getOrCreate(serverPlayer);
		if (id == GO) {
			if (record.home == null) {
				Chat.send(serverPlayer, Chat.red("У вас нет метки дома."));
				return true;
			}
			serverPlayer.closeHandledScreen();
			ru.admincontrol.util.ChunkPrewarm.holdPoint(serverPlayer.getServer(), record.home);
			ru.admincontrol.util.TeleportWarmup.start(serverPlayer, "дом", () -> {
				PlayerRecord now = DataManager.get().getOrCreate(serverPlayer);
				if (now.home == null) {
					Chat.send(serverPlayer, Chat.red("У вас нет метки дома."));
					return;
				}
				SpawnService.teleportHome(serverPlayer, now.home);
				Chat.send(serverPlayer, Chat.green("Вы телепортированы домой."));
			});
			return true;
		}
		if (id == DELETE) {
			record.home = null;
			record.homePrivate = false;
			DataManager.get().saveAll();
			HomeProtection.refresh();
			ru.admincontrol.util.AdminNotify.playerAction(serverPlayer, ru.admincontrol.data.ActionKind.HOME, "удалил метку дома");
			Chat.send(serverPlayer, Chat.green("Метка дома удалена."));
			serverPlayer.closeHandledScreen();
			return true;
		}
		if (id == TOGGLE_PRIV) {
			if (!record.homePrivateOwned) {
				Chat.send(serverPlayer, Chat.red("Приват дома покупается в /shop."));
				return true;
			}
			if (record.home == null) {
				Chat.send(serverPlayer, Chat.red("Сначала поставьте дом: /addhome"));
				return true;
			}
			record.homePrivate = !record.homePrivate;
			if (record.homeSize < SIZE_MIN || record.homeSize > SIZE_MAX) {
				record.homeSize = 3;
			}
			DataManager.get().saveAll();
			HomeProtection.refresh();
			Chat.send(serverPlayer, Chat.green("Приват дома: " + (record.homePrivate ? "включён" : "выключен") + "."));
			serverPlayer.closeHandledScreen();
			return true;
		}
		if (id == SIZE_M || id == SIZE_P || id == SIZE_M10 || id == SIZE_P10) {
			int delta = switch (id) {
				case SIZE_M -> -1;
				case SIZE_P -> 1;
				case SIZE_M10 -> -10;
				default -> 10;
			};
			int next = clamp(size() + delta);
			int owned = Math.max(3, record.maxHomeSize);
			if (next > owned) {
				Chat.send(serverPlayer, Chat.red("Размер " + next + "³ покупается в /shop. Доступно до " + owned + "³."));
				next = Math.min(next, owned);
			}
			this.props.set(0, Math.max(SIZE_MIN, Math.min(owned, next)));
			return true;
		}
		if (id == SIZE_APPLY) {
			int next = size();
			int owned = Math.max(3, record.maxHomeSize);
			if (next > 3 && next > owned) {
				Chat.send(serverPlayer, Chat.red("Размер " + next + "³ покупается в /shop. Доступно до " + owned + "³."));
				return true;
			}
			if (!record.homePrivateOwned) {
				Chat.send(serverPlayer, Chat.red("Приват дома покупается в /shop."));
				return true;
			}
			record.homeSize = Math.max(SIZE_MIN, Math.min(SIZE_MAX, next));
			record.homePrivate = true;
			DataManager.get().saveAll();
			HomeProtection.refresh();
			Chat.send(serverPlayer, Chat.green("Размер привата: " + record.homeSize + "×" + record.homeSize + "×" + record.homeSize));
			serverPlayer.closeHandledScreen();
			return true;
		}
		return false;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return true;
	}
}
