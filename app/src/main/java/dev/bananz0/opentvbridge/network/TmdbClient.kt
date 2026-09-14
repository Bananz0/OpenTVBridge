// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge.network

import com.google.gson.JsonObject
import dev.bananz0.opentvbridge.core.MediaMatch
import dev.bananz0.opentvbridge.core.MediaType
import dev.bananz0.opentvbridge.core.MetadataCandidate
import dev.bananz0.opentvbridge.core.MetadataMatcher
import dev.bananz0.opentvbridge.core.ParsedTitle
import dev.bananz0.opentvbridge.core.ResolveResult
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Maps an IMDb id to a TMDB id using the user's own TMDB key.
 *
 * Upstream compiled a TMDB key into its APK, which made it a shared secret
 * that anyone could extract. OpenTVBridge ships no key: this client is inert
 * until the user supplies one, and only Fladder's Jellyseerr route needs it.
 */
class TmdbClient(
    private val client: OkHttpClient,
    private val apiKey: String,
    baseUrl: String = DEFAULT_BASE_URL,
) : TmdbLookup, MetadataResolver {
    private val base = baseUrl.toHttpUrl()

    /**
     * Secondary resolver, used only when Cinemeta finds nothing confident.
     * TMDB indexes titles Cinemeta's IMDb-derived catalogue omits, but the
     * IMDb id still has to be fetched separately, so this costs two requests
     * and is never the first choice.
     */
    override fun resolve(query: ParsedTitle): ResolveResult {
        if (apiKey.isBlank()) return ResolveResult.NotFound
        val candidates = runCatching { search(query) }
            .getOrElse { return ResolveResult.NetworkError(it.message) }
        val best = MetadataMatcher.bestMatch(query, candidates) ?: return ResolveResult.NotFound
        val imdbId = externalImdbId(best.tmdbId, best.type) ?: return ResolveResult.NotFound
        return ResolveResult.Found(best.copy(imdbId = imdbId))
    }

    private fun search(query: ParsedTitle): List<MetadataCandidate> {
        val url = base.newBuilder()
            .addPathSegment("search")
            .addPathSegment("multi")
            .addQueryParameter("query", query.title)
            .addQueryParameter("include_adult", "false")
            .addQueryParameter("api_key", apiKey)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "OpenTVBridge")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("TMDB HTTP ${response.code}")
            parseSearchResults(response.body?.string().orEmpty())
        }
    }

    /**
     * TMDB ids are carried in [MetadataCandidate.tmdbId]; the IMDb id stays
     * blank until [resolve] fills it in from `external_ids`.
     */
    internal fun parseSearchResults(json: String): List<MetadataCandidate> {
        val root = parseJsonObject(json) ?: return emptyList()
        val results = root.getAsJsonArray("results") ?: return emptyList()
        return buildList {
            for (element in results) {
                if (!element.isJsonObject) continue
                val item = element.asJsonObject
                // Anything that is not a film or series — people, most often —
                // is not watchable and must not become a candidate.
                val type = when (item.stringOrEmpty("media_type")) {
                    "movie" -> MediaType.MOVIE
                    "tv" -> MediaType.SERIES
                    else -> continue
                }
                val id = item.intOrNull("id") ?: continue
                val title = if (type == MediaType.MOVIE) {
                    item.stringOrEmpty("title")
                } else {
                    item.stringOrEmpty("name")
                }.trim()
                if (title.isBlank()) continue
                val date = if (type == MediaType.MOVIE) {
                    item.stringOrEmpty("release_date")
                } else {
                    item.stringOrEmpty("first_air_date")
                }
                add(MetadataCandidate("", type, title, date.take(4).toIntOrNull(), tmdbId = id))
            }
        }
    }

    private fun externalImdbId(tmdbId: Int?, type: MediaType): String? {
        tmdbId ?: return null
        val url = base.newBuilder()
            .addPathSegment(type.tmdbPath)
            .addPathSegment(tmdbId.toString())
            .addPathSegment("external_ids")
            .addQueryParameter("api_key", apiKey)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "OpenTVBridge")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                parseJsonObject(response.body?.string())
                    ?.stringOrEmpty("imdb_id")
                    ?.takeIf { it.startsWith("tt") }
            }
        }.getOrNull()
    }

    override fun tmdbId(match: MediaMatch): Int? {
        if (apiKey.isBlank()) return null
        val url = base.newBuilder()
            .addPathSegment("find")
            .addPathSegment(match.imdbId)
            .addQueryParameter("external_source", "imdb_id")
            .addQueryParameter("api_key", apiKey)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "OpenTVBridge")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                parseFindResponse(body, match.type)
            }
        }.getOrNull()
    }

    internal fun parseFindResponse(json: String, type: MediaType): Int? {
        val root = parseJsonObject(json) ?: return null
        // Prefer the array matching the resolved type, but accept the other one
        // when a title is catalogued differently by TMDB and Cinemeta.
        val preferred = if (type == MediaType.MOVIE) "movie_results" else "tv_results"
        val alternate = if (type == MediaType.MOVIE) "tv_results" else "movie_results"
        return firstId(root, preferred) ?: firstId(root, alternate)
    }

    private fun firstId(
        root: JsonObject,
        name: String,
    ): Int? = root.getAsJsonArray(name)
        ?.firstOrNull { it.isJsonObject }
        ?.asJsonObject
        ?.get("id")
        ?.takeUnless { it.isJsonNull }
        ?.runCatching { asInt }
        ?.getOrNull()

    companion object {
        const val DEFAULT_BASE_URL = "https://api.themoviedb.org/3/"
    }
}
