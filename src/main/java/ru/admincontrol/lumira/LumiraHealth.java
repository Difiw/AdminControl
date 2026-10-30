package ru.admincontrol.lumira;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.cw.CwStoryRules;
import ru.admincontrol.util.EventService;
import ru.admincontrol.util.SkillTreeService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Предел здоровья в трёх режимах.
 * <ul>
 *   <li>обычный сервер — прокачка дерева навыков как раньше (30, 50 и т. д.);</li>
 *   <li>сюжет — база {@code storyBaseHealth} (20), дерево не учитывается, ожерелье может прибавить свой бонус;</li>
 *   <li>ивент — у участников ровно {@code eventMaxHealth} (20), без дерева и без поглощения золотого яблока.</li>
 * </ul>
 * Модификаторы заменяются по фиксированным UUID, поэтому повторные переключения не складываются и не лечат:
 * при уменьшении максимума текущее здоровье ограничивается, при увеличении не растёт.
 */
public final class LumiraHealth {
	public enum Mode {
		NORMAL, STORY, EVENT
	}

	private static final UUID BASE_ID = UUID.fromString("5d0b6b63-7a0e-4c54-9b0e-3f5a7a10e001");
	private static final UUID AMULET_ID = UUID.fromString("5d0b6b63-7a0e-4c54-9b0e-3f5a7a10e002");
	private static final Map<UUID, Mode> LAST_MODE = new ConcurrentHashMap<>();

	private LumiraHealth() {
	}

	public static boolean inEvent(ServerPlayerEntity player) {
		if (player == null || !EventService.active()) {
			return false;
		}
		EventService.State state = EventService.current();
		return state != null && state.members.containsKey(player.getUuid());
	}

	public static Mode mode(ServerPlayerEntity player) {
		if (CwStoryRules.inside(player)) {
			return Mode.STORY;
		}
		return inEvent(player) ? Mode.EVENT : Mode.NORMAL;
	}

	/** Выставляет базу и бонус ожерелья для режима игрока. Дерево навыков обнуляется в SkillTreeService. */
	public static void apply(ServerPlayerEntity player) {
		EntityAttributeInstance max = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (max == null) {
			return;
		}
		LumiraConfig cfg = LumiraConfig.get();
		Mode mode = mode(player);
		LAST_MODE.put(player.getUuid(), mode);
		double target = switch (mode) {
			case STORY -> cfg.storyBaseHealth;
			case EVENT -> cfg.eventMaxHealth;
			case NORMAL -> max.getBaseValue();
		};
		set(max, BASE_ID, "Люмира: предел здоровья режима", target - max.getBaseValue(), mode != Mode.NORMAL);
		int amulet = mode == Mode.STORY ? LumiraNecklaces.bonusMaxHealth(player) : 0;
		set(max, AMULET_ID, "Люмира: бонус ожерелья", amulet, amulet != 0);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
		if (mode == Mode.EVENT) {
			clearAbsorption(player);
		}
	}

	private static void set(EntityAttributeInstance inst, UUID id, String name, double value, boolean on) {
		EntityAttributeModifier old = inst.getModifier(id);
		if (old != null) {
			if (on && old.getValue() == value) {
				return;
			}
			inst.removeModifier(id);
		}
		if (on && value != 0) {
			inst.addTemporaryModifier(new EntityAttributeModifier(id, name, value, EntityAttributeModifier.Operation.ADDITION));
		}
	}

	/** В ивенте поглощение золотого яблока не даёт преимущества (решение не оговорено пользователем). */
	private static void clearAbsorption(ServerPlayerEntity player) {
		if (player.getAbsorptionAmount() > 0) {
			player.setAbsorptionAmount(0);
		}
		if (player.hasStatusEffect(StatusEffects.ABSORPTION)) {
			player.removeStatusEffect(StatusEffects.ABSORPTION);
		}
	}

	/** Раз в секунду: смена режима → пересчёт всех атрибутов (дерево навыков + предел), в ивенте — очистка поглощения. */
	public static void tick(MinecraftServer server) {
		if (server.getTicks() % 20 != 0) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			Mode now = mode(player);
			Mode before = LAST_MODE.get(player.getUuid());
			if (before == null) {
				LAST_MODE.put(player.getUuid(), now);
				if (now != Mode.NORMAL) {
					SkillTreeService.applyEffects(player);
				}
			} else if (before != now) {
				SkillTreeService.applyEffects(player);
			}
			if (now == Mode.EVENT) {
				clearAbsorption(player);
			}
		}
	}

	public static void forget(UUID id) {
		LAST_MODE.remove(id);
	}
}
