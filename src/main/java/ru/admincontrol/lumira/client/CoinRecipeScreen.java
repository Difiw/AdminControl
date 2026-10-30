package ru.admincontrol.lumira.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.admincontrol.client.CwItemPickerScreen;
import ru.admincontrol.lumira.CoinRecipes;
import ru.admincontrol.lumira.LumiraContent;

/**
 * Окно «Изменить крафт Coin лиры и Coin мирты»: бесформенный рецепт до 9 предметов и количество монет на выходе.
 * ЛКМ по ячейке — выбрать предмет, ПКМ — очистить. Пустой рецепт = крафта нет.
 */
@Environment(EnvType.CLIENT)
public final class CoinRecipeScreen extends Screen {
	private static final int CELL = 24;
	private final Screen parent;
	private final String[][] slots = new String[2][9];
	private final int[] counts = {1, 1};
	private boolean loaded;

	public CoinRecipeScreen(Screen parent) {
		super(Text.literal("Крафт монет сюжета"));
		this.parent = parent;
	}

	public static void requestSync() {
		ClientPlayNetworking.send(CoinRecipes.REQUEST, PacketByteBufs.create());
	}

	public static void onSync() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.currentScreen instanceof CoinRecipeScreen s) {
			s.load();
			s.clearAndInit();
		}
	}

	private void load() {
		for (int coin = 0; coin < 2; coin++) {
			java.util.Arrays.fill(slots[coin], null);
			java.util.List<String> list = CoinRecipes.CLIENT_RECIPE[coin];
			for (int i = 0; i < Math.min(9, list.size()); i++) {
				slots[coin][i] = list.get(i);
			}
			counts[coin] = CoinRecipes.CLIENT_COUNT[coin];
		}
		loaded = true;
	}

	private int gridX(int coin) {
		return this.width / 2 - 170 + coin * 190;
	}

	private int gridY() {
		return 70;
	}

	@Override
	protected void init() {
		if (!loaded) {
			load();
		}
		for (int coin = 0; coin < 2; coin++) {
			final int cn = coin;
			int gx = gridX(coin);
			int by = gridY() + 3 * CELL + 8;
			this.addDrawableChild(ButtonWidget.builder(Text.literal("−"), b -> {
				counts[cn] = Math.max(1, counts[cn] - 1);
				clearAndInit();
			}).dimensions(gx, by, 20, 18).build());
			this.addDrawableChild(ButtonWidget.builder(Text.literal("Выдаёт: " + counts[coin]), b -> {
			}).dimensions(gx + 22, by, 80, 18).build());
			this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> {
				counts[cn] = Math.min(64, counts[cn] + 1);
				clearAndInit();
			}).dimensions(gx + 104, by, 20, 18).build());
			this.addDrawableChild(ButtonWidget.builder(Text.literal("Сохранить"), b -> save(cn)).dimensions(gx, by + 22, 124, 18).build());
		}
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Назад"), b -> close()).dimensions(this.width / 2 - 50, this.height - 28, 100, 20).build());
	}

	private void save(int coin) {
		java.util.List<String> chosen = new java.util.ArrayList<>();
		for (String s : slots[coin]) {
			if (s != null) {
				chosen.add(s);
			}
		}
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(coin);
		buf.writeVarInt(counts[coin]);
		buf.writeVarInt(chosen.size());
		for (String s : chosen) {
			buf.writeString(s, 128);
		}
		ClientPlayNetworking.send(CoinRecipes.SET, buf);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		for (int coin = 0; coin < 2; coin++) {
			for (int i = 0; i < 9; i++) {
				int x = gridX(coin) + (i % 3) * CELL;
				int y = gridY() + (i / 3) * CELL;
				if (mx >= x && mx < x + CELL - 2 && my >= y && my < y + CELL - 2) {
					final int cn = coin, idx = i;
					if (button == 1) {
						slots[cn][idx] = null;
					} else {
						this.client.setScreen(new CwItemPickerScreen(this, (Identifier id) -> slots[cn][idx] = id.toString()));
					}
					return true;
				}
			}
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public void render(DrawContext c, int mx, int my, float delta) {
		renderBackground(c);
		c.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFD27A);
		c.drawCenteredTextWithShadow(this.textRenderer, "ЛКМ — выбрать предмет, ПКМ — очистить. Пустая сетка = крафта нет.", this.width / 2, 30, 0xFF9FB3BD);
		String[] names = {"Coin лиры", "Coin мирты"};
		for (int coin = 0; coin < 2; coin++) {
			int gx = gridX(coin);
			c.drawTextWithShadow(this.textRenderer, names[coin], gx, gridY() - 14, 0xFFFFE27A);
			for (int i = 0; i < 9; i++) {
				int x = gx + (i % 3) * CELL;
				int y = gridY() + (i / 3) * CELL;
				c.fill(x, y, x + CELL - 2, y + CELL - 2, 0xFF2B3138);
				c.fill(x + 1, y + 1, x + CELL - 3, y + CELL - 3, 0xFF4A535C);
				if (slots[coin][i] != null) {
					Identifier id = Identifier.tryParse(slots[coin][i]);
					if (id != null && Registries.ITEM.containsId(id)) {
						c.drawItem(new ItemStack(Registries.ITEM.get(id)), x + 3, y + 3);
					}
				}
			}
			c.drawItem(new ItemStack(LumiraContent.item(coin == 0 ? "coin_lyra" : "coin_myrtle")), gx + 3 * CELL + 6, gridY() + CELL + 3);
		}
		super.render(c, mx, my, delta);
	}

	@Override
	public void close() {
		this.client.setScreen(this.parent);
	}
}
