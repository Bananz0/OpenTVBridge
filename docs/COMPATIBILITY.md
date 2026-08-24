# Compatibility contracts

OpenTVBridge uses Android's documented accessibility and intent APIs. The
destination contracts are isolated in `LaunchRequestFactory` so changes do not
affect title detection or metadata matching.

| Target | Contract |
|---|---|
| Nuvio | `nuvio://movie/{imdb}` or `nuvio://detail/tv/{imdb}` |
| Stremio | `stremio:///detail/{movie|series}/{imdb}` |
| Plex | Public `https://watch.plex.tv/search?q={title}` URL, targeted to Plex with browser fallback |
| Jellyfin | Android `ACTION_SEARCH` with a `query` extra targeted to Jellyfin |
| SmartTube | YouTube search URL targeted to stable, then beta package |

The resolver uses the public Cinemeta movie and series catalog search endpoints
and only consumes id, type, title, and release-year metadata. OpenTVBridge does
not bundle a TMDB or Plex credential.

Nuvio does not publish a stable public deep-link specification. Treat those
two URIs as an interoperability contract that must be tested against current
Nuvio releases. If it changes, update its adapter and tests without adding a
dependency on Nuvio implementation code.
