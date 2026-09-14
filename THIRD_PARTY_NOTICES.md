# Third-party software

OpenTVBridge's distributed application has a deliberately small dependency
surface:

| Component | Purpose | Licence |
|---|---|---|
| AndroidX Core / Core KTX | Android compatibility helpers | Apache-2.0 |
| OkHttp and Okio | HTTPS client | Apache-2.0 |
| Gson | JSON parsing | Apache-2.0 |
| Kotlin standard library | Language runtime | Apache-2.0 |

All four are Apache-2.0, which is compatible with AGPL-3.0-or-later. The
combined work is distributed under AGPL-3.0-or-later; the Apache-2.0 components
keep their own terms, and their notices are reproduced in the app's **Licence
and notices** screen.

Development and test-only dependencies include JUnit 4 and MockWebServer,
licensed under EPL-1.0 and Apache-2.0 respectively. Android SDK/Gradle tooling
is not redistributed as part of the application APK.

Dependency versions are centralized in `gradle/libs.versions.toml` and are
monitored by Dependabot. Licence texts and source links are available from the
corresponding upstream projects:

- https://github.com/androidx/androidx
- https://github.com/square/okhttp
- https://github.com/google/gson
- https://github.com/JetBrains/kotlin
- https://github.com/junit-team/junit4

## Services

OpenTVBridge talks to these services but bundles no credential for any of them
and vendors none of their code:

- Cinemeta (`v3-cinemeta.strem.io`), the keyless default metadata source
- TMDB, Plex, Trakt, and the user's own Jellyfin, Emby, or Kodi instance, each
  only when the user has supplied their own credentials

## Interoperability

The deep-link and intent formats OpenTVBridge emits were read from the public
source of Nuvio, Jellyfin, and Fladder, and from the published JSON-RPC and web
APIs of Kodi, Plex, TMDB, and Trakt. No code from those projects is copied,
linked, or redistributed here; only the externally observable request formats
are reimplemented. See `docs/COMPATIBILITY.md` for what each contract was
verified against.
