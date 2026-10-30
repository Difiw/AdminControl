package ru.admincontrol.mixin.client;

import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DisconnectedScreen.class)
public interface DisconnectedScreenAccessor {
	@Accessor("parent")
	Screen adminControl$parent();

	@Accessor("reason")
	Text adminControl$reason();
}
