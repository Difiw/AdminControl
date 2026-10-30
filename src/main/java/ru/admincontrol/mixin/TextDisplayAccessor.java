package ru.admincontrol.mixin;

import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DisplayEntity.TextDisplayEntity.class)
public interface TextDisplayAccessor {
	@Invoker("setText")
	void adminControl$setText(Text text);

	@Invoker("getText")
	Text adminControl$getText();
}
