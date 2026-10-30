package ru.admincontrol.mixin.client;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Accesses the inherited renderer model; PlayerEntityRenderer does not declare this field itself. */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccessor {
	@Accessor("model")
	EntityModel<? extends LivingEntity> adminControl$getModel();

	@Accessor("model")
	void adminControl$setModel(EntityModel<? extends LivingEntity> model);
}
