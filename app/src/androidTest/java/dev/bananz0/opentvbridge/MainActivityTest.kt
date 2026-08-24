package dev.bananz0.opentvbridge

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bananz0.opentvbridge.core.TargetApp
import org.junit.After
import org.junit.Assert.assertEquals
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
        onView(withId(R.id.target_plex)).check(matches(isDisplayed()))
        onView(withId(R.id.target_jellyfin)).check(matches(isDisplayed())).perform(click())

        scenario?.recreate()
        onView(withId(R.id.target_jellyfin)).check(matches(isChecked()))
        scenario?.onActivity {
            assertEquals(TargetApp.JELLYFIN, SettingsRepository(it).targetApp)
        }
    }
}
