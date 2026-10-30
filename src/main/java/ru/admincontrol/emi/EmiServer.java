package ru.admincontrol.emi;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.item.ItemEncryption;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Серверная часть CW EMI. Сервер — единственный источник истины: клиент получает только рецепты, разрешённые именно
 * этому игроку. Закрытые станции/рецепты не передаются даже в виде идентификаторов — лишь общий флаг «есть недоступное».
 */
public final class EmiServer {
	public static final int PROTOCOL = 1;
	public static final Identifier HELLO = new Identifier(AdminControlMod.MOD_ID, "emi_hello");
	public static final Identifier ACK = new Identifier(AdminControlMod.MOD_ID, "emi_ack");
	public static final Identifier QUERY = new Identifier(AdminControlMod.MOD_ID, "emi_query");
	public static final Identifier RESULT = new Identifier(AdminControlMod.MOD_ID, "emi_result");
	public static final Identifier INVALIDATE = new Identifier(AdminControlMod.MOD_ID, "emi_invalidate");

	public static final int MODE_OBTAIN = 0, MODE_USES = 1;
	public static final int STATUS_OK = 0, STATUS_DENIED = 1;
	private static final int HELLO_TIMEOUT_TICKS = 200;
	private static final double BURST = 10, REFILL_PER_SEC = 8;

	private record Ack(int protocol, List<String> conflicts) {
	}

	private static final Map<UUID, Long> PENDING = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> JOIN_TICK = new ConcurrentHashMap<>();
	private static final Map<UUID, Ack> ACKS = new ConcurrentHashMap<>();
	private static final class Bucket {
		double tokens = BURST;
		long last = System.nanoTime();
	}

	private static final Map<UUID, Bucket> BUCKETS = new ConcurrentHashMap<>();
	private static final AtomicInteger VERSION = new AtomicInteger();
	private static List<EmiRecipe> cache = List.of();
	private static int cacheVersion = -1;
	private static Object cacheOwner;

	private EmiServer() {
	}

	// ---------- настройки ----------
	public static boolean enabled() {
		DataManager data = DataManager.get();
		return data == null || data.settings().cwEmiEnabled;
	}

	public static boolean enforce() {
		DataManager data = DataManager.get();
		return data != null && data.settings().cwEmiEnforce;
	}

