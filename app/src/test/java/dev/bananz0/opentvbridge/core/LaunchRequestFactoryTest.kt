package dev.bananz0.opentvbridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchRequestFactoryTest {
    private val movie = MediaMatch("tt0371746", MediaType.MOVIE, "Iron Man", 2008)
    private val series = MediaMatch("tt0386676", MediaType.SERIES, "The Office", 2005)

    @Test fun `Nuvio uses separate movie and series contracts`() {
        assertEquals(
            "nuvio://movie/tt0371746",
            (LaunchRequestFactory.forMedia(TargetApp.NUVIO, movie) as LaunchRequest.View).uri,
        )
        assertEquals(
            "nuvio://detail/tv/tt0386676",
            (LaunchRequestFactory.forMedia(TargetApp.NUVIO, series) as LaunchRequest.View).uri,
        )
    }

    @Test fun `Stremio uses correct type path`() {
        assertEquals(
            "stremio:///detail/movie/tt0371746",
            (LaunchRequestFactory.forMedia(TargetApp.STREMIO, movie) as LaunchRequest.View).uri,
        )
        assertEquals(
            "stremio:///detail/series/tt0386676",
            (LaunchRequestFactory.forMedia(TargetApp.STREMIO, series) as LaunchRequest.View).uri,
        )
    }

    @Test fun `Plex search encodes title and permits browser fallback`() {
        val request = LaunchRequestFactory.forMedia(
            TargetApp.PLEX,
            movie.copy(title = "WALL·E & friends"),
        ) as LaunchRequest.View
        assertEquals("https://watch.plex.tv/search?q=WALL%C2%B7E%20%26%20friends", request.uri)
        assertTrue(request.allowGenericFallback)
    }

    @Test fun `Jellyfin receives explicit search request`() {
        val request = LaunchRequestFactory.forMedia(TargetApp.JELLYFIN, series) as LaunchRequest.Search
        assertEquals("The Office", request.query)
        assertEquals("org.jellyfin.androidtv", request.packageName)
        assertEquals("org.jellyfin.androidtv.ui.startup.StartupActivity", request.componentClass)
    }

    @Test fun `SmartTube stable and beta never fall back to ordinary YouTube`() {
        val stable = LaunchRequestFactory.forSmartTube("A title, with punctuation")
        val beta = LaunchRequestFactory.forSmartTube("A title, with punctuation", beta = true)
        assertEquals("org.smarttube.stable", stable.packageName)
        assertEquals("org.smarttube.beta", beta.packageName)
        assertFalse(stable.allowGenericFallback)
        assertTrue(stable.uri.endsWith("A%20title%2C%20with%20punctuation"))
    }
}
