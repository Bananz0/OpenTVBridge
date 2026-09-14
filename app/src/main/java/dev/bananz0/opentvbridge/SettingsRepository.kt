// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge

import android.content.Context
import androidx.core.content.edit
import dev.bananz0.opentvbridge.core.Requirement
import dev.bananz0.opentvbridge.core.RoutingPreferences
import dev.bananz0.opentvbridge.core.TargetApp
import dev.bananz0.opentvbridge.network.KodiConfig
import dev.bananz0.opentvbridge.network.MediaServerConfig
import dev.bananz0.opentvbridge.network.TraktConfig

/**
 * All user configuration, stored in app-private preferences.
 *
 * Backups are disabled in the manifest, so credentials entered here stay on
 * the device they were typed into and are never included in a device-transfer
 * or cloud backup. They are stored in plain preferences; see SECURITY.md for
 * why that is the honest trade-off rather than a false promise of encryption.
 */
class SettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("open_tv_bridge", Context.MODE_PRIVATE)

    // ---- Routing ----------------------------------------------------------

    var routingOrder: List<TargetApp>
        get() {
            val stored = preferences.getString(KEY_ORDER, null)
                ?.split(',')
                ?.mapNotNull { TargetApp.fromName(it.trim()) }
                ?.distinct()
                .orEmpty()
            if (stored.isNotEmpty()) return stored

            // Migration: v0.1.0 stored a single destination.
            val legacy = TargetApp.fromName(preferences.getString(KEY_LEGACY_TARGET, null))
            return if (legacy != null) {
                listOf(legacy) + RoutingPreferences.DEFAULT_ORDER.filterNot { it == legacy }
            } else {
                RoutingPreferences.DEFAULT_ORDER
            }
        }
        set(value) {
            preferences.edit {
                putString(KEY_ORDER, value.distinct().joinToString(",", transform = TargetApp::name))
            }
        }

    var movieOverride: TargetApp?
        get() = TargetApp.fromName(preferences.getString(KEY_MOVIE_OVERRIDE, null))
        set(value) = preferences.edit { putString(KEY_MOVIE_OVERRIDE, value?.name) }

    var seriesOverride: TargetApp?
        get() = TargetApp.fromName(preferences.getString(KEY_SERIES_OVERRIDE, null))
        set(value) = preferences.edit { putString(KEY_SERIES_OVERRIDE, value?.name) }

    var skipUninstalled: Boolean
        get() = preferences.getBoolean(KEY_SKIP_UNINSTALLED, true)
        set(value) = preferences.edit { putBoolean(KEY_SKIP_UNINSTALLED, value) }

    var autoRank: Boolean
        get() = preferences.getBoolean(KEY_AUTO_RANK, true)
        set(value) = preferences.edit { putBoolean(KEY_AUTO_RANK, value) }

    val routingPreferences: RoutingPreferences
        get() = RoutingPreferences(
            order = routingOrder,
            movieOverride = movieOverride,
            seriesOverride = seriesOverride,
            skipUninstalled = skipUninstalled,
            autoRank = autoRank,
        )

    /**
     * Which optional integrations are configured. Auto-ranking uses this to
     * tell a destination that can open the exact item from one that can only
     * search for it.
     */
    val availableRequirements: Set<Requirement>
        get() = buildSet {
            if (jellyfinServer.isUsable) add(Requirement.JELLYFIN_SERVER)
            if (embyServer.isUsable) add(Requirement.EMBY_SERVER)
            if (plexToken.isNotBlank()) add(Requirement.PLEX_TOKEN)
            if (kodi.isUsable) add(Requirement.KODI_INSTANCE)
            if (tmdbApiKey.isNotBlank()) add(Requirement.TMDB_KEY)
        }

    /** Convenience for the single-choice UI and the v0.1.0 behaviour. */
    var primaryTarget: TargetApp
        get() = routingOrder.firstOrNull() ?: TargetApp.NUVIO
        set(value) {
            routingOrder = listOf(value) + routingOrder.filterNot { it == value }
        }

    var smartTubeEnabled: Boolean
        get() = preferences.getBoolean(KEY_SMART_TUBE, true)
        set(value) = preferences.edit { putBoolean(KEY_SMART_TUBE, value) }

    // ---- Optional integrations -------------------------------------------

    var tmdbApiKey: String
        get() = preferences.getString(KEY_TMDB_KEY, "").orEmpty()
        set(value) = preferences.edit { putString(KEY_TMDB_KEY, value.trim()) }

    var plexToken: String
        get() = preferences.getString(KEY_PLEX_TOKEN, "").orEmpty()
        set(value) = preferences.edit { putString(KEY_PLEX_TOKEN, value.trim()) }

    var jellyfinServer: MediaServerConfig
        get() = MediaServerConfig(
            baseUrl = preferences.getString(KEY_JELLYFIN_URL, "").orEmpty(),
            apiKey = preferences.getString(KEY_JELLYFIN_KEY, "").orEmpty(),
        )
        set(value) = preferences.edit {
            putString(KEY_JELLYFIN_URL, value.baseUrl.trim())
            putString(KEY_JELLYFIN_KEY, value.apiKey.trim())
        }

    var embyServer: MediaServerConfig
        get() = MediaServerConfig(
            baseUrl = preferences.getString(KEY_EMBY_URL, "").orEmpty(),
            apiKey = preferences.getString(KEY_EMBY_KEY, "").orEmpty(),
        )
        set(value) = preferences.edit {
            putString(KEY_EMBY_URL, value.baseUrl.trim())
            putString(KEY_EMBY_KEY, value.apiKey.trim())
        }

    var kodi: KodiConfig
        get() = KodiConfig(
            baseUrl = preferences.getString(KEY_KODI_URL, "").orEmpty(),
            username = preferences.getString(KEY_KODI_USER, "").orEmpty(),
            password = preferences.getString(KEY_KODI_PASSWORD, "").orEmpty(),
        )
        set(value) = preferences.edit {
            putString(KEY_KODI_URL, value.baseUrl.trim())
            putString(KEY_KODI_USER, value.username.trim())
            putString(KEY_KODI_PASSWORD, value.password)
        }

    var trakt: TraktConfig
        get() = TraktConfig(
            clientId = preferences.getString(KEY_TRAKT_CLIENT, "").orEmpty(),
            accessToken = preferences.getString(KEY_TRAKT_TOKEN, "").orEmpty(),
        )
        set(value) = preferences.edit {
            putString(KEY_TRAKT_CLIENT, value.clientId.trim())
            putString(KEY_TRAKT_TOKEN, value.accessToken.trim())
        }

    var traktWatchlistEnabled: Boolean
        get() = preferences.getBoolean(KEY_TRAKT_WATCHLIST, false)
        set(value) = preferences.edit { putBoolean(KEY_TRAKT_WATCHLIST, value) }

    /** True when any optional credential has been supplied. */
    val hasOptionalIntegrations: Boolean
        get() = tmdbApiKey.isNotBlank() ||
            plexToken.isNotBlank() ||
            jellyfinServer.isUsable ||
            embyServer.isUsable ||
            kodi.isUsable ||
            trakt.isUsable

    /** Removes every stored credential, leaving routing preferences intact. */
    fun clearCredentials() = preferences.edit {
        listOf(
            KEY_TMDB_KEY, KEY_PLEX_TOKEN,
            KEY_JELLYFIN_URL, KEY_JELLYFIN_KEY,
            KEY_EMBY_URL, KEY_EMBY_KEY,
            KEY_KODI_URL, KEY_KODI_USER, KEY_KODI_PASSWORD,
            KEY_TRAKT_CLIENT, KEY_TRAKT_TOKEN,
        ).forEach(::remove)
        putBoolean(KEY_TRAKT_WATCHLIST, false)
    }

    private companion object {
        const val KEY_LEGACY_TARGET = "target_app"
        const val KEY_ORDER = "routing_order"
        const val KEY_MOVIE_OVERRIDE = "movie_override"
        const val KEY_SERIES_OVERRIDE = "series_override"
        const val KEY_SKIP_UNINSTALLED = "skip_uninstalled"
        const val KEY_AUTO_RANK = "auto_rank"
        const val KEY_SMART_TUBE = "smarttube_enabled"
        const val KEY_TMDB_KEY = "tmdb_api_key"
        const val KEY_PLEX_TOKEN = "plex_token"
        const val KEY_JELLYFIN_URL = "jellyfin_url"
        const val KEY_JELLYFIN_KEY = "jellyfin_key"
        const val KEY_EMBY_URL = "emby_url"
        const val KEY_EMBY_KEY = "emby_key"
        const val KEY_KODI_URL = "kodi_url"
        const val KEY_KODI_USER = "kodi_user"
        const val KEY_KODI_PASSWORD = "kodi_password"
        const val KEY_TRAKT_CLIENT = "trakt_client_id"
        const val KEY_TRAKT_TOKEN = "trakt_access_token"
        const val KEY_TRAKT_WATCHLIST = "trakt_watchlist_enabled"
    }
}
