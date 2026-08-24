# Compatibility contracts

OpenTVBridge uses Android's documented accessibility and intent APIs. The
destination contracts are isolated in `LaunchRequestFactory` so changes do not
affect title detection or metadata matching.

| Target | Contract |
|---|---|
| Nuvio | `nuvio://movie/{imdb}` or `nuvio://detail/tv/{imdb}`; tries full/GitHub `com.nuvio.tv`, then Play/compatible `com.nuvio.app` |
| Stremio | `stremio:///detail/{movie|series}/{imdb}` |
| Plex | Public `https://watch.plex.tv/search?q={title}` URL, targeted to Plex with browser fallback |
| Jellyfin | Android `ACTION_SEARCH` with a `query` extra targeted to Jellyfin |
| SmartTube | YouTube search URL targeted to stable, then beta package |

The resolver uses the public Cinemeta movie and series catalog search endpoints
and only consumes id, type, title, and release-year metadata. OpenTVBridge does
not bundle a TMDB or Plex credential.

The Nuvio contract is verified against the public parser tests in Nuvio TV
0.8.7-beta. Its public Gradle configuration defines `com.nuvio.tv` for full
builds and `com.nuvio.app` for Play builds. Treat this as versioned
compatibility and retest newer releases. If it changes, update the adapter and
tests without adding a dependency on Nuvio implementation code.
