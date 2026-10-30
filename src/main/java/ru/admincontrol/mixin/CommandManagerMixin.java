package ru.admincontrol.mixin;

import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.admincontrol.data.ActionKind;
import ru.admincontrol.util.AdminNotify;
import ru.admincontrol.util.CommandInputLog;
import ru.admincontrol.util.CommandLog;

@Mixin(CommandManager.class)
public class CommandManagerMixin {
	@Inject(method = "executeWithPrefix", at = @At("HEAD"), cancellable = true)
	private void adminControl$guardVanillaTargets(ServerCommandSource source, String command, CallbackInfoReturnable<Integer> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		ru.admincontrol.util.VanillaTargetGuard.begin(command);
		if (!ru.admincontrol.util.VanillaTargetGuard.allowCommand(source, command)) {
			ru.admincontrol.util.VanillaTargetGuard.deny(source, "");
			ru.admincontrol.util.VanillaTargetGuard.end();
			cir.setReturnValue(0);
		}
	}

	@Inject(method = "executeWithPrefix", at = @At("RETURN"))
	private void adminControl$endGuard(ServerCommandSource source, String command, CallbackInfoReturnable<Integer> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		ru.admincontrol.util.VanillaTargetGuard.end();
	}

	@Inject(method = "executeWithPrefix", at = @At("HEAD"))
	private void adminControl$commandTyped(ServerCommandSource source, String command, CallbackInfoReturnable<Integer> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if (source != null && source.getEntity() instanceof ServerPlayerEntity player) {
			CommandInputLog.typed(player, command);
			ru.admincontrol.util.TeleportLog.beginCommand(command);
		}
	}

	@Inject(method = "executeWithPrefix", at = @At("RETURN"))
	private void adminControl$commandResult(ServerCommandSource source, String command, CallbackInfoReturnable<Integer> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		ServerPlayerEntity player = source != null && source.getEntity() instanceof ServerPlayerEntity p ? p : null;
		Integer code = cir.getReturnValue();
		boolean ok = code != null && code > 0;
		try {
			if (player != null) {
				ru.admincontrol.util.TeleportLog.finishCommand(player, ok);
			}
			if (CommandLog.take()) {
				return;
			}
			if (player == null) {
				return;
			}
			String raw = command == null ? "" : command.trim();
			if (raw.startsWith("/")) {
				raw = raw.substring(1);
			}
			if (raw.isBlank()) {
				return;
			}
			raw = CommandInputLog.display(raw);
			if (ok) {
				AdminNotify.playerAction(player, ActionKind.COMMAND, "выполнил команду /" + raw, true);
			} else {
				AdminNotify.playerAction(player, ActionKind.COMMAND_FAIL, "команда не выполнена: /" + raw, false);
			}
		} finally {
			CommandInputLog.endCommand();
		}
	}

	@Inject(method = "execute(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
	private void adminControl$executeGuard(com.mojang.brigadier.ParseResults<ServerCommandSource> parse, String command, CallbackInfoReturnable<Integer> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if (parse == null) {
			return;
		}
		try {
			ServerCommandSource source = parse.getContext().getSource();
			ru.admincontrol.util.VanillaTargetGuard.begin(command);
			if (!ru.admincontrol.util.VanillaTargetGuard.allowCommand(source, command)) {
				ru.admincontrol.util.VanillaTargetGuard.deny(source, "");
				ru.admincontrol.util.VanillaTargetGuard.end();
				cir.setReturnValue(0);
			}
		} catch (RuntimeException ignored) {
		}
	}

	@Inject(method = "execute(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
	private void adminControl$cwCommandGuard(com.mojang.brigadier.ParseResults<ServerCommandSource> parse, String command, CallbackInfoReturnable<Integer> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if (parse == null || !(parse.getContext().getSource().getEntity() instanceof ServerPlayerEntity player)
				|| ru.admincontrol.util.NpcService.isNpc(player)) {
			return;
		}
		String raw = command == null ? "" : command.trim();
		if (raw.startsWith("/")) {
			raw = raw.substring(1);
		}
		String name = raw.isEmpty() ? "" : raw.split("\\s+")[0];
		String deny = ru.admincontrol.util.FreezeService.commandDeny(player, name);
		if (deny == null && ru.admincontrol.data.DataManager.get() != null) {
			String vanilla = ru.admincontrol.util.RankPerms.bloggerVanillaDenied(
					ru.admincontrol.data.DataManager.get().getAdminLevel(player), raw);
			if (vanilla != null) {
				deny = "Блогеру недоступна команда /" + vanilla + ".";
			}
		}
		if (deny == null) {
			String blocked = ru.admincontrol.util.CwService.commandBlocked(player, raw);
			if (blocked != null) {
				deny = "Команда " + blocked + " вам запрещена.";
			}
		}
		if (deny != null) {
			ru.admincontrol.util.Chat.send(player, ru.admincontrol.util.Chat.red(deny));
			ru.admincontrol.util.VanillaTargetGuard.end();
			cir.setReturnValue(0);
		}
	}

	@Inject(method = "execute(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)I", at = @At("HEAD"))
	private void adminControl$tpGuardEnter(com.mojang.brigadier.ParseResults<ServerCommandSource> parse, String command, CallbackInfoReturnable<Integer> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		ru.admincontrol.util.TpGuard.enterCommand(parse == null ? null : parse.getContext().getSource(), command);
	}

	@Inject(method = "execute(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)I", at = @At("RETURN"))
	private void adminControl$tpGuardExit(com.mojang.brigadier.ParseResults<ServerCommandSource> parse, String command, CallbackInfoReturnable<Integer> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		ru.admincontrol.util.TpGuard.exitCommand();
	}

	@Inject(method = "execute(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)I", at = @At("HEAD"))
	private void adminControl$executeTyped(com.mojang.brigadier.ParseResults<ServerCommandSource> parse, String command, CallbackInfoReturnable<Integer> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		if (parse == null) {
			return;
		}
		try {
			ServerCommandSource source = parse.getContext().getSource();
			if (source != null && source.getEntity() instanceof ServerPlayerEntity player) {
				CommandInputLog.typed(player, command);
			}
		} catch (RuntimeException ignored) {
		}
	}

	@Inject(method = "execute(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)I", at = @At("RETURN"))
	private void adminControl$executeDone(com.mojang.brigadier.ParseResults<ServerCommandSource> parse, String command, CallbackInfoReturnable<Integer> cir) {
		if (!ru.admincontrol.util.ModGate.on()) {
			return;
		}
		ru.admincontrol.util.VanillaTargetGuard.end();
		CommandInputLog.endCommand();
	}
}
