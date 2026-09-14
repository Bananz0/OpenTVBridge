// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge.network

import com.google.gson.JsonParser
import dev.bananz0.opentvbridge.core.MediaMatch
import dev.bananz0.opentvbridge.core.MediaType
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Response parsing for the optional integrations. These run against captured
 * response shapes rather than live services, so they stay deterministic and
 * need no credentials.
 */
class IntegrationParsingTest {
    private val http = OkHttpClient()
    private val movie = MediaMatch("tt0371746", MediaType.MOVIE, "Iron Man", 2008)
    private val series = MediaMatch("tt0386676", MediaType.SERIES, "The Office", 2005)

    // ---- Jellyfin / Emby --------------------------------------------------

    private val jellyfin = JellyfinLibraryClient(http, MediaServerConfig("http://localhost", "k"))

    @Test fun `the first usable item id is taken`() {
        val json = """
            {"Items":[
              {"Id":"","Name":"Broken"},
              {"Id":"abc","Name":"Iron Man","ProductionYear":2008}
            ]}
        """.trimIndent()
        assertEquals("abc", jellyfin.parseFirstItemId(json) { _, _ -> true })
    }

    @Test fun `a search fallback rejects a same-named item from another year`() {
        val json = """
            {"Items":[{"Id":"x","Name":"Iron Man","ProductionYear":1931}]}
        """.trimIndent()
        val accepted = jellyfin.parseFirstItemId(json) { name, year ->
            name == "Iron Man" && year == 2008
        }
        assertNull(accepted)
    }

    @Test fun `a malformed library response yields no id rather than throwing`() {
        assertNull(jellyfin.parseFirstItemId("not json at all") { _, _ -> true })
        assertNull(jellyfin.parseFirstItemId("""{"Items":[]}""") { _, _ -> true })
        assertNull(jellyfin.parseFirstItemId("""{}""") { _, _ -> true })
    }

    // ---- Plex -------------------------------------------------------------

    private val plex = PlexMatchClient(http, "token")

    @Test fun `the public item page is read out of a match response`() {
        val xml = """
            <MediaContainer size="1">
              <Video title="Iron Man" publicPagesURL="https://watch.plex.tv/movie/iron-man" />
            </MediaContainer>
        """.trimIndent()
        assertEquals("https://watch.plex.tv/movie/iron-man", plex.parsePublicPagesUrl(xml))
    }

    @Test fun `XML entities in the item page are decoded`() {
        val xml = """<Video publicPagesURL="https://watch.plex.tv/s?a=1&amp;b=2" />"""
        assertEquals("https://watch.plex.tv/s?a=1&b=2", plex.parsePublicPagesUrl(xml))
    }

    @Test fun `a match with no public page is not turned into a bogus URL`() {
        assertNull(plex.parsePublicPagesUrl("""<MediaContainer size="0" />"""))
        // Anything that is not https is refused rather than opened.
        assertNull(plex.parsePublicPagesUrl("""<Video publicPagesURL="javascript:alert(1)" />"""))
    }

    // ---- TMDB -------------------------------------------------------------

    private val tmdb = TmdbClient(http, "key")

    @Test fun `the TMDB id is read from the array matching the resolved type`() {
        val json = """
            {"movie_results":[{"id":1726}],"tv_results":[{"id":9999}]}
        """.trimIndent()
        assertEquals(1726, tmdb.parseFindResponse(json, MediaType.MOVIE))
        assertEquals(9999, tmdb.parseFindResponse(json, MediaType.SERIES))
    }

    @Test fun `a title catalogued under the other type is still found`() {
        val json = """{"movie_results":[],"tv_results":[{"id":2316}]}"""
        assertEquals(2316, tmdb.parseFindResponse(json, MediaType.MOVIE))
    }

    @Test fun `an empty TMDB find response yields no id`() {
        assertNull(tmdb.parseFindResponse("""{"movie_results":[],"tv_results":[]}""", MediaType.MOVIE))
        assertNull(tmdb.parseFindResponse("garbage", MediaType.MOVIE))
    }

    @Test fun `TMDB search results keep their type year and tmdb id`() {
        val json = """
            {"results":[
              {"media_type":"person","id":1,"name":"Someone"},
              {"media_type":"movie","id":1726,"title":"Iron Man","release_date":"2008-04-30"},
              {"media_type":"tv","id":2316,"name":"The Office","first_air_date":"2005-03-24"}
            ]}
        """.trimIndent()
        val candidates = tmdb.parseSearchResults(json)

        // People are not watchable and must not become candidates.
        assertEquals(2, candidates.size)
        assertEquals(MediaType.MOVIE, candidates[0].type)
        assertEquals(2008, candidates[0].year)
        assertEquals(1726, candidates[0].tmdbId)
        // The IMDb id is unknown until external_ids is fetched.
        assertEquals("", candidates[0].imdbId)
        assertEquals(MediaType.SERIES, candidates[1].type)
        assertEquals(2005, candidates[1].year)
    }

    // ---- Kodi -------------------------------------------------------------

    private val kodi = KodiRpcClient(http, KodiConfig("http://localhost:8080"))

    private fun result(json: String) = JsonParser.parseString(json).asJsonObject

    @Test fun `an IMDb id match beats a title match`() {
        val json = """
            {"movies":[
              {"movieid":1,"title":"Iron Man","year":2008,"imdbnumber":""},
              {"movieid":2,"title":"Something Else","year":1999,"imdbnumber":"tt0371746"}
            ]}
        """.trimIndent()
        assertEquals(2, kodi.findId(result(json), "movies", "movieid", movie))
    }

    @Test fun `a title match is used when no IMDb id is stored`() {
        val json = """{"movies":[{"movieid":7,"title":"Iron Man","year":2008}]}"""
        assertEquals(7, kodi.findId(result(json), "movies", "movieid", movie))
    }

    @Test fun `a same-named film from another year is not played`() {
        val json = """{"movies":[{"movieid":7,"title":"Iron Man","year":1931}]}"""
        assertNull(kodi.findId(result(json), "movies", "movieid", movie))
    }

    @Test fun `an empty library yields nothing to open`() {
        assertNull(kodi.findId(result("""{"movies":[]}"""), "movies", "movieid", movie))
        assertNull(kodi.findId(result("{}"), "movies", "movieid", movie))
    }

    // ---- Trakt ------------------------------------------------------------

    @Test fun `watchlist entries use the field matching the media type`() {
        val trakt = TraktClient(http, TraktConfig("id", "token"))
        assertEquals(
            """{"movies":[{"ids":{"imdb":"tt0371746"}}]}""",
            trakt.buildBody(movie),
        )
        assertEquals(
            """{"shows":[{"ids":{"imdb":"tt0386676"}}]}""",
            trakt.buildBody(series),
        )
    }
}
