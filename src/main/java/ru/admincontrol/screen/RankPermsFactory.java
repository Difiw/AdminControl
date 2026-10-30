package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.CustomRank;
import ru.admincontrol.util.CustomRanks;
import ru.admincontrol.util.RankPerms;

import java.util.ArrayList;
import java.util.List;

public final class RankPermsFactory implements ExtendedScreenHandlerFactory {
	private final int rank;

	public RankPermsFactory(int rank) {
		this.rank = rank;
	}

	@Override
	public Text getDisplayName() {
		return Text.literal(this.rank < 0 ? "Уровни админов"
				: "Ур. " + AdminRanks.display(this.rank) + "  " + AdminRanks.title(this.rank));
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity viewer) {
		return new RankPermsScreenHandler(syncId, this.rank, rows());
	}

	/** Список уровней (штатные и созданные в /aroles по возрастанию) или права одного уровня. */
	private List<RankPerms.Row> rows() {
		if (this.rank >= 0) {
			return RankPerms.rowsFor(this.rank);
		}
		List<Integer> levels = new ArrayList<>();
		for (int level : RankPerms.LEVELS) {
			levels.add(level);
		}
		for (CustomRank custom : CustomRanks.list()) {
			levels.add(custom.level);
		}
		levels.sort(Integer::compare);
		List<RankPerms.Row> out = new ArrayList<>();
		for (int level : levels) {
			out.add(new RankPerms.Row(String.valueOf(level), RankPermsScreenHandler.rankButton(level), false, false));
		}
		return out;
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
		buf.writeVarInt(this.rank);
		List<RankPerms.Row> rows = rows();
		buf.writeVarInt(rows.size());
		for (RankPerms.Row row : rows) {
			buf.writeString(row.id, 32);
			buf.writeString(row.label, 64);
			buf.writeBoolean(row.on);
			buf.writeBoolean(row.pair);
		}
	}
}
