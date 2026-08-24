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
