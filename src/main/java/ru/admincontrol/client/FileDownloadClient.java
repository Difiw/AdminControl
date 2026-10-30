package ru.admincontrol.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.admincontrol.util.PlayerFileService;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Файлы игрока из /filecheck сохраняются на компьютер админа: .minecraft/admincontrol-downloads/<ник>/<папка>/… */
public final class FileDownloadClient {
	public static final String DIR = "admincontrol-downloads";

	private static final Map<String, Job> JOBS = new HashMap<>();
	private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "AdminControl-Download");
		t.setDaemon(true);
		return t;
	});

	private FileDownloadClient() {
	}

	private static final class Job {
		Path file;
		OutputStream out;
		int next;
		long written;
	}

	public static void onChunk(MinecraftClient client, String nick, String folder, String path, int chunk, int total,
			byte[] data, String error) {
		Path base = client.runDirectory.toPath().toAbsolutePath().normalize().resolve(DIR);
		IO.execute(() -> handle(client, base, nick, folder, path, chunk, total, data, error));
	}

	private static void handle(MinecraftClient client, Path base, String nick, String folder, String path, int chunk,
			int total, byte[] data, String error) {
		String key = nick + "\0" + folder + "/" + path;
		String label = nick + ": " + folder + "/" + path;
		if (total < 0) {
			abort(JOBS.remove(key));
			chat(client, Text.literal("Не удалось скачать «" + label + "»: " + error + ".").formatted(Formatting.RED));
			return;
		}
		try {
			Job job = JOBS.get(key);
			if (chunk == 0) {
				abort(job);
				job = new Job();
				Path file = base.resolve(PlayerFileService.safe(nick)).resolve(PlayerFileService.safe(folder))
						.resolve(PlayerFileService.relative(path)).normalize();
				if (!file.startsWith(base)) {
					throw new IOException("недопустимый путь");
				}
				Files.createDirectories(file.getParent());
				job.file = file;
				job.out = Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
						StandardOpenOption.WRITE);
				JOBS.put(key, job);
			}
			if (job == null || chunk != job.next) {
				throw new IOException("части пришли не по порядку");
			}
			job.out.write(data);
			job.next++;
			job.written += data.length;
			if (chunk + 1 >= total) {
				job.out.close();
				JOBS.remove(key);
				done(client, label, job);
			}
		} catch (IOException | RuntimeException ex) {
			abort(JOBS.remove(key));
			chat(client, Text.literal("Не удалось сохранить «" + label + "»: " + ex.getMessage() + ".")
					.formatted(Formatting.RED));
		}
	}

	private static void done(MinecraftClient client, String label, Job job) {
		String file = job.file.toString();
		String dir = job.file.getParent().toString();
		MutableText text = Text.literal("Файл «" + label + "» (" + PlayerFileService.human(job.written)
				+ ") сохранён у вас: ").formatted(Formatting.GREEN);
		text.append(Text.literal(file).styled(s -> s.withColor(Formatting.AQUA).withUnderline(true)
				.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Открыть файл")))));
		text.append(Text.literal(" "));
		text.append(Text.literal("[открыть папку]").styled(s -> s.withColor(Formatting.YELLOW)
				.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, dir))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(dir)))));
		chat(client, text);
	}

	private static void chat(MinecraftClient client, Text text) {
		client.execute(() -> {
			if (client.inGameHud != null) {
				client.inGameHud.getChatHud().addMessage(text);
			}
		});
	}

	private static void abort(Job job) {
		if (job == null) {
			return;
		}
		try {
			if (job.out != null) {
				job.out.close();
			}
			if (job.file != null) {
				Files.deleteIfExists(job.file);
			}
		} catch (IOException ignored) {
		}
	}

	public static void reset() {
		IO.execute(() -> {
			for (Job job : JOBS.values()) {
				abort(job);
			}
			JOBS.clear();
		});
	}
}
