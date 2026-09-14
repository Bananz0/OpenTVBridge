// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge.network

import dev.bananz0.opentvbridge.core.MediaMatch
import dev.bananz0.opentvbridge.core.MediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Resolves Plex's public item page for a title using the user's own token.
 *
 * Without a token OpenTVBridge opens a Plex search instead, which is why this
 * whole client is optional. The token is sent as a header rather than a query
 * parameter so it does not end up in intermediary logs.
 */
class PlexMatchClient(
    private val client: OkHttpClient,
    private val token: String,
    baseUrl: String = DEFAULT_BASE_URL,
) : PlexLookup {
    private val base = baseUrl.toHttpUrl()

    override fun publicPageUrl(match: MediaMatch): String? {
        if (token.isBlank()) return null
        val url = base.newBuilder()
            .addPathSegments("library/metadata/matches")
            .addQueryParameter("type", if (match.type == MediaType.MOVIE) "1" else "2")
            .addQueryParameter("guid", "imdb://${match.imdbId}")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/xml")
            .header("X-Plex-Token", token)
            .header("X-Plex-Product", "OpenTVBridge")
            .header("User-Agent", "OpenTVBridge")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                parsePublicPagesUrl(response.body?.string().orEmpty())
            }
        }.getOrNull()
    }

    internal fun parsePublicPagesUrl(xml: String): String? =
        PUBLIC_PAGES_URL.find(xml)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::unescapeXml)
            ?.takeIf { it.startsWith("https://") }

    private fun unescapeXml(value: String): String = value
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")

    companion object {
        const val DEFAULT_BASE_URL = "https://metadata.provider.plex.tv/"
        private val PUBLIC_PAGES_URL = Regex("publicPagesURL=\"([^\"]+)\"")
    }
}
