// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge.network

import com.google.gson.JsonObject
import dev.bananz0.opentvbridge.core.MediaMatch
import dev.bananz0.opentvbridge.core.MediaType
import dev.bananz0.opentvbridge.core.MetadataMatcher
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Drives a Kodi instance over its documented JSON-RPC API.
 *
 * Kodi has no title-addressable intent, so the destination is reached over the
 * network instead: find the item in the user's own library, then play it
 * (films) or open it (series). The Android intent only brings Kodi forward.
 * Requires "Allow remote control via HTTP" in Kodi's settings.
 */
class KodiRpcClient(
    private val client: OkHttpClient,
    private val config: KodiConfig,
) : KodiControl {

    override fun open(match: MediaMatch): Boolean {
        if (!config.isUsable) return false
        return when (match.type) {
            MediaType.MOVIE -> openMovie(match)
            MediaType.SERIES -> openSeries(match)
        }
    }

    private fun openMovie(match: MediaMatch): Boolean {
        val movies = call(
            method = "VideoLibrary.GetMovies",
            params = params {
                add("properties", jsonArrayOf("title", "year", "imdbnumber"))
            },
        ) ?: return false
        val id = findId(movies, "movies", "movieid", match) ?: return false
        return call(
            method = "Player.Open",
            params = params {
                val item = JsonObject().apply { addProperty("movieid", id) }
                add("item", item)
            },
        ) != null
    }

    private fun openSeries(match: MediaMatch): Boolean {
        val shows = call(
            method = "VideoLibrary.GetTVShows",
            params = params {
                add("properties", jsonArrayOf("title", "year", "imdbnumber"))
            },
        ) ?: return false
        val id = findId(shows, "tvshows", "tvshowid", match) ?: return false
        return call(
            method = "GUI.ActivateWindow",
            params = params {
                addProperty("window", "videos")
                add("parameters", jsonArrayOf("videodb://tvshows/titles/$id/"))
            },
        ) != null
    }

    /** Prefers an IMDb id match, then an exact normalised title (and year). */
    internal fun findId(
        result: JsonObject,
        arrayName: String,
        idField: String,
        match: MediaMatch,
    ): Int? {
        val items = result.getAsJsonArray(arrayName) ?: return null
        val wanted = MetadataMatcher.normalize(match.title)
        var titleFallback: Int? = null

        for (element in items) {
            if (!element.isJsonObject) continue
            val item = element.asJsonObject
            val id = item.intOrNull(idField) ?: continue
            val imdb = item.stringOrEmpty("imdbnumber")
            if (imdb.isNotBlank() && imdb.equals(match.imdbId, ignoreCase = true)) return id

            val title = item.stringOrEmpty("title")
            val year = item.intOrNull("year")
            val titleMatches = MetadataMatcher.normalize(title) == wanted &&
                (match.year == null || year == null || match.year == year)
            if (titleMatches && titleFallback == null) titleFallback = id
        }
        return titleFallback
    }

    private fun call(method: String, params: JsonObject): JsonObject? {
        val url = config.baseUrl.trim().trimEnd('/').toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegment("jsonrpc")
            ?.build()
            ?: return null

        val payload = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", 1)
            addProperty("method", method)
            add("params", params)
        }.toString()

        val builder = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(JSON))
            .header("Accept", "application/json")
            .header("User-Agent", "OpenTVBridge")
        if (config.username.isNotBlank()) {
            builder.header("Authorization", Credentials.basic(config.username, config.password))
        }

        return runCatching {
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                parseJsonObject(response.body?.string())?.getAsJsonObject("result")
            }
        }.getOrNull()
    }

    private fun params(build: JsonObject.() -> Unit): JsonObject = JsonObject().apply(build)

    private fun jsonArrayOf(vararg values: String) = com.google.gson.JsonArray().apply {
        values.forEach(::add)
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
