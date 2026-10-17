# DreamPipe changelog

## 1.0.4

- Match the opened bottom Menu to the navigation bar while keeping other menus lavender.
- Scroll playlist detail headers away while keeping search and selection controls accessible.
- Keep playback service lifetime through task removal and automatic notification cancellation.
- Promote foreground playback before metadata loads, preserve task ownership, reject stale callbacks and keep an explicit Stop action.

## 1.0.3

- Match watch-page bottom navigation to Home using one native component and shared style.
- Share bar height, label/icon colors and bottom navigation insets while preserving lavender menus.
- Keep watch-page swipes, fixed positioning, fullscreen and page restoration.

## 1.0.2 — navigation and library workflow

- Playlists label; permanent bottom Menu with Subscriptions, Downloads, Settings and You, with icons. Four configurable destinations leave a reserved Menu slot.
- Watch tabs stay outside clipped scrolling content. Deliberate horizontal page swipes preserve vertical scrolling, player/seek gestures, text selection and screen-edge navigation.
- Explicit Select / Import / Playlist actions replace library kebabs. Selection checkboxes drive batch merge, export, download, removal and video moves.
- Merge asks for a destination and checked sources, with an explicit option to remove sources. The merge and removal share one atomic write.
- Batch downloads use existing format options and the existing download service, with one quality choice, deduplication, progress and cancellation of preparation.
- Violet-tinted dialog and sheet surfaces, rounded alerts, accent checkmarks and clear selection states in light/dark themes.

Validation: 146 portable checks pass. Android regression tests and the 1.0.2 device checks remain pending. See [revision notes](docs/DREAMPIPE_1.0.2.md).

## 1.0.1 — watch page and playlist corrections

- Original coral D-and-play launcher icon, including adaptive and monochrome variants.
- Scrollable video header with persistent Comments / Next videos / Description tabs. Comments retain sorting, replies, paging and scroll state; descriptions have links and seekable timestamps.
- One thumbnail picker for local and writable account playlists. Local saving works while signed out; a row saves immediately and reports a failed write accurately.
- Feed menu: Play in background, Save to playlist, Share. Destination titles refresh when leaving the local library.
- Manual playlist/video ordering with right-side drag handles and accessible position actions. Creating a playlist stays in the library.
- Playlist header: background, play all, right-aligned shuffle icon. Playback starts a complete session queue; a video's background action plays only that video.
- Playlist video menus: background, confirmed deletion, share, then a separate thumbnail/move menu. Chosen covers survive JSON export/import; moves commit both sides together.

Validation: 120 portable checks pass. New Android UI tests, lint and physical-device acceptance are pending for this revision. See [the revision notes](docs/DREAMPIPE_1.0.1.md).

## 1.0.0 — initial source candidate

- **DreamPipe identity.** Independent package, original icon, clear upstream attribution, and isolated update behavior.
- **Local library.** Searchable and sortable playlists; atomic saving; ordered merges; JSON and M3U import/export; local history.
- **Navigation.** Home, Local playlists and History by default, configurable tabs, overflow destinations, compact video rows, and no Explore section in You.
- **Background audio.** Audio-first leaving behavior and a foreground-service handoff when an ongoing playback task is dismissed.
- **Removed sleep timer.** Both the UI and the runtime timeout are removed; the old serialized preference slot stays reserved.

See docs/DREAMPIPE_RELEASE.md for validation status. This source candidate has not yet been built or checked on an Android device in this workspace.
