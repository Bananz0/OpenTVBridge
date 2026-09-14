# Security policy

Please report vulnerabilities privately through GitHub's **Report a
vulnerability** feature. Do not open a public issue containing credentials,
device identifiers, private media-library information, or exploit details.

Supported versions are the latest tagged release and the current `main`
branch.

OpenTVBridge intentionally requests only Internet access and an accessibility
service bound to supported launcher packages. A report that shows the service
reading another package, transmitting more than a selected title, or opening
an untrusted URI is considered high priority.

## Optional credentials

OpenTVBridge ships no API key, token, or secret of its own. Everything under
**Optional connections** is supplied by the user, for services the user
controls, and is used only to address an item more precisely than a search can.

Those values are held in app-private `SharedPreferences`, and:

- backups are disabled (`allowBackup="false"`), so they are excluded from
  device transfer and cloud backup;
- they are stored in plain text within that private storage.

Plain text is a deliberate choice rather than an oversight. On Android, private
preferences are already protected from other apps by the platform sandbox, and
the keystore-backed alternatives available to a TV app do not defend against
the case that actually matters here — someone with root or physical access to
an unlocked device. Encrypting them would mostly buy the appearance of safety.
Treat any token entered here as recoverable by anyone who fully controls the
device, and prefer scoped, revocable credentials.

**Forget all credentials** in that screen clears every stored value.

## What leaves the device

| Path | Sent | When |
|---|---|---|
| Cinemeta | The selected title | Always, this is the keyless default |
| TMDB | The title, or an IMDb id | Only with a user-supplied key |
| Plex | An IMDb id and the user's token | Only with a user-supplied token |
| Jellyfin / Emby | A title or IMDb id and the user's key, to the user's own server | Only with a configured server |
| Kodi | A JSON-RPC call to the user's own instance | Only with a configured instance |
| Trakt | An IMDb id and the user's token | Only when watchlist sync is enabled |

No account, advertising, analytics, crash-reporting, or device-identifier
traffic exists on any path. There is no OpenTVBridge backend.

## Diagnostics

The **Recent activity** screen holds a bounded in-memory log of what the
service saw and did. It is never written to disk and is lost when the process
ends. It can contain the titles you selected, so review it before pasting it
into a public issue.
