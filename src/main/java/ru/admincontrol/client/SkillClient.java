package ru.admincontrol.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.network.PacketByteBuf;
import ru.admincontrol.net.ModPackets;
import ru.admincontrol.skill.SkillEffects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Последнее состояние дерева навыков от сервера и дистанции ников игроков со «Скрытностью». */
public final class SkillClient {
	public record NodeState(int level, int max, String note) {
	}

	public record QuestState(long progress, int stage) {
	}

	public record DailyState(long progress, boolean done) {
	}

	public static final class State {
		public boolean viewOnly;
		public String target = "";
		public String notice = "";
		public boolean ok;
		public final Map<String, int[]> coins = new HashMap<>();
		public final Map<String, NodeState> nodes = new HashMap<>();
		public final Map<String, QuestState> quests = new HashMap<>();
		public final Map<String, DailyState> dailies = new HashMap<>();
		public long lifeSeconds;
		public final List<String> history = new ArrayList<>();
		/** Ветки, отключённые администрацией для владельца дерева. */
		public final java.util.Set<String> offBranches = new java.util.HashSet<>();
		public boolean questPage;

		public int coins(String currency) {
			int[] c = this.coins.get(currency);
			return c == null ? 0 : c[0];
		}

		public NodeState node(String id) {
			NodeState n = this.nodes.get(id);
			return n == null ? new NodeState(0, 1, "") : n;
		}
	}

	private static volatile State state;
	private static long openedFromInventoryAt;
	private static final Map<String, int[]> NAMES = new ConcurrentHashMap<>();

	private SkillClient() {
	}

	public static State state() {
		return state;
	}

	public static void onState(MinecraftClient client, PacketByteBuf buf) {
		State s = new State();
		boolean open = buf.readBoolean();
		s.viewOnly = buf.readBoolean();
		s.target = buf.readString(32);
		s.notice = buf.readString(256);
		s.ok = buf.readBoolean();
		int n = buf.readVarInt();
		for (int i = 0; i < n; i++) {
			String id = buf.readString(16);
			s.coins.put(id, new int[] {buf.readVarInt(), buf.readVarInt(), buf.readVarInt()});
		}
		n = buf.readVarInt();
		for (int i = 0; i < n; i++) {
			String id = buf.readString(32);
			s.nodes.put(id, new NodeState(buf.readVarInt(), buf.readVarInt(), buf.readString(200)));
		}
		n = buf.readVarInt();
		for (int i = 0; i < n; i++) {
			String id = buf.readString(32);
			s.quests.put(id, new QuestState(buf.readVarLong(), buf.readVarInt()));
		}
		n = buf.readVarInt();
		for (int i = 0; i < n; i++) {
			String id = buf.readString(32);
			s.dailies.put(id, new DailyState(buf.readVarLong(), buf.readBoolean()));
		}
		s.lifeSeconds = buf.readVarLong();
		n = buf.readVarInt();
		for (int i = 0; i < n; i++) {
			s.history.add(buf.readString(200));
		}
		n = buf.isReadable() ? buf.readVarInt() : 0;
		for (int i = 0; i < n; i++) {
			s.offBranches.add(buf.readString(16));
		}
		s.questPage = buf.isReadable() && buf.readBoolean();
		client.execute(() -> {
			state = s;
			if (client.currentScreen instanceof SkillTreeScreen screen) {
				screen.onState(s);
			} else if (client.currentScreen instanceof QuestScreen screen) {
				screen.onState(s);
			} else if (open) {
				client.setScreen(s.questPage ? new QuestScreen(s) : new SkillTreeScreen(s));
			}
		});
	}

	public static void onSelf(MinecraftClient client, PacketByteBuf buf) {
		double jump = buf.readDouble();
		double mine = buf.readDouble();
		double air = buf.isReadable() ? buf.readDouble() : 0;
		client.execute(() -> {
			if (client.player != null) {
				SkillEffects.setClient(client.player.getUuid(), jump, mine, air);
			}
		});
	}

	public static void onNames(MinecraftClient client, PacketByteBuf buf) {
		int n = Math.min(buf.readVarInt(), 1024);
		Map<String, int[]> next = new HashMap<>();
		for (int i = 0; i < n; i++) {
			String nick = buf.readString(16);
			next.put(nick.toLowerCase(Locale.ROOT), new int[] {buf.readVarInt(), buf.readVarInt()});
		}
		client.execute(() -> {
			NAMES.clear();
			NAMES.putAll(next);
		});
	}

	/** Скрыть ник: игрок дальше дистанции, которую для него выставил сервер. */
	public static boolean labelHidden(AbstractClientPlayerEntity player) {
		if (NAMES.isEmpty()) {
			return false;
		}
		int[] d = NAMES.get(player.getGameProfile().getName().toLowerCase(Locale.ROOT));
		if (d == null) {
			return false;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == player) {
			return false;
		}
		double max = player.isSneaky() ? d[1] : d[0];
		return client.getEntityRenderDispatcher().getSquaredDistanceToCamera(player) > max * max;
	}

	public static void buy(String nodeId, int expectedLevel) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeString(nodeId, 32);
		buf.writeVarInt(expectedLevel);
		ClientPlayNetworking.send(ModPackets.SKILL_BUY, buf);
	}

	public static void refresh() {
		ClientPlayNetworking.send(ModPackets.SKILL_REFRESH, PacketByteBufs.create());
	}

	/** Кнопка в инвентаре: закрыть инвентарь, открыть дерево, по Esc вернуться в инвентарь. */
	public static void openFromInventory(MinecraftClient client) {
		if (client.player == null || !ClientPlayNetworking.canSend(ModPackets.SKILL_OPEN)) {
			return;
		}
		openedFromInventoryAt = System.currentTimeMillis();
		client.player.closeHandledScreen();
		ClientPlayNetworking.send(ModPackets.SKILL_OPEN, PacketByteBufs.create());
	}

	/** Дерево открыто кнопкой из инвентаря (ответ сервера пришёл в течение 5 с). */
	public static boolean takeOpenedFromInventory() {
		boolean back = System.currentTimeMillis() - openedFromInventoryAt < 5_000L;
		openedFromInventoryAt = 0L;
		return back;
	}

	public static void closed() {
		if (ClientPlayNetworking.canSend(ModPackets.SKILL_CLOSE)) {
			ClientPlayNetworking.send(ModPackets.SKILL_CLOSE, PacketByteBufs.create());
		}
	}

	public static void reset() {
		state = null;
		openedFromInventoryAt = 0L;
		NAMES.clear();
		SkillEffects.resetClient();
	}
}
