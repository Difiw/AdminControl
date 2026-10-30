package ru.admincontrol.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.advancement.Advancement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.screen.AchievementsScreenHandler;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class Achievements {
	public static final Identifier DEATH_IN_POVERTY = new Identifier("admin_control", "death_in_poverty");
	public static final Identifier GAMBLER = new Identifier("admin_control", "gambler");
	public static final Identifier BODYBUILDER = new Identifier("admin_control", "bodybuilder");
	public static final Identifier LUCKY = new Identifier("admin_control", "lucky");
	public static final Identifier ENTERTAINMENT_FAN = new Identifier("admin_control", "entertainment_fan");

	private Achievements() {
	}

	public static void onCasinoPlayed(ServerPlayerEntity player, int bet, int payout) {
		if (player == null) {
			return;
		}
		grant(player, GAMBLER, "play");
		if (payout > bet) {
			grant(player, LUCKY, "win");
		}
		checkCasinoPoverty(player);
	}

	public static void onTrainingPrize(ServerPlayerEntity player) {
		grant(player, BODYBUILDER, "prize");
	}

	public static void onEventWin(ServerPlayerEntity player) {
		grant(player, ENTERTAINMENT_FAN, "win");
	}

	public static void checkCasinoPoverty(ServerPlayerEntity player) {
		if (player == null || DataManager.get() == null) {
			return;
		}
		PlayerRecord record = DataManager.get().progress(player);
		if (record.casinoGames <= 20 || record.casinoSpent <= record.casinoWon) {
			return;
		}
		grant(player, DEATH_IN_POVERTY, "broke");
	}

	public static void grant(ServerPlayerEntity player, Identifier id, String criterion) {
		if (player == null || id == null || criterion == null) {
			return;
		}
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		Advancement advancement = server.getAdvancementLoader().get(id);
		if (advancement == null) {
			return;
		}
		player.getAdvancementTracker().grantCriterion(advancement, criterion);
	}

	public static List<AchievementsScreenHandler.Row> rows(MinecraftServer server, UUID uuid) {
		List<AchievementsScreenHandler.Row> done = new ArrayList<>();
		List<AchievementsScreenHandler.Row> rest = new ArrayList<>();
		if (server == null) {
			return done;
		}
		ServerPlayerEntity online = uuid == null ? null : server.getPlayerManager().getPlayer(uuid);
		JsonObject stored = online == null ? loadFile(server, uuid) : null;
		for (Advancement adv : server.getAdvancementLoader().getAdvancements()) {
			if (adv.getDisplay() == null) {
				continue;
			}
			boolean complete = online != null
					? online.getAdvancementTracker().getProgress(adv).isDone()
					: isDone(stored, adv);
			String title = adv.getDisplay().getTitle().getString();
			var row = new AchievementsScreenHandler.Row(adv.getId().toString(), title, complete);
			if (row.done) {
				done.add(row);
			} else {
				rest.add(row);
			}
		}
		done.addAll(rest);
		return done;
	}

	public static void clearAll(MinecraftServer server, UUID uuid) {
		if (server == null || uuid == null) {
			return;
		}
		ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
		if (online != null) {
			for (Advancement adv : server.getAdvancementLoader().getAdvancements()) {
				for (String crit : adv.getCriteria().keySet()) {
					online.getAdvancementTracker().revokeCriterion(adv, crit);
				}
			}
		}
		saveFile(server, uuid, new JsonObject());
	}

	public static boolean toggle(MinecraftServer server, UUID uuid, Identifier id, boolean grant) {
		if (server == null || uuid == null || id == null) {
			return false;
		}
		Advancement adv = server.getAdvancementLoader().get(id);
		if (adv == null) {
			return false;
		}
		ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
		if (online != null) {
			for (String crit : adv.getCriteria().keySet()) {
				if (grant) {
					online.getAdvancementTracker().grantCriterion(adv, crit);
				} else {
					online.getAdvancementTracker().revokeCriterion(adv, crit);
				}
			}
			return true;
		}
		JsonObject root = loadFile(server, uuid);
		if (grant) {
			JsonObject entry = new JsonObject();
			JsonObject criteria = new JsonObject();
			String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date());
			for (String crit : adv.getCriteria().keySet()) {
				criteria.addProperty(crit, stamp);
			}
			entry.add("criteria", criteria);
			entry.addProperty("done", true);
			root.add(id.toString(), entry);
		} else {
			root.remove(id.toString());
		}
		return saveFile(server, uuid, root);
	}

	private static boolean isDone(JsonObject stored, Advancement adv) {
		if (stored == null || adv == null) {
			return false;
		}
		JsonElement el = stored.get(adv.getId().toString());
		if (el == null || !el.isJsonObject()) {
			return false;
		}
		JsonObject obj = el.getAsJsonObject();
		if (obj.has("done") && obj.get("done").isJsonPrimitive() && obj.get("done").getAsBoolean()) {
			return true;
		}
		if (!obj.has("criteria") || !obj.get("criteria").isJsonObject()) {
			return false;
		}
		JsonObject criteria = obj.getAsJsonObject("criteria");
		if (adv.getCriteria().isEmpty()) {
			return false;
		}
		for (String crit : adv.getCriteria().keySet()) {
			if (!criteria.has(crit)) {
				return false;
			}
		}
		return true;
	}

	private static Path file(MinecraftServer server, UUID uuid) {
		if (server == null || uuid == null) {
			return null;
		}
		return server.getSavePath(WorldSavePath.ADVANCEMENTS).resolve(uuid + ".json");
	}

	private static JsonObject loadFile(MinecraftServer server, UUID uuid) {
		Path path = file(server, uuid);
		if (path == null || !Files.isRegularFile(path)) {
			return new JsonObject();
		}
		try {
			JsonElement el = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
			return el != null && el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();
		} catch (Exception ignored) {
			return new JsonObject();
		}
	}

	private static boolean saveFile(MinecraftServer server, UUID uuid, JsonObject root) {
		Path path = file(server, uuid);
		if (path == null || root == null) {
			return false;
		}
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, root.toString(), StandardCharsets.UTF_8);
			return true;
		} catch (Exception ignored) {
			return false;
		}
	}
}
