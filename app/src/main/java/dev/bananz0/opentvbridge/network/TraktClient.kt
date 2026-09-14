// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge.network

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dev.bananz0.opentvbridge.core.MediaMatch
import dev.bananz0.opentvbridge.core.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Adds a resolved title to the user's Trakt watchlist.
 *
 * This is a side effect of a launch, not a destination: Trakt has no Android
 * TV app to open. It stays off unless the user supplies their own Trakt client
 * id and access token.
 */
class TraktClient(
    private val client: OkHttpClient,
    private val config: TraktConfig,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : WatchlistSink {

    override fun add(match: MediaMatch): Boolean {
        if (!config.isUsable) return false
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/sync/watchlist")
            .post(buildBody(match).toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .header("trakt-api-version", "2")
            .header("trakt-api-key", config.clientId)
            .header("Authorization", "Bearer ${config.accessToken}")
            .header("User-Agent", "OpenTVBridge")
            .build()

        return runCatching {
            client.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    internal fun buildBody(match: MediaMatch): String {
        val ids = JsonObject().apply { addProperty("imdb", match.imdbId) }
        val entry = JsonObject().apply { add("ids", ids) }
        val collection = JsonArray().apply { add(entry) }
        val field = if (match.type == MediaType.MOVIE) "movies" else "shows"
        return JsonObject().apply { add(field, collection) }.toString()
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.trakt.tv"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
