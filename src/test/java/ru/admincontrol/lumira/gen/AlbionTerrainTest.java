package ru.admincontrol.lumira.gen;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AlbionTerrainTest {
	@Test void stoneDistributionIsDeterministicAndApproximatelySeventyFivePercentAcrossHeights() {
		long seed = 0x41B10F5EEDL;
		int selected = 0, total = 0;
		for (int y : new int[]{-48, -1, 32, 96, 180}) {
			int layer = 0;
			for (int x = -64; x < 64; x++) for (int z = -64; z < 64; z++) {
				boolean a = LumiraBiomeGen.albionStone(seed, x, y, z);
				assertEquals(a, LumiraBiomeGen.albionStone(seed, x, y, z));
				if (a) { selected++; layer++; }
				total++;
			}
			double ratio = layer / (128.0 * 128.0);
			assertTrue(ratio > .73 && ratio < .77, "height " + y + " ratio=" + ratio);
		}
		assertEquals(.75, selected / (double) total, .01);
	}

	@Test void albionTreePoolContainsNoSpruce() {
		assertFalse(LumiraBiomeGen.speciesSet().stream().anyMatch(s -> s.toLowerCase().contains("spruce")));
	}

	@Test void terrainTexturesAreSquare64Pngs() throws Exception {
		Path dir = Path.of("src/main/resources/assets/admin_control/textures/block");
		for (String name : new String[]{"albion_grass_top.png", "albion_grass_side.png", "albion_dirt.png", "albion_stone.png", "albion_cobblestone.png"}) {
			Path file = dir.resolve(name);
			assertTrue(Files.isRegularFile(file), name);
			var image = ImageIO.read(file.toFile());
			assertNotNull(image, name);
			assertEquals(64, image.getWidth(), name);
			assertEquals(64, image.getHeight(), name);
		}
	}
}
