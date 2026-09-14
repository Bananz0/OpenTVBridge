// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge.launch

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dev.bananz0.opentvbridge.core.InstalledTargets

/**
 * Package visibility is declared in `<queries>`, so this only ever sees the
 * destination apps OpenTVBridge is built to open — never a general inventory
 * of what the user has installed.
 *
 * Results are cached for the life of the instance; the service creates a new
 * one per resolution pass, so an app installed mid-session is picked up on the
 * next launcher selection rather than requiring a restart.
 */
class InstalledPackages(context: Context) : InstalledTargets {
    private val packageManager: PackageManager = context.packageManager
    private val cache = mutableMapOf<String, Boolean>()

    override fun isInstalled(packageName: String): Boolean = cache.getOrPut(packageName) {
        runCatching {
            launcherIntent(packageManager, packageName) != null
        }.getOrDefault(false)
    }
}

/**
 * Some Android TV destinations declare only the Leanback launcher category
 * (e.g. WuPlay), so `getLaunchIntentForPackage` alone would report them as
 * missing.
 */
internal fun launcherIntent(packageManager: PackageManager, packageName: String): Intent? {
    packageManager.getLaunchIntentForPackage(packageName)?.let { return it }
    val activity = packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            .setPackage(packageName),
        0,
    ).firstOrNull()?.activityInfo ?: return null
    return Intent(Intent.ACTION_MAIN).setComponent(
        ComponentName(activity.packageName, activity.name),
    )
}
