package ru.admincontrol.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import ru.admincontrol.item.ModItems;
import ru.admincontrol.net.ModPackets;
import ru.admincontrol.util.CasinoWheelService;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * «Рулетка удачи» игрового автомата: слева выбор секторов, в центре колесо, справа шанс, множитель и возможный
 * выигрыш, снизу ставка и «Крутить!». Сектор выбирает сервер; колесо докручивается до него, затем окно «Результаты».
 * Выбор и ставка принадлежат автомату: изменения видят все игроки у него, чужое вращение тоже показывается.
 */
public class CasinoWheelScreen extends Screen {
	private static final int W = 372;
	private static final int H = 214;
	private static final int SIDE = 110;
	private static final int R = 52;
	private static final int SEGMENTS = 14;
	private static final long SPIN_MS = 4200;
	private static final long REVEAL_MS = 450;
	private static final long TIMEOUT_MS = 5000;
	private static final long ERROR_MS = 4000;
	private static final int[] COLORS = {0xFFF2CF2E, 0xFF2BC84A, 0xFF3D8EF5, 0xFFA85CF0, 0xFFF052B4, 0xFFFF9822};
	private static final int WINDOW = 0xFF2A2A2A;
	private static final int PANEL = 0xFF313131;
	private static final int CELL = 0xFF3B3B3B;
	private static final int EDGE = 0xFF1B1B1B;
	private static final int MUTED = 0xFF9E9E9E;
	private static final String HINT = "Выбери сектор и сделай ставку, чем меньше выбранных секторов, тем выше множитель!";

	private final ItemStack coin = new ItemStack(ModItems.WORLD_COIN);
	private final Random random = new Random();
	private final BlockPos pos;
	private int mask;
	private String betText;
	private long busyUntil;
	private State other;
	private String otherText = "";
	private int otherColor;
	private long otherAt;
	private int balance;
	private int liveBalance;
	private int minBet;
	private int maxBet;
	private float rot;
	private float spinFrom;
	private float spinTo;
	private long spinStart = -1;
	private boolean waiting;
	private long waitingSince;
	private Result result;
	private boolean showResult;
	private long resultAt;
	/** Автомат сломался на докрутившемся колесе: окно закрывается на ближайшем тике. */
	private boolean eject;
	private long revealAt;
	private int lastTick;
	private float kick;
	private boolean editing;
	/** Автомат сломался на последней прокрутке (показывается, когда колесо остановится). */
	private boolean broken;
	private String error = "";
	private long errorAt;
	private long lastFrame = Util.getMeasuringTimeMs();
	private int x0;
	private int y0;

	public record Result(boolean ok, String error, int sector, int mask, int mult, int bet, int payout, int balance, boolean broke) {
		public static Result read(PacketByteBuf buf) {
			return new Result(buf.readBoolean(), buf.readString(200), buf.readByte(), buf.readByte(),
					buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
		}

		boolean win() {
			return this.payout > 0;
		}
	}

	public record Opened(BlockPos pos, int balance, int minBet, int maxBet, int mask, int bet, int busyMs) {
		public static Opened read(PacketByteBuf buf) {
			return new Opened(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByte(),
					buf.readVarInt(), buf.readVarInt());
		}
	}

	/** Состояние автомата от сервера; {@code spin} — его только что крутил другой игрок. */
	public record State(BlockPos pos, int mask, int bet, boolean spin, int sector, String nick, int payout, boolean broke) {
		public static State read(PacketByteBuf buf) {
			BlockPos pos = buf.readBlockPos();
			int mask = buf.readByte();
			int bet = buf.readVarInt();
			boolean spin = buf.readBoolean();
			if (!spin) {
				return new State(pos, mask, bet, false, -1, "", 0, false);
			}
			return new State(pos, mask, bet, true, buf.readByte(), buf.readString(16), buf.readVarInt(), buf.readBoolean());
		}
	}

	private CasinoWheelScreen(Opened opened) {
		super(Text.literal("Рулетка удачи"));
		this.pos = opened.pos();
		this.rot = this.random.nextInt(360);
		this.lastTick = sectorAt(this.rot);
		apply(opened);
	}

	private void apply(Opened opened) {
		this.balance = opened.balance();
		this.liveBalance = opened.balance();
		this.minBet = opened.minBet();
		this.maxBet = opened.maxBet();
		this.mask = opened.mask();
		this.betText = Integer.toString(opened.bet());
		this.busyUntil = opened.busyMs() > 0 ? Util.getMeasuringTimeMs() + opened.busyMs() : 0;
		this.broken = false;
	}

	public static void open(MinecraftClient client, Opened opened) {
		if (client.currentScreen instanceof CasinoWheelScreen screen && screen.pos.equals(opened.pos())) {
			if (!screen.busy()) {
				screen.apply(opened);
			}
			return;
		}
		client.setScreen(new CasinoWheelScreen(opened));
	}

	public static void onBalance(MinecraftClient client, int balance) {
		if (client.currentScreen instanceof CasinoWheelScreen screen) {
			screen.liveBalance = balance;
			if (!screen.revealPending()) {
				screen.balance = balance;
			}
		}
	}

