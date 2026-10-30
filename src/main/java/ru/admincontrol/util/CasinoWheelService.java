package ru.admincontrol.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import ru.admincontrol.block.CasinoMachineBlock;
import ru.admincontrol.block.CasinoMachineBlockEntity;
import ru.admincontrol.block.ModBlocks;
import ru.admincontrol.data.ActionKind;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.net.ModPackets;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * «Рулетка удачи» игрового автомата: шесть цветных секторов, на автомате отмечают от одного до трёх и ставят World coin.
 * Выбор и ставка принадлежат автомату: любой игрок у него видит их и может крутить; пока колесо крутится, автомат занят.
 * Сектор выбирает сервер с равными шансами; чем меньше отмечено секторов, тем выше множитель.
 */
public final class CasinoWheelService {
	public static final int SECTORS = 6;
	public static final int MAX_SELECTED = 3;
	public static final String[] NAMES = {"Жёлтый", "Зелёный", "Голубой", "Фиолетовый", "Розовый", "Оранжевый"};
	/** Множитель в десятых по числу отмеченных секторов (1…3). */
	private static final int[] MULT_TENTHS = {0, 50, 25, 20};
	public static final int MAX_BET = 100_000_000;
	/** Сколько на клиенте крутится колесо; всё это время автомат занят. */
	public static final long SPIN_MS = 4_700;
	private static final double REACH_SQ = 8 * 8;

	private record Machine(RegistryKey<World> world, BlockPos pos) {
	}

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final Map<UUID, Machine> OPEN = new ConcurrentHashMap<>();
	private static final Map<Machine, Long> BUSY = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> SENT_BALANCE = new ConcurrentHashMap<>();
	/** Автоматы, сломавшиеся на прокрутке: дым и звук поломки — когда колесо докрутится. */
	private static final Map<Machine, Long> PENDING_BREAK = new ConcurrentHashMap<>();

	private CasinoWheelService() {
	}

