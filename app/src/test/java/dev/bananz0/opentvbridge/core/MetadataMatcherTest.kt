package dev.bananz0.opentvbridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataMatcherTest {
    @Test fun `normalization handles accents punctuation case and ampersands`() {
        assertEquals("wall e and friends", MetadataMatcher.normalize("WALL·E & Fríends!"))
    }

    @Test fun `exact title and year beat homonym`() {
        val result = MetadataMatcher.bestMatch(
            ParsedTitle("The Office", 2005, MediaType.SERIES),
            listOf(
                MetadataCandidate("tt0290978", MediaType.SERIES, "The Office", 2001),
                MetadataCandidate("tt0386676", MediaType.SERIES, "The Office", 2005),
            ),
        )
        assertEquals("tt0386676", result?.imdbId)
    }

    @Test fun `wrong type and distant year are rejected`() {
        val result = MetadataMatcher.bestMatch(
            ParsedTitle("Crash", 2004, MediaType.MOVIE),
            listOf(MetadataCandidate("tt0113670", MediaType.SERIES, "Crash", 1996)),
        )
        assertNull(result)
    }

    @Test fun `one year drift remains acceptable`() {
        val score = MetadataMatcher.score(
            ParsedTitle("Festival Cut", 2024),
            MetadataCandidate("tt1234567", MediaType.MOVIE, "Festival Cut", 2023),
        )
        assertTrue(score >= MetadataMatcher.MINIMUM_SCORE)
    }

    @Test fun `weak token overlap does not open unrelated title`() {
        val result = MetadataMatcher.bestMatch(
            ParsedTitle("Planet Earth"),
            listOf(MetadataCandidate("tt1234567", MediaType.SERIES, "Earth at Night", 2020)),
        )
        assertNull(result)
    }

    @Test fun `invalid ids and duplicates are ignored deterministically`() {
        val result = MetadataMatcher.bestMatch(
            ParsedTitle("Arrival"),
            listOf(
                MetadataCandidate("not-imdb", MediaType.MOVIE, "Arrival", 2016),
                MetadataCandidate("tt2543164", MediaType.MOVIE, "Arrival", 2016),
                MetadataCandidate("tt2543164", MediaType.MOVIE, "Arrival", 2016),
            ),
        )
        assertEquals("tt2543164", result?.imdbId)
    }
}
