package ru.admincontrol.cw;

import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import ru.admincontrol.util.NpcPlayerEntity;
import ru.admincontrol.util.NpcService;

import java.util.ArrayList;
import java.util.List;

/**
 * Dedicated-server check of the NORMAL / BUILD / STORY profile protocol with a real (fake-connection) player entity.
 * Runs inside {@code LumiraSelfTest} (only with -Dadmin_control.selftest=true) and reports PASS / FAIL lines.
 * It covers: profile isolation and exact restore (inventory with mod NBT, offhand, armour, ender chest, cursor stack, effects,
 * XP, health, hunger, game mode, spawn point, position, dimension), double entry, disconnect and rejoin recovery,
 * the BUILD profile surviving between sessions, blocks reaching the region files, and the personal story instance keeping
 * its blocks and profile across exits.
 */
public final class CwPersistenceSelfTest {
	private static final String[] KEYS = {"Inventory", "EnderItems", "XpLevel", "XpTotal", "Health", "foodLevel", "playerGameType", "SpawnX", "SpawnY", "SpawnZ"};

	private CwPersistenceSelfTest() {}

	public static List<String> run(MinecraftServer server) {
		List<String> out = new ArrayList<>();
		try { inner(server, out); }
		catch (Throwable t) { out.add("FAIL CW persistence selftest crashed :: " + t); ru.admincontrol.AdminControlMod.LOGGER.error("CW persistence selftest", t); }
		try { hiding(out); chatRules(out); }
		catch (Throwable t) { out.add("FAIL CW hiding/chat selftest crashed :: " + t); }
		finally { try { NpcService.removeAll(server); } catch (Throwable ignored) { /* cleanup only */ } }
		for (String line : out) ru.admincontrol.AdminControlMod.LOGGER.info("[CW-TEST] {}", line);
		return out;
	}

	/** Wooden story items are hidden like all others; name, description and icon are independent; recipes leak nothing. */
	private static void hiding(List<String> out) {
		var item = ru.admincontrol.lumira.LumiraContent.item(ru.admincontrol.lumira.LumiraIds.TREES.get(0).id() + "_stairs");
		String id = net.minecraft.registry.Registries.ITEM.getId(item).toString();
		ru.admincontrol.item.ItemEncryption.apply(true, List.of(id), true, List.of(), true, List.of());
		check(out, "wooden item name hidden when only the name is listed", ru.admincontrol.item.ItemEncryption.hidden(item) && !ru.admincontrol.item.ItemEncryption.iconHidden(item) && !ru.admincontrol.item.ItemEncryption.descHidden(item), "");
		ru.admincontrol.item.ItemEncryption.apply(true, List.of(), true, List.of(), true, List.of(id));
		check(out, "only the description is hidden when only it is listed", !ru.admincontrol.item.ItemEncryption.hidden(item) && ru.admincontrol.item.ItemEncryption.descHidden(item), "");
		ru.admincontrol.item.ItemEncryption.apply(true, List.of(), true, List.of(id), true, List.of());
		check(out, "recipe is blocked through a hidden ingredient", ru.admincontrol.item.ItemEncryption.recipeBlocked(net.minecraft.item.Items.STICK, List.of(item)) && !ru.admincontrol.item.ItemEncryption.recipeBlocked(net.minecraft.item.Items.STICK, List.of(net.minecraft.item.Items.DIRT)), "");
		ru.admincontrol.item.ItemEncryption.apply(true, List.of(), true, List.of(), true, List.of());
		check(out, "allowing every field reveals the item", !ru.admincontrol.item.ItemEncryption.hidden(item) && !ru.admincontrol.item.ItemEncryption.iconHidden(item) && !ru.admincontrol.item.ItemEncryption.descHidden(item), "");
		check(out, "every story item is in the default deny list", ru.admincontrol.item.ItemEncryption.defaults().containsAll(ru.admincontrol.lumira.LumiraContent.ITEMS.keySet().stream().map(k -> "admin_control:" + k).toList()), "");
	}

