package ru.admincontrol.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.server.command.ServerCommandSource;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.util.ModGate;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.function.Predicate;

/** Команды мода скрыты и недоступны, пока на сервере нет ГА (кроме /add_admin — им назначают ГА). */
public final class CommandGate {
	private static final Set<String> ALWAYS = Set.of("add_admin", "actest");

	private CommandGate() {
	}

	@SuppressWarnings("unchecked")
	public static void apply(CommandDispatcher<ServerCommandSource> dispatcher, Set<CommandNode<ServerCommandSource>> before) {
		Field field;
		try {
			field = CommandNode.class.getDeclaredField("requirement");
			field.setAccessible(true);
		} catch (ReflectiveOperationException | RuntimeException e) {
			AdminControlMod.LOGGER.error("Не удалось подключить блокировку команд без ГА", e);
			return;
		}
		for (CommandNode<ServerCommandSource> node : dispatcher.getRoot().getChildren()) {
			if (before.contains(node) || ALWAYS.contains(node.getName())) {
				continue;
			}
			try {
				Predicate<ServerCommandSource> original = (Predicate<ServerCommandSource>) field.get(node);
				Predicate<ServerCommandSource> gated = source -> ModGate.on() && (original == null || original.test(source));
				field.set(node, gated);
			} catch (ReflectiveOperationException | RuntimeException e) {
				AdminControlMod.LOGGER.error("Не удалось закрыть команду /{} без ГА", node.getName(), e);
			}
		}
	}
}
