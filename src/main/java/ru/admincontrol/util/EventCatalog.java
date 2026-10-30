package ru.admincontrol.util;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import ru.admincontrol.data.CustomEvent;
import ru.admincontrol.data.DataManager;
import ru.admincontrol.data.EventDefaults;
import ru.admincontrol.data.ModSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Все МП по порядку выбора: сначала стандартные (индексы EventService.TYPES), за ними свои из /devent.
 * У каждого МП есть ключ: «0»…«12» для стандартных и постоянный id («c1»…) для своих.
 */
public final class EventCatalog {
	/** Кнопки выбора МП — id 100 + индекс, vanilla передаёт id байтом (до 127): 15 стандартных + 12 своих. */
	public static final int MAX_CUSTOM = 12;
	/** Всего МП в списке выбора — столько мест под порядок кнопок. */
	public static final int MAX_CHOICES = 27;
	public static final int MAX_BREAKABLE = 50;
	public static final int NAME_MAX = 32;
	public static final int DESC_MAX = 600;

	private EventCatalog() {
	}

	private static ModSettings settings() {
		DataManager data = DataManager.get();
		return data == null ? null : data.settings();
	}

	public static int builtIn() {
		return EventService.TYPES.length;
	}

	public static List<CustomEvent> customs() {
		ModSettings s = settings();
		if (s == null) {
			return List.of();
		}
		if (s.customEvents == null) {
			s.customEvents = new ArrayList<>();
		}
		return s.customEvents;
	}

	public static int count() {
		return builtIn() + Math.min(MAX_CUSTOM, customs().size());
	}

	public static boolean valid(int choice) {
		return choice >= 0 && choice < count();
	}

	public static CustomEvent custom(int choice) {
		int i = choice - builtIn();
		List<CustomEvent> list = customs();
		return i >= 0 && i < list.size() && i < MAX_CUSTOM ? list.get(i) : null;
	}

	public static boolean isCustom(int choice) {
		return custom(choice) != null;
	}

	public static int clampBase(int base) {
		return Math.max(0, Math.min(builtIn() - 1, base));
	}

	public static String name(int choice) {
		CustomEvent c = custom(choice);
		if (c != null) {
			return c.name == null || c.name.isBlank() ? "Своё МП " + c.id : c.name;
		}
		return choice >= 0 && choice < builtIn() ? EventService.TYPES[choice] : "—";
	}

	public static String description(int choice) {
		CustomEvent c = custom(choice);
		if (c == null) {
			return EventService.description(Math.max(0, choice));
		}
		String rules = "Правила — как у «" + EventService.TYPES[clampBase(c.base)] + "».";
		return c.description == null || c.description.isBlank() ? rules : c.description + " " + rules;
	}

	public static int base(int choice) {
		CustomEvent c = custom(choice);
		if (c != null) {
			return clampBase(c.base);
		}
		return choice >= 0 && choice < builtIn() ? choice : 0;
	}

	public static String key(int choice) {
		CustomEvent c = custom(choice);
		return c != null ? c.id : String.valueOf(Math.max(0, choice));
	}

	public static int byKey(String key) {
		if (key == null) {
			return -1;
		}
		for (int i = 0; i < count(); i++) {
			if (key.equals(key(i))) {
				return i;
			}
		}
		return -1;
	}

