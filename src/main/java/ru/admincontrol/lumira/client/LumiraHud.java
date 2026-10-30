package ru.admincontrol.lumira.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import ru.admincontrol.lumira.LumiraNet;

/**
 * Магический HUD v2 «Шёпот Люмиры». Включается только сюжетным пакетом сервера; на основном сервере и в ивентах
 * остаётся стандартный HUD. Все значения берутся из реального игрока клиента: здоровье, поглощение, сытость, опыт,
 * руки, броня и прочность. Цифр слотов и повторяющихся сердец/иконок еды нет.
 */
@Environment(EnvType.CLIENT)
public final class LumiraHud {
	private static final int SLATE = 0xE61B2228;
	private static final int SLATE_LIGHT = 0xE6293238;
	private static final int SILVER = 0xFFB9C6CE;
	private static final int TEAL = 0xFF3FD9C8;
	private static final int RUBY_TOP = 0xFFE0314B;
	private static final int RUBY_BOTTOM = 0xFF7E0F26;
	private static final int AMBER_TOP = 0xFFF2B23A;
	private static final int AMBER_BOTTOM = 0xFF9A5F10;
	private static final int GOLD = 0xFFFFD34D;
	private static final int WARN = 0xFFFF5A4F;

	private static boolean story;
	private static int amuletBonus;
	private static ItemStack necklace = ItemStack.EMPTY;

	private LumiraHud() {
	}

	public static ItemStack necklaceStack() {
		return necklace;
	}

	/** Окно контейнера, которое перекрашивается в стиль сюжета (не инвентарь игрока и не окна мода). */
	public static boolean restyle(net.minecraft.client.gui.screen.Screen screen) {
		if (!active() || !(screen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?> handled)
				|| screen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen) {
			return false;
		}
		return handled.getScreenHandler().getClass().getName().startsWith("net.minecraft.");
	}