	private static void chatRules(List<String> out) {
		check(out, "private message passes the story chat filter", CwChatFilter.allowsChatType("msg_command_incoming", "Admin", "Player"), "");
		check(out, "own chat message passes", CwChatFilter.allowsChatType("chat", "<Player>", "Player"), "");
		check(out, "other player chat is dropped", !CwChatFilter.allowsChatType("chat", "<Other>", "Player"), "");
		check(out, "/say broadcast is dropped", !CwChatFilter.allowsChatType("say_command", "Server", "Player"), "");
	}

	private static void check(List<String> out, String name, boolean ok, String detail) {
		out.add((ok ? "PASS " : "FAIL ") + "CW " + name + (detail == null || detail.isEmpty() ? "" : " :: " + detail));
	}

	private static NbtCompound snapshot(NpcPlayerEntity p) { NbtCompound n = new NbtCompound(); p.writeNbt(n); return n; }

	private static String diff(NbtCompound a, NbtCompound b) {
		StringBuilder sb = new StringBuilder();
		for (String key : KEYS) if (!java.util.Objects.equals(a.get(key), b.get(key))) sb.append(key).append(" was=").append(a.get(key)).append(" now=").append(b.get(key)).append("; ");
		if (a.getList("active_effects", 10).size() != b.getList("active_effects", 10).size()) sb.append("effects ").append(a.getList("active_effects", 10).size()).append("->").append(b.getList("active_effects", 10).size()).append("; ");
		return sb.toString();
	}

	private static void inner(MinecraftServer server, List<String> out) {
		ServerWorld overworld = server.getOverworld(), builder = CwBuilderWorld.world(server);
		check(out, "build dimension is registered", builder != null, "");
		if (builder == null) return;
		BlockPos spawn = overworld.getSpawnPos();
		String created = NpcService.create(server, overworld, "cwtest_a", spawn.getX() + .5, overworld.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, spawn.getX(), spawn.getZ()), spawn.getZ() + .5, 90f, 10f, null);
		check(out, "test player created", created == null, created);
		NpcPlayerEntity p = NpcService.find(server, "cwtest_a");
		if (p == null) return;
		CwClientGate.acknowledge(p, CwClientGate.PROTOCOL);
		ru.admincontrol.emi.EmiServer.acknowledgeForTest(p);

