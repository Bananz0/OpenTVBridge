# Release guide

## One-time repository setup

1. Enable Issues, Discussions, private vulnerability reporting, and Dependabot
   security updates.
2. Protect `main`: require a pull request, the `verify` status check, resolved
   conversations, and no force pushes.
3. Generate a dedicated upload key and keep it offline:

   ```bash
   keytool -genkeypair -v -keystore release.jks -alias opentvbridge \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

4. Add GitHub Actions secrets:

   - `ANDROID_KEYSTORE_BASE64`
   - `ANDROID_STORE_PASSWORD`
   - `ANDROID_KEY_ALIAS`
   - `ANDROID_KEY_PASSWORD`

   Create the first value with `base64 -w 0 release.jks` on Linux or
   `[Convert]::ToBase64String([IO.File]::ReadAllBytes('release.jks'))` in
   PowerShell.

Never commit the keystore or `keystore.properties`. Losing the key prevents
users from upgrading an existing installation under the same package name.

## Each release

1. Complete the physical-device checklist in `docs/TESTING.md`.
2. Update `versionCode` and `versionName` in `app/build.gradle.kts`.
3. Update the changelog and compatibility notes.
4. Run:

   ```bash
   ./gradlew clean testDebugUnitTest lintRelease connectedDebugAndroidTest
   ```

5. Merge through the protected branch, then create an annotated tag:

   ```bash
   git tag -s v0.1.0 -m "OpenTVBridge 0.1.0"
   git push origin v0.1.0
   ```

The release workflow builds a signed APK and AAB, creates SHA-256 files, and
publishes a GitHub prerelease. Download the APK from GitHub, verify its checksum
and signing certificate with `apksigner verify --print-certs`, install it on a
clean TV, and promote it from prerelease only after the smoke test passes.
