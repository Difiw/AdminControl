package ru.admincontrol.lumira;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.ModSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Крафт «Coin лиры» и «Coin мирты», который администратор меняет в /dserver. Рецепт бесформенный (до 9 предметов),
 * пустой список — рецепт выключен (по умолчанию, чтобы не появилось самовольной выдачи).
 */
public final class CoinRecipes {
	public static final Identifier REQUEST = new Identifier(AdminControlMod.MOD_ID, "coin_recipe_req");
	public static final Identifier SET = new Identifier(AdminControlMod.MOD_ID, "coin_recipe_set");
	public static final Identifier SYNC = new Identifier(AdminControlMod.MOD_ID, "coin_recipe_sync");

	/** Копия на клиенте: [0] — лиры, [1] — мирты. */
	public static final List<String>[] CLIENT_RECIPE = newLists();
	public static final int[] CLIENT_COUNT = {1, 1};

	@SuppressWarnings("unchecked")
	private static List<String>[] newLists() {
		return new List[]{new ArrayList<String>(), new ArrayList<String>()};
	}

	private CoinRecipes() {
	}

	public static List<String> recipe(boolean lyra) {
		DataManager data = DataManager.get();
		if (data == null) {
			return List.of();
		}
		ModSettings s = data.settings();
		List<String> list = lyra ? s.coinLyraRecipe : s.coinMyrtleRecipe;
		return list == null ? List.of() : list;
	}

	public static int count(boolean lyra) {
		DataManager data = DataManager.get();
		if (data == null) {
			return 1;
		}
		return Math.max(1, Math.min(64, lyra ? data.settings().coinLyraCount : data.settings().coinMyrtleCount));
	}

	public static void sendTo(ServerPlayerEntity player) {
		if (player == null || player.networkHandler == null || !ServerPlayNetworking.canSend(player, SYNC)) {
			return;
		}
		PacketByteBuf buf = PacketByteBufs.create();
		for (boolean lyra : new boolean[]{true, false}) {
			buf.writeVarInt(count(lyra));
			List<String> list = recipe(lyra);
			buf.writeVarInt(list.size());
			for (String id : list) {
				buf.writeString(id, 128);
			}
		}
		ServerPlayNetworking.send(player, SYNC, buf);
	}

	public static void handleRequest(MinecraftServer server, ServerPlayerEntity player) {
		server.execute(() -> {
			if (CommandUtil.canUse(player.getCommandSource(), "dserver")) {
				sendTo(player);
			}
		});
	}

	public static void handleSet(MinecraftServer server, ServerPlayerEntity player, PacketByteBuf buf) {
		int coin = buf.readVarInt();
		int count = buf.readVarInt();
		int n = Math.min(9, buf.readVarInt());
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			ids.add(buf.readString(128));
		}
		server.execute(() -> {
			if (!CommandUtil.canUse(player.getCommandSource(), "dserver") || DataManager.get() == null) {
				return;
			}
			List<String> clean = new ArrayList<>();
			for (String id : ids) {
				Identifier ident = Identifier.tryParse(id);
				if (ident != null && Registries.ITEM.containsId(ident)) {
					clean.add(ident.toString());
				}
			}
			ModSettings s = DataManager.get().settings();
			int safe = Math.max(1, Math.min(64, count));
			if (coin == 0) {
				s.coinLyraRecipe = clean;
				s.coinLyraCount = safe;
			} else {
				s.coinMyrtleRecipe = clean;
				s.coinMyrtleCount = safe;
			}
			DataManager.get().saveAll();
			ru.admincontrol.emi.EmiServer.invalidate(server);
			sendTo(player);
		});
	}
}
