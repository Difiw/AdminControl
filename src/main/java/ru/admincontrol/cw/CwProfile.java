package ru.admincontrol.cw;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket;
import net.minecraft.network.packet.s2c.play.ExperienceBarUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Whole-player profile swapping for NORMAL / BUILD / STORY. A profile is the complete player NBT (inventory with
 * item components and mod NBT, armour, offhand, ender chest, effects, health, hunger, XP, spawn point, abilities,
 * game mode, position). Loading one first wipes everything the NBT reader would otherwise leave untouched
 * (ender chest, effects, riding state, cursor stack), so nothing leaks from one profile into another.
 */
public final class CwProfile {
	private CwProfile() {}

	/**
	 * Complete snapshot of the player as NBT. Open screens are closed first so the cursor stack and the crafting grid
	 * return into the inventory that is being saved instead of being dropped into the world being left.
	 */
	public static NbtCompound capture(ServerPlayerEntity p) {
		p.closeHandledScreen();
		p.stopRiding();
		NbtCompound nbt = new NbtCompound();
		p.writeNbt(nbt);
		return nbt;
	}

	public static NbtCompound parse(String snbt) throws Exception { return StringNbtReader.parse(snbt); }

	/**
	 * Replaces the player's state with {@code nbt}. Position inside the NBT is applied to the current world only as a
	 * placeholder; the caller teleports right after. {@code restoreAbilities} re-applies the saved flight / invulnerable
	 * flags after the game mode was changed (a survival admin who had flight enabled keeps it).
	 */
	public static void apply(ServerPlayerEntity p, NbtCompound nbt) {
		p.closeHandledScreen();
		p.stopRiding();
		p.clearStatusEffects();
		p.getInventory().clear();
		p.getEnderChestInventory().clear();
		NbtCompound copy = nbt.copy();
		copy.remove("RootVehicle"); // would spawn another copy of the vehicle every time the profile is loaded
		p.readNbt(copy);
		p.extinguish();
		p.fallDistance = 0;
		p.setVelocity(Vec3d.ZERO);
		p.velocityModified = true;
	}

	/** Re-applies abilities (flight, invulnerability, speeds) saved in {@code nbt}; call after the game mode was set. */
	public static void applyAbilities(ServerPlayerEntity p, NbtCompound nbt) {
		if (nbt != null && nbt.contains("abilities", 10)) p.getAbilities().readNbt(nbt);
		p.sendAbilitiesUpdate();
	}

	/** An empty profile (new BUILD or STORY profile): nothing from the previous profile survives. */
	public static void clean(ServerPlayerEntity p) {
		p.closeHandledScreen();
		p.stopRiding();
		p.clearStatusEffects();
		p.getInventory().clear();
		p.getEnderChestInventory().clear();
		p.experienceLevel = 0; p.experienceProgress = 0; p.totalExperience = 0;
		p.getHungerManager().setFoodLevel(20); p.getHungerManager().setSaturationLevel(5.0F);
		p.setHealth(p.getMaxHealth());
		p.setAbsorptionAmount(0);
		p.extinguish();
		p.fallDistance = 0;
		p.setVelocity(Vec3d.ZERO);
		p.velocityModified = true;
	}

	/** Pushes server-only state (abilities, slot, XP, health, effects) to the client after a profile swap. */
	public static void resync(ServerPlayerEntity p) {
		if (p == null || p.networkHandler == null) return;
		p.sendAbilitiesUpdate();
		p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket(p.getInventory().selectedSlot));
		p.playerScreenHandler.syncState();
		p.networkHandler.sendPacket(new ExperienceBarUpdateS2CPacket(p.experienceProgress, p.totalExperience, p.experienceLevel));
		p.networkHandler.sendPacket(new HealthUpdateS2CPacket(p.getHealth(), p.getHungerManager().getFoodLevel(), p.getHungerManager().getSaturationLevel()));
		for (StatusEffectInstance effect : p.getStatusEffects()) p.networkHandler.sendPacket(new EntityStatusEffectS2CPacket(p.getId(), effect));
	}

	/** One-line summary for journal / diagnostics: item stacks, health, XP level, position. */
	public static String describe(NbtCompound nbt) {
		if (nbt == null) return "none";
		int items = nbt.getList("Inventory", 10).size(), ender = nbt.getList("EnderItems", 10).size();
		return "items=" + items + " ender=" + ender + " hp=" + nbt.getFloat("Health") + " xpLevel=" + nbt.getInt("XpLevel")
				+ " effects=" + nbt.getList("active_effects", 10).size() + nbt.getList("ActiveEffects", 10).size()
				+ " mode=" + nbt.getInt("playerGameType");
	}
}
