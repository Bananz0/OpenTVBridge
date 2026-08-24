# Contributing

By contributing, you agree that your work is licensed under GPL-3.0-or-later
and that you have the right to submit it.

## Development

1. Install JDK 21 and Android SDK platform 37.0.
2. Run `./gradlew testDebugUnitTest lintDebug assembleDebug`.
3. If an emulator or TV is available, run `./gradlew connectedDebugAndroidTest`.
4. Add tests for every parser, resolver, or intent change.

Do not contribute code copied from a decompiler, closed repository, leaked
source archive, or another project without a compatible licence. Do not commit
API keys, signing keys, device identifiers, email addresses, or private server
URLs.

Compatibility fixes should include the launcher package, OS/launcher version,
a redacted accessibility-tree fixture, and the expected result. Never attach a
tree containing account names or viewing history.
