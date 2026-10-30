package ru.admincontrol.lumira.gen;

/** Детерминированный value-noise и фрактальный шум для рельефа «Рощи Люмиры». */
public final class LumiraNoise {
	private final long seed;

	public LumiraNoise(long seed) {
		this.seed = seed;
	}

	private double hash(int x, int z, int salt) {
		long h = seed * 0x9E3779B97F4A7C15L + x * 0xC2B2AE3D27D4EB4FL + z * 0x165667B19E3779F9L + salt * 0x27D4EB2F165667C5L;
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return ((h >>> 11) / (double) (1L << 53)) * 2.0 - 1.0;
	}

	private static double smooth(double t) {
		return t * t * (3.0 - 2.0 * t);
	}

	public double value(double x, double z, int salt) {
		int x0 = (int) Math.floor(x);
		int z0 = (int) Math.floor(z);
		double fx = smooth(x - x0);
		double fz = smooth(z - z0);
		double a = hash(x0, z0, salt);
		double b = hash(x0 + 1, z0, salt);
		double c = hash(x0, z0 + 1, salt);
		double d = hash(x0 + 1, z0 + 1, salt);
		double top = a + (b - a) * fx;
		double bottom = c + (d - c) * fx;
		return top + (bottom - top) * fz;
	}

	/** Фрактальный шум примерно в диапазоне [-1; 1]. */
	public double fbm(double x, double z, int octaves, int salt) {
		double sum = 0;
		double amp = 1;
		double norm = 0;
		double freq = 1;
		for (int i = 0; i < octaves; i++) {
			sum += value(x * freq, z * freq, salt + i * 31) * amp;
			norm += amp;
			amp *= 0.5;
			freq *= 2.0;
		}
		return sum / norm;
	}

	public static double clamp01(double v) {
		return v < 0 ? 0 : Math.min(v, 1);
	}

	public static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	public static double sstep(double edge0, double edge1, double v) {
		return smooth(clamp01((v - edge0) / (edge1 - edge0)));
	}
}
