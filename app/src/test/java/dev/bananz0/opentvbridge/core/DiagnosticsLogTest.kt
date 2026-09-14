// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsLogTest {
    @Test fun `the log is bounded and keeps the newest entries`() {
        val log = DiagnosticsLog(capacity = 3) { 0L }
        repeat(5) { log.record(DiagnosticStage.DETECTED, parsedTitle = "title $it") }

        val titles = log.snapshot().map { it.parsedTitle }
        // Newest first, oldest two dropped.
        assertEquals(listOf("title 4", "title 3", "title 2"), titles)
    }

    @Test fun `render produces one line per event with the fields present`() {
        val log = DiagnosticsLog { 1_000L }
        log.record(
            stage = DiagnosticStage.LAUNCHED,
            launcherPackage = "com.google.android.apps.tv.launcherx",
            parsedTitle = "Iron Man",
            imdbId = "tt0371746",
            score = 100,
            target = TargetApp.NUVIO,
        )
        val rendered = log.render { "12:00:00" }

        assertEquals(1, rendered.lines().size)
        assertTrue(rendered.startsWith("12:00:00"))
        assertTrue(rendered.contains("LAUNCHED"))
        assertTrue(rendered.contains("imdb=tt0371746"))
        assertTrue(rendered.contains("target=NUVIO"))
    }

    @Test fun `an empty log renders as empty rather than a placeholder line`() {
        assertEquals("", DiagnosticsLog().render())
    }

    @Test fun `clear empties the log`() {
        val log = DiagnosticsLog()
        log.record(DiagnosticStage.DETECTED, parsedTitle = "x")
        log.clear()
        assertTrue(log.snapshot().isEmpty())
    }
}
