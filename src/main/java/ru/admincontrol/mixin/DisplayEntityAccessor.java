package ru.admincontrol.mixin;

import net.minecraft.entity.decoration.DisplayEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DisplayEntity.class)
public interface DisplayEntityAccessor {
	@Invoker("setBillboardMode")
	void adminControl$setBillboardMode(DisplayEntity.BillboardMode mode);

	@Invoker("setViewRange")
	void adminControl$setViewRange(float range);
}
