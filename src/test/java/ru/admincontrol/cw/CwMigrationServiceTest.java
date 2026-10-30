package ru.admincontrol.cw;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CwMigrationServiceTest {
	@Test void legacyBuilderMigrationIsIdempotentAndRecoverable(){
		String legacy="{\"player\":{\"profileNbt\":\"{Health:10.0f}\",\"dimension\":\"minecraft:overworld\"}}";
		String once=CwMigrationService.migrateDocument("cw-builder-returns.json",legacy);
		String twice=CwMigrationService.migrateDocument("cw-builder-returns.json",once);
		assertEquals(JsonParser.parseString(once),JsonParser.parseString(twice));
		var record=JsonParser.parseString(once).getAsJsonObject().getAsJsonObject("player");
		assertEquals(2,record.get("schemaVersion").getAsInt());
		assertEquals("RECOVERY_REQUIRED",record.get("phase").getAsString());
		assertTrue(record.has("sessionId"));
	}
	@Test void progressMigrationUsesVersionField(){
		String migrated=CwMigrationService.migrateDocument("cw-story-progress.json","{\"u\":{\"chapter\":1}}");
		var record=JsonParser.parseString(migrated).getAsJsonObject().getAsJsonObject("u");
		assertEquals(1,record.get("version").getAsInt());
	}
}
