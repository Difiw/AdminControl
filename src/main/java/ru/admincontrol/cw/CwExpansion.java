package ru.admincontrol.cw;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.item.ModItems;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-authoritative selection state. The callback only consumes the dedicated brush. */
public final class CwExpansion {
	public static final long DEFAULT_MAX_VOLUME = 1_000_000L;
	public static final double DEFAULT_RAYCAST = 64.0D;
	private static final Map<UUID, CwSelection> SELECTIONS = new ConcurrentHashMap<>();
	private static final Map<UUID, String> MARKED_NPCS = new ConcurrentHashMap<>();
	private static final Map<UUID, java.util.List<BlockPos>> NPC_PATHS = new ConcurrentHashMap<>();
	private static boolean registered;

	private CwExpansion() {}

	public static synchronized void register() {
		if (registered) return;
		registered = true;
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			ItemStack held = player.getStackInHand(hand);
			if (held.isOf(ModItems.PLAYER_MARKER)) {
				if (world.isClient) return ActionResult.SUCCESS;
				if (!(player instanceof ServerPlayerEntity sp) || !CommandUtil.hasRank(sp.getCommandSource(), AdminRanks.GA)) {
					player.sendMessage(Text.literal("[CW] Недостаточно прав для указателя точки игрока."), true);
					return ActionResult.FAIL;
				}
				CwPointRegistry.Point point = CwPointRegistry.record(sp, "start", hit);
				sp.sendMessage(Text.literal(String.format(java.util.Locale.ROOT,
						"[CW] Метка start: %s, %.3f %.3f %.3f, yaw %.1f pitch %.1f",
						point.dimension, point.x, point.y, point.z, point.yaw, point.pitch)), false);
				return ActionResult.SUCCESS;
			}
			if (held.isOf(ModItems.NPC_PATH_TOOL)) {
				if (world.isClient) return ActionResult.SUCCESS;
				if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.FAIL;
				String npc = MARKED_NPCS.get(sp.getUuid());
				if (npc == null) { sp.sendMessage(Text.literal("[CW] Сначала отметьте NPC предметом «Маркер NPC»."), true); return ActionResult.FAIL; }
				var path = NPC_PATHS.computeIfAbsent(sp.getUuid(), k -> new java.util.ArrayList<>());
				if (path.size() >= 256) { sp.sendMessage(Text.literal("[CW] Лимит маршрута — 256 точек."), true); return ActionResult.FAIL; }
				path.add(hit.getBlockPos().toImmutable());
				sp.sendMessage(Text.literal("[CW] Маршрут «" + npc + "»: точка " + path.size() + " = " + coords(hit.getBlockPos())), true);
				return ActionResult.SUCCESS;
			}
			if (held.isOf(ModItems.BUILDER_MARKER)) {
				if (world.isClient) return ActionResult.SUCCESS;
				if (!(player instanceof ServerPlayerEntity sp) || !CommandUtil.hasRank(sp.getCommandSource(), AdminRanks.GA)) {
					player.sendMessage(Text.literal("[CW] Недостаточно прав для маркера строителя."), true);
					return ActionResult.FAIL;
				}
				if (!CwBuilderWorld.inside(sp)) {
					sp.sendMessage(Text.literal("[CW] Маркер работает только в строительном измерении."), true);
					return ActionResult.FAIL;
				}
				int selected=CwBuilderSelection.selectNext(sp,hit.getBlockPos(),hit.getPos(),hit.getSide());
				sp.sendMessage(Text.literal("[CW] Карандаш: точка "+selected+" = "+coords(hit.getBlockPos())+(selected==2?". Область готова к заполнению.":".")),true);
				return ActionResult.SUCCESS;
			}
			if (!held.isOf(ModItems.SELECTION_BRUSH)) return ActionResult.PASS;
			if (world.isClient) return ActionResult.SUCCESS;
			if (!(player instanceof ServerPlayerEntity sp) || !CommandUtil.hasRank(sp.getCommandSource(), AdminRanks.GA)) {
				player.sendMessage(Text.literal("[CW] Недостаточно прав для кисточки."), true);
				return ActionResult.FAIL;
			}
			CwSelection current = get(sp);int point=current.first()==null?1:current.second()==null?2:1;
			if(point==1&&current.complete())current=CwSelection.empty();
			set(sp, current.set(point, world.getRegistryKey(), hit.getBlockPos()));
			player.sendMessage(Text.literal("[CW] Точка " + point + ": " + coords(hit.getBlockPos())), true);
			return ActionResult.SUCCESS;
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClient || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
			if (player.getStackInHand(hand).isOf(ModItems.PLAYER_MARKER) && entity instanceof ServerPlayerEntity target && !(target instanceof ru.admincontrol.util.NpcPlayerEntity)) {
				sp.sendMessage(Text.literal("[CW] Игрок отмечен: " + target.getName().getString() + " (" + target.getUuidAsString() + ")"), false);
				return ActionResult.SUCCESS;
			}
			if (!(entity instanceof ru.admincontrol.util.NpcPlayerEntity npc)) return ActionResult.PASS;
			var saved = ru.admincontrol.util.NpcStore.get(npc.getGameProfile().getName());
			if (player.getStackInHand(hand).isOf(ModItems.NPC_MARKER)) {
				String id = saved != null && saved.logicalId != null && !saved.logicalId.isBlank() ? saved.logicalId : npc.getGameProfile().getName();
				MARKED_NPCS.put(sp.getUuid(), id); NPC_PATHS.remove(sp.getUuid());
				sp.sendMessage(Text.literal("[CW] NPC отмечен: " + id + ". Инструмент маршрута теперь добавляет ему точки."), false);
				return ActionResult.SUCCESS;
			}
			if (player.getStackInHand(hand).isOf(ModItems.NPC_POSE_TOOL)) {
				if (!CommandUtil.hasRank(sp.getCommandSource(), AdminRanks.GA) || !ru.admincontrol.util.CwGuards.developerOnline(sp.getServer())) {
					sp.sendMessage(Text.literal("[CW] Манипулятор недоступен: требуется ГА+ и разработчик в сети."), true);
					return ActionResult.FAIL;
				}
				String id = saved != null && saved.logicalId != null && !saved.logicalId.isBlank() ? saved.logicalId : npc.getGameProfile().getName();
				CwAnimationService.openEditor(sp, npc);
					if (!sp.isSneaking()) return ActionResult.SUCCESS;
					sp.sendMessage(Text.literal("[CW] Быстрые позы NPC «" + id + "»:"), false);
				for (String[] option : new String[][]{{"Стойка", "straight"},{"Смотрит влево", "look_left"},{"Смотрит вправо", "look_right"},{"Голова вверх", "head_up"},{"Голова вниз", "head_down"}}) {
					String command = "/cw npc pose " + id + " " + option[1];
					sp.sendMessage(Text.literal(" • " + option[0]).styled(style -> style.withColor(net.minecraft.util.Formatting.AQUA)
							.withClickEvent(new net.minecraft.text.ClickEvent(net.minecraft.text.ClickEvent.Action.RUN_COMMAND, command))), false);
				}
				for (String[] option : new String[][]{{"Махнуть правой", "wave_right once"},{"Махать правой (цикл)", "wave_right loop"},{"Махнуть левой", "wave_left once"},{"Махать левой (цикл)", "wave_left loop"},{"Остановить анимацию", "none stop"}}) {
					String command = "/cw npc animation " + id + " " + option[1];
					sp.sendMessage(Text.literal(" • " + option[0]).styled(style -> style.withColor(net.minecraft.util.Formatting.LIGHT_PURPLE)
							.withClickEvent(new net.minecraft.text.ClickEvent(net.minecraft.text.ClickEvent.Action.RUN_COMMAND, command))), false);
				}
				return ActionResult.SUCCESS;
			}
			if (saved == null) return ActionResult.PASS;
			if(!saved.storyStart&&saved.dialogue!=null&&!saved.dialogue.isEmpty()){return CwDialogueService.open(sp,npc,saved)?ActionResult.SUCCESS:ActionResult.PASS;}
			if (!saved.storyStart) return ActionResult.PASS;
				CwStoryEntryService.focus(sp, npc.getId());
			if (ru.admincontrol.data.DataManager.get() == null || !ru.admincontrol.data.DataManager.get().settings().cwStoryEnabled) {
				CwStoryEntryService.unavailable(sp);return ActionResult.SUCCESS;
			}
			String error=CwStoryEntryService.offer(sp,"default","builder",1);
			if(error!=null)sp.sendMessage(Text.literal("[CW] "+error),true);
			return ActionResult.SUCCESS;
		});
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack held = player.getStackInHand(hand);
			if(!world.isClient&&player instanceof ServerPlayerEntity sp&&(held.isOf(ModItems.STORY_SAVE_TOKEN)||held.isOf(ModItems.STORY_RESPAWN_TOKEN))){String error=held.isOf(ModItems.STORY_SAVE_TOKEN)?CwCheckpointService.manual(sp):CwCheckpointService.setRespawn(sp);if(error!=null){sp.sendMessage(Text.literal(error),true);return TypedActionResult.fail(held);}if(!sp.getAbilities().creativeMode)held.decrement(1);sp.sendMessage(Text.literal(held.isOf(ModItems.STORY_SAVE_TOKEN)?"Луя • Путь сохранён":"Луя • Точка возрождения установлена"),true);return TypedActionResult.success(held);}
			if (!ModItems.isCwTool(held) || world.isClient) return TypedActionResult.pass(held);
			if (!(player instanceof ServerPlayerEntity sp)) return TypedActionResult.fail(held);
			if(held.isOf(ModItems.SELECTION_BRUSH)||held.isOf(ModItems.BUILDER_MARKER)){
				if(!CommandUtil.hasRank(sp.getCommandSource(),AdminRanks.GA)||!CwBuilderWorld.inside(sp))return TypedActionResult.fail(held);
				net.minecraft.util.hit.BlockHitResult ray=selectionHit(sp);
				BlockPos pos=ray.getBlockPos();
				if(held.isOf(ModItems.BUILDER_MARKER)){int n=CwBuilderSelection.selectNext(sp,pos,ray.getPos(),ray.getSide());sp.sendMessage(Text.literal("[CW] Карандаш: точка "+n+" в воздухе = "+coords(pos)),true);}
				else{CwSelection current=get(sp);int n=current.first()==null?1:current.second()==null?2:1;if(n==1&&current.complete())current=CwSelection.empty();set(sp,current.set(n,world.getRegistryKey(),pos));sp.sendMessage(Text.literal("[CW] Кисть: точка "+n+" в воздухе = "+coords(pos)),true);}
				return TypedActionResult.success(held);
			}
			if (held.isOf(ModItems.TEMPLATE_TELEPORTER)) {
				String error = CwBuilderWorld.inside(sp) ? CwBuilderWorld.leave(sp) : CwBuilderWorld.enter(sp);
				sp.sendMessage(Text.literal(error == null ? "[CW] Телепортация выполнена." : "[CW] " + error), false);
				return error == null ? TypedActionResult.success(held) : TypedActionResult.fail(held);
			}
			if (held.isOf(ModItems.TEST_TELEPORTER)) {
				if (CwStoryService.inTest(sp)) { String error=CwTestEntryService.finish(sp);sp.sendMessage(Text.literal(error==null?"[CW] Тест завершён; вы возвращены назад, прогресс сохранён.":"[CW] "+error), false); }
				else { String error = CwTestEntryService.offer(sp, "default", "builder", 1); if (error != null) sp.sendMessage(Text.literal("[CW] " + error), false); }
				return TypedActionResult.success(held);
			}
			if (held.isOf(ModItems.STORY_TABLET)) {
				if (CommandUtil.hasRank(sp.getCommandSource(), AdminRanks.GA)) { CwGraphNetwork.open(sp); return TypedActionResult.success(held); }
				boolean enabled = ru.admincontrol.data.DataManager.get() != null && ru.admincontrol.data.DataManager.get().settings().cwStoryEnabled;
				sp.sendMessage(Text.literal("[CW] Планшет сюжета • " + (enabled ? "ВКЛ" : "ВЫКЛ") + " • " + CwStoryService.describe(CwStoryService.live(sp.getUuid()))), false);
				sp.sendMessage(Text.literal("[Статус] /cw story status " + sp.getName().getString()).styled(s -> s.withColor(net.minecraft.util.Formatting.AQUA).withClickEvent(new net.minecraft.text.ClickEvent(net.minecraft.text.ClickEvent.Action.RUN_COMMAND, "/cw story status " + sp.getName().getString()))), false);
				return TypedActionResult.success(held);
			}
			sp.sendMessage(Text.literal("[CW] Используйте этот инструмент по подходящей цели: игроку, NPC или блоку."), true);
			return TypedActionResult.success(held);
		});
	}

	public static CwSelection get(ServerPlayerEntity player) {
		return SELECTIONS.getOrDefault(player.getUuid(), CwSelection.empty());
	}
	public static void set(ServerPlayerEntity player, CwSelection selection) { SELECTIONS.put(player.getUuid(), selection); }
	public static void clear(ServerPlayerEntity player) { SELECTIONS.remove(player.getUuid()); }
	public static String coords(BlockPos p) { return p.getX() + " " + p.getY() + " " + p.getZ(); }

	/**
	 * Returns the block under the crosshair. On a real miss we deliberately use the
	 * nearest cell in front of the player's eyes instead of Minecraft's synthetic
	 * 64-block MISS endpoint (which was producing nonsensical selections).
	 */
	private static net.minecraft.util.hit.BlockHitResult selectionHit(ServerPlayerEntity player) {
		Vec3d eye = player.getEyePos();
		Vec3d direction = player.getRotationVec(1.0F);
		Vec3d end = eye.add(direction.multiply(32.0D));
		var hit = player.getWorld().raycast(new RaycastContext(eye, end,
				RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
		if (hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) return hit;
		Vec3d near = eye.add(direction.multiply(2.0D));
		BlockPos pos = BlockPos.ofFloored(near);
		return net.minecraft.util.hit.BlockHitResult.createMissed(near,
				net.minecraft.util.math.Direction.getFacing(direction.x, direction.y, direction.z), pos);
	}
}
