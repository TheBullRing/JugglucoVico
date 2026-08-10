# Juggluco-Portrait-No-widgets — Implementation Plan

## Purpose

This repository is a **clean fork** of `Juggluco-Portrait` that merges **only**
the Vico/Compose chart migration and the portrait-orientation support, while
deliberately **excluding** all widget, alarm lock-screen, snooze, and
`PermanentGlucoseNotification` additions that exist in `Juggluco-Portrait`.

The canonical implementation detail lives in
[`../Juggluco-Portrait/IMPLEMENTATION_PLAN.md`](../Juggluco-Portrait/IMPLEMENTATION_PLAN.md).
Refer to it for the full phase history, architecture overview, and session log.

---

## What Was Migrated

| Area | Files |
|------|-------|
| Vico/Compose chart (9 Kotlin files) | `Common/src/mobile/java/tk/glucodata/chart/` |
| Unit tests | `Common/src/test/java/tk/glucodata/chart/` |
| Portrait menu layout | `Common/src/mobile/res/layout-port/menus.xml` |
| Portrait BlueSensor layout | `Common/src/mobile/res/layout-port/bluesensor.xml` |
| Landscape menu (0dp fix) | `Common/src/mobile/res/layout/menus.xml` |
| View IDs | `Common/src/mobile/res/values/ids.xml` |
| Orientation + Vico strings (EN) | `Common/src/main/res/values/strings.xml` |
| Orientation strings (ES) | `Common/src/main/res/values-es/strings.xml` |
| `Applic.java` | `useVicoChart()` / `setUseVicoChart()` runtime switcher |
| `MainActivity.java` | `startdisplay()` Vico branch + `showVicoScanResult()` |
| `GlucoseCurve.java` | `summaryready()` Vico notification, `setgeo()`, `removeviews()`, portrait search rows |
| `Menus.java` | `FrameLayout wrapper`, `show(act, tab)` overload, portrait-aware inflater |
| `Settings.java` | Orientation spinner, vicoToggle, portrait rows, `settinglayout` public static |
| `Natives.java` | `historyfromSensorptr` JNI declaration |
| `g.cpp` | `historyfromSensorptr` C++ implementation |
| `build.gradle` | Compose plugin, `buildFeatures { compose }`, Vico deps |

## What Was Deliberately Excluded

- `AlarmLockScreenActivity.java`, `AlarmSnooze.java`, `LockScreenWallpaper.java`
- `PermanentGlucoseNotification.java`, `SnoozeReceiver.java`
- `ChartGlucoseWidget.java`, `GlucoseDeltaTimeWidget.java`, `GlucoseTrendWidget.java`, `GlucoseTrendDeltaWidget.java`
- Widget XML layouts (`main/res/layout/chart_glucose_widget*.xml`, etc.)
- Widget `res/xml/` app-widget info files
- Snooze/widget strings in `strings.xml`
- `Natives.getWidgetGraphBitmap()` JNI
- `Natives.setalarmSoundType()` / `Natives.getalarmSoundType()` JNI
- Lock-screen wallpaper drawable and layout
- Snooze / widget / lock-screen helpers in `Settings.java`

---

## Session Log

| Date | Summary |
|------|---------|
| 2026-07-22 | Initial selective migration from Juggluco-Portrait — Vico/Compose chart + portrait orientation only, all widget/alarm additions excluded. |
| 2026-08-06 | Merged upstream 10.9.9 (`4d28ea31`). Key changes: `applyScreenOrientation()` with Android 16 large-screen support in `MainActivity`, `canRotate` flag + `ContentObserver` for auto-rotate, `reverseorientation` checkbox conditional on `canRotate` in `Settings`, `screenOrientation="unspecified"` in AndroidManifest, updated `NumberView` keyboard overlay, `FloatingConfig` portrait/landscape dynamic layout, `Dialogs` portrait overlay centering fix, `watchserver` new HTTP endpoints, `sslserver` improvements, `GlucoseCurve` Context imports, Ukrainian strings, version 10.9.9 / versionCodes 899 (mobile) 900 (wear). |
| 2026-08-06 | Post-merge 10.9.9 build fixes: (1) `jugglucotext.hpp` array sizes enlarged (`daylabel[20]`, `speakdaylabel[56]`, `monthlabel[20]`) to fit longer Ukrainian UTF-8 strings. (2) BOM (`\ufeff`) removed from 7 Java files (`editfocus`, `Layout`, `NumberView`, `Sensors`, `Calibration`, `Dialogs`, `FloatingConfig`). (3) `GlucoseCurve.java`: added `getwidth(Context)`, `getheight(Context)`, `isLandscape(Context)`, `configurationChanged(MainActivity)` from upstream. (4) `Layout.java`: restored `shouldPortraitStack()`, `portraitRows()`, `portraitRow()` lost during merge. (5) `Settings.java`: added missing `SCREEN_ORIENTATION_LANDSCAPE` / `SCREEN_ORIENTATION_REVERSE_LANDSCAPE` imports. BUILD SUCCESSFUL. |
