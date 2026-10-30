package ru.admincontrol.mixin.client;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Mouse.class)
public interface MousePosAccessor {
	@Accessor("x")
	double adminControl$getX();

	@Accessor("y")
	double adminControl$getY();

	@Accessor("x")
	void adminControl$setX(double x);

	@Accessor("y")
	void adminControl$setY(double y);
}
