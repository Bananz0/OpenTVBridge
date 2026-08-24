package dev.bananz0.opentvbridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessibilityTreeTitleFinderTest {
    @Test fun `preferred Google title id wins over generic title`() {
        val tree = NodeSnapshot(children = listOf(
            NodeSnapshot(viewId = "launcher:id/title", text = "Wrong generic heading"),
            NodeSnapshot(children = listOf(
                NodeSnapshot(
                    viewId = "com.google.android.apps.tv.launcherx:id/entity_details_title_row",
                    text = "Monsters, Inc.",
                ),
            )),
        ))
        assertEquals("Monsters, Inc.", AccessibilityTreeTitleFinder.findGoogleTitle(tree)?.title)
    }

    @Test fun `blank preferred node falls through to next candidate`() {
        val tree = NodeSnapshot(children = listOf(
            NodeSnapshot(viewId = "launcher:id/entity_details_title_row", text = " "),
            NodeSnapshot(viewId = "launcher:id/movie_title", text = "Arrival (2016)"),
        ))
        assertEquals(ParsedTitle("Arrival", 2016), AccessibilityTreeTitleFinder.findGoogleTitle(tree))
    }

    @Test fun `Fire TV only trusts main image content description`() {
        val tree = NodeSnapshot(children = listOf(
            NodeSnapshot(viewId = "com.amazon.tv.launcher:id/label", contentDescription = "Wrong"),
            NodeSnapshot(viewId = "com.amazon.tv.launcher:id/main_image", contentDescription = "The Bear"),
        ))
        assertEquals("The Bear", AccessibilityTreeTitleFinder.findFireTvTitle(tree)?.title)
    }

    @Test fun `missing title returns null`() {
        assertNull(AccessibilityTreeTitleFinder.findGoogleTitle(NodeSnapshot(text = "Play")))
        assertNull(AccessibilityTreeTitleFinder.findFireTvTitle(NodeSnapshot()))
    }
}
