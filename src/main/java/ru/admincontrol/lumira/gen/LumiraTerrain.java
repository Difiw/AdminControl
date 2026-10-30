package ru.admincontrol.lumira.gen;

import ru.admincontrol.lumira.LumiraConfig;

import static ru.admincontrol.lumira.gen.LumiraNoise.lerp;
import static ru.admincontrol.lumira.gen.LumiraNoise.sstep;

/**
 * Рельеф «Рощи Люмиры» — одна функция высоты: смешанный лес, поляны, холмы, горы, ручьи, озёра. Все участки —
 * части одного биома, а не отдельные биомы. Ориентиры площади: лес и опушки ~60%, поляны ~20%, холмы ~12%, горы ~8%.
 */
public final class LumiraTerrain {
	public record Column(int height, double meadow, double hill, double mountain, double river, double lake,
						 double moisture, boolean underwater) {
	}

	private final LumiraNoise noise;
	private final LumiraConfig cfg;
	private final int radius;

	public LumiraTerrain(long seed, LumiraConfig cfg, int radius) {
		this.noise = new LumiraNoise(seed);
		this.cfg = cfg;
		this.radius = radius;
	}

	public LumiraNoise noise() {
		return noise;
	}

	public int sea() {
		return cfg.biomeSeaLevel;
	}

	public Column sample(int x, int z) {
		double base = cfg.biomeBaseY;
		double n = noise.fbm(x * 0.011, z * 0.011, 4, 1);
		double h = base + n * 8.0;
		double hm = noise.fbm(x * 0.0065 + 37, z * 0.0065 - 11, 3, 2);
		double hillF = sstep(0.12, 0.55, hm);
		h += hillF * (cfg.biomeHillY - base) * (0.55 + 0.45 * noise.fbm(x * 0.03, z * 0.03, 2, 3));
		double mt = noise.fbm(x * 0.0045 - 90, z * 0.0045 + 55, 3, 4);
		double mtF = sstep(0.34, 0.68, mt);
		double ridge = 1.0 - Math.abs(noise.fbm(x * 0.02, z * 0.02, 3, 5));
		h += mtF * (cfg.biomeMountainY - cfg.biomeHillY) * (0.55 + 0.45 * ridge);
		double meadow = noise.fbm(x * 0.012 + 200, z * 0.012 + 200, 3, 6);
		double meadowF = sstep(0.14, 0.40, meadow) * (1 - hillF) * (1 - mtF);
		h = lerp(h, base + n * 2.0, meadowF);
		double rv = Math.abs(noise.fbm(x * 0.0085 + 300, z * 0.0085 - 40, 3, 7));
		double riverF = (1.0 - sstep(0.0, 0.05, rv)) * (1.0 - sstep(base + 6, base + 16, h));
		h = lerp(h, cfg.biomeSeaLevel - 3, Math.pow(riverF, 0.75));
		double lk = noise.fbm(x * 0.006 - 500, z * 0.006 + 120, 3, 8);
		double lakeF = sstep(0.50, 0.62, lk) * (1.0 - sstep(base + 4, base + 12, h));
		h = lerp(h, cfg.biomeSeaLevel - 6, lakeF);
		int d = Math.max(Math.abs(x), Math.abs(z));
		double edge = sstep(radius - 16, radius, d);
		h = lerp(h, base, edge);
		int height = (int) Math.round(h);
		double moisture = Math.max(riverF, lakeF);
		moisture = Math.max(moisture, 0.5 + 0.5 * noise.fbm(x * 0.02 + 700, z * 0.02 - 300, 3, 9) - 0.25);
		return new Column(height, meadowF, hillF, mtF, riverF, lakeF, Math.max(0, Math.min(1, moisture)), height < cfg.biomeSeaLevel);
	}

	/** Плотность леса 0..1: мало на полянах, горах и скалах. */
	public double forestDensity(Column c, int x, int z) {
		double f = 0.55 + 0.45 * noise.fbm(x * 0.02 - 40, z * 0.02 + 90, 3, 10);
		f *= 1.0 - c.meadow() * 0.95;
		f *= 1.0 - c.mountain() * 0.8;
		f *= 1.0 - c.hill() * 0.4;
		return Math.max(0, Math.min(1, f));
	}
}