		// ---- NORMAL profile with everything that must never leak or vanish ----
		p.changeGameMode(GameMode.SURVIVAL);
		p.getInventory().clear(); p.getEnderChestInventory().clear(); p.clearStatusEffects();
		ItemStack modded = new ItemStack(Items.DIAMOND, 5);
		modded.getOrCreateNbt().putString("cw_mod_tag", "kept");
		p.getInventory().setStack(0, modded);
		p.equipStack(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
		p.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		p.getEnderChestInventory().setStack(2, new ItemStack(Items.EMERALD, 3));
		p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 6000, 1));
		p.experienceLevel = 7; p.totalExperience = 150; p.setHealth(13f); p.getHungerManager().setFoodLevel(9);
		p.setSpawnPoint(World.OVERWORLD, new BlockPos(11, 70, 12), 0f, true, false);
		p.playerScreenHandler.setCursorStack(new ItemStack(Items.GOLD_INGOT, 2));
		p.closeHandledScreen(); // the held stack returns to the inventory, exactly as a transition does
		NbtCompound before = snapshot(p);
		String beforeWorld = p.getServerWorld().getRegistryKey().getValue().toString();
		double bx = p.getX(), by = p.getY(), bz = p.getZ();

		// ---- A: NORMAL -> BUILD -> NORMAL ----
		String error = CwBuilderWorld.enter(p);
		check(out, "A enter build", error == null, error);
		check(out, "A in build world, creative, state BUILD", p.getServerWorld() == builder && p.interactionManager.getGameMode() == GameMode.CREATIVE && CwPlayerState.mode(p.getUuid()) == CwPlayerState.Mode.BUILD, p.getServerWorld().getRegistryKey().getValue() + " " + CwPlayerState.mode(p.getUuid()));
		check(out, "A build profile starts clean (no NORMAL items, ender chest, effects, XP)", p.getInventory().isEmpty() && p.getEnderChestInventory().isEmpty() && p.getStatusEffects().isEmpty() && p.experienceLevel == 0, "");
		check(out, "A held cursor stack was not dropped into the world", overworld.getEntitiesByClass(ItemEntity.class, p.getBoundingBox().expand(32), e -> true).isEmpty(), "");
		check(out, "A second entry is refused", CwBuilderWorld.enter(p) != null, "");
		BlockPos b0 = new BlockPos(100, 70, 100);
		for (int i = 0; i < 3; i++) builder.setBlockState(b0.add(i, 0, 0), Blocks.STONE.getDefaultState());
		p.getInventory().setStack(0, new ItemStack(Items.STONE, 64));
		error = CwBuilderWorld.leave(p, false);
		check(out, "A leave build", error == null, error);
		NbtCompound after = snapshot(p);
		check(out, "A NORMAL profile identical after BUILD (inventory+mod NBT, offhand, armour, ender, XP, health, hunger, mode, spawn point, effects)", diff(before, after).isEmpty(), diff(before, after));
		check(out, "A returned to the same dimension and position", p.getServerWorld().getRegistryKey().getValue().toString().equals(beforeWorld) && Math.abs(p.getX() - bx) < 0.01 && Math.abs(p.getY() - by) < 0.5 && Math.abs(p.getZ() - bz) < 0.01, p.getX() + " " + p.getY() + " " + p.getZ());
		check(out, "A BUILD items did not reach NORMAL", p.getInventory().count(Items.STONE) == 0, "");
		check(out, "A state back to NORMAL", CwPlayerState.mode(p.getUuid()) == CwPlayerState.Mode.NORMAL, "" + CwPlayerState.mode(p.getUuid()));
		check(out, "A leave is idempotent", CwBuilderWorld.leave(p, false) != null, "");

		// ---- BUILD profile and blocks persist ----
		error = CwBuilderWorld.enter(p);
		check(out, "C re-enter build", error == null, error);
		check(out, "C BUILD inventory restored from its own profile", p.getInventory().count(Items.STONE) == 64, "" + p.getInventory().count(Items.STONE));
		check(out, "C built blocks are still there", builder.getBlockState(b0).isOf(Blocks.STONE) && builder.getBlockState(b0.add(2, 0, 0)).isOf(Blocks.STONE), "");
		builder.getChunkManager().save(true);
		java.nio.file.Path region = net.minecraft.world.dimension.DimensionType.getSaveDirectory(builder.getRegistryKey(), server.getSavePath(net.minecraft.util.WorldSavePath.ROOT)).resolve("region");
		boolean regionOnDisk;
		try (var files = java.nio.file.Files.list(region)) { regionOnDisk = files.anyMatch(f -> f.getFileName().toString().endsWith(".mca") && f.toFile().length() > 8192); }
		catch (Exception e) { regionOnDisk = false; }
		check(out, "C build chunks are written to region files " + region, regionOnDisk, "");

		// ---- E: disconnect inside BUILD, rejoin ----
		CwBuilderWorld.onDisconnect(p);
		var point = CwBuilderWorld.sessions().get(p.getUuidAsString());
		check(out, "E disconnect keeps a recoverable record", point != null && "RETURN_PENDING".equals(point.phase) && point.builderProfileNbt != null && point.profileNbt != null, point == null ? "none" : point.phase);
		CwBuilderWorld.recoverOnJoin(p);
		check(out, "E rejoin restores NORMAL (never leaves the player in the build world)", p.getServerWorld().getRegistryKey().getValue().toString().equals(beforeWorld) && diff(before, snapshot(p)).isEmpty() && p.getInventory().count(Items.STONE) == 0, diff(before, snapshot(p)));
		error = CwBuilderWorld.enter(p);
		check(out, "E BUILD profile survived disconnect", error == null && p.getInventory().count(Items.STONE) == 64, error);
		CwBuilderWorld.leave(p, false);

		// ---- B/D: personal story instance ----
		error = CwStoryWorldService.enterPersonal(p, "cwtest", "builder", 1, "main");
		check(out, "B enter story", error == null, error);
		if (error == null) {
			check(out, "B in a personal story world, survival, clean profile, state STORY", CwStoryRules.inside(p) && p.interactionManager.getGameMode() == GameMode.SURVIVAL && p.getInventory().isEmpty() && p.getEnderChestInventory().isEmpty()
					&& CwPlayerState.mode(p.getUuid()) == CwPlayerState.Mode.STORY, p.getServerWorld().getRegistryKey().getValue() + " " + CwPlayerState.mode(p.getUuid()));
			ServerWorld story = p.getServerWorld();
			check(out, "B template was copied into the instance", story.getBlockState(b0).isOf(Blocks.STONE), "");
			String instanceWorld = story.getRegistryKey().getValue().toString();
			story.setBlockState(b0.up(), Blocks.DIAMOND_BLOCK.getDefaultState()); // progress made inside the story world
			p.getInventory().setStack(0, new ItemStack(Items.BREAD, 4));
			check(out, "B double entry is refused", CwStoryWorldService.enterPersonal(p, "cwtest", "builder", 1, "main") != null, "");
			error = CwStoryWorldService.leaveStory(p);
			check(out, "B leave story", error == null, error);
			check(out, "B NORMAL profile identical after STORY", diff(before, snapshot(p)).isEmpty() && p.getServerWorld().getRegistryKey().getValue().toString().equals(beforeWorld), diff(before, snapshot(p)));
			check(out, "B story items did not reach NORMAL", p.getInventory().count(Items.BREAD) == 0, "");
			check(out, "B state back to NORMAL", CwPlayerState.mode(p.getUuid()) == CwPlayerState.Mode.NORMAL, "" + CwPlayerState.mode(p.getUuid()));
			error = CwStoryWorldService.enterPersonal(p, "cwtest", "builder", 1, "main");
			check(out, "D re-enter story", error == null, error);
			check(out, "D same story instance again", p.getServerWorld().getRegistryKey().getValue().toString().equals(instanceWorld), p.getServerWorld().getRegistryKey().getValue().toString());
			check(out, "D blocks made in the story survive (template is not copied over them)", p.getServerWorld().getBlockState(b0.up()).isOf(Blocks.DIAMOND_BLOCK), "");
			check(out, "D story inventory continues", p.getInventory().count(Items.BREAD) == 4, "" + p.getInventory().count(Items.BREAD));
			CwStoryWorldService.onDisconnect(p);
			CwStoryWorldService.onJoin(p);
			check(out, "E story rejoin resumes in the same story world with the same items", CwStoryRules.inside(p) && p.getServerWorld().getRegistryKey().getValue().toString().equals(instanceWorld) && p.getInventory().count(Items.BREAD) == 4, p.getServerWorld().getRegistryKey().getValue().toString());
			error = CwStoryWorldService.leaveStory(p);
			check(out, "D final exit restores NORMAL", error == null && diff(before, snapshot(p)).isEmpty(), error == null ? diff(before, snapshot(p)) : error);
		}
		check(out, "journal has transitions", !CwPlayerState.tail(p.getUuid(), 100).isEmpty(), "");

		// ---- K: the server error from the screenshots ("Failed to handle packet ... NullPointerException: Cannot invoke ObjectSet.remove") ----
		// Reproduce the invariant violation behind it: the player is said to watch a section that this world never registered, then crosses a section border.
		int repairsBefore = CwDiagnostics.repairs;
		p.setWatchedSection(net.minecraft.util.math.ChunkSectionPos.from(new BlockPos(30000, 64, 30000)));
		p.setPosition(p.getX() + 64, p.getY(), p.getZ());
		boolean npe = false;
		try { overworld.getChunkManager().updatePosition(p); } catch (NullPointerException e) { npe = true; }
		check(out, "K movement across a section border with an unregistered watched section no longer throws NullPointerException", !npe, "");
		check(out, "K the desync was detected and repaired (count " + (CwDiagnostics.repairs - repairsBefore) + ")", CwDiagnostics.repairs > repairsBefore, "");
		p.setPosition(p.getX() + 64, p.getY(), p.getZ());
		npe = false;
		try { overworld.getChunkManager().updatePosition(p); } catch (NullPointerException e) { npe = true; }
		check(out, "K the player is registered consistently afterwards (next border crossing is clean)", !npe, "");
	}
}
