package ru.admincontrol.util;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;
import ru.admincontrol.net.ModPackets;
import ru.admincontrol.skill.SkillTree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Кнопки слева от инвентаря (рюкзак, верстак, автопечь) в порядке покупки. */
public final class InvButtons {
	private InvButtons() {
	}

	private record Owned(String id, long at) {
	}

	public static List<String> order(PlayerRecord rec) {
		List<Owned> owned = new ArrayList<>();
		if (rec.backpackLevel > 0 && !SkillTreeService.nodeOff(rec, SkillTree.BACKPACK)) {
			owned.add(new Owned("backpack", rec.backpackBoughtAt));
		}
		if (rec.workbenchOwned && !SkillTreeService.nodeOff(rec, SkillTree.WORKBENCH)) {
			owned.add(new Owned("workbench", rec.workbenchBoughtAt));
		}
		if (rec.furnaceLevel > 0 && !SkillTreeService.nodeOff(rec, SkillTree.FURNACE)) {
			owned.add(new Owned("furnace", rec.furnaceBoughtAt));
		}
		owned.sort(Comparator.comparingLong(Owned::at));
		List<String> ids = new ArrayList<>();
		for (Owned o : owned) {
			ids.add(o.id());
		}
		return ids;
	}

	public static void sync(ServerPlayerEntity player) {
		if (player == null || player.networkHandler == null || player.networkHandler instanceof NpcNetworkHandler
				|| DataManager.get() == null) {
			return;
		}
		// В сюжетных мирах и на стройке кнопок рюкзака, верстака и печи нет (там будут другие вкладки).
		List<String> ids = blocked(player) ? List.of() : order(DataManager.get().progress(player));
		var buf = PacketByteBufs.create();
		buf.writeVarInt(ids.size());
		for (String id : ids) {
			buf.writeString(id, 32);
		}
		ServerPlayNetworking.send(player, ModPackets.FEATURES, buf);
	}

	/** Ветка узла отключена администрацией: сообщить и не открывать. */
	public static boolean offNotice(ServerPlayerEntity player, PlayerRecord rec, SkillTree.Node node) {
		if (!SkillTreeService.nodeOff(rec, node)) {
			return false;
		}
		Chat.send(player, Chat.red("«" + node.title + "» недоступен: ветка «" + node.branch.title() + "» отключена администрацией."));
		return true;
	}

	public static boolean blocked(ServerPlayerEntity player) {
		return ru.admincontrol.cw.CwStoryRules.inside(player) || ru.admincontrol.cw.CwBuilderWorld.inside(player);
	}

	private static boolean locked(ServerPlayerEntity player) {
		if (player == null || DataManager.get() == null || AuthService.pending(player)) {
			return true;
		}
		if (blocked(player)) {
			return true;
		}
		if (ActivityLock.blocked(player)) {
			ActivityLock.deny(player, true);
			return true;
		}
		if (FreezeService.actionsBlocked(player)) {
			Chat.send(player, Chat.red("Вы заморожены: интерфейсы недоступны."));
			return true;
		}
		return false;
	}

	public static void openWorkbench(ServerPlayerEntity player) {
		if (locked(player)) {
			return;
		}
		PlayerRecord rec = DataManager.get().progress(player);
		if (offNotice(player, rec, SkillTree.WORKBENCH)) {
			return;
		}
		if (!rec.workbenchOwned) {
			Chat.send(player, Chat.red("Верстак открывается в дереве навыков: /skills, ветка «Добыча и ремесло»."));
			return;
		}
		ScreenHandlerContext ctx = ScreenHandlerContext.create(player.getWorld(), player.getBlockPos());
		player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inv, viewer) ->
				new CraftingScreenHandler(syncId, inv, ctx) {
					@Override
					public boolean canUse(PlayerEntity p) {
						return true;
					}
				}, Text.literal("Верстак")));
	}

	public static void openFurnace(ServerPlayerEntity player) {
		if (locked(player)) {
			return;
		}
		if (offNotice(player, DataManager.get().progress(player), SkillTree.FURNACE)) {
			return;
		}
		if (FurnaceService.level(player) <= 0) {
			Chat.send(player, Chat.red("Автопечь открывается в дереве навыков: /skills, ветка «Добыча и ремесло»."));
			return;
		}
		ScreenNav.openRoot(player, new ru.admincontrol.screen.AutoFurnaceFactory(player));
	}
}
