package dev.bananz0.opentvbridge

import android.content.Context
import androidx.core.content.edit
import dev.bananz0.opentvbridge.core.TargetApp

class SettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("open_tv_bridge", Context.MODE_PRIVATE)

    var targetApp: TargetApp
        get() = preferences.getString(KEY_TARGET, null)
            ?.let { runCatching { TargetApp.valueOf(it) }.getOrNull() }
            ?: TargetApp.NUVIO
        set(value) {
            preferences.edit { putString(KEY_TARGET, value.name) }
        }

    var smartTubeEnabled: Boolean
        get() = preferences.getBoolean(KEY_SMART_TUBE, true)
        set(value) {
            preferences.edit { putBoolean(KEY_SMART_TUBE, value) }
        }

    private companion object {
        const val KEY_TARGET = "target_app"
        const val KEY_SMART_TUBE = "smarttube_enabled"
    }
}
