package ru.admincontrol.mixin.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.admincontrol.lumira.LumiraNecklaces;
import ru.admincontrol.lumira.client.LumiraHud;

/**
 * Инвентарь игрока в сюжетных измерениях: тёмная сланцевая панель с бирюзовой рамкой вместо ванильной текстуры
 * (в одном стиле с окнами настройки) и отдельная ячейка ожерелья. Вне сюжета инвентарь остаётся ванильным.
 */
@Mixin(InventoryScreen.class)
public abstract class LumiraInventoryMixin {
	private static final int NECK_X = 140, NECK_Y = 62;

	@Inject(method = "drawBackground", at = @At("HEAD"), cancellable = true)
	private void admin_control$background(DrawContext c, float delta, int mouseX, int mouseY, CallbackInfo ci) {
		if (!LumiraHud.active()) {
			return;
		}
		InventoryScreen self = (InventoryScreen) (Object) this;
		HandledScreenAccessor acc = (HandledScreenAccessor) self;
		int x = acc.adminControl$getX(), y = acc.adminControl$getY(), w = 176, h = 166;
		c.fillGradient(x, y, x + w, y + h, 0xF0222A32, 0xF0161B21);
		c.drawBorder(x, y, w, h, 0xFFB9C6CE);
		c.drawBorder(x + 2, y + 2, w - 4, h - 4, 0xFF2B6F6A);
		for (int[] corner : new int[][]{{x + 4, y + 4}, {x + w - 8, y + 4}, {x + 4, y + h - 8}, {x + w - 8, y + h - 8}}) {
			c.fill(corner[0], corner[1], corner[0] + 4, corner[1] + 4, 0xFF3FE0D0);
			c.fill(corner[0] + 1, corner[1] + 1, corner[0] + 3, corner[1] + 3, 0xFF161B21);
		}
		c.fill(x + 26, y + 8, x + 76, y + 78, 0xC00C1014);
		c.drawBorder(x + 26, y + 8, 50, 70, 0xFF39505A);
		for (Slot slot : self.getScreenHandler().slots) {
			if (!slot.isEnabled()) {
				continue;
			}
			c.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF2B3138);
			c.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xFF4B555F);
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null) {
			InventoryScreen.drawEntity(c, x + 51, y + 75, 30, (float) (x + 51) - mouseX, (float) (y + 75 - 50) - mouseY, mc.player);
		}
		// ячейка ожерелья
		int nx = x + NECK_X, ny = y + NECK_Y;
		c.fill(nx - 1, ny - 1, nx + 17, ny + 17, 0xFF3FE0D0);
		c.fill(nx, ny, nx + 16, ny + 16, 0xFF1B2A30);
		ItemStack neck = LumiraHud.necklaceStack();
		if (!neck.isEmpty()) {
			c.drawItem(neck, nx, ny);
			c.drawItemInSlot(mc.textRenderer, neck, nx, ny);
		} else {
			c.drawText(mc.textRenderer, "✧", nx + 4, ny + 4, 0xFF3FE0D0, false);
		}
		ci.cancel();
	}

	@Inject(method = "drawForeground", at = @At("HEAD"), cancellable = true)
	private void admin_control$foreground(DrawContext c, int mouseX, int mouseY, CallbackInfo ci) {
		if (LumiraHud.active()) {
			MinecraftClient mc = MinecraftClient.getInstance();
			c.drawText(mc.textRenderer, Text.translatable("container.crafting"), 97, 8, 0xFFE8F4F2, false);
			ci.cancel();
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void admin_control$neckClick(double mx, double my, int button, CallbackInfoReturnable<Boolean> cir) {
		if (!LumiraHud.active()) {
			return;
		}
		HandledScreenAccessor acc = (HandledScreenAccessor) (InventoryScreen) (Object) this;
		int nx = acc.adminControl$getX() + NECK_X, ny = acc.adminControl$getY() + NECK_Y;
		if (mx >= nx && mx < nx + 16 && my >= ny && my < ny + 16) {
			ClientPlayNetworking.send(LumiraNecklaces.CLICK, PacketByteBufs.create());
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void admin_control$neckTooltip(DrawContext c, int mx, int my, float delta, CallbackInfo ci) {
		if (!LumiraHud.active()) {
			return;
		}
		HandledScreenAccessor acc = (HandledScreenAccessor) (InventoryScreen) (Object) this;
		int nx = acc.adminControl$getX() + NECK_X, ny = acc.adminControl$getY() + NECK_Y;
		if (mx >= nx && mx < nx + 16 && my >= ny && my < ny + 16) {
			MinecraftClient mc = MinecraftClient.getInstance();
			ItemStack neck = LumiraHud.necklaceStack();
			if (!neck.isEmpty()) {
				c.drawItemTooltip(mc.textRenderer, neck, mx, my);
			} else {
				c.drawTooltip(mc.textRenderer, Text.literal("Ячейка ожерелья: положите ожерелье из курсора"), mx, my);
			}
		}
	}
}
