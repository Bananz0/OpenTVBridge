// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingPolicyTest {
    private fun installed(vararg packages: String) = InstalledTargets { it in packages }

    /** Manual mode isolates the ordering rules from capability ranking. */
    private fun manual(
        order: List<TargetApp>,
        movieOverride: TargetApp? = null,
        seriesOverride: TargetApp? = null,
        skipUninstalled: Boolean = true,
    ) = RoutingPreferences(
        order = order,
        movieOverride = movieOverride,
        seriesOverride = seriesOverride,
        skipUninstalled = skipUninstalled,
        autoRank = false,
    )

    @Test fun `per-type override is tried before the general order`() {
        val preferences = manual(
            order = listOf(TargetApp.NUVIO, TargetApp.STREMIO),
            seriesOverride = TargetApp.JELLYFIN,
            skipUninstalled = false,
        )
        assertEquals(
            listOf(TargetApp.JELLYFIN, TargetApp.NUVIO, TargetApp.STREMIO),
            RoutingPolicy.candidates(preferences, MediaType.SERIES),
        )
        // Films are unaffected by a series override.
        assertEquals(
            listOf(TargetApp.NUVIO, TargetApp.STREMIO),
            RoutingPolicy.candidates(preferences, MediaType.MOVIE),
        )
    }

    @Test fun `override already in the order is not duplicated`() {
        val preferences = manual(
            order = listOf(TargetApp.NUVIO, TargetApp.STREMIO),
            movieOverride = TargetApp.STREMIO,
            skipUninstalled = false,
        )
        assertEquals(
            listOf(TargetApp.STREMIO, TargetApp.NUVIO),
            RoutingPolicy.candidates(preferences, MediaType.MOVIE),
        )
    }

    @Test fun `uninstalled destinations are skipped`() {
        val preferences = manual(
            order = listOf(TargetApp.NUVIO, TargetApp.STREMIO, TargetApp.JELLYFIN),
        )
        assertEquals(
            listOf(TargetApp.STREMIO, TargetApp.JELLYFIN),
            RoutingPolicy.candidates(
                preferences,
                MediaType.MOVIE,
                installed("com.stremio.one", "org.jellyfin.androidtv"),
            ),
        )
    }

    @Test fun `either Nuvio package counts as installed`() {
        val preferences = manual(order = listOf(TargetApp.NUVIO))
        // Play builds use com.nuvio.app rather than the full build's id.
        assertEquals(
            listOf(TargetApp.NUVIO),
            RoutingPolicy.candidates(preferences, MediaType.MOVIE, installed("com.nuvio.app")),
        )
        assertEquals(
            "com.nuvio.app",
            RoutingPolicy.packageFor(TargetApp.NUVIO, installed("com.nuvio.app")),
        )
    }

    @Test fun `nothing installed still yields the unfiltered order`() {
        val preferences = manual(order = listOf(TargetApp.NUVIO, TargetApp.STREMIO))
        // Better to attempt and record a failure than to drop the event silently.
        assertEquals(
            listOf(TargetApp.NUVIO, TargetApp.STREMIO),
            RoutingPolicy.candidates(preferences, MediaType.MOVIE, installed()),
        )
    }

    @Test fun `packageFor falls back to the preferred package`() {
        assertEquals("com.nuvio.tv", RoutingPolicy.packageFor(TargetApp.NUVIO, installed()))
    }

    @Test fun `every target declares at least one package`() {
        assertTrue(TargetApp.entries.all { it.packageNames.isNotEmpty() })
        // Package ids must be unique across targets or routing is ambiguous.
        val all = TargetApp.entries.flatMap(TargetApp::packageNames)
        assertEquals(all.size, all.distinct().size)
    }

    @Test fun `every destination participates in fallback by default`() {
        // A destination missing from the default order could only ever be
        // reached by being chosen as primary, which silently disables fallback
        // to it.
        assertEquals(
            TargetApp.entries.toSet(),
            RoutingPreferences.DEFAULT_ORDER.toSet(),
        )
    }

    // ---- Capability ranking ----------------------------------------------

    @Test fun `a destination that can open the item beats one that can only search`() {
        val preferences = RoutingPreferences(
            order = listOf(TargetApp.CLOUDSTREAM, TargetApp.NUVIO),
            skipUninstalled = false,
        )
        // CloudStream is listed first but can only search; Nuvio addresses the
        // item from a public id, so auto-ranking promotes it.
        val candidates = RoutingPolicy.candidates(preferences, MediaType.MOVIE)
        assertEquals(TargetApp.NUVIO, candidates.first())
    }

    @Test fun `configuring a server promotes that ecosystem's clients`() {
        val preferences = RoutingPreferences(
            order = listOf(TargetApp.CLOUDSTREAM, TargetApp.JELLYFIN),
            skipUninstalled = false,
        )
        // Compared against each other rather than against the whole pool:
        // other id-addressable apps outrank both when nothing is configured.
        val unconfigured = RoutingPolicy.candidates(preferences, MediaType.MOVIE)
        assertTrue(
            "listed order should stand while both can only search",
            unconfigured.indexOf(TargetApp.CLOUDSTREAM) < unconfigured.indexOf(TargetApp.JELLYFIN),
        )

        val configured = RoutingPolicy.candidates(
            preferences,
            MediaType.MOVIE,
            available = setOf(Requirement.JELLYFIN_SERVER),
        )
        assertTrue(
            "a configured server should promote Jellyfin above a search-only app",
            configured.indexOf(TargetApp.JELLYFIN) < configured.indexOf(TargetApp.CLOUDSTREAM),
        )
        // And it should now lead outright, since it can open the exact item.
        assertEquals(TargetApp.JELLYFIN, configured.first())
    }

    @Test fun `the user's order breaks ties between equally capable apps`() {
        val preferences = RoutingPreferences(
            order = listOf(TargetApp.STREMIO, TargetApp.NUVIO),
            skipUninstalled = false,
        )
        val candidates = RoutingPolicy.candidates(preferences, MediaType.MOVIE)
        assertEquals(
            listOf(TargetApp.STREMIO, TargetApp.NUVIO),
            candidates.take(2),
        )
    }

    @Test fun `an override still leads even when something outranks it`() {
        val preferences = RoutingPreferences(
            order = TargetApp.entries.toList(),
            movieOverride = TargetApp.CLOUDSTREAM,
            skipUninstalled = false,
        )
        // An explicit instruction is not a preference to be optimised away.
        assertEquals(
            TargetApp.CLOUDSTREAM,
            RoutingPolicy.candidates(preferences, MediaType.MOVIE).first(),
        )
    }

    @Test fun `auto-ranking never drops the user on an app's home screen`() {
        val preferences = RoutingPreferences(
            order = listOf(TargetApp.NUVIO),
            skipUninstalled = false,
        )
        val candidates = RoutingPolicy.candidates(preferences, MediaType.MOVIE)
        // Kodi and Emby can only open their app when unconfigured, and the user
        // did not ask for them, so they are not silently used as fallback.
        assertTrue(TargetApp.KODI !in candidates)
        assertTrue(TargetApp.EMBY !in candidates)
        assertTrue(TargetApp.FLADDER !in candidates)
    }

    @Test fun `a user who names an app-only destination still gets it`() {
        val preferences = RoutingPreferences(
            order = listOf(TargetApp.KODI, TargetApp.NUVIO),
            skipUninstalled = false,
        )
        assertTrue(TargetApp.KODI in RoutingPolicy.candidates(preferences, MediaType.MOVIE))
    }

    @Test fun `configuring Kodi makes it eligible without being named`() {
        val preferences = RoutingPreferences(
            order = listOf(TargetApp.NUVIO),
            skipUninstalled = false,
        )
        assertTrue(
            TargetApp.KODI in RoutingPolicy.candidates(
                preferences,
                MediaType.MOVIE,
                available = setOf(Requirement.KODI_INSTANCE),
            ),
        )
    }

    @Test fun `auto-ranking reaches an installed app the user never listed`() {
        val preferences = RoutingPreferences(order = listOf(TargetApp.NUVIO))
        val candidates = RoutingPolicy.candidates(
            preferences,
            MediaType.MOVIE,
            installed("com.stremio.one"),
        )
        // Nuvio is absent, so the installed Stremio is found automatically.
        assertEquals(listOf(TargetApp.STREMIO), candidates)
    }

    @Test fun `manual mode considers only what the user listed`() {
        val preferences = manual(order = listOf(TargetApp.NUVIO), skipUninstalled = false)
        assertEquals(
            listOf(TargetApp.NUVIO),
            RoutingPolicy.candidates(preferences, MediaType.MOVIE),
        )
    }

    // ---- Capability model -------------------------------------------------

    @Test fun `capability reflects the configuration present`() {
        assertEquals(
            TargetCapability.SEARCH,
            TargetApp.PLEX.capabilityWith(emptySet()),
        )
        assertEquals(
            TargetCapability.EXACT_ITEM,
            TargetApp.PLEX.capabilityWith(setOf(Requirement.PLEX_TOKEN)),
        )
        // Fladder is lifted by either a Jellyfin server or a TMDB key.
        assertEquals(
            TargetCapability.APP_ONLY,
            TargetApp.FLADDER.capabilityWith(emptySet()),
        )
        assertEquals(
            TargetCapability.EXACT_ITEM,
            TargetApp.FLADDER.capabilityWith(setOf(Requirement.TMDB_KEY)),
        )
        assertEquals(
            TargetCapability.EXACT_ITEM,
            TargetApp.FLADDER.capabilityWith(setOf(Requirement.JELLYFIN_SERVER)),
        )
        // CloudStream has no id route, so nothing lifts it above search.
        assertEquals(
            TargetCapability.SEARCH,
            TargetApp.CLOUDSTREAM.capabilityWith(Requirement.entries.toSet()),
        )
    }
}
