package dev.bananz0.opentvbridge

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import dev.bananz0.opentvbridge.core.LaunchRequestFactory
import dev.bananz0.opentvbridge.core.ParsedTitle
import dev.bananz0.opentvbridge.core.ResolveResult
import dev.bananz0.opentvbridge.core.TargetApp
import dev.bananz0.opentvbridge.launch.AndroidTargetLauncher
import dev.bananz0.opentvbridge.network.CinemetaClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = SettingsRepository(this)
        configureTargets()
        configureSmartTube()
        findViewById<Button>(R.id.open_accessibility).setOnClickListener { openAccessibility() }
        findViewById<Button>(R.id.run_test).setOnClickListener { runResolverTest(openTarget = false) }
        findViewById<Button>(R.id.open_test).setOnClickListener { runResolverTest(openTarget = true) }
    }

    private fun configureTargets() {
        val ids = mapOf(
            TargetApp.NUVIO to R.id.target_nuvio,
            TargetApp.STREMIO to R.id.target_stremio,
            TargetApp.PLEX to R.id.target_plex,
            TargetApp.JELLYFIN to R.id.target_jellyfin,
        )
        findViewById<RadioGroup>(R.id.target_group).apply {
            check(ids.getValue(settings.targetApp))
            setOnCheckedChangeListener { _, checkedId ->
                ids.entries.firstOrNull { it.value == checkedId }?.key?.let { settings.targetApp = it }
            }
        }
    }

    private fun configureSmartTube() {
        findViewById<CheckBox>(R.id.smarttube_enabled).apply {
            isChecked = settings.smartTubeEnabled
            setOnCheckedChangeListener { _, checked -> settings.smartTubeEnabled = checked }
        }
    }

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
            val client = CinemetaClient(
                OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build(),
                BuildConfig.CINEMETA_BASE_URL.toHttpUrl(),
            )
            val result = client.resolve(ParsedTitle("Iron Man", 2008))
            runOnUiThread {
                status.text = when (result) {
                    is ResolveResult.Found -> {
                        if (openTarget) {
                            AndroidTargetLauncher(this).open(
                                LaunchRequestFactory.forMedia(settings.targetApp, result.match),
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

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
