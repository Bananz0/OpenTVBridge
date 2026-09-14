// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge.network

import dev.bananz0.opentvbridge.core.MediaMatch

/**
 * Optional integrations.
 *
 * Every credential in this file is supplied by the user and stored only on
 * their device. OpenTVBridge ships no API key of its own, so a stock install
 * uses the keyless Cinemeta path and every integration below stays disabled.
 */

/** A Jellyfin or Emby server the user owns. */
data class MediaServerConfig(
    val baseUrl: String,
    val apiKey: String,
) {
    val isUsable: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank()
}

/** A Kodi instance with its JSON-RPC web server enabled. */
data class KodiConfig(
    val baseUrl: String,
    val username: String = "",
    val password: String = "",
) {
    val isUsable: Boolean get() = baseUrl.isNotBlank()
}

data class TraktConfig(
    val clientId: String,
    val accessToken: String,
) {
    val isUsable: Boolean get() = clientId.isNotBlank() && accessToken.isNotBlank()
}

/** Resolves an item id on the user's own media server. */
fun interface LibraryLookup {
    fun findItemId(match: MediaMatch): String?
}

/** Resolves Plex's public item page for a title. */
fun interface PlexLookup {
    fun publicPageUrl(match: MediaMatch): String?
}

/** Maps an IMDb id to a TMDB id. */
fun interface TmdbLookup {
    fun tmdbId(match: MediaMatch): Int?
}

/** Records a title against the user's own account. */
fun interface WatchlistSink {
    fun add(match: MediaMatch): Boolean
}

/** Plays or shows a title on a Kodi instance. */
fun interface KodiControl {
    fun open(match: MediaMatch): Boolean
}
