package ru.admincontrol.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * Ловит включённую подсветку структур MiniHUD / BBOR, не имена файлов.
 */
public final class CheatOverlayScan {
	private CheatOverlayScan() {
	}

	public static String find() {
		String mini = minihud();
		if (mini != null) {
			return mini;
		}
		return bbor();
	}

	private static String minihud() {
		try {
			Class<?> toggle = Class.forName("fi.dy.masa.minihud.config.RendererToggle");
			Object[] values = toggle.getEnumConstants();
			if (values != null) {
				for (Object value : values) {
					if (value == null || !highlightName(((Enum<?>) value).name())) {
						continue;
					}
					if (bool(value)) {
						return "подсветка MiniHUD: " + ((Enum<?>) value).name();
					}
				}
			}
		} catch (Throwable ignored) {
		}
		try {
			Class<?> overlay = Class.forName("fi.dy.masa.minihud.renderer.OverlayRenderer");
			for (Field field : overlay.getDeclaredFields()) {
				field.setAccessible(true);
				Object list = Modifier.isStatic(field.getModifiers()) ? field.get(null) : null;
				if (!(list instanceof Iterable<?> it)) {
					continue;
				}
				for (Object renderer : it) {
					if (renderer != null && boolCall(renderer, "shouldRender", "isEnabled", "isActive")) {
						return "подсветка MiniHUD: overlay " + renderer.getClass().getSimpleName();
					}
				}
			}
		} catch (Throwable ignored) {
		}
		try {
			Class<?> shapes = Class.forName("fi.dy.masa.minihud.renderer.shapes.ShapeManager");
			Object manager = firstStatic(shapes);
			if (manager != null) {
				Object all = call(manager, "getAllShapes", "getShapes", "getShapeList");
				if (size(all) > 0 && boolCall(manager, "shouldRender", "isEnabled")) {
					return "подсветка MiniHUD: фигуры/сферы деспавна";
				}
				if (size(all) > 0) {
					return "подсветка MiniHUD: фигуры/сферы деспавна";
				}
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	private static String bbor() {
		try {
			Class<?> config = Class.forName("com.irtimaled.bbor.client.config.ConfigManager");
			for (Field field : config.getDeclaredFields()) {
				if (!Modifier.isStatic(field.getModifiers())) {
					continue;
				}
				field.setAccessible(true);
				String name = field.getName().toLowerCase(Locale.ROOT);
				if (!highlightName(name) && !name.contains("render") && !name.contains("box")
						&& !name.contains("structure") && !name.contains("village")
						&& !name.contains("stronghold") && !name.contains("monument")
						&& !name.contains("mansion") && !name.contains("slime")
						&& !name.contains("spawn")) {
					continue;
				}
				Object value = field.get(null);
				if (bool(value)) {
					return "подсветка BBOR: " + field.getName();
				}
			}
		} catch (Throwable ignored) {
		}
		try {
			Class<?> cache = Class.forName("com.irtimaled.bbor.common.BoundingBoxCache");
			Object instance = firstStatic(cache);
			if (instance == null) {
				instance = callStatic(cache, "getInstance", "getCache");
			}
			if (instance != null) {
				Object boxes = call(instance, "getBoundingBoxes", "getBoxes", "values", "getCache");
				if (size(boxes) > 0) {
					return "подсветка BBOR: рамки структур в загруженных чанках ×" + size(boxes);
				}
			}
		} catch (Throwable ignored) {
		}
		try {
			Class<?> getCache = Class.forName("com.irtimaled.bbor.client.GetCache");
			Object boxes = callStatic(getCache, "getBoundingBoxes", "getCache");
			if (size(boxes) > 0) {
				return "подсветка BBOR: рамки структур ×" + size(boxes);
			}
		} catch (Throwable ignored) {
		}
		try {
			Class<?> renderer = Class.forName("com.irtimaled.bbor.client.ClientRenderer");
			if (boolCallStatic(renderer, "isRendering", "shouldRender", "active")) {
				return "подсветка BBOR: ClientRenderer";
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	private static boolean highlightName(String raw) {
		if (raw == null) {
			return false;
		}
		String n = raw.toLowerCase(Locale.ROOT);
		return n.contains("overlay") || n.contains("structure") || n.contains("slime")
				|| n.contains("spawn") || n.contains("light") || n.contains("beacon")
				|| n.contains("shape") || n.contains("region") || n.contains("grid")
				|| n.contains("biome") || n.contains("conduit") || n.contains("spawner")
				|| n.contains("village") || n.contains("mansion") || n.contains("monument")
				|| n.contains("stronghold") || n.contains("fortress") || n.contains("temple")
				|| n.contains("outline") || n.contains("bounding");
	}

	private static Object firstStatic(Class<?> type) {
		try {
			for (Field field : type.getDeclaredFields()) {
				if (!Modifier.isStatic(field.getModifiers())) {
					continue;
				}
				field.setAccessible(true);
				Object value = field.get(null);
				if (value != null && type.isInstance(value)) {
					return value;
				}
			}
			Method get = type.getMethod("getInstance");
			return get.invoke(null);
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static Object callStatic(Class<?> type, String... names) {
		for (String name : names) {
			try {
				Method method = type.getMethod(name);
				return method.invoke(null);
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	private static Object call(Object target, String... names) {
		if (target == null) {
			return null;
		}
		for (String name : names) {
			try {
				Method method = target.getClass().getMethod(name);
				return method.invoke(target);
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	private static boolean boolCall(Object target, String... names) {
		return bool(call(target, names));
	}

	private static boolean boolCallStatic(Class<?> type, String... names) {
		return bool(callStatic(type, names));
	}

	private static boolean bool(Object value) {
		if (value == null) {
			return false;
		}
		if (value instanceof Boolean b) {
			return b;
		}
		Object nested = call(value, "getBooleanValue", "getAsBoolean", "booleanValue", "getValue", "get");
		if (nested instanceof Boolean b) {
			return b;
		}
		if (nested != value) {
			return bool(nested);
		}
		return false;
	}

	private static int size(Object value) {
		if (value == null) {
			return 0;
		}
		if (value instanceof Collection<?> c) {
			return c.size();
		}
		if (value instanceof Map<?, ?> m) {
			return m.size();
		}
		if (value.getClass().isArray()) {
			return java.lang.reflect.Array.getLength(value);
		}
		Object n = call(value, "size", "count", "getCount");
		if (n instanceof Number number) {
			return number.intValue();
		}
		return 0;
	}
}
