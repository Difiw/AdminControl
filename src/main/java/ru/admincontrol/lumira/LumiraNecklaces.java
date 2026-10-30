package ru.admincontrol.lumira;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.cw.CwStoryRules;
import ru.admincontrol.lumira.item.NecklaceItem;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Слот ожерелья и все эффекты ожерелий. Действует только в сюжетных измерениях, только для надетого предмета. */
public final class LumiraNecklaces {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static final UUID DEW_SPEED = UUID.fromString("5d0b6b63-7a0e-4c54-9b0e-3f5a7a10d001");
	private static final UUID GUARD_SPEED = UUID.fromString("5d0b6b63-7a0e-4c54-9b0e-3f5a7a10d002");
	private static Map<String, String> slots;
	private static final Map<UUID, Long> LAST_COMBAT = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_HEAL = new ConcurrentHashMap<>();

	public static final net.minecraft.util.Identifier CLICK = new net.minecraft.util.Identifier(AdminControlMod.MOD_ID, "neck_click");

	private LumiraNecklaces() {
	}

	private static Path file() {
		return LumiraConfig.dir().resolve("lumira_necklaces.json");
	}

	private static synchronized Map<String, String> load() {
		if (slots == null) {
			slots = new HashMap<>();
			try {
				if (Files.exists(file())) {
					Map<String, String> read = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8),
							new TypeToken<HashMap<String, String>>() {
							}.getType());
					if (read != null) {
						slots = read;
					}
				}
			} catch (Exception e) {
				AdminControlMod.LOGGER.error("Lumira: слоты ожерелий не прочитаны", e);
			}
		}
		return slots;
	}

	private static synchronized void save() {
		try {
			Files.writeString(file(), GSON.toJson(load()), StandardCharsets.UTF_8);
		} catch (Exception e) {
			AdminControlMod.LOGGER.error("Lumira: слоты ожерелий не сохранены", e);
		}
	}

	public static synchronized ItemStack equipped(UUID id) {
		String snbt = load().get(id.toString());
		if (snbt == null || snbt.isEmpty()) {
			return ItemStack.EMPTY;
		}
		try {
			NbtCompound nbt = StringNbtReader.parse(snbt);
			return ItemStack.fromNbt(nbt);
		} catch (Exception e) {
			return ItemStack.EMPTY;
		}
	}

	private static synchronized void set(UUID id, ItemStack stack) {
		if (stack.isEmpty()) {
			load().remove(id.toString());
		} else {
			load().put(id.toString(), stack.writeNbt(new NbtCompound()).toString());
		}
		save();
	}

	public static boolean equip(ServerPlayerEntity player, Hand hand) {
		if (!CwStoryRules.inside(player)) {
			player.sendMessage(Text.literal("Ожерелья действуют только в сюжетном мире"), true);
			return false;
		}
		ItemStack held = player.getStackInHand(hand);
		if (!(held.getItem() instanceof NecklaceItem)) {
			return false;
		}
		ItemStack previous = equipped(player.getUuid());
		ItemStack moved = held.copyWithCount(1);
		held.decrement(1);
		set(player.getUuid(), moved);
		if (!previous.isEmpty() && !player.getInventory().insertStack(previous)) {
			player.dropItem(previous, false);
		}
		refresh(player);
		player.sendMessage(Text.literal("Ожерелье надето"), true);
		return true;
	}

	/** Клик по ячейке ожерелья в инвентаре: с ожерельем на курсоре — надеть (обмен), с пустым курсором — снять. */
	public static void handleSlotClick(ServerPlayerEntity player) {
		var handler = player.currentScreenHandler;
		ItemStack cursor = handler.getCursorStack();
		ItemStack worn = equipped(player.getUuid());
		if (!cursor.isEmpty()) {
			if (!(cursor.getItem() instanceof NecklaceItem) || !CwStoryRules.inside(player)) {
				player.sendMessage(Text.literal("В ячейку ожерелья можно положить только ожерелье, и только в сюжетном мире"), true);
				return;
			}
			ItemStack put = cursor.copyWithCount(1);
			cursor.decrement(1);
			set(player.getUuid(), put);
			if (!worn.isEmpty()) {
				if (cursor.isEmpty()) {
					handler.setCursorStack(worn);
				} else if (!player.getInventory().insertStack(worn)) {
					player.dropItem(worn, false);
				}
			}
		} else if (!worn.isEmpty()) {
			set(player.getUuid(), ItemStack.EMPTY);
			handler.setCursorStack(worn);
		} else {
			return;
		}
		refresh(player);
		LumiraNet.sendState(player, true);
		handler.updateToClient();
	}

	public static boolean unequip(ServerPlayerEntity player) {
		ItemStack current = equipped(player.getUuid());
		if (current.isEmpty()) {
			return false;
		}
		set(player.getUuid(), ItemStack.EMPTY);
		if (!player.getInventory().insertStack(current)) {
			player.dropItem(current, false);
		}
		refresh(player);
		return true;
	}

	/** Активное ожерелье: надето, игрок в сюжетном мире, заряды остались. */
	public static ItemStack active(ServerPlayerEntity player) {
		if (player == null || !CwStoryRules.inside(player)) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = equipped(player.getUuid());
		return !stack.isEmpty() && NecklaceItem.charges(stack) > 0 ? stack : ItemStack.EMPTY;
	}

	private static void spend(ServerPlayerEntity player, ItemStack stack) {
		stack.setDamage(Math.min(stack.getMaxDamage(), stack.getDamage() + 1));
		set(player.getUuid(), stack);
		if (NecklaceItem.charges(stack) <= 0) {
			player.sendMessage(Text.literal("Ожерелье разряжено: ремонт на рунной наковальне"), true);
		}
	}

	public static int bonusMaxHealth(ServerPlayerEntity player) {
		return NecklaceItem.maxHealthBonus(active(player));
	}

	private static void setModifier(ServerPlayerEntity player, UUID id, String name, double value) {
		EntityAttributeInstance inst = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (inst == null) {
			return;
		}
		EntityAttributeModifier existing = inst.getModifier(id);
		if (value == 0) {
			if (existing != null) {
				inst.removeModifier(id);
			}
			return;
		}
		if (existing != null && existing.getValue() == value) {
			return;
		}
		if (existing != null) {
			inst.removeModifier(id);
		}
		inst.addTemporaryModifier(new EntityAttributeModifier(id, name, value, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
	}

	/** Недостатки скорости и лечение «Росы»; вызывается каждые 10 тиков. */
	public static void tick(MinecraftServer server) {
		long now = server.getTicks();
		if (now % 10 != 0) {
			return;
		}
		LumiraConfig cfg = LumiraConfig.get();
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			ItemStack stack = active(player);
			String kind = stack.getItem() instanceof NecklaceItem n ? n.kind() : "";
			setModifier(player, DEW_SPEED, "Ожерелье Росы", kind.equals("dew") ? -cfg.dewSpeedPenalty : 0);
			setModifier(player, GUARD_SPEED, "Ожерелье Стража", kind.equals("guard") ? -cfg.guardSpeedPenalty : 0);
			if (kind.equals("dew") && player.isAlive() && player.getHealth() < player.getMaxHealth()) {
				long lastCombat = LAST_COMBAT.getOrDefault(player.getUuid(), -1_000_000L);
				long lastHeal = LAST_HEAL.getOrDefault(player.getUuid(), -1_000_000L);
				if (now - lastCombat >= cfg.dewIdleSeconds * 20L && now - lastHeal >= cfg.dewIntervalSeconds * 20L) {
					player.heal((float) cfg.dewHeal);
					LAST_HEAL.put(player.getUuid(), now);
					spend(player, stack);
				}
			}
		}
	}

	/** После смены слота/мира: пересчитать модификаторы и здоровье. */
	public static void refresh(ServerPlayerEntity player) {
		LumiraHealth.apply(player);
	}

	public static void forget(UUID id) {
		LAST_COMBAT.remove(id);
		LAST_HEAL.remove(id);
	}

	private static boolean direct(DamageSource source) {
		return source.getSource() != null && source.getSource() == source.getAttacker();
	}

	/**
	 * Вызывается один раз на событие урона (начало LivingEntity.damage), до обработки брони.
	 * Возвращает итоговое значение урона.
	 */
	public static float modifyDamage(LivingEntity victim, DamageSource source, float amount) {
		if (amount <= 0) {
			return amount;
		}
		LumiraConfig cfg = LumiraConfig.get();
		Entity attacker = source.getAttacker();
		long now = victim.getWorld().getTime();
		ServerPlayerEntity victimPlayer = victim instanceof ServerPlayerEntity p ? p : null;
		ServerPlayerEntity attackerPlayer = attacker instanceof ServerPlayerEntity p ? p : null;
		if (victimPlayer != null && CwStoryRules.inside(victimPlayer)) {
			LAST_COMBAT.put(victimPlayer.getUuid(), (long) victimPlayer.getServer().getTicks());
		}
		if (attackerPlayer != null && CwStoryRules.inside(attackerPlayer)) {
			LAST_COMBAT.put(attackerPlayer.getUuid(), (long) attackerPlayer.getServer().getTicks());
		}
		float result = amount;
		if (victimPlayer != null && attacker != null && !(attacker instanceof ServerPlayerEntity) && direct(source)
				&& cfg.isHostile(attacker)) {
			ItemStack stack = active(victimPlayer);
			if (stack.getItem() instanceof NecklaceItem n) {
				switch (n.kind()) {
					case "echo" -> result *= 1f + (float) cfg.echoTaken;
					case "star" -> result *= 1f + (float) cfg.starTaken;
					case "guard" -> {
						result *= 1f - (float) cfg.guardReduction;
						spend(victimPlayer, stack);
					}
					default -> {
					}
				}
			}
		}
		if (attackerPlayer != null && victimPlayer == null && direct(source) && cfg.isHostile(victim)) {
			ItemStack stack = active(attackerPlayer);
			if (stack.getItem() instanceof NecklaceItem n) {
				if (n.kind().equals("echo")) {
					result *= 1f + (float) cfg.echoDealt;
					spend(attackerPlayer, stack);
				} else if (n.kind().equals("star")) {
					result *= 1f + (float) cfg.starDealt;
					spend(attackerPlayer, stack);
				}
			}
		}
		return result;
	}
}
