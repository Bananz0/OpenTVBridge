// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge.core

/**
 * How well a destination can serve a resolved title.
 *
 * Ordered worst to best so they compare naturally: landing someone on an app's
 * home screen after they clicked a specific film is the weakest outcome, and
 * opening that exact item is the best.
 */
enum class TargetCapability {
    /** Only brings the app forward. The user must find the title themselves. */
    APP_ONLY,

    /** Hands over the title as a search term. */
    SEARCH,

    /** Opens the exact item. */
    EXACT_ITEM,
}

/**
 * Optional user configuration a destination needs before it can address an
 * item rather than merely search or open.
 */
enum class Requirement {
    JELLYFIN_SERVER,
    EMBY_SERVER,
    PLEX_TOKEN,
    KODI_INSTANCE,
    TMDB_KEY,
}

/**
 * A destination OpenTVBridge can hand a resolved title to.
 *
 * [contractVerified] records whether the launch contract was read from the
 * destination's own published source. Unverified targets still work, but they
 * degrade rather than pretending to address an item they cannot.
 *
 * [baseCapability] is what the destination manages with no optional
 * configuration at all; any one of [upgradeRequirements] lifts it to
 * [TargetCapability.EXACT_ITEM].
 */
enum class TargetApp(
    val displayName: String,
    val packageNames: List<String>,
    val contractVerified: Boolean,
    val baseCapability: TargetCapability,
    val upgradeRequirements: Set<Requirement> = emptySet(),
) {
    // Public catalogue ids are enough for these three; they need nothing else.
    NUVIO(
        "Nuvio",
        listOf("com.nuvio.tv", "com.nuvio.app"),
        contractVerified = true,
        baseCapability = TargetCapability.EXACT_ITEM,
    ),
    STREMIO(
        "Stremio",
        listOf("com.stremio.one"),
        contractVerified = true,
        baseCapability = TargetCapability.EXACT_ITEM,
    ),
    WUPLAY(
        "WuPlay",
        listOf("app.wuplay.androidtv"),
        contractVerified = false,
        baseCapability = TargetCapability.EXACT_ITEM,
    ),

    // CloudStream only accepts a search term; it has no id-addressable route.
    CLOUDSTREAM(
        "CloudStream",
        listOf("com.lagradost.cloudstream3", "com.lagradost.cloudstream3.prerelease"),
        contractVerified = true,
        baseCapability = TargetCapability.SEARCH,
    ),

    PLEX(
        "Plex",
        listOf("com.plexapp.android"),
        contractVerified = true,
        baseCapability = TargetCapability.SEARCH,
        upgradeRequirements = setOf(Requirement.PLEX_TOKEN),
    ),
    JELLYFIN(
        "Jellyfin",
        listOf("org.jellyfin.androidtv"),
        contractVerified = true,
        baseCapability = TargetCapability.SEARCH,
        upgradeRequirements = setOf(Requirement.JELLYFIN_SERVER),
    ),
    WHOLPHIN(
        "Wholphin",
        listOf("com.github.damontecres.wholphin"),
        contractVerified = true,
        baseCapability = TargetCapability.SEARCH,
        upgradeRequirements = setOf(Requirement.JELLYFIN_SERVER),
    ),

    // Fladder's details route needs a Jellyfin id; its Jellyseerr route needs
    // a TMDB id. Either one is enough to address an item.
    FLADDER(
        "Fladder",
        listOf("nl.jknaapen.fladder"),
        contractVerified = true,
        baseCapability = TargetCapability.APP_ONLY,
        upgradeRequirements = setOf(Requirement.JELLYFIN_SERVER, Requirement.TMDB_KEY),
    ),
    EMBY(
        "Emby",
        listOf("tv.emby.embyatv"),
        contractVerified = false,
        baseCapability = TargetCapability.APP_ONLY,
        upgradeRequirements = setOf(Requirement.EMBY_SERVER),
    ),
    KODI(
        "Kodi",
        listOf("org.xbmc.kodi"),
        contractVerified = false,
        baseCapability = TargetCapability.APP_ONLY,
        upgradeRequirements = setOf(Requirement.KODI_INSTANCE),
    ),
    ;

    /** Preferred package. Retained for single-package call sites. */
    val packageName: String get() = packageNames.first()

    /** What this destination can do given the configuration present. */
    fun capabilityWith(available: Set<Requirement>): TargetCapability =
        if (upgradeRequirements.any { it in available }) {
            TargetCapability.EXACT_ITEM
        } else {
            baseCapability
        }

    companion object {
        fun fromName(value: String?): TargetApp? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }

        /** Every package OpenTVBridge may hand an intent to, for `<queries>`. */
        val allPackageNames: List<String> =
            entries.flatMap(TargetApp::packageNames).distinct()
    }
}

/** SmartTube is a YouTube redirect, not a film/series destination. */
object SmartTube {
    const val STABLE = "org.smarttube.stable"
    const val BETA = "org.smarttube.beta"
    val packageNames = listOf(STABLE, BETA)
}
