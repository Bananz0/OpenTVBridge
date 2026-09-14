// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contracts for the destinations added after v0.1.0. Each expectation here was
 * read from the destination's own published source; see docs/COMPATIBILITY.md.
 */
class LaunchContractsTest {
    private val movie = MediaMatch("tt0371746", MediaType.MOVIE, "Iron Man", 2008)
    private val series = MediaMatch("tt0386676", MediaType.SERIES, "The Office", 2005)

    @Test fun `Jellyfin opens an item directly once a library id is known`() {
        val request = LaunchRequestFactory.forMedia(
            TargetApp.JELLYFIN,
            movie,
            LaunchContext(libraryItemId = "abc123"),
        ) as LaunchRequest.Item

        assertEquals(mapOf("ItemId" to "abc123"), request.extras)
        assertEquals("org.jellyfin.androidtv", request.packageName)
        assertEquals(LaunchRequestFactory.JELLYFIN_STARTUP_ACTIVITY, request.componentClass)
    }

    @Test fun `Jellyfin still searches when no server is configured`() {
        val request = LaunchRequestFactory.forMedia(TargetApp.JELLYFIN, series)
        assertTrue(request is LaunchRequest.Search)
        assertEquals("The Office", (request as LaunchRequest.Search).query)
    }

    @Test fun `Fladder prefers its details route over its request route`() {
        val request = LaunchRequestFactory.forMedia(
            TargetApp.FLADDER,
            movie,
            LaunchContext(libraryItemId = "item-1", tmdbId = 1726),
        ) as LaunchRequest.View
        assertEquals("fladder:///details?id=item-1", request.uri)
    }

    @Test fun `Fladder falls back to the Jellyseerr route keyed on TMDB`() {
        val film = LaunchRequestFactory.forMedia(
            TargetApp.FLADDER,
            movie,
            LaunchContext(tmdbId = 1726),
        ) as LaunchRequest.View
        assertEquals("fladder:///seerr/movie/1726", film.uri)

        // Fladder's route uses TMDB's "tv" path, not Stremio's "series".
        val show = LaunchRequestFactory.forMedia(
            TargetApp.FLADDER,
            series,
            LaunchContext(tmdbId = 2316),
        ) as LaunchRequest.View
        assertEquals("fladder:///seerr/tv/2316", show.uri)
    }

    @Test fun `Fladder opens the app when it has no id to address`() {
        val request = LaunchRequestFactory.forMedia(TargetApp.FLADDER, movie)
        assertEquals("nl.jknaapen.fladder", (request as LaunchRequest.Launch).packageName)
    }

    @Test fun `Fladder deep links never leak to a browser`() {
        val request = LaunchRequestFactory.forMedia(
            TargetApp.FLADDER,
            movie,
            LaunchContext(tmdbId = 1),
        ) as LaunchRequest.View
        assertTrue(!request.allowGenericFallback)
    }

    @Test fun `Plex uses the resolved item page when a token supplied one`() {
        val request = LaunchRequestFactory.forMedia(
            TargetApp.PLEX,
            movie,
            LaunchContext(plexPublicUrl = "https://watch.plex.tv/movie/iron-man"),
        ) as LaunchRequest.View
        assertEquals("https://watch.plex.tv/movie/iron-man", request.uri)
    }

    @Test fun `Kodi only ever brings the app forward`() {
        // The library lookup and playback happen over JSON-RPC beforehand.
        val request = LaunchRequestFactory.forMedia(TargetApp.KODI, movie)
        assertEquals("org.xbmc.kodi", (request as LaunchRequest.Launch).packageName)
    }

    @Test fun `Emby addresses an item only when a server provided the id`() {
        assertTrue(LaunchRequestFactory.forMedia(TargetApp.EMBY, movie) is LaunchRequest.Launch)

        val request = LaunchRequestFactory.forMedia(
            TargetApp.EMBY,
            movie,
            LaunchContext(libraryItemId = "e1"),
        ) as LaunchRequest.Item
        assertEquals(mapOf("ItemId" to "e1"), request.extras)
        // Emby publishes no activity name, so none is assumed.
        assertNull(request.componentClass)
    }

    @Test fun `Wholphin uses its documented view and search routes`() {
        val withItem = LaunchRequestFactory.forMedia(
            TargetApp.WHOLPHIN,
            movie,
            LaunchContext(libraryItemId = "5cf8f8e7-2a5f-4aa9-8c12-ddf63d42ee6d"),
        ) as LaunchRequest.View
        assertEquals(
            "wholphin://view?itemId=5cf8f8e7-2a5f-4aa9-8c12-ddf63d42ee6d",
            withItem.uri,
        )

        val withoutItem = LaunchRequestFactory.forMedia(TargetApp.WHOLPHIN, series)
            as LaunchRequest.View
        assertEquals("wholphin://search?query=The%20Office", withoutItem.uri)
    }

    @Test fun `WuPlay uses its registered movie and series hosts`() {
        assertEquals(
            "wuplay://movie/tt0371746",
            (LaunchRequestFactory.forMedia(TargetApp.WUPLAY, movie) as LaunchRequest.View).uri,
        )
        assertEquals(
            "wuplay://series/tt0386676",
            (LaunchRequestFactory.forMedia(TargetApp.WUPLAY, series) as LaunchRequest.View).uri,
        )
    }

    @Test fun `CloudStream uses the search scheme its own manifest documents`() {
        val request = LaunchRequestFactory.forMedia(TargetApp.CLOUDSTREAM, series)
            as LaunchRequest.View
        assertEquals("cloudstreamsearch://The%20Office", request.uri)
        assertEquals("com.lagradost.cloudstream3", request.packageName)
        // The prerelease build is a separate package and is tried after it.
        assertEquals(listOf("com.lagradost.cloudstream3.prerelease"), request.fallbackPackageNames)
    }

    @Test fun `unverified and item-specific deep links never reach a browser`() {
        // An inferred or item-specific URI must not be handed to whatever else
        // claims ACTION_VIEW when the destination app is absent.
        val unverifiable = listOf(
            LaunchRequestFactory.forMedia(TargetApp.WUPLAY, movie),
            LaunchRequestFactory.forMedia(TargetApp.WHOLPHIN, movie),
            LaunchRequestFactory.forMedia(
                TargetApp.FLADDER,
                movie,
                LaunchContext(libraryItemId = "x"),
            ),
        )
        unverifiable.forEach {
            assertTrue(!(it as LaunchRequest.View).allowGenericFallback)
        }
    }

    @Test fun `an unconfigured install behaves exactly as v0_1_0 did`() {
        assertEquals(
            "nuvio://movie/tt0371746",
            (LaunchRequestFactory.forMedia(TargetApp.NUVIO, movie) as LaunchRequest.View).uri,
        )
        assertEquals(
            "https://watch.plex.tv/search?q=Iron%20Man",
            (LaunchRequestFactory.forMedia(TargetApp.PLEX, movie) as LaunchRequest.View).uri,
        )
    }
}
