package ru.admincontrol.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import ru.admincontrol.AdminControlMod;
import ru.admincontrol.data.DataManager;

/** Ошибки (уровень ERROR и выше) из логов сервера — в баг-лист, если это включено в /anticheat. */
public final class ServerErrorWatch extends AbstractAppender {
	private static final ThreadLocal<Boolean> BUSY = ThreadLocal.withInitial(() -> false);
	private static boolean installed;

	private ServerErrorWatch() {
		super("AdminControlBugList", null, null, true, Property.EMPTY_ARRAY);
	}

	public static synchronized void install() {
		if (installed) {
			return;
		}
		try {
			LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
			ServerErrorWatch appender = new ServerErrorWatch();
			appender.start();
			ctx.getConfiguration().getRootLogger().addAppender(appender, Level.ERROR, null);
			ctx.updateLoggers();
			installed = true;
		} catch (Throwable t) {
			AdminControlMod.LOGGER.warn("Баг-лист: не удалось подключиться к логам сервера — ошибки сервера записываться не будут", t);
		}
	}

	/** Выполнить без записи собственных ошибок в баг-лист (иначе сбой записи файла зациклится). */
	static void quietly(Runnable action) {
		boolean was = BUSY.get();
		BUSY.set(true);
		try {
			action.run();
		} finally {
			BUSY.set(was);
		}
	}

	@Override
	public void append(LogEvent event) {
		if (BUSY.get() || event == null || !event.getLevel().isMoreSpecificThan(Level.ERROR)) {
			return;
		}
		DataManager data = DataManager.get();
		if (data == null || data.settings() == null || !data.settings().bugServerErrors) {
			return;
		}
		String logger = event.getLoggerName() == null ? "" : event.getLoggerName();
		String message = event.getMessage() == null ? "" : event.getMessage().getFormattedMessage();
		Throwable thrown = event.getThrown();
		StringBuilder desc = new StringBuilder();
		if (!logger.isEmpty()) {
			desc.append('[').append(logger.substring(logger.lastIndexOf('.') + 1)).append("] ");
		}
		desc.append(message == null ? "" : message);
		// Object identity hashes ("class_2830@44455e0c") differ per packet: without removing them every repeat of one error looked new.
		String signature = logger + "|" + (message == null ? "" : (message.length() > 160 ? message.substring(0, 160) : message).replaceAll("@[0-9a-f]{5,8}", "@#"));
		if (thrown != null) {
			desc.append(" — ").append(thrown.getClass().getSimpleName());
			if (thrown.getMessage() != null) {
				desc.append(": ").append(thrown.getMessage());
			}
			StackTraceElement[] trace = thrown.getStackTrace();
			if (trace != null && trace.length > 0) {
				desc.append(" (");
				for (int i = 0; i < Math.min(4, trace.length); i++) {
					String cls = trace[i].getClassName();
					desc.append(i == 0 ? "" : " <- ").append(cls.substring(cls.lastIndexOf('.') + 1)).append('.').append(trace[i].getMethodName()).append(':').append(trace[i].getLineNumber());
				}
				desc.append(')');
			}
			ru.admincontrol.cw.CwErrorLog.append(logger, message, thrown);
			signature += "|" + thrown.getClass().getName();
		}
		String sig = signature;
		String text = desc.toString();
		quietly(() -> BugList.serverError(sig, text));
	}
}
