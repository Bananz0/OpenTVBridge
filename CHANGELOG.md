# Changelog

All notable changes follow [Keep a Changelog](https://keepachangelog.com/) and
versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Fladder, Wholphin, WuPlay, CloudStream, Emby, and Kodi destinations. Fladder
  uses its published `fladder:///details` and `fladder:///seerr` routes,
  Wholphin the `wholphin://view` and `wholphin://search` routes from its own
  `Intents.md`, CloudStream the `cloudstreamsearch://` scheme documented in its
  manifest, and Kodi is driven over JSON-RPC against the user's own instance.
- Destination fallback: the primary choice is tried first, then the next
  installed one. A destination that cannot serve a title yields to the next
  rather than failing the selection.
- Capability-aware auto-ranking, on by default: destinations that can open the
  exact title are preferred over ones that can only search, which are preferred
  over ones that merely open an app. Configuration feeds the ranking, so adding
  one Jellyfin server promotes whichever of Jellyfin, Wholphin, or Fladder is
  installed. The user's order breaks ties and a per-type override always leads.
  A destination that can only open its app is never chosen automatically unless
  the user named or configured it.
- Per-type routing, so films and series can go to different apps.
- Uninstalled destinations are marked in the UI and skipped.
- **Recent activity** screen: a bounded in-memory log of what was detected,
  matched, and launched, copyable as text for bug reports.
- **Optional connections** screen for user-supplied TMDB, Plex, Jellyfin, Emby,
  Kodi, and Trakt credentials. With them, Plex, Jellyfin, Fladder, and Emby
  open the exact item instead of a search, and opened titles can be added to a
  Trakt watchlist.
- TMDB as a secondary metadata resolver, used only when Cinemeta finds nothing.
- Launcher detection markers for German, French, Italian, Portuguese, Dutch,
  and Polish, alongside the existing English and Spanish.
- Experimental support for the Fire TV leanback and Xiaomi launchers.
- **Licence and notices** screen carrying the full AGPL-3.0 text, the source
  location, and the warranty disclaimer. The licence now ships inside the APK.
- `SPDX-License-Identifier` headers on every source file.

### Changed

- **Relicensed from GPL-3.0-or-later to AGPL-3.0-or-later.** Relicensed by the
  sole copyright holder. GPL-3.0 already required anyone distributing a binary
  built from this code to publish the corresponding source; AGPL extends that
  obligation to making a modified version available to others over a network,
  which matters if this project ever grows a hosted or companion component.
- Launcher support is now described by data (`LauncherProfile`) rather than
  code, so adding a launcher is a table entry.
- A clicked node's content description is re-read after 600 ms, recovering
  titles Google TV populates after the click event is dispatched.
- Response parsing no longer throws on malformed or non-JSON bodies.
- The accessibility XML config now matches the service's runtime scope exactly.
- `SettingsRepository` migrates the v0.1.0 single-destination preference into
  the new ordered list.

### Fixed

- Every destination now participates in fallback. The default routing order
  listed only four of them, so the rest could be reached only by being chosen
  as the primary destination — which silently disabled fallback to them.
- Bringing an app forward no longer clears its task. `FLAG_ACTIVITY_CLEAR_TASK`
  was applied to every launch, which would reset the target to its root
  activity and discard state — for Kodi, tearing down the playback that had
  just been started over JSON-RPC. Item and search requests still clear the
  task, which is what makes them land on the right screen.
- The accessibility service now requests `FLAG_INCLUDE_NOT_IMPORTANT_VIEWS`.
  Launcher cards are frequently marked unimportant for accessibility, and
  without it their nodes never reach the service, so detection saw an empty
  tree on some launchers.

### Security

- Package visibility remains enumerated; the three new destinations are
  declared individually rather than by a broad query.
- Item-specific deep links no longer permit a generic `ACTION_VIEW` fallback,
  so a missing app cannot hand a `fladder://` or `nuvio://` URI to a browser.

## [0.1.0] - 2026-08-24

### Added

- Independent Android TV launcher accessibility bridge.
- Nuvio, Stremio, Plex, Jellyfin, and optional SmartTube adapters.
- Public Cinemeta metadata resolution with confidence scoring.
- JVM edge-case suite, Android smoke tests, CI, signed release automation, and
  security/contribution documentation.
