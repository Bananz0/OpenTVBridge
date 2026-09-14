// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge.core

/** Where a launcher selection got to. */
enum class DiagnosticStage {
    /** A title was read out of the launcher. */
    DETECTED,

    /** Metadata identified the title confidently. */
    RESOLVED,

    /** A destination was opened. */
    LAUNCHED,

    /** Deliberately dropped: duplicate, low confidence, or unrecognised card. */
    IGNORED,

    /** Something went wrong: no network, no installed target, refused intent. */
    FAILED,
}

data class DiagnosticEvent(
    val timestampMs: Long,
    val stage: DiagnosticStage,
    val launcherPackage: String? = null,
    val rawText: String? = null,
    val parsedTitle: String? = null,
    val parsedYear: Int? = null,
    val matchTitle: String? = null,
    val imdbId: String? = null,
    val score: Int? = null,
    val target: TargetApp? = null,
    val detail: String? = null,
)

/**
 * A bounded, in-memory record of recent activity.
 *
 * Launcher accessibility trees are vendor-controlled and change without
 * notice, so the most common failure is silent: nothing happens and the user
 * cannot say why. This log makes that diagnosable, and [render] produces text
 * a user can paste into an issue. Nothing is written to disk and the log dies
 * with the process.
 */
class DiagnosticsLog(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val events = ArrayDeque<DiagnosticEvent>()

    @Synchronized
    fun record(event: DiagnosticEvent) {
        events.addLast(event)
        while (events.size > capacity) events.removeFirst()
    }

    fun record(
        stage: DiagnosticStage,
        launcherPackage: String? = null,
        rawText: String? = null,
        parsedTitle: String? = null,
        parsedYear: Int? = null,
        matchTitle: String? = null,
        imdbId: String? = null,
        score: Int? = null,
        target: TargetApp? = null,
        detail: String? = null,
    ) = record(
        DiagnosticEvent(
            timestampMs = clock(),
            stage = stage,
            launcherPackage = launcherPackage,
            rawText = rawText,
            parsedTitle = parsedTitle,
            parsedYear = parsedYear,
            matchTitle = matchTitle,
            imdbId = imdbId,
            score = score,
            target = target,
            detail = detail,
        ),
    )

    /** Newest first. */
    @Synchronized
    fun snapshot(): List<DiagnosticEvent> = events.toList().asReversed()

    @Synchronized
    fun clear() = events.clear()

    /**
     * Plain text for a bug report. [formatTime] keeps this class free of
     * Android and JVM date formatting differences.
     */
    fun render(formatTime: (Long) -> String = { it.toString() }): String {
        val lines = snapshot().map { event ->
            buildString {
                append(formatTime(event.timestampMs))
                append("  ")
                append(event.stage.name.padEnd(8))
                event.launcherPackage?.let { append(" launcher=").append(it) }
                event.rawText?.let { append(" raw=\"").append(it).append('"') }
                event.parsedTitle?.let { append(" parsed=\"").append(it).append('"') }
                event.parsedYear?.let { append(" year=").append(it) }
                event.matchTitle?.let { append(" match=\"").append(it).append('"') }
                event.imdbId?.let { append(" imdb=").append(it) }
                event.score?.let { append(" score=").append(it) }
                event.target?.let { append(" target=").append(it.name) }
                event.detail?.let { append(" detail=").append(it) }
            }
        }
        return if (lines.isEmpty()) "" else lines.joinToString("\n")
    }

    private companion object {
        const val DEFAULT_CAPACITY = 60
    }
}

/**
 * Process-wide log shared by the accessibility service and the UI. The service
 * and activity run in the same process, so a plain singleton is sufficient.
 */
object Diagnostics {
    val log = DiagnosticsLog()
}
