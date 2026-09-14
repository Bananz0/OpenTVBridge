// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge.network

import dev.bananz0.opentvbridge.core.MediaMatch
import dev.bananz0.opentvbridge.core.MediaType
import dev.bananz0.opentvbridge.core.ParsedTitle
import dev.bananz0.opentvbridge.core.ResolveResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositeResolverTest {
    private val query = ParsedTitle("Iron Man", 2008)
    private val match = MediaMatch("tt0371746", MediaType.MOVIE, "Iron Man", 2008)

    @Test fun `the first confident match wins and later resolvers are not called`() {
        var secondCalled = false
        val resolver = CompositeResolver(
            listOf(
                MetadataResolver { ResolveResult.Found(match) },
                MetadataResolver { secondCalled = true; ResolveResult.NotFound },
            ),
        )
        assertEquals(ResolveResult.Found(match), resolver.resolve(query))
        assertTrue(!secondCalled)
    }

    @Test fun `a later resolver can answer what the first one missed`() {
        val resolver = CompositeResolver(
            listOf(
                MetadataResolver { ResolveResult.NotFound },
                MetadataResolver { ResolveResult.Found(match) },
            ),
        )
        assertEquals(ResolveResult.Found(match), resolver.resolve(query))
    }

    @Test fun `a definite miss is reported as a miss even when another failed`() {
        // Cinemeta answering "nothing matched" is real information; a TMDB
        // outage alongside it must not turn that into a network error.
        val resolver = CompositeResolver(
            listOf(
                MetadataResolver { ResolveResult.NotFound },
                MetadataResolver { ResolveResult.NetworkError("timeout") },
            ),
        )
        assertEquals(ResolveResult.NotFound, resolver.resolve(query))
    }

    @Test fun `only a total outage is reported as a network error`() {
        val resolver = CompositeResolver(
            listOf(
                MetadataResolver { ResolveResult.NetworkError("dns") },
                MetadataResolver { ResolveResult.NetworkError("timeout") },
            ),
        )
        assertEquals(ResolveResult.NetworkError("timeout"), resolver.resolve(query))
    }

    @Test fun `no resolvers is a miss rather than a crash`() {
        assertEquals(ResolveResult.NotFound, CompositeResolver(emptyList()).resolve(query))
    }
}
