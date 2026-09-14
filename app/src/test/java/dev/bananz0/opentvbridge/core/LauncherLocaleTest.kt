// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Upstream only recognised Spanish launcher cards. These cover the languages
 * added here, plus the typography Google TV actually emits.
 */
class LauncherLocaleTest {
    /** Google TV inserts these around punctuation in some locales. */
    private val narrowNoBreakSpace = " "
    private val noBreakSpace = " "

    private fun mediaTitle(description: String): String? =
        (LauncherTextParser.fromDescription(description) as? DetectedContent.Media)
            ?.parsedTitle
            ?.title

    private fun youTubeTitle(description: String): String? =
        (LauncherTextParser.fromDescription(description) as? DetectedContent.YouTube)?.title

    @Test fun `media markers are recognised in every supported language`() {
        val cards = mapOf(
            "en" to "Iron Man, rating: 7.9",
            "es" to "Iron Man, puntuación: 7,9",
            "de" to "Iron Man, bewertung: 7,9",
            "it" to "Iron Man, valutazione: 7,9",
            "pt" to "Iron Man, classificação: 7,9",
            "nl" to "Iron Man, beoordeling: 7,9",
            "pl" to "Iron Man, ocena: 7,9",
        )
        cards.forEach { (language, card) ->
            assertEquals("card for $language", "Iron Man", mediaTitle(card))
        }
    }

    @Test fun `a no-break space before a French colon still matches`() {
        assertEquals("Iron Man", mediaTitle("Iron Man, note${narrowNoBreakSpace}: 7,9"))
        assertEquals("Iron Man", mediaTitle("Iron Man, note${noBreakSpace}: 7,9"))
        assertEquals("Iron Man", mediaTitle("Iron Man, note: 7,9"))
    }

    @Test fun `subscription markers are recognised beyond Spanish`() {
        assertEquals("Severance", mediaTitle("Severance, requires a subscription to Apple TV+"))
        assertEquals("Severance", mediaTitle("Severance, se necesita una suscripción a Apple TV+"))
        assertEquals("Severance", mediaTitle("Severance, erfordert ein Abo von Apple TV+"))
        assertEquals("Severance", mediaTitle("Severance, nécessite un abonnement à Apple TV+"))
    }

    @Test fun `duration markers route to SmartTube in every language`() {
        assertEquals("A video", youTubeTitle("A video, duration: 10:00"))
        assertEquals("A video", youTubeTitle("A video, duración: 10:00"))
        assertEquals("A video", youTubeTitle("A video, Dauer: 10:00"))
        assertEquals("A video", youTubeTitle("A video, czas trwania: 10:00"))
    }

    @Test fun `sponsored cards are dropped in every language`() {
        listOf(
            "sponsored", "patrocinado", "gesponsert", "sponsorisé",
            "sponsorizzato", "gesponsord", "sponsorowane",
        ).forEach { label ->
            assertNull(label, LauncherTextParser.fromDescription(label))
            assertNull(label, LauncherTextParser.fromHeroText(listOf(label)))
        }
    }

    @Test fun `a no-break space inside a title is normalised away`() {
        assertEquals(
            "Iron Man",
            LauncherTextParser.parseTitle("Iron${noBreakSpace}Man")?.title,
        )
    }

    @Test fun `marker tables stay internally consistent`() {
        assertTrue(LauncherTextParser.markers.isNotEmpty())
        LauncherTextParser.markers.forEach { markers ->
            assertTrue(markers.language, markers.duration.isNotEmpty())
            assertTrue(markers.language, markers.media.isNotEmpty())
            assertTrue(markers.language, markers.sponsored.isNotEmpty())
            // Markers are compared case-insensitively, so storing them
            // lowercase keeps the table honest about what is compared.
            val all = markers.duration + markers.media + markers.sponsored
            assertTrue(markers.language, all.all { it == it.lowercase() })
        }
    }
}
