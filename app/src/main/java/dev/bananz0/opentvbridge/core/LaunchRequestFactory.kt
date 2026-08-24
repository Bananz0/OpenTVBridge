package dev.bananz0.opentvbridge.core

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class TargetApp(val packageName: String) {
    NUVIO("com.nuvio.app"),
    STREMIO("com.stremio.one"),
    PLEX("com.plexapp.android"),
    JELLYFIN("org.jellyfin.androidtv"),
}

sealed interface LaunchRequest {
    data class View(
        val uri: String,
        val packageName: String,
        val allowGenericFallback: Boolean = true,
    ) : LaunchRequest

    data class Search(
        val query: String,
        val packageName: String,
        val componentClass: String? = null,
    ) : LaunchRequest
}

object LaunchRequestFactory {
    fun forMedia(target: TargetApp, match: MediaMatch): LaunchRequest = when (target) {
        TargetApp.NUVIO -> LaunchRequest.View(
            uri = if (match.type == MediaType.MOVIE) {
                "nuvio://movie/${match.imdbId}"
            } else {
                "nuvio://detail/tv/${match.imdbId}"
            },
            packageName = target.packageName,
        )

        TargetApp.STREMIO -> LaunchRequest.View(
            uri = "stremio:///detail/${match.type.wireValue}/${match.imdbId}",
            packageName = target.packageName,
        )

        TargetApp.PLEX -> LaunchRequest.View(
            uri = "https://watch.plex.tv/search?q=${encode(match.title)}",
            packageName = target.packageName,
        )

        TargetApp.JELLYFIN -> LaunchRequest.Search(
            query = match.title,
            packageName = target.packageName,
            componentClass = "org.jellyfin.androidtv.ui.startup.StartupActivity",
        )
    }

    fun forSmartTube(title: String, beta: Boolean = false): LaunchRequest.View = LaunchRequest.View(
        uri = "https://www.youtube.com/results?search_query=${encode(title)}",
        packageName = if (beta) "org.smarttube.beta" else "org.smarttube.stable",
        allowGenericFallback = false,
    )

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
