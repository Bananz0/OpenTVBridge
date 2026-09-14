// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge.bridge

import android.content.Context
import dev.bananz0.opentvbridge.BuildConfig
import dev.bananz0.opentvbridge.SettingsRepository
import dev.bananz0.opentvbridge.core.Diagnostics
import dev.bananz0.opentvbridge.core.LaunchContext
import dev.bananz0.opentvbridge.core.MediaMatch
import dev.bananz0.opentvbridge.core.RecentOpenGuard
import dev.bananz0.opentvbridge.core.TargetApp
import dev.bananz0.opentvbridge.launch.AndroidTargetLauncher
import dev.bananz0.opentvbridge.launch.InstalledPackages
import dev.bananz0.opentvbridge.network.CinemetaClient
import dev.bananz0.opentvbridge.network.CompositeResolver
import dev.bananz0.opentvbridge.network.JellyfinLibraryClient
import dev.bananz0.opentvbridge.network.KodiRpcClient
import dev.bananz0.opentvbridge.network.MetadataResolver
import dev.bananz0.opentvbridge.network.PlexMatchClient
import dev.bananz0.opentvbridge.network.TmdbClient
import dev.bananz0.opentvbridge.network.TraktClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Builds the resolver and pipeline from current settings.
 *
 * Every optional client is constructed only when its credential is present, so
 * a stock install makes exactly one kind of network request: Cinemeta.
 */
object BridgeFactory {

    fun httpClient(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    fun resolver(settings: SettingsRepository, http: OkHttpClient): MetadataResolver {
        val cinemeta = CinemetaClient(http, BuildConfig.CINEMETA_BASE_URL.toHttpUrl())
        val tmdbKey = settings.tmdbApiKey
        return if (tmdbKey.isBlank()) {
            cinemeta
        } else {
            CompositeResolver(listOf(cinemeta, TmdbClient(http, tmdbKey)))
        }
    }

    fun pipeline(
        context: Context,
        settings: SettingsRepository,
        guard: RecentOpenGuard,
    ): BridgePipeline {
        val http = httpClient()
        val launcher = AndroidTargetLauncher(context)
        val trakt = settings.trakt
            .takeIf { it.isUsable && settings.traktWatchlistEnabled }
            ?.let { TraktClient(http, it) }

        return BridgePipeline(
            resolver = resolver(settings, http),
            preferences = settings::routingPreferences,
            installed = InstalledPackages(context),
            launch = launcher::open,
            diagnostics = Diagnostics.log,
            guard = guard,
            preparer = preparer(settings, http),
            watchlist = trakt?.let { sink -> { match: MediaMatch -> sink.add(match) } },
            available = settings::availableRequirements,
        )
    }

    /**
     * Turns a match into the ids a given destination needs. Runs on the
     * caller's background thread; each lookup is independently optional and
     * failing one only costs that destination its enrichment.
     */
    fun preparer(settings: SettingsRepository, http: OkHttpClient): TargetPreparer {
        val plex = settings.plexToken.takeIf(String::isNotBlank)
            ?.let { PlexMatchClient(http, it) }
        val jellyfin = settings.jellyfinServer.takeIf { it.isUsable }
            ?.let { JellyfinLibraryClient(http, it) }
        val emby = settings.embyServer.takeIf { it.isUsable }
            ?.let { JellyfinLibraryClient(http, it) }
        val kodi = settings.kodi.takeIf { it.isUsable }
            ?.let { KodiRpcClient(http, it) }
        val tmdb = settings.tmdbApiKey.takeIf(String::isNotBlank)
            ?.let { TmdbClient(http, it) }

        return TargetPreparer { target, match ->
            when (target) {
                TargetApp.PLEX -> LaunchContext(plexPublicUrl = plex?.publicPageUrl(match))

                // Both are Jellyfin clients, so the same library lookup gives
                // each the item id it addresses by.
                TargetApp.JELLYFIN,
                TargetApp.WHOLPHIN,
                -> LaunchContext(libraryItemId = jellyfin?.findItemId(match))

                // Fladder addresses items by Jellyfin id, and falls back to its
                // Jellyseerr route, which is keyed on TMDB.
                TargetApp.FLADDER -> {
                    val itemId = jellyfin?.findItemId(match)
                    LaunchContext(
                        libraryItemId = itemId,
                        tmdbId = if (itemId == null) match.tmdbId ?: tmdb?.tmdbId(match) else null,
                    )
                }

                TargetApp.EMBY -> LaunchContext(libraryItemId = emby?.findItemId(match))

                // Kodi is driven over JSON-RPC. If it cannot play the title,
                // this target yields to the next one rather than opening Kodi
                // on whatever happened to be on screen.
                TargetApp.KODI -> if (kodi?.open(match) == true) LaunchContext() else null

                TargetApp.NUVIO,
                TargetApp.STREMIO,
                TargetApp.WUPLAY,
                TargetApp.CLOUDSTREAM,
                -> LaunchContext()
            }
        }
    }

    private const val HTTP_TIMEOUT_SECONDS = 10L
}
