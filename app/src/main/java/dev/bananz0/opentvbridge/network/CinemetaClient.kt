// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge.network

import dev.bananz0.opentvbridge.core.MediaType
import dev.bananz0.opentvbridge.core.MetadataCandidate
import dev.bananz0.opentvbridge.core.MetadataMatcher
import dev.bananz0.opentvbridge.core.ParsedTitle
import dev.bananz0.opentvbridge.core.ResolveResult
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class CinemetaClient(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl,
) : MetadataResolver {

    override fun resolve(query: ParsedTitle): ResolveResult {
        val candidates = mutableListOf<MetadataCandidate>()
        var failures = 0

        MediaType.entries.forEach { type ->
            runCatching { fetch(type, query.title) }
                .onSuccess(candidates::addAll)
                .onFailure { failures++ }
        }

        if (candidates.isEmpty() && failures == MediaType.entries.size) {
            return ResolveResult.NetworkError("All metadata requests failed")
        }
        return MetadataMatcher.bestMatch(query, candidates)
            ?.let(ResolveResult::Found)
            ?: ResolveResult.NotFound
    }

    private fun fetch(type: MediaType, title: String): List<MetadataCandidate> {
        val url = baseUrl.newBuilder()
            .addPathSegment("catalog")
            .addPathSegment(type.wireValue)
            .addPathSegment("top")
            .addPathSegment("search=$title.json")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "OpenTVBridge/0.1")
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Cinemeta HTTP ${response.code}")
            val body = response.body?.string() ?: error("Cinemeta returned an empty body")
            parseCandidates(body, type)
        }
    }

    internal fun parseCandidates(json: String, requestedType: MediaType): List<MetadataCandidate> {
        val root = parseJsonObject(json) ?: return emptyList()
        val metas = root.getAsJsonArray("metas") ?: return emptyList()
        return buildList {
            for (element in metas) {
                if (!element.isJsonObject) continue
                val item = element.asJsonObject
                val id = item.stringOrEmpty("imdb_id").ifBlank { item.stringOrEmpty("id") }
                val title = item.stringOrEmpty("name").trim()
                if (id.isBlank() || title.isBlank()) continue
                val itemType = when (item.stringOrEmpty("type").lowercase()) {
                    "movie" -> MediaType.MOVIE
                    "series", "tv" -> MediaType.SERIES
                    else -> requestedType
                }
                val year = Regex("(?:19|20)\\d{2}")
                    .find(item.stringOrEmpty("releaseInfo"))
                    ?.value
                    ?.toIntOrNull()
                add(MetadataCandidate(id, itemType, title, year))
            }
        }
    }
}
