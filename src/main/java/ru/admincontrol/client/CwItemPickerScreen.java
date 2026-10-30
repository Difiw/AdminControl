package ru.admincontrol.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Visual item catalogue used by the story tablet; no registry ID typing is required. */
public final class CwItemPickerScreen extends Screen {
	private final Screen parent;private final Consumer<Identifier> selected;private TextFieldWidget search;private List<Item> items=List.of();private int scroll;
	private static final int CELL=22,COLS=9,ROWS=6;
	public CwItemPickerScreen(Screen parent,Consumer<Identifier> selected){super(Text.literal("Выбор предмета"));this.parent=parent;this.selected=selected;}
	@Override protected void init(){int w=COLS*CELL;search=new TextFieldWidget(textRenderer,width/2-w/2,30,w,20,Text.literal("Поиск"));search.setPlaceholder(Text.literal("Название предмета…"));search.setChangedListener(v->{scroll=0;refresh();});addDrawableChild(search);addDrawableChild(ButtonWidget.builder(Text.literal("Назад"),b->close()).dimensions(width/2-50,30+ROWS*CELL+60,100,20).build());refresh();}
	private void refresh(){String q=search==null?"":search.getText().trim().toLowerCase(java.util.Locale.ROOT);List<Item> out=new ArrayList<>();for(Item item:Registries.ITEM){ItemStack stack=new ItemStack(item);String id=Registries.ITEM.getId(item).toString().toLowerCase(java.util.Locale.ROOT),name=stack.getName().getString().toLowerCase(java.util.Locale.ROOT);if(q.isEmpty()||id.contains(q)||name.contains(q))out.add(item);}items=List.copyOf(out);scroll=Math.max(0,Math.min(scroll,maxScroll()));}
	private int maxScroll(){return Math.max(0,(items.size()+COLS-1)/COLS-ROWS);}
	@Override public boolean mouseScrolled(double mx,double my,double amount){scroll=Math.max(0,Math.min(maxScroll(),scroll-(int)Math.signum(amount)));return true;}
	@Override public boolean mouseClicked(double mx,double my,int button){if(super.mouseClicked(mx,my,button))return true;int left=width/2-COLS*CELL/2,top=58;int col=(int)((mx-left)/CELL),row=(int)((my-top)/CELL);if(col>=0&&col<COLS&&row>=0&&row<ROWS){int index=(scroll+row)*COLS+col;if(index<items.size()){selected.accept(Registries.ITEM.getId(items.get(index)));if(client!=null)client.setScreen(parent);return true;}}return false;}
	@Override public void close(){if(client!=null)client.setScreen(parent);}
	@Override public boolean shouldPause(){return false;}
	@Override public void render(DrawContext c,int mx,int my,float delta){renderBackground(c);c.drawCenteredTextWithShadow(textRenderer,title,width/2,12,0xFFFFD27A);int left=width/2-COLS*CELL/2,top=58;c.fill(left-5,top-5,left+COLS*CELL+5,top+ROWS*CELL+5,0xEE111722);for(int row=0;row<ROWS;row++)for(int col=0;col<COLS;col++){int i=(scroll+row)*COLS+col,x=left+col*CELL,y=top+row*CELL;c.fill(x,y,x+20,y+20,0xFF303947);if(i<items.size()){ItemStack stack=new ItemStack(items.get(i));c.drawItem(stack,x+2,y+2);if(mx>=x&&mx<x+20&&my>=y&&my<y+20)c.drawTooltip(textRenderer,stack.getTooltip(client==null?null:client.player, TooltipContext.BASIC),mx,my);}}c.drawCenteredTextWithShadow(textRenderer,"Колёсико — прокрутка • найдено: "+items.size(),width/2,top+ROWS*CELL+10,0xFFAFC7DD);super.render(c,mx,my,delta);}
}
