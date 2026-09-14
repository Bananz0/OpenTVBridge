// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import dev.bananz0.opentvbridge.network.KodiConfig
import dev.bananz0.opentvbridge.network.MediaServerConfig
import dev.bananz0.opentvbridge.network.TraktConfig

/**
 * Credentials the user supplies for their own services. Nothing here is
 * required; leaving it empty keeps OpenTVBridge on the keyless path.
 */
class IntegrationsActivity : Activity() {
    private lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_integrations)
        settings = SettingsRepository(this)

        load()
        findViewById<Button>(R.id.save).setOnClickListener { save() }
        findViewById<Button>(R.id.clear).setOnClickListener { clear() }
    }

    private fun load() {
        field(R.id.tmdb_key).setText(settings.tmdbApiKey)
        field(R.id.plex_token).setText(settings.plexToken)

        settings.jellyfinServer.let {
            field(R.id.jellyfin_url).setText(it.baseUrl)
            field(R.id.jellyfin_key).setText(it.apiKey)
        }
        settings.embyServer.let {
            field(R.id.emby_url).setText(it.baseUrl)
            field(R.id.emby_key).setText(it.apiKey)
        }
        settings.kodi.let {
            field(R.id.kodi_url).setText(it.baseUrl)
            field(R.id.kodi_user).setText(it.username)
            field(R.id.kodi_password).setText(it.password)
        }
        settings.trakt.let {
            field(R.id.trakt_client_id).setText(it.clientId)
            field(R.id.trakt_token).setText(it.accessToken)
        }
        findViewById<CheckBox>(R.id.trakt_enabled).isChecked = settings.traktWatchlistEnabled
    }

    private fun save() {
        settings.tmdbApiKey = text(R.id.tmdb_key)
        settings.plexToken = text(R.id.plex_token)
        settings.jellyfinServer = MediaServerConfig(
            baseUrl = text(R.id.jellyfin_url),
            apiKey = text(R.id.jellyfin_key),
        )
        settings.embyServer = MediaServerConfig(
            baseUrl = text(R.id.emby_url),
            apiKey = text(R.id.emby_key),
        )
        settings.kodi = KodiConfig(
            baseUrl = text(R.id.kodi_url),
            username = text(R.id.kodi_user),
            password = text(R.id.kodi_password),
        )
        settings.trakt = TraktConfig(
            clientId = text(R.id.trakt_client_id),
            accessToken = text(R.id.trakt_token),
        )
        settings.traktWatchlistEnabled = findViewById<CheckBox>(R.id.trakt_enabled).isChecked

        Toast.makeText(this, R.string.integrations_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun clear() {
        settings.clearCredentials()
        load()
        Toast.makeText(this, R.string.integrations_cleared, Toast.LENGTH_LONG).show()
    }

    private fun field(id: Int): EditText = findViewById(id)

    private fun text(id: Int): String = field(id).text?.toString().orEmpty().trim()
}
