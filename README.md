# Playcial

Foundation build. Push to GitHub, the included Actions workflow
(`.github/workflows/android-build.yml`) builds a debug APK automatically —
download it from the run's Artifacts tab and install on your phone.

## What's real and working in this slice
- MediaStore video scanning (no placeholder data) — `MediaRepository`
- Home screen: Videos/Folders segmented tabs, grid/list toggle, sort, search filter, swipe-to-refresh, empty state
- Video card: thumbnail, duration, resolution badge, size, folder, favorite icon, progress bar, long-press hook
- Media3 ExoPlayer screen with the orientation bug actually fixed: orientation is
  set from `VideoSize` (post-rotation-metadata) once ExoPlayer reports it —
  portrait stays portrait, landscape stays landscape, square stays free
- Gesture controls: double-tap seek (±10s), swipe-left = brightness, swipe-right = volume
- Custom rounded dialog system (`PlaycialDialog`) — no default AlertDialog anywhere
- Custom animated bottom sheet (`ActionBottomSheet`) for all contextual menus
- Light/dark theme wired to brand color `#00AAFF`
- Clean architecture: MVVM + Hilt DI + Repository pattern + Coroutines/Flow

## Not built yet (next slices)
Folder screen, favorites/hidden/locked/pinned state persistence (needs Room),
recycle bin, rename/move/copy/share actions (menu items exist, not wired),
PiP + background playback, sleep timer, AB repeat, subtitle/audio track UI,
duplicate finder, storage analyzer, vault, statistics, custom themes engine,
tablet/foldable layouts.

## Why it's split this way
The full spec is a multi-week app. Building it as one slice would either be
95% placeholder code or fail to compile with no way for you to debug it from
a phone. Each future message adds one working vertical slice on top of this,
so you always have an installable APK from CI.
