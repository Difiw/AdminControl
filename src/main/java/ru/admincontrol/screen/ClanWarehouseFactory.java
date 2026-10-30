package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.data.ClanRecord;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.util.ClanLog;
import ru.admincontrol.util.ClanService;
import ru.admincontrol.util.PlayFeatures;
import ru.admincontrol.util.StackIo;

public final class ClanWarehouseFactory implements ExtendedScreenHandlerFactory {
	private final ClanRecord inspect;
	private final boolean viewOnly;

	public ClanWarehouseFactory() {
		this(null, false);
	}

	public ClanWarehouseFactory(ClanRecord inspect) {
		this(inspect, inspect != null);
	}

	public ClanWarehouseFactory(ClanRecord inspect, boolean viewOnly) {
		this.inspect = inspect;
		this.viewOnly = viewOnly;
	}

	@Override
	public Text getDisplayName() {
		if (this.inspect != null && this.inspect.name != null && !this.inspect.name.isBlank()) {
			return Text.literal("Склад клана «" + this.inspect.name + "»");
		}
		return Text.literal("Склад клана");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity viewer) {
		ServerPlayerEntity player = viewer instanceof ServerPlayerEntity p ? p : null;
		ClanRecord clan = this.inspect != null ? this.inspect : ClanService.of(player);
		int rows = rowsOf(clan);
		SimpleInventory chest = new SimpleInventory(rows * 9);
		if (clan != null) {
			StackIo.fill(chest, clan.warehouse, rows * 9);
		}
		boolean viewOnly = this.viewOnly;
		ClanRecord target = clan;
		String before = String.join(" | ", ClanService.warehouseItemLines(clan));
		ServerPlayerEntity actor = player;
		return new ExtraChestScreenHandler(syncId, inventory, chest, rows, canPut(clan, player), canTake(clan, player), () -> {
			if (viewOnly || target == null || DataManager.get() == null) {
				return;
			}
			target.warehouse = StackIo.writeInv(chest, rows * 9);
			DataManager.get().saveAll();
			String after = String.join(" | ", ClanService.warehouseItemLines(target));
			if (!before.equals(after)) {
				ClanLog.action(target, actor, "склад: было [" + (before.isBlank() ? "пусто" : before)
						+ "] стало [" + (after.isBlank() ? "пусто" : after) + "]");
			}
		});
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
		ClanRecord clan = this.inspect != null ? this.inspect : ClanService.of(opener);
		buf.writeVarInt(rowsOf(clan));
		buf.writeBoolean(this.viewOnly);
		buf.writeBoolean(canPut(clan, opener));
		buf.writeBoolean(canTake(clan, opener));
	}

	/** Грабёж в битве кланов (чужой склад без viewOnly) — полный доступ; свой склад — по доступам уровня. */
	private boolean canPut(ClanRecord clan, ServerPlayerEntity player) {
		return !this.viewOnly && (this.inspect != null || ClanService.hasFlag(clan, player, ClanRecord.WH_PUT));
	}

	private boolean canTake(ClanRecord clan, ServerPlayerEntity player) {
		return !this.viewOnly && (this.inspect != null || ClanService.hasFlag(clan, player, ClanRecord.WH_TAKE));
	}

	private static int rowsOf(ClanRecord clan) {
		int level = clan == null ? 0 : Math.max(0, clan.warehouseLevel);
		int slots = level <= 0 ? 9 : PlayFeatures.s().clanWarehouseSlots(level);
		return Math.max(1, Math.min(6, (slots + 8) / 9));
	}
}
