package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.data.CustomRank;
import ru.admincontrol.util.Chat;
import ru.admincontrol.util.CustomRanks;
import ru.admincontrol.util.ScreenNav;

import java.util.ArrayList;
import java.util.List;

/** /aroles: список созданных уровней и форма выбранного (или нового) уровня. */
public class RoleEditorScreenHandler extends ScreenHandler {
	public static final int NEW = 1;
	public static final int DELETE = 2;
	public static final int RESET = 3;
	public static final int PERMS = 4;
	public static final int SELECT_BASE = 100;

	public record Entry(String key, String access, String title, String role, String format, int color,
			int holders, int commands, boolean editable) {
	}

	private final String selected;
	private final String maxAccess;
	private final List<Entry> entries = new ArrayList<>();

	public static RoleEditorScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		String selected = buf.readString();
		String maxAccess = buf.readString();
		int n = buf.readVarInt();
		List<Entry> entries = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			entries.add(new Entry(buf.readString(), buf.readString(), buf.readString(), buf.readString(),
					buf.readString(), buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
		}
		return new RoleEditorScreenHandler(syncId, selected, maxAccess, entries);
	}

	public RoleEditorScreenHandler(int syncId, String selected, String maxAccess, List<Entry> entries) {
		super(ModScreenHandlers.ROLE_EDITOR, syncId);
		this.selected = selected == null ? "" : selected;
		this.maxAccess = maxAccess == null ? "" : maxAccess;
		if (entries != null) {
			this.entries.addAll(entries);
		}
	}

	public String selected() {
		return this.selected;
	}

	public String maxAccess() {
		return this.maxAccess;
	}

	public List<Entry> entries() {
		return this.entries;
	}

	public Entry current() {
		for (Entry e : this.entries) {
			if (e.key().equals(this.selected)) {
				return e;
			}
		}
		return null;
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!(player instanceof ServerPlayerEntity serverPlayer)
				|| !CommandUtil.canUse(serverPlayer.getCommandSource(), "aroles")) {
			return false;
		}
		if (id == NEW) {
			ScreenNav.replace(serverPlayer, new RoleEditorFactory(""));
			return true;
		}
		if (id >= SELECT_BASE && id < SELECT_BASE + this.entries.size()) {
			ScreenNav.replace(serverPlayer, new RoleEditorFactory(this.entries.get(id - SELECT_BASE).key()));
			return true;
		}
		CustomRank rank = CustomRanks.byKey(this.selected);
		if (rank == null) {
			return false;
		}
		if (id == PERMS) {
			if (!CustomRanks.canEdit(CommandUtil.sourceRank(serverPlayer.getCommandSource()), rank)) {
				Chat.send(serverPlayer, Chat.red("Права этого уровня может менять только тот, кто выше его."));
				return true;
			}
			ScreenNav.open(serverPlayer, new RankPermsFactory(rank.level));
			return true;
		}
		if (id == RESET) {
			String error = CustomRanks.resetCommands(serverPlayer, rank.key);
			Chat.send(serverPlayer, error != null ? Chat.red(error)
					: Chat.green("Права уровня «" + rank.title + "» подобраны заново по уровню " + rank.access + "."));
			ScreenNav.replace(serverPlayer, new RoleEditorFactory(rank.key));
			return true;
		}
		if (id == DELETE) {
			String title = rank.title;
			String error = CustomRanks.delete(serverPlayer, rank.key);
			Chat.send(serverPlayer, error != null ? Chat.red(error) : Chat.green("Уровень «" + title + "» удалён."));
			ScreenNav.replace(serverPlayer, new RoleEditorFactory(error != null ? rank.key : ""));
			return true;
		}
		return false;
	}

	/** Сохранение формы (пакет ROLE_SAVE). */
	public static void save(ServerPlayerEntity player, String key, String title, String role, String format,
			String color, String access) {
		if (!(player.currentScreenHandler instanceof RoleEditorScreenHandler)
				|| !CommandUtil.canUse(player.getCommandSource(), "aroles")) {
			return;
		}
		boolean created = key == null || key.isBlank();
		String error = CustomRanks.save(player, key, title, role, format, color, access);
		if (error != null) {
			Chat.send(player, Chat.red(error));
			return;
		}
		Integer tenths = CustomRanks.parseAccess(access);
		CustomRank saved = tenths == null ? null : CustomRanks.byKey(CustomRanks.formatAccess(tenths));
		if (saved == null) {
			return;
		}
		Chat.send(player, Chat.green((created ? "Создан уровень «" : "Сохранён уровень «") + saved.title + "» ("
				+ saved.access + "). Выдать: /set_admin <ник> " + saved.key));
		ScreenNav.replace(player, new RoleEditorFactory(saved.key));
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
