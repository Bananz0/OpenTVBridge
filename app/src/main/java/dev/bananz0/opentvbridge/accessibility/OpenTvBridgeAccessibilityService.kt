// SPDX-License-Identifier: AGPL-3.0-or-later
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
import dev.bananz0.opentvbridge.bridge.BridgeFactory
import dev.bananz0.opentvbridge.core.AccessibilityTreeTitleFinder
import dev.bananz0.opentvbridge.core.DetectedContent
import dev.bananz0.opentvbridge.core.DiagnosticStage
import dev.bananz0.opentvbridge.core.Diagnostics
import dev.bananz0.opentvbridge.core.LaunchRequestFactory
import dev.bananz0.opentvbridge.core.LauncherProfile
import dev.bananz0.opentvbridge.core.LauncherProfiles
import dev.bananz0.opentvbridge.core.LauncherTextParser
import dev.bananz0.opentvbridge.core.NodeSnapshot
import dev.bananz0.opentvbridge.core.ParsedTitle
import dev.bananz0.opentvbridge.core.RecentOpenGuard
import dev.bananz0.opentvbridge.launch.AndroidTargetLauncher
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class OpenTvBridgeAccessibilityService : AccessibilityService() {
    private val background = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val recentOpenGuard = RecentOpenGuard()
    private val launcher by lazy { AndroidTargetLauncher(this) }
    private val settings by lazy { SettingsRepository(this) }
    private val diagnostics get() = Diagnostics.log

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            // View ids are how detail-page titles are located at all.
            // Launcher cards are frequently marked unimportant for
            // accessibility, so without the second flag their nodes never
            // reach us and detection silently sees an empty tree.
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            packageNames = LauncherProfiles.packageNames.toTypedArray()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return
        val profile = LauncherProfiles.forPackage(packageName) ?: return

        when (event.eventType) {
            // Detail pages also open from voice and search results, which
            // produce no click event at all.
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ->
                if (profile.inspectsDetailPages) scheduleDetailInspection(profile, DETAIL_DELAY_MS)

            AccessibilityEvent.TYPE_VIEW_CLICKED -> handleClick(event, profile)

            else -> Unit
        }
    }

    private fun handleClick(event: AccessibilityEvent, profile: LauncherProfile) {
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
            handleDetected(detected, profile)
            return
        }

        // Cards whose title lives on a child node (Fire TV) are read from the
        // clicked subtree rather than the event itself.
        if (profile.cardDescriptionViewIds.isNotEmpty()) {
            val fromCard = event.source
                ?.let(::snapshotAndRecycle)
                ?.let { AccessibilityTreeTitleFinder.findCardTitle(it, profile) }
            if (fromCard != null) {
                resolveAndOpen(fromCard, profile)
                return
            }
        }

        // Google TV populates some content descriptions after the click event
        // is dispatched. Re-reading the clicked node recovers those; the detail
        // sweep afterwards covers cards that open a detail page instead.
        scheduleSourceRetry(event, profile)
        if (profile.inspectsDetailPages) scheduleDetailInspection(profile, CLICK_DETAIL_DELAY_MS)
    }

    private fun scheduleSourceRetry(event: AccessibilityEvent, profile: LauncherProfile) {
        val source = event.source ?: return
        mainHandler.postDelayed({
            val description = runCatching {
                source.refresh()
                source.contentDescription
            }.getOrNull()
            runCatching {
                @Suppress("DEPRECATION")
                source.recycle()
            }
            val detected = LauncherTextParser.fromDescription(description) ?: return@postDelayed
            handleDetected(detected, profile)
        }, LATE_DESCRIPTION_DELAY_MS)
    }

    private fun scheduleDetailInspection(profile: LauncherProfile, delayMs: Long) {
        mainHandler.postDelayed({
            val root = rootInActiveWindow ?: return@postDelayed
            val snapshot = snapshotAndRecycle(root)
            val parsed = AccessibilityTreeTitleFinder.findDetailTitle(snapshot, profile)
                ?: AccessibilityTreeTitleFinder.findCardTitle(snapshot, profile)
                ?: return@postDelayed
            resolveAndOpen(parsed, profile)
        }, delayMs)
    }

    private fun handleDetected(content: DetectedContent, profile: LauncherProfile) {
        when (content) {
            is DetectedContent.Media -> resolveAndOpen(content.parsedTitle, profile)

            is DetectedContent.YouTube -> {
                if (!settings.smartTubeEnabled) {
                    diagnostics.record(
                        stage = DiagnosticStage.IGNORED,
                        launcherPackage = profile.packageName,
                        parsedTitle = content.title,
                        detail = "SmartTube redirect disabled",
                    )
                    return
                }
                if (!recentOpenGuard.shouldOpen("youtube:${content.title}")) return

                val opened = launcher.open(LaunchRequestFactory.forSmartTube(content.title)) ||
                    launcher.open(LaunchRequestFactory.forSmartTube(content.title, beta = true))
                diagnostics.record(
                    stage = if (opened) DiagnosticStage.LAUNCHED else DiagnosticStage.FAILED,
                    launcherPackage = profile.packageName,
                    parsedTitle = content.title,
                    detail = if (opened) "SmartTube" else "SmartTube is not installed",
                )
            }
        }
    }

    private fun resolveAndOpen(query: ParsedTitle, profile: LauncherProfile) {
        val key = query.title.lowercase() + ":" + (query.year ?: "")
        if (!inFlight.add(key)) return

        diagnostics.record(
            stage = DiagnosticStage.DETECTED,
            launcherPackage = profile.packageName,
            parsedTitle = query.title,
            parsedYear = query.year,
        )
        debug("Detected ${query.title}")

        background.execute {
            try {
                // Built per pass so credential and routing changes take effect
                // without re-enabling the accessibility service.
                BridgeFactory.pipeline(this, settings, recentOpenGuard)
                    .handle(query, profile.packageName)
            } catch (error: Exception) {
                diagnostics.record(
                    stage = DiagnosticStage.FAILED,
                    launcherPackage = profile.packageName,
                    parsedTitle = query.title,
                    detail = error.message ?: error.javaClass.simpleName,
                )
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
        const val MAX_DEPTH = 20
        const val MAX_NODES = 400

        /** Detail pages settle shortly after the window-state change. */
        const val DETAIL_DELAY_MS = 250L
        const val CLICK_DETAIL_DELAY_MS = 300L

        /** Matches the delay upstream used for late content descriptions. */
        const val LATE_DESCRIPTION_DELAY_MS = 600L
    }
}
