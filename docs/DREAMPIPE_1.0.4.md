# DreamPipe 1.0.4 — menu palette, playlist header and task removal

VersionName 1.0.4 / versionCode 10004. Application ID and stored playlists are unchanged.

## Behavior and ownership

The bottom navigation Menu sheet now uses the navigation bar background and inactive icon/text
colors. This is scoped to `DreamDialogs.navigation` and a dedicated bottom-sheet theme; generic
action sheets and dialogs keep the approved lavender surfaces.

Playlist detail headers use a native `AppBarLayout`: the name, count and playback buttons scroll
away, while search, management and selection controls stay accessible. Scrolling back reveals the
header. Opening another playlist expands it; the toolbar still carries the playlist name.
The RecyclerView, drag handles, item IDs, filtering and selection remain in their existing owners.

The playback changes extend beyond visual UI into the phone service/activity lifecycle. The
extractor/engine modules, shared controllers and dependencies are unchanged:

- Start the media foreground service while the app is visible and post its preparing notification
  immediately, before metadata/artwork work. The normal media notification replaces the same ID.
  Preparing playback includes an explicit Stop action.
- Automatic notification cancellation during source changes no longer calls `stopSelf()` on a live
  player. A paused bound player also keeps its started lifetime until an explicit stop/task cleanup.
  Keeping only an Activity binding was insufficient after that binding disappeared.
- Acquire the existing presenter/controller in `onTaskRemoved` and as the last window unbinds,
  before relying on Activity destruction. Repeated ownership transfer is idempotent.
- Ignore stale notification callbacks after player replacement. Release invalidates the notification
  generation before detaching it, so a previous callback cannot demote/stop a newer session.
- Explicit Stop/dismissal still pauses and releases playback. Playback is not restarted after a
  process kill or force-stop; `START_NOT_STICKY` is retained. Vendor task-removal policies that kill
  even a foreground process require physical-device evidence and are not claimed bypassed.

## File mapping

| Responsibility | Files |
| --- | --- |
| Scoped navigation-menu palette and rows | `ui/common/DreamDialogs`, `ui/browse/MobileBrowseActivity`, `styles_mobile.xml` |
| Scroll-away playlist detail header | `view_local_library.xml`, `library/LocalLibraryPanel` |
| Started/foreground lifetime and task ownership | `ui/playback/{MobilePlaybackActivity,MobilePlaybackService}` |
| Startup/Stop notification labels | `strings_dreampipe.xml` |
| Callback, Stop, menu/theme and header regressions | `BackgroundPlaybackServiceOwnershipTest`, `DreamDialogsTest`, `LocalLibraryPanelTest` |
| Scoped playback logs/exit evidence | `tools/dreampipe-playback-diagnostics.sh` |

Eight Android regression cases were added: five service lifecycle/notification cases, two menu
palette cases and one native header-collapse case. They are pending on the user's Android SDK.
Java syntax/resource checks do not substitute for that run. Portable resource checks and update
installer fixtures are recorded in DREAMPIPE_VERIFICATION.md.

## Device acceptance

1. Open the bottom Menu in both themes; compare it with the navigation bar. Open a playlist action
   dialog/player sheet and verify its approved lavender surface is retained.
2. Open a playlist, scroll away the header and scroll back; test short/empty lists, larger text,
   manual row dragging, search, selection, rotation and switching to another playlist.
3. Play a normal video and a local playlist, background the app, then remove its task from Recents.
   Confirm audio, notification controls and playlist advance continue. Reopen via the notification
   and via the launcher. Repeat around loading/buffering and source/queue changes.
4. Test Pause, notification dismissal and Stop during preparation; stopping must not resume audio.
5. If playback still stops on Titan 2 Elite, reproduce once, then run
   `bash tools/dreampipe-playback-diagnostics.sh`. It writes scoped lifecycle, service and process-exit
   evidence to `~/.down/DreamPipe-1.0.4-playback.txt` without changing phone settings or clearing logs.

Android's started/bound service lifecycle is documented at
https://developer.android.com/develop/background-work/services and the corresponding task-removal
guidance at https://developer.android.com/media/media3/session/background-playback . The existing
custom service is retained; this update does not migrate playback to MediaSessionService.
