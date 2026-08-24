package dev.bananz0.opentvbridge.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.bananz0.opentvbridge.BuildConfig
import dev.bananz0.opentvbridge.SettingsRepository
import dev.bananz0.opentvbridge.core.AccessibilityTreeTitleFinder
import dev.bananz0.opentvbridge.core.DetectedContent
import dev.bananz0.opentvbridge.core.LaunchRequestFactory
import dev.bananz0.opentvbridge.core.LauncherTextParser
import dev.bananz0.opentvbridge.core.MetadataMatcher
import dev.bananz0.opentvbridge.core.NodeSnapshot
import dev.bananz0.opentvbridge.core.ParsedTitle
import dev.bananz0.opentvbridge.core.RecentOpenGuard
import dev.bananz0.opentvbridge.core.ResolveResult
import dev.bananz0.opentvbridge.launch.AndroidTargetLauncher
import dev.bananz0.opentvbridge.network.CinemetaClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class OpenTvBridgeAccessibilityService : AccessibilityService() {
    private val background = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val recentOpenGuard = RecentOpenGuard()
    private val resolver by lazy {
        CinemetaClient(
            OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build(),
            BuildConfig.CINEMETA_BASE_URL.toHttpUrl(),
        )
    }
    private val launcher by lazy { AndroidTargetLauncher(this) }
    private val settings by lazy { SettingsRepository(this) }

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            packageNames = SUPPORTED_LAUNCHERS.toTypedArray()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return
        if (packageName !in SUPPORTED_LAUNCHERS) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (packageName in GOOGLE_LAUNCHERS) scheduleDetailInspection(250L)
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> handleClick(event, packageName)
            else -> Unit
        }
    }

    private fun handleClick(event: AccessibilityEvent, packageName: String) {
        if (packageName == FIRE_TV_LAUNCHER) {
            val title = event.source?.let(::snapshotAndRecycle)
                ?.let(AccessibilityTreeTitleFinder::findFireTvTitle)
            title?.let(::resolveAndOpen)
            return
        }

        val detected = LauncherTextParser.fromDescription(
            event.contentDescription,
            event.className,
            event.text ?: emptyList(),
        ) ?: if (event.className?.toString() == "android.view.ViewGroup") {
            LauncherTextParser.fromHeroText(event.text)
        } else {
            null
        }

        if (detected != null) {
            handleDetected(detected)
        } else {
            // Detail pages are more reliable than card descriptions and keep
            // punctuation such as commas in titles intact.
            scheduleDetailInspection(300L)
        }
    }

    private fun scheduleDetailInspection(delayMs: Long) {
        mainHandler.postDelayed({
            val root = rootInActiveWindow ?: return@postDelayed
            val parsed = snapshotAndRecycle(root).let(AccessibilityTreeTitleFinder::findGoogleTitle)
            parsed?.let(::resolveAndOpen)
        }, delayMs)
    }

    private fun handleDetected(content: DetectedContent) {
        when (content) {
            is DetectedContent.Media -> resolveAndOpen(content.parsedTitle)
            is DetectedContent.YouTube -> {
                if (!settings.smartTubeEnabled || !recentOpenGuard.shouldOpen("youtube:${content.title}")) return
                val stable = LaunchRequestFactory.forSmartTube(content.title)
                if (!launcher.open(stable)) {
                    launcher.open(LaunchRequestFactory.forSmartTube(content.title, beta = true))
                }
            }
        }
    }

    private fun resolveAndOpen(query: ParsedTitle) {
        val key = MetadataMatcher.normalize(query.title) + ":" + (query.year ?: "")
        if (!inFlight.add(key)) return
        background.execute {
            try {
                when (val result = resolver.resolve(query)) {
                    is ResolveResult.Found -> {
                        if (recentOpenGuard.shouldOpen("${result.match.type}:${result.match.imdbId}")) {
                            val request = LaunchRequestFactory.forMedia(settings.targetApp, result.match)
                            mainHandler.post { launcher.open(request) }
                        }
                    }
                    ResolveResult.NotFound -> debug("No confident metadata match for ${query.title}")
                    is ResolveResult.NetworkError -> debug("Metadata network error: ${result.message}")
                }
            } finally {
                inFlight.remove(key)
            }
        }
    }

    private fun snapshotAndRecycle(root: AccessibilityNodeInfo): NodeSnapshot = try {
        snapshot(root, 0, AtomicInteger(MAX_NODES))
    } finally {
        @Suppress("DEPRECATION")
        root.recycle()
    }

    private fun snapshot(
        node: AccessibilityNodeInfo,
        depth: Int,
        remaining: AtomicInteger,
    ): NodeSnapshot {
        if (depth >= MAX_DEPTH || remaining.decrementAndGet() < 0) {
            return NodeSnapshot(
                viewId = node.viewIdResourceName,
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
            )
        }
        val children = buildList {
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                try {
                    add(snapshot(child, depth + 1, remaining))
                } finally {
                    @Suppress("DEPRECATION")
                    child.recycle()
                }
            }
        }
        return NodeSnapshot(
            viewId = node.viewIdResourceName,
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            children = children,
        )
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        background.shutdownNow()
        super.onDestroy()
    }

    private fun debug(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private companion object {
        const val TAG = "OpenTVBridge"
        const val FIRE_TV_LAUNCHER = "com.amazon.tv.launcher"
        const val MAX_DEPTH = 20
        const val MAX_NODES = 400
        val GOOGLE_LAUNCHERS = setOf(
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
        )
        val SUPPORTED_LAUNCHERS = GOOGLE_LAUNCHERS + FIRE_TV_LAUNCHER
    }
}