	/** Поиск по названию без учёта регистра; «_» можно писать вместо пробела. -1 — не найдено. */
	public static int byName(String name) {
		if (name == null) {
			return -1;
		}
		String want = name.replace('_', ' ').trim();
		for (int i = 0; i < count(); i++) {
			if (name(i).equalsIgnoreCase(want)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Порядок кнопок выбора МП в /event, /tevent, /allevent и /devent — индексы выбора.
	 * Хранится ключами МП; МП, которых нет в сохранённом порядке (новые свои), идут в конце.
	 */
	public static List<Integer> order() {
		ModSettings s = settings();
		int n = count();
		List<Integer> out = new ArrayList<>(n);
		boolean[] used = new boolean[n];
		if (s != null && s.eventOrder != null) {
			for (String key : s.eventOrder) {
				int c = byKey(key);
				if (c >= 0 && !used[c]) {
					used[c] = true;
					out.add(c);
				}
			}
		}
		for (int c = 0; c < n; c++) {
			if (!used[c]) {
				out.add(c);
			}
		}
		return out;
	}

	/** Сдвинуть кнопку с позиции pos на delta мест; возвращает её новую позицию. */
	public static int moveOrder(int pos, int delta) {
		ModSettings s = settings();
		List<Integer> list = order();
		if (s == null || pos < 0 || pos >= list.size()) {
			return pos;
		}
		int to = Math.max(0, Math.min(list.size() - 1, pos + delta));
		if (to == pos) {
			return pos;
		}
		Integer choice = list.remove(pos);
		list.add(to, choice);
		s.eventOrder = new ArrayList<>();
		for (int c : list) {
			s.eventOrder.add(key(c));
		}
		DataManager.get().saveAll();
		return to;
	}

	public static void resetOrder() {
		ModSettings s = settings();
		if (s != null && s.eventOrder != null) {
			s.eventOrder = null;
			DataManager.get().saveAll();
		}
	}

	public static boolean blocked(int choice) {
		CustomEvent c = custom(choice);
		if (c != null) {
			return c.blocked;
		}
		ModSettings s = settings();
		return s != null && s.blockedEvents != null && s.blockedEvents.contains(choice);
	}

	public static void setBlocked(int choice, boolean block) {
		ModSettings s = settings();
		if (s == null || !valid(choice)) {
			return;
		}
		CustomEvent c = custom(choice);
		if (c != null) {
			c.blocked = block;
		} else {
			if (s.blockedEvents == null) {
				s.blockedEvents = new ArrayList<>();
			}
			s.blockedEvents.remove(Integer.valueOf(choice));
			if (block) {
				s.blockedEvents.add(choice);
			}
		}
		DataManager.get().saveAll();
	}

	/** Бит i — МП с индексом выбора i заблокировано. */
	public static int blockedMask() {
		int mask = 0;
		for (int i = 0; i < count(); i++) {
			if (blocked(i)) {
				mask |= 1 << i;
			}
		}
		return mask;
	}

	public static List<String> breakable(String key) {
		ModSettings s = settings();
		List<String> list = s == null || s.eventBreakable == null || key == null ? null : s.eventBreakable.get(key);
		return list == null ? List.of() : List.copyOf(list);
	}

	public static boolean breakable(String key, Block block) {
		if (block == null) {
			return false;
		}
		return breakable(key).contains(Registries.BLOCK.getId(block).toString());
	}

	/** null — добавлен, иначе причина отказа. */
	public static String addBreakable(String key, Block block) {
		ModSettings s = settings();
		if (s == null || key == null || block == null) {
			return "Нет данных.";
		}
		String id = Registries.BLOCK.getId(block).toString();
		if ("minecraft:air".equals(id)) {
			return "Это воздух — выберите блок.";
		}
		if (s.eventBreakable == null) {
			s.eventBreakable = new java.util.LinkedHashMap<>();
		}
		List<String> list = s.eventBreakable.computeIfAbsent(key, k -> new ArrayList<>());
		if (list.contains(id)) {
			return "Блок «" + ItemLabels.of(block) + "» уже в списке.";
		}
		if (list.size() >= MAX_BREAKABLE) {
			return "В списке уже " + MAX_BREAKABLE + " блоков — больше нельзя.";
		}
		list.add(id);
		DataManager.get().saveAll();
		return null;
	}

	public static void removeBreakable(String key, int index) {
		ModSettings s = settings();
		List<String> list = s == null || s.eventBreakable == null ? null : s.eventBreakable.get(key);
		if (list == null || index < 0 || index >= list.size()) {
			return;
		}
		list.remove(index);
		if (list.isEmpty()) {
			s.eventBreakable.remove(key);
		}
		DataManager.get().saveAll();
	}

	public static void clearBreakable(String key) {
		ModSettings s = settings();
		if (s != null && s.eventBreakable != null && s.eventBreakable.remove(key) != null) {
			DataManager.get().saveAll();
		}
	}

	/** Новое своё МП по правилам base; null — достигнут предел. */
	public static CustomEvent create(int base) {
		ModSettings s = settings();
		if (s == null || customs().size() >= MAX_CUSTOM) {
			return null;
		}
		CustomEvent c = new CustomEvent();
		do {
			s.customEventSeq++;
			c.id = "c" + s.customEventSeq;
		} while (byKey(c.id) >= 0);
		c.base = clampBase(base);
		c.name = "Своё МП " + s.customEventSeq;
		c.description = "";
		s.customEvents.add(c);
		copyKit(String.valueOf(c.base), c.id);
		DataManager.get().saveAll();
		return c;
	}

	/** Набор своего МП — копия набора стандартного МП с теми же правилами. */
	public static void copyKit(String from, String to) {
		ModSettings s = settings();
		if (s == null) {
			return;
		}
		List<ru.admincontrol.data.TrainingKitItem> source = TrainingConfig.kitOf(from);
		List<ru.admincontrol.data.TrainingKitItem> copy = new ArrayList<>();
		com.google.gson.Gson gson = new com.google.gson.Gson();
		for (ru.admincontrol.data.TrainingKitItem item : source) {
			copy.add(gson.fromJson(gson.toJson(item), ru.admincontrol.data.TrainingKitItem.class));
		}
		s.eventKits.put(to, copy);
	}

	/** null — переименовано, иначе причина отказа. */
	public static String rename(int choice, String name, String description) {
		CustomEvent c = custom(choice);
		if (c == null) {
			return "Стандартные МП переименовать нельзя.";
		}
		String n = name == null ? "" : name.trim().replaceAll("\\s+", " ");
		String d = description == null ? "" : description.trim().replaceAll("\\s+", " ");
		if (n.isEmpty()) {
			return "Название не может быть пустым.";
		}
		if (n.length() > NAME_MAX) {
			return "Название длиннее " + NAME_MAX + " символов.";
		}
		if (d.length() > DESC_MAX) {
			return "Описание длиннее " + DESC_MAX + " символов.";
		}
		for (int i = 0; i < count(); i++) {
			if (i != choice && name(i).equalsIgnoreCase(n)) {
				return "МП «" + name(i) + "» уже есть — выберите другое название.";
			}
		}
		c.name = n;
		c.description = d;
		DataManager.get().saveAll();
		return null;
	}

	public static String nextBase(int choice) {
		CustomEvent c = custom(choice);
		if (c == null) {
			return "Правила меняются только у своих МП.";
		}
		c.base = (clampBase(c.base) + 1) % builtIn();
		EventDefaults.edit(c.id, c.base).clamp(c.base);
		DataManager.get().saveAll();
		return null;
	}

	/** Удалить своё МП вместе с картой, черновиком, набором, настройками и списком ломаемых блоков. */
	public static String delete(int choice) {
		CustomEvent c = custom(choice);
		if (c == null) {
			return "Стандартные МП удалить нельзя.";
		}
		ModSettings s = settings();
		s.customEvents.remove(c);
		EventDefaults.reset(c.id);
		if (s.eventKits != null) {
			s.eventKits.remove(c.id);
		}
		if (s.eventBreakable != null) {
			s.eventBreakable.remove(c.id);
		}
		EventMaps.delete(c.id);
		EventMaps.deleteDraft(c.id);
		DataManager.get().saveAll();
		return null;
	}
}
