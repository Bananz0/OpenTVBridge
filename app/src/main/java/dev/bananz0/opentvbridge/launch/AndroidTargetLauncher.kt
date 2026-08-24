package dev.bananz0.opentvbridge.launch

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import androidx.core.net.toUri
import dev.bananz0.opentvbridge.core.LaunchRequest

class AndroidTargetLauncher(private val service: Service) {
    fun open(request: LaunchRequest): Boolean = when (request) {
        is LaunchRequest.Search -> openSearch(request)
        is LaunchRequest.View -> openView(request)
    }

    private fun openSearch(request: LaunchRequest.Search): Boolean {
        val intent = Intent(Intent.ACTION_SEARCH)
            .putExtra("query", request.query)
            .setPackage(request.packageName)
            .addFlags(FLAGS)
        request.componentClass?.let {
            intent.component = ComponentName(request.packageName, it)
        }
        return start(intent)
    }

    private fun openView(request: LaunchRequest.View): Boolean {
        val targeted = Intent(Intent.ACTION_VIEW, request.uri.toUri())
            .setPackage(request.packageName)
            .addFlags(FLAGS)
        if (start(targeted)) return true
        if (!request.allowGenericFallback) return false
        return start(Intent(Intent.ACTION_VIEW, request.uri.toUri()).addFlags(FLAGS))
    }

    private fun start(intent: Intent): Boolean = runCatching {
        service.startActivity(intent)
        true
    }.getOrDefault(false)

    private companion object {
        const val FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
}
