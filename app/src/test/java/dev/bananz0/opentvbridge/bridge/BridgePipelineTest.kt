// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge.bridge

import dev.bananz0.opentvbridge.core.DiagnosticStage
import dev.bananz0.opentvbridge.core.DiagnosticsLog
import dev.bananz0.opentvbridge.core.InstalledTargets
import dev.bananz0.opentvbridge.core.LaunchContext
import dev.bananz0.opentvbridge.core.LaunchRequest
import dev.bananz0.opentvbridge.core.MediaMatch
import dev.bananz0.opentvbridge.core.MediaType
import dev.bananz0.opentvbridge.core.ParsedTitle
import dev.bananz0.opentvbridge.core.RecentOpenGuard
import dev.bananz0.opentvbridge.core.ResolveResult
import dev.bananz0.opentvbridge.core.RoutingPreferences
import dev.bananz0.opentvbridge.core.TargetApp
import dev.bananz0.opentvbridge.network.MetadataResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgePipelineTest {
    private val match = MediaMatch("tt0371746", MediaType.MOVIE, "Iron Man", 2008, score = 100)
    private val query = ParsedTitle("Iron Man", 2008)
    private val diagnostics = DiagnosticsLog()

    private fun pipeline(
        resolver: MetadataResolver = MetadataResolver { ResolveResult.Found(match) },
        order: List<TargetApp> = listOf(TargetApp.NUVIO, TargetApp.STREMIO),
        launch: (LaunchRequest) -> Boolean = { true },
        preparer: TargetPreparer = TargetPreparer.PLAIN,
        watchlist: ((MediaMatch) -> Boolean)? = null,
    ) = BridgePipeline(
        resolver = resolver,
        preferences = { RoutingPreferences(order = order, skipUninstalled = false) },
        installed = InstalledTargets.ALL,
        launch = launch,
        diagnostics = diagnostics,
        guard = RecentOpenGuard(),
        preparer = preparer,
        watchlist = watchlist,
    )

    private fun stages() = diagnostics.snapshot().map { it.stage }

    @Test fun `a refused destination falls through to the next one`() {
        val attempted = mutableListOf<String>()
        pipeline(
            launch = { request ->
                val packageName = (request as LaunchRequest.View).packageName
                attempted += packageName
                // Nuvio is not installed; Stremio accepts.
                packageName == TargetApp.STREMIO.packageName
            },
        ).handle(query)

        // One request per destination; the package-level fallback within a
        // destination is the launcher's job, not the pipeline's.
        assertEquals(listOf("com.nuvio.tv", "com.stremio.one"), attempted)
        assertEquals(TargetApp.STREMIO, diagnostics.snapshot().first().target)
        assertTrue(DiagnosticStage.LAUNCHED in stages())
    }

    @Test fun `a preparer returning null skips that destination`() {
        val requested = mutableListOf<LaunchRequest>()
        pipeline(
            preparer = { target, _ ->
                // Stands in for Kodi not holding the title in its library.
                if (target == TargetApp.NUVIO) null else LaunchContext()
            },
            launch = { requested += it; true },
        ).handle(query)

        val launched = diagnostics.snapshot().first { it.stage == DiagnosticStage.LAUNCHED }
        assertEquals(TargetApp.STREMIO, launched.target)
        // Nuvio never reached the launcher at all.
        assertEquals(1, requested.size)
    }

    @Test fun `every destination refusing is recorded as a failure`() {
        pipeline(launch = { false }).handle(query)
        assertEquals(DiagnosticStage.FAILED, stages().first())
    }

    @Test fun `a repeated selection is ignored rather than reopened`() {
        val opened = mutableListOf<LaunchRequest>()
        val guard = RecentOpenGuard()
        val shared = BridgePipeline(
            resolver = { ResolveResult.Found(match) },
            preferences = { RoutingPreferences(order = listOf(TargetApp.NUVIO), skipUninstalled = false) },
            installed = InstalledTargets.ALL,
            launch = { opened += it; true },
            diagnostics = diagnostics,
            guard = guard,
        )
        shared.handle(query)
        shared.handle(query)

        assertEquals(1, opened.size)
        assertTrue(diagnostics.snapshot().any { it.detail == "repeat of the previous selection" })
    }

    @Test fun `a low-confidence result opens nothing`() {
        var launched = false
        pipeline(
            resolver = { ResolveResult.NotFound },
            launch = { launched = true; true },
        ).handle(query)

        assertTrue(!launched)
        assertEquals(DiagnosticStage.IGNORED, stages().first())
    }

    @Test fun `a metadata outage is reported as a failure not a miss`() {
        pipeline(resolver = { ResolveResult.NetworkError("timeout") }).handle(query)
        assertEquals(DiagnosticStage.FAILED, stages().first())
        assertEquals("timeout", diagnostics.snapshot().first().detail)
    }

    @Test fun `the watchlist is only written after something actually opened`() {
        val added = mutableListOf<MediaMatch>()
        pipeline(launch = { false }, watchlist = { added += it; true }).handle(query)
        assertTrue(added.isEmpty())

        pipeline(launch = { true }, watchlist = { added += it; true }).handle(query)
        assertEquals(listOf(match), added)
    }
}
