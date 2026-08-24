# OpenTVBridge

[![verify](https://github.com/Bananz0/OpenTVBridge/actions/workflows/ci.yml/badge.svg)](https://github.com/Bananz0/OpenTVBridge/actions/workflows/ci.yml)
[![GPL-3.0-or-later](https://img.shields.io/badge/license-GPL--3.0--or--later-blue.svg)](LICENSE)

OpenTVBridge is a free, account-free Android TV / Google TV accessibility
utility. Select a film or series on a supported TV launcher and it identifies
the title, then opens Nuvio, Stremio, Plex, or Jellyfin.

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
backend. It is licensed under GPL-3.0-or-later so recipients retain the same
freedoms.

This repository is an independent compatibility implementation. It does not
contain decompiled code, extracted credentials, a patched APK, or closed
project assets. See [NOTICE](NOTICE) for provenance boundaries.

## How it works

1. An accessibility service restricted to supported launcher package names
   notices a click or detail page.
2. It reads a likely title from known view ids or a card description.
3. It queries Cinemeta's public film and series catalogs and scores title,
   year, and type rather than accepting an unrelated first result.
4. It creates one documented/tested Android launch request:

| Target | Result |
|---|---|
| Nuvio | Opens the IMDb-backed film or series deep link |
| Stremio | Opens its IMDb-backed detail deep link |
| Plex | Opens Plex's public search for the resolved title |
| Jellyfin | Starts an in-app search for the resolved title |
| SmartTube | Optionally opens a YouTube title search in stable, then beta |

The app suppresses duplicate launcher events, bounds accessibility-tree
traversal, and declines low-confidence metadata matches.

## Privacy and permissions

- `INTERNET`: sends only the selected title to Cinemeta for identification.
- Accessibility service: can retrieve window content, but its manifest and
  runtime configuration restrict events to the Google TV, Android TV, and
  experimental Fire TV launcher packages.
- No storage, location, microphone, camera, overlay, device-id, account, or
  package-install permission.
- Backups are disabled. Titles are not persisted. Raw titles are only logged
  in debug builds.

Review the complete manifest at
`app/src/main/AndroidManifest.xml`; there is no hidden backend.

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

The main CI workflow runs unit tests, lint, and a debug build. A separate
instrumentation workflow exercises the UI and manifest on an emulator. See the
[edge-case matrix](docs/TESTING.md), [compatibility contracts](docs/COMPATIBILITY.md),
and [release guide](docs/RELEASING.md).

## Known limitations

- Google and device manufacturers can change launcher accessibility trees
  without notice.
- Generic cards with ambiguous descriptions are intentionally ignored; the
  subsequent detail page is safer.
- Same-named works without a visible year can still be ambiguous. A match below
  the confidence floor is not opened.
- Nuvio has no stable public deep-link specification, so compatibility must be
  checked against current releases.
- Plex and Jellyfin open searches instead of assuming access to a user's
  private library identifiers.
- Fire TV support is experimental.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md). Compatibility changes must include
redacted fixtures and tests. Never submit proprietary/decompiled source or
credentials.

## Licence and trademarks

Copyright © 2026 OpenTVBridge contributors.

Licensed under the GNU General Public License, version 3 or later. Product
names belong to their respective owners. This project is not affiliated with
or endorsed by any named launcher, player, or metadata provider.
