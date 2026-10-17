# DreamPipe

A touch-first Android YouTube client with a local library and background audio, built on NewTube and SmartTube.

Fork of NewTube by aleixrodriala. Original licensed under MIT.

## What changes

- **Home, Playlists and History** are the default bottom tabs, followed by a permanent **Menu** for Subscriptions, Downloads, Settings and You. Choose and reorder up to four destinations in Settings → General → Bottom navigation.
- **Compact video lists** put thumbnails beside titles and metadata. Optional row limits reveal additional rows with Show more; videos and continuation state are kept.
- **Local playlists** work without an account. Create, rename, search, sort, reorder entries, save videos to several playlists, and merge playlists with a choice to keep or remove their sources. Select playlists or entries for bulk export, download, deletion or moving.
- **Import and export** complete DreamPipe JSON backups or individual M3U playlists containing public YouTube links. Imports are previewed before saving. Merges preserve existing order and skip duplicate videos.
- **Local history** works while signed out and can be paused or cleared independently of YouTube account history.
- **Background audio** is the default when leaving the app. Back from the player returns to Browse with audio. An ongoing session is handed to the foreground service when the Android task is dismissed, and reopening reuses that player.
- Downloads use separate **Music/DreamPipe** and **Movies/DreamPipe** collections on Android 10+, or separate Audio/Video directories on Android 7–9.
- The You page retains account/personal controls without an Explore section. The sleep timer has been removed.

## Build

Android 7.0+ (API 24) is supported. Use JDK 21 and Android SDK 37; the Gradle wrapper and Android Gradle Plugin remain pinned to the upstream toolchain.

The installed application ID is `app.dreampipe`; debug builds without a release keystore use `app.dreampipe.debug`.

```bash
./gradlew :smarttubetv:testStmobileDebugUnitTest :common:testDebugUnitTest :smarttubetv:lintStmobileDebug :smarttubetv:assembleStmobileDebug
```

APKs: `smarttubetv/build/outputs/renamed_apks/stmobileDebug/DreamPipe_1.0.4_<abi>.apk`.

See [building](docs/BUILDING.md), [release validation](docs/DREAMPIPE_RELEASE.md), [playlist format](docs/DREAMPIPE_PLAYLIST_FORMAT.md), and [implementation notes](docs/DREAMPIPE_IMPLEMENTATION.md).

## Source and updates

Set `-PdreampipeRepositoryUrl=https://github.com/YOUR_NAME/DreamPipe` when building for your own public repository. CI infers the URL from `GITHUB_REPOSITORY`. Without a configured fork URL, About clearly identifies the NewTube link as **upstream source**.

The inherited NewTube update feed is disabled, including manual update checks. DreamPipe is currently updated by manually installing a newer APK signed with your own key.

## Attribution and license

DreamPipe's original additions are MIT-licensed. The original [MIT LICENSE](LICENSE), copyright notices, vendored licenses and [third-party notices](THIRD_PARTY_NOTICES.md) are preserved; third-party files retain their own licenses. Original DreamPipe code © 2026 DreamPipe contributors.

The UI uses independently implemented interaction patterns inspired by PipePipe. No PipePipe source files, implementation code, icons or other assets were imported. DreamPipe's coral D-and-play launcher artwork is original; its vector source is [here](images/dreampipe-icon.svg).

DreamPipe is independent and is not affiliated with or endorsed by NewTube, SmartTube, PipePipe, Google or YouTube. YouTube and Android are trademarks of Google LLC. Historical NewTube documentation is retained under [docs/upstream](docs/upstream/).
