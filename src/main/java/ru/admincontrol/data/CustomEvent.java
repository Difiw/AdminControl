package ru.admincontrol.data;

/** Своё МП из /devent: название, описание, правила одного из стандартных МП и своя карта. */
public final class CustomEvent {
	/** Постоянный ключ («c1», «c2»…): по нему хранятся карта, черновик, набор, настройки и ломаемые блоки. */
	public String id = "";
	public String name = "";
	public String description = "";
	/** Индекс стандартного МП (EventService.TYPES), по правилам и размеру карты которого идёт это МП. */
	public int base;
	public boolean blocked;
}
