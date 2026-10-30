package ru.admincontrol.mixin.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
	@Accessor("x")
	int adminControl$getX();

	@Accessor("y")
	int adminControl$getY();

	@Accessor("backgroundWidth")
	int adminControl$getBackgroundWidth();

	@Accessor("backgroundHeight")
	int adminControl$getBackgroundHeight();
}
