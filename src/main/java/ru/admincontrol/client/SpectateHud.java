package ru.admincontrol.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public final class SpectateHud {
	private static boolean visible;
	private static boolean control;
	private static String npcName = "";
	private static float npcHealth;
	private static int npcFood;
	private static boolean held;

	private static String target = "";
	private static int coins;
	private static double luck;
	private static String clan = "";
	private static double power = 1.0;
	private static int food;
	private static float health;
	private static double tx;
	private static double ty;
	private static double tz;
	private static int xpLevel;
	private static final ItemStack[] HOTBAR = new ItemStack[10];
	private static int selected;
	private static final Identifier WIDGETS = new Identifier("textures/gui/widgets.png");

	private SpectateHud() {
	}

	public static void hotbar(int slot, ItemStack[] stacks) {
		selected = MathHelper.clamp(slot, 0, 8);
		for (int i = 0; i < HOTBAR.length; i++) {
			HOTBAR[i] = i < stacks.length && stacks[i] != null ? stacks[i] : ItemStack.EMPTY;
		}
	}

	/** Камера наблюдателя повторяет интерполированную позицию цели, поэтому движение плавное. */
	public static void followTarget(MinecraftClient client) {
		if (!spectating() || client.player == null || client.world == null || target.isBlank()) {
			return;
		}
		PlayerEntity t = null;
		for (PlayerEntity p : client.world.getPlayers()) {
			if (p != client.player && target.equalsIgnoreCase(p.getGameProfile().getName())) {
				t = p;
				break;
			}
		}
		if (t == null || client.player.squaredDistanceTo(t) > 64.0) {
			return;
		}
		client.player.setVelocity(Vec3d.ZERO);
		client.player.setPosition(t.getX(), t.getY(), t.getZ());
		client.player.prevX = t.prevX;
		client.player.prevY = t.prevY;
		client.player.prevZ = t.prevZ;
		client.player.lastRenderX = t.lastRenderX;
		client.player.lastRenderY = t.lastRenderY;
		client.player.lastRenderZ = t.lastRenderZ;
	}

	public static void set(boolean show) {
		set(show, false, "", 0f, 0);
	}

	public static void set(boolean show, boolean npcControl) {
		set(show, npcControl, npcName, npcHealth, npcFood);
	}

	public static void set(boolean show, boolean npcControl, String name, float health, int food) {
		visible = show;
		control = show && npcControl;
		npcName = name == null ? "" : name;
		npcHealth = health;
		npcFood = food;
		if (!show) {
			held = false;
			npcName = "";
			npcHealth = 0f;
			npcFood = 0;
			target = "";
			java.util.Arrays.fill(HOTBAR, ItemStack.EMPTY);
			selected = 0;
		}
	}

	public static void info(String nick, int balance, double luckValue, String clanName, double powerValue,
			int hunger, float hp, double x, double y, double z, int level) {
		target = nick == null ? "" : nick;
		coins = balance;
		luck = luckValue;
		clan = clanName == null || clanName.isBlank() ? "нет" : clanName;
		power = powerValue;
		food = hunger;
		health = hp;
		tx = x;
		ty = y;
		tz = z;
		xpLevel = level;
	}

	public static boolean possessing() {
		return visible && control;
	}

	public static boolean spectating() {
		return visible && !control;
	}

	public static boolean hidingPossessedNpc(String nick) {
		if (!possessing() || nick == null || nick.isBlank() || npcName.isBlank()) {
			return false;
		}
		return nick.equalsIgnoreCase(npcName);
	}

	/** В режиме от первого лица модель цели не мешает обзору: камера стоит у её глаз. */
	public static boolean hidingSpectateTarget(String nick) {
		if (!spectating() || nick == null || target.isBlank() || !nick.equalsIgnoreCase(target)) {
			return false;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		return client.options.getPerspective().isFirstPerson();
	}

	public static boolean hidingLocalPlayer() {
		return false;
	}

	public static void hide() {
		set(false);
	}

	private static String title() {
		if (control) {
			return npcName.isBlank() ? "Управление NPC" : "NPC «" + npcName + "»";
		}
		return target.isBlank() ? "Слежка" : "Слежка: " + target;
	}

	public static void render(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!visible || client.player == null || client.options.hudHidden) {
			return;
		}
		if (!control) {
			renderSpectate(context, client);
			return;
		}
		TextRenderer tr = client.textRenderer;
		String title = title();
		String stats = "HP " + Math.round(npcHealth) + "   Еда " + npcFood;
		String hint = "Вы играете за NPC. Выход — кнопка справа";
		String exit = "Выйти";
		int bw = tr.getWidth(exit) + 12;
		int w = Math.max(Math.max(tr.getWidth(title), bw), Math.max(tr.getWidth(stats), tr.getWidth(hint))) + 16;
		int x = client.getWindow().getScaledWidth() / 2 - w / 2;
		int y = 8;
		int h = tr.fontHeight * 3 + 6;
		context.fill(x - 2, y - 2, x + w, y + h + 2, 0x90000000);
		context.drawText(tr, title, x + w / 2 - tr.getWidth(title) / 2, y, 0xFFFF55, false);
		int line = y + tr.fontHeight + 2;
		context.drawText(tr, stats, x + w / 2 - tr.getWidth(stats) / 2, line, 0xAAFFAA, false);
		line += tr.fontHeight + 1;
		context.drawText(tr, hint, x + w / 2 - tr.getWidth(hint) / 2, line, 0xCCCCCC, false);
		line += tr.fontHeight + 2;
		context.fill(x + (w - bw) / 2, line, x + (w + bw) / 2, line + tr.fontHeight + 4, 0x50000000);
		context.drawText(tr, exit, x + w / 2 - tr.getWidth(exit) / 2, line + 2, 0xFFFF55, false);
	}

	private static void renderSpectate(DrawContext context, MinecraftClient client) {
		TextRenderer tr = client.textRenderer;
		int sw = client.getWindow().getScaledWidth();
		int sh = client.getWindow().getScaledHeight();
		String title = title();
		int tw = tr.getWidth(title) + 12;
		context.fill(sw / 2 - tw / 2, 6, sw / 2 + tw / 2, 8 + tr.fontHeight + 4, 0x90000000);
		context.drawTextWithShadow(tr, title, sw / 2 - tr.getWidth(title) / 2, 9, 0xFFFF55);
		String[][] right = {
				{"Баланс", ru.admincontrol.util.Money.format(coins)},
				{"Удача", String.format(Locale.US, "%.1f", luck)},
				{"Клан", AcTheme.ellipsize(tr, clan, 72)},
				{"Сила", String.format(Locale.US, "%.1f", power)}
		};
		String[][] left = {
				{"Голод", food + "/20"},
				{"Здоровье", String.format(Locale.US, "%.1f", health) + "/20"},
				{"X", String.format(Locale.US, "%.2f", tx)},
				{"Y", String.format(Locale.US, "%.2f", ty)},
				{"Z", String.format(Locale.US, "%.2f", tz)},
				{"Уровень опыта", String.valueOf(xpLevel)}
		};
		panel(context, tr, "Игрок", left, 6, sh, false, sw);
		panel(context, tr, "Профиль", right, 6, sh, true, sw);
		renderHotbar(context, tr, sw, sh);
	}

	private static void renderHotbar(DrawContext context, TextRenderer tr, int sw, int sh) {
		int x = sw / 2 - 91;
		int y = sh - 22;
		RenderSystem.enableBlend();
		context.drawTexture(WIDGETS, x, y, 0, 0, 182, 22);
		context.drawTexture(WIDGETS, x - 1 + selected * 20, y - 1, 0, 22, 24, 22);
		ItemStack off = HOTBAR[9] == null ? ItemStack.EMPTY : HOTBAR[9];
		if (!off.isEmpty()) {
			context.drawTexture(WIDGETS, x - 29, y - 1, 24, 22, 29, 24);
		}
		RenderSystem.disableBlend();
		for (int i = 0; i < 9; i++) {
			ItemStack stack = HOTBAR[i] == null ? ItemStack.EMPTY : HOTBAR[i];
			if (stack.isEmpty()) {
				continue;
			}
			int ix = x + 3 + i * 20;
			int iy = y + 3;
			context.drawItem(stack, ix, iy);
			context.drawItemInSlot(tr, stack, ix, iy);
		}
		if (!off.isEmpty()) {
			context.drawItem(off, x - 26, y + 3);
			context.drawItemInSlot(tr, off, x - 26, y + 3);
		}
		ItemStack hand = HOTBAR[selected] == null ? ItemStack.EMPTY : HOTBAR[selected];
		String label = hand.isEmpty() ? "В руке: пусто" : "В руке: " + hand.getName().getString()
				+ (hand.getCount() > 1 ? " ×" + hand.getCount() : "");
		label = AcTheme.ellipsize(tr, label, 240);
		int lw = tr.getWidth(label);
		int ly = y - tr.fontHeight - 6;
		context.fill(sw / 2 - lw / 2 - 4, ly - 2, sw / 2 + lw / 2 + 4, ly + tr.fontHeight + 1, 0x90000000);
		context.drawTextWithShadow(tr, label, sw / 2 - lw / 2, ly, hand.isEmpty() ? 0xAAAAAA : 0xFFFFFF);
	}

	private static void panel(DrawContext context, TextRenderer tr, String head, String[][] rows, int margin,
			int sh, boolean alignRight, int sw) {
		int lineH = tr.fontHeight + 3;
		int w = tr.getWidth(head);
		for (String[] row : rows) {
			w = Math.max(w, tr.getWidth(row[0]) + 12 + tr.getWidth(row[1]));
		}
		w += 12;
		int h = tr.fontHeight + 6 + lineH * rows.length + 4;
		int x = alignRight ? sw - w - margin : margin;
		int y = sh / 2 - h / 2;
		context.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0x4A000000);
		context.fill(x, y, x + w, y + h, 0x3A14141C);
		context.fill(x, y, x + w, y + tr.fontHeight + 6, 0x421C1810);
		context.drawTextWithShadow(tr, head, x + (w - tr.getWidth(head)) / 2, y + 4, 0xFFD76A);
		int rowY = y + tr.fontHeight + 9;
		for (String[] row : rows) {
			context.drawTextWithShadow(tr, row[0], x + 6, rowY, 0x7FE3FF);
			context.drawTextWithShadow(tr, row[1], x + w - 6 - tr.getWidth(row[1]), rowY, 0xFFE27A);
			rowY += lineH;
		}
	}

	public static void tickClick() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!visible || !control || client.player == null || client.currentScreen != null) {
			held = false;
			return;
		}
		Window window = client.getWindow();
		double mx = client.mouse.getX() * window.getScaledWidth() / window.getWidth();
		double my = client.mouse.getY() * window.getScaledHeight() / window.getHeight();
		TextRenderer tr = client.textRenderer;
		String title = title();
		String stats = "HP " + Math.round(npcHealth) + "   Еда " + npcFood;
		String hint = "Вы играете за NPC. Выход — кнопка справа";
		int bw = tr.getWidth("Выйти") + 12;
		int w = Math.max(Math.max(tr.getWidth(title), bw), Math.max(tr.getWidth(stats), tr.getWidth(hint))) + 16;
		int x = window.getScaledWidth() / 2 - w / 2;
		int by = 8 + tr.fontHeight + 2 + tr.fontHeight + 1 + tr.fontHeight + 2;
		int x1 = x + (w - bw) / 2;
		int x2 = x + (w + bw) / 2;
		boolean over = mx >= x1 && mx < x2 && my >= by && my < by + tr.fontHeight + 4;
		boolean down = GLFW.glfwGetMouseButton(window.getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		if (over && down && !held) {
			held = true;
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
					ru.admincontrol.net.ModPackets.NPC_CONTROL_EXIT,
					net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create());
		}
		if (!down) {
			held = false;
		}
	}
}
