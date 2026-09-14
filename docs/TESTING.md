# Test strategy and edge-case matrix

## Automated unit tests

The JVM suite covers:

- Spanish and English card markers.
- Titles containing commas, punctuation, accents, ampersands, and trailing
  edition/year suffixes.
- Empty, sponsored, malformed, and non-content events.
- Google TV and Fire TV accessibility-tree traversal.
- Movie/series ambiguity, exact and fuzzy metadata matches, year agreement and
  conflict, invalid IMDb ids, duplicate results, and confidence thresholds.
- Partial provider failure, total provider failure, malformed JSON, HTTP
  errors, empty catalogs, URL encoding, and deterministic result selection.
- Every Nuvio/Stremio/Plex/Jellyfin/SmartTube launch request.
- Duplicate-click suppression and cooldown expiry.

## Instrumented smoke tests

The Android suite verifies that the TV activity renders, every target can be
selected, preferences survive activity recreation, and the service metadata is
declared. The `instrumentation.yml` workflow runs these tests on an emulator.

## Target-app smoke test

The settings screen includes **Test selected app with Iron Man**. It resolves a
known title through the same metadata client and opens it through the same
adapter used by the accessibility service. Use it once with each installed
target before testing launcher cards.

Use **Test SmartTube redirect** to verify the stable package and beta fallback
without waiting for Google TV to surface a YouTube recommendation.

For Nuvio, test both distributions when available:

- `com.nuvio.tv` (full/GitHub distribution)
- `com.nuvio.app` (Play distribution)

The bridge tries them in that order. Jellyfin requires a configured server to
show library search results; reaching Jellyfin without a crash verifies only
the Android hand-off until a server is connected.

## Emulator verification (Google TV, API 36)

Run against AVD `OpenTVBridgeApi36`
(`system-images/android-36/google-tv/x86_64`, booted with `-gpu auto`; the
image accepts arm64 destination APKs through built-in translation). The Google
TV image ships the real `com.google.android.apps.tv.launcherx` launcher with
live recommendation rows, so the launcher-card flow is exercised against the
real launcher, not a stub.

The instrumented smoke suite passes on the AVD (4/4, zero failures). Scope
Gradle to one device with `ANDROID_SERIAL=emulator-5554` — without it the
suite also fans out to any attached phone, where a locked screen and enabled
animations produce environmental failures (`NoActivityResumedException`,
scroll blocked by animations) unrelated to the app.

Destinations installed from their GitHub releases plus official sources:
Nuvio, Stremio, WuPlay, CloudStream, Jellyfin, Fladder, Wholphin, SmartTube,
Kodi. Emby and Plex have no public Android TV APK (GitHub carries only the
mobile `com.mb.android`; Plex needs an account token), so their deep links
stay covered by unit tests until a device with them is available.

On-device deep-link contracts, verified with
`cmd package query-activities` plus `am start -W` probes that confirm the
landing activity:

| Destination | Verified intent | Landing |
|---|---|---|
| Nuvio | `nuvio://movie/{imdb}`, `nuvio://detail/tv/{imdb}` | Nuvio detail activity |
| Stremio | `stremio:///detail/{movie\|series}/{imdb}` | Stremio player/detail |
| WuPlay | `wuplay://{movie\|series}/{imdb}` | MainActivity claims it |
| CloudStream | `cloudstreamsearch://{title}` | CloudStream search |
| Jellyfin | explicit component + `Search` / `ItemId` extras | StartupActivity |
| Fladder | `fladder:///details?id=`, `fladder:///seerr/{movie\|tv}/{tmdbId}` | Fladder detail |
| Wholphin | `wholphin://view?itemId=`, `wholphin://search?query=` | Wholphin |
| SmartTube | `https://www.youtube.com/results?search_query=` | SearchTagsActivity |
| Kodi | plain launch (JSON-RPC follow-up) | Kodi home |

Nuvio also registers the `stremio://` scheme, so a bare scheme intent would
raise a chooser — the bridge's explicit `-p` package targeting is what keeps
the hand-off deterministic.

The accessibility pipeline was exercised end-to-end against the real Google TV
launcher: with the debug service bound
(`settings put secure enabled_accessibility_services
dev.bananz0.opentvbridge.debug/...OpenTvBridgeAccessibilityService`), activating
a launcher hero card with `DPAD_CENTER` produced the full chain in the
diagnostics log —

```
14:48:15  DETECTED launcher=com.google.android.apps.tv.launcherx parsed="Doctor Strange"
14:48:16  RESOLVED match="Doctor Strange" imdb=tt0910865 score=85
14:48:16  LAUNCHED target=NUVIO
```

— with `com.nuvio.tv` foreground as the hand-off result. The SmartTube
redirect path was verified with the in-app **Test SmartTube redirect** button,
landing directly on SmartTube's search screen. Two device-only findings came
out of the run:

- WuPlay declares only `CATEGORY_LEANBACK_LAUNCHER`, which made
  `getLaunchIntentForPackage` report it missing; the installed check now
  falls back to the Leanback entry point, fixing both the UI hint and routing.
- The unexported `DiagnosticsActivity` correctly rejects shell launches
  (`Permission Denial: not exported`), confirming the privacy posture
  end-to-end. Reach it through the in-app **Recent activity** row; its text
  uses single-quoted attributes in `uiautomator` dumps, so grep for
  `text='` not `text="`.

## Required physical-device matrix before a stable release

| Device family | Required checks |
|---|---|
| Google TV Streamer / Chromecast | Home rows, hero card, detail page, voice search, duplicate-back behavior |
| Sony/TCL/Hisense Google TV | Same checks plus background-process survival |
| Android TV launcher | Detail-title discovery and no interception of application icons |
| Fire TV (experimental) | Main-image title extraction and normal app-tile behavior |

For each installed destination, test a film, a series, a title containing a
comma, two works with the same name but different years, non-Latin text, a
missing title, and behavior when the target app is absent.

Accessibility trees change independently of Android OS releases. Physical
device verification is therefore mandatory before calling a release stable;
the test fixtures protect known behavior but cannot predict a launcher update.
