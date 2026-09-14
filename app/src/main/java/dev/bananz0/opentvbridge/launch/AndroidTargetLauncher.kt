// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.bananz0.opentvbridge.launch

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dev.bananz0.opentvbridge.core.LaunchRequest

class AndroidTargetLauncher(private val context: Context) {
    fun open(request: LaunchRequest): Boolean = when (request) {
        is LaunchRequest.Search -> openSearch(request)
        is LaunchRequest.View -> openView(request)
        is LaunchRequest.Item -> openItem(request)
        is LaunchRequest.Launch -> openApp(request)
    }

    private fun openSearch(request: LaunchRequest.Search): Boolean {
        val intent = Intent(Intent.ACTION_SEARCH)
            .putExtra("query", request.query)
            .setPackage(request.packageName)
            .addFlags(ADDRESSED_FLAGS)
        request.componentClass?.let {
            intent.component = ComponentName(request.packageName, it)
        }
        return start(intent)
    }

    private fun openView(request: LaunchRequest.View): Boolean {
        for (packageName in request.packageNamesInPriorityOrder) {
            val targeted = Intent(Intent.ACTION_VIEW, request.uri.toUri())
                .setPackage(packageName)
                .addFlags(ADDRESSED_FLAGS)
            if (start(targeted)) return true
        }
        if (!request.allowGenericFallback) return false
        return start(Intent(Intent.ACTION_VIEW, request.uri.toUri()).addFlags(ADDRESSED_FLAGS))
    }

    private fun openItem(request: LaunchRequest.Item): Boolean {
        val intent = Intent(Intent.ACTION_VIEW)
            .setPackage(request.packageName)
            .addFlags(ADDRESSED_FLAGS)
        request.extras.forEach { (key, value) -> intent.putExtra(key, value) }
        request.componentClass?.let {
            intent.component = ComponentName(request.packageName, it)
        }
        if (start(intent)) return true
        // An explicit component can be renamed between releases; retrying
        // without it lets the package's own manifest resolve the intent.
        if (request.componentClass == null) return false
        val withoutComponent = Intent(Intent.ACTION_VIEW)
            .setPackage(request.packageName)
            .addFlags(ADDRESSED_FLAGS)
        request.extras.forEach { (key, value) -> withoutComponent.putExtra(key, value) }
        return start(withoutComponent)
    }

    private fun openApp(request: LaunchRequest.Launch): Boolean =
        request.packageNamesInPriorityOrder.any { packageName ->
            val intent = launcherIntent(context.packageManager, packageName)
                ?: return@any false
            start(intent.addFlags(RESUME_FLAGS))
        }

    private fun start(intent: Intent): Boolean = runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    private companion object {
        /**
         * For requests that name a specific item or search. Clearing the task
         * stops the target from resuming whatever screen it was last on and
         * ignoring the item we asked for.
         */
        const val ADDRESSED_FLAGS =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        /**
         * For merely bringing an app forward. Clearing the task here would
         * reset it to its root activity and discard state — which for Kodi
         * would tear down the playback we just started over JSON-RPC.
         */
        const val RESUME_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK
    }
}
