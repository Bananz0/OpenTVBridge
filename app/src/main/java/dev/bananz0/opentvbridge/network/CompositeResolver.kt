// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge.network

import dev.bananz0.opentvbridge.core.ParsedTitle
import dev.bananz0.opentvbridge.core.ResolveResult

/**
 * Tries each resolver in order and returns the first confident match.
 *
 * Cinemeta comes first because it needs no credentials. A network error is
 * only reported when every resolver failed to reach its service — a resolver
 * that answered "nothing matched" is a real answer, not an outage.
 */
class CompositeResolver(
    private val resolvers: List<MetadataResolver>,
) : MetadataResolver {

    override fun resolve(query: ParsedTitle): ResolveResult {
        if (resolvers.isEmpty()) return ResolveResult.NotFound
        var lastError: ResolveResult.NetworkError? = null
        var sawAnswer = false

        for (resolver in resolvers) {
            when (val result = resolver.resolve(query)) {
                is ResolveResult.Found -> return result
                is ResolveResult.NotFound -> sawAnswer = true
                is ResolveResult.NetworkError -> lastError = result
            }
        }
        return if (sawAnswer) ResolveResult.NotFound else (lastError ?: ResolveResult.NotFound)
    }
}
