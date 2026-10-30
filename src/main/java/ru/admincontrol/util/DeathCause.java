package ru.admincontrol.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;

/** Читаемая причина смерти для логов игрока. */
public final class DeathCause {
	private static final Map<String, String> TYPES = Map.ofEntries(
			Map.entry("fall", "падение с высоты"),
			Map.entry("lava", "лава"),
			Map.entry("inFire", "огонь"),
			Map.entry("onFire", "горение"),
			Map.entry("hotFloor", "магмовый блок"),
			Map.entry("drown", "утонул"),
			Map.entry("dryout", "высох"),
			Map.entry("explosion", "взрыв"),
			Map.entry("explosion.player", "взрыв"),
			Map.entry("badRespawnPoint", "взрыв кровати / якоря возрождения"),
			Map.entry("outOfWorld", "падение в бездну"),
			Map.entry("outsideBorder", "граница мира"),
			Map.entry("starve", "голод"),
			Map.entry("cactus", "кактус"),
			Map.entry("sweetBerryBush", "куст сладких ягод"),
			Map.entry("inWall", "задохнулся в блоке"),
			Map.entry("cramming", "давка сущностей"),
			Map.entry("lightningBolt", "молния"),
			Map.entry("magic", "магия / зелье"),
			Map.entry("indirectMagic", "магия / зелье"),
			Map.entry("wither", "эффект иссушения"),
			Map.entry("witherSkull", "череп иссушителя"),
			Map.entry("freeze", "замерзание"),
			Map.entry("flyIntoWall", "удар о стену на элитрах"),
			Map.entry("anvil", "наковальня"),
			Map.entry("fallingBlock", "падающий блок"),
			Map.entry("fallingStalactite", "сталактит"),
			Map.entry("stalagmite", "сталагмит"),
			Map.entry("dragonBreath", "дыхание дракона"),
			Map.entry("sonic_boom", "звуковой удар вардена"),
			Map.entry("thorns", "шипы"),
			Map.entry("arrow", "стрела"),
			Map.entry("trident", "трезубец"),
			Map.entry("fireworks", "фейерверк"),
			Map.entry("fireball", "огненный шар"),
			Map.entry("sting", "ужаление"),
			Map.entry("thrown", "брошенный предмет"),
			Map.entry("mob", "атака моба"),
			Map.entry("player", "атака игрока"),
			Map.entry("genericKill", "команда /kill"),
			Map.entry("generic", "неизвестная причина"));

	private DeathCause() {
	}

	public static String describe(DamageSource source) {
		if (source == null) {
			return "неизвестная причина";
		}
		String type = TYPES.getOrDefault(source.getName(), source.getName());
		Entity attacker = source.getAttacker();
		if (attacker instanceof ServerPlayerEntity killer) {
			String out = "убит игроком " + killer.getGameProfile().getName() + " (" + type + ")";
			ItemStack weapon = killer.getMainHandStack();
			if (!weapon.isEmpty()) {
				out += ", оружие: " + Registries.ITEM.getId(weapon.getItem()).getPath();
			}
			return out;
		}
		if (attacker instanceof LivingEntity mob) {
			return "убит мобом " + mob.getName().getString() + " (" + type + ")";
		}
		if (attacker != null) {
			return type + " от " + attacker.getName().getString();
		}
		return type;
	}
}
