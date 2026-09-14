// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge.bridge

import dev.bananz0.opentvbridge.core.DiagnosticStage
import dev.bananz0.opentvbridge.core.DiagnosticsLog
import dev.bananz0.opentvbridge.core.InstalledTargets
import dev.bananz0.opentvbridge.core.LaunchContext
import dev.bananz0.opentvbridge.core.LaunchRequest
import dev.bananz0.opentvbridge.core.LaunchRequestFactory
import dev.bananz0.opentvbridge.core.MediaMatch
import dev.bananz0.opentvbridge.core.ParsedTitle
import dev.bananz0.opentvbridge.core.RecentOpenGuard
import dev.bananz0.opentvbridge.core.Requirement
import dev.bananz0.opentvbridge.core.ResolveResult
import dev.bananz0.opentvbridge.core.RoutingPolicy
import dev.bananz0.opentvbridge.core.RoutingPreferences
import dev.bananz0.opentvbridge.core.TargetApp
import dev.bananz0.opentvbridge.network.MetadataResolver

/**
 * Prepares a target for a match.
 *
 * Returning `null` means this target cannot serve the match — the pipeline
 * then falls through to the next candidate. That is how a Kodi instance that
 * does not have the film, or an unreachable Jellyfin server, hands over to the
 * user's next-preferred app instead of failing the whole selection.
 */
fun interface TargetPreparer {
    fun prepare(target: TargetApp, match: MediaMatch): LaunchContext?

    companion object {
        /** No optional integrations configured: every target launches plainly. */
        val PLAIN = TargetPreparer { _, _ -> LaunchContext() }
    }
}

/**
 * The whole path from a parsed launcher title to an opened app.
 *
 * Deliberately free of Android types so the routing and fallback behaviour can
 * be tested on the JVM.
 */
class BridgePipeline(
    private val resolver: MetadataResolver,
    private val preferences: () -> RoutingPreferences,
    private val installed: InstalledTargets,
    private val launch: (LaunchRequest) -> Boolean,
    private val diagnostics: DiagnosticsLog,
    private val guard: RecentOpenGuard = RecentOpenGuard(),
    private val preparer: TargetPreparer = TargetPreparer.PLAIN,
    private val watchlist: ((MediaMatch) -> Boolean)? = null,
    private val available: () -> Set<Requirement> = { emptySet() },
) {
    fun handle(query: ParsedTitle, launcherPackage: String? = null) {
        when (val result = resolver.resolve(query)) {
            is ResolveResult.Found -> open(result.match, query, launcherPackage)

            ResolveResult.NotFound -> diagnostics.record(
                stage = DiagnosticStage.IGNORED,
                launcherPackage = launcherPackage,
                parsedTitle = query.title,
                parsedYear = query.year,
                detail = "no confident metadata match",
            )

            is ResolveResult.NetworkError -> diagnostics.record(
                stage = DiagnosticStage.FAILED,
                launcherPackage = launcherPackage,
                parsedTitle = query.title,
                parsedYear = query.year,
                detail = result.message ?: "metadata lookup failed",
            )
        }
    }

    private fun open(match: MediaMatch, query: ParsedTitle, launcherPackage: String?) {
        diagnostics.record(
            stage = DiagnosticStage.RESOLVED,
            launcherPackage = launcherPackage,
            parsedTitle = query.title,
            parsedYear = query.year,
            matchTitle = match.title,
            imdbId = match.imdbId,
            score = match.score,
        )

        if (!guard.shouldOpen("${match.type}:${match.imdbId}")) {
            diagnostics.record(
                stage = DiagnosticStage.IGNORED,
                launcherPackage = launcherPackage,
                matchTitle = match.title,
                imdbId = match.imdbId,
                detail = "repeat of the previous selection",
            )
            return
        }

        val candidates = RoutingPolicy.candidates(preferences(), match.type, installed, available())
        for (target in candidates) {
            val context = preparer.prepare(target, match) ?: continue
            if (!launch(LaunchRequestFactory.forMedia(target, match, context))) continue

            diagnostics.record(
                stage = DiagnosticStage.LAUNCHED,
                launcherPackage = launcherPackage,
                matchTitle = match.title,
                imdbId = match.imdbId,
                score = match.score,
                target = target,
            )
            watchlist?.invoke(match)
            return
        }

        diagnostics.record(
            stage = DiagnosticStage.FAILED,
            launcherPackage = launcherPackage,
            matchTitle = match.title,
            imdbId = match.imdbId,
            detail = "no destination accepted the launch (tried ${candidates.size})",
        )
    }
}
