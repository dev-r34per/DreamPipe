# DreamPipe 1.0.1 review and device checks

This revision addresses the reported watch-page, save-picker, playlist actions and title problems.
It preserves package IDs and app storage, and increments versionCode to 10001. Build on the same
machine/signing key to update the installed debug app without removing its playlists.

| Requirement | Implementation |
| --- | --- |
| Original icon with a small play-icon inspiration | `images/dreampipe-icon.svg`, phone launcher vectors/mipmaps and store icon |
| Scroll video out of view; bottom detail tabs | `DreamWatchPage`, watch/description layouts, embedded `CommentsPanel`/`CommentsPanelLayout` mode |
| Description behavior | `DescriptionText`: selectable text, web links and seekable timestamps |
| Correct destination title | `MobileBrowseActivity`: refresh chrome after the current section changes |
| Three feed actions | `VideoActions`: background, save to playlist, share |
| Save locally or to account playlists | `PlaylistSaveDialog` + `AccountPlaylistClient`, shared by feed and watch page |
| Create without opening the new playlist | `LocalLibraryPanel.create` |
| Drag order and accessible order controls | `LocalListAdapter` + ItemTouchHelper; store operations anchor by identity |
| Four video actions; separate thumbnail/move window | `LocalLibraryPanel.videoActions` / `moreVideoActions` |
| Correct list and single-video playback | `LocalPlaybackLauncher`, session queue policy and the shared mode seam |
| Chosen thumbnail and persistent manual order | `LocalPlaylist`, `LocalPlaylistStore`, compatible optional JSON field |

The library row menu contains Rename, Merge, Export and Delete playlist. The detail overflow adds
copying entries to another local playlist and confirmed removal of videos watched to at least 90%.
Duplicates are prevented by the local model on save/import/merge; there is no decorative no-op
“remove duplicates” action. PipePipe's public descriptions are interaction references, not a copied
implementation or a guarantee that every menu in every PipePipe release is reproduced.

## Verified here

- 82 portable model/archive/query/queue/launcher checks and 38 actual storage-transaction checks pass.
- The host launcher checks run the actual launcher against small Android/presenter boundary fixtures;
  they verify dispatched requests and queue policy, not decoded media or service lifecycle.
- Java syntax and resource XML/reference checks pass. These do not replace Android compilation.
- Original MIT license hashes and both engine commit pins are preserved. No PipePipe source/assets
  were imported. The original D-and-play icon was drawn in vectors and rasterized for legacy sizes.

Eight new Robolectric UI tests cover existing-list selection, correct local/account routing, failed
account writes, tab navigation, fullscreen restoration, saved tab state, header scrolling and
seekable description links. They use the upstream SDK 28 / Conscrypt-OFF configuration. They have
not run in this workspace because the Android SDK/toolchain is unavailable.

## Device acceptance

1. Signed out: Home long press has exactly Background / Save to playlist / Share. Save to an
   existing local playlist and check membership immediately, close/reopen the picker, then remove it.
   Create a new playlist from the same picker; it contains the video and has a thumbnail.
2. Sign in: choose a writable account playlist and confirm the video appears on YouTube. Local
   destinations still work if the account request fails. Two playlists with the same name are
   distinguished by “On device” / “YouTube”. Read-only/public playlists are not save destinations.
3. Local playlists: the New playlist button keeps the list open. In Sort → Manual order, drag the
   right handle, restart, and verify the order. Repeat within a playlist; use TalkBack/keyboard
   position controls as well. Sorting/searching must not rewrite manual order.
4. In a local playlist: long press has Background / Delete / Share / More actions. Cancel then
   confirm deletion. More actions opens only Thumbnail / Move. Set a cover, reorder entries, export
   JSON and import it; verify cover/order. Move to a playlist already containing the same video and
   confirm it exists once in the destination and is removed from the source.
5. With three short videos: Background plays the entire list in order; Play all opens the first
   entry and advances; Shuffle contains every entry once. Use a global Pause-after-each-video
   setting first: it must not make Play all stop after the first entry. An individual video's
   Background action plays only that video. Moving/deleting saved entries does not rewrite an
   already playing session queue.
6. Watch a video: scroll Next videos, Comments and Description to move the header out of view.
   Tabs remain at the bottom; pulling back brings the video back without restarting it. Comments
   support Top/Newest, replies and paging. Back leaves a replies page before closing comments.
   Descriptions support copying text, web links and timestamps. Change videos and verify no old
   comments or description remain.
7. Rotate to fullscreen and back, enter/leave PiP, change theme, lock/unlock and reopen background
   audio. The chosen tab restores, controls remain usable and a hidden header must not steal scroll
   touches for seeking. Dismissing Recents while background audio plays needs Titan-specific testing.
8. Local playlists → Home must immediately display Home in the title bar. Repeat with History,
   overflow destinations, playlist details and Back. Check the new icon in adaptive and themed modes.

The full build command runs common/app unit tests, Android lint and APK assembly. Do not publish
this revision as device-tested until these checks and the acceptance cases have passed.

The first user build reached APK assembly but failed lint (118 errors). The explicit haptic API
guards were confirmed by a rerun that reports 112 errors. Complete diagnostics identify 78 unused
Leanback sample translations, 20 AppCompat tint attributes, 11 internal Material helper references
and three constant checks. The lint correction removes the unused demo strings, uses `app:tint`,
replaces the comments back helper with app-owned public-API animation, and guards the named
network capability on API 30+. The two PiP restore checks are narrowly annotated: the private field
holds the exact platform-provided orientation, with its sentinel excluded before use.

Six regression tests were added for animation cancellation/commit/interruption/embedded behavior
and older-Android metering. Their execution and the corrected lint run remain pending on the user
machine. `python3 tools/dreampipe-diagnostics.py` captures every reported error if further failures
appear. There is no lint baseline or blanket suppression. Engine/extractor modules are unchanged;
the version guard touches an application network-policy helper and preserves its existing policy.
