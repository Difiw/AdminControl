package ru.admincontrol.mixin;

import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketManager;
import net.minecraft.util.math.ChunkSectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla's {@code ChunkTicketManager.handleChunkLeave} dereferences {@code playersByChunkPos.get(chunk)} without a null
 * check. When the section a player is said to be watching was never registered in this world's ticket manager
 * (a player whose dimension change or profile swap was cut short, or one that moved while a transition was running) every
 * later section crossing throws {@code NullPointerException: Cannot invoke "ObjectSet.remove(Object)"} out of
 * {@code onPlayerMove}, which the server logs as "Failed to handle packet ... suppressing error" and which the bug list
 * reports. Nothing is registered for that section, so there is nothing to remove: skipping the removal leaves the manager
 * consistent, and the {@code handleChunkEnter} that follows registers the section the player now stands in. The
 * inconsistency is reported once per minute per player with the active CW transition id, never silently swallowed.
 *
 * <p>Implemented as redirects of the two fastutil calls (library methods, so no name remapping is involved) instead of a
 * shadowed field, which keeps the mixin valid in the obfuscated production runtime as well.</p>
 */
@Mixin(ChunkTicketManager.class)
public abstract class ChunkTicketManagerMixin {
	@Inject(method = "handleChunkLeave", at = @At("HEAD"))
	private void adminControl$begin(ChunkSectionPos pos, ServerPlayerEntity player, CallbackInfo ci) {
		ru.admincontrol.cw.CwDiagnostics.beginLeave(pos, player);
	}

	@Inject(method = "handleChunkLeave", at = @At("RETURN"))
	private void adminControl$end(ChunkSectionPos pos, ServerPlayerEntity player, CallbackInfo ci) {
		ru.admincontrol.cw.CwDiagnostics.endLeave();
	}

	@Redirect(method = "handleChunkLeave", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectSet;remove(Ljava/lang/Object;)Z"))
	private boolean adminControl$remove(ObjectSet<?> watchers, Object player) {
		if (watchers == null) {
			ru.admincontrol.cw.CwDiagnostics.chunkWatchDesync();
			return false;
		}
		return watchers.remove(player);
	}

	@Redirect(method = "handleChunkLeave", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectSet;isEmpty()Z"))
	private boolean adminControl$isEmpty(ObjectSet<?> watchers) {
		return watchers != null && watchers.isEmpty();
	}
}
