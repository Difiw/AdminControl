package ru.admincontrol.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.util.CasinoService;

public class CasinoItemScreenHandler extends ScreenHandler {
	public static final int PAIR_M = 0;
	public static final int PAIR_P = 1;
	public static final int TRIPLE_M = 2;
	public static final int TRIPLE_P = 3;
	public static final int JACK_M = 4;
	public static final int JACK_P = 5;
	public static final int PAIR_PRIZE_M = 6;
	public static final int PAIR_PRIZE_P = 7;
	public static final int TRIPLE_PRIZE_M = 8;
	public static final int TRIPLE_PRIZE_P = 9;
	public static final int JACK_PRIZE_M = 10;
	public static final int JACK_PRIZE_P = 11;
	private final PropertyDelegate props = new ArrayPropertyDelegate(6);
	public final String symbols;
	public final String specials;

	public static CasinoItemScreenHandler createClient(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		String symbols = buf.readString(256);
		String specials = buf.readString(256);
		int[] data = new int[6];
		for (int i = 0; i < 6; i++) {
			data[i] = buf.readVarInt();
		}
		return new CasinoItemScreenHandler(syncId, symbols, specials, data);
	}

	public static CasinoItemScreenHandler of(int syncId) {
		var s = DataManager.get().settings();
		return new CasinoItemScreenHandler(syncId, s.casinoItemSymbols, s.casinoSpecials, snapshot());
	}

	public static void write(PacketByteBuf buf) {
		var s = DataManager.get().settings();
		buf.writeString(s.casinoItemSymbols == null ? "" : s.casinoItemSymbols, 256);
		buf.writeString(s.casinoSpecials == null ? "" : s.casinoSpecials, 256);
		for (int v : snapshot()) {
			buf.writeVarInt(v);
		}
	}

	private static int[] snapshot() {
		var s = DataManager.get().settings();
		return new int[] {
				s.casinoPairChance, s.casinoTripleChance, s.casinoJackpotChance,
				s.casinoPairPrize, s.casinoTriplePrize, s.casinoJackpotPrize
		};
	}

	public CasinoItemScreenHandler(int syncId, String symbols, String specials, int[] data) {
		super(ModScreenHandlers.CASINO_ITEM, syncId);
		this.symbols = symbols == null ? "" : symbols;
		this.specials = specials == null ? "" : specials;
		this.addProperties(this.props);
		for (int i = 0; i < 6; i++) {
			this.props.set(i, data != null && i < data.length ? data[i] : 0);
		}
	}

	public int pairChance() {
		return this.props.get(0);
	}

	public int tripleChance() {
		return this.props.get(1);
	}

	public int jackChance() {
		return this.props.get(2);
	}

	public int pairPrize() {
		return this.props.get(3);
	}

	public int triplePrize() {
		return this.props.get(4);
	}

	public int jackPrize() {
		return this.props.get(5);
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!(player instanceof ServerPlayerEntity serverPlayer) || DataManager.get() == null) {
			return false;
		}
		if (DataManager.get().getAdminLevel(serverPlayer) < AdminRanks.DEV) {
			return false;
		}
		switch (id) {
			case PAIR_M -> CasinoService.shiftItemChance("pair", -1);
			case PAIR_P -> CasinoService.shiftItemChance("pair", 1);
			case TRIPLE_M -> CasinoService.shiftItemChance("triple", -1);
			case TRIPLE_P -> CasinoService.shiftItemChance("triple", 1);
			case JACK_M -> CasinoService.shiftItemChance("jackpot", -1);
			case JACK_P -> CasinoService.shiftItemChance("jackpot", 1);
			case PAIR_PRIZE_M -> CasinoService.shiftItemChance("pairPrize", -1);
			case PAIR_PRIZE_P -> CasinoService.shiftItemChance("pairPrize", 1);
			case TRIPLE_PRIZE_M -> CasinoService.shiftItemChance("triplePrize", -1);
			case TRIPLE_PRIZE_P -> CasinoService.shiftItemChance("triplePrize", 1);
			case JACK_PRIZE_M -> CasinoService.shiftItemChance("jackpotPrize", -1);
			case JACK_PRIZE_P -> CasinoService.shiftItemChance("jackpotPrize", 1);
			default -> {
				return false;
			}
		}
		int[] data = snapshot();
		for (int i = 0; i < 6; i++) {
			this.props.set(i, data[i]);
		}
		return true;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return true;
	}
}
