package ru.admincontrol.client;
import net.minecraft.client.gui.DrawContext;import net.minecraft.client.gui.screen.Screen;import net.minecraft.client.gui.widget.ButtonWidget;import net.minecraft.text.Text;import java.util.List;import java.util.function.Consumer;
public final class CwOptionPickerScreen extends Screen{
	public record Option(String label,String value){}private final Screen parent;private final String heading;private final List<Option> options;private final Consumer<String> selected;
	public CwOptionPickerScreen(Screen parent,String heading,List<Option> options,Consumer<String> selected){super(Text.literal(heading));this.parent=parent;this.heading=heading;this.options=options;this.selected=selected;}
	@Override protected void init(){int w=Math.min(280,width-30),y=42;for(Option option:options){addDrawableChild(ButtonWidget.builder(Text.literal(option.label),b->{selected.accept(option.value);if(client!=null)client.setScreen(parent);}).dimensions(width/2-w/2,y,w,20).build());y+=23;}addDrawableChild(ButtonWidget.builder(Text.literal("Назад"),b->close()).dimensions(width/2-50,Math.min(height-28,y+8),100,20).build());}
	@Override public void close(){if(client!=null)client.setScreen(parent);}@Override public boolean shouldPause(){return false;}@Override public void render(DrawContext c,int mx,int my,float d){renderBackground(c);c.drawCenteredTextWithShadow(textRenderer,heading,width/2,16,0xFFFFD27A);super.render(c,mx,my,d);}
}
