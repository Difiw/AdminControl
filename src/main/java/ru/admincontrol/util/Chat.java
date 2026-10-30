package ru.admincontrol.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.admincontrol.data.DataManager;

/**
 * Цвета чата: синий / зелёный / красный / жёлтый (классическая палитра Minecraft).
 */
public final class Chat {
	private Chat() {
	}

	public static MutableText blue(String message) {
		return Text.literal(message).formatted(Formatting.BLUE);
	}

	public static MutableText green(String message) {
		return Text.literal(message).formatted(Formatting.GREEN);
	}

	public static MutableText red(String message) {
		return Text.literal(message).formatted(Formatting.RED);
	}

	public static MutableText brightRed(String message) {
		return Text.literal(message).formatted(Formatting.RED, Formatting.BOLD);
	}

	public static MutableText purple(String message) {
		return Text.literal(message).formatted(Formatting.LIGHT_PURPLE);
	}

	public static MutableText yellow(String message) {
		return Text.literal(message).formatted(Formatting.YELLOW);
	}

	public static MutableText gray(String message) {
		return Text.literal(message).formatted(Formatting.GRAY);
	}

	public static MutableText white(String message) {
		return Text.literal(message).formatted(Formatting.WHITE);
	}

	public static MutableText gold(String message) {
		return Text.literal(message).formatted(Formatting.GOLD);
	}

	/** Светло-бежевый — личные сообщения администратора. */
	public static MutableText beige(String message) {
		return Text.literal(message).styled(style -> style.withColor(0xF5F5DC));
	}

	public static MutableText color(String message, int rgb) {
		return Text.literal(message).styled(style -> style.withColor(rgb));
	}

	public static int rankColor(int level) {
		return NickColors.of(level);
	}

	public static MutableText nick(String name, int level) {
		return color(name, rankColor(level));
	}

	public static Text chatMessage(ServerPlayerEntity player, String message) {
		if (player == null) {
			return Text.literal(message == null ? "" : message);
		}
		DataManager data = DataManager.get();
		int level = data == null ? 0 : data.getAdminLevel(player);
		String name = player.getGameProfile().getName();
		NpcPlayerEntity npc = NpcService.controlledNpc(player);
		if (npc != null) {
			level = 0;
			name = npc.getGameProfile().getName();
		}
		ru.admincontrol.data.CustomRank custom = CustomRanks.byLevel(level);
		if (custom != null) {
			return CustomRanks.chatLine(custom, name, message);
		}
		MutableText line = Text.empty();
		if (level >= ru.admincontrol.data.AdminRanks.MOD) {
			String tag = ru.admincontrol.data.AdminRanks.chatTag(level);
			line.append(color("[" + tag + "] ", rankColor(level)));
		}
		line.append(color("<" + name + ">", rankColor(level)));
		line.append(Text.literal(" " + (message == null ? "" : message)).formatted(Formatting.WHITE));
		return line;
	}

	public static void send(ServerPlayerEntity player, Text text) {
		if (player != null) {
			player.sendMessage(text, false);
		}
	}

	public static void broadcast(MinecraftServer server, Text text) {
		if (server == null) {
			return;
		}
		server.getPlayerManager().broadcast(text, false);
	}

	public static String daysWord(int days) {
		int n = Math.abs(days) % 100;
		int n1 = n % 10;
		if (n > 10 && n < 20) {
			return "дней";
		}
		if (n1 == 1) {
			return "день";
		}
		if (n1 >= 2 && n1 <= 4) {
			return "дня";
		}
		return "дней";
	}

	public static String minutesWord(int minutes) {
		int n = Math.abs(minutes) % 100;
		int n1 = n % 10;
		if (n > 10 && n < 20) {
			return "минут";
		}
		if (n1 == 1) {
			return "минуту";
		}
		if (n1 >= 2 && n1 <= 4) {
			return "минуты";
		}
		return "минут";
	}
}
