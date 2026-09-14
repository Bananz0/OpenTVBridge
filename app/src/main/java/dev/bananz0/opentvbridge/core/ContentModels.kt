// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge.core

enum class MediaType(val wireValue: String) {
    MOVIE("movie"),
    SERIES("series"),
    ;

    /** The path segment TMDB and Fladder's Seerr route use for this type. */
    val tmdbPath: String get() = if (this == MOVIE) "movie" else "tv"
}

data class ParsedTitle(
    val title: String,
    val year: Int? = null,
    val typeHint: MediaType? = null,
)

data class MediaMatch(
    val imdbId: String,
    val type: MediaType,
    val title: String,
    val year: Int? = null,
    val score: Int = 0,
    val tmdbId: Int? = null,
)

sealed interface ResolveResult {
    data class Found(val match: MediaMatch) : ResolveResult
    data object NotFound : ResolveResult
    data class NetworkError(val message: String? = null) : ResolveResult
}

sealed interface DetectedContent {
    data class Media(val parsedTitle: ParsedTitle) : DetectedContent
    data class YouTube(val title: String) : DetectedContent
}

/**
 * Ids that only exist once optional, user-supplied credentials have been used.
 * Everything here is null on a stock install, and every destination has a
 * documented behaviour for that case.
 */
data class LaunchContext(
    /** Item id on the user's own Jellyfin/Emby server. */
    val libraryItemId: String? = null,
    /** `publicPagesURL` returned by Plex's match endpoint. */
    val plexPublicUrl: String? = null,
    /** TMDB id, used by Fladder's Jellyseerr route. */
    val tmdbId: Int? = null,
)
