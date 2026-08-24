package dev.bananz0.opentvbridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherTextParserTest {
    @Test fun `extracts Spanish price card without truncating title comma`() {
        val result = LauncherTextParser.fromDescription("Monstruos, S.A., cuesta: 3,99 €")
        assertEquals("Monstruos, S.A.", (result as DetectedContent.Media).parsedTitle.title)
    }

    @Test fun `extracts English rating marker case insensitively`() {
        val result = LauncherTextParser.fromDescription("Arrival, RATING: 8.2")
        assertEquals("Arrival", (result as DetectedContent.Media).parsedTitle.title)
    }

    @Test fun `extracts YouTube titles in both languages`() {
        assertEquals(
            "A difficult, excellent video",
            (LauncherTextParser.fromDescription("A difficult, excellent video, Duration: 12 min") as DetectedContent.YouTube).title,
        )
        assertEquals(
            "Viaje a Japón",
            (LauncherTextParser.fromDescription("Viaje a Japón, Duración: 8 min") as DetectedContent.YouTube).title,
        )
    }

    @Test fun `parses year and repeated edition suffixes`() {
        assertEquals(
            ParsedTitle("Dune", 2021),
            LauncherTextParser.parseTitle(" Dune (2021) (VOSE) [4K] "),
        )
    }

    @Test fun `hero rejects sponsored cards in two languages`() {
        assertNull(LauncherTextParser.fromHeroText(listOf("Patrocinado", "Ver ahora")))
        assertNull(LauncherTextParser.fromHeroText(listOf("Sponsored", "Watch now")))
    }

    @Test fun `hero accepts non Latin title`() {
        val result = LauncherTextParser.fromHeroText(listOf("千と千尋の神隠し", "2001"))
        assertEquals("千と千尋の神隠し", result?.parsedTitle?.title)
    }

    @Test fun `generic descriptions require a leaf view without event text`() {
        assertTrue(
            LauncherTextParser.fromDescription(
                "Arrival, A linguist meets visitors",
                "android.view.View",
            ) is DetectedContent.Media,
        )
        assertNull(
            LauncherTextParser.fromDescription(
                "Netflix, Watch now",
                "android.view.ViewGroup",
                listOf("WATCH NOW"),
            ),
        )
    }

    @Test fun `blank malformed and label-only values are ignored`() {
        assertNull(LauncherTextParser.fromDescription(null))
        assertNull(LauncherTextParser.fromDescription("  "))
        assertNull(LauncherTextParser.fromDescription("Settings"))
        assertNull(LauncherTextParser.parseTitle("---"))
    }
}
