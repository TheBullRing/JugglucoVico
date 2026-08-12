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
| 2026-08-06 | Fix orientación Vico no rotaba a landscape + menú Compose nativo: (1) `applyScreenOrientation()` — `stored==0` (LANDSCAPE, valor por defecto del campo C++ `uint8_t:7`) ahora se trata igual que el sentinel `127` → `UNSPECIFIED`, evitando que usuarios que usaron el viejo UI queden bloqueados en portrait. (2) Corregido error de compilación `settings.Settings.set(act)` → `tk.glucodata.settings.Settings.set(act)`. (3) Corregidas 11 lambdas Java `(Boolean)->Unit` que devolvían void: añadido `return kotlin.Unit.INSTANCE`. (4) `VicoMenuSheet.kt` — menú 100% Compose con 3 pestañas (Acciones/Datos/Ver), `TwoColumnGrid` via `SubcomposeLayout`. BUILD SUCCESSFUL. |
| 2026-08-11 | Fix colores del tema en UI Vico + Settings: (1) `GlucoseChartScreen.kt` — `GlucoseTheme` ya no usa `dynamicColorScheme` (Material You del sistema que ignora el tema Juggluco); nueva función `buildJugglucoColorScheme()` lee `windowBackground`, `colorButtonBackground`, `colorButtonText` y `textColorPrimary` del tema Android activo, construyendo un `ColorScheme` M3 que refleja el tema personalizado del usuario. Eliminados imports `dynamicDarkColorScheme`/`dynamicLightColorScheme`. (2) `Settings.java` `mksettings()` — añadido `scroller.setFillViewport(true)` al `ScrollView` principal que lo necesitaba (igual que los otros 3 ScrollViews del mismo fichero): corrige el espacio vacío en la parte inferior y los elementos cortados por la derecha en modo portrait. BUILD SUCCESSFUL. |
| 2026-08-11b | Fix Settings cortado al rotar + menú Vico con colores correctos: (1) `GlucoseChartScreen.kt` — `VicoMenuSheet` envuelto en `GlucoseTheme` para que reciba los colores del tema Juggluco (antes heredaba Material You del sistema). (2) `Settings.java` `layoutweg()` — descomentado: ahora destruye realmente el `settinglayout` (antes era no-op). (3) `Settings.java` `recreateIfOpen()` — nuevo método estático que reconstruye el layout solo si Settings está visible. (4) `MainActivity.onConfigurationChanged()` — llama `Settings.recreateIfOpen()` al rotar para que se redibujen las filas con `portraitRows()` según la nueva orientación. BUILD SUCCESSFUL. |
| 2026-08-11c | Fix visual menú Vico — espacio vacío + distinción ON/OFF: (1) `VicoMenuSheet.kt` — sheet cambia de `fillMaxHeight(0.60f)` a `heightIn(max=72%)` + inner column de `fillMaxSize()` a `fillMaxWidth()`: el sheet ahora se encoge al contenido real sin espacio muerto. (2) `ToggleButton` — activo usa `Button` (fondo primario sólido), inactivo usa `OutlinedButton` (transparente con borde): diferencia visual inmediata entre ON y OFF. BUILD SUCCESSFUL. Verificado en dispositivo con tema VintageParchment. |
| 2026-08-11d | Modernización visual del dashboard Vico (8 mejoras): (1) `ChartTopBar` — altura 44→40dp, `titleMedium`→`titleSmall` SemiBold, icono ☰ sin fondo de card (solo zona táctil 40dp). (2) `TirChip` — fondo de `color.copy(alpha=0.10f)` saturado → `surfaceContainer` neutro + borde sutil de 1dp coloreado; etiqueta pasa a `onSurfaceVariant`. (3) `SecondaryChip` nuevo — para predicción 20min, más compacto y en `surfaceContainerLow`. (4) Predicción 20min separada en fila propia de ancho completo (`SecondaryChip`) fuera de la fila TIR/GMI/CV% en portrait y landscape. (5) `GmiNaChip` — se oculta cuando faltan >3 días (reduce ruido); cuando es inminente muestra "GMI / Datos insuficientes" en lugar de "N/A / GMI (<14 días)". (6) Banda objetivo: `COLOR_TARGET_BAND` de 5% (0x0D) a 12% (0x1E) alpha en ambos charts portrait y landscape — banda CGM estilo Dexcom Clarity. (7) `buildJugglucoColorScheme` — tema oscuro usa `Surface` M3 real (~#1C1B1F) en lugar de inversión cromática que podía producir blanco puro; temas ya oscuros no se invierten. (8) `surfaceContainer` y `surfaceContainerLow` derivados y pasados a `darkColorScheme`/`lightColorScheme`. BUILD SUCCESSFUL. |
| 2026-08-13 | Merge upstream 10.10.0 (`4f8d94b4`). Cambios clave: nuevo motor de seguridad Libre 3 (loadlibs.cpp completamente reescrito, 82 ficheros nuevos en `libre3/process/`); `CMakeLists.txt` lista `LIBRE3SRC` condicional; 9 nuevos JNI en `Natives.java` (`libre3BeginSecurityHandshake`…`libre3ExportSavedAuthorization`) + `gethttpport`/`sethttpport`; `Nightscout.java` con puerto HTTP configurable + botón WebPageUpload; `WebPageUpload.java` nuevo (mobile/small/wear); `GlucoseWidget.java` `oldage` → `final`; strings actualizados (17 locales) + strings Portrait re-aplicados (EN/ES: orientation/vico); `html.xml` (15 locales mobile); NDK 30.0.15729638, `TARGETSDK` 36, `versionName` 10.10.0, `versionCode` 901/902. Commit: `78740b5c`. |
