// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge.network

import dev.bananz0.opentvbridge.core.MediaMatch
import dev.bananz0.opentvbridge.core.MetadataMatcher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Looks an item up on the user's own Jellyfin or Emby server.
 *
 * Emby's `/Items` query surface and `X-Emby-Token` header are compatible with
 * Jellyfin's for the two parameters used here, so one client serves both. The
 * provider-id query is exact; the search-term query is the fallback for
 * libraries whose items carry no IMDb id.
 */
class JellyfinLibraryClient(
    private val client: OkHttpClient,
    private val config: MediaServerConfig,
) : LibraryLookup {

    override fun findItemId(match: MediaMatch): String? {
        if (!config.isUsable) return null
        val base = config.baseUrl.trim().trimEnd('/').toHttpUrlOrNull() ?: return null
        return byProviderId(base, match) ?: bySearchTerm(base, match)
    }

    private fun byProviderId(base: HttpUrl, match: MediaMatch): String? {
        val url = itemsUrl(base)
            .addQueryParameter("AnyProviderIdEquals", "Imdb.${match.imdbId}")
            .addQueryParameter("Limit", "1")
            .build()
        return firstItemId(url) { _, _ -> true }
    }

    private fun bySearchTerm(base: HttpUrl, match: MediaMatch): String? {
        val url = itemsUrl(base)
            .addQueryParameter("SearchTerm", match.title)
            .addQueryParameter("Limit", "10")
            .build()
        val wanted = MetadataMatcher.normalize(match.title)
        return firstItemId(url) { name, year ->
            MetadataMatcher.normalize(name) == wanted &&
                (match.year == null || year == null || match.year == year)
        }
    }

    private fun itemsUrl(base: HttpUrl): HttpUrl.Builder = base.newBuilder()
        .addPathSegment("Items")
        .addQueryParameter("Recursive", "true")
        .addQueryParameter("IncludeItemTypes", "Movie,Series")
        .addQueryParameter("Fields", "ProviderIds,ProductionYear")

    private fun firstItemId(url: HttpUrl, accept: (String, Int?) -> Boolean): String? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("X-Emby-Token", config.apiKey)
            .header("User-Agent", USER_AGENT)
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                parseFirstItemId(body, accept)
            }
        }.getOrNull()
    }

    internal fun parseFirstItemId(json: String, accept: (String, Int?) -> Boolean): String? {
        val root = parseJsonObject(json) ?: return null
        val items = root.getAsJsonArray("Items") ?: return null
        for (element in items) {
            if (!element.isJsonObject) continue
            val item = element.asJsonObject
            val id = item.stringOrEmpty("Id")
            if (id.isBlank()) continue
            if (accept(item.stringOrEmpty("Name"), item.intOrNull("ProductionYear"))) return id
        }
        return null
    }

    private companion object {
        const val USER_AGENT = "OpenTVBridge"
    }
}
