# Third-party software

OpenTVBridge's distributed application has a deliberately small dependency
surface:

| Component | Purpose | Licence |
|---|---|---|
| AndroidX Core / Core KTX | Android compatibility helpers | Apache-2.0 |
| OkHttp and Okio | HTTPS client | Apache-2.0 |
| Gson | JSON parsing | Apache-2.0 |
| Kotlin standard library | Language runtime | Apache-2.0 |

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
