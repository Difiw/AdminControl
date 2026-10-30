package ru.admincontrol.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.admincontrol.util.EventService;

public final class EventFactory implements ExtendedScreenHandlerFactory {
	private final boolean adminOnly;
	private final boolean hostPlays;
	private final int page;

	public EventFactory() {
		this(false, false);
	}

	public EventFactory(boolean adminOnly) {
		this(adminOnly, false);
	}

	public EventFactory(boolean adminOnly, boolean hostPlays) {
		this(adminOnly, hostPlays, EventScreenHandler.PAGE_MAIN);
	}

	public EventFactory(boolean adminOnly, boolean hostPlays, int page) {
		this.adminOnly = adminOnly;
		this.hostPlays = hostPlays;
		this.page = page;
	}

	public static EventFactory playingHost() {
		return new EventFactory(false, true);
	}

	@Override
	public Text getDisplayName() {
		if (this.adminOnly) {
			return Text.literal("Тест мероприятия");
		}
		return Text.literal(this.hostPlays ? "Мероприятие (вы играете)" : "Мероприятие");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity viewer) {
		return new EventScreenHandler(syncId, this.adminOnly, this.hostPlays,
				viewer instanceof ServerPlayerEntity player ? player : null, this.page);
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity opener, PacketByteBuf buf) {
		if (EventService.current() != null) {
			buf.writeVarInt(EventService.current().choice);
			buf.writeVarInt(EventService.current().maxPlayers);
			buf.writeVarInt(EventService.current().reward);
			buf.writeBoolean(true);
			buf.writeVarInt(EventService.memberCount());
			buf.writeVarInt(EventService.current().phase.ordinal());
			buf.writeVarInt(EventService.current().powerTenths);
			buf.writeBoolean(EventService.current().privateEvent);
			buf.writeBoolean(EventService.current().raceBumpers);
			buf.writeVarInt(EventService.current().prize1);
			buf.writeVarInt(EventService.current().prize2);
			buf.writeVarInt(EventService.current().prize3);
			buf.writeVarInt(EventService.current().raceLuckTenths);
			buf.writeBoolean(EventService.current().hostPlays);
			buf.writeVarInt(this.page);
			EventScreenHandler.writeRoster(buf);
		} else {
			ru.admincontrol.data.EventDefaults d = ru.admincontrol.data.EventDefaults.of(0);
			buf.writeVarInt(0);
			buf.writeVarInt(this.adminOnly ? 1 : d.players);
			buf.writeVarInt(d.reward);
			buf.writeBoolean(false);
			buf.writeVarInt(0);
			buf.writeVarInt(0);
			buf.writeVarInt(d.power);
			buf.writeBoolean(this.adminOnly);
			buf.writeBoolean(d.bumpers);
			buf.writeVarInt(d.prize1);
			buf.writeVarInt(d.prize2);
			buf.writeVarInt(d.prize3);
			buf.writeVarInt(d.luck);
			buf.writeBoolean(this.hostPlays);
			buf.writeVarInt(EventScreenHandler.PAGE_MAIN);
			buf.writeVarInt(0);
		}
		EventScreenHandler.writeCatalog(buf);
	}
}