	// ---------- регистрация ----------
	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(ACK, (server, player, handler, buf, sender) -> {
			long nonce = buf.readLong();
			int protocol = buf.readVarInt();
			int n = Math.max(0, Math.min(8, buf.readVarInt()));
			List<String> conflicts = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				conflicts.add(buf.readString(96));
			}
			server.execute(() -> onAck(player, nonce, protocol, conflicts));
		});
		ServerPlayNetworking.registerGlobalReceiver(QUERY, (server, player, handler, buf, sender) -> {
			int req = buf.readVarInt();
			int mode = buf.readUnsignedByte();
			String item = buf.readString(128);
			server.execute(() -> handleQuery(server, player, req, mode, item));
		});
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> invalidate(server));
	}

	private static final Map<UUID, Long> NONCES = new ConcurrentHashMap<>();

	public static void hello(ServerPlayerEntity player) {
		forget(player);
		if (!enabled() || !ServerPlayNetworking.canSend(player, HELLO)) {
			return;
		}
		long nonce = player.getRandom().nextLong();
		NONCES.put(player.getUuid(), nonce);
		JOIN_TICK.put(player.getUuid(), (long) player.getServer().getTicks());
		PENDING.put(player.getUuid(), nonce);
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(PROTOCOL);
		buf.writeLong(nonce);
		ServerPlayNetworking.send(player, HELLO, buf);
	}

	public static void forget(ServerPlayerEntity player) {
		if (player == null) {
			return;
		}
		UUID id = player.getUuid();
		PENDING.remove(id);
		NONCES.remove(id);
		JOIN_TICK.remove(id);
		ACKS.remove(id);
		BUCKETS.remove(id);
	}

	private static void onAck(ServerPlayerEntity player, long nonce, int protocol, List<String> conflicts) {
		Long expected = NONCES.get(player.getUuid());
		if (expected == null || expected != nonce) {
			return; // чужой или устаревший ответ рукопожатия
		}
		PENDING.remove(player.getUuid());
		ACKS.put(player.getUuid(), new Ack(protocol, List.copyOf(conflicts)));
		if (!conflicts.isEmpty()) {
			AdminControlMod.LOGGER.warn("[CW EMI] {} отклонён: несовместимые просмотрщики {}", player.getGameProfile().getName(), conflicts);
			player.networkHandler.disconnect(Text.literal("CW EMI: обнаружен несовместимый просмотрщик рецептов: "
					+ String.join(", ", conflicts) + ". " + EmiIncompat.instruction()));
			return;
		}
		if (protocol != PROTOCOL && enforce()) {
			player.networkHandler.disconnect(Text.literal("CW EMI: версия протокола клиента (" + protocol + ") не совпадает с серверной ("
					+ PROTOCOL + "). Обновите admin-control.jar на клиенте."));
		}
	}

	/** Только для автотестов: имитирует подтверждённое рукопожатие чистого клиента. */
	public static void acknowledgeForTest(ServerPlayerEntity player) {
		ACKS.put(player.getUuid(), new Ack(PROTOCOL, List.of()));
	}

	public static void tick(MinecraftServer server) {
		if (!enforce() || server.getTicks() % 20 != 0) {
			return;
		}
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			UUID id = p.getUuid();
			Long joined = JOIN_TICK.get(id);
			if (joined != null && PENDING.containsKey(id) && server.getTicks() - joined > HELLO_TIMEOUT_TICKS) {
				p.networkHandler.disconnect(Text.literal("CW EMI: клиент не подтвердил проверку совместимости. "
						+ "Установите актуальный admin-control.jar и " + EmiIncompat.instruction()));
			}
		}
	}

	/** Для CwClientGate: null — клиент подтвердил совместимость; иначе причина отказа входа в сюжетные измерения. */
	public static String cwProblem(ServerPlayerEntity player) {
		if (!enabled()) {
			return null;
		}
		Ack ack = ACKS.get(player.getUuid());
		if (ack == null) {
			return "CW EMI: клиент не подтвердил проверку совместимости просмотрщиков. Установите актуальный admin-control.jar и переподключитесь.";
		}
		if (!ack.conflicts().isEmpty()) {
			return "CW EMI: несовместимый просмотрщик рецептов (" + String.join(", ", ack.conflicts()) + "). " + EmiIncompat.instruction();
		}
		if (ack.protocol() != PROTOCOL) {
			return "CW EMI: версия протокола клиента " + ack.protocol() + ", сервера " + PROTOCOL + ". Обновите клиентский JAR.";
		}
		return null;
	}

	private static boolean trusted(ServerPlayerEntity player) {
		Ack ack = ACKS.get(player.getUuid());
		return enabled() && ack != null && ack.protocol() == PROTOCOL && ack.conflicts().isEmpty();
	}

	// ---------- обновление клиентов ----------
	public static void invalidate(MinecraftServer server) {
		VERSION.incrementAndGet();
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (ACKS.containsKey(p.getUuid()) && ServerPlayNetworking.canSend(p, INVALIDATE)) {
				ServerPlayNetworking.send(p, INVALIDATE, PacketByteBufs.create());
			}
		}
	}

	private static synchronized List<EmiRecipe> allRecipes(MinecraftServer server) {
		Object owner = server.getRecipeManager();
		int version = VERSION.get();
		if (owner != cacheOwner || version != cacheVersion) {
			List<EmiRecipe> converted = EmiConvert.convertAll(server.getRecipeManager(), server.getRegistryManager());
			List<EmiRecipe> withSources = new ArrayList<>(converted);
			try {
				withSources.addAll(EmiSources.compute(server, converted));
			} catch (RuntimeException e) {
				AdminControlMod.LOGGER.warn("[CW EMI] способы получения не рассчитаны: {}", e.toString());
			}
			cache = List.copyOf(withSources);
			cacheOwner = owner;
			cacheVersion = version;
		}
		return cache;
	}

	// ---------- запрос ----------
	public static boolean allowRate(ServerPlayerEntity player) {
		Bucket b = BUCKETS.computeIfAbsent(player.getUuid(), k -> new Bucket());
		long now = System.nanoTime();
		b.tokens = Math.min(BURST, b.tokens + (now - b.last) / 1e9 * REFILL_PER_SEC);
		b.last = now;
		if (b.tokens < 1) {
			return false;
		}
		b.tokens -= 1;
		return true;
	}

	/**
	 * Авторитетный расчёт ответа для игрока. null = запрос отклонён (неизвестный, скрытый или запрещённый предмет); причина
	 * намеренно не различается, чтобы ответ не подтверждал существование скрытого ID.
	 */
	public static EmiFilter.Out compute(MinecraftServer server, ServerPlayerEntity player, String itemId, int mode) {
		Identifier ident = Identifier.tryParse(itemId);
		Item item = ident != null && Registries.ITEM.containsId(ident) ? Registries.ITEM.get(ident) : Items.AIR;
		boolean dev = ItemEncryption.isDeveloper(player);
		boolean op = player.hasPermissionLevel(2);
		boolean denied = item == Items.AIR || !EmiAccess.listed(item, dev, op) || ItemEncryption.iconHiddenFor(player, item)
				|| (mode == MODE_USES && ItemEncryption.useViewBlockedFor(player, item));
		if (denied) {
			return null;
		}
		java.util.function.Predicate<Item> hidden = other -> ItemEncryption.recipeHiddenFor(player, other) || !EmiAccess.listed(other, dev, op);
		EmiFilter.Out out = EmiFilter.build(allRecipes(server), item, mode == MODE_OBTAIN, hidden);
		if (mode == MODE_OBTAIN) {
			// бокс зависит от настроек админов и меняется на лету: добавляем к кэшу при каждом запросе
			for (EmiRecipe box : EmiSources.boxFor(item)) {
				EmiRecipe safe = EmiFilter.sanitize(box, hidden);
				if (safe != null) {
					List<EmiRecipe> group = out.groups.remove("source");
					if (group == null) {
						group = new ArrayList<>();
					}
					group.add(safe);
					out.groups.put("source", group);
				}
			}
		}
		return out;
	}

	/** Полный кэш рецептов и способов получения без фильтра игрока: для отчёта администратора. */
	public static List<EmiRecipe> allForReport(MinecraftServer server) {
		return allRecipes(server);
	}

	private static void handleQuery(MinecraftServer server, ServerPlayerEntity player, int req, int mode, String itemId) {
		if (!trusted(player) || !allowRate(player) || mode < 0 || mode > 1) {
			return;
		}
		EmiFilter.Out filtered = compute(server, player, itemId, mode);
		PacketByteBuf out = PacketByteBufs.create();
		out.writeVarInt(req);
		out.writeByte(mode);
		out.writeString(filtered == null ? "" : itemId, 128);
		if (filtered == null) {
			out.writeByte(STATUS_DENIED);
			out.writeBoolean(false);
			out.writeVarInt(0);
			ServerPlayNetworking.send(player, RESULT, out);
			return;
		}
		out.writeByte(STATUS_OK);
		out.writeBoolean(filtered.locked);
		out.writeVarInt(filtered.groups.size());
		for (List<EmiRecipe> group : filtered.groups.values()) {
			out.writeVarInt(group.size());
			for (EmiRecipe r : group) {
				r.write(out);
			}
		}
		ServerPlayNetworking.send(player, RESULT, out);
	}
}
