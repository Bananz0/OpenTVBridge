# Compatibility contracts

OpenTVBridge uses Android's documented accessibility and intent APIs. The
destination contracts are isolated in `LaunchRequestFactory` so changes do not
affect title detection or metadata matching.

Every contract below was read from the destination's own published source, or
is explicitly marked unverified. An unverified destination still works — it
just does not pretend to address an item it cannot prove it can address.

## Destinations

| Target | Contract | Verified against |
|---|---|---|
| Nuvio | `nuvio://movie/{imdb}` or `nuvio://detail/tv/{imdb}`; tries full/GitHub `com.nuvio.tv`, then Play/compatible `com.nuvio.app` | Nuvio TV 0.8.7-beta parser tests |
| Stremio | `stremio:///detail/{movie\|series}/{imdb}` | Published deep-link format |
| WuPlay | `wuplay://movie/{imdb}` or `wuplay://series/{imdb}`. **Partly unverified** | Scheme and hosts read from the shipped manifest of `app.wuplay.androidtv` 0.9.0-beta; the path shape is inferred |
| CloudStream | `cloudstreamsearch://{title}`; tries release, then the `.prerelease` package | Documented in CloudStream's own manifest comment. It has no id-addressable route, so it can only ever search |
| Wholphin | `wholphin://view?itemId={jellyfinItemId}`, else `wholphin://search?query={title}` | Wholphin's own `Intents.md` |
| Plex | With a user token, the `publicPagesURL` returned by Plex's match endpoint; otherwise `https://watch.plex.tv/search?q={title}` | Plex metadata match endpoint |
| Jellyfin | With a user server, `ACTION_VIEW` carrying the `ItemId` extra to `StartupActivity`; otherwise `ACTION_SEARCH` with a `query` extra | `StartupActivity.kt`, which reads `EXTRA_ITEM_ID = "ItemId"` and handles `ACTION_SEARCH` |
| Fladder | `fladder:///details?id={jellyfinItemId}`, else `fladder:///seerr/{movie\|tv}/{tmdbId}`, else open the app | `lib/util/deep_link_helper.dart` |
| Emby | With a user server, `ACTION_VIEW` with an `ItemId` extra; otherwise open the app. **Unverified** | Emby publishes no intent contract |
| Kodi | Driven over JSON-RPC (`VideoLibrary.GetMovies` / `GetTVShows`, then `Player.Open` or `GUI.ActivateWindow`); the intent only brings Kodi forward. **Unverified as an intent target** | Kodi JSON-RPC API |
| SmartTube | YouTube search URL targeted to stable, then beta | Unchanged from v0.1.0 |

Deep links that name a specific item, and any link whose format is inferred
rather than verified, never fall back to a generic `ACTION_VIEW` — so a missing
app cannot silently hand a `fladder://`, `wholphin://`, or `wuplay://` URI to a
browser.

WuPlay's `Intents.md` equivalent does not exist, and Wholphin's own file warns
that its intent surface is experimental and may change. Both are pinned to the
versions named above and should be retested when either app updates.

### Clients deliberately not included

A destination is only worth offering if it can be told *which* title to open.
These were checked and have no deep-link intent filter at all, so the best they
could do is drop the user on a home screen after they clicked a specific film:

| Client | Package | Finding |
|---|---|---|
| Findroid | `dev.jdtech.jellyfin` | No scheme in either its phone or TV manifest |
| Streamyfin | — | No scheme found in its Android manifest |

Adding a client is a one-row change to `TargetApp` plus a `LaunchRequestFactory`
case. The bar is a contract read from the client's own published source or
shipped manifest — not a plausible-looking URI.

### Routing and capability

Destinations are ranked by what they can actually do for a given match:

1. `EXACT_ITEM` — opens the title itself
2. `SEARCH` — hands over the title as a search term
3. `APP_ONLY` — merely opens the app

The ranking is configuration-aware. Jellyfin, Wholphin, and Fladder all resolve
through the same Jellyfin item id, so configuring one server promotes whichever
of those clients is installed. Plex is a search until a token makes it exact.

The user's own order breaks ties between equally capable destinations, and a
per-type override always leads regardless of ranking. A destination that can
only open its app is never used as automatic fallback unless the user named it
or configured it — auto-ranking will not silently strand someone on a home
screen.

### Intent flags

Requests that address an item or a search use
`FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`, so the destination cannot
resume its previous screen and ignore what was asked for.

Requests that merely bring an app forward use `FLAG_ACTIVITY_NEW_TASK` only.
Clearing the task there would reset the app to its root activity and discard
its state — which for Kodi would tear down the playback just started over
JSON-RPC.

## Metadata

The keyless path uses the public Cinemeta film and series catalogue search and
consumes only id, type, title, and release year.

If the user supplies their own TMDB key, TMDB is added as a *second* resolver,
tried only when Cinemeta returns no confident match. A resolver answering "no
match" is a real answer: only a total outage across every resolver is reported
as a network error.

OpenTVBridge bundles no TMDB, Plex, Trakt, or Jellyfin credential. Upstream
compiled a TMDB key and a Plex token into its APK, which made both trivially
extractable; there is nothing here to extract.

## Launchers

| Launcher | Package | Status |
|---|---|---|
| Google TV | `com.google.android.apps.tv.launcherx` | Supported |
| Android TV | `com.google.android.tvlauncher` | Supported |
| Fire TV | `com.amazon.tv.launcher` | Profiles present, but see below |
| Fire TV (leanback) | `com.amazon.tv.leanbacklauncher` | Profiles present, but see below |
| Xiaomi | `com.mitv.tvhome` | Experimental |

### Fire TV

Fire OS restricts enabling an accessibility service for sideloaded apps, so the
approach this project uses is expected **not** to work on Fire TV, regardless
of the launcher profiles being present. The upstream closed-source app works
around this with a different mechanism entirely: `PACKAGE_USAGE_STATS` plus a
MediaProjection screen capture, a dwell delay, and a confirmation prompt.

OpenTVBridge does not do that and currently has no plan to. Screen capture is a
far broader capability than reading one launcher's node tree, it forces a
persistent recording notification, and it would undermine the permission story
that is the point of this project. The Fire TV profiles are kept because they
cost nothing and would work anywhere the service can actually be enabled.

If you have a Fire TV device where the service *can* be enabled, a diagnostics
export from it would be genuinely useful.

### Voice and search

Detail pages opened by voice — Google Assistant, Gemini, or Alexa — are handled
by the same `TYPE_WINDOW_STATE_CHANGED` sweep as any other detail page. The
mechanism does not care how the page was reached, only that it is on screen, so
no assistant-specific support is required.

Each launcher is described by a `LauncherProfile` holding its detail-page title
view ids and any card ids whose content description carries a title. Adding a
launcher is a data change, not a code change.

The service's runtime configuration is kept in step with
`res/xml/accessibility_service_config.xml`, so the manifest never understates
the real event or package scope.

## Detection languages

Launcher cards describe themselves in the device language. Markers are defined
per language in `LauncherTextParser.markers` for English, Spanish, German,
French, Italian, Portuguese, Dutch, and Polish. Google TV inserts no-break and
narrow no-break spaces around punctuation in some locales; these are normalised
before matching.

Adding a language means adding a row to that table and a case to
`LauncherLocaleTest`.
