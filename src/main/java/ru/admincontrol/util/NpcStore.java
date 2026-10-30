package ru.admincontrol.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Собственное состояние каждого NPC (позиция, HP, голод, опыт, инвентарь, эффекты, WC, сила, удача, табло, дерево навыков). */
public final class NpcStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private static Store store = new Store();

	private NpcStore() {
	}

	private static final class Store {
		Map<String, Saved> npcs = new LinkedHashMap<>();
	}

	public static final class Saved {
		public String nick = "";
		public String logicalId = "";
		public String displayName = "";
		public boolean storyStart;
		public Map<String, String> dialogue = new LinkedHashMap<>();
		public String skinId = "";
		public String pose = "straight";
		public String animation = "none";
		public boolean animationLoop;
		/** Custom bone-animation cycle (CwAnimation JSON) played by clients on this NPC; empty = none. */
		public String animJson = "";
		public String dimension = "minecraft:overworld";
		public double x;
		public double y;
		public double z;
		public float yaw;
		public float pitch;
		public float health = 20.0F;
		public int food = 20;
		public float saturation = 5.0F;
		public int xpLevel;
		public int xpTotal;
		public float xpProgress;
		public String inventory = "";
		public List<String> effects = new ArrayList<>();
		public PlayerRecord record;
	}

	/** Эффекты зелий/еды NPC. Пока NPC управляют, они висят на админе — берём оттуда. */
	public static List<String> effectsOf(ServerPlayerEntity entity) {
		List<String> out = new ArrayList<>();
		if (entity != null) {
			for (StatusEffectInstance effect : entity.getStatusEffects()) {
				out.add(effect.writeNbt(new NbtCompound()).toString());
			}
		}
		return out;
	}

	public static void giveEffects(ServerPlayerEntity entity, List<String> effects) {
		if (entity == null || effects == null) {
			return;
		}
		for (String raw : effects) {
			try {
				StatusEffectInstance effect = StatusEffectInstance.fromNbt(StringNbtReader.parse(raw));
				if (effect != null) {
					entity.addStatusEffect(effect);
				}
			} catch (Exception e) {
				ActionLog.error("Не удалось восстановить эффект NPC: " + raw, e);
			}
		}
	}

	/** После входа NPC в мир: дерево навыков, эффекты и HP с учётом бонусов к максимуму. */
	public static void applyAfterJoin(NpcPlayerEntity npc, Saved saved) {
		if (npc == null) {
			return;
		}
		SkillTreeService.applyEffects(npc);
		if (saved == null) {
			return;
		}
		giveEffects(npc, saved.effects);
		npc.setHealth(Math.max(1.0F, Math.min(npc.getMaxHealth(), saved.health)));
	}

	private static String key(String nick) {
		return nick == null ? "" : nick.toLowerCase(Locale.ROOT);
	}

	public static synchronized Saved get(String nick) {
		return store.npcs.get(key(nick));
	}

	public static synchronized void setSkin(String nick, String skinId) {
		Saved saved = store.npcs.computeIfAbsent(key(nick), k -> new Saved());
		saved.nick = nick;
		saved.skinId = skinId == null ? "" : skinId;
		save();
	}

	public static synchronized void setPose(String nick, String pose, String animation, boolean loop) {
		Saved saved = store.npcs.computeIfAbsent(key(nick), k -> new Saved());
		saved.nick = nick;
		saved.pose = pose == null ? "straight" : pose;
		saved.animation = animation == null ? "none" : animation;
		saved.animationLoop = loop;
		save();
	}

	public static synchronized void setAnimJson(String nick, String json) {
		Saved saved = store.npcs.computeIfAbsent(key(nick), k -> new Saved());
		saved.nick = nick;
		saved.animJson = json == null ? "" : json;
		save();
	}

	public static synchronized void setCwMetadata(String nick, String id, String displayName, boolean storyStart) {
		Saved saved = store.npcs.computeIfAbsent(key(nick), k -> new Saved());
		saved.nick = nick; saved.logicalId = id == null ? "" : id;
		saved.displayName = displayName == null ? nick : displayName; saved.storyStart = storyStart; save();
	}

	public static synchronized Saved byLogicalId(String id) {
		for (Saved saved : store.npcs.values()) if (saved != null && saved.logicalId != null && saved.logicalId.equalsIgnoreCase(id)) return saved;
		return null;
	}

	public static synchronized void setDialogue(String id, String dialogueKey, String text) {
		Saved saved = byLogicalId(id); if (saved == null) return;
		if (saved.dialogue == null) saved.dialogue = new LinkedHashMap<>(); saved.dialogue.put(dialogueKey, text); save();
	}

	public static synchronized List<Saved> all() {
		return new ArrayList<>(store.npcs.values());
	}

	public static synchronized void capture(NpcPlayerEntity npc) {
		if (npc == null || npc.getServer() == null) {
			return;
		}
		String nick = npc.getGameProfile().getName();
		Saved saved = store.npcs.computeIfAbsent(key(nick), k -> new Saved());
		saved.nick = nick;
		saved.dimension = npc.getServerWorld().getRegistryKey().getValue().toString();
		saved.x = npc.getX();
		saved.y = npc.getY();
		saved.z = npc.getZ();
		saved.yaw = npc.getYaw();
		saved.pitch = npc.getPitch();
		if (!npc.isDead() && npc.getHealth() > 0.0F) {
			saved.health = npc.getHealth();
			saved.food = npc.getHungerManager().getFoodLevel();
			saved.saturation = npc.getHungerManager().getSaturationLevel();
		}
		saved.xpLevel = npc.experienceLevel;
		saved.xpTotal = npc.totalExperience;
		saved.xpProgress = npc.experienceProgress;
		saved.inventory = InventoryUtil.serialize(npc);
		ServerPlayerEntity controller = NpcService.controllerOf(npc);
		saved.effects = effectsOf(controller != null ? controller : npc);
		if (DataManager.get() != null) {
			PlayerRecord record = DataManager.get().getOrCreate(npc);
			InventoryUtil.snapshot(npc, record);
			saved.record = record;
		}
	}

	public static void apply(NpcPlayerEntity npc, Saved saved) {
		if (npc == null || saved == null) {
			return;
		}
		if (saved.inventory != null && !saved.inventory.isBlank()) {
			InventoryUtil.restore(npc, saved.inventory);
		}
		npc.setHealth(Math.max(1.0F, Math.min(npc.getMaxHealth(), saved.health)));
		npc.getHungerManager().setFoodLevel(Math.max(0, Math.min(20, saved.food)));
		npc.getHungerManager().setSaturationLevel(Math.max(0.0F, saved.saturation));
		npc.experienceLevel = Math.max(0, saved.xpLevel);
		npc.totalExperience = Math.max(0, saved.xpTotal);
		npc.experienceProgress = Math.max(0.0F, Math.min(1.0F, saved.xpProgress));
	}

	/** Вернуть запись WC/силы/удачи NPC в DataManager до того, как сущность запросит её. */
	public static synchronized void adoptRecord(String nick) {
		Saved saved = store.npcs.get(key(nick));
		if (saved != null && saved.record != null && DataManager.get() != null) {
			DataManager.get().restoreNps(saved.record);
		}
	}

	public static synchronized void forget(String nick) {
		if (store.npcs.remove(key(nick)) != null) {
			save();
		}
	}

	private static Path file() {
		DataManager data = DataManager.get();
		return data == null ? null : data.getConfigDir().resolve("npcs.json");
	}

	public static synchronized void load() {
		store = new Store();
		Path file = file();
		if (file == null || !Files.exists(file)) {
			return;
		}
		try {
			Store loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Store.class);
			if (loaded != null && loaded.npcs != null) {
				store = loaded;
			}
		} catch (Exception e) {
			ActionLog.error("Не удалось прочитать npcs.json", e);
		}
	}

	public static synchronized void save() {
		Path file = file();
		if (file == null) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling("npcs.json.tmp");
			Files.writeString(tmp, GSON.toJson(store), StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			ActionLog.error("Не удалось сохранить npcs.json", e);
		}
	}
}
