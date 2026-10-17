# DreamPipe 1.0.2 — navigation and playlist workflow

Application ID stays `app.dreampipe` (`app.dreampipe.debug` for debug), versionCode 10002.
The user built and installed 1.0.1. User diagnostics confirm 1.0.2 APK assembly and zero lint
errors after the resource correction. One UI test required queued-dialog synchronization; its
corrected rerun and physical-device acceptance remain pending.

Resource correction: the first 1.0.2 build reported a duplicate `dreampipe_done` string. The duplicate
was removed; the build/install helper now checks value-resource uniqueness before Gradle, with five
portable checker regressions covering duplicates, arrays and legitimate source/qualifier overrides.

Test synchronization correction: `LocalLibraryPanelTest` now processes queued UI button callbacks
before waiting for store/query work and before inspecting the next merge dialog. It also asserts
that Continue closes the destination picker and opens the source picker. This changes test timing
and assertions only; production merge behavior is unchanged.

| User-facing behavior | Owning files |
| --- | --- |
| Playlists label; permanent bottom Menu with exactly Subscriptions, Downloads, Settings, You and left icons | `ui/browse/{MobileBrowseActivity,NavigationConfig,NavigationPreferences}`, `ui/common/DreamDialogs` |
| Four configurable destinations plus reserved Menu; previous order retained up to this capacity | `NavigationConfig`, `strings_dreampipe.xml` |
| Fixed watch tabs; scrolling pages cannot paint over them | `activity_mobile_playback.xml`, existing `DreamWatchPage` |
| Horizontal page changes with vertical direction lock, distance threshold, edge/long-press/selection/slider protection and RTL support | `ui/playback/{WatchPagesLayout,WatchSwipeGesture}` |
| Explicit Select / Import / Playlist actions, checkmarks, selected count, Select shown, adaptive Rename/Merge/Move, Download, Export and confirmed Delete | `library/{LocalLibraryPanel,LocalListAdapter}`, local library layouts |
| Choose the merge destination and sources; Remove source playlists after merging is checked by default and can be unchecked | `LocalLibraryPanel`, `LocalPlaylistStore.merge` |
| Atomic source removal, bulk deletion and bulk video moves | `LocalPlaylistStore` |
| One audio/video quality choice for selected entries/playlists; unique videos, available quality capped at the chosen height, active/exact existing downloads skipped, progress and Stop adding | `downloads/PlaylistDownloadDialog` |
| Violet-tinted dialog/sheet surfaces, rounded alerts, accent checkmarks and readable selection states | phone `styles_mobile.xml`, light/night `colors_mobile.xml` |

The single-selection action is Rename for a playlist. It becomes Merge when multiple playlists
are selected, and Move for selected entries inside a playlist. Exports of multiple playlists use
one JSON archive. One playlist or selected-video collection can also use M3U. Selection survives
recreation by stable identities; changing screens exits selection. Filtering retains checked items,
with a visible total and names in delete confirmation. Select shown affects only rows currently
revealed by the filter/row limit. Video long-press actions keep their existing behavior outside
selection mode. Playlist long-press enters selection.

Merge retains the destination's identity, name, cover and existing entry order. New entries append
once in source-library order. Source removal and the destination write commit together; failed
writes preserve every original. Choosing to keep sources performs a non-destructive merge. Local
storage/archive schema and JSON import/export compatibility are unchanged.

## Upstream merge boundaries

This revision changes no engine/extractor module, decoder, Media3 session, network client,
background-player ownership or shared playback presenter. The four added production helpers own
UI policy/composition only. The batch-download dialog calls the same existing format-info API,
`DownloadOptions`, registry and `MobileDownloadService.enqueue` used by the individual picker.
Live/unavailable videos are reported as unavailable. It adds no extractor or download-job logic.
Preparing remaining formats belongs to the Activity: Stop adding or Activity destruction cancels
preparation; jobs already queued continue under the existing service. It does not promise that an
unprepared batch resumes after process death. Previously implemented playback/service seams are
still documented in DREAMPIPE_IMPLEMENTATION.md; they are unchanged here.

Keep DreamPipe-owned helpers/resources when merging a NewTube release. Review the small hooks in
Browse, watch-page XML and the phone theme against upstream UI changes. Internal package/R names
and pinned dependencies remain stable; no new dependency is introduced.

## Validation and device acceptance

146 portable checks pass (93 policy/model/queue/launcher, 53 store transactions/recovery). Added
cases cover direction lock, cancellation/short/edge/long-press gestures, reserved navigation
capacity, optional source retention/removal, merge rollback, bulk deletion and two-sided moves.
591 Java sources parse; 297 app/common resource XML files parse. These are not Android type checks.
Ten Android/JVM tests are added for selection/recycled checkmarks, actual Menu/merge/delete dialogs,
protected swiping and download-quality choices. The existing watch geometry test checks fixed-tab
containment. These Android-facing tests and lint require the user's Android SDK and have not been
run in this workspace.

Before publishing, test on Titan 2 Elite, with large text and both themes:

1. Start on Home: Home / Playlists / History / Menu. Top-right overflow is absent. Menu has exactly
   the four named entries, working routes and icons. Menu highlights when a destination is not
   pinned. Customize/reset the other tabs in Settings; Menu remains present after restart.
2. Scroll each watch page until the video disappears; tabs remain visible and clickable. Swipe
   left/right between pages, return vertically to the video, seek, select/copy description text,
   open a link, long-press a row, use replies/sorting, and use screen-edge Back. Check both end
   tabs, cancelled/short/diagonal gestures, fullscreen/PiP and returning to portrait.
3. Long-press a playlist, check others, filter and Select shown. Rename one; merge several into a
   chosen destination. Test both source-removal choices and overlapping/empty playlists. Confirm
   names, cover, order, restart persistence and unchanged downloaded files.
4. Cancel deletion; then delete checked playlists/entries and verify unselected items remain.
   Move several videos, including an existing destination entry and the source cover video.
5. Export selected playlists as JSON and entries as JSON/M3U; cancel the picker and retry. Import
   each archive and verify names, order, covers and duplicate handling.
6. Queue selected videos/playlists as audio or capped video; inspect separate audio/video folders,
   duplicates, unavailable/live entries, progress, Stop adding, rotation and queued-job completion.
7. Inspect prompts, pickers, menus, checkboxes and sheets in light/dark modes. Check text contrast,
   font scaling, dialog scrolling and TalkBack labels/check states.

`bash tools/dreampipe-build-install.sh` reads the app version, runs common/app unit tests, lint and
APK assembly, then pins the USB device serial for installation on user 0. A failure stops the
install and writes the complete `~/.down/DreamPipe-1.0.2-diagnostics.txt` report. No checks are skipped.
