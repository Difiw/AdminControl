package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.command.CommandSync;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.RankPerms;

import java.util.ArrayList;
import java.util.List;

public class RankPermsScreenHandler extends ScreenHandler {
	public static final int BACK = 100;
	public static final int RESET = 101;
	public static final int BAN_M = 200;
	public static final int BAN_P = 201;
	public static final int JAIL_M = 202;
	public static final int JAIL_P = 203;
	public static final int TOGGLE_BASE = 300;

	private final int rank;
	private final List<RankPerms.Row> rows = new ArrayList<>();

	public static RankPermsScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		int rank = buf.readVarInt();
		int n = buf.readVarInt();
		List<RankPerms.Row> rows = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			rows.add(new RankPerms.Row(buf.readString(), buf.readString(), buf.readBoolean(), buf.readBoolean()));
		}
		return new RankPermsScreenHandler(syncId, rank, rows);
	}

	public RankPermsScreenHandler(int syncId, int rank, List<RankPerms.Row> rows) {
		super(ModScreenHandlers.RANK_PERMS, syncId);
		this.rank = rank;
		if (rows != null) {
			this.rows.addAll(rows);
		}
	}

	public int rank() {
		return this.rank;
	}

	public List<RankPerms.Row> rows() {
		return this.rows;
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			return false;
		}
		if (!CommandUtil.canUse(serverPlayer.getCommandSource(), "aranks")
				&& !CommandUtil.canUse(serverPlayer.getCommandSource(), "dserver")) {
			return false;
		}
		if (this.rank < 0) {
			if (id == RESET) {
				RankPerms.resetAll();
				CommandSync.refreshAll(serverPlayer.getServer());
				Chat.send(serverPlayer, Chat.green("Права уровней сброшены к стандартным."));
				ru.admincontrol.util.ScreenNav.replace(serverPlayer, new RankPermsFactory(-1));
				return true;
			}
			if (id >= 0 && id < this.rows.size()) {
				int level;
				try {
					level = Integer.parseInt(this.rows.get(id).id);
				} catch (NumberFormatException e) {
					return false;
				}
				ru.admincontrol.util.ScreenNav.open(serverPlayer, new RankPermsFactory(level));
				return true;
			}
			return false;
		}
		if (id == BACK) {
			ru.admincontrol.util.ScreenNav.back(serverPlayer);
			return true;
		}
		ru.admincontrol.data.CustomRank custom = ru.admincontrol.util.CustomRanks.byLevel(this.rank);
		int editor = CommandUtil.sourceRank(serverPlayer.getCommandSource());
		if (custom != null && !ru.admincontrol.util.CustomRanks.canEdit(editor, custom)) {
			Chat.send(serverPlayer, Chat.red("Права этого уровня может менять только ГА или разработчик выше его по уровню."));
			return true;
		}
		if (custom != null && id >= TOGGLE_BASE && id < TOGGLE_BASE + this.rows.size()) {
			RankPerms.Row row = this.rows.get(id - TOGGLE_BASE);
			if (!row.id.startsWith("_") && !row.on && !RankPerms.allowed(editor, row.id)) {
				Chat.send(serverPlayer, Chat.red("Нельзя выдать уровню команду, которой нет у вас самих."));
				return true;
			}
		}
		if (id == BAN_M || id == BAN_P) {
			RankPerms.shiftBanDays(this.rank, id == BAN_M ? -1 : 1);
		} else if (id == JAIL_M || id == JAIL_P) {
			RankPerms.shiftJailMinutes(this.rank, id == JAIL_M ? -10 : 10);
		} else if (id >= TOGGLE_BASE && id < TOGGLE_BASE + this.rows.size()) {
			RankPerms.Row row = this.rows.get(id - TOGGLE_BASE);
			if ("_punish".equals(row.id)) {
				RankPerms.togglePunishStaff(this.rank);
			} else if (!row.id.startsWith("_")) {
				RankPerms.toggleCommand(this.rank, row.id);
				CommandSync.refreshAll(serverPlayer.getServer());
			}
		} else {
			return false;
		}
		ru.admincontrol.util.ScreenNav.replace(serverPlayer, new RankPermsFactory(this.rank));
		return true;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return true;
	}

	public static String rankButton(int level) {
		return AdminRanks.display(level) + "  " + AdminRanks.title(level);
	}
}
