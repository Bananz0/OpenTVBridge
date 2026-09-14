// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge.core

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed interface LaunchRequest {
    /** `ACTION_VIEW` on a URI. */
    data class View(
        val uri: String,
        val packageName: String,
        val fallbackPackageNames: List<String> = emptyList(),
        val allowGenericFallback: Boolean = true,
    ) : LaunchRequest {
        val packageNamesInPriorityOrder: List<String>
            get() = (listOf(packageName) + fallbackPackageNames).distinct()
    }

    /** `ACTION_SEARCH` carrying a query extra. */
    data class Search(
        val query: String,
        val packageName: String,
        val componentClass: String? = null,
    ) : LaunchRequest

    /** `ACTION_VIEW` carrying string extras rather than a URI. */
    data class Item(
        val packageName: String,
        val extras: Map<String, String>,
        val componentClass: String? = null,
    ) : LaunchRequest

    /** Open the app's own launch intent; used when no item contract exists. */
    data class Launch(
        val packageName: String,
        val fallbackPackageNames: List<String> = emptyList(),
    ) : LaunchRequest {
        val packageNamesInPriorityOrder: List<String>
            get() = (listOf(packageName) + fallbackPackageNames).distinct()
    }
}

object LaunchRequestFactory {
    /** Jellyfin's `StartupActivity` extra for an item id. */
    const val JELLYFIN_ITEM_ID_EXTRA = "ItemId"
    const val JELLYFIN_STARTUP_ACTIVITY = "org.jellyfin.androidtv.ui.startup.StartupActivity"

    fun forMedia(
        target: TargetApp,
        match: MediaMatch,
        context: LaunchContext = LaunchContext(),
    ): LaunchRequest = when (target) {
        TargetApp.NUVIO -> LaunchRequest.View(
            uri = if (match.type == MediaType.MOVIE) {
                "nuvio://movie/${match.imdbId}"
            } else {
                "nuvio://detail/tv/${match.imdbId}"
            },
            packageName = target.packageName,
            fallbackPackageNames = target.packageNames.drop(1),
        )

        TargetApp.STREMIO -> LaunchRequest.View(
            uri = "stremio:///detail/${match.type.wireValue}/${match.imdbId}",
            packageName = target.packageName,
        )

        // WuPlay registers movie/series hosts on its own scheme. The hosts are
        // confirmed from its shipped manifest; the path shape is inferred from
        // its Stremio lineage, so no generic fallback is permitted.
        TargetApp.WUPLAY -> LaunchRequest.View(
            uri = "wuplay://${match.type.wireValue}/${match.imdbId}",
            packageName = target.packageName,
            allowGenericFallback = false,
        )

        // CloudStream's manifest documents this scheme itself:
        // "Allow searching with intents: cloudstreamsearch://Your%20Name".
        // It has no id-addressable route, so a search is the best available.
        TargetApp.CLOUDSTREAM -> LaunchRequest.View(
            uri = "cloudstreamsearch://${encode(match.title)}",
            packageName = target.packageName,
            fallbackPackageNames = target.packageNames.drop(1),
            allowGenericFallback = false,
        )

        // With the user's own Plex token the match endpoint yields a real item
        // page; without one, a public search is the honest fallback.
        TargetApp.PLEX -> LaunchRequest.View(
            uri = context.plexPublicUrl
                ?: "https://watch.plex.tv/search?q=${encode(match.title)}",
            packageName = target.packageName,
        )

        TargetApp.JELLYFIN -> if (context.libraryItemId != null) {
            LaunchRequest.Item(
                packageName = target.packageName,
                extras = mapOf(JELLYFIN_ITEM_ID_EXTRA to context.libraryItemId),
                componentClass = JELLYFIN_STARTUP_ACTIVITY,
            )
        } else {
            LaunchRequest.Search(
                query = match.title,
                packageName = target.packageName,
                componentClass = JELLYFIN_STARTUP_ACTIVITY,
            )
        }

        // Fladder routes `/details` by Jellyfin item id and `/seerr` by TMDB id.
        TargetApp.FLADDER -> when {
            context.libraryItemId != null -> LaunchRequest.View(
                uri = "fladder:///details?id=${encode(context.libraryItemId)}",
                packageName = target.packageName,
                allowGenericFallback = false,
            )

            context.tmdbId != null -> LaunchRequest.View(
                uri = "fladder:///seerr/${match.type.tmdbPath}/${context.tmdbId}",
                packageName = target.packageName,
                allowGenericFallback = false,
            )

            else -> LaunchRequest.Launch(target.packageName)
        }

        // Wholphin documents both routes in its own Intents.md. It is a
        // Jellyfin client, so the item id is the one the library lookup found.
        TargetApp.WHOLPHIN -> if (context.libraryItemId != null) {
            LaunchRequest.View(
                uri = "wholphin://view?itemId=${encode(context.libraryItemId)}",
                packageName = target.packageName,
                allowGenericFallback = false,
            )
        } else {
            LaunchRequest.View(
                uri = "wholphin://search?query=${encode(match.title)}",
                packageName = target.packageName,
                allowGenericFallback = false,
            )
        }

        // Emby's intent contract is not published; an item id is the only
        // addressing we attempt, and it is best-effort.
        TargetApp.EMBY -> if (context.libraryItemId != null) {
            LaunchRequest.Item(
                packageName = target.packageName,
                extras = mapOf(JELLYFIN_ITEM_ID_EXTRA to context.libraryItemId),
            )
        } else {
            LaunchRequest.Launch(target.packageName)
        }

        // Kodi is driven over JSON-RPC before this request is issued, so the
        // intent only needs to bring the app to the foreground.
        TargetApp.KODI -> LaunchRequest.Launch(target.packageName)
    }

    fun forSmartTube(title: String, beta: Boolean = false): LaunchRequest.View = LaunchRequest.View(
        uri = "https://www.youtube.com/results?search_query=${encode(title)}",
        packageName = if (beta) SmartTube.BETA else SmartTube.STABLE,
        allowGenericFallback = false,
    )

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
