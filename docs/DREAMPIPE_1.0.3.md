# DreamPipe 1.0.3 — matching bottom bars

VersionName 1.0.3 / versionCode 10003. Application ID and local-data formats are unchanged.

The watch page now uses the same `BottomNavigationView` component and
`DreamPipeBottomNavigation` style as Home. Both use the component's native row height, 24dp icons,
the existing 11sp labels, identical checked/unchecked tints and `mobile_color_background`.
There is no independently sized watch strip or separate selected-tab underline.

The component owns the bottom navigation inset on both screens; the playback activity retains
the existing top/side-safe insets and opts out of adding the same bottom inset a second time.
Fullscreen still uses the existing edge-to-edge path and hides the watch navigation. The bar
remains outside the scrolling coordinator. Comments, next videos, description, horizontal swipes,
scroll restoration and saved page IDs keep their existing behavior.

The deep lavender menu/dialog palette, sheet themes and color resources are unchanged.
No engine, extractor, media session, shared playback controller or dependency changes are made.

| Responsibility | Files |
| --- | --- |
| Shared geometry, background, icon/text styling and inset policy | `res/values/styles_mobile.xml`, `activity_mobile_browse.xml`, `activity_mobile_playback.xml` |
| Watch destinations and stable menu IDs | `res/menu/dreampipe_watch_pages.xml` |
| Existing page selection/swipes, saved state and in-place theme refresh | `ui/playback/DreamWatchPage.java` |
| Single bottom-inset ownership | Small UI override and comment in `ui/playback/MobilePlaybackActivity.java` |
| Selection, restoration, fullscreen and fixed-bar regression coverage | `DreamWatchPageTest.java` |

Two Android parity cases compare actual Home/watch bar measurements and colors in light/dark
themes. They dispatch navigation insets of 0, 24 and 48 pixels, then reset to zero to check that
insets are neither doubled nor accumulated. They also check selected/unselected icon/text colors
and theme refresh. Existing watch-page behavior cases are adapted to the shared native component.
The Android rerun remains pending on the user's SDK; syntax/resource checks are not substitutes.

Device acceptance: compare Home and watch bars on Titan 2 Elite in both themes, with its normal
font/display settings and larger text; verify both gesture and three-button navigation if used.
Scroll the video away, swipe/click all pages, rotate fullscreen and back, and change theme while
playing. Both bars should have the same content height, navigation-area spacing and colors;
menus should keep the approved deep lavender surface.
