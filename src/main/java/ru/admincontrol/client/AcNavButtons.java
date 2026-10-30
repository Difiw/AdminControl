package ru.admincontrol.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import ru.admincontrol.mixin.client.HandledScreenAccessor;
import ru.admincontrol.mixin.client.ScreenInvoker;
import ru.admincontrol.net.ModPackets;
import ru.admincontrol.util.NavAccess;
import ru.admincontrol.util.NavReady;

public final class AcNavButtons {
	private AcNavButtons() {
	}

	public static void add(Screen self) {
		if (self == null || !self.getClass().getName().startsWith("ru.admincontrol.client.")) {
			return;
		}
		if (!(self instanceof HandledScreen<?> handled)) {
			return;
		}
		if (self instanceof NavReady ready && !ready.adminControl$navReady()) {
			return;
		}
		HandledScreenAccessor access = (HandledScreenAccessor) handled;
		int x = access.adminControl$getX();
		int y = access.adminControl$getY();
		int bw = access.adminControl$getBackgroundWidth();
		int bh = access.adminControl$getBackgroundHeight();
		boolean back = handled.getScreenHandler() instanceof NavAccess nav && nav.adminControl$hasBack();
		int gap = 4;
		int btnW = 72;
		int count = back ? 2 : 1;
		int need = count * btnW + (count - 1) * gap;
		ScreenInvoker inv = (ScreenInvoker) self;
		// Под панелью → над панелью → сбоку столбиком → в правом верхнем углу панели.
		int by;
		if (y + bh + 20 <= self.height) {
			by = y + bh + 4;
		} else if (y >= 20) {
			by = y - 20;
		} else {
			int side = self.width - (x + bw);
			int colW = Math.min(btnW, side - 8);
			if (colW >= 48) {
				int bx = x + bw + 4;
				int cy = y + 4;
				if (back) {
					addBack(inv, bx, cy, colW);
					cy += 20;
				}
				addClose(inv, self, bx, cy, colW);
				return;
			}
			int small = 52;
			int bx = x + bw - 4 - small;
			if (back) {
				addBack(inv, bx - small - gap, y + 3, small);
			}
			addClose(inv, self, bx, y + 3, small);
			return;
		}
		int left = x + Math.max(8, (bw - need) / 2);
		int maxRight = x + bw - 8;
		if (left + need > maxRight) {
			left = Math.max(x, maxRight - need);
		}
		if (back) {
			addBack(inv, left, by, btnW);
			left += btnW + gap;
		}
		addClose(inv, self, left, by, btnW);
	}

	private static void addBack(ScreenInvoker inv, int x, int y, int w) {
		inv.adminControl$addChild(ButtonWidget.builder(Text.literal("Назад"), b ->
						ClientPlayNetworking.send(ModPackets.SCREEN_BACK, PacketByteBufs.empty()))
				.dimensions(x, y, w, 16).build());
	}

	private static void addClose(ScreenInvoker inv, Screen self, int x, int y, int w) {
		inv.adminControl$addChild(ButtonWidget.builder(Text.literal("Закрыть"), b -> self.close())
				.dimensions(x, y, w, 16).build());
	}
}
