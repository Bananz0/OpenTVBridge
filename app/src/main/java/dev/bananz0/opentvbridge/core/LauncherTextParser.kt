// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge.core

private val YEAR_SUFFIX = Regex("""\s*[\[(](19\d{2}|20\d{2})[\])]\s*$""")
private val EDITION_SUFFIX = Regex(
    """\s*[\[(](?:VO|VE|VOSE|VOS|SUB|DUB|4K|UHD|HD|SD|HDR|Dolby(?:\s+Vision)?)[^\])]*[\])]\s*$""",
    RegexOption.IGNORE_CASE,
)

/** Spaces Google TV emits around punctuation that break naive marker matching. */
private val NON_BREAKING_SPACES = Regex("[\u00A0\u202F\u2009]")

/**
 * Launcher cards describe themselves in the device language, so detection
 * markers are per-locale. Upstream hard-coded Spanish; this keeps the same
 * shape but covers the languages Google TV ships on TV hardware.
 */
data class LauncherMarkers(
    val language: String,
    val duration: List<String>,
    val media: List<String>,
    val sponsored: List<String>,
)

object LauncherTextParser {
    val markers: List<LauncherMarkers> = listOf(
        LauncherMarkers(
            language = "en",
            duration = listOf("duration:", "length:"),
            media = listOf("costs:", "requires a subscription to", "rating:", "score:"),
            sponsored = listOf("sponsored"),
        ),
        LauncherMarkers(
            language = "es",
            duration = listOf("duración:"),
            media = listOf("cuesta:", "se necesita una suscripción a", "puntuación:"),
            sponsored = listOf("patrocinado"),
        ),
        LauncherMarkers(
            language = "de",
            duration = listOf("dauer:", "länge:"),
            media = listOf(
                "kostet:",
                "erfordert ein abo von",
                "erfordert ein abonnement",
                "bewertung:",
            ),
            sponsored = listOf("gesponsert"),
        ),
        LauncherMarkers(
            language = "fr",
            duration = listOf("durée :", "durée:"),
            media = listOf(
                "coûte :",
                "coûte:",
                "nécessite un abonnement à",
                "nécessite un abonnement",
                "note :",
                "note:",
            ),
            sponsored = listOf("sponsorisé"),
        ),
        LauncherMarkers(
            language = "it",
            duration = listOf("durata:"),
            media = listOf("costa:", "richiede un abbonamento a", "valutazione:"),
            sponsored = listOf("sponsorizzato"),
        ),
        LauncherMarkers(
            language = "pt",
            duration = listOf("duração:"),
            media = listOf(
                "custa:",
                "requer uma assinatura",
                "requer uma subscrição",
                "classificação:",
            ),
            sponsored = listOf("patrocinado"),
        ),
        LauncherMarkers(
            language = "nl",
            duration = listOf("duur:", "speelduur:"),
            media = listOf("kost:", "vereist een abonnement op", "beoordeling:"),
            sponsored = listOf("gesponsord"),
        ),
        LauncherMarkers(
            language = "pl",
            duration = listOf("czas trwania:"),
            media = listOf("kosztuje:", "wymaga subskrypcji", "ocena:"),
            sponsored = listOf("sponsorowane"),
        ),
    )

    private val durationMarkers = markers.flatMap(LauncherMarkers::duration)
    private val mediaMarkers = markers.flatMap(LauncherMarkers::media)
    private val sponsoredMarkers = markers.flatMap(LauncherMarkers::sponsored)

    fun fromDescription(
        description: CharSequence?,
        className: CharSequence? = null,
        eventText: List<CharSequence> = emptyList(),
    ): DetectedContent? {
        val raw = normalizeSpaces(description?.toString()).trim()
        if (raw.isBlank() || isSponsored(raw)) return null

        markerIndex(raw, durationMarkers)?.let { index ->
            return cleanTitle(raw.substring(0, index))
                .takeIf(String::isNotBlank)
                ?.let(DetectedContent::YouTube)
        }

        markerIndex(raw, mediaMarkers)?.let { index ->
            return parseTitle(raw.substring(0, index))?.let(DetectedContent::Media)
        }

        // Generic comma descriptions are only trusted for a leaf View with no
        // event text. ViewGroups commonly represent ads and action buttons.
        if (className?.toString() == "android.view.View" && eventText.isEmpty()) {
            val comma = raw.indexOf(", ")
            if (comma > 0 && raw.substring(comma + 2).isNotBlank()) {
                return parseTitle(raw.substring(0, comma))?.let(DetectedContent::Media)
            }
        }
        return null
    }

    fun fromHeroText(parts: List<CharSequence>?): DetectedContent.Media? {
        val first = normalizeSpaces(parts?.firstOrNull()?.toString()).trim()
        if (first.isBlank() || isSponsored(first)) return null
        return parseTitle(first)?.let(DetectedContent::Media)
    }

    fun parseTitle(value: String?): ParsedTitle? {
        var title = normalizeSpaces(value).trim().trimEnd(',', '·', '-', '—').trim()
        if (title.isBlank()) return null

        while (true) {
            val next = title.replace(EDITION_SUFFIX, "").trim()
            if (next == title) break
            title = next
        }

        val yearMatch = YEAR_SUFFIX.find(title)
        val year = yearMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (yearMatch != null) title = title.removeRange(yearMatch.range).trim()

        return title.takeIf(String::isNotBlank)?.let { ParsedTitle(it, year) }
    }

    /** Exposed so diagnostics can explain why a card was dropped. */
    fun isSponsored(value: String): Boolean =
        sponsoredMarkers.any { value.equals(it, ignoreCase = true) }

    private fun normalizeSpaces(value: String?): String =
        value.orEmpty().replace(NON_BREAKING_SPACES, " ")

    private fun markerIndex(value: String, markers: List<String>): Int? =
        markers.map { value.indexOf(it, ignoreCase = true) }
            .filter { it >= 0 }
            .minOrNull()

    private fun cleanTitle(value: String): String =
        value.trim().trimEnd(',', '·', '-', '—').trim()
}
