# DreamPipe implementation notes

Base: NewTube 1.15.3, `f9771e61e94a7dbfb6f4b5962ff93f5e28a1e29a`.
MediaServiceCore remains `7c61fd331a118800e2bf1b70e3ef8fb6661de655` and SharedModules remains
`11b65d83fc527ecef3ecf2ee9e842a760f41c989`. No extractor or network-client changes are part of this fork.

The phone source set uses Java, XML, RecyclerView, Material Components and shared RxJava presenters.
Internal package/R namespaces remain stable; `applicationId` controls the independent DreamPipe install.

| Area | Main files |
| --- | --- |
| Navigation | `ui/browse/NavigationConfig`, `NavigationPreferences`, `MobileBrowseActivity` |
| Compact lists and row limits | `res/layout/item_video_card.xml`, `ui/common/{MobileGrid,RowLimitPolicy,VideoRowLimit,MoreRowsAdapter}` |
| Local library | `library/{LocalPlaylist,LocalVideo,LocalPlaylistStore,LocalHistoryStore,PlaylistArchive,LocalLibraryQuery,LocalLibraryPanel}` |
| Shared video actions | `library/{LocalPlaylistActions,VideoActions,LocalPlaybackLauncher}` |
| Local playback queue | `common/.../models/playback/DevicePlaylistQueue`, `VideoLoaderController`, `SuggestionsController` |
| Task-independent audio | `ui/playback/{MobilePlaybackService,BackgroundPlaybackView,MobilePlaybackActivity}` |
| Background presenter seam | `PlaybackView.isHeadless`, `BasePresenter`, `PlaybackPresenter` |
| Downloads | `downloads/{DownloadStorage,PlaylistDownloadDialog}`; the dialog reuses existing format/queue APIs |
| Watch-page gestures | `ui/playback/{DreamWatchPage,WatchPagesLayout,WatchSwipeGesture}`; tabs are outside clipped scrolling content |
| Icon action rows | `ui/common/DreamDialogs` |

Local library snapshots are immutable. One serial worker validates and atomically writes a transaction
before publishing it to UI listeners. Search is debounced and evaluated on a separate worker; stale
results are discarded after a query, destination or snapshot changes. Queries and export/import never use YouTube playlist IDs as
local identities. The session queue is independent of the legacy 50/300-item transient queue and
does not fall through to unrelated YouTube recommendations at its end.

An ongoing playback window transfers its existing controller/player to a service-owned PlaybackView
before releasing its UI. The service keeps the same shared queue, recovery and SponsorBlock pipeline.
Reopening adopts the existing player; UI/subtitle/benchmark listeners and surfaces are detached on
handoff. Watch metadata and chapters rebind without reinitializing the adopted engine. Explicit Stop releases the owned engine and notification. Task dismissal does not request
a fresh background service start. Process death/force-stop does not silently restart playback.

The removed sleep timer's serialized preference slot remains reserved to avoid shifting existing
settings. All runtime timeout checks and timer menu/settings entries are removed.

## Scope beyond presentation

Local storage, download destinations, local queue routing, and service lifecycle ownership are
app-side changes outside pure view rendering. The headless presenter seam and local queue hooks
touch shared application playback code in `common/`. Engine submodule contents and pointers remain
unchanged. This is deliberate; disguising these changes as UI-only would be misleading.

Original UI code and original D-and-play artwork were authored for DreamPipe. PipePipe's
published screenshots/features were used as interaction references; no PipePipe source or assets
were copied. MIT was retained for DreamPipe's additions, with inherited notices preserved.

## 1.0.1 maintenance boundary

`ui/playback/DreamWatchPage` composes the original player with native AppBarLayout/CoordinatorLayout
scrolling and bottom tabs. The selected tab is a phone UI preference; per-page scroll positions also survive window recreation. Controls, controller delegates, metadata IDs, comment/reply paging and
recommendation loading remain in their existing implementations. `MobilePlaybackActivity` has small
hooks for page composition, save state and the unified picker. The old comment sheet has an explicit
embedded-page mode; fullscreen/PiP still suspend it through the existing lifecycle methods.

`library/PlaylistSaveDialog` renders explicit clickable thumbnail rows. `AccountPlaylistClient` is
its single adapter to existing account playlist APIs. The legacy `LocalPlaylistActions.showSave`
entry point delegates here; no extractor/account implementation is copied into the UI. Local and
account identities stay separate even if a returned string or title happens to match.

`LocalListAdapter` is private to the local library. Right-side handles use ItemTouchHelper and stable
entry identities; sorting/filtering remain projections, so dragging is available in unfiltered Manual
order. Array order and optional `thumbnailVideoId` persist in the existing version-one JSON format.
Old archives remain readable. Atomic move/reorder transactions preserve concurrent unrelated entries.

The new shared application hooks are `DevicePlaylistQueue.applyPlaybackMode`, the single read in
`VideoLoaderController.getPlaybackMode`, and the explicit-choice notification in `PlayerData`.
They make a list action advance through its chosen queue without rewriting the global setting at
launch. A later explicit repeat/shuffle choice takes precedence. This is application playback policy,
not a UI-only change. Neither engine module's tracked contents nor its commit pin changed.

Upstream merges can still conflict in the activity or watch XML. Keep DreamPipe composition/picker
hooks and merge upstream controls, controllers, metadata handling and extraction fixes normally;
there is no claim that arbitrary future upstream merges will be conflict-free. The full patch retains
the original fork point so these additions can be reviewed separately from engine updates.

The 1.0.2 UI changes are mapped in DREAMPIPE_1.0.2.md. Menu reserves a fifth bottom-bar slot;
configurable destinations are capped at four. Library selection keeps stable checked identities,
uses atomic bulk mutations and offers optional source removal in the merge confirmation. Batch
download preparation is an Activity-owned caller of existing APIs; no engine/network-client or
download-service implementation changes were needed.
