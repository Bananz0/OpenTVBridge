package dev.bananz0.opentvbridge.core

import java.text.Normalizer
import kotlin.math.roundToInt

data class MetadataCandidate(
    val imdbId: String,
    val type: MediaType,
    val title: String,
    val year: Int? = null,
)

object MetadataMatcher {
    const val MINIMUM_SCORE = 70
    private val validImdb = Regex("tt[0-9]{5,12}")

    fun bestMatch(query: ParsedTitle, candidates: List<MetadataCandidate>): MediaMatch? =
        candidates.asSequence()
            .filter { validImdb.matches(it.imdbId) }
            .distinctBy { Triple(it.imdbId, it.type, normalize(it.title)) }
            .map { it to score(query, it) }
            .filter { it.second >= MINIMUM_SCORE }
            .sortedWith(
                compareByDescending<Pair<MetadataCandidate, Int>> { it.second }
                    .thenBy { it.first.year ?: Int.MAX_VALUE }
                    .thenBy { it.first.imdbId },
            )
            .firstOrNull()
            ?.let { (candidate, score) ->
                MediaMatch(candidate.imdbId, candidate.type, candidate.title, candidate.year, score)
            }

    fun score(query: ParsedTitle, candidate: MetadataCandidate): Int {
        val wanted = normalize(query.title)
        val actual = normalize(candidate.title)
        if (wanted.isBlank() || actual.isBlank()) return 0

        val titleScore = when {
            wanted == actual -> 85
            wanted.startsWith(actual) || actual.startsWith(wanted) -> 60
            else -> (tokenSimilarity(wanted, actual) * 65).roundToInt()
        }
        val typeScore = when {
            query.typeHint == null -> 0
            query.typeHint == candidate.type -> 10
            else -> -25
        }
        val yearScore = when {
            query.year == null || candidate.year == null -> 0
            query.year == candidate.year -> 15
            kotlin.math.abs(query.year - candidate.year) <= 1 -> 5
            else -> -30
        }
        return (titleScore + typeScore + yearScore).coerceIn(0, 100)
    }

    internal fun normalize(value: String): String = Normalizer
        .normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .replace("&", " and ")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun tokenSimilarity(left: String, right: String): Double {
        val a = left.split(' ').filter(String::isNotBlank).toSet()
        val b = right.split(' ').filter(String::isNotBlank).toSet()
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return a.intersect(b).size.toDouble() / a.union(b).size
    }
}
