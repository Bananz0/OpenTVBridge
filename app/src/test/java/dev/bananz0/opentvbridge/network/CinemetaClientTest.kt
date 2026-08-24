package dev.bananz0.opentvbridge.network

import dev.bananz0.opentvbridge.core.MediaType
import dev.bananz0.opentvbridge.core.ParsedTitle
import dev.bananz0.opentvbridge.core.ResolveResult
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CinemetaClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: CinemetaClient

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        client = CinemetaClient(OkHttpClient(), server.url("/"))
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun `resolves movie and URL-encodes catalog search`() {
        server.enqueue(jsonResponse(metas("tt0371746", "movie", "Iron Man", "2008")))
        server.enqueue(jsonResponse("{\"metas\":[]}"))

        val result = client.resolve(ParsedTitle("Iron Man", 2008)) as ResolveResult.Found

        assertEquals("tt0371746", result.match.imdbId)
        assertEquals(MediaType.MOVIE, result.match.type)
        assertEquals("/catalog/movie/top/search=Iron%20Man.json", server.takeRequest().requestUrl?.encodedPath)
        assertEquals("/catalog/series/top/search=Iron%20Man.json", server.takeRequest().requestUrl?.encodedPath)
    }

    @Test fun `one provider can fail while the other succeeds`() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(jsonResponse(metas("tt0386676", "series", "The Office", "2005-2013")))

        val result = client.resolve(ParsedTitle("The Office", 2005)) as ResolveResult.Found
        assertEquals(MediaType.SERIES, result.match.type)
        assertEquals(2005, result.match.year)
    }

    @Test fun `two provider failures return network error`() {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(429))
        assertTrue(client.resolve(ParsedTitle("Arrival")) is ResolveResult.NetworkError)
    }

    @Test fun `empty responses return not found`() {
        server.enqueue(jsonResponse("{\"metas\":[]}"))
        server.enqueue(jsonResponse("{}"))
        assertEquals(ResolveResult.NotFound, client.resolve(ParsedTitle("Not a real title xyz")))
    }

    @Test fun `malformed provider response does not hide valid other result`() {
        server.enqueue(jsonResponse("not-json"))
        server.enqueue(jsonResponse(metas("tt0386676", "series", "The Office", "2005-2013")))
        assertTrue(client.resolve(ParsedTitle("The Office")) is ResolveResult.Found)
    }

    @Test fun `parser skips incomplete and invalid catalog rows`() {
        val parsed = client.parseCandidates(
            """{"metas":[
              {"id":"tt1234567","type":"movie","name":"Valid","releaseInfo":"2024"},
              {"id":"","type":"movie","name":"No id"},
              {"id":"tt9999999","type":"movie","name":""},
              null
            ]}""",
            MediaType.MOVIE,
        )
        assertEquals(1, parsed.size)
        assertEquals(2024, parsed.single().year)
    }

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun metas(id: String, type: String, name: String, releaseInfo: String) =
        """{"metas":[{"id":"$id","imdb_id":"$id","type":"$type","name":"$name","releaseInfo":"$releaseInfo"}]}"""
}
