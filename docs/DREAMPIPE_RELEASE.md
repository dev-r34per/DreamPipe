# DreamPipe release validation

This is DreamPipe source candidate 1.0.4. It has not been published or signed
with a release key in this workspace. The portable checks pass, but Android compilation,
Robolectric, lint and physical-device checks must pass before publishing an APK.

## Automated checks

Use the toolchain and commands in [BUILDING.md](BUILDING.md). CI runs portable regression checks,
both Android unit-test suites, Android lint, and the debug APK build. Its reports are retained as
artifacts. Android tests include service/player ownership, phone background policy and archive
round trips. The host fixtures exercise the actual transaction and codec code; they do not simulate
Android task management, foreground service policy, notifications or Media3 rendering.

Checks completed during implementation: portable regression and transaction checks; Java syntax
parsing; XML parsing and new resource-reference checks; workflow shell/Python parsing; original
license hashes and unchanged engine submodule pins. The Android SDK was unavailable and the Gradle
distribution download was blocked by the workspace network. The user's 1.0.2 diagnostics now
confirm debug APK assembly and zero lint errors after the resource correction. One queued-dialog
UI test was corrected; its rerun and physical-device acceptance remain pending.

## Device acceptance

Run on API 24 and a current Android phone, with at least one API 29+ device for scoped downloads.
Also check a wide display, rotation, dark mode, large text and TalkBack. Record device, Android
version, APK SHA-256, signing fingerprint and results with the release candidate.
New DreamPipe-specific screens currently use English fallback; inherited translations are retained.

| Area | Acceptance check |
| --- | --- |
| Identity | Launcher/Settings/install show DreamPipe and the new icon; package is `app.dreampipe` for release; About includes upstream attribution and the configured fork source URL. |
| Navigation | A fresh install shows Home, Playlists, History, Menu. Menu exposes exactly Subscriptions, Downloads, Settings and You. Choose/reorder 1–4 destinations, restart and rotate; Menu stays present. Reset restores the default three plus Menu. |
| Pages | Home, search, subscriptions, history, downloads and channel uploads use compact rows. At 5/10/20 row limits, Show more reveals buffered items before fetching another page; channels spanning a grid row count correctly. You has no Explore group. |
| Local library | Create, rename, search with Unicode, sort, save from a feed and the player, save into multiple playlists, reorder/remove entries, cancel dialogs, and delete a playlist. Changes persist after restarting. No account is required. |
| Merge | Merge multiple overlapping sources into a target. Target order/identity stay intact, new videos append once, and source removal follows the explicit checkbox. Test keeping and removing sources, including a failed write. Repeat a kept-source merge; no duplicates appear. |
| Interchange | Export all JSON, import into a fresh library, export again and compare contents. Check empty and Unicode playlists, M3U order/duration, Add copies, Merge matching, duplicate names, cancelled pickers and rotation while a picker is open. Unsupported/oversized files change nothing. |
| Storage failure | Simulate a failed write with a test build. The existing snapshot and file remain unchanged. Restore from a valid backup after a corrupt store; confirm the original unreadable bytes are preserved. |
| Local queue | Play/shuffle a playlist exceeding 300 entries. Previous/Next cross that boundary. At its end playback stops rather than jumping to an unrelated recommendation. Explicitly opening another video ends the local queue. |
| History | Signed-out playback appears in local history. Incognito and paused-history playback do not. Remove an entry and clear history; restarting preserves the changes. YouTube account history remains separate. |
| Background | During playback press Home, Back, turn the screen off, and swipe the task from Recents. Audio continues without an automatic mini-player/PiP. Test the next item and SponsorBlock while the task is absent. |
| Reopen | Tap the notification or open the retained session. The same session/position returns with one audio stream and restored video. Check repeated leave/reopen cycles, seeking, track changes and rotation. |
| Controls/recovery | Notification/headset Play/Pause/Next/Previous and Stop work. Stop releases the session. Test temporary network loss, retry, paused task removal and the local queue ending while the task is absent. |
| Downloads | Audio and video go into their separate DreamPipe destinations on API 24–28 and API 29+. Existing download playback/sharing still work. |
| Removed feature | No sleep-timer menu, preference or timeout remains. Waiting beyond the old timer duration does not stop audio. |

An ongoing started foreground service supports task dismissal; Android force-stop, process death,
reboot and vendor battery management can end playback. DreamPipe does not secretly restart audio
after those events. Validate Recents behavior on the devices you intend to support.

## Signing and publishing

Use your own release key and preserve it for every future update. The inherited NewTube updater
is disabled, so the APK cannot replace itself with an upstream build. No automatic update feed
is advertised for this first version.

The manual **DreamPipe signed release** workflow uses the GitHub `release` environment. Configure:

- `RELEASE_KEYSTORE_BASE64`: your own keystore encoded as base64.
- `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
- `RELEASE_CERT_SHA256`: environment variable containing the expected signing certificate SHA-256.

Here `RELEASE_CERT_SHA256` is a GitHub environment **variable**, not a secret. The workflow refuses
to proceed without it, verifies the built APK certificates, and produces checksums and build
provenance. By default it only saves artifacts. Its optional `create_draft` input requires a `v*`
tag and creates a draft release for review; it does not publish it. Add environment reviewers if
you want a separate signing approval in your fork.

Before uploading store metadata, capture fresh screenshots of the actual DreamPipe release
candidate. The inherited store screenshots were removed because they showed the old application.
Historical upstream documentation/images are reference material, not DreamPipe release artwork.
Keep `LICENSE`, `THIRD_PARTY_NOTICES.md` and every inherited file-level notice with the source.

The user built and installed 1.0.1. This revision still requires Android verification; see [DREAMPIPE_1.0.4.md](DREAMPIPE_1.0.4.md).