	/** Своё вращение ещё не закончилось: выигрыш на балансе показывается вместе с результатом. */
	private boolean revealPending() {
		return this.result != null && !this.showResult;
	}

	public static void onState(MinecraftClient client, State state) {
		if (client.currentScreen instanceof CasinoWheelScreen screen && screen.pos.equals(state.pos())) {
			screen.applyState(state);
		}
	}

	private void applyState(State state) {
		this.mask = state.mask();
		if (!this.editing) {
			this.betText = Integer.toString(state.bet());
		}
		if (!state.spin() || this.spinStart >= 0) {
			return;
		}
		this.other = state;
		this.otherText = "";
		this.showResult = false;
		this.result = null;
		this.busyUntil = Util.getMeasuringTimeMs() + SPIN_MS + REVEAL_MS;
		startSpin(state.sector());
	}

	public static void onResult(MinecraftClient client, Result result) {
		if (client.currentScreen instanceof CasinoWheelScreen screen) {
			screen.accept(result);
		} else if (client.player != null) {
			client.player.sendMessage(result.ok() ? summary(result) : Text.literal(result.error()).formatted(Formatting.RED), false);
		}
	}

	private static Text summary(Result r) {
		String name = CasinoWheelService.NAMES[Math.floorMod(r.sector(), CasinoWheelService.SECTORS)];
		MutableText text = r.win()
				? Text.literal("Рулетка удачи: выпал " + name + " — выигрыш " + money(r.payout()) + " WC").formatted(Formatting.GREEN)
				: Text.literal("Рулетка удачи: выпал " + name + " — неудача, ставка " + money(r.bet()) + " WC").formatted(Formatting.RED);
		if (r.broke()) {
			text.append(Text.literal(". Автомат сломался — нужен ремонт.").formatted(Formatting.GOLD));
		}
		return text;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.eject || this.client == null) {
			return;
		}
		this.eject = false;
		if (this.result == null && this.other != null && this.client.player != null) {
			this.client.player.sendMessage(Text.literal("Рулетка удачи: " + this.otherText).formatted(Formatting.GRAY)
					.append(Text.literal(". Автомат сломался — нужен ремонт.").formatted(Formatting.GOLD)), false);
		}
		close();
	}

	@Override
	public void close() {
		finishEditing();
		if (this.result != null && !this.showResult && this.client != null && this.client.player != null) {
			this.client.player.sendMessage(summary(this.result), false);
		}
		super.close();
	}

	private boolean busy() {
		return this.waiting || this.spinStart >= 0 || (this.result != null && !this.showResult)
				|| Util.getMeasuringTimeMs() < this.busyUntil;
	}

	private void accept(Result r) {
		this.waiting = false;
		this.liveBalance = r.balance();
		if (!r.ok()) {
			this.balance = r.balance();
			fail(r.error());
			return;
		}
		this.balance = Math.max(0, r.balance() - r.payout());
		this.result = r;
		this.other = null;
		this.otherText = "";
		this.showResult = false;
		startSpin(r.sector());
	}

	private void startSpin(int sector) {
		float local = sector * 60f + 30f + (this.random.nextFloat() - 0.5f) * 40f;
		float delta = Math.floorMod((int) Math.floor(-local - this.rot), 360);
		this.spinFrom = this.rot;
		this.spinTo = this.rot + 5 * 360f + delta;
		this.spinStart = Util.getMeasuringTimeMs();
	}

	private void sendSelect() {
		if (!ClientPlayNetworking.canSend(ModPackets.CASINO_SELECT)) {
			return;
		}
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(this.pos);
		buf.writeByte(this.mask);
		buf.writeVarInt(bet());
		ClientPlayNetworking.send(ModPackets.CASINO_SELECT, buf);
	}

	private void finishEditing() {
		if (this.editing) {
			this.editing = false;
			setBet(bet());
		}
	}

