package ru.admincontrol.cw;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.BlockStateArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.data.AdminRanks;
import ru.admincontrol.item.ModItems;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** /1, /2, /afill и вход/выход из постоянного void-измерения. */
public final class CwBuilderCommands {
	private CwBuilderCommands() {}
	public static void register(CommandDispatcher<ServerCommandSource> d, CommandRegistryAccess access) {
		CwFillService.register();
		d.register(literal("1").requires(CwBuilderCommands::allowed).executes(c->fix(c.getSource(),1)));
		d.register(literal("2").requires(CwBuilderCommands::allowed).executes(c->fix(c.getSource(),2)));
		d.register(literal("afill").requires(CwBuilderCommands::allowed)
				.then(literal("preview").executes(c->preview(c.getSource())))
				.then(literal("confirm").executes(c->confirm(c.getSource())))
				.then(literal("cancel").executes(c->cancel(c.getSource())))
				.then(literal("clear").executes(c->clear(c.getSource())))
				.then(literal("status").executes(c->status(c.getSource())))
				.then(argument("block",BlockStateArgumentType.blockState(access)).executes(c->fill(c.getSource(),BlockStateArgumentType.getBlockState(c,"block").getBlockState()))));
		d.register(literal("cw").requires(CommandUtil::canUseCw)
				.then(literal("template").requires(CwBuilderCommands::allowed)
						.then(literal("enter").executes(c->enter(c.getSource())))
						.then(literal("leave").executes(c->leave(c.getSource())))
						.then(literal("sessions").executes(c->sessions(c.getSource())))
						.then(literal("recover").then(argument("player",EntityArgumentType.player())
								.then(literal("confirm").executes(c->recover(c.getSource(),EntityArgumentType.getPlayer(c,"player"))))))
						.then(literal("marker").requires(CwBuilderCommands::developer).executes(c->marker(c.getSource())))
						.then(literal("invite").requires(CwBuilderCommands::developer).then(argument("player",EntityArgumentType.player()).executes(c->invite(c.getSource(),EntityArgumentType.getPlayer(c,"player")))))
						.then(literal("tp").requires(CwBuilderCommands::developer).then(argument("player",EntityArgumentType.player()).executes(c->teleport(c.getSource(),EntityArgumentType.getPlayer(c,"player")))))
						.then(literal("kick").requires(CwBuilderCommands::developer).then(argument("player",EntityArgumentType.player()).executes(c->kick(c.getSource(),EntityArgumentType.getPlayer(c,"player"))))))
				.then(literal("instances").requires(CwBuilderCommands::allowed)
						.then(literal("list").executes(c->instances(c.getSource())))
						.then(literal("archive").then(argument("id",StringArgumentType.word()).then(literal("confirm").executes(c->instanceLifecycle(c.getSource(),StringArgumentType.getString(c,"id"),"ARCHIVE")))))
						.then(literal("unarchive").then(argument("id",StringArgumentType.word()).then(literal("confirm").executes(c->instanceLifecycle(c.getSource(),StringArgumentType.getString(c,"id"),"UNARCHIVE")))))
						.then(literal("reset").then(argument("id",StringArgumentType.word()).then(literal("confirm").executes(c->instanceLifecycle(c.getSource(),StringArgumentType.getString(c,"id"),"RESET")))))
						.then(literal("delete").then(argument("id",StringArgumentType.word()).then(literal("confirm").executes(c->instanceLifecycle(c.getSource(),StringArgumentType.getString(c,"id"),"DELETE")))))
						.then(literal("test").then(argument("story",StringArgumentType.word()).then(argument("template",StringArgumentType.word())
								.executes(c->instanceTest(c.getSource(),StringArgumentType.getString(c,"story"),StringArgumentType.getString(c,"template"))))))
						.then(literal("personal").then(argument("player",EntityArgumentType.player()).then(argument("story",StringArgumentType.word()).then(argument("template",StringArgumentType.word()).then(argument("slot",StringArgumentType.word())
								.executes(c->instancePersonal(c.getSource(),EntityArgumentType.getPlayer(c,"player"),StringArgumentType.getString(c,"story"),StringArgumentType.getString(c,"template"),StringArgumentType.getString(c,"slot")))))))))
				.then(literal("graph").requires(CwBuilderCommands::allowed)
						.then(literal("list").executes(c->graphs(c.getSource())))
						.then(literal("create").then(argument("story",StringArgumentType.word()).then(argument("template",StringArgumentType.word())
								.executes(c->graphCreate(c.getSource(),StringArgumentType.getString(c,"story"),StringArgumentType.getString(c,"template"))))))
						.then(literal("validate").then(argument("id",StringArgumentType.word()).executes(c->graphValidate(c.getSource(),StringArgumentType.getString(c,"id"))))))
				.then(literal("markers").requires(CwBuilderCommands::allowed)
						.then(literal("list").executes(c->markers(c.getSource())))
						.then(literal("set").then(argument("name",StringArgumentType.word()).executes(c->markerSet(c.getSource(),StringArgumentType.getString(c,"name")))))
						.then(literal("rename").then(argument("old",StringArgumentType.word()).then(argument("name",StringArgumentType.word()).executes(c->markerRename(c.getSource(),StringArgumentType.getString(c,"old"),StringArgumentType.getString(c,"name"))))))
						.then(literal("delete").then(argument("name",StringArgumentType.word()).then(literal("confirm").executes(c->markerDelete(c.getSource(),StringArgumentType.getString(c,"name")))))))
				.then(literal("diag").requires(CwBuilderCommands::allowed)
						.executes(c->diag(c.getSource(),null))
						.then(argument("player",EntityArgumentType.player()).executes(c->diag(c.getSource(),EntityArgumentType.getPlayer(c,"player")))
								.then(literal("restore").requires(CwBuilderCommands::developer).then(literal("confirm").executes(c->diagRestore(c.getSource(),EntityArgumentType.getPlayer(c,"player")))))
								.then(literal("resync").requires(CwBuilderCommands::developer).then(literal("confirm").executes(c->diagResync(c.getSource(),EntityArgumentType.getPlayer(c,"player")))))))
				.then(literal("backup").requires(CwBuilderCommands::allowed)
						.then(literal("now").executes(c->backup(c.getSource())))
						.then(literal("verify").executes(c->backupVerify(c.getSource())))
						.then(literal("dry-run").executes(c->migrationDryRun(c.getSource())))
						.then(literal("migrate").then(literal("confirm").executes(c->migrationApply(c.getSource()))))));
		d.register(literal("cwbuild")
				.then(literal("accept").executes(c->accept(c.getSource())))
				.then(literal("leave").executes(c->leavePublic(c.getSource()))));
		d.register(literal("storyexit").executes(c->storyExit(c.getSource())));
		d.register(literal("cwexit").executes(c->storyExit(c.getSource())));
	}
	/** /cw diag [player]: paths, dimensions, chunk counts, the player's phase and the tail of the transition journal. */
	private static int diag(ServerCommandSource s,ServerPlayerEntity target){
		ServerPlayerEntity who=target!=null?target:player(s);
		s.sendFeedback(()->Text.literal("[CW] config="+(ru.admincontrol.data.DataManager.get()==null?"n/a":ru.admincontrol.data.DataManager.get().getConfigDir().toAbsolutePath())),false);
		s.sendFeedback(()->Text.literal("[CW] переходов выполняется: "+CwPlayerState.inFlight().size()+"; миграция: "+(CwMigrationService.healthy()?"ok":"ОШИБКА")),false);
		if(who==null)return 1;
		var rec=CwPlayerState.get(who.getUuid());
		s.sendFeedback(()->Text.literal("[CW] "+who.getName().getString()+" uuid="+who.getUuidAsString()+" mode="+rec.mode+" transition="+rec.transitionId+" dim="+who.getWorld().getRegistryKey().getValue()),false);
		for(String line:CwBuilderWorld.diagnostics(who.getUuid(),s.getServer()))s.sendFeedback(()->Text.literal("[CW] "+line),false);
		for(String line:CwStoryWorldService.diagnostics(who.getUuid(),s.getServer()))s.sendFeedback(()->Text.literal("[CW] "+line),false);
		for(String line:CwPlayerState.tail(who.getUuid(),6))s.sendFeedback(()->Text.literal("[CW] journal: "+line),false);
		return 1;
	}
	/** Safe recovery: finishes whatever the journal says is unfinished; never deletes anything. */
	private static int diagRestore(ServerCommandSource s,ServerPlayerEntity t){
		String note="";
		if(CwStoryRules.inside(t)){String e=CwStoryWorldService.leaveStory(t);if(e!=null)return err(s,e);note+="сюжет закрыт; ";}
		else if(CwStoryWorldService.hasSession(t.getUuid())){CwStoryWorldService.onJoin(t);note+="сюжетная сессия приведена в порядок; ";}
		var point=CwBuilderWorld.sessions().get(t.getUuidAsString());
		if(point!=null&&!"RESTORED".equals(point.phase)){String e=CwBuilderWorld.forceRecover(t);if(e!=null)return err(s,e);note+="основной профиль восстановлен; ";}
		if(CwPlayerState.interrupted(t.getUuid()))CwPlayerState.force(t.getUuid(),CwStoryRules.inside(t)?CwPlayerState.Mode.STORY:CwBuilderWorld.inside(t)?CwPlayerState.Mode.BUILD:CwPlayerState.Mode.NORMAL,"admin restore");
		CwAudit.write(s,"diag restore",t.getUuidAsString(),note);
		return ok(s,"Восстановление выполнено: "+(note.isEmpty()?"нечего восстанавливать.":note));
	}
	private static int diagResync(ServerCommandSource s,ServerPlayerEntity t){
		if(CwStoryRules.inside(t))return err(s,"Игрок сейчас в сюжете; сначала выведите его.");
		String e=CwStoryWorldService.requestResync(t.getUuid());CwAudit.write(s,"diag resync",t.getUuidAsString(),e==null?"ok":e);
		return e==null?ok(s,"При следующем входе карта игрока будет заново скопирована из шаблона (блоки его экземпляра заменятся)."):err(s,e);
	}
	private static boolean allowed(ServerCommandSource s){return CommandUtil.hasRank(s,AdminRanks.GA);}
	private static boolean developer(ServerCommandSource s){return CommandUtil.hasRank(s,AdminRanks.DEV);}
	private static ServerPlayerEntity player(ServerCommandSource s){try{return s.getPlayer();}catch(Exception e){return null;}}
	private static int ok(ServerCommandSource s,String m){s.sendFeedback(()->Text.literal("[CW] "+m),false);return 1;}
	private static int err(ServerCommandSource s,String m){s.sendError(Text.literal("[CW] "+m));return 0;}
	private static int enter(ServerCommandSource s){ServerPlayerEntity p=player(s);if(p==null)return err(s,"Требуется игрок.");String e=CwBuilderWorld.enter(p);return e==null?ok(s,"Вход в строительное измерение: платформа 0 64 0, Creative. Выход: /cw template leave."):err(s,e);}
	private static int leave(ServerCommandSource s){ServerPlayerEntity p=player(s);if(p==null)return err(s,"Требуется игрок.");String e=CwBuilderWorld.leave(p);return e==null?ok(s,"Вы вернулись в сохранённую точку; игровой режим восстановлен."):err(s,e);}
	private static int leavePublic(ServerCommandSource s){ServerPlayerEntity p=player(s);if(p==null)return err(s,"Требуется игрок.");if(!CwBuilderWorld.inside(p))return err(s,"Вы не на стройке.");String e=CwBuilderWorld.leave(p);return e==null?ok(s,"Вы покинули стройку."):err(s,e);}
	private static int storyExit(ServerCommandSource s){ServerPlayerEntity p=player(s);if(p==null)return err(s,"Команда доступна только игроку.");String e=CwLeaveService.request(p);return e==null?1:err(s,e);}
	private static int marker(ServerCommandSource s){ServerPlayerEntity p=player(s);if(p==null)return err(s,"Требуется игрок.");ItemStack stack=new ItemStack(ModItems.BUILDER_MARKER);if(!p.getInventory().insertStack(stack))p.dropItem(stack,false);return ok(s,"Маркер строителя выдан. ПКМ по блоку → /1 или /2.");}
	private static int invite(ServerCommandSource s,ServerPlayerEntity target){if(CwBuilderWorld.inside(target))return err(s,"Игрок уже находится на стройке.");CwBuilderWorld.invite(target);target.sendMessage(Text.literal("[CW] Разработчик пригласил вас на стройку. Нажмите сюда, чтобы войти (2 минуты).").styled(st->st.withColor(net.minecraft.util.Formatting.AQUA).withClickEvent(new net.minecraft.text.ClickEvent(net.minecraft.text.ClickEvent.Action.RUN_COMMAND,"/cwbuild accept"))),false);return ok(s,"Приглашение отправлено игроку "+target.getName().getString()+" на 2 минуты.");}
	private static int accept(ServerCommandSource s){ServerPlayerEntity p=player(s);if(p==null)return err(s,"Требуется игрок.");String e=CwBuilderWorld.accept(p);return e==null?ok(s,"Вы вошли на стройку. Выход: /cwbuild leave."):err(s,e);}
	private static int teleport(ServerCommandSource s,ServerPlayerEntity target){if(CwBuilderWorld.inside(target))return err(s,"Игрок уже находится на стройке.");String e=CwBuilderWorld.enter(target);return e==null?ok(s,"Игрок "+target.getName().getString()+" перенесён на стройку."):err(s,e);}
	private static int kick(ServerCommandSource s,ServerPlayerEntity target){CwBuilderWorld.revoke(target);if(!CwBuilderWorld.inside(target))return err(s,"Игрок не находится на стройке.");String e=CwBuilderWorld.leave(target,false);return e==null?ok(s,"Игрок "+target.getName().getString()+" удалён со стройки и возвращён назад."):err(s,e);}
	private static int sessions(ServerCommandSource s){var all=CwBuilderWorld.sessions();if(all.isEmpty())return ok(s,"Строительных сессий нет.");all.forEach((player,q)->s.sendFeedback(()->Text.literal("[CW] "+player+" session="+q.sessionId+" phase="+q.phase+(q.failure==null?"":" error="+q.failure)),false));return all.size();}
	private static int recover(ServerCommandSource s,ServerPlayerEntity target){String e=CwBuilderWorld.forceRecover(target);CwAudit.write(s,"template recover",target.getUuidAsString(),e==null?"ok":e);return e==null?ok(s,"Профиль "+target.getName().getString()+" восстановлен."):err(s,e);}
	private static int instances(ServerCommandSource s){var all=CwInstanceRegistry.all();if(all.isEmpty())return ok(s,"Реестр instances пуст.");all.values().forEach(i->s.sendFeedback(()->Text.literal("[CW] "+i.instanceId+" "+i.type+" "+i.lifecycle+" owner="+(i.ownerUuid==null?"-":i.ownerUuid)+" template="+i.templateId+"@"+i.templateVersion+" world="+i.worldKey+" reason="+i.creationReason),false));return all.size();}
	private static int instanceTest(ServerCommandSource s,String story,String template){ServerPlayerEntity p=player(s);if(p==null)return err(s,"Требуется игрок.");String e=CwStoryWorldService.enterTest(p,story,template,1);CwAudit.write(s,"instance test",story,e==null?"ok":e);return e==null?ok(s,"Открыт или переиспользован test instance."):err(s,e);}
	private static int instancePersonal(ServerCommandSource s,ServerPlayerEntity p,String story,String template,String slot){String e=CwStoryWorldService.enterPersonal(p,story,template,1,slot);CwAudit.write(s,"instance personal",p.getUuidAsString()+"/"+story+"/"+slot,e==null?"ok":e);return e==null?ok(s,"Открыт или переиспользован personal instance игрока "+p.getName().getString()+"."):err(s,e);}
	private static int instanceLifecycle(ServerCommandSource s,String id,String operation){var instance=CwInstanceRegistry.get(id);if(instance==null)return err(s,"Instance не найден.");if((operation.equals("RESET")||operation.equals("DELETE"))&&s.getServer().getPlayerManager().getPlayerList().stream().anyMatch(p->p.getWorld().getRegistryKey().getValue().toString().equals(instance.worldKey)))return err(s,"В instance находятся игроки; сначала выведите их из мира.");String e;try{if(operation.equals("ARCHIVE"))e=CwInstanceRegistry.archive(id);else if(operation.equals("UNARCHIVE"))e=CwInstanceRegistry.unarchive(id);else{CwBackupService.backupNow();e=CwInstanceRegistry.schedule(id,operation);}}catch(Exception ex){e="Backup/операция не выполнены: "+ex.getMessage();}CwAudit.write(s,"instance "+operation.toLowerCase(java.util.Locale.ROOT),id,e==null?"scheduled/ok":e);return e==null?ok(s,(operation.equals("RESET")||operation.equals("DELETE"))?"Операция запланирована после безопасного перезапуска сервера; создан backup.":"Lifecycle обновлён."):err(s,e);}
	private static int backup(ServerCommandSource s){try{var p=CwBackupService.backupNow();CwAudit.write(s,"backup now",p.toString(),"ok");return ok(s,"Backup создан: "+p);}catch(Exception e){return err(s,"Backup не создан: "+e.getMessage());}}
	private static int migrationDryRun(ServerCommandSource s){var lines=CwBackupService.dryRunMigration();lines.forEach(x->s.sendFeedback(()->Text.literal("[CW] "+x),false));return lines.stream().anyMatch(x->x.startsWith("FAIL"))?0:1;}
	private static int migrationApply(ServerCommandSource s){var lines=CwMigrationService.run(false);lines.forEach(x->s.sendFeedback(()->Text.literal("[CW] "+x),false));boolean failed=lines.stream().anyMatch(x->x.startsWith("FAIL"));CwAudit.write(s,"backup migrate","CW stores",failed?"failed":"ok");return failed?0:1;}
	private static int backupVerify(ServerCommandSource s){try{var lines=CwBackupService.verifyLatest();lines.forEach(x->s.sendFeedback(()->Text.literal("[CW] "+x),false));return lines.stream().anyMatch(x->x.startsWith("FAIL"))?0:1;}catch(Exception e){return err(s,"Проверка backup не выполнена: "+e.getMessage());}}
	private static int graphs(ServerCommandSource s){var all=CwStoryGraph.all();if(all.isEmpty())return ok(s,"Графов нет.");all.values().forEach(g->s.sendFeedback(()->Text.literal("[CW] graph="+g.graphId+" story="+g.storyId+" template="+g.templateId+" revision="+g.revision+" nodes="+g.nodes.size()+" edges="+g.edges.size()),false));return all.size();}
	private static int graphCreate(ServerCommandSource s,String story,String template){if(!story.matches("[a-z0-9_.-]{1,48}")||!template.matches("[a-z0-9_.-]{1,48}"))return err(s,"ID: a-z, 0-9, _.-; максимум 48 символов.");var g=CwStoryGraph.create(story,template,s.getName());CwAudit.write(s,"graph create",g.graphId,"story="+story+" template="+template);return ok(s,"Создан graphId="+g.graphId+" revision=0.");}
	private static int graphValidate(ServerCommandSource s,String id){var g=CwStoryGraph.get(id);if(g==null)return err(s,"Граф не найден.");var errors=CwStoryGraph.validate(g);if(errors.isEmpty())return ok(s,"Граф валиден.");errors.forEach(x->s.sendError(Text.literal("[CW] "+x)));return 0;}
	private static int markers(ServerCommandSource s){ServerPlayerEntity p=player(s);if(p==null)return err(s,"Требуется игрок.");var mine=CwPointRegistry.all().values().stream().filter(q->p.getUuidAsString().equals(q.ownerUuid)).toList();if(mine.isEmpty())return ok(s,"Именованных меток нет.");mine.forEach(q->s.sendFeedback(()->Text.literal(String.format(java.util.Locale.ROOT,"[CW] %s: %s %.3f %.3f %.3f yaw=%.1f pitch=%.1f",q.name,q.dimension,q.x,q.y,q.z,q.yaw,q.pitch)),false));return mine.size();}
	private static int markerSet(ServerCommandSource s,String name){ServerPlayerEntity p=player(s);if(p==null)return err(s,"Требуется игрок.");if(!CwPointRegistry.validName(name))return err(s,"Имя: a-z, 0-9, _.-; максимум 48 символов.");var hit=p.raycast(64.0,0,false);if(!(hit instanceof net.minecraft.util.hit.BlockHitResult block)||hit.getType()!=net.minecraft.util.hit.HitResult.Type.BLOCK)return err(s,"Блок в радиусе 64 не найден. Наведите прицел на блок.");var q=CwPointRegistry.record(p,name,block);return ok(s,"Метка "+name+": "+q.dimension+" "+q.x+" "+q.y+" "+q.z);}
	private static int markerRename(ServerCommandSource s,String oldName,String name){ServerPlayerEntity p=player(s);if(p==null)return err(s,"Требуется игрок.");String e=CwPointRegistry.rename(p.getUuidAsString(),oldName,name);return e==null?ok(s,"Метка "+oldName+" переименована в "+name+"."):err(s,e);}
	private static int markerDelete(ServerCommandSource s,String name){ServerPlayerEntity p=player(s);if(p==null)return err(s,"Требуется игрок.");String e=CwPointRegistry.remove(p.getUuidAsString(),name);return e==null?ok(s,"Метка "+name+" удалена."):err(s,e);}
	private static int fix(ServerCommandSource s,int n){ServerPlayerEntity p=player(s);if(p==null)return err(s,"Требуется игрок.");if(!CwBuilderWorld.inside(p))return err(s,"Маркер работает только в строительном измерении.");String e=CwBuilderSelection.fix(p,n);BlockStateInfo info=new BlockStateInfo(CwBuilderSelection.get(p),n);return e==null?ok(s,"Точка "+n+": "+info.coords()):err(s,e);}
	private record BlockStateInfo(CwBuilderSelection.State state,int point){String coords(){var p=point==1?state.first:state.second;return p.getX()+" "+p.getY()+" "+p.getZ();}}
	private static int preview(ServerCommandSource s){ServerPlayerEntity p=player(s);if(p==null)return err(s,"Требуется игрок.");var q=CwBuilderSelection.get(p);if(!CwBuilderSelection.complete(q))return err(s,"Сначала задайте /1 и /2.");return ok(s,"Область: "+q.first.toShortString()+" → "+q.second.toShortString()+", объём "+CwBuilderSelection.volume(q)+" блоков.");}
	private static int fill(ServerCommandSource s,net.minecraft.block.BlockState state){ServerPlayerEntity p=player(s);if(p==null)return err(s,"Требуется игрок.");String e=CwFillService.prepare(p,state);if(e!=null)return e.contains("confirm")||e.contains("Выполните")?ok(s,e):err(s,e);return ok(s,"Пакетное заполнение начато. /afill status — прогресс, /afill cancel — отмена.");}
	private static int confirm(ServerCommandSource s){ServerPlayerEntity p=player(s);if(p==null)return err(s,"Требуется игрок.");String e=CwFillService.confirm(p);return e==null?ok(s,"Заполнение подтверждено и запущено."):err(s,e);}
	private static int cancel(ServerCommandSource s){ServerPlayerEntity p=player(s);return p!=null&&CwFillService.cancel(p)?ok(s,"Операция отменена; уже изменённые блоки не откатываются."):err(s,"Активной операции нет.");}
	private static int clear(ServerCommandSource s){ServerPlayerEntity p=player(s);if(p==null)return err(s,"Требуется игрок.");CwFillService.cancel(p);CwBuilderSelection.clear(p);return ok(s,"Клик и обе точки очищены.");}
	private static int status(ServerCommandSource s){ServerPlayerEntity p=player(s);return p==null?err(s,"Требуется игрок."):ok(s,CwFillService.status(p));}
}
