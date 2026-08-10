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

## Phase 7 — Vico Chart para Wear OS (pendiente)

**Estado:** ⏳ Pendiente (bloqueada hasta completar la fase 6 en mobile)

### Contexto

La app Wear OS (`flavor = wear`) actualmente usa el renderer C++/OpenGL ES 2/NanoVG
igual que el mobile original. La pantalla de un reloj es siempre **portrait** (ancho < 300 dp),
por lo que el `PortraitLayout` existente de Vico ya es compatible sin cambios de lógica.

### Trabajo necesario

#### 1. Mover código compartido de `src/mobile/` a `src/main/`
Los 5 ficheros que no tienen dependencias de UI pueden vivir en `src/main/` para
que tanto `mobile` como `wear` los compilen:

| Fichero | Acción |
|---------|--------|
| `Models.kt` | Mover a `src/main/java/tk/glucodata/chart/` |
| `GraphDataRepository.kt` | Mover a `src/main/java/tk/glucodata/chart/` |
| `NativeGraphDataRepository.kt` | Mover a `src/main/java/tk/glucodata/chart/` |
| `GlucoseChartViewModel.kt` | Mover a `src/main/java/tk/glucodata/chart/` |
| `GlucoseChartViewBridge.kt` | Mover a `src/main/java/tk/glucodata/chart/` (o duplicar en wear) |

Los 4 ficheros de UI pura quedan en `src/mobile/`:
`GlucoseChartScreen.kt`, `GlucoseChartCanvas.kt`, `GlucoseChartCanvasPortrait.kt`, cualquier otro composable con `DropdownMenu` / `systemBarsPadding`.

#### 2. Crear UI específica para Wear OS en `src/wear/java/tk/glucodata/chart/`

```
WearGlucoseChartScreen.kt       — portrait fija, sin menú popup, sin systemBarsPadding
WearGlucoseChartViewBridge.kt   — bridge Java→Compose para MainActivity en wear
```

Diferencias vs mobile:
- Sin `MenuPopupButton` (DropdownMenu no existe en Wear Compose)
- Sin `systemBarsPadding()` (no aplica en Wear OS)
- Sin rama `LandscapeLayout` (siempre portrait en reloj)
- Tipografía más compacta (pantalla ~300 dp)
- Usar `androidx.wear.compose:compose-material3` en lugar de `androidx.compose.material3`

#### 3. Dependencias en `build.gradle`

```groovy
wearImplementation 'com.patrykandpatrick.vico:compose-m3:3.2.3'
wearImplementation 'com.patrykandpatrick.vico:compose-android:3.2.3'
wearImplementation 'androidx.wear.compose:compose-material3:1.0.0'
wearImplementation 'androidx.wear.compose:compose-foundation:1.4.0'
```

#### 4. `MainActivity.java` — `startdisplay()`
El bloque Vico ya existe pero usa `GlucoseChartViewBridgeKt` que no está en el
classpath del flavor `wear`. Al mover/duplicar el bridge a `src/main/` o `src/wear/`
el condicional `if (!isWearable)` existente ya protege los bloques exclusivos de mobile
(WindowInsets, lightBars, orientación). No se necesita lógica nueva.

#### 5. Toggle Vico en Wear OS
`Applic.useVicoChart()` y `setUseVicoChart()` ya son genéricos (SharedPreferences).
En Wear OS se podría prescindir del toggle (Vico siempre activo), o mantenerlo para
facilitar el rollback. Decisión pendiente al inicio de esta fase.

### Restricciones a respetar
- El watch face propio (`watchface/WatchFaceService.kt`) **no se toca** — usa su
  propio renderer independiente.
- WearOS retiene el `GlucoseCurve` como fallback (misma política que mobile).
- No añadir dependencias Compose al flavor `wear` hasta que esta fase esté activa.

---

## Session Log

