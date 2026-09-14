// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge.core

/**
 * What OpenTVBridge knows about one TV launcher.
 *
 * View ids are stored as the suffix after `:id/` so a profile does not depend
 * on the launcher's package name appearing in the id string.
 */
data class LauncherProfile(
    val packageName: String,
    /** Detail-page title ids, most specific first. */
    val detailTitleViewIds: List<String> = emptyList(),
    /** Card ids whose content description carries the title. */
    val cardDescriptionViewIds: List<String> = emptyList(),
    /** Whether a window-state change should trigger a detail-page sweep. */
    val inspectsDetailPages: Boolean = true,
    /** Support is unverified on real hardware. */
    val experimental: Boolean = false,
)

object LauncherProfiles {
    val GOOGLE_TV = LauncherProfile(
        packageName = "com.google.android.apps.tv.launcherx",
        detailTitleViewIds = listOf(
            "entity_details_title_row",
            "entity_details_title",
            "movie_title",
            "detail_title",
            "title",
        ),
    )

    val ANDROID_TV = LauncherProfile(
        packageName = "com.google.android.tvlauncher",
        detailTitleViewIds = listOf("detail_title", "movie_title", "title"),
    )

    val FIRE_TV = LauncherProfile(
        packageName = "com.amazon.tv.launcher",
        detailTitleViewIds = listOf("detail_title", "title"),
        cardDescriptionViewIds = listOf("main_image"),
        experimental = true,
    )

    val FIRE_TV_LEANBACK = LauncherProfile(
        packageName = "com.amazon.tv.leanbacklauncher",
        cardDescriptionViewIds = listOf("main_image"),
        inspectsDetailPages = false,
        experimental = true,
    )

    val XIAOMI = LauncherProfile(
        packageName = "com.mitv.tvhome",
        detailTitleViewIds = listOf("detail_title", "title"),
        experimental = true,
    )

    val all: List<LauncherProfile> = listOf(
        GOOGLE_TV,
        ANDROID_TV,
        FIRE_TV,
        FIRE_TV_LEANBACK,
        XIAOMI,
    )

    val packageNames: List<String> = all.map(LauncherProfile::packageName)

    fun forPackage(packageName: String?): LauncherProfile? =
        all.firstOrNull { it.packageName == packageName }

    fun isSupported(packageName: String?): Boolean = forPackage(packageName) != null
}
