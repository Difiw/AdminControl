package ru.admincontrol.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;

public final class NpcPlayerEntity extends ServerPlayerEntity {
	public NpcPlayerEntity(MinecraftServer server, ServerWorld world, GameProfile profile) {
		super(server, world, profile);
	}

	@Override
	public String getIp() {
		return "127.0.0.1";
	}

	@Override
	public boolean hasNoGravity() {
		return false;
	}

	@Override
	public boolean isSpectator() {
		return false;
	}

	@Override
	public void tick() {
		this.updateLastActionTime();
		this.setNoGravity(false);
		if (NpcService.isClientDriven(this)) {
			this.noClip = false;
			super.tick();
			return;
		}
		boolean puppet = NpcService.isControlled(this) && !NpcService.isClientDriven(this) && !this.dead;
		this.noClip = puppet;
		try {
			if (this.dead) {
				this.updatePostDeath();
			} else if (!puppet) {
				this.baseTick();
				if (!this.isRemoved()) {
					this.tickMovement();
					this.tickHandSwing();
					this.playerTick();
				}
			}
		} catch (RuntimeException ignored) {
		}
		if (!this.isRemoved() && this.getServerWorld() != null) {
			if (CwNpcService.isStoryNpc(this)) {
				// A story actor is a fixed scene object, not a regular survival player.
				// Clearing only when needed avoids needless inventory sync every tick.
				if (!this.getInventory().isEmpty()) this.getInventory().clear();
				this.setSneaking(false);
				this.setSprinting(false);
				this.setSwimming(false);
				this.setPose(net.minecraft.entity.EntityPose.STANDING);
				this.setVelocity(0.0D, this.getVelocity().y, 0.0D);
			}
			ru.admincontrol.cw.CwNpcPoseService.tick(this);
			if (this.networkHandler != null) {
				this.networkHandler.syncWithPlayerPosition();
			}
			this.getServerWorld().getChunkManager().updatePosition(this);
		}
	}

	@Override
	public boolean isPushable() {
		return !this.dead && !NpcService.isControlled(this) && super.isPushable();
	}

	@Override
	public boolean isCollidable() {
		return !this.dead && !NpcService.isControlled(this) && super.isCollidable();
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (this.dead || GodModeService.enabled(this)) {
			return false;
		}
		return super.damage(source, amount);
	}

	@Override
	public void onDeath(DamageSource source) {
		if (this.dead) {
			return;
		}
		ServerPlayerEntity controller = NpcService.controllerOf(this);
		if (controller != null) {
			NpcService.stopControl(controller, true);
			Chat.send(controller, Chat.red("NPC «" + this.getGameProfile().getName() + "» погиб. Управление прекращено."));
		}
		try {
			super.onDeath(source);
		} catch (RuntimeException ignored) {
		}
		this.dead = true;
		this.setHealth(0.0F);
	}

	@Override
	protected void updatePostDeath() {
		this.deathTime++;
		if (!this.getWorld().isClient && this.deathTime >= 20) {
			NpcService.respawn(this);
		}
	}

	void prepare() {
		this.changeGameMode(GameMode.SURVIVAL);
		this.setHealth(this.getMaxHealth());
		this.getHungerManager().setFoodLevel(20);
		this.getHungerManager().setSaturationLevel(20);
		this.unsetRemoved();
		this.dead = false;
		this.deathTime = 0;
		this.noClip = false;
		this.setNoGravity(false);
		this.getAbilities().flying = false;
		this.getAbilities().allowFlying = false;
		this.getAbilities().invulnerable = false;
		this.sendAbilitiesUpdate();
		DataTracker tracker = this.getDataTracker();
		if (tracker != null) {
			tracker.set(PlayerEntity.PLAYER_MODEL_PARTS, (byte) 0x7F);
		}
	}
}