| Date | Summary |
|------|---------|
| 2026-07-22 | Initial selective migration from Juggluco-Portrait — Vico/Compose chart + portrait orientation only, all widget/alarm additions excluded. |
| 2026-08-06 | Merged upstream 10.9.9 (`4d28ea31`). Key changes: `applyScreenOrientation()` with Android 16 large-screen support in `MainActivity`, `canRotate` flag + `ContentObserver` for auto-rotate, `reverseorientation` checkbox conditional on `canRotate` in `Settings`, `screenOrientation="unspecified"` in AndroidManifest, updated `NumberView` keyboard overlay, `FloatingConfig` portrait/landscape dynamic layout, `Dialogs` portrait overlay centering fix, `watchserver` new HTTP endpoints, `sslserver` improvements, `GlucoseCurve` Context imports, Ukrainian strings, version 10.9.9 / versionCodes 899 (mobile) 900 (wear). |
| 2026-08-06 | Post-merge 10.9.9 build fixes: (1) `jugglucotext.hpp` array sizes enlarged (`daylabel[20]`, `speakdaylabel[56]`, `monthlabel[20]`) to fit longer Ukrainian UTF-8 strings. (2) BOM (`\ufeff`) removed from 7 Java files (`editfocus`, `Layout`, `NumberView`, `Sensors`, `Calibration`, `Dialogs`, `FloatingConfig`). (3) `GlucoseCurve.java`: added `getwidth(Context)`, `getheight(Context)`, `isLandscape(Context)`, `configurationChanged(MainActivity)` from upstream. (4) `Layout.java`: restored `shouldPortraitStack()`, `portraitRows()`, `portraitRow()` lost during merge. (5) `Settings.java`: added missing `SCREEN_ORIENTATION_LANDSCAPE` / `SCREEN_ORIENTATION_REVERSE_LANDSCAPE` imports. BUILD SUCCESSFUL. |
| 2026-08-06 | Android 16 large-screen orientation resilience applied to Vico UI: (1) `startdisplay()` — removed manual `setRequestedOrientation()` block; old UI uses `requestOrientationOnce(LANDSCAPE)`, Vico path delegates to `applyScreenOrientation()`. (2) `Settings.finish()` — direct `setRequestedOrientation(getScreenOrientation())` replaced by `activity.applyScreenOrientation(config)`. (3) Orientation spinner `onItemSelected` — direct `setRequestedOrientation(val)` replaced by `context.applyScreenOrientation(config)`. BUILD SUCCESSFUL. |
| 2026-08-06 | Fix doble de orientación: (1) UI viejo (OpenGL/NanoVG) se quedaba en portrait porque `onStart()` y `onConfigurationChanged()` llaman `applyScreenOrientation()` sin comprobar `useVicoChart()`, sobreescribiendo el `LANDSCAPE` forzado en `startdisplay()`. Fix: guardia `if (!useVicoChart()) { requestOrientationOnce(LANDSCAPE); return; }` al inicio de `applyScreenOrientation()`. (2) UI nuevo (Vico) no rotaba a landscape porque el campo C++ `orientation` es `uint8_t:7` con valor por defecto `0` = `SCREEN_ORIENTATION_LANDSCAPE`, bloqueando la rotación. Fix: sentinel `127` (campo nunca escrito) → `SCREEN_ORIENTATION_UNSPECIFIED`. BUILD SUCCESSFUL. |
| 2026-08-06 | Límite de scroll en ambos charts: el usuario podía desplazarse al futuro y al pasado infinito. Fix en `GlucoseLandscapeChart.kt` y `GlucosePortraitChart.kt`: `pointerInput(entries)` recalcula en cada gesto `maxEnd = maxTimestamp + 5 min` y `minEnd = minTimestamp + windowMs`, aplicando `coerceIn` al arrastre y al zoom. BUILD SUCCESSFUL. |
| 2026-08-06 | Fix overlays (menús, settings, stats, backup, etc.) detrás del notch en path Vico: `MainActivity.addMyContentView()` ahora aplica automáticamente `ViewCompat.setOnApplyWindowInsetsListener` + `requestApplyInsets` a cualquier overlay `MATCH_PARENT` cuando `useVicoChart()` = true, usando `Math.max(existente, inset)` para no destruir padding horizontal/bottom ya calculado. Eliminado `fitsSystemWindows="true"` del layout-port/menus.xml. BUILD SUCCESSFUL. |
