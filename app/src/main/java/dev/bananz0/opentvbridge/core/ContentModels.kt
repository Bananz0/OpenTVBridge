package dev.bananz0.opentvbridge.core

enum class MediaType(val wireValue: String) {
    MOVIE("movie"),
    SERIES("series"),
}

data class ParsedTitle(
    val title: String,
    val year: Int? = null,
    val typeHint: MediaType? = null,
)

data class MediaMatch(
    val imdbId: String,
    val type: MediaType,
    val title: String,
    val year: Int? = null,
    val score: Int = 0,
)

sealed interface ResolveResult {
    data class Found(val match: MediaMatch) : ResolveResult
    data object NotFound : ResolveResult
    data class NetworkError(val message: String? = null) : ResolveResult
}

sealed interface DetectedContent {
    data class Media(val parsedTitle: ParsedTitle) : DetectedContent
    data class YouTube(val title: String) : DetectedContent
}
