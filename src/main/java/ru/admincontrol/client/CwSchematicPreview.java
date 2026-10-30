package ru.admincontrol.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.BlockRotation;
import ru.admincontrol.net.ModPackets;

import java.util.List;

/** Client-side ghost of a copied structure. Server keeps the authoritative origin. */
public final class CwSchematicPreview {
	private static boolean active;
	private static int width,height,length,distance=4;
	public record GhostBlock(BlockPos pos,BlockState state){}
	private static List<GhostBlock> blocks=List.of();
	private static BlockPos origin,lastSent;
	private static int rotation,lastRotation=-1;
	private CwSchematicPreview(){}
	public static void receive(boolean show,int w,int h,int l,List<GhostBlock> sample){active=show;width=w;height=h;length=l;blocks=sample;origin=null;lastSent=null;lastRotation=-1;}
	public static boolean active(){return active;}
	public static boolean scroll(double amount){if(!active)return false;distance=Math.max(-32,Math.min(32,distance+(amount>0?1:-1)));update(MinecraftClient.getInstance(),true);return true;}
	public static void tick(MinecraftClient client){if(active)update(client,false);}
	private static void update(MinecraftClient client,boolean force){if(client.player==null||client.world==null)return;Direction facing=client.player.getHorizontalFacing();rotation=switch(facing){case SOUTH->0;case WEST->1;case NORTH->2;case EAST->3;default->0;};origin=client.player.getBlockPos().offset(facing,distance);if(force||!origin.equals(lastSent)||rotation!=lastRotation){lastSent=origin;lastRotation=rotation;var buf=PacketByteBufs.create();buf.writeBlockPos(origin);buf.writeByte(rotation);ClientPlayNetworking.send(ModPackets.CW_SCHEM_TRANSFORM,buf);}}
	private static BlockPos rotated(BlockPos p){return switch(rotation){case 1->new BlockPos(length-1-p.getZ(),p.getY(),p.getX());case 2->new BlockPos(width-1-p.getX(),p.getY(),length-1-p.getZ());case 3->new BlockPos(p.getZ(),p.getY(),width-1-p.getX());default->p;};}
	private static BlockRotation blockRotation(){return switch(rotation){case 1->BlockRotation.CLOCKWISE_90;case 2->BlockRotation.CLOCKWISE_180;case 3->BlockRotation.COUNTERCLOCKWISE_90;default->BlockRotation.NONE;};}
	public static void render(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context){if(!active||origin==null||context.consumers()==null)return;MinecraftClient client=MinecraftClient.getInstance();if(client.world==null)return;MatrixStack matrices=context.matrixStack();var cam=context.camera().getPos();matrices.push();matrices.translate(-cam.x,-cam.y,-cam.z);var lines=context.consumers().getBuffer(RenderLayer.getLines());int rw=(rotation%2==0?width:length),rl=(rotation%2==0?length:width);WorldRenderer.drawBox(matrices,lines,origin.getX(),origin.getY(),origin.getZ(),origin.getX()+rw,origin.getY()+height,origin.getZ()+rl,.3f,.9f,1f,1f);var ghost=context.consumers().getBuffer(RenderLayer.getTranslucent());ghost.fixedColor(255,255,255,105);for(GhostBlock block:blocks){BlockPos rel=rotated(block.pos()),at=origin.add(rel);matrices.push();matrices.translate(at.getX(),at.getY(),at.getZ());client.getBlockRenderManager().renderBlockAsEntity(block.state().rotate(blockRotation()),matrices,layer->ghost,15728880,net.minecraft.client.render.OverlayTexture.DEFAULT_UV);matrices.pop();}ghost.unfixColor();matrices.pop();}
}