	/** Клиент находится в сюжетном или строительном измерении (кнопки обычного сервера там скрыты). */
	public static boolean inCwWorld() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) {
			return false;
		}
		var id = mc.world.getRegistryKey().getValue();
		return id.getNamespace().equals("admin_control") && (id.getPath().startsWith("cw_story_") || id.getPath().equals("cw_builder"));
	}

	public static boolean active() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return story && mc.player != null && !mc.player.isSpectator();
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(LumiraNet.STATE, (client, handler, buf, responseSender) -> {
			boolean inStory = buf.readBoolean();
			int bonus = buf.readVarInt();
			ItemStack stack = buf.readItemStack();
			client.execute(() -> {
				story = inStory;
				amuletBonus = bonus;
				necklace = stack;
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(ru.admincontrol.item.ItemEncryption.SYNC, (client, handler, buf, responseSender) -> {
			boolean names = buf.readBoolean();
			boolean icons = buf.readBoolean();
			boolean recipes = buf.readBoolean();
			int n = buf.isReadable() ? buf.readVarInt() : 0;
			java.util.List<String> nameList = new java.util.ArrayList<>();
			for (int i = 0; i < n; i++) {
				nameList.add(buf.readString(128));
			}
			int m = buf.isReadable() ? buf.readVarInt() : 0;
			java.util.List<String> iconList = new java.util.ArrayList<>();
			for (int i = 0; i < m; i++) {
				iconList.add(buf.readString(128));
			}
			int d = buf.isReadable() ? buf.readVarInt() : 0;
			java.util.List<String> descList = new java.util.ArrayList<>();
			for (int i = 0; i < d; i++) {
				descList.add(buf.readString(128));
			}
			int u = buf.isReadable() ? buf.readVarInt() : 0;
			java.util.List<String> useBlockedList = new java.util.ArrayList<>();
			for (int i = 0; i < u; i++) {
				useBlockedList.add(buf.readString(128));
			}
			boolean dev = buf.isReadable() && buf.readBoolean();
			client.execute(() -> {
				ru.admincontrol.item.ItemEncryption.apply(names, nameList, icons, iconList, recipes, descList, useBlockedList);
				ru.admincontrol.item.ItemEncryption.setViewBypass(dev);
				EncryptedItemsScreen.onSync();
				ru.admincontrol.emi.client.CwEmiClient.onAccessChanged();
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(ru.admincontrol.lumira.CoinRecipes.SYNC, (client, handler, buf, responseSender) -> {
			int[] counts = new int[2];
			java.util.List<java.util.List<String>> lists = new java.util.ArrayList<>();
			for (int coin = 0; coin < 2; coin++) {
				counts[coin] = buf.readVarInt();
				int n = buf.readVarInt();
				java.util.List<String> list = new java.util.ArrayList<>();
				for (int i = 0; i < n; i++) {
					list.add(buf.readString(128));
				}
				lists.add(list);
			}
			client.execute(() -> {
				for (int coin = 0; coin < 2; coin++) {
					ru.admincontrol.lumira.CoinRecipes.CLIENT_COUNT[coin] = counts[coin];
					ru.admincontrol.lumira.CoinRecipes.CLIENT_RECIPE[coin].clear();
					ru.admincontrol.lumira.CoinRecipes.CLIENT_RECIPE[coin].addAll(lists.get(coin));
				}
				CoinRecipeScreen.onSync();
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(ru.admincontrol.lumira.recipe.RecipeEdits.LIST, (client, handler, buf, responseSender) ->
				RecipeEditorScreen.onList(buf));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			ru.admincontrol.item.ItemEncryption.clearLocal();
			story = false;
			amuletBonus = 0;
			necklace = ItemStack.EMPTY;
		}));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(EncryptedItemsScreen::requestSync));
		HudRenderCallback.EVENT.register((context, tickDelta) -> render(context));
	}

	private static void frame(DrawContext c, int x1, int y1, int x2, int y2, int fill, int border) {
		c.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, border);
		c.fill(x1, y1, x2, y2, fill);
	}

	/** Гранёная шкала: градиент и тонкие грани-блики через равные промежутки. */
	private static void gem(DrawContext c, int x, int y, int w, int h, float fraction, int top, int bottom) {
		frame(c, x, y, x + w, y + h, SLATE, SILVER);
		int filled = MathHelper.clamp(Math.round(w * fraction), 0, w);
		if (filled > 0) {
			c.fillGradient(x, y, x + filled, y + h, top, bottom);
			for (int fx = x + 6; fx < x + filled; fx += 8) {
				c.fill(fx, y, fx + 1, y + h, 0x33FFFFFF);
			}
			c.fill(x, y, x + filled, y + 1, 0x55FFFFFF);
		}
	}

	private static void render(DrawContext c) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!story || mc.player == null || mc.options.hudHidden || mc.interactionManager == null) {
			return;
		}
		if (mc.player.isSpectator()) {
			return;
		}
		boolean creative = mc.interactionManager.getCurrentGameMode().isCreative();
		PlayerEntity p = mc.player;
		TextRenderer tr = mc.textRenderer;
		int w = c.getScaledWindowWidth();
		int h = c.getScaledWindowHeight();
		int barW = MathHelper.clamp((w - 182 - 80) / 2, 60, 112);
		int hotbarX = w / 2 - 91;
		int hotbarY = h - 24;

		drawHotbar(c, mc, p, tr, hotbarX, hotbarY);
		drawHeldName(c, p, tr, w / 2, hotbarY - 22);
		if (creative) {
			return;
		}
		drawXp(c, p, tr, hotbarX, hotbarY - 8);
		drawHealth(c, p, tr, 8, h - 20, barW);
		drawHunger(c, p, tr, w - 8 - barW, h - 20, barW);
		drawArmor(c, p, tr, 6, h / 2 - 34);
		drawAir(c, p, tr, w - 8 - barW, h - 36, barW);
	}

	/** Хотбар: каждая ячейка в своей рамке с зазором, выбранная подсвечена бирюзой; справа ячейка ожерелья. */
	private static void slotBox(DrawContext c, int sx, int y, boolean selected) {
		c.fill(sx - 1, y - 1, sx + 19, y + 21, selected ? TEAL : 0xFF0A0D10);
		c.fill(sx, y, sx + 18, y + 20, selected ? 0xFF1F4E52 : 0xFF343E48);
		c.fill(sx, y, sx + 18, y + 1, selected ? 0xFF6FF0E4 : 0xFF566574);
		c.fill(sx, y + 19, sx + 18, y + 20, 0xFF1B2229);
	}

	private static void drawHotbar(DrawContext c, MinecraftClient mc, PlayerEntity p, TextRenderer tr, int x, int y) {
		c.fill(x - 3, y - 3, x + 185, y + 23, 0xE00B0F13);
		c.drawBorder(x - 3, y - 3, 188, 26, SILVER);
		int selected = p.getInventory().selectedSlot;
		for (int i = 0; i < 9; i++) {
			int sx = x + i * 20 + 1;
			if (i != selected) {
				slotBox(c, sx, y, false);
			}
		}
		slotBox(c, x + selected * 20 + 1, y, true);
		for (int i = 0; i < 9; i++) {
			int sx = x + i * 20 + 1;
			ItemStack stack = p.getInventory().main.get(i);
			if (!stack.isEmpty()) {
				c.drawItem(stack, sx + 1, y + 2);
				c.drawItemInSlot(tr, stack, sx + 1, y + 2);
			}
		}
		// Вторая рука: отдельная ячейка слева; пустая не рисует предмет.
		ItemStack off = p.getOffHandStack();
		int ox = x - 28;
		slotBox(c, ox, y, false);
		if (!off.isEmpty()) {
			c.drawItem(off, ox + 1, y + 2);
			c.drawItemInSlot(tr, off, ox + 1, y + 2);
		}
		// Ожерелье: отдельная ячейка справа; в ней видно надетое ожерелье и остаток зарядов.
		int nx = x + 194;
		c.fill(nx - 1, y - 1, nx + 19, y + 21, TEAL);
		c.fill(nx, y, nx + 18, y + 20, 0xFF1B2A30);
		if (!necklace.isEmpty()) {
			c.drawItem(necklace, nx + 1, y + 2);
			int charges = necklace.getMaxDamage() - necklace.getDamage();
			c.drawTextWithShadow(tr, String.valueOf(charges), nx + 10 - tr.getWidth(String.valueOf(charges)) / 2, y + 22, charges > 0 ? 0xFF7FF0E4 : WARN);
		} else {
			c.drawText(tr, "✧", nx + 6, y + 6, 0xFF3FE0D0, false);
		}
	}

	private static void drawXp(DrawContext c, PlayerEntity p, TextRenderer tr, int x, int y) {
		frame(c, x, y, x + 182, y + 3, SLATE, SILVER);
		int filled = Math.round(182 * p.experienceProgress);
		if (filled > 0) {
			c.fill(x, y, x + filled, y + 3, TEAL);
		}
		String level = "Ур. " + p.experienceLevel;
		c.drawTextWithShadow(tr, level, x + 91 - tr.getWidth(level) / 2, y - 10, 0xFF7FF0E4);
	}

	private static void drawHeldName(DrawContext c, PlayerEntity p, TextRenderer tr, int cx, int y) {
		ItemStack held = p.getMainHandStack();
		if (held.isEmpty()) {
			return;
		}
		String name = held.getName().getString();
		int color = 0xFFE8F4F2;
		if (held.isDamageable()) {
			int cur = held.getMaxDamage() - held.getDamage();
			name += "  " + cur + "/" + held.getMaxDamage();
			// ниже 15% — красный текст с «!», ниже 5% (или меньше 10 единиц) — красный мигающий
			boolean low = cur * 100 < held.getMaxDamage() * 15;
			boolean critical = cur * 100 < held.getMaxDamage() * 5 || cur < 10;
			if (low) {
				name += " !";
				color = 0xFFFF4A4A;
			}
			if (critical && (System.currentTimeMillis() / 350) % 2 == 0) {
				color = 0xFF6A1A1A;
			}
		}
		c.drawCenteredTextWithShadow(tr, name, cx, y, color);
	}

	private static void drawHealth(DrawContext c, PlayerEntity p, TextRenderer tr, int x, int y, int w) {
		float max = Math.max(1f, p.getMaxHealth());
		float hp = Math.min(p.getHealth(), max);
		float abs = p.getAbsorptionAmount();
		boolean low = hp * 4f < max;
		gem(c, x, y, w, 11, hp / max, RUBY_TOP, RUBY_BOTTOM);
		if (low) {
			c.fill(x - 2, y - 2, x + w + 2, y - 1, WARN);
			c.fill(x - 2, y + 12, x + w + 2, y + 13, WARN);
		}
		String text = fmt(hp) + " / " + fmt(max);
		c.drawTextWithShadow(tr, "◆", x + 3, y + 2, 0xFFFFC6CF);
		c.drawTextWithShadow(tr, text + (low ? " !" : ""), x + 14, y + 2, 0xFFFFFFFF);
		// Тонкая золотая полоса поглощения отдельно от базы; ширина постоянна.
		frame(c, x, y + 15, x + w, y + 18, SLATE, SILVER);
		if (abs > 0) {
			c.fill(x, y + 15, x + Math.min(w, Math.round(w * abs / Math.max(abs, 20f))), y + 18, GOLD);
			c.drawTextWithShadow(tr, "+" + fmt(abs), x + w + 4, y + 11, GOLD);
		}
		if (amuletBonus > 0) {
			double base = max - amuletBonus;
			String src = "База " + fmt((float) base) + " · Амулет +" + amuletBonus;
			c.drawTextWithShadow(tr, src, x, y - 11, 0xFF9FB3BD);
		}
	}

	private static void drawHunger(DrawContext c, PlayerEntity p, TextRenderer tr, int x, int y, int w) {
		int food = p.getHungerManager().getFoodLevel();
		gem(c, x, y, w, 11, food / 20f, AMBER_TOP, AMBER_BOTTOM);
		String text = food + " / 20";
		c.drawTextWithShadow(tr, "❦", x + 3, y + 2, 0xFFFFE2A0);
		c.drawTextWithShadow(tr, text, x + w - 3 - tr.getWidth(text), y + 2, 0xFFFFFFFF);
	}

	private static void drawArmor(DrawContext c, PlayerEntity p, TextRenderer tr, int x, int y) {
		int row = 0;
		for (int i = 3; i >= 0; i--) {
			ItemStack stack = p.getInventory().armor.get(i);
			if (stack.isEmpty()) {
				continue;
			}
			int ry = y + row * 20;
			frame(c, x, ry, x + 18, ry + 18, SLATE_LIGHT, SILVER);
			c.drawItem(stack, x + 1, ry + 1);
			if (stack.isDamageable()) {
				int cur = stack.getMaxDamage() - stack.getDamage();
				int pct = Math.round(100f * cur / stack.getMaxDamage());
				boolean warn = pct < 15;
				boolean blinkOff = pct < 5 && (System.currentTimeMillis() / 350) % 2 == 0;
				c.fill(x + 21, ry + 4, x + 45, ry + 8, SLATE);
				c.fill(x + 21, ry + 4, x + 21 + Math.round(24f * pct / 100f), ry + 8, warn ? (blinkOff ? 0xFF6A1A1A : WARN) : TEAL);
				c.drawTextWithShadow(tr, pct + "%" + (warn ? " !" : ""), x + 48, ry + 3, warn ? (blinkOff ? 0xFF6A1A1A : WARN) : 0xFFD6E4EA);
			}
			row++;
		}
	}

	private static void drawAir(DrawContext c, PlayerEntity p, TextRenderer tr, int x, int y, int w) {
		int max = p.getMaxAir();
		int air = p.getAir();
		if (air >= max && !p.isSubmergedInWater()) {
			return;
		}
		gem(c, x, y, w, 7, Math.max(0, air) / (float) max, 0xFF5CB8FF, 0xFF1E5E9E);
		String text = "Воздух " + Math.max(0, air / 20) + " с";
		c.drawTextWithShadow(tr, text, x + w - tr.getWidth(text), y - 10, air < 60 ? WARN : 0xFFBFE6FF);
	}

	private static String fmt(float v) {
		return v == Math.floor(v) ? String.valueOf((int) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
	}
}