	public static void register() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			OPEN.remove(handler.player.getUuid());
			SENT_BALANCE.remove(handler.player.getUuid());
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			BalanceBoard.releaseHolds(server);
			if (!PENDING_BREAK.isEmpty()) {
				long now = System.currentTimeMillis();
				PENDING_BREAK.entrySet().removeIf(entry -> {
					if (entry.getValue() > now) {
						return false;
					}
					ServerWorld world = server.getWorld(entry.getKey().world());
					if (world != null) {
						CasinoRepairService.breakEffects(world, entry.getKey().pos());
					}
					return true;
				});
			}
			if (server.getTicks() % 5 != 0 || OPEN.isEmpty() || DataManager.get() == null) {
				return;
			}
			for (UUID id : OPEN.keySet()) {
				ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
				if (player != null) {
					syncBalance(player);
				}
			}
		});
	}

	/** Баланс в окне автомата: шлётся, когда он изменился (ставка, выигрыш, переводы, продажи). */
	private static void syncBalance(ServerPlayerEntity player) {
		if (!ServerPlayNetworking.canSend(player, ModPackets.CASINO_BALANCE)) {
			return;
		}
		int balance = Economy.balance(player);
		Integer sent = SENT_BALANCE.get(player.getUuid());
		if (sent != null && sent == balance) {
			return;
		}
		SENT_BALANCE.put(player.getUuid(), balance);
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(balance);
		ServerPlayNetworking.send(player, ModPackets.CASINO_BALANCE, buf);
	}

	/** Автомат сломался на прокрутке, а колесо ещё крутится: поломку пока не показываем. */
	public static boolean breakPending(World world, BlockPos base) {
		return PENDING_BREAK.containsKey(new Machine(world.getRegistryKey(), base.toImmutable()));
	}

	public static int multTenths(int selected) {
		return selected >= 1 && selected < MULT_TENTHS.length ? MULT_TENTHS[selected] : 0;
	}

	public static void open(ServerPlayerEntity player, BlockPos base) {
		String deny = deny(player);
		if (deny != null) {
			Chat.send(player, Chat.red(deny));
			return;
		}
		if (!ServerPlayNetworking.canSend(player, ModPackets.CASINO_WHEEL)) {
			Chat.send(player, Chat.red("Для игрового автомата нужен мод Admin Control на клиенте."));
			return;
		}
		CasinoMachineBlockEntity machine = machine(player.getServerWorld(), base);
		if (machine == null) {
			return;
		}
		Machine key = new Machine(player.getServerWorld().getRegistryKey(), base.toImmutable());
		if (PENDING_BREAK.containsKey(key)) {
			player.sendMessage(Chat.yellow("Автомат крутится — подождите."), true);
			return;
		}
		if (machine.broken()) {
			CasinoRepairService.explain(player, machine);
			return;
		}
		OPEN.put(player.getUuid(), key);
		int balance = Economy.balance(player);
		SENT_BALANCE.put(player.getUuid(), balance);
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(base);
		buf.writeVarInt(balance);
		buf.writeVarInt(Math.max(1, CasinoService.minBet()));
		buf.writeVarInt(MAX_BET);
		buf.writeByte(machine.mask());
		buf.writeVarInt(machine.bet());
		buf.writeVarInt((int) Math.max(0, busyLeft(key)));
		ServerPlayNetworking.send(player, ModPackets.CASINO_WHEEL, buf);
		player.getServerWorld().playSound(null, base, SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.BLOCKS, 0.8f, 1.4f);
	}

	/** Игрок у автомата поменял отмеченные сектора или ставку. */
	public static void select(ServerPlayerEntity player, BlockPos pos, int mask, int bet) {
		Machine key = viewing(player, pos);
		CasinoMachineBlockEntity machine = key == null ? null : machine(player.getServerWorld(), key.pos());
		if (machine == null) {
			return;
		}
		String error = null;
		mask &= (1 << SECTORS) - 1;
		if (machine.broken()) {
			error = "Автомат сломан — нужен ремонт.";
		} else if (busyLeft(key) > 0) {
			error = "Автомат крутится — подождите.";
		} else if (Integer.bitCount(mask) > MAX_SELECTED) {
			error = "Можно выбрать не больше трёх секторов.";
		}
		if (error != null) {
			reject(player, error);
			sendState(player, key, machine, null);
			return;
		}
		machine.set(mask, Math.max(Math.max(1, CasinoService.minBet()), Math.min(MAX_BET, bet)));
		broadcast(player, key, machine, null);
	}

	public static void spin(ServerPlayerEntity player, BlockPos pos, int mask, int bet) {
		if (player == null) {
			return;
		}
		String deny = deny(player);
		if (deny != null) {
			reject(player, deny);
			return;
		}
		Machine key = viewing(player, pos);
		CasinoMachineBlockEntity machine = key == null ? null : machine(player.getServerWorld(), key.pos());
		if (machine == null) {
			reject(player, "Подойдите к игровому автомату.");
			return;
		}
		if (busyLeft(key) > 0) {
			reject(player, "Автомат уже крутится — подождите.");
			return;
		}
		if (machine.broken()) {
			reject(player, "Автомат сломан — нужен ремонт.");
			return;
		}
		if (machine.mask() != (mask & ((1 << SECTORS) - 1)) || machine.bet() != bet) {
			reject(player, "Выбор на автомате изменился. Проверьте сектора и ставку.");
			sendState(player, key, machine, null);
			return;
		}
		mask = machine.mask();
		bet = machine.bet();
		int selected = Integer.bitCount(mask);
		if (selected == 0) {
			reject(player, "Выберите хотя бы один сектор.");
			return;
		}
		if (selected > MAX_SELECTED) {
			reject(player, "Можно выбрать не больше трёх секторов.");
			return;
		}
		int min = Math.max(1, CasinoService.minBet());
		if (bet < min) {
			reject(player, "Ставка должна быть не меньше " + Money.wc(min) + ".");
			return;
		}
		long now = System.currentTimeMillis();
		PlayerRecord record = DataManager.get().progress(player);
		int delay = CasinoService.moneyDelayMs();
		long since = now - Math.max(record.lastCasinoMoneyMs, record.lastCasinoGameMs);
		if (delay > 0 && since < delay) {
			reject(player, "Подождите " + Math.max(1, (delay - since + 999) / 1000) + " сек. перед следующей ставкой.");
			return;
		}
		if (!Economy.takeCurrency(player, bet, "казино: рулетка, ставка")) {
			reject(player, "Недостаточно World coin.");
			return;
		}
		BalanceBoard.refresh(player, true);
		BalanceBoard.hold(player, SPIN_MS);
		BUSY.put(key, now + SPIN_MS);
		int sector = RANDOM.nextInt(SECTORS);
		boolean win = (mask >> sector & 1) != 0;
		int mult = multTenths(selected);
		int payout = win ? (int) Math.min(Integer.MAX_VALUE, (long) bet * mult / 10) : 0;
		record.lastCasinoGameMs = now;
		record.lastCasinoMoneyMs = now;
		record.casinoGames++;
		record.casinoMoneyGames++;
		record.casinoSpent += bet;
		record.casinoWon += payout;
		if (payout > record.casinoBiggestWin) {
			record.casinoBiggestWin = payout;
		}
		if (win && mult >= 20) {
			double from = record.luck();
			record.luck = Math.round((from + 0.1) * 10.0) / 10.0;
			ProgressLog.luck(record, from, record.luck(), ProgressLog.CASINO);
		}
		if (payout > 0) {
			Economy.giveCurrency(player, payout, "казино: рулетка, выигрыш ×" + CasinoService.formatMult(mult));
		}
		boolean broke = CasinoRepairService.rollBreak(player, machine);
		if (broke) {
			PENDING_BREAK.put(key, now + SPIN_MS);
		}
		DataManager.get().saveAll();
		PlayFeatures.onCasino(player);
		Achievements.onCasinoPlayed(player, bet, payout);
		SkillTreeService.onCasinoSpin(player, bet);
		AdminNotify.playerAction(player, ActionKind.CASINO,
				"рулетка: ставка " + Money.format(bet) + " на " + sectorList(mask) + ", выпал " + NAMES[sector].toLowerCase()
						+ (win ? ", выигрыш ×" + CasinoService.formatMult(mult) + " = " + Money.format(payout) : ", проигрыш"),
				win);

		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBoolean(true);
		buf.writeString("", 200);
		buf.writeByte(sector);
		buf.writeByte(mask);
		buf.writeVarInt(mult);
		buf.writeVarInt(bet);
		buf.writeVarInt(payout);
		int balance = Economy.balance(player);
		buf.writeVarInt(balance);
		buf.writeBoolean(broke);
		ServerPlayNetworking.send(player, ModPackets.CASINO_RESULT, buf);
		SENT_BALANCE.put(player.getUuid(), balance);
		Spin spin = new Spin(sector, player.getGameProfile().getName(), payout, broke);
		for (ServerPlayerEntity other : viewers(player, key)) {
			if (other != player) {
				sendState(other, key, machine, spin);
			}
		}
	}

	private record Spin(int sector, String nick, int payout, boolean broke) {
	}

	private static Machine viewing(ServerPlayerEntity player, BlockPos pos) {
		Machine key = OPEN.get(player.getUuid());
		if (key == null || !key.pos().equals(pos) || key.world() != player.getServerWorld().getRegistryKey()
				|| player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5) > REACH_SQ) {
			return null;
		}
		return key;
	}

	private static CasinoMachineBlockEntity machine(ServerWorld world, BlockPos base) {
		BlockState state = world.getBlockState(base);
		if (!state.isOf(ModBlocks.CASINO_MACHINE)) {
			return null;
		}
		BlockEntity entity = world.getBlockEntity(base);
		if (entity instanceof CasinoMachineBlockEntity machine) {
			return machine;
		}
		CasinoMachineBlockEntity created = (CasinoMachineBlockEntity) ((CasinoMachineBlock) state.getBlock()).createBlockEntity(base, state);
		if (created != null) {
			world.addBlockEntity(created);
		}
		return created;
	}

	private static long busyLeft(Machine key) {
		Long until = BUSY.get(key);
		if (until == null) {
			return 0;
		}
		long left = until - System.currentTimeMillis();
		if (left <= 0) {
			BUSY.remove(key);
		}
		return left;
	}

	private static Iterable<ServerPlayerEntity> viewers(ServerPlayerEntity source, Machine key) {
		java.util.List<ServerPlayerEntity> out = new java.util.ArrayList<>();
		if (source.getServer() == null) {
			return out;
		}
		for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
			if (key.equals(OPEN.get(player.getUuid())) && viewing(player, key.pos()) != null
					&& ServerPlayNetworking.canSend(player, ModPackets.CASINO_STATE)) {
				out.add(player);
			}
		}
		return out;
	}

	private static void broadcast(ServerPlayerEntity source, Machine key, CasinoMachineBlockEntity machine, Spin spin) {
		for (ServerPlayerEntity player : viewers(source, key)) {
			sendState(player, key, machine, spin);
		}
	}

	private static void sendState(ServerPlayerEntity player, Machine key, CasinoMachineBlockEntity machine, Spin spin) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(key.pos());
		buf.writeByte(machine.mask());
		buf.writeVarInt(machine.bet());
		buf.writeBoolean(spin != null);
		if (spin != null) {
			buf.writeByte(spin.sector());
			buf.writeString(spin.nick(), 16);
			buf.writeVarInt(spin.payout());
			buf.writeBoolean(spin.broke());
		}
		ServerPlayNetworking.send(player, ModPackets.CASINO_STATE, buf);
	}

	private static String deny(ServerPlayerEntity player) {
		if (!ModGate.on() || DataManager.get() == null) {
			return "Игровой автомат не работает: Admin Control отключён.";
		}
		if (NpcService.isIdleNpc(player)) {
			return "NPC не может играть в казино.";
		}
		if (AuthService.pending(player)) {
			return "Сначала войдите в аккаунт.";
		}
		ActivityLock.sync(player);
		if (ActivityLock.blocked(player)) {
			return "Казино недоступно на МП и тренировке.";
		}
		if (!CasinoService.enabled()) {
			return "Казино сейчас выключено.";
		}
		if (DataManager.get().progress(player).casinoBanned) {
			return "Вам запрещено играть в казино.";
		}
		return null;
	}

	private static void reject(ServerPlayerEntity player, String reason) {
		if (!ServerPlayNetworking.canSend(player, ModPackets.CASINO_RESULT)) {
			Chat.send(player, Chat.red(reason));
			return;
		}
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBoolean(false);
		buf.writeString(reason, 200);
		buf.writeByte(-1);
		buf.writeByte(0);
		buf.writeVarInt(0);
		buf.writeVarInt(0);
		buf.writeVarInt(0);
		buf.writeVarInt(DataManager.get() == null ? 0 : Economy.balance(player));
		buf.writeBoolean(false);
		ServerPlayNetworking.send(player, ModPackets.CASINO_RESULT, buf);
	}

	private static String sectorList(int mask) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < SECTORS; i++) {
			if ((mask >> i & 1) != 0) {
				if (!out.isEmpty()) {
					out.append(", ");
				}
				out.append(NAMES[i].toLowerCase());
			}
		}
		return out.toString();
	}
}
