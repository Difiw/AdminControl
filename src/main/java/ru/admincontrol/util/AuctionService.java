package ru.admincontrol.util;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.admincontrol.command.CommandUtil;
import ru.admincontrol.data.AuctionListing;
import ru.admincontrol.data.AuctionStore;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.PlayerRecord;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class AuctionService {
	private AuctionService() {
	}

	public static List<AuctionListing> listings() {
		DataManager data = DataManager.get();
		if (data == null) {
			return List.of();
		}
		if (data.auction().listings == null) {
			data.auction().listings = new ArrayList<>();
		}
		return data.auction().listings;
	}

	public static void removeBySeller(String uuid, String nick) {
		Iterator<AuctionListing> it = listings().iterator();
		boolean changed = false;
		while (it.hasNext()) {
			AuctionListing listing = it.next();
			boolean sameUuid = uuid != null && !uuid.isBlank() && uuid.equalsIgnoreCase(listing.sellerUuid);
			boolean sameNick = nick != null && !nick.isBlank() && nick.equalsIgnoreCase(listing.sellerNick);
			if (sameUuid || sameNick) {
				it.remove();
				changed = true;
			}
		}
		if (changed && DataManager.get() != null) {
			DataManager.get().saveAll();
		}
	}

	public static AuctionListing find(String listingId) {
		if (listingId == null || listingId.isBlank()) {
			return null;
		}
		for (AuctionListing listing : listings()) {
			if (listingId.equals(listing.id)) {
				return listing;
			}
		}
		return null;
	}

	public static ItemStack takeMatching(ServerPlayerEntity player, ItemStack template, int count) {
		if (player == null || template == null || template.isEmpty() || count <= 0) {
			return ItemStack.EMPTY;
		}
		int need = Math.min(count, template.getMaxCount());
		int have = 0;
		for (int i = 0; i < player.getInventory().size(); i++) {
			ItemStack stack = player.getInventory().getStack(i);
			if (ItemStack.canCombine(stack, template)) {
				have += stack.getCount();
			}
		}
		if (have < 1) {
			return ItemStack.EMPTY;
		}
		int take = Math.min(need, have);
		ItemStack result = template.copy();
		result.setCount(take);
		int left = take;
		for (int i = 0; i < player.getInventory().size() && left > 0; i++) {
			ItemStack stack = player.getInventory().getStack(i);
			if (!ItemStack.canCombine(stack, template)) {
				continue;
			}
			int from = Math.min(left, stack.getCount());
			stack.decrement(from);
			left -= from;
		}
		player.getInventory().markDirty();
		return result;
	}

	public static void add(ServerPlayerEntity seller, ItemStack stack, int pricePerItem) {
		if (seller == null || stack == null || stack.isEmpty() || pricePerItem < 0) {
			return;
		}
		if (Economy.sellBlocked(seller)) {
			return;
		}
		AuctionListing listing = new AuctionListing();
		listing.id = UUID.randomUUID().toString();
		listing.sellerUuid = seller.getUuid().toString();
		listing.sellerNick = seller.getGameProfile().getName();
		listing.itemNbt = stack.writeNbt(new NbtCompound()).asString();
		listing.pricePerItem = Economy.clampAuctionPrice(stack.getItem(), pricePerItem);
		listing.createdAt = System.currentTimeMillis();
		listings().add(listing);
		DataManager.get().saveAll();
	}

	public static void clampAllPrices() {
		boolean changed = false;
		for (AuctionListing listing : listings()) {
			ItemStack stack = stackOf(listing);
			if (stack.isEmpty()) {
				continue;
			}
			int clamped = Economy.clampAuctionPrice(stack.getItem(), listing.pricePerItem);
			if (clamped != listing.pricePerItem) {
				listing.pricePerItem = clamped;
				changed = true;
			}
		}
		if (changed && DataManager.get() != null) {
			DataManager.get().saveAll();
		}
	}

	public static boolean adjustOwnPrice(ServerPlayerEntity player, String listingId, int delta) {
		if (player == null || listingId == null || delta == 0) {
			return false;
		}
		for (AuctionListing listing : listings()) {
			if (!listingId.equals(listing.id)) {
				continue;
			}
			if (!isOwner(player, listing)) {
				return false;
			}
			ItemStack stack = stackOf(listing);
			if (stack.isEmpty()) {
				return false;
			}
			int next = Economy.clampAuctionPrice(stack.getItem(), listing.pricePerItem + delta);
			if (next == listing.pricePerItem) {
				return false;
			}
			listing.pricePerItem = next;
			DataManager.get().saveAll();
			return true;
		}
		return false;
	}

	private static boolean isOwner(ServerPlayerEntity player, AuctionListing listing) {
		return player.getUuid().toString().equalsIgnoreCase(listing.sellerUuid)
				|| player.getGameProfile().getName().equalsIgnoreCase(listing.sellerNick);
	}

	public static ItemStack stackOf(AuctionListing listing) {
		if (listing == null || listing.itemNbt == null || listing.itemNbt.isBlank()) {
			return ItemStack.EMPTY;
		}
		try {
			return ItemStack.fromNbt((NbtCompound) StringNbtReader.parse(listing.itemNbt));
		} catch (Exception e) {
			return ItemStack.EMPTY;
		}
	}

	public static boolean buy(ServerPlayerEntity buyer, String listingId) {
		return buy(buyer, listingId, Integer.MAX_VALUE);
	}

	public static boolean buy(ServerPlayerEntity buyer, String listingId, int count) {
		if (buyer == null || Economy.sellBlocked(buyer)) {
			return false;
		}
		AuctionListing found = find(listingId);
		if (found == null) {
			return false;
		}
		ItemStack stack = stackOf(found);
		if (stack.isEmpty()) {
			return false;
		}
		int take = Math.max(1, Math.min(count, stack.getCount()));
		int total = found.pricePerItem * take;
		if (!Economy.takeCurrency(buyer, total, "покупка на аукционе " + Registries.ITEM.getId(stack.getItem())
				+ " x" + take)) {
			return false;
		}
		ItemStack give = stack.copy();
		give.setCount(take);
		if (!buyer.getInventory().insertStack(give) || !give.isEmpty()) {
			buyer.dropItem(give, false);
		}
		paySeller(buyer.getServer(), found, total);
		if (take >= stack.getCount()) {
			listings().remove(found);
		} else {
			stack.setCount(stack.getCount() - take);
			found.itemNbt = stack.writeNbt(new NbtCompound()).asString();
		}
		DataManager.get().saveAll();
		return true;
	}

	public static boolean cancel(ServerPlayerEntity player, String listingId) {
		Iterator<AuctionListing> iterator = listings().iterator();
		while (iterator.hasNext()) {
			AuctionListing listing = iterator.next();
			if (!listing.id.equals(listingId)) {
				continue;
			}
			if (!isOwner(player, listing)) {
				return false;
			}
			ItemStack stack = stackOf(listing);
			if (!stack.isEmpty()) {
				if (!player.getInventory().insertStack(stack) || !stack.isEmpty()) {
					player.dropItem(stack, false);
				}
			}
			iterator.remove();
			DataManager.get().saveAll();
			return true;
		}
		return false;
	}

	private static void paySeller(MinecraftServer server, AuctionListing listing, int amount) {
		if (server == null) {
			return;
		}
		ServerPlayerEntity online = CommandUtil.findOnline(server, listing.sellerNick).orElse(null);
		if (online != null) {
			Economy.giveCurrency(online, amount, "продажа на аукционе");
			return;
		}
		DataManager data = DataManager.get();
		if (data == null) {
			return;
		}
		PlayerRecord record = null;
		if (listing.sellerUuid != null && !listing.sellerUuid.isBlank()) {
			try {
				record = data.findByUuid(UUID.fromString(listing.sellerUuid)).orElse(null);
			} catch (IllegalArgumentException ignored) {
				record = null;
			}
		}
		if (record == null) {
			record = data.findByNick(listing.sellerNick).orElse(null);
		}
		if (record != null) {
			Economy.addCoins(record, amount, "продажа на аукционе");
		} else {
			data.pendingCoins(listing.sellerUuid, listing.sellerNick, amount);
		}
	}
}
