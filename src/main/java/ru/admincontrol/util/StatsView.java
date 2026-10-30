package ru.admincontrol.util;

import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.data.PunishmentType;

import java.util.ArrayList;
import java.util.List;

public final class StatsView {
	private StatsView() {
	}

	public static void fillCommon(PlayerRecord record, List<String> labels, List<String> values) {
		labels.add("Баны");
		values.add(String.valueOf(record.countByType(PunishmentType.BAN) + record.countByType(PunishmentType.PERMABAN)));
		labels.add("Варны");
		values.add(String.valueOf(record.countByType(PunishmentType.WARN)));
		labels.add("Кики");
		values.add(String.valueOf(record.countByType(PunishmentType.KICK)));
		labels.add("Устные пред.");
		values.add(String.valueOf(record.countByType(PunishmentType.VERBAL_WARN)));
		labels.add("Jail");
		values.add(String.valueOf(record.countByType(PunishmentType.JAIL)));
		labels.add("Время на сервере");
		values.add(record.playtimeFormatted());
		labels.add("Клан");
		values.add(ClanService.nameOf(record == null ? null : record.clanId));
		PlayFeatures.fillStats(record, labels, values);
		labels.add("Координаты");
		values.add(fmt(record.x) + " " + fmt(record.y) + " " + fmt(record.z));
		labels.add("Мир");
		values.add(record.dimension == null ? "-" : record.dimension);
		labels.add("Сломано блоков");
		values.add(String.valueOf(record.blocksBroken));
		labels.add("Поставлено блоков");
		values.add(String.valueOf(record.blocksPlaced));
		labels.add("Убито мирных");
		values.add(String.valueOf(record.peacefulKills));
		labels.add("Убито монстров");
		values.add(String.valueOf(record.monsterKills));
		labels.add("Смерти");
		values.add(String.valueOf(record.deaths));
		fillCasinoMoney(record, labels, values);
	}

	public static void fillCasinoMoney(PlayerRecord record, List<String> labels, List<String> values) {
		labels.add("Казино: всего игр");
		values.add(String.valueOf(record.casinoGames));
		labels.add("Казино на деньги: игр");
		values.add(String.valueOf(record.casinoMoneyGames));
		labels.add("Казино на предметы: игр");
		values.add(String.valueOf(record.casinoItemGames));
		labels.add("Казино WC: заработано");
		values.add(Money.format(record.casinoWon));
		labels.add("Казино WC: потрачено");
		values.add(Money.format(record.casinoSpent));
		labels.add("Казино предметы: потрачено");
		values.add(String.valueOf(record.casinoItemLost));
		labels.add("Казино предметы: получено");
		values.add(String.valueOf(record.casinoItemReceived));
		labels.add("Казино предметы: чаще всего");
		values.add(CasinoService.topItemLabel(record));
	}

	public static void fillAdmin(PlayerRecord record, ServerPlayerEntity online, List<String> labels, List<String> values) {
		labels.add("Пинг");
		values.add(online == null ? "оффлайн" : online.pingMilliseconds + " мс");
		labels.add("Последний вход");
		values.add(online == null
				? record.lastJoinFormatted()
				: "онлайн, зашёл " + record.lastJoinFormatted());
		labels.add("TPS");
		values.add(ServerTps.formatted());
		float hp = online != null ? online.getHealth() : record.lastHealth;
		int food = online != null ? online.getHungerManager().getFoodLevel() : record.lastHunger;
		int level = online != null ? online.experienceLevel : record.lastKnownLevel;
		labels.add("Здоровье");
		values.add(String.format("%.1f", hp));
		labels.add("Сытость");
		values.add(String.valueOf(food));
		labels.add("Уровень");
		values.add(String.valueOf(level));
		labels.add("IP");
		values.add(record.loginIps == null || record.loginIps.isEmpty() ? "нет данных" : String.join(", ", record.loginIps));
		labels.add("Сила");
		values.add(String.format(java.util.Locale.US, "%.1f", record.power()));
		labels.add("Баланс");
		values.add(Money.format(Economy.balanceOf(record)));
		labels.add("Заработано WC");
		values.add(Money.format(CoinLedger.earned(record)));
		labels.add("Потрачено WC");
		values.add(Money.format(CoinLedger.spent(record)));
		labels.add("Удача");
		values.add(String.format(java.util.Locale.US, "%.1f", record.luck()));
		labels.add("Рюкзак");
		values.add(record.backpackLevel <= 0 ? "нет" : ("ур. " + record.backpackLevel));
	}

	public static List<String> labels() {
		return new ArrayList<>();
	}

	private static String fmt(double v) {
		return String.format(java.util.Locale.US, "%.1f", v);
	}
}
