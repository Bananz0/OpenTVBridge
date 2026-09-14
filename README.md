# OpenTVBridge

[![verify](https://github.com/Bananz0/OpenTVBridge/actions/workflows/ci.yml/badge.svg)](https://github.com/Bananz0/OpenTVBridge/actions/workflows/ci.yml)
[![AGPL-3.0-or-later](https://img.shields.io/badge/license-AGPL--3.0--or--later-blue.svg)](LICENSE)

OpenTVBridge is a free, account-free Android TV / Google TV accessibility
utility. Select a film or series on a supported TV launcher and it identifies
the title, then opens it in Nuvio, Stremio, WuPlay, Plex, Jellyfin, Fladder,
Wholphin, Emby, or Kodi.

This project does not provide, index, host, or stream audiovisual content. It
only connects a launcher selection to another app already installed by the
user.

> **Project status:** pre-release. The automated test suite is comprehensive,
> but launcher accessibility trees are vendor-controlled. Complete the
> physical-device matrix in [Testing](docs/TESTING.md) before calling a build
> stable.

## Why this project exists

The functionality is small enough to be transparent and community-maintained.
OpenTVBridge has no subscription, account, analytics, advertising, or private
backend, and it embeds no API key or token of its own.

It is licensed under AGPL-3.0-or-later so recipients retain the same freedoms.
The full licence text is bundled inside the app, and the **Licence and notices**
screen tells every recipient where the corresponding source is.

This repository is an independent compatibility implementation. It does not
contain decompiled code, extracted credentials, a patched APK, or closed
project assets. See [NOTICE](NOTICE) for provenance boundaries.

## How it works

1. An accessibility service restricted to supported launcher package names
   notices a click or detail page.
2. It reads a likely title from known view ids or a card description, in any of
   eight languages.
3. It queries Cinemeta's public catalogues and scores title, year, and type
   rather than accepting an unrelated first result.
4. It walks your destination list until one accepts the launch.

| Target | Result |
|---|---|
| Nuvio | IMDb-backed film or series deep link |
| Stremio | IMDb-backed detail deep link |
| WuPlay | IMDb-backed deep link on its own scheme |
| CloudStream | A title search on its documented scheme |
| Plex | The item page with your own token, otherwise a public search |
| Jellyfin | The exact library item with your own server, otherwise an in-app search |
| Fladder | The library item, otherwise its Jellyseerr route, otherwise the app |
| Wholphin | The exact library item, otherwise an in-app search |
| Emby | The library item with your own server, otherwise the app |
| Kodi | Found and played over JSON-RPC on your own instance |
| SmartTube | Optionally opens a YouTube title search in stable, then beta |

The app suppresses duplicate launcher events, bounds accessibility-tree
traversal, and declines low-confidence metadata matches.

## Routing

By default OpenTVBridge picks for you: it prefers whichever installed app can
open the *exact* title over one that can only search for it, and only falls
back to merely opening an app if you asked for that app yourself.

Because Jellyfin, Wholphin, and Fladder all resolve through the same Jellyfin
item id, configuring one server is enough — whichever of those clients you
actually have gets promoted. Plex works the same way once you add a token.

Your own order breaks ties, a per-type override always wins (films and series
can go to different apps), and uninstalled destinations are marked in the UI
and skipped. Turn off **Prefer whichever installed app can open the exact
title** if you would rather your order be followed literally.

A destination that cannot serve a particular title — Kodi not holding it,
an unreachable Jellyfin server — hands over to the next one rather than
failing the whole selection.

## Optional connections

Everything below is optional and belongs to you. Without any of it,
OpenTVBridge uses the keyless Cinemeta path and opens searches rather than
exact items.

| You supply | It buys you |
|---|---|
| TMDB API key | Better matching for titles Cinemeta does not index, and Fladder's request route |
| Plex token | Plex opens the item page instead of a search |
| Jellyfin server + key | Jellyfin and Fladder open the exact item in your library |
| Emby server + key | Best-effort item addressing |
| Kodi address | Kodi finds and plays the title from your library |
| Trakt client id + token | Opened titles are added to your watchlist |

These are stored only on this device and excluded from backups. Read
[SECURITY.md](SECURITY.md) for exactly what is sent where, and for why they are
stored in plain private preferences rather than behind a false promise of
encryption.

## Recent activity

Launcher layouts change without notice, and the resulting failure is silent.
The **Recent activity** screen shows what the service saw, what it parsed, how
confident the match was, and which destination was tried — and copies it as
text for a bug report. It is in-memory only and cleared when the app stops.

## Privacy and permissions

- `INTERNET`: sends the selected title to Cinemeta, plus any optional service
  you have configured.
- Accessibility service: can retrieve window content, but its manifest and
  runtime configuration restrict events to the supported launcher packages.
- No storage, location, microphone, camera, overlay, device-id, account, or
  package-install permission.
- Package visibility is enumerated in `<queries>`: OpenTVBridge can see only
  the destinations it opens, never your wider app inventory.
- Backups are disabled. Titles are not persisted. Raw titles are only logged
  in debug builds.

Review the complete manifest at
`app/src/main/AndroidManifest.xml`; there is no hidden backend.

## Release authenticity

Official release APKs are signed with this certificate SHA-256 fingerprint:

```text
36:4F:B1:16:7F:33:57:2D:D0:33:06:90:CA:4B:A7:16:BE:23:31:9A:FC:A7:49:8C:56:43:04:AE:2D:D8:F3:47
```

Compare it with `apksigner verify --print-certs app-release.apk` and verify the
published SHA-256 checksum before installing a release.

## Install a development build

Android 13+ may block accessibility for normally sideloaded apps under
Restricted Settings. Installing through ADB is the most consistent test path:

```bash
adb connect TV_IP:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open OpenTVBridge, select a destination, and choose **Open accessibility
settings**. Enable only the OpenTVBridge service.

Never overwrite the complete `enabled_accessibility_services` setting unless
you first preserve services such as TalkBack.

## Build and test

Requirements: JDK 21 and Android SDK platform 37.0.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest # emulator or connected TV
```

The main CI workflow runs unit tests, lint, and both debug and minified release
builds. A separate instrumentation workflow exercises the UI and manifest on an
emulator. See the [edge-case matrix](docs/TESTING.md),
[compatibility contracts](docs/COMPATIBILITY.md), and
[release guide](docs/RELEASING.md).

## Known limitations

- Google and device manufacturers can change launcher accessibility trees
  without notice.
- Generic cards with ambiguous descriptions are intentionally ignored; the
  subsequent detail page is safer.
- Same-named works without a visible year can still be ambiguous. A match below
  the confidence floor is not opened.
- Nuvio and Fladder compatibility is verified against their published source
  and should be retested when either changes its contract.
- Emby publishes no intent contract, so its item addressing is best-effort and
  falls back to opening the app.
- Kodi requires "Allow remote control via HTTP" and only finds titles already
  in its library.
- WuPlay's deep-link path shape is inferred rather than confirmed; its scheme
  and hosts were read from its shipped manifest. Wholphin documents its intents
  but calls them experimental.
- **Fire TV is expected not to work.** Fire OS restricts enabling an
  accessibility service for sideloaded apps. Working around that needs usage
  access plus MediaProjection screen capture, which is a far broader capability
  than this project is willing to ask for. See
  [compatibility](docs/COMPATIBILITY.md#fire-tv).
- Xiaomi launcher support is experimental.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md). Compatibility changes must include
redacted fixtures and tests. Never submit proprietary/decompiled source or
credentials.

## Licence and trademarks

Copyright © 2026 OpenTVBridge contributors.

Licensed under the GNU Affero General Public License, version 3 or later.
Product
names belong to their respective owners. This project is not affiliated with
or endorsed by any named launcher, player, or metadata provider.
