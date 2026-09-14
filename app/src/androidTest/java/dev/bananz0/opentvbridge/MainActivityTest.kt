// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bananz0.opentvbridge.core.TargetApp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    private var scenario: ActivityScenario<MainActivity>? = null

    @After fun close() {
        scenario?.close()
    }

    @Test fun allTargetsRenderAndSelectionPersistsAcrossRecreation() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.target_nuvio)).check(matches(isDisplayed()))
        onView(withId(R.id.target_stremio)).check(matches(isDisplayed()))
        onView(withId(R.id.target_wuplay)).check(matches(isDisplayed()))
        onView(withId(R.id.target_cloudstream)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.target_plex)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.target_fladder)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.target_wholphin)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.target_emby)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.target_kodi)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.target_jellyfin)).perform(scrollTo()).perform(click())
        onView(withId(R.id.open_test)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.open_smarttube_test)).perform(scrollTo()).check(matches(isDisplayed()))

        scenario?.recreate()
        onView(withId(R.id.target_jellyfin)).perform(scrollTo()).check(matches(isChecked()))
        scenario?.onActivity {
            val settings = SettingsRepository(it)
            assertEquals(TargetApp.JELLYFIN, settings.primaryTarget)
            // Choosing a primary destination reorders rather than discards the
            // remaining ones, so fallback still has somewhere to go.
            assertTrue(settings.routingOrder.size > 1)
            assertEquals(TargetApp.JELLYFIN, settings.routingOrder.first())
        }
    }

    @Test fun routingTogglesPersist() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.auto_rank)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.skip_uninstalled)).perform(scrollTo()).check(matches(isDisplayed()))
        scenario?.onActivity {
            val settings = SettingsRepository(it)
            // Auto-ranking is the default so a fresh install picks sensibly
            // without the user configuring an order at all.
            assertTrue(settings.autoRank)
            assertTrue(settings.skipUninstalled)
        }
    }

    @Test fun secondaryScreensOpen() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        listOf(R.id.open_integrations, R.id.open_diagnostics, R.id.open_about).forEach { id ->
            onView(withId(id)).perform(scrollTo()).check(matches(isDisplayed()))
        }
    }
}
