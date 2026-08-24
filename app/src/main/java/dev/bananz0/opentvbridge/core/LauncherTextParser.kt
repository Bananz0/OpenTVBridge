package dev.bananz0.opentvbridge.core

private val YEAR_SUFFIX = Regex("""\s*[\[(](19\d{2}|20\d{2})[\])]\s*$""")
private val EDITION_SUFFIX = Regex(
    """\s*[\[(](?:VO|VE|VOSE|VOS|SUB|DUB|4K|UHD|HD|SD|HDR|Dolby(?:\s+Vision)?)[^\])]*[\])]\s*$""",
    RegexOption.IGNORE_CASE,
)

object LauncherTextParser {
    private val youtubeMarkers = listOf("duración:", "duration:")
    private val mediaMarkers = listOf(
        "cuesta:",
        "se necesita una suscripción a",
        "puntuación:",
        "costs:",
        "requires a subscription to",
        "rating:",
    )

    fun fromDescription(
        description: CharSequence?,
        className: CharSequence? = null,
        eventText: List<CharSequence> = emptyList(),
    ): DetectedContent? {
        val raw = description?.toString()?.trim().orEmpty()
        if (raw.isBlank()) return null
        if (raw.equals("patrocinado", true) || raw.equals("sponsored", true)) return null

        markerIndex(raw, youtubeMarkers)?.let { index ->
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
        val first = parts?.firstOrNull()?.toString()?.trim().orEmpty()
        if (first.isBlank() || first.equals("patrocinado", true) || first.equals("sponsored", true)) {
            return null
        }
        return parseTitle(first)?.let(DetectedContent::Media)
    }

    fun parseTitle(value: String?): ParsedTitle? {
        var title = value?.trim()?.trimEnd(',', '·', '-', '—')?.trim().orEmpty()
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

    private fun markerIndex(value: String, markers: List<String>): Int? =
        markers.map { value.indexOf(it, ignoreCase = true) }
            .filter { it >= 0 }
            .minOrNull()

    private fun cleanTitle(value: String): String =
        value.trim().trimEnd(',', '·', '-', '—').trim()
}
