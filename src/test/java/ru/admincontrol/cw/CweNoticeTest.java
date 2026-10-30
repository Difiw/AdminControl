package ru.admincontrol.cw;

import org.junit.jupiter.api.Test;
import ru.admincontrol.util.CweNotice;

import static org.junit.jupiter.api.Assertions.*;

class CweNoticeTest {
	private static final long T = 1_761_141_480_000L; // 22.10.2025-ish, inside the accepted range

	@Test void roundTrip() {
		var d = new CweNotice.Data(CweNotice.Kind.RESERVED, "Aluya", T);
		assertEquals(d, CweNotice.decode(CweNotice.encode(d)));
	}

	@Test void decoderRejectsUntrustedPayloads() {
		assertNull(CweNotice.decode(null));
		assertNull(CweNotice.decode("RESERVED\u001eAluya"));
		assertNull(CweNotice.decode("BAN\u001eAluya\u001e" + T));
		assertNull(CweNotice.decode("RESERVED\u001e<script>\u001e" + T));
		assertNull(CweNotice.decode("RESERVED\u001eAluya\u001e12"));
		assertNull(CweNotice.decode("RESERVED\u001eAluya\u001eabc"));
		assertNull(CweNotice.decode("RESERVED\u001e" + "A".repeat(40) + "\u001e" + T));
		assertNull(CweNotice.decode("RESERVED\u001eAluya\u001e" + T + "\u001eextra"));
	}

	@Test void fallbackTextHidesStaffInformation() {
		String text = CweNotice.fallback(new CweNotice.Data(CweNotice.Kind.ACCOUNT, "Aluya", T));
		assertTrue(text.contains("Aluya"));
		assertTrue(text.contains("Common World: Expedition"));
		assertFalse(text.toLowerCase().contains("кем внесено"));
	}

	@Test void nameValidation() {
		assertTrue(CweNotice.validName("Aluya"));
		assertTrue(CweNotice.validName("a_1"));
		assertFalse(CweNotice.validName("a b"));
		assertFalse(CweNotice.validName(""));
		assertFalse(CweNotice.validName("a".repeat(17)));
	}

	@Test void namesAreComparedCaseInsensitively() {
		assertEquals(CweBlacklist.key("ALUYA"), CweBlacklist.key(" aluya "));
	}

	@Test void paginationRoundsUp() {
		assertEquals(1, CweBlacklist.pages(0));
		assertEquals(1, CweBlacklist.pages(10));
		assertEquals(2, CweBlacklist.pages(11));
	}
}
