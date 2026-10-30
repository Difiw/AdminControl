package ru.admincontrol.mixin;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.admincontrol.util.VanillaTargetGuard;

import java.util.Collection;

@Mixin(EntityArgumentType.class)
public class EntityArgumentTypeMixin {
	private static final SimpleCommandExceptionType DENIED =
			new SimpleCommandExceptionType(Text.literal("Нельзя применить команду к этому администратору."));

	@Inject(method = "getPlayer", at = @At("RETURN"))
	private static void adminControl$guardPlayer(CommandContext<ServerCommandSource> context, String name,
			CallbackInfoReturnable<ServerPlayerEntity> cir) throws CommandSyntaxException {
				if (!ru.admincontrol.util.ModGate.on()) {
					return;
				}
		if (!VanillaTargetGuard.inCheatContext()) {
			return;
		}
		ServerPlayerEntity target = cir.getReturnValue();
		if (target != null && !VanillaTargetGuard.allow(context.getSource(), target)) {
			VanillaTargetGuard.deny(context.getSource(), target.getGameProfile().getName());
			throw DENIED.create();
		}
	}

	@Inject(method = "getPlayers", at = @At("RETURN"), cancellable = true)
	private static void adminControl$guardPlayers(CommandContext<ServerCommandSource> context, String name,
			CallbackInfoReturnable<Collection<ServerPlayerEntity>> cir) throws CommandSyntaxException {
				if (!ru.admincontrol.util.ModGate.on()) {
					return;
				}
		if (!VanillaTargetGuard.inCheatContext()) {
			return;
		}
		Collection<ServerPlayerEntity> players = cir.getReturnValue();
		if (players == null || players.isEmpty()) {
			return;
		}
		ServerCommandSource source = context.getSource();
		for (ServerPlayerEntity target : players) {
			if (!VanillaTargetGuard.allow(source, target)) {
				VanillaTargetGuard.deny(source, target.getGameProfile().getName());
				throw DENIED.create();
			}
		}
	}
}