	private void fail(String text) {
		this.error = text;
		this.errorAt = Util.getMeasuringTimeMs();
		sound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8f, 0.9f);
	}

	private static int sectorAt(float rot) {
		float local = ((-rot) % 360f + 360f) % 360f;
		return Math.min(CasinoWheelService.SECTORS - 1, (int) (local / 60f));
	}

	private int bet() {
		if (betText.isEmpty()) {
			return 0;
		}
		try {
			return (int) Math.min(this.maxBet, Long.parseLong(betText));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private void setBet(long value) {
		long v = Math.max(this.minBet, Math.min(this.maxBet, value));
		this.betText = Long.toString(v);
		sendSelect();
	}

	private void trySpin() {
		if (busy()) {
			return;
		}
		if (this.broken) {
			fail("Автомат сломан — нужен ремонт.");
			return;
		}
		finishEditing();
		int bet = bet();
		if (this.mask == 0) {
			fail("Выберите хотя бы один сектор.");
			return;
		}
		if (bet < this.minBet) {
			fail("Ставка должна быть не меньше " + money(this.minBet) + " WC.");
			return;
		}
		if (bet > this.balance) {
			fail("Недостаточно World coin.");
			return;
		}
		if (!ClientPlayNetworking.canSend(ModPackets.CASINO_SPIN)) {
			fail("Сервер не принимает ставки.");
			return;
		}
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(this.pos);
		buf.writeByte(this.mask);
		buf.writeVarInt(bet);
		ClientPlayNetworking.send(ModPackets.CASINO_SPIN, buf);
		this.waiting = true;
		this.waitingSince = Util.getMeasuringTimeMs();
		this.error = "";
		sound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.8f);
	}

	private void toggle(int sector) {
		int next = this.mask ^ (1 << sector);
		if (Integer.bitCount(next) > CasinoWheelService.MAX_SELECTED) {
			fail("Можно выбрать не больше трёх секторов.");
			return;
		}
		this.mask = next;
		if (this.editing) {
			finishEditing();
		} else {
			sendSelect();
		}
		sound(SoundEvents.UI_BUTTON_CLICK.value(), (next & (1 << sector)) != 0 ? 1.4f : 1.1f, 0.7f);
	}

	private void sound(SoundEvent event, float pitch, float volume) {
		if (this.client != null) {
			this.client.getSoundManager().play(PositionedSoundInstance.master(event, pitch, volume));
		}
	}

	// ---------- кадр ----------

	private void animate() {
		long now = Util.getMeasuringTimeMs();
		float dt = Math.min(0.1f, (now - this.lastFrame) / 1000f);
		this.lastFrame = now;
		if (this.spinStart >= 0) {
			double t = (now - this.spinStart) / (double) SPIN_MS;
			if (t >= 1) {
				this.rot = this.spinTo % 360f;
				this.spinStart = -1;
				this.resultAt = now;
				if (this.other != null && this.result == null) {
					String name = CasinoWheelService.NAMES[Math.floorMod(this.other.sector(), CasinoWheelService.SECTORS)];
					boolean win = this.other.payout() > 0;
					this.otherText = this.other.nick() + ": выпал " + name
							+ (win ? " — выигрыш " + money(this.other.payout()) + " WC" : " — неудача");
					this.otherColor = win ? 0xFF52E052 : 0xFFFF5A5A;
					this.otherAt = now;
					sound(win ? SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP : SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), win ? 1.2f : 0.6f, 0.7f);
					if (this.other.broke()) {
						this.broken = true;
						this.eject = true;
					}
				}
				if (this.result != null && this.result.broke()) {
					this.eject = true;
				}
			} else {
				double e = 1 - Math.pow(1 - t, 4);
				this.rot = (float) (this.spinFrom + (this.spinTo - this.spinFrom) * e);
			}
		}
		int idx = sectorAt(this.rot);
		if (idx != this.lastTick) {
			this.lastTick = idx;
			this.kick = 1f;
			sound(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 1.9f, 0.6f);
		}
		this.kick *= (float) Math.exp(-dt * 9f);
		if (this.result != null && !this.showResult && this.spinStart < 0 && now - this.resultAt >= REVEAL_MS) {
			this.showResult = true;
			this.revealAt = now;
			this.balance = this.liveBalance;
			if (this.result.broke()) {
				this.broken = true;
			}
			if (this.result.win()) {
				sound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
			} else {
				sound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.5f, 1.0f);
			}
		}
		if (this.waiting && now - this.waitingSince > TIMEOUT_MS) {
			this.waiting = false;
			fail("Сервер не ответил. Попробуйте ещё раз.");
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		animate();
		this.x0 = (this.width - W) / 2;
		this.y0 = (this.height - H) / 2;
		this.renderBackground(context);
		int mx = this.showResult ? -1000 : mouseX;
		int my = this.showResult ? -1000 : mouseY;
		drawWindow(context, mx, my);
		drawSectors(context, mx, my);
		drawInfo(context);
		drawWheel(context);
		drawBet(context, mx, my);
		if (this.showResult) {
			drawResult(context, mouseX, mouseY);
		}
	}

	private void drawWindow(DrawContext c, int mx, int my) {
		int x = this.x0;
		int y = this.y0;
		c.fill(x + 3, y + 3, x + W + 3, y + H + 3, 0x70000000);
		c.fill(x, y, x + W, y + H, EDGE);
		c.fill(x + 1, y + 1, x + W - 1, y + H - 1, WINDOW);
		c.fill(x + 1, y + 1, x + W - 1, y + 19, 0xFF353535);
		c.fill(x + 1, y + 19, x + W - 1, y + 20, EDGE);
		Text title = Text.literal("РУЛЕТКА УДАЧИ").formatted(Formatting.BOLD);
		c.drawText(this.textRenderer, title, x + (W - this.textRenderer.getWidth(title)) / 2, y + 6, 0xFFFFFFFF, true);
		int cx = x + W - 15;
		int cy = y + 6;
		boolean hot = inside(mx, my, cx - 2, cy - 2, 12, 12);
		int col = hot ? 0xFFFF7070 : 0xFFE04646;
		for (int i = 0; i < 7; i++) {
			c.fill(cx + i, cy + i, cx + i + 2, cy + i + 2, col);
			c.fill(cx + 6 - i, cy + i, cx + 8 - i, cy + i + 2, col);
		}
	}

	private void drawSectors(DrawContext c, int mx, int my) {
		int x = this.x0 + 10;
		int y = this.y0 + 28;
		box(c, x, y, SIDE, 114, PANEL);
		center(c, Text.literal("ВЫБОР СЕКТОРОВ").formatted(Formatting.BOLD), x + SIDE / 2, y + 5, 0xFFFFFFFF, 0.8f);
		for (int i = 0; i < CasinoWheelService.SECTORS; i++) {
			int ry = y + 17 + i * 16;
			boolean hot = !busy() && inside(mx, my, x + 5, ry, SIDE - 10, 14);
			c.fill(x + 5, ry, x + SIDE - 5, ry + 14, hot ? 0xFF474747 : CELL);
			small(c, CasinoWheelService.NAMES[i], x + 9, ry + 4, COLORS[i], 0.85f);
			int bx = x + SIDE - 17;
			int by = ry + 3;
			c.fill(bx, by, bx + 9, by + 9, 0xFF262626);
			c.fill(bx, by, bx + 9, by + 1, 0xFF1C1C1C);
			if ((mask >> i & 1) != 0) {
				check(c, bx + 1, by + 1, 0xFF7EDB57);
			}
		}
		int by = y + 120;
		box(c, x, by, SIDE, 30, PANEL);
		small(c, "Баланс", x + 6, by + 5, MUTED, 0.75f);
		amount(c, money(this.balance), x + 6, by + 16, 0xFFFFFFFF);
	}

	private void drawInfo(DrawContext c) {
		int x = this.x0 + W - 10 - SIDE;
		int y = this.y0 + 28;
		box(c, x, y, SIDE, 96, PANEL);
		center(c, Text.literal("ИНФОРМАЦИЯ").formatted(Formatting.BOLD), x + SIDE / 2, y + 5, 0xFFFFFFFF, 0.8f);
		int selected = Integer.bitCount(mask);
		int mult = CasinoWheelService.multTenths(selected);
		String chance = selected == 0 ? "—" : percent(selected * 100.0 / CasinoWheelService.SECTORS);
		String multText = selected == 0 ? "—" : mult(mult);
		long potential = selected == 0 ? 0 : (long) bet() * mult / 10;
		infoCell(c, x + 5, y + 16, "Шанс победы", Text.literal(chance), null);
		infoCell(c, x + 5, y + 42, "Множитель", Text.literal(multText), null);
		infoCell(c, x + 5, y + 68, "Потенциальный выигрыш", null, selected == 0 ? "—" : money(potential));
		int hy = y + 102;
		box(c, x, hy, SIDE, 48, PANEL);
		List<OrderedText> lines = this.textRenderer.wrapLines(Text.literal(HINT), (int) ((SIDE - 10) / 0.75f));
		int ly = hy + 5;
		for (OrderedText line : lines) {
			c.getMatrices().push();
			c.getMatrices().translate(x + SIDE / 2f, ly, 0);
			c.getMatrices().scale(0.75f, 0.75f, 1f);
			c.drawText(this.textRenderer, line, -this.textRenderer.getWidth(line) / 2, 0, 0xFFD8D8D8, false);
			c.getMatrices().pop();
			ly += 8;
		}
	}

	private void infoCell(DrawContext c, int x, int y, String label, Text value, String coins) {
		int w = SIDE - 10;
		c.fill(x, y, x + w, y + 23, CELL);
		center(c, Text.literal(label), x + w / 2, y + 3, MUTED, 0.75f);
		if (coins != null) {
			int tw = this.textRenderer.getWidth(coins) + (coins.equals("—") ? 0 : 10);
			if (coins.equals("—")) {
				c.drawText(this.textRenderer, coins, x + (w - tw) / 2, y + 12, 0xFFFFFFFF, false);
			} else {
				amount(c, coins, x + (w - tw) / 2, y + 12, 0xFFFFFFFF);
			}
		} else {
			c.drawText(this.textRenderer, value, x + (w - this.textRenderer.getWidth(value)) / 2, y + 12, 0xFFFFFFFF, false);
		}
	}

	private int wheelX() {
		return this.x0 + W / 2;
	}

	private int wheelY() {
		return this.y0 + 28 + 10 + R;
	}

	private void drawWheel(DrawContext c) {
		c.draw();
		float cx = wheelX();
		float cy = wheelY();
		Matrix4f m = c.getMatrices().peek().getPositionMatrix();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		BufferBuilder bb = Tessellator.getInstance().getBuffer();
		bb.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
		ring(bb, m, cx, cy, R + 1, R + 4, 0xFF1A1A1A, 0f, 360f);
		long now = Util.getMeasuringTimeMs();
		int winSector = -1;
		if (this.spinStart < 0 && this.result != null) {
			winSector = this.result.sector();
		} else if (this.spinStart < 0 && this.other != null && now - this.otherAt < 6000) {
			winSector = this.other.sector();
		}
		for (int i = 0; i < CasinoWheelService.SECTORS; i++) {
			float a0 = this.rot + i * 60f;
			int color = COLORS[i];
			if (i == winSector) {
				float pulse = (float) (0.5 + 0.5 * Math.sin((now - this.resultAt) / 110.0));
				color = mix(color, 0xFFFFFFFF, 0.15f + 0.25f * pulse);
			}
			sector(bb, m, cx, cy, R, a0, a0 + 60f, shade(color, 0.78f), color);
			if ((mask >> i & 1) != 0) {
				ring(bb, m, cx, cy, R + 1, R + 4, 0xFFFFFFFF, a0 + 1.5f, a0 + 58.5f);
			}
		}
		for (int i = 0; i < CasinoWheelService.SECTORS; i++) {
			float a = (float) Math.toRadians(this.rot + i * 60f);
			line(bb, m, cx, cy, cx + (float) Math.sin(a) * R, cy - (float) Math.cos(a) * R, 0.6f, 0x40FFFFFF);
			disc(bb, m, cx + (float) Math.sin(a) * (R - 3), cy - (float) Math.cos(a) * (R - 3), 1.6f, 0xFFF4F4F4);
		}
		disc(bb, m, cx, cy, 12f, 0xFF1E1E1E);
		disc(bb, m, cx, cy, 10.5f, 0xFF2E2E2E);
		clover(bb, m, cx, cy);
		float tip = cy - R + 9;
		float base = cy - R - 7;
		float sway = -this.kick * 3.5f;
		tri(bb, m, cx - 8.5f, base - 1.5f, cx + 8.5f, base - 1.5f, cx + sway, tip + 2f, 0xFF0E2A55);
		tri(bb, m, cx - 7f, base, cx + 7f, base, cx + sway, tip, 0xFF3A8EE8);
		tri(bb, m, cx - 7f, base, cx + 1f, base, cx + sway, tip, 0xFF66AEF5);
		BufferRenderer.drawWithGlobalProgram(bb.end());
		RenderSystem.enableCull();
	}

	private void clover(BufferBuilder bb, Matrix4f m, float cx, float cy) {
		int leaf = 0xFF3FB24A;
		int light = 0xFF6AD86E;
		float d = 2.9f;
		line(bb, m, cx, cy, cx + 3.5f, cy + 6.5f, 0.9f, 0xFF2B7A32);
		disc(bb, m, cx - d, cy - d, 3.1f, leaf);
		disc(bb, m, cx + d, cy - d, 3.1f, leaf);
		disc(bb, m, cx - d, cy + d, 3.1f, leaf);
		disc(bb, m, cx + d, cy + d, 3.1f, leaf);
		disc(bb, m, cx - d - 0.8f, cy - d - 0.8f, 1.1f, light);
		disc(bb, m, cx + d - 0.8f, cy - d - 0.8f, 1.1f, light);
		disc(bb, m, cx - d - 0.8f, cy + d - 0.8f, 1.1f, light);
		disc(bb, m, cx + d - 0.8f, cy + d - 0.8f, 1.1f, light);
		disc(bb, m, cx, cy, 1.6f, 0xFF2B7A32);
	}

	private void drawBet(DrawContext c, int mx, int my) {
		int cx = wheelX();
		int x = cx - 62;
		int y = this.y0 + 28 + 2 * R + 26;
		box(c, x, y, 124, 34, PANEL);
		boolean idle = !busy();
		blueButton(c, x + 4, y + 4, 15, 14, "-", idle && inside(mx, my, x + 4, y + 4, 15, 14));
		blueButton(c, x + 105, y + 4, 15, 14, "+", idle && inside(mx, my, x + 105, y + 4, 15, 14));
		c.fill(x + 21, y + 4, x + 103, y + 18, this.editing ? 0xFF5A5A5A : 0xFF1F1F1F);
		c.fill(x + 22, y + 5, x + 102, y + 17, 0xFF262626);
		String shown = this.editing ? betText : money(bet());
		if (this.editing && (Util.getMeasuringTimeMs() / 400) % 2 == 0) {
			shown = shown + "_";
		}
		c.drawText(this.textRenderer, shown, x + 62 - this.textRenderer.getWidth(shown) / 2, y + 7, 0xFFFFFFFF, false);
		String cur = "Текущая ставка: ";
		String sum = money(bet());
		float s = 0.75f;
		int total = (int) ((this.textRenderer.getWidth(cur) + this.textRenderer.getWidth(sum)) * s) + 10;
		int tx = cx - total / 2;
		small(c, cur, tx, y + 23, MUTED, s);
		int ax = tx + (int) (this.textRenderer.getWidth(cur) * s);
		coinIcon(c, ax, y + 21);
		small(c, sum, ax + 10, y + 23, 0xFFFFFFFF, s);

		int bx = cx - 42;
		int by = y + 40;
		boolean ready = idle && !this.broken;
		boolean hot = ready && inside(mx, my, bx, by, 84, 17);
		String label = this.waiting || this.spinStart >= 0 || (this.result != null && !this.showResult) ? "Крутится..."
				: this.broken ? "Сломан" : "Крутить!";
		int top = this.broken && idle ? 0xFF8A3A3A : !ready ? 0xFF5E5E5E : (hot ? 0xFF5FD85F : 0xFF46BE46);
		int bottom = this.broken && idle ? 0xFF6A2A2A : !ready ? 0xFF4A4A4A : (hot ? 0xFF3FAE3F : 0xFF2F9A2F);
		c.fill(bx, by, bx + 84, by + 17, EDGE);
		c.fillGradient(bx + 1, by + 1, bx + 83, by + 16, top, bottom);
		c.drawText(this.textRenderer, label, bx + 42 - this.textRenderer.getWidth(label) / 2, by + 5, 0xFFFFFFFF, true);

		long now = Util.getMeasuringTimeMs();
		if (!this.error.isEmpty() && now - this.errorAt < ERROR_MS) {
			center(c, Text.literal(this.error), cx, this.y0 + H - 11, 0xFFFF5A5A, 0.85f);
		} else if (this.other != null && this.spinStart >= 0) {
			center(c, Text.literal(this.other.nick() + " крутит рулетку..."), cx, this.y0 + H - 11, 0xFFFFD34D, 0.85f);
		} else if (!this.otherText.isEmpty() && now - this.otherAt < 6000) {
			center(c, Text.literal(this.otherText), cx, this.y0 + H - 11, this.otherColor, 0.85f);
		} else if (this.broken) {
			center(c, Text.literal("Автомат сломался — почините его инструментами из вкладки WorldCoin"), cx, this.y0 + H - 11, 0xFFFFA040, 0.8f);
		} else {
			center(c, Text.literal("Выбор и ставка общие для всех игроков у этого автомата"), cx, this.y0 + H - 11, 0xFF7A7A7A, 0.75f);
		}
	}

	private void drawResult(DrawContext c, int mouseX, int mouseY) {
		Result r = this.result;
		c.getMatrices().push();
		c.getMatrices().translate(0, 0, 400);
		c.fill(this.x0 + 1, this.y0 + 1, this.x0 + W - 1, this.y0 + H - 1, 0xC8101010);
		int w = 168;
		int h = resultHeight(r);
		float pop = Math.min(1f, (Util.getMeasuringTimeMs() - this.revealAt) / 180f);
		float scale = 0.9f + 0.1f * (float) (1 - Math.pow(1 - pop, 3));
		int x = this.x0 + (W - w) / 2;
		int y = this.y0 + (H - h) / 2;
		c.getMatrices().push();
		c.getMatrices().translate(x + w / 2f, y + h / 2f, 0);
		c.getMatrices().scale(scale, scale, 1f);
		c.getMatrices().translate(-(x + w / 2f), -(y + h / 2f), 0);
		c.fill(x, y, x + w, y + h, EDGE);
		c.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF2D2D2D);
		int tw = 84;
		c.fill(x + (w - tw) / 2, y - 6, x + (w + tw) / 2, y + 8, EDGE);
		c.fill(x + (w - tw) / 2 + 1, y - 5, x + (w + tw) / 2 - 1, y + 7, 0xFF3A3A3A);
		center(c, Text.literal("Результаты"), x + w / 2, y - 2, 0xFFFFFFFF, 0.85f);
		Text head = Text.literal(r.win() ? "ПОБЕДА!" : "НЕУДАЧА").formatted(Formatting.BOLD);
		center(c, head, x + w / 2, y + 16, r.win() ? 0xFF52E052 : 0xFFFF4040, 1.6f);
		c.fill(x + 12, y + 34, x + w - 12, y + 35, 0xFF454545);
		int sector = Math.floorMod(r.sector(), CasinoWheelService.SECTORS);
		int line = y + 42;
		pair(c, x + w / 2, line, "Сектор: ", Text.literal(CasinoWheelService.NAMES[sector]), COLORS[sector], null);
		pair(c, x + w / 2, line + 11, "Множитель: ", Text.literal(mult(r.mult())), 0xFFFFFFFF, null);
		pair(c, x + w / 2, line + 22, "Ставка: ", null, 0xFFFFFFFF, money(r.bet()));
		if (r.win()) {
			pair(c, x + w / 2, line + 33, "Выигрыш: ", null, 0xFFFFD34D, money(r.payout()));
		}
		if (r.broke()) {
			center(c, Text.literal("Автомат сломался! Нужен ремонт."), x + w / 2, line + (r.win() ? 46 : 35), 0xFFFFA040, 0.85f);
		}
		int bx = x + 24;
		int by = y + h - 24;
		boolean hot = inside(mouseX, mouseY, bx, by, w - 48, 16);
		c.fill(bx, by, bx + w - 48, by + 16, EDGE);
		c.fillGradient(bx + 1, by + 1, bx + w - 49, by + 15, hot ? 0xFF4AA8FF : 0xFF2F8FEF, hot ? 0xFF2B83E0 : 0xFF1F70CC);
		String again = r.broke() ? "Понятно" : "Ещё раз";
		c.drawText(this.textRenderer, again, bx + (w - 48) / 2 - this.textRenderer.getWidth(again) / 2, by + 4, 0xFFFFFFFF, true);
		c.getMatrices().pop();
		c.getMatrices().pop();
	}

	private void pair(DrawContext c, int cx, int y, String label, Text value, int color, String coins) {
		float s = 0.85f;
		int lw = (int) (this.textRenderer.getWidth(label) * s);
		int vw = coins != null ? (int) (this.textRenderer.getWidth(coins) * s) + 10 : (int) (this.textRenderer.getWidth(value) * s);
		int x = cx - (lw + vw) / 2;
		small(c, label, x, y, MUTED, s);
		if (coins != null) {
			small(c, coins, x + lw, y, color, s);
			coinIcon(c, x + lw + (int) (this.textRenderer.getWidth(coins) * s) + 2, y - 1);
		} else {
			c.getMatrices().push();
			c.getMatrices().translate(x + lw, y, 0);
			c.getMatrices().scale(s, s, 1f);
			c.drawText(this.textRenderer, value, 0, 0, color, false);
			c.getMatrices().pop();
		}
	}

	private int resultButtonX() {
		return this.x0 + (W - 168) / 2 + 24;
	}

	private int resultButtonY() {
		int h = this.result == null ? 106 : resultHeight(this.result);
		return this.y0 + (H - h) / 2 + h - 24;
	}

	private static int resultHeight(Result r) {
		return (r.win() ? 116 : 106) + (r.broke() ? 12 : 0);
	}

	// ---------- ввод ----------

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int mx = (int) mouseX;
		int my = (int) mouseY;
		if (button != 0) {
			return super.mouseClicked(mouseX, mouseY, button);
		}
		if (this.showResult) {
			if (inside(mx, my, resultButtonX(), resultButtonY(), 120, 16)) {
				this.showResult = false;
				this.result = null;
				sound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.8f);
			}
			return true;
		}
		if (inside(mx, my, this.x0 + W - 17, this.y0 + 4, 12, 12)) {
			sound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.6f);
			close();
			return true;
		}
		if (busy()) {
			return true;
		}
		int sx = this.x0 + 10;
		int sy = this.y0 + 28;
		for (int i = 0; i < CasinoWheelService.SECTORS; i++) {
			if (inside(mx, my, sx + 5, sy + 17 + i * 16, SIDE - 10, 14)) {
				toggle(i);
				return true;
			}
		}
		double dx = mx - wheelX();
		double dy = my - wheelY();
		if (dx * dx + dy * dy <= R * R && dx * dx + dy * dy > 12 * 12) {
			double angle = Math.toDegrees(Math.atan2(dx, -dy));
			float local = (float) (((angle - this.rot) % 360 + 360) % 360);
			toggle(Math.min(CasinoWheelService.SECTORS - 1, (int) (local / 60f)));
			return true;
		}
		int x = wheelX() - 62;
		int y = this.y0 + 28 + 2 * R + 26;
		long step = hasShiftDown() ? 1000 : 100;
		if (inside(mx, my, x + 4, y + 4, 15, 14)) {
			setBet(bet() - step);
			this.editing = false;
			sound(SoundEvents.UI_BUTTON_CLICK.value(), 0.9f, 0.6f);
			return true;
		}
		if (inside(mx, my, x + 105, y + 4, 15, 14)) {
			setBet(bet() + step);
			this.editing = false;
			sound(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.6f);
			return true;
		}
		if (inside(mx, my, x + 21, y + 4, 82, 14)) {
			this.editing = true;
			return true;
		}
		finishEditing();
		if (inside(mx, my, wheelX() - 42, y + 40, 84, 17)) {
			trySpin();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (this.editing && chr >= '0' && chr <= '9') {
			if (betText.length() < 9 && !(betText.isEmpty() && chr == '0')) {
				betText = betText + chr;
			}
			return true;
		}
		return super.charTyped(chr, modifiers);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.showResult) {
			this.showResult = false;
			this.result = null;
			return true;
		}
		if (this.editing && keyCode == GLFW.GLFW_KEY_BACKSPACE) {
			if (!betText.isEmpty()) {
				betText = betText.substring(0, betText.length() - 1);
			}
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			if (this.showResult) {
				this.showResult = false;
				this.result = null;
			} else if (this.editing) {
				finishEditing();
			} else {
				trySpin();
			}
			return true;
		}
		if (this.client != null && this.client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
			close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	// ---------- рисование ----------

	private static boolean inside(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private static void box(DrawContext c, int x, int y, int w, int h, int fill) {
		c.fill(x, y, x + w, y + h, EDGE);
		c.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
	}

	private void blueButton(DrawContext c, int x, int y, int w, int h, String text, boolean hot) {
		c.fill(x, y, x + w, y + h, EDGE);
		c.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, hot ? 0xFF5AA8FF : 0xFF3A8EE8, hot ? 0xFF3A86E0 : 0xFF2A6FC4);
		c.drawText(this.textRenderer, text, x + (w - this.textRenderer.getWidth(text)) / 2 + 1, y + (h - 8) / 2 + 1, 0xFFFFFFFF, true);
	}

	private static void check(DrawContext c, int x, int y, int color) {
		int[][] pts = {{0, 3}, {1, 4}, {2, 5}, {3, 4}, {4, 3}, {5, 2}, {6, 1}};
		for (int[] p : pts) {
			c.fill(x + p[0], y + p[1], x + p[0] + 1, y + p[1] + 2, color);
		}
	}

	private void small(DrawContext c, String text, int x, int y, int color, float scale) {
		c.getMatrices().push();
		c.getMatrices().translate(x, y, 0);
		c.getMatrices().scale(scale, scale, 1f);
		c.drawText(this.textRenderer, text, 0, 0, color, false);
		c.getMatrices().pop();
	}

	private void center(DrawContext c, Text text, int cx, int y, int color, float scale) {
		c.getMatrices().push();
		c.getMatrices().translate(cx, y, 0);
		c.getMatrices().scale(scale, scale, 1f);
		c.drawText(this.textRenderer, text, -this.textRenderer.getWidth(text) / 2, 0, color, scale >= 1.2f);
		c.getMatrices().pop();
	}

	private void coinIcon(DrawContext c, int x, int y) {
		c.getMatrices().push();
		c.getMatrices().translate(x, y, 0);
		c.getMatrices().scale(0.5f, 0.5f, 1f);
		c.drawItem(this.coin, 0, 0);
		c.getMatrices().pop();
	}

	private void amount(DrawContext c, String text, int x, int y, int color) {
		c.drawText(this.textRenderer, text, x, y, color, false);
		coinIcon(c, x + this.textRenderer.getWidth(text) + 2, y);
	}

	private static String money(long value) {
		String raw = Long.toString(Math.abs(value));
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < raw.length(); i++) {
			if (i > 0 && (raw.length() - i) % 3 == 0) {
				out.append(' ');
			}
			out.append(raw.charAt(i));
		}
		return (value < 0 ? "-" : "") + out;
	}

	private static String mult(int tenths) {
		return tenths % 10 == 0 ? (tenths / 10) + "x" : String.format(Locale.US, "%.1fx", tenths / 10.0);
	}

	private static String percent(double value) {
		return Math.abs(value - Math.rint(value)) < 0.001
				? (long) Math.rint(value) + "%"
				: String.format(Locale.US, "%.2f%%", value);
	}

	private static int shade(int argb, float f) {
		int r = (int) (((argb >> 16) & 0xFF) * f);
		int g = (int) (((argb >> 8) & 0xFF) * f);
		int b = (int) ((argb & 0xFF) * f);
		return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
	}

	private static int mix(int a, int b, float t) {
		int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}

	private static void vertex(BufferBuilder bb, Matrix4f m, float x, float y, int color) {
		bb.vertex(m, x, y, 0f).color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >>> 24) & 0xFF).next();
	}

	private static void tri(BufferBuilder bb, Matrix4f m, float x1, float y1, float x2, float y2, float x3, float y3, int color) {
		vertex(bb, m, x1, y1, color);
		vertex(bb, m, x2, y2, color);
		vertex(bb, m, x3, y3, color);
	}

	/** Сектор круга от угла {@code a0} до {@code a1} (градусы по часовой стрелке от верха). */
	private static void sector(BufferBuilder bb, Matrix4f m, float cx, float cy, float r, float a0, float a1, int inner, int outer) {
		for (int s = 0; s < SEGMENTS; s++) {
			double t0 = Math.toRadians(a0 + (a1 - a0) * s / SEGMENTS);
			double t1 = Math.toRadians(a0 + (a1 - a0) * (s + 1) / SEGMENTS);
			vertex(bb, m, cx, cy, inner);
			vertex(bb, m, cx + (float) Math.sin(t0) * r, cy - (float) Math.cos(t0) * r, outer);
			vertex(bb, m, cx + (float) Math.sin(t1) * r, cy - (float) Math.cos(t1) * r, outer);
		}
	}

	private static void ring(BufferBuilder bb, Matrix4f m, float cx, float cy, float r0, float r1, int color, float a0, float a1) {
		int steps = Math.max(6, (int) ((a1 - a0) / 4f));
		for (int s = 0; s < steps; s++) {
			double t0 = Math.toRadians(a0 + (a1 - a0) * s / steps);
			double t1 = Math.toRadians(a0 + (a1 - a0) * (s + 1) / steps);
			float ix0 = cx + (float) Math.sin(t0) * r0;
			float iy0 = cy - (float) Math.cos(t0) * r0;
			float ox0 = cx + (float) Math.sin(t0) * r1;
			float oy0 = cy - (float) Math.cos(t0) * r1;
			float ix1 = cx + (float) Math.sin(t1) * r0;
			float iy1 = cy - (float) Math.cos(t1) * r0;
			float ox1 = cx + (float) Math.sin(t1) * r1;
			float oy1 = cy - (float) Math.cos(t1) * r1;
			tri(bb, m, ix0, iy0, ox0, oy0, ox1, oy1, color);
			tri(bb, m, ix0, iy0, ox1, oy1, ix1, iy1, color);
		}
	}

	private static void disc(BufferBuilder bb, Matrix4f m, float cx, float cy, float r, int color) {
		int steps = Math.max(10, (int) (r * 4));
		for (int s = 0; s < steps; s++) {
			double t0 = Math.PI * 2 * s / steps;
			double t1 = Math.PI * 2 * (s + 1) / steps;
			tri(bb, m, cx, cy, cx + (float) Math.sin(t0) * r, cy - (float) Math.cos(t0) * r,
					cx + (float) Math.sin(t1) * r, cy - (float) Math.cos(t1) * r, color);
		}
	}

	private static void line(BufferBuilder bb, Matrix4f m, float x1, float y1, float x2, float y2, float half, int color) {
		float dx = x2 - x1;
		float dy = y2 - y1;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 0.001f) {
			return;
		}
		float nx = -dy / len * half;
		float ny = dx / len * half;
		tri(bb, m, x1 + nx, y1 + ny, x2 + nx, y2 + ny, x2 - nx, y2 - ny, color);
		tri(bb, m, x1 + nx, y1 + ny, x2 - nx, y2 - ny, x1 - nx, y1 - ny, color);
	}
}
