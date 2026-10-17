# DreamPipe verification — 2026-10-17

Current source candidate: **1.0.4**. Upstream fork point: `f9771e61e94a7dbfb6f4b5962ff93f5e28a1e29a`.

| Check | Result |
| --- | --- |
| Portable policy/model/archive/query/queue/launcher checks | 93 passed, including the actual swipe-direction policy and reserved Menu capacity. |
| Serial playlist/history transactions and recovery | 53 passed, including optional merge-source removal, failed-merge rollback, bulk deletion, bulk moves, covers/order and existing corruption recovery. |
| Java syntax | 591 app/common production and test files parsed. This is not Android compilation. |
| XML | 298 app/common resource files parsed; new resource references checked. |
| Duplicate Android values | Removed a duplicate `dreampipe_done` reported by the user's 1.0.2 resource merge. All 79 values files checked by source set/configuration; five checker regressions passed. The build/install helper now runs this check before Gradle. |
| Previous Android release | User confirms 1.0.1 built and installed. Test counts and a final report were not supplied. Historical lint corrections remain in the source and in DREAMPIPE_1.0.1.md. |
| Android 1.0.2 tests, lint and packaging | User diagnostics confirm APK assembly and zero lint errors after the resource correction. One test, `LocalLibraryPanelTest.longPressAndTapSelectPlaylistsAndMergeCanKeepSources`, failed while checking the next dialog before queued UI callbacks ran. The test now drains the UI queue before store/query barriers and after Continue, and asserts the destination dialog closed and source picker opened. The corrected Android test rerun remains pending. |
| Matching bottom bars in 1.0.3 | Same native component and shared style; menu/dialog color files unchanged. Two Android light/dark parity cases added for measured height, navigation insets, background and selected/unselected colors. Android rerun and device acceptance remain pending; cases are in DREAMPIPE_1.0.3.md. |
| 1.0.4 menu/header/playback lifecycle | Scoped navigation Menu palette, scroll-away playlist header, early foreground/start lifetime, task ownership and notification-generation guards implemented. Eight Android cases added; rerun and Titan Recents acceptance remain pending. The read-only playback report distinguishes app lifecycle events from system exit reasons. |

Portable tests compile the actual DreamPipe policy/model/store/launcher Java using small Android
boundary fixtures. They do not decode media, inflate Android views or simulate system lifecycle.
This workspace has no Android SDK/toolchain. The build/install helper keeps Android tests and lint
mandatory, and collects complete diagnostics if they fail.

The source/archive installer validates accepted original hashes before writing, preserves SDK
configuration and app data, and backs up replaced files. Its checks cover previous delivered
source revisions, repeat application, refusal of unexpected edits and rollback after early/late
write failures. The final package validation is recorded with the delivered checksums.

No engine/extractor changes in 1.0.2–1.0.4. The 1.0.4 playback fix changes the phone service/activity lifecycle, beyond visual UI. Pinned modules remain:

- MediaServiceCore: `7c61fd331a118800e2bf1b70e3ef8fb6661de655`.
- SharedModules: `11b65d83fc527ecef3ecf2ee9e842a760f41c989`.

Root MIT LICENSE and both top-level module LICENSE files retain SHA-256
`8e3cfc52890feae676fa35c38bf939d9d386a341d7c21a323937b5a9f87dc70d`.
Inherited notices remain applicable. No PipePipe code/assets were imported. No APK was signed,
published or installed by this workspace. Release signing and device acceptance remain with the user.
