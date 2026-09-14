// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge.network

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Parses a response body into an object, or `null`.
 *
 * Gson throws on malformed input, and these bodies come from servers the user
 * points us at — including ones that answer with an HTML error page. Every
 * response parser goes through here so a bad body is a miss, never a crash on
 * the accessibility service's background thread.
 */
internal fun parseJsonObject(body: String?): JsonObject? = runCatching {
    JsonParser.parseString(body.orEmpty()).takeIf { it.isJsonObject }?.asJsonObject
}.getOrNull()

/** Reads a string field, treating absent, null, and non-string as empty. */
internal fun JsonObject.stringOrEmpty(name: String): String = get(name)
    ?.takeUnless { it.isJsonNull }
    ?.runCatching { asString }
    ?.getOrNull()
    .orEmpty()

/** Reads an int field, treating absent, null, and non-numeric as `null`. */
internal fun JsonObject.intOrNull(name: String): Int? = get(name)
    ?.takeUnless { it.isJsonNull }
    ?.runCatching { asInt }
    ?.getOrNull()
