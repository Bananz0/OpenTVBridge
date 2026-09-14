// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge.core

/** Whether a destination package is present on this device. */
fun interface InstalledTargets {
    fun isInstalled(packageName: String): Boolean

    companion object {
        /** Assumes everything is present; used by tests and as a safe fallback. */
        val ALL = InstalledTargets { true }
    }
}

/**
 * User routing configuration: an ordered preference list plus optional
 * per-type overrides, so films and series can go to different apps.
 */
data class RoutingPreferences(
    val order: List<TargetApp> = DEFAULT_ORDER,
    val movieOverride: TargetApp? = null,
    val seriesOverride: TargetApp? = null,
    val skipUninstalled: Boolean = true,
    /**
     * Consider every installed destination, best-capability first, rather than
     * only the ones the user listed. The user's order still breaks ties.
     */
    val autoRank: Boolean = true,
) {
    companion object {
        /**
         * Every destination participates in fallback by default. Order is the
         * tie-break when two can do equally well, not a capability judgement.
         */
        val DEFAULT_ORDER = TargetApp.entries.toList()
    }
}

object RoutingPolicy {
    /**
     * Returns the destinations to try, best first.
     *
     * The per-type override always leads. After that, if
     * [RoutingPreferences.autoRank] is set, candidates are ordered by what they
     * can actually do for this match given [available] configuration — opening
     * the exact item beats a search, which beats dumping the user on a home
     * screen — with the user's own order breaking ties.
     *
     * A destination that can only open its app is included **only** if the user
     * named it. Auto-ranking will not silently drop someone on an app's home
     * screen when they clicked a specific title.
     *
     * When filtering by installed apps would leave nothing to try, the
     * unfiltered list is returned so the attempt is made and recorded rather
     * than the event vanishing.
     */
    fun candidates(
        preferences: RoutingPreferences,
        type: MediaType,
        installed: InstalledTargets = InstalledTargets.ALL,
        available: Set<Requirement> = emptySet(),
    ): List<TargetApp> {
        val override = when (type) {
            MediaType.MOVIE -> preferences.movieOverride
            MediaType.SERIES -> preferences.seriesOverride
        }
        val chosen = (listOfNotNull(override) + preferences.order).distinct()
        if (!preferences.autoRank) return applyInstalled(chosen, chosen, preferences, installed)

        // The user's list leads, then anything else installed can still serve
        // as a last resort.
        val pool = (chosen + TargetApp.entries).distinct().filter { target ->
            target in chosen || target.capabilityWith(available) != TargetCapability.APP_ONLY
        }
        val ranked = pool.sortedByDescending { it.capabilityWith(available) }
        // An explicit per-type override is an instruction, not a preference to
        // be optimised away, so it is re-pinned ahead of the ranking.
        val ordered = (listOfNotNull(override) + ranked).distinct()
        return applyInstalled(ordered, chosen, preferences, installed)
    }

    private fun applyInstalled(
        ordered: List<TargetApp>,
        fallback: List<TargetApp>,
        preferences: RoutingPreferences,
        installed: InstalledTargets,
    ): List<TargetApp> {
        if (!preferences.skipUninstalled) return ordered
        val available = ordered.filter { target -> target.packageNames.any(installed::isInstalled) }
        return available.ifEmpty { fallback }
    }

    /** The first installed package for [target], or its preferred package. */
    fun packageFor(target: TargetApp, installed: InstalledTargets = InstalledTargets.ALL): String =
        target.packageNames.firstOrNull(installed::isInstalled) ?: target.packageName
}
