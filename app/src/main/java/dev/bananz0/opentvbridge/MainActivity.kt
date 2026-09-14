// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import dev.bananz0.opentvbridge.bridge.BridgeFactory
import dev.bananz0.opentvbridge.core.LaunchRequestFactory
import dev.bananz0.opentvbridge.core.ParsedTitle
import dev.bananz0.opentvbridge.core.ResolveResult
import dev.bananz0.opentvbridge.core.TargetApp
import dev.bananz0.opentvbridge.launch.AndroidTargetLauncher
import dev.bananz0.opentvbridge.launch.InstalledPackages
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var settings: SettingsRepository

    private val targetButtonIds = mapOf(
        TargetApp.NUVIO to R.id.target_nuvio,
        TargetApp.STREMIO to R.id.target_stremio,
        TargetApp.WUPLAY to R.id.target_wuplay,
        TargetApp.CLOUDSTREAM to R.id.target_cloudstream,
        TargetApp.PLEX to R.id.target_plex,
        TargetApp.JELLYFIN to R.id.target_jellyfin,
        TargetApp.FLADDER to R.id.target_fladder,
        TargetApp.WHOLPHIN to R.id.target_wholphin,
        TargetApp.EMBY to R.id.target_emby,
        TargetApp.KODI to R.id.target_kodi,
    )

    private val targetLabels = mapOf(
        TargetApp.NUVIO to R.string.target_nuvio,
        TargetApp.STREMIO to R.string.target_stremio,
        TargetApp.WUPLAY to R.string.target_wuplay,
        TargetApp.CLOUDSTREAM to R.string.target_cloudstream,
        TargetApp.PLEX to R.string.target_plex,
        TargetApp.JELLYFIN to R.string.target_jellyfin,
        TargetApp.FLADDER to R.string.target_fladder,
        TargetApp.WHOLPHIN to R.string.target_wholphin,
        TargetApp.EMBY to R.string.target_emby,
        TargetApp.KODI to R.string.target_kodi,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = SettingsRepository(this)

        configureTargets()
        configureToggles()
        configureOverrides()

        findViewById<Button>(R.id.open_accessibility).setOnClickListener { openAccessibility() }
        findViewById<Button>(R.id.open_integrations).setOnClickListener { open(IntegrationsActivity::class.java) }
        findViewById<Button>(R.id.open_diagnostics).setOnClickListener { open(DiagnosticsActivity::class.java) }
        findViewById<Button>(R.id.open_about).setOnClickListener { open(AboutActivity::class.java) }
        findViewById<Button>(R.id.run_test).setOnClickListener { runResolverTest(openTarget = false) }
        findViewById<Button>(R.id.open_test).setOnClickListener { runResolverTest(openTarget = true) }
        findViewById<Button>(R.id.open_smarttube_test).setOnClickListener { runSmartTubeTest() }
    }

    /** Marks destinations that are not installed, rather than hiding them. */
    private fun configureTargets() {
        val installed = InstalledPackages(this)
        targetButtonIds.forEach { (target, id) ->
            val label = getString(targetLabels.getValue(target))
            findViewById<TextView>(id).text = if (target.packageNames.any(installed::isInstalled)) {
                label
            } else {
                getString(R.string.target_not_installed, label)
            }
        }

        findViewById<RadioGroup>(R.id.target_group).apply {
            check(targetButtonIds.getValue(settings.primaryTarget))
            setOnCheckedChangeListener { _, checkedId ->
                targetButtonIds.entries
                    .firstOrNull { it.value == checkedId }
                    ?.key
                    ?.let { settings.primaryTarget = it }
            }
        }
    }

    private fun configureToggles() {
        findViewById<CheckBox>(R.id.smarttube_enabled).apply {
            isChecked = settings.smartTubeEnabled
            setOnCheckedChangeListener { _, checked -> settings.smartTubeEnabled = checked }
        }
        findViewById<CheckBox>(R.id.skip_uninstalled).apply {
            isChecked = settings.skipUninstalled
            setOnCheckedChangeListener { _, checked -> settings.skipUninstalled = checked }
        }
        findViewById<CheckBox>(R.id.auto_rank).apply {
            isChecked = settings.autoRank
            setOnCheckedChangeListener { _, checked -> settings.autoRank = checked }
        }
    }

    private fun configureOverrides() {
        bindOverride(R.id.movie_override, settings.movieOverride) { settings.movieOverride = it }
        bindOverride(R.id.series_override, settings.seriesOverride) { settings.seriesOverride = it }
    }

    /** Index 0 is "use the order above"; the rest map to [TargetApp.entries]. */
    private fun bindOverride(spinnerId: Int, current: TargetApp?, store: (TargetApp?) -> Unit) {
        val choices = listOf(getString(R.string.override_none)) +
            TargetApp.entries.map { getString(targetLabels.getValue(it)) }
        findViewById<Spinner>(spinnerId).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                choices,
            )
            setSelection(current?.let { TargetApp.entries.indexOf(it) + 1 } ?: 0)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) = store(TargetApp.entries.getOrNull(position - 1))

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
    }

    private fun open(activity: Class<out Activity>) = startActivity(Intent(this, activity))

    private fun openAccessibility() {
        runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .onFailure {
                Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_LONG).show()
            }
    }

    private fun runResolverTest(openTarget: Boolean) {
        val status = findViewById<TextView>(R.id.test_status)
        status.setText(R.string.test_running)
        executor.execute {
            val http = BridgeFactory.httpClient()
            val result = BridgeFactory.resolver(settings, http).resolve(ParsedTitle("Iron Man", 2008))
            val target = settings.primaryTarget
            // Enrichment can make network calls, so it stays off the main thread.
            val context = if (openTarget && result is ResolveResult.Found) {
                BridgeFactory.preparer(settings, http).prepare(target, result.match)
            } else {
                null
            }

            runOnUiThread {
                status.text = when (result) {
                    is ResolveResult.Found -> {
                        if (openTarget && context != null) {
                            AndroidTargetLauncher(this).open(
                                LaunchRequestFactory.forMedia(target, result.match, context),
                            )
                        }
                        getString(
                            R.string.test_success,
                            result.match.title,
                            result.match.year?.toString() ?: "?",
                            result.match.imdbId,
                        )
                    }

                    ResolveResult.NotFound -> getString(R.string.test_not_found)
                    is ResolveResult.NetworkError -> getString(R.string.test_network_error)
                }
            }
        }
    }

    private fun runSmartTubeTest() {
        val launcher = AndroidTargetLauncher(this)
        val opened = launcher.open(LaunchRequestFactory.forSmartTube("OpenTVBridge test")) ||
            launcher.open(LaunchRequestFactory.forSmartTube("OpenTVBridge test", beta = true))
        if (!opened) {
            Toast.makeText(this, R.string.smarttube_test_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
