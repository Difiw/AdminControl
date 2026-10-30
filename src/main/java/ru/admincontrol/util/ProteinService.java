package ru.admincontrol.util;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.skill.SkillEffects;
import ru.admincontrol.skill.SkillTree;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Временные бафы протеиновых батончиков и коктейлей. */
public final class ProteinService {
	public static final int BAR_SEC = 120;
	public static final int MIXED_SEC = 180;

	private static final Map<UUID, Buff> POWER = new ConcurrentHashMap<>();
	private static final Map<UUID, Buff> TRAIN = new ConcurrentHashMap<>();

	private ProteinService() {
	}

	public static final class Buff {
		public final double amount;
		public final long endsAt;
		public final String label;

		public Buff(double amount, long endsAt, String label) {
			this.amount = amount;
			this.endsAt = endsAt;
			this.label = label == null ? "" : label;
		}

		public boolean alive() {
			return System.currentTimeMillis() < this.endsAt;
		}

		public int leftSec() {
			return (int) Math.max(0L, (this.endsAt - System.currentTimeMillis() + 999L) / 1000L);
		}
	}

	public static double effectivePower(ServerPlayerEntity player) {
		if (player == null || DataManager.get() == null) {
			return 1.0;
		}
		PlayerRecord record = DataManager.get().progress(player);
		return Math.round((record.power() + powerBonus(player)) * 10.0) / 10.0;
	}

	public static double powerBonus(ServerPlayerEntity player) {
		Buff buff = current(POWER, player);
		return buff == null ? 0 : buff.amount;
	}

	/** Множитель WC за тренировку при активном коктейле (1.0 = без бафа). */
	public static double trainPayMult(ServerPlayerEntity player) {
		Buff buff = current(TRAIN, player);
		double base = buff == null ? 1.0 : 1.0 + buff.amount;
		double skill = SkillEffects.server(player, SkillTree.Stat.TRAIN_PAY);
		return base * SkillTree.more(skill);
	}

	public static int boostTrainingCoins(ServerPlayerEntity player, int coins) {
		if (coins <= 0) {
			return coins;
		}
		return Math.max(0, (int) Math.round(coins * trainPayMult(player)));
	}

	public static int barDurationSec(ServerPlayerEntity player, boolean mixed) {
		int base = mixed ? MIXED_SEC : BAR_SEC;
		double pct = SkillEffects.server(player, SkillTree.Stat.PROTEIN_TIME);
		return Math.max(30, (int) Math.round(base * SkillTree.more(pct)));
	}

	public static void applyPower(ServerPlayerEntity player, double amount, int seconds, String label) {
		if (player == null || amount <= 0 || seconds <= 0) {
			return;
		}
		long ends = System.currentTimeMillis() + seconds * 1000L;
		POWER.put(player.getUuid(), new Buff(amount, ends, label));
		Chat.send(player, Chat.green("Протеин: сила +" + String.format(java.util.Locale.US, "%.1f", amount)
				+ " на " + formatSec(seconds) + "."));
		player.sendMessage(Text.literal("Сила +" + String.format(java.util.Locale.US, "%.1f", amount)
				+ " · " + formatSec(seconds)).formatted(Formatting.GOLD), true);
	}

	/** Коктейль: amount — доля бонуса к WC тренировки (0.25 = +25%). */
	public static void applyTrainPay(ServerPlayerEntity player, double amount, int seconds, String label) {
		if (player == null || amount <= 0 || seconds <= 0) {
			return;
		}
		long ends = System.currentTimeMillis() + seconds * 1000L;
		TRAIN.put(player.getUuid(), new Buff(amount, ends, label));
		Chat.send(player, Chat.green("Коктейль: +" + (int) Math.round(amount * 100) + "% WC с тренировок на "
				+ formatSec(seconds) + "."));
		player.sendMessage(Text.literal("Тренировки +" + (int) Math.round(amount * 100) + "% WC · "
				+ formatSec(seconds)).formatted(Formatting.AQUA), true);
	}

	public static Buff powerBuff(ServerPlayerEntity player) {
		return current(POWER, player);
	}

	public static Buff trainBuff(ServerPlayerEntity player) {
		return current(TRAIN, player);
	}

	public static void clear(UUID uuid) {
		if (uuid != null) {
			POWER.remove(uuid);
			TRAIN.remove(uuid);
		}
	}

	private static Buff current(Map<UUID, Buff> map, ServerPlayerEntity player) {
		if (player == null) {
			return null;
		}
		Buff buff = map.get(player.getUuid());
		if (buff == null) {
			return null;
		}
		if (!buff.alive()) {
			map.remove(player.getUuid());
			return null;
		}
		return buff;
	}

	private static String formatSec(int sec) {
		if (sec >= 60 && sec % 60 == 0) {
			return (sec / 60) + " мин";
		}
		if (sec >= 60) {
			return (sec / 60) + " мин " + (sec % 60) + " с";
		}
		return sec + " с";
	}
}
