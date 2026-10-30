package ru.admincontrol.cw;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.item.ModItems;
import ru.admincontrol.util.CwNpcService;
import ru.admincontrol.util.NpcService;
import ru.admincontrol.util.NpcStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** New CW Expedition command tree. Registered once beside the legacy /cw controls. */
public final class CwExpansionCommands {
	private static final Map<UUID,String> LAST_COPY=new ConcurrentHashMap<>();
	private CwExpansionCommands() {}
	public static void register(CommandDispatcher<ServerCommandSource> d) {
		CwSchematicService.register();
		d.register(literal("cw").requires(CommandUtil::canUseCw)
				.then(literal("help").executes(c->help(c.getSource())))
				.then(literal("status").executes(c->status(c.getSource())))
				.then(literal("diagnose").executes(c->diagnose(c.getSource())))
				.then(literal("reload").requires(s->rank(s,AdminRanks.TECH)).executes(c->reload(c.getSource())))
				.then(literal("wand").requires(s->rank(s,AdminRanks.GA)).executes(c->wand(c.getSource())))
				.then(literal("pos1").requires(s->rank(s,AdminRanks.GA)).executes(c->rayPoint(c.getSource(),1)))
				.then(literal("pos2").requires(s->rank(s,AdminRanks.GA)).executes(c->rayPoint(c.getSource(),2)))
				.then(literal("point").requires(s->rank(s,AdminRanks.GA))
						.then(literal("1").executes(c->activePoint(c.getSource(),1)))
						.then(literal("2").executes(c->activePoint(c.getSource(),2))))
				.then(literal("selection").requires(s->rank(s,AdminRanks.GA)).executes(c->selection(c.getSource()))
						.then(literal("clear").executes(c->clearSelection(c.getSource()))))
				.then(toolsTree()).then(schemTree()).then(npcTree()).then(storyTree()).then(adminTree()));
		d.register(literal("copy").requires(s->rank(s,AdminRanks.GA)).then(argument("name",StringArgumentType.word()).executes(c->copyAlias(c.getSource(),StringArgumentType.getString(c,"name")))));
		d.register(literal("paste").requires(s->rank(s,AdminRanks.GA)).executes(c->pasteAlias(c.getSource(),null)).then(argument("name",StringArgumentType.word()).executes(c->pasteAlias(c.getSource(),StringArgumentType.getString(c,"name")))));
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> toolsTree(){return literal("tools").requires(s->rank(s,AdminRanks.GA))
			.then(literal("give").then(argument("tool",StringArgumentType.word()).suggests((c,b)->CommandSource.suggestMatching(
					List.of("all","selection","player_marker","npc_marker","story_tablet","npc_path","template_tp","test_tp","npc_pose","builder_marker"),b))
					.executes(c->toolsGive(c.getSource(),StringArgumentType.getString(c,"tool"),self(c.getSource())))
					.then(argument("player",EntityArgumentType.player()).executes(c->toolsGive(c.getSource(),StringArgumentType.getString(c,"tool"),EntityArgumentType.getPlayer(c,"player"))))));}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> schemTree(){return literal("schem").requires(s->rank(s,AdminRanks.GA))
			.then(literal("save").then(argument("name",StringArgumentType.word()).executes(c->schemSave(c.getSource(),StringArgumentType.getString(c,"name")))))
			.then(literal("list").executes(c->schemList(c.getSource())))
			.then(literal("info").then(argument("name",StringArgumentType.word()).executes(c->schemInfo(c.getSource(),StringArgumentType.getString(c,"name")))))
			.then(literal("load").then(argument("name",StringArgumentType.word()).executes(c->schemLoad(c.getSource(),StringArgumentType.getString(c,"name")))))
			.then(literal("preview").executes(c->schemPreview(c.getSource())))
			.then(literal("paste").requires(s->rank(s,AdminRanks.GA)).executes(c->needConfirm(c.getSource(),"/cw schem paste confirm"))
					.then(literal("confirm").executes(c->schemPaste(c.getSource(),null)))
					.then(argument("x",IntegerArgumentType.integer()).then(argument("y",IntegerArgumentType.integer()).then(argument("z",IntegerArgumentType.integer())
							.then(literal("confirm").executes(c->schemPaste(c.getSource(),new BlockPos(IntegerArgumentType.getInteger(c,"x"),IntegerArgumentType.getInteger(c,"y"),IntegerArgumentType.getInteger(c,"z")))))))))
			.then(literal("cancel").executes(c->schemCancel(c.getSource())));}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> npcTree(){return literal("npc").requires(s->rank(s,AdminRanks.GA))
			.then(literal("help").executes(c->npcHelp(c.getSource()))).then(literal("list").executes(c->npcList(c.getSource())))
			.then(literal("create").then(argument("name",StringArgumentType.word()).then(argument("skin",StringArgumentType.word())
					.suggests((c,b)->CommandSource.suggestMatching(CwNpcService.skins(),b))
					.executes(c->npcCreate(c.getSource(),StringArgumentType.getString(c,"name"),StringArgumentType.getString(c,"skin"))))))
			.then(literal("remove").then(argument("id",StringArgumentType.word()).executes(c->npcRemove(c.getSource(),StringArgumentType.getString(c,"id")))))
			.then(literal("tp").then(argument("id",StringArgumentType.word()).executes(c->npcTp(c.getSource(),StringArgumentType.getString(c,"id")))))
			.then(literal("info").then(argument("id",StringArgumentType.word()).executes(c->npcInfo(c.getSource(),StringArgumentType.getString(c,"id")))))
			.then(literal("skin").then(literal("list").executes(c->skinList(c.getSource())))
					.then(literal("reload").executes(c->reload(c.getSource())))
					.then(literal("info").then(argument("skin",StringArgumentType.word()).executes(c->skinInfo(c.getSource(),StringArgumentType.getString(c,"skin")))))
					.then(literal("set").then(argument("id",StringArgumentType.word()).then(argument("skin",StringArgumentType.word()).executes(c->skinSet(c.getSource(),StringArgumentType.getString(c,"id"),StringArgumentType.getString(c,"skin")))))))
			.then(literal("dialogue").then(literal("set").then(argument("id",StringArgumentType.word()).then(argument("key",StringArgumentType.word()).then(argument("text",StringArgumentType.greedyString()).executes(c->dialogueSet(c.getSource(),StringArgumentType.getString(c,"id"),StringArgumentType.getString(c,"key"),StringArgumentType.getString(c,"text")))))))
					.then(literal("test").then(argument("id",StringArgumentType.word()).executes(c->dialogueTest(c.getSource(),StringArgumentType.getString(c,"id"))))))
			.then(literal("start").then(literal("set").then(argument("id",StringArgumentType.word()).executes(c->startSet(c.getSource(),StringArgumentType.getString(c,"id")))))
					.then(literal("info").executes(c->startInfo(c.getSource()))))
			.then(literal("pose").then(argument("id",StringArgumentType.word()).then(argument("pose",StringArgumentType.word())
					.suggests((c,b)->CommandSource.suggestMatching(List.of("straight","look_left","look_right","head_up","head_down"),b))
					.executes(c->npcPose(c.getSource(),StringArgumentType.getString(c,"id"),StringArgumentType.getString(c,"pose"))))))
			.then(literal("animation").then(argument("id",StringArgumentType.word()).then(argument("animation",StringArgumentType.word())
					.suggests((c,b)->CommandSource.suggestMatching(List.of("wave_left","wave_right","none"),b))
					.then(argument("mode",StringArgumentType.word()).suggests((c,b)->CommandSource.suggestMatching(List.of("once","loop","stop"),b))
							.executes(c->npcAnimation(c.getSource(),StringArgumentType.getString(c,"id"),StringArgumentType.getString(c,"animation"),StringArgumentType.getString(c,"mode")))))))
			.then(literal("debug").then(argument("id",StringArgumentType.word()).executes(c->npcInfo(c.getSource(),StringArgumentType.getString(c,"id")))));}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> storyTree(){return literal("story")
			.then(literal("enable").requires(s->rank(s,AdminRanks.GA)).executes(c->storyToggle(c.getSource(),true,false))
					.then(literal("announce").executes(c->storyToggle(c.getSource(),true,true))))
			.then(literal("disable").requires(s->rank(s,AdminRanks.GA)).executes(c->storyToggle(c.getSource(),false,false))
					.then(literal("announce").executes(c->storyToggle(c.getSource(),false,true))))
			.then(literal("resources").requires(s->rank(s,AdminRanks.DEV))
					.then(literal("status").executes(c->storyResources(c.getSource(),null)))
					.then(literal("reload").executes(c->storyResources(c.getSource(),null)))
					.then(literal("set").then(argument("path",StringArgumentType.greedyString()).executes(c->storyResources(c.getSource(),StringArgumentType.getString(c,"path"))))))
			.then(literal("status").then(argument("player",EntityArgumentType.player()).executes(c->storyStatus(c.getSource(),EntityArgumentType.getPlayer(c,"player")))))
			.then(literal("start").requires(s->rank(s,AdminRanks.ZGA)).then(argument("player",EntityArgumentType.player()).executes(c->storyChange(c.getSource(),EntityArgumentType.getPlayer(c,"player"),"start",null))))
			.then(literal("set-stage").requires(s->rank(s,AdminRanks.ZGA)).then(argument("player",EntityArgumentType.player()).then(argument("stage",StringArgumentType.word()).executes(c->storyChange(c.getSource(),EntityArgumentType.getPlayer(c,"player"),"stage",StringArgumentType.getString(c,"stage"))))))
			.then(literal("complete").requires(s->rank(s,AdminRanks.ZGA)).then(argument("player",EntityArgumentType.player()).then(argument("stage",StringArgumentType.word()).executes(c->storyChange(c.getSource(),EntityArgumentType.getPlayer(c,"player"),"complete",StringArgumentType.getString(c,"stage"))))))
			.then(literal("pause").requires(s->rank(s,AdminRanks.SENIOR)).then(argument("player",EntityArgumentType.player()).executes(c->storyChange(c.getSource(),EntityArgumentType.getPlayer(c,"player"),"pause",null))))
			.then(literal("resume").requires(s->rank(s,AdminRanks.SENIOR)).then(argument("player",EntityArgumentType.player()).executes(c->storyChange(c.getSource(),EntityArgumentType.getPlayer(c,"player"),"resume",null))))
			.then(literal("reset").requires(s->rank(s,AdminRanks.ZGA)).then(argument("player",EntityArgumentType.player()).then(literal("confirm").executes(c->storyChange(c.getSource(),EntityArgumentType.getPlayer(c,"player"),"reset",null)))))
			.then(literal("reset-uuid").requires(s->rank(s,AdminRanks.ZGA)).then(argument("uuid",StringArgumentType.word()).executes(c->needConfirm(c.getSource(),"/cw story reset-uuid "+StringArgumentType.getString(c,"uuid")+" confirm")).then(literal("confirm").executes(c->storyResetUuid(c.getSource(),StringArgumentType.getString(c,"uuid"))))))
			.then(literal("time").requires(s->rank(s,AdminRanks.ZGA)).then(argument("player",EntityArgumentType.player())
					.then(argument("ticks",IntegerArgumentType.integer(0,23999)).executes(c->storyTime(c.getSource(),EntityArgumentType.getPlayer(c,"player"),IntegerArgumentType.getInteger(c,"ticks"))))
					.then(literal("day").executes(c->storyTime(c.getSource(),EntityArgumentType.getPlayer(c,"player"),1000)))
					.then(literal("noon").executes(c->storyTime(c.getSource(),EntityArgumentType.getPlayer(c,"player"),6000)))
					.then(literal("night").executes(c->storyTime(c.getSource(),EntityArgumentType.getPlayer(c,"player"),13000)))
					.then(literal("midnight").executes(c->storyTime(c.getSource(),EntityArgumentType.getPlayer(c,"player"),18000)))))
			.then(literal("flags").then(argument("player",EntityArgumentType.player()).executes(c->storyFlags(c.getSource(),EntityArgumentType.getPlayer(c,"player")))))
			.then(testTree());}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> testTree(){return literal("test").requires(s->rank(s,AdminRanks.TECH))
			.then(literal("enter").executes(c->testEnter(c.getSource(),self(c.getSource()))).then(argument("player",EntityArgumentType.player()).executes(c->testEnter(c.getSource(),EntityArgumentType.getPlayer(c,"player")))))
			.then(literal("exit").executes(c->testExit(c.getSource()))).then(literal("status").executes(c->testStatus(c.getSource())))
			.then(literal("set-stage").then(argument("stage",StringArgumentType.word()).executes(c->testStage(c.getSource(),StringArgumentType.getString(c,"stage")))))
			.then(literal("reset").then(literal("confirm").executes(c->testReset(c.getSource()))));}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> adminTree(){return literal("admin").requires(s->rank(s,AdminRanks.SENIOR))
			.then(literal("help").executes(c->adminHelp(c.getSource())))
			.then(literal("player").then(argument("player",EntityArgumentType.player()).executes(c->storyStatus(c.getSource(),EntityArgumentType.getPlayer(c,"player")))))
			.then(literal("tp").then(argument("player",EntityArgumentType.player()).executes(c->tp(c.getSource(),EntityArgumentType.getPlayer(c,"player"),false))))
			.then(literal("bring").then(argument("player",EntityArgumentType.player()).executes(c->tp(c.getSource(),EntityArgumentType.getPlayer(c,"player"),true))))
			.then(literal("inventory").then(literal("view").then(argument("player",EntityArgumentType.player()).executes(c->invView(c.getSource(),EntityArgumentType.getPlayer(c,"player")))))
					.then(literal("give").requires(s->rank(s,AdminRanks.ZGA)).then(argument("player",EntityArgumentType.player()).then(argument("item",IdentifierArgumentType.identifier()).then(argument("count",IntegerArgumentType.integer(1,2304)).then(literal("confirm").executes(c->invGive(c.getSource(),EntityArgumentType.getPlayer(c,"player"),IdentifierArgumentType.getIdentifier(c,"item"),IntegerArgumentType.getInteger(c,"count"))))))))
					.then(literal("take").requires(s->rank(s,AdminRanks.ZGA)).then(argument("player",EntityArgumentType.player()).then(argument("item",IdentifierArgumentType.identifier()).then(argument("count",IntegerArgumentType.integer(1,2304)).then(literal("confirm").executes(c->invTake(c.getSource(),EntityArgumentType.getPlayer(c,"player"),IdentifierArgumentType.getIdentifier(c,"item"),IntegerArgumentType.getInteger(c,"count")))))))))
			.then(literal("xp").requires(s->rank(s,AdminRanks.ZGA)).then(literal("give").then(argument("player",EntityArgumentType.player()).then(argument("amount",IntegerArgumentType.integer(1,1000000)).then(literal("confirm").executes(c->xp(c.getSource(),EntityArgumentType.getPlayer(c,"player"),IntegerArgumentType.getInteger(c,"amount"))))))));}

	private static boolean rank(ServerCommandSource s,int min){return CommandUtil.hasRank(s,min) && CommandUtil.sourceRank(s)!=AdminRanks.BLOGGER;}
	private static ServerPlayerEntity self(ServerCommandSource s){try{return s.getPlayer();}catch(Exception e){return null;}}
	private static int ok(ServerCommandSource s,String m){s.sendFeedback(()->Text.literal("[CW] "+m),false);return 1;} private static int err(ServerCommandSource s,String m){s.sendError(Text.literal("[CW] "+m));return 0;}
	private static int help(ServerCommandSource s){for(String l:List.of("/cw status|diagnose|reload — диагностика (Senior+/Tech)","/cw tools give <all|...|npc_pose> [player] — инструменты CW (GA+; разработчик онлайн)","/cw wand|pos1|pos2|point|selection — выделение (GA+)","/cw schem save|list|info|load|preview|paste|cancel — Sponge .schem (GA+)","/cw npc ... — NPC, скины, позы и анимации (GA+)","/cw story ... — прогресс игроков (Senior+/ZGA+)","/cw story test ... — изолированный тест (Tech+)","/cw admin ... — помощь игрокам с аудитом"))s.sendFeedback(()->Text.literal(l),false);return 1;}
	private static int status(ServerCommandSource s){return ok(s,"CW Expansion активен; схемы: "+CwSchematicService.folder()+"; скины: "+CwNpcService.folder());}
	private static int diagnose(ServerCommandSource s){return ok(s,"rank="+AdminRanks.title(CommandUtil.sourceRank(s))+", schematics="+Files.isDirectory(CwSchematicService.folder())+", skins="+Files.isDirectory(CwNpcService.folder()));}
	private static int reload(ServerCommandSource s){String e=CwNpcService.reload();return e==null?ok(s,"Скины NPC перечитаны."):err(s,e);}
	private static int storyToggle(ServerCommandSource s, boolean enabled, boolean announce) {
		var data = ru.admincontrol.data.DataManager.get();
		if (data == null) return err(s, "Данные сервера ещё не загружены.");
		data.settings().cwStoryEnabled = enabled; data.saveAll();
		if (announce) ru.admincontrol.util.Chat.broadcast(s.getServer(), Text.literal("[CW Expedition] Сюжет "
				+ (enabled ? "открыт для прохождения." : "временно отключён администрацией.")));
		CwAudit.write(s, "story toggle", "global", (enabled ? "enabled" : "disabled") + (announce ? " announced" : ""));
		return ok(s, "Сюжет CW Expedition: " + (enabled ? "включён" : "выключён") + (announce ? "; объявление отправлено" : ""));
	}
	private static int storyResources(ServerCommandSource s,String raw){var data=ru.admincontrol.data.DataManager.get();if(data==null)return err(s,"Данные сервера ещё не загружены.");if(raw!=null){String value=raw.trim().replace('\\','/');if(value.isBlank()||value.startsWith("/")||value.matches("^[A-Za-z]:.*")||value.contains("../")||value.equals(".."))return err(s,"Путь должен быть относительным и не содержать '..'.");data.settings().cwStoryResourcePath=value;data.saveAll();}Path root=net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();Path dir=root.resolve(data.settings().cwStoryResourcePath).normalize();if(!dir.startsWith(root))return err(s,"Путь выходит за корень сервера.");try{Files.createDirectories(dir);Path realRoot=root.toRealPath(),realDir=dir.toRealPath();if(!realDir.startsWith(realRoot))return err(s,"Символическая ссылка выводит путь за корень сервера.");}catch(Exception e){return err(s,"Не удалось открыть папку ресурсов: "+e.getMessage());}return ok(s,"Ресурсы сюжета: "+dir+"; состояние: "+(data.settings().cwStoryEnabled?"включено":"выключено"));}
	private static int wand(ServerCommandSource s){ServerPlayerEntity p=self(s);if(p==null)return err(s,"Требуется игрок.");ItemStack stack=new ItemStack(ModItems.SELECTION_BRUSH);if(!p.getInventory().insertStack(stack))p.dropItem(stack,false);return ok(s,"Кисточка выдана.");}
	private static int toolsGive(ServerCommandSource s,String id,ServerPlayerEntity p){
		if(p==null)return err(s,"Укажите игрока: консоль не имеет инвентаря.");
		if(!ru.admincontrol.util.CwGuards.developerOnline(s.getServer()))return err(s,"Инструменты CW недоступны: разработчик не находится в сети.");
		java.util.Map<String,net.minecraft.item.Item> map=java.util.Map.of(
				"selection",ModItems.SELECTION_BRUSH,"player_marker",ModItems.PLAYER_MARKER,
				"npc_marker",ModItems.NPC_MARKER,"story_tablet",ModItems.STORY_TABLET,
				"npc_path",ModItems.NPC_PATH_TOOL,"template_tp",ModItems.TEMPLATE_TELEPORTER,
				"test_tp",ModItems.TEST_TELEPORTER,"npc_pose",ModItems.NPC_POSE_TOOL,"builder_marker",ModItems.BUILDER_MARKER);
		java.util.List<net.minecraft.item.Item> chosen="all".equals(id)?ModItems.CW_TOOLS:
				(map.containsKey(id)?java.util.List.of(map.get(id)):java.util.List.of());
		if(chosen.isEmpty())return err(s,"Неизвестный инструмент. Используйте /cw tools give all.");
		for(var item:chosen){ItemStack stack=new ItemStack(item);if(!p.getInventory().insertStack(stack))p.dropItem(stack,false);}
		CwAudit.write(s,"tools give",p.getName().getString(),id);
		return ok(s,"Выдано инструментов: "+chosen.size()+" → "+p.getName().getString()+".");
	}
	private static int activePoint(ServerCommandSource s,int n){ServerPlayerEntity p=self(s);if(p==null)return err(s,"Требуется игрок.");CwExpansion.set(p,CwExpansion.get(p).active(n));return ok(s,"Активна точка "+n+".");}
	private static int rayPoint(ServerCommandSource s,int n){ServerPlayerEntity p=self(s);if(p==null)return err(s,"Требуется игрок.");var hit=p.getWorld().raycast(new RaycastContext(p.getEyePos(),p.getEyePos().add(p.getRotationVec(1).multiply(CwExpansion.DEFAULT_RAYCAST)),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,p));if(hit.getType()!=HitResult.Type.BLOCK)return err(s,"Блок в пределах 64 не найден.");BlockPos pos=((BlockHitResult)hit).getBlockPos();CwExpansion.set(p,CwExpansion.get(p).set(n,p.getWorld().getRegistryKey(),pos));return ok(s,"Точка "+n+": "+CwExpansion.coords(pos));}
	private static int selection(ServerCommandSource s){ServerPlayerEntity p=self(s);if(p==null)return err(s,"Требуется игрок.");CwSelection q=CwExpansion.get(p);if(!q.complete())return err(s,"Задайте обе точки.");var b=q.box();return ok(s,"pos1="+CwExpansion.coords(q.first().pos())+", pos2="+CwExpansion.coords(q.second().pos())+", размер="+b.getBlockCountX()+"×"+b.getBlockCountY()+"×"+b.getBlockCountZ()+", блоков="+q.volume()+(q.sameWorld()?"":"; РАЗНЫЕ ИЗМЕРЕНИЯ"));}
	private static int clearSelection(ServerCommandSource s){ServerPlayerEntity p=self(s);if(p==null)return err(s,"Требуется игрок.");CwExpansion.clear(p);return ok(s,"Выделение очищено.");}
	private static int schemSave(ServerCommandSource s,String n){ServerPlayerEntity p=self(s);if(p==null)return err(s,"Требуется игрок.");String e=CwSchematicService.save(p,n,CwExpansion.get(p));CwAudit.write(s,"schem save",n,e==null?"ok":e);if(e==null){LAST_COPY.put(p.getUuid(),n);CwExpansion.clear(p);}return e==null?ok(s,"Копия «"+n+"» сохранена навсегда; точки очищены."):err(s,e);}
	private static int copyAlias(ServerCommandSource s,String n){return schemSave(s,n);}
	private static int pasteAlias(ServerCommandSource s,String requested){ServerPlayerEntity p=self(s);if(p==null)return err(s,"Требуется игрок.");String name=requested==null?LAST_COPY.get(p.getUuid()):requested;if(name==null)return err(s,"Нет последней копии. Используйте /paste <имя>.");String e=CwSchematicService.load(p,name);if(e!=null)return err(s,e);LAST_COPY.put(p.getUuid(),name);var q=CwSchematicService.loaded(p);CwSchematicService.preview(p);s.sendFeedback(()->Text.literal("[CW] Призрачная копия «"+name+"»: "+q.width()+"×"+q.height()+"×"+q.length()+". Колесо — ближе/дальше; движение и взгляд меняют позицию."),false);s.sendFeedback(()->Text.literal("[ПОДТВЕРДИТЬ ВСТАВКУ]").styled(st->st.withColor(net.minecraft.util.Formatting.GREEN).withBold(true).withClickEvent(new net.minecraft.text.ClickEvent(net.minecraft.text.ClickEvent.Action.RUN_COMMAND,"/cw schem paste confirm"))),false);return 1;}
	private static int schemLoad(ServerCommandSource s,String n){ServerPlayerEntity p=self(s);if(p==null)return err(s,"Требуется игрок.");String e=CwSchematicService.load(p,n);return e==null?schemPreview(s):err(s,e);}
	private static int schemInfo(ServerCommandSource s,String n){return schemLoad(s,n);}
	private static int schemPreview(ServerCommandSource s){ServerPlayerEntity p=self(s);var q=p==null?null:CwSchematicService.loaded(p);if(q==null)return err(s,"Схема не загружена.");CwSchematicService.preview(p);return ok(s,q.name()+": "+q.width()+"×"+q.height()+"×"+q.length()+", "+q.volume()+" блоков; предупреждения="+q.warnings());}
	private static int schemList(ServerCommandSource s){try{Files.createDirectories(CwSchematicService.folder());try(var f=Files.list(CwSchematicService.folder())){var names=f.filter(p->p.getFileName().toString().endsWith(".schem")).map(p->p.getFileName().toString()).sorted().toList();return ok(s,names.isEmpty()?"Схем нет.":String.join(", ",names));}}catch(Exception e){return err(s,e.getMessage());}}
	private static int needConfirm(ServerCommandSource s,String usage){return err(s,"Требуется явное подтверждение: "+usage);}
	private static int schemPaste(ServerCommandSource s,BlockPos pos){ServerPlayerEntity p=self(s);if(p==null)return err(s,"Требуется игрок.");BlockPos at=pos==null?p.getBlockPos():pos;String e=CwSchematicService.startPaste(p,at);CwAudit.write(s,"schem paste",at.toShortString(),e==null?"started":e);return e==null?ok(s,"Пакетная вставка начата; rollback не обещается. /cw schem cancel — остановить."):err(s,e);}
	private static int schemCancel(ServerCommandSource s){ServerPlayerEntity p=self(s);return p!=null&&CwSchematicService.cancel(p)?ok(s,"Вставка отменена; уже размещённые блоки не откатываются."):err(s,"Активной вставки нет.");}
	private static int npcHelp(ServerCommandSource s){return ok(s,"npc: create <name> <skin>, list/remove/tp/info, pose <id> <pose>, animation <id> <animation> <once|loop|stop>, skin, dialogue, start, debug");}
	private static NpcStore.Saved npc(String id){return NpcStore.byLogicalId(id);}
	private static int npcList(ServerCommandSource s){var list=NpcStore.all().stream().filter(n->n.logicalId!=null&&!n.logicalId.isBlank()).map(n->n.logicalId+" ("+n.displayName+")").toList();return ok(s,list.isEmpty()?"CW NPC нет.":String.join(", ",list));}
	private static int npcCreate(ServerCommandSource s,String name,String skin){ServerPlayerEntity p=self(s);if(p==null)return err(s,"Требуется игрок.");if(!name.matches("[\\p{L}\\p{N}_]{1,16}"))return err(s,"Имя NPC: 1–16 символов, буквы (включая русские), цифры/_.");if(npc(name)!=null)return err(s,"NPC с таким именем уже существует.");String validation=CwNpcService.validate(skin);if(validation!=null)return err(s,validation);String e=NpcService.spawnSkinned(p,skin,name);if(e!=null)return err(s,e);NpcStore.setCwMetadata(name,name,name,false);NpcStore.setSkin(name,skin);NpcStore.setPose(name,"straight","none",false);CwAudit.write(s,"npc create",name,"skin="+skin);return ok(s,"NPC «"+name+"» создан со скином "+skin+".png и сохранён.");}
	private static int npcPose(ServerCommandSource s,String id,String pose){var n=npc(id);var entity=n==null?null:NpcService.find(s.getServer(),n.nick);String e=ru.admincontrol.cw.CwNpcPoseService.setPose(entity,pose);return e==null?ok(s,"Поза "+pose+" применена к "+id+" и сохранена."):err(s,e);}
	private static int npcAnimation(ServerCommandSource s,String id,String animation,String mode){var n=npc(id);var entity=n==null?null:NpcService.find(s.getServer(),n.nick);if("stop".equals(mode)){animation="none";}else if(!"once".equals(mode)&&!"loop".equals(mode))return err(s,"Режим: once, loop или stop.");String e=ru.admincontrol.cw.CwNpcPoseService.animate(entity,animation,"loop".equals(mode));return e==null?ok(s,"Анимация "+animation+" ("+mode+") применена к "+id+" и сохранена."):err(s,e);}
	private static int npcRemove(ServerCommandSource s,String id){var n=npc(id);if(n==null)return err(s,"NPC не найден.");String e=NpcService.remove(s.getServer(),n.nick);CwAudit.write(s,"npc remove",id,e==null?"ok":e);return e==null?ok(s,"NPC удалён."):err(s,e);}
	private static int npcTp(ServerCommandSource s,String id){ServerPlayerEntity p=self(s);var n=npc(id);var entity=n==null?null:NpcService.find(s.getServer(),n.nick);if(p==null||entity==null)return err(s,"NPC не найден в мире.");p.teleport(entity.getServerWorld(),entity.getX(),entity.getY(),entity.getZ(),entity.getYaw(),entity.getPitch());CwAudit.write(s,"npc tp",id,"ok");return ok(s,"Телепортировано к NPC "+id+".");}
	private static int npcInfo(ServerCommandSource s,String id){var n=npc(id);return n==null?err(s,"NPC не найден."):ok(s,"id="+n.logicalId+", name="+n.displayName+", entity="+n.nick+", skin="+n.skinId+", start="+n.storyStart+", present="+(NpcService.find(s.getServer(),n.nick)!=null));}
	private static int skinList(ServerCommandSource s){var x=CwNpcService.skins();return x.isEmpty()?err(s,CwNpcService.scanError()):ok(s,String.join(", ",x));}
	private static int skinInfo(ServerCommandSource s,String id){String e=CwNpcService.info(id);return e.startsWith("[CW NPC]")?err(s,e):ok(s,e);}
	private static int skinSet(ServerCommandSource s,String id,String skin){var n=npc(id);if(n==null)return err(s,"NPC не найден.");String e=CwNpcService.validate(skin);if(e!=null)return err(s,e);NpcStore.setSkin(n.nick,skin);var entity=NpcService.find(s.getServer(),n.nick);if(entity!=null)CwNpcService.syncSkinToAll(s.getServer(),entity.getUuid(),skin);return ok(s,"Скин "+skin+" назначен "+id+".");}
	private static int dialogueSet(ServerCommandSource s,String id,String key,String text){if(npc(id)==null)return err(s,"NPC не найден.");NpcStore.setDialogue(id,key,text);return ok(s,"Диалог сохранён.");}
	private static int dialogueTest(ServerCommandSource s,String id){var n=npc(id);if(n==null)return err(s,"NPC не найден.");return ok(s,"Диалоги "+id+": "+n.dialogue);}
	private static int startSet(ServerCommandSource s,String id){var n=npc(id);if(n==null)return err(s,"NPC не найден.");for(var x:NpcStore.all())if(x.storyStart)NpcStore.setCwMetadata(x.nick,x.logicalId,x.displayName,false);NpcStore.setCwMetadata(n.nick,n.logicalId,n.displayName,true);return ok(s,"Стартовый NPC: "+id);}
	private static int startInfo(ServerCommandSource s){for(var n:NpcStore.all())if(n.storyStart)return npcInfo(s,n.logicalId);return err(s,"Стартовый NPC не назначен.");}
	private static int storyStatus(ServerCommandSource s,ServerPlayerEntity p){return ok(s,p.getName().getString()+": "+CwStoryService.describe(CwStoryService.live(p.getUuid())));}
	private static int storyFlags(ServerCommandSource s,ServerPlayerEntity p){return ok(s,p.getName().getString()+": "+CwStoryService.live(p.getUuid()).flags);}
	private static int storyChange(ServerCommandSource s,ServerPlayerEntity p,String op,String arg){
		if("reset".equals(op)){String error=CwStoryWorldService.resetPlayer(p);if(error!=null){CwAudit.write(s,"story reset",p.getName().getString(),"failed: "+error);return err(s,error);}CwStoryService.reset(p.getUuid());CwAudit.write(s,"story reset",p.getName().getString(),"complete; instance reset scheduled");return ok(s,"Прогресс, обе точки, время, профиль и instance игрока сброшены. (Устаревший путь: используйте /delstory <ник> <причина>.)");}
		var q=CwStoryService.live(p.getUuid());switch(op){case"start"->{if(q.chapter==0){q.chapter=1;q.stageId="chapter1_start";}}case"stage"->q.stageId=arg;case"complete"->q.completed.add(arg);case"pause"->q.paused=true;case"resume"->q.paused=false;}CwStoryService.touch(q,s.getName());CwStoryService.save();CwAudit.write(s,"story "+op,p.getName().getString(),"ok");return storyStatus(s,p);}
	private static int storyTime(ServerCommandSource s,ServerPlayerEntity p,int ticks){if(!CwStoryClock.set(p,ticks))return err(s,"Игрок не находится в своём сюжетном instance.");CwAudit.write(s,"story time",p.getName().getString(),Integer.toString(ticks));return ok(s,"Сюжетное время "+p.getName().getString()+" = "+ticks+"; другие миры не изменены.");}
	private static int storyResetUuid(ServerCommandSource s,String raw){java.util.UUID id;try{id=java.util.UUID.fromString(raw);}catch(Exception e){return err(s,"Некорректный UUID.");}if(s.getServer().getPlayerManager().getPlayer(id)!=null)return err(s,"Игрок online: используйте /cw story reset <игрок> confirm для безопасного выхода.");String error=CwStoryWorldService.resetOffline(id);CwAudit.write(s,"story reset offline",id.toString(),error==null?"complete":error);return error==null?ok(s,"Ветка offline-игрока "+id+" транзакционно сброшена."):err(s,error);}
	private static int testEnter(ServerCommandSource s,ServerPlayerEntity target){ServerPlayerEntity a=self(s);if(a==null||target==null)return err(s,"Требуется игрок.");CwStoryService.enterTest(a,target.getUuid());return ok(s,"CW TEST MODE — изменения не сохраняются в обычный сюжет. Target: "+target.getName().getString());}
	private static int testExit(ServerCommandSource s){ServerPlayerEntity a=self(s);if(a==null)return err(s,"Требуется игрок.");CwStoryService.exitTest(a);return ok(s,"Тестовый режим завершён; отдельный тестовый прогресс сохранён.");}
	private static int testStatus(ServerCommandSource s){ServerPlayerEntity a=self(s);var q=a==null?null:CwStoryService.test(a);return q==null?err(s,"Тестовый режим не активен."):ok(s,"CW TEST MODE: "+CwStoryService.describe(q));}
	private static int testStage(ServerCommandSource s,String stage){ServerPlayerEntity a=self(s);var q=a==null?null:CwStoryService.test(a);if(q==null)return err(s,"Сначала /cw story test enter.");q.stageId=stage;CwStoryService.touch(q,"test:"+s.getName());return testStatus(s);}
	private static int testReset(ServerCommandSource s){ServerPlayerEntity a=self(s);if(a==null||!CwStoryService.inTest(a))return err(s,"Тестовый режим не активен.");CwStoryService.enterTest(a,CwStoryService.testTarget(a),true);return testStatus(s);}
	private static int adminHelp(ServerCommandSource s){return ok(s,"admin: player, tp, bring, inventory view/give/take, xp give. Изменения требуют confirm и пишутся в аудит.");}
	private static int tp(ServerCommandSource s,ServerPlayerEntity target,boolean bring){ServerPlayerEntity a=self(s);if(a==null)return err(s,"Требуется игрок.");if(bring)target.teleport(a.getServerWorld(),a.getX(),a.getY(),a.getZ(),a.getYaw(),a.getPitch());else a.teleport(target.getServerWorld(),target.getX(),target.getY(),target.getZ(),target.getYaw(),target.getPitch());CwAudit.write(s,bring?"admin bring":"admin tp",target.getName().getString(),"ok");return ok(s,"Телепортация выполнена.");}
	private static int invView(ServerCommandSource s,ServerPlayerEntity p){int used=0;for(int i=0;i<p.getInventory().size();i++)if(!p.getInventory().getStack(i).isEmpty())used++;return ok(s,"Инвентарь "+p.getName().getString()+": занято "+used+"/"+p.getInventory().size()+" слотов.");}
	private static int invGive(ServerCommandSource s,ServerPlayerEntity p,Identifier id,int count){if(!Registries.ITEM.containsId(id))return err(s,"Неизвестный item ID.");ItemStack stack=new ItemStack(Registries.ITEM.get(id),count);if(!p.getInventory().insertStack(stack)||!stack.isEmpty())return err(s,"Инвентарь переполнен; остаток не выдан.");CwAudit.write(s,"inventory give",p.getName().getString(),id+" x"+count);return ok(s,"Выдано.");}
	private static int invTake(ServerCommandSource s,ServerPlayerEntity p,Identifier id,int count){int have=0;for(int i=0;i<p.getInventory().size();i++){var st=p.getInventory().getStack(i);if(Registries.ITEM.getId(st.getItem()).equals(id))have+=st.getCount();}if(have<count)return err(s,"У игрока только "+have+" шт.; ничего не удалено.");int left=count;for(int i=0;i<p.getInventory().size()&&left>0;i++){var st=p.getInventory().getStack(i);if(Registries.ITEM.getId(st.getItem()).equals(id)){int n=Math.min(left,st.getCount());st.decrement(n);left-=n;}}CwAudit.write(s,"inventory take",p.getName().getString(),id+" x"+count);return ok(s,"Удалено "+count+" шт.");}
	private static int xp(ServerCommandSource s,ServerPlayerEntity p,int amount){p.addExperience(amount);CwAudit.write(s,"xp give",p.getName().getString(),Integer.toString(amount));return ok(s,"Добавлено XP: "+amount);}
}
