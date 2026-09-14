// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge.network

import dev.bananz0.opentvbridge.core.ParsedTitle
import dev.bananz0.opentvbridge.core.ResolveResult

fun interface MetadataResolver {
    fun resolve(query: ParsedTitle): ResolveResult
}
