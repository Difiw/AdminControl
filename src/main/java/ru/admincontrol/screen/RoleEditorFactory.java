package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.data.CustomRank;
import ru.admincontrol.util.CustomRanks;

import java.util.ArrayList;
import java.util.List;

public final class RoleEditorFactory implements ExtendedScreenHandlerFactory {
	private final String selected;

	public RoleEditorFactory(String selected) {
		this.selected = selected == null ? "" : selected;
	}

	@Override
	public Text getDisplayName() {
		return Text.literal("Уровни администрации");
	}

	private static List<RoleEditorScreenHandler.Entry> entries(ServerPlayerEntity viewer) {
		int editor = viewer == null ? 0 : CommandUtil.sourceRank(viewer.getCommandSource());
		List<RoleEditorScreenHandler.Entry> out = new ArrayList<>();
		for (CustomRank r : CustomRanks.sorted()) {
			out.add(new RoleEditorScreenHandler.Entry(nz(r.key), nz(r.access), nz(r.title), nz(r.role),
					r.format == null || r.format.isBlank() ? CustomRanks.DEFAULT_FORMAT : r.format,
					CustomRanks.color(r.level, CustomRanks.DEFAULT_COLOR), CustomRanks.holders(r.level),
					r.commands == null ? 0 : r.commands.size(), CustomRanks.canEdit(editor, r)));
		}
		return out;
	}

	private static String nz(String value) {
		return value == null ? "" : value;
	}

	private String selectedKey() {
		return CustomRanks.byKey(this.selected) == null ? "" : this.selected;
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity viewer) {
		ServerPlayerEntity player = viewer instanceof ServerPlayerEntity sp ? sp : null;
		return new RoleEditorScreenHandler(syncId, selectedKey(), maxAccess(player), entries(player));
	}

	private static String maxAccess(ServerPlayerEntity player) {
		int editor = player == null ? 0 : CommandUtil.sourceRank(player.getCommandSource());
		int max = CustomRanks.maxTenths(editor);
		return max <= 0 ? "" : CustomRanks.formatAccess(max);
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
		buf.writeString(selectedKey(), 16);
		buf.writeString(maxAccess(opener), 8);
		List<RoleEditorScreenHandler.Entry> list = entries(opener);
		buf.writeVarInt(list.size());
		for (RoleEditorScreenHandler.Entry e : list) {
			buf.writeString(e.key(), 16);
			buf.writeString(e.access(), 8);
			buf.writeString(e.title(), 64);
			buf.writeString(e.role(), 64);
			buf.writeString(e.format(), 128);
			buf.writeInt(e.color());
			buf.writeVarInt(e.holders());
			buf.writeVarInt(e.commands());
			buf.writeBoolean(e.editable());
		}
	}
}
