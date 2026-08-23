/*
 * Phase 3 / Phase 6 — Vico Integration + Polish
 *
 * Main Compose entry-point for the glucose chart.
 * Hosted inside a ComposeView that replaces GlucoseCurve when USE_VICO_CHART = true.
 *
 * Layout is responsive:
 *   - Portrait / compact width  (< 600 dp): chart stacked above stats panel.
 *   - Landscape / expanded width (≥ 600 dp): chart left, stats panel right.
 *
 * RTL rules:
 *   - CartesianChartHost is wrapped in LayoutDirection.Ltr so the time axis
 *     always runs left → right regardless of device locale.
 *   - Surrounding stat/sensor info panels use the locale direction
 *     (RTL when ChartSettings.isRtl == true).
 *
 * Threading: all ViewModel StateFlows are collected on the main thread via
 * collectAsStateWithLifecycle.
 *
 * Vico 3.x API notes (verified against compose-android 3.2.3 AAR):
 *   - Only artifact: com.patrykandpatrick.vico:compose-m3:3.2.3
 *   - Axes: HorizontalAxis.rememberBottom() / VerticalAxis.rememberStart()
 *   - HorizontalLine is in compose.cartesian.decoration
 *   - CartesianLayerRangeProvider.auto() for data-range Y-axis scaling
 *   - x-values must be Unix SECONDS (not ms) — Float precision would lose
 *     ~5 digits at the ms epoch magnitude (~1.75e12)
 *   - valueFormatter must never return blank — Vico 3.x throws IllegalStateException
 */
package tk.glucodata.chart

import tk.glucodata.ui.menus.VicoMenuHost
import tk.glucodata.ui.menus.VicoMenuState
import tk.glucodata.ui.menus.VicoMenuViewModel
import android.app.DatePickerDialog
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tk.glucodata.Natives
import java.util.Calendar

// ─────────────────────────────────────────────────────────────────────────────
// Public entry-point
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Root composable. Call from the ComposeView created in MainActivity.startdisplay()
 * when [tk.glucodata.Applic.useVicoChart()] is true.
 *
 * El chart y el menú viven en dos contextos de tema distintos:
 *  - El chart usa [GlucoseTheme] (puede invertir colores según preferencia del usuario).
 *  - El menú usa el MaterialTheme heredado de la Activity (tema dinámico del dispositivo).
 *  Esto evita que los colores del menú diverjan del tema Material You del teléfono.
 *
 * @param viewModel   The chart ViewModel (created via [GlucoseChartViewModel.Factory]).
 * @param menuActions All menu actions and toggle states for the Compose menu sheet.
 * @param onLongPress Invoked when the user long-presses a data point.
 */
@Composable
fun GlucoseChartScreen(
    viewModel:     GlucoseChartViewModel,
    menuActions:   VicoMenuActions,
    menuViewModel: VicoMenuViewModel,
    onLongPress:   (GlucoseEntry) -> Unit = {},
) {
    val entries  by viewModel.glucoseEntries.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    // Ventana de 8 horas — igual que el original JCurve.hpp: duration = 8*60*60
    val windowMsState  = remember { mutableFloatStateOf(8L * 3_600_000L.toFloat()) }
    val windowEndState = remember { mutableLongStateOf(0L) }

    // Inicializar windowEnd la primera vez que llegan datos reales.
    // Margen pequeño (15 min) para que el último punto quede cerca del borde inferior
    // del gráfico portrait, sin dejar espacio en blanco excesivo en el futuro.
    if (windowEndState.longValue == 0L && entries.isNotEmpty()) {
        windowEndState.longValue = entries.maxOf { it.timestampMs } + 15L * 60_000L
    }

    val windowEndMs by windowEndState
    val windowMs    by windowMsState

    var menuVisible by remember { mutableStateOf(false) }

    // El Box raíz ocupa toda la pantalla incluyendo barras del sistema.
    // El chart aplica systemBarsPadding internamente; el menú overlay lo ignora
    // para poder cubrir toda la pantalla (incluyendo nav bar) con el scrim.
    Box(modifier = Modifier.fillMaxSize()) {

        // ── Chart con su propio tema (puede invertir colores) ────────────────
        GlucoseTheme(invertColors = settings.invertColors) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "Glucose chart screen" },
                color = MaterialTheme.colorScheme.background,
            ) {
                Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val configuration = LocalConfiguration.current
                        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                        if (isLandscape) {
                            LandscapeLayout(
                                entries        = entries,
                                settings       = settings,
                                windowEndMs    = windowEndMs,
                                windowMs       = windowMs,
                                windowEndState = windowEndState,
                                windowMsState  = windowMsState,
                                onMenuClick    = { menuVisible = true },
                            )
                        } else {
                            PortraitLayout(
                                entries        = entries,
                                settings       = settings,
                                windowEndMs    = windowEndMs,
                                windowMs       = windowMs,
                                windowEndState = windowEndState,
                                windowMsState  = windowMsState,
                                onMenuClick    = { menuVisible = true },
                            )
                        }
                    }
                }
            }
        }

        // ── Menú overlay — usa el mismo tema Juggluco que el chart ───────────
        if (menuVisible) {
            GlucoseTheme(invertColors = settings.invertColors) {
                VicoMenuSheet(
                    actions       = menuActions,
                    darkModeState = settings.invertColors,
                    onDismiss     = { menuVisible = false },
                )
            }
        }

        // ── Diálogos Compose del path Vico (Fase 8) ──────────────────────────
        // Envuelto en GlucoseTheme para que los diálogos hereden el mismo
        // ColorScheme que el chart y el VicoMenuSheet (Material You / M3).
        GlucoseTheme(invertColors = settings.invertColors) {
            VicoMenuHost(
                viewModel      = menuViewModel,
                onJavaDelegate = { state ->
                    // Solo los estados que aún no tienen equivalente Compose llegan aquí.
                    // About ya es Compose nativo (VicoAboutDialog) — nunca llega aquí.
                    when (state) {
                        is VicoMenuState.BlueSensor -> menuActions.onBlueSensor.run()
                        is VicoMenuState.History    -> menuActions.onHistory.run()
                        is VicoMenuState.Iob        -> menuActions.onIob.run()
                        is VicoMenuState.SensorInfo -> { /* showsensorinfo llama Sensors.show() directamente */ }
                        is VicoMenuState.Export     -> menuActions.onExport.run()
                        is VicoMenuState.LastScan   -> menuActions.onLastScan.run()
                        is VicoMenuState.Sensor     -> menuActions.onSensor.run()
                        is VicoMenuState.Backup     -> menuActions.onMirror.run()
                        is VicoMenuState.Talk       -> menuActions.onTalk.run()
                        is VicoMenuState.Search     -> menuActions.onSearch.run()
                        is VicoMenuState.DatePick   -> menuActions.onDate.run()
                        else                        -> { /* resto de estados manejados por VicoMenuHost */ }
                    }
                },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top App Bar — Material 3 estándar, sin fondo destacado en el icono de menú
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Barra superior Material 3 estándar.
 * - Título a la izquierda con tipografía titleSmall (compacta, no domina).
 * - Icono ☰ a la derecha sin fondo de card — solo zona táctil con ripple implícito.
 * Altura fija de 40 dp para ceder el máximo espacio al gráfico y los datos.
 */
@Composable
private fun ChartTopBar(onMenuClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Icono de la app — PackageManager con el packageName real en tiempo de ejecución.
        // Funciona en todos los flavors (debug/release) sin hardcodear R ni applicationId.
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val ctx = LocalContext.current
            val appIconBitmap = remember(ctx) {
                try {
                    val drawable = ctx.packageManager.getApplicationIcon(ctx.packageName)
                    val bmp = android.graphics.Bitmap.createBitmap(
                        drawable.intrinsicWidth.coerceAtLeast(1),
                        drawable.intrinsicHeight.coerceAtLeast(1),
                        android.graphics.Bitmap.Config.ARGB_8888,
                    )
                    val canvas = android.graphics.Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bmp
                } catch (_: Exception) { null }
            }
            if (appIconBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap             = appIconBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier           = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
            }
            Text(
                text       = "Juggluco",
                style      = MaterialTheme.typography.titleSmall,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
        }
        // Icono de menú sin fondo: zona táctil de 40×40 dp con padding interior
        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable(onClick = onMenuClick)
                .semantics { contentDescription = "Abrir menú principal" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text  = "☰",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Layout variants
// ─────────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────
// Cálculo compartido de datos de glucosa actual
// Se usa tanto en PortraitLayout (para el gauge) como en StatsPanel.
// ─────────────────────────────────────────────────────────────────────────────

private data class CurrentGlucoseData(
    val latestMgdL:    Float?,
    val glucoseColor:  Color,
    val trendArrow:    String,
    val trendLabel:    String,
    val trendColor:    Color,
    val displayValue:  String,
    val unitSuffix:    String,
    val ageStr:        String?,
    val ageColor:      Color,
    val deltaDisplay:  String?,
    val deltaColor:    Color,
)

@Composable
private fun rememberCurrentGlucoseData(
    entries:  List<GlucoseEntry>,
    settings: ChartSettings,
): CurrentGlucoseData {
    val latest   = entries.maxByOrNull { it.timestampMs }
    val previous = entries
        .filter { latest == null || it.timestampMs < latest.timestampMs }
        .maxByOrNull { it.timestampMs }

    val latestDisplay = latest?.let { toDisplayUnit(it.valueMgdL, settings.unit) }
    val displayValue  = latestDisplay?.let {
        if (settings.unit == GlucoseUnit.MMOL) "%.1f".format(it) else it.toInt().toString()
    } ?: "—"
    val unitSuffix = if (settings.unit == GlucoseUnit.MMOL) "mmol/L" else "mg/dL"

    val glucoseColor = when {
        latestDisplay == null              -> MaterialTheme.colorScheme.onSurfaceVariant
        latestDisplay < settings.veryLow   -> Color(0xFFE74C3C)
        latestDisplay < settings.targetLow -> Color(0xFFF39C12)
        latestDisplay > settings.veryHigh  -> Color(0xFFE74C3C)
        latestDisplay > settings.targetHigh-> Color(0xFFF39C12)
        else                               -> Color(0xFF2ECC71)
    }

    val sensorRate: Float? = try {
        Natives.lastglucose()?.rate?.let { r -> if (r.isNaN()) null else r }
    } catch (_: Exception) { null }

    val (trendArrow, trendLabel) = when {
        latest == null    -> Pair("→", "Estable")
        sensorRate != null -> when {
            sensorRate >  2.0f -> Pair("↗↗", "Subiendo")
            sensorRate >  1.0f -> Pair("↗",  "Subiendo")
            sensorRate < -2.0f -> Pair("↙↙", "Bajando")
            sensorRate < -1.0f -> Pair("↙",  "Bajando")
            else               -> Pair("→",  "Estable")
        }
        previous == null -> Pair("→", "Estable")
        else -> {
            val delta = latest.valueMgdL - previous.valueMgdL
            when {
                delta > 10f  -> Pair("↗↗", "Subiendo")
                delta > 4f   -> Pair("↗",  "Subiendo")
                delta < -10f -> Pair("↙↙", "Bajando")
                delta < -4f  -> Pair("↙",  "Bajando")
                else         -> Pair("→",  "Estable")
            }
        }
    }
    val trendColor = when (trendLabel) {
        "Subiendo" -> Color(0xFFF39C12)
        "Bajando"  -> Color(0xFF1E88E5)
        else       -> Color(0xFF8E8E93)
    }

    val now = System.currentTimeMillis()
    val ageStr = if (latest == null) null else {
        val ageMin = ((now - latest.timestampMs) / 60_000L).toInt()
        when {
            ageMin < 1  -> "ahora"
            ageMin < 60 -> "hace ${ageMin}m"
            else        -> "hace ${"%.0f".format(ageMin / 60f)}h"
        }
    }
    val ageColor = if (latest == null) Color(0xFF8E8E93) else {
        val ageMin = ((now - latest.timestampMs) / 60_000L).toInt()
        when {
            ageMin > 15 -> Color(0xFFE74C3C)
            ageMin > 7  -> Color(0xFFF39C12)
            else        -> Color(0xFF8E8E93)
        }
    }

    val deltaDisplay = if (latest != null && previous != null) {
        val d = toDisplayUnit(latest.valueMgdL, settings.unit) - toDisplayUnit(previous.valueMgdL, settings.unit)
        val sign = if (d >= 0f) "+" else ""
        if (settings.unit == GlucoseUnit.MMOL) "$sign${"%.1f".format(d)}" else "$sign${d.toInt()}"
    } else null
    val deltaColor = when {
        deltaDisplay == null -> Color(0xFF8E8E93)
        latest != null && previous != null -> {
            val d = toDisplayUnit(latest.valueMgdL, settings.unit) - toDisplayUnit(previous.valueMgdL, settings.unit)
            when {
                kotlin.math.abs(d) > if (settings.unit == GlucoseUnit.MMOL) 1.1f else 20f -> Color(0xFFE74C3C)
                kotlin.math.abs(d) > if (settings.unit == GlucoseUnit.MMOL) 0.6f else 10f -> Color(0xFFF39C12)
                else -> Color(0xFF8E8E93)
            }
        }
        else -> Color(0xFF8E8E93)
    }

    return CurrentGlucoseData(
        latestMgdL   = latest?.valueMgdL,
        glucoseColor = glucoseColor,
        trendArrow   = trendArrow,
        trendLabel   = trendLabel,
        trendColor   = trendColor,
        displayValue = displayValue,
        unitSuffix   = unitSuffix,
        ageStr       = ageStr,
        ageColor     = ageColor,
        deltaDisplay = deltaDisplay,
        deltaColor   = deltaColor,
    )
}

/**
 * Landscape: barra superior mínima con selector de fecha + gráfica a pantalla completa.
 * Se elimina el panel lateral de stats — la gráfica ocupa el máximo espacio posible.
 */
@Composable
private fun LandscapeLayout(
    entries:        List<GlucoseEntry>,
    settings:       ChartSettings,
    windowEndMs:    Long,
    windowMs:       Float,
    windowEndState: MutableLongState,
    windowMsState:  MutableFloatState,
    onMenuClick:    () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // ── Barra superior mínima: selector de fecha + botones día + menú ─────
        LandscapeTopBar(
            windowEndMs    = windowEndMs,
            windowEndState = windowEndState,
            windowMsState  = windowMsState,
            onMenuClick    = onMenuClick,
        )

        // ── Gráfica ocupa todo el espacio restante — sin panel lateral ─────────
        GlucoseLandscapeChart(
            entries        = entries,
            settings       = settings,
            windowEndMs    = windowEndMs,
            windowMs       = windowMs,
            windowEndState = windowEndState,
            windowMsState  = windowMsState,
            modifier       = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

/**
 * Barra superior de landscape: selector de fecha (chip pulsable → DatePickerDialog),
 * botones de navegación día anterior/siguiente, y botón de menú.
 * Altura fija 40dp para ceder el máximo espacio al gráfico.
 */
@Composable
private fun LandscapeTopBar(
    windowEndMs:    Long,
    windowEndState: MutableLongState,
    windowMsState:  MutableFloatState,
    onMenuClick:    () -> Unit,
) {
    val ctx = LocalContext.current

    // Fecha visible formateada desde el extremo derecho de la ventana
    val dateLabel = remember(windowEndMs) {
        val cal = Calendar.getInstance().apply { timeInMillis = windowEndMs }
        val now = Calendar.getInstance()
        val isToday = cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        if (isToday) "Hoy"
        else {
            val fmt = android.text.format.DateFormat.getMediumDateFormat(ctx)
            fmt.format(cal.time)
        }
    }

    val oneDayMs = 24L * 3_600_000L

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // ── Botón día anterior ────────────────────────────────────────────────
        Box(
            modifier         = Modifier
                .size(36.dp)
                .clickable { windowEndState.longValue -= oneDayMs }
                .semantics { contentDescription = "Día anterior" },
            contentAlignment = Alignment.Center,
        ) {
            Text("◀", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // ── Selector de fecha (chip) ──────────────────────────────────────────
        AssistChip(
            onClick = {
                val cal = Calendar.getInstance().apply { timeInMillis = windowEndMs }
                DatePickerDialog(
                    ctx,
                    { _, year, month, day ->
                        val selected = Calendar.getInstance().apply {
                            set(year, month, day, 23, 59, 59)
                        }
                        windowEndState.longValue = selected.timeInMillis
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH),
                ).show()
            },
            label  = {
                Text(dateLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold)
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            modifier = Modifier.height(32.dp),
        )

        // ── Botón día siguiente ───────────────────────────────────────────────
        Box(
            modifier         = Modifier
                .size(36.dp)
                .clickable {
                    val newEnd = windowEndState.longValue + oneDayMs
                    // No permitir navegar más allá de ahora + 15 min
                    windowEndState.longValue = minOf(newEnd, System.currentTimeMillis() + 15L * 60_000L)
                }
                .semantics { contentDescription = "Día siguiente" },
            contentAlignment = Alignment.Center,
        ) {
            Text("▶", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // ── Botón "Ir a hoy" — vuelve al presente ────────────────────────────
        Box(
            modifier         = Modifier
                .size(36.dp)
                .clickable {
                    windowEndState.longValue = System.currentTimeMillis() + 15L * 60_000L
                }
                .semantics { contentDescription = "Ir a hoy" },
            contentAlignment = Alignment.Center,
        ) {
            Text("⊙", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.weight(1f))

        // ── Botón de menú ─────────────────────────────────────────────────────
        Box(
            modifier         = Modifier
                .size(40.dp)
                .clickable(onClick = onMenuClick)
                .semantics { contentDescription = "Abrir menú principal" },
            contentAlignment = Alignment.Center,
        ) {
            Text("☰", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Portrait: 3 secciones verticales:
 *   [a] GlucoseGauge  — velocímetro con valor actual y puntero animado  (~33%)
 *   [b] Gráfica       — GlucoseLandscapeChart / GlucosePortraitChart    (~45%)
 *   [c] Chips TIR     — TIR, GMI, CV%, Predicción                      (~22%)
 *
 * El botón de menú vive como icono flotante en la esquina superior derecha del gauge.
 */
@Composable
private fun PortraitLayout(
    entries:        List<GlucoseEntry>,
    settings:       ChartSettings,
    windowEndMs:    Long,
    windowMs:       Float,
    windowEndState: MutableLongState,
    windowMsState:  MutableFloatState,
    onMenuClick:    () -> Unit,
) {
    val glucoseData = rememberCurrentGlucoseData(entries, settings)

    Column(modifier = Modifier.fillMaxSize()) {

        // ── [a] Sección 1: Velocímetro ────────────────────────────────────────
        // weight(0.35f): el gauge tiene aspectRatio(1.6) propio, pero el Box
        // padre necesita un peso para que la Column divida el espacio sobrante
        // entre gauge y gráfica.  El gauge se encoge al contenido con wrapContentHeight.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.35f),
        ) {
            GlucoseGauge(
                currentMgdL  = glucoseData.latestMgdL,
                settings     = settings,
                trendArrow   = glucoseData.trendArrow,
                glucoseColor = glucoseData.glucoseColor,
                modifier     = Modifier.fillMaxSize(),
            )
            // Botón ☰ flotante en esquina superior derecha
            Box(
                modifier         = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .size(40.dp)
                    .clickable(onClick = onMenuClick)
                    .semantics { contentDescription = "Abrir menú principal" },
                contentAlignment = Alignment.Center,
            ) {
                Text("☰", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
            }
            // Age of data — esquina superior izquierda, sobre el gauge
            if (glucoseData.ageStr != null) {
                Text(
                    text     = glucoseData.ageStr,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = glucoseData.ageColor,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 10.dp, top = 6.dp),
                )
            }
        }

        // ── [b] Sección 2: Gráfica ────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.65f)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            if (settings.portraitPivoted) {
                GlucosePortraitChart(
                    entries        = entries,
                    settings       = settings,
                    windowEndMs    = windowEndMs,
                    windowMs       = windowMs,
                    windowEndState = windowEndState,
                    windowMsState  = windowMsState,
                    modifier       = Modifier.fillMaxSize(),
                )
            } else {
                GlucoseLandscapeChart(
                    entries        = entries,
                    settings       = settings,
                    windowEndMs    = windowEndMs,
                    windowMs       = windowMs,
                    windowEndState = windowEndState,
                    windowMsState  = windowMsState,
                    modifier       = Modifier.fillMaxSize(),
                )
            }
        }

        // ── [c] Sección 3: Chips TIR / GMI / CV% / Predicción ────────────────
        // wrapContentHeight: la sección toma exactamente la altura que necesitan
        // los chips, sin recortarlos ni dejar espacio vacío.
        StatsPanelWithDirection(
            entries     = entries,
            settings    = settings,
            modifier    = Modifier.fillMaxWidth(),
            isLandscape = false,
            chipsOnly   = true,
        )
    }
}

@Composable
private fun StatsPanelWithDirection(
    entries:     List<GlucoseEntry>,
    settings:    ChartSettings,
    modifier:    Modifier = Modifier,
    isLandscape: Boolean  = false,
    chipsOnly:   Boolean  = false,
) {
    val direction = if (settings.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        StatsPanel(entries, settings, modifier, isLandscape, chipsOnly)
    }
}

/**
 * Converts a raw mg/dL value to the display unit.
 * GlucoseEntry.valueMgdL is always stored as mg/dL by streamfromSensorptr.
 * targetLow/targetHigh are already in display units (returned by gconvert()).
 * This function is used to bring sensor values into the same unit as the thresholds.
 */
private fun toDisplayUnit(mgdl: Float, unit: GlucoseUnit): Float =
    if (unit == GlucoseUnit.MMOL) mgdl / 18.0f else mgdl

// ─────────────────────────────────────────────────────────────────────────────
// Stats panel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Panel de estadísticas completo.
 *
 * @param chipsOnly  true → solo muestra los chips (TIR/GMI/CV%/Pred) sin la fila del valor grande.
 *                   Se usa en PortraitLayout donde el valor ya aparece en el GlucoseGauge.
 */
@Composable
private fun StatsPanel(
    entries:     List<GlucoseEntry>,
    settings:    ChartSettings,
    modifier:    Modifier = Modifier,
    isLandscape: Boolean  = false,
    chipsOnly:   Boolean  = false,
) {
    val latest   = entries.maxByOrNull { it.timestampMs }
    val previous = entries
        .filter { latest == null || it.timestampMs < latest.timestampMs }
        .maxByOrNull { it.timestampMs }

    val latestDisplay = latest?.let { toDisplayUnit(it.valueMgdL, settings.unit) }

    // Valor de glucosa actual formateado
    val displayValue = latestDisplay?.let {
        if (settings.unit == GlucoseUnit.MMOL) "%.1f".format(it) else it.toInt().toString()
    } ?: "—"
    val unitSuffix = if (settings.unit == GlucoseUnit.MMOL) "mmol/L" else "mg/dL"

    // Color según 5 zonas:
    //   < veryLow              → rojo  (muy bajo)
    //   veryLow .. targetLow   → amarillo (bajo)
    //   targetLow .. targetHigh → verde  (en rango)
    //   targetHigh .. veryHigh → amarillo (alto)
    //   > veryHigh             → rojo  (muy alto)
    val glucoseColor = when {
        latestDisplay == null                 -> MaterialTheme.colorScheme.onSurfaceVariant
        latestDisplay < settings.veryLow      -> Color(0xFFE74C3C)
        latestDisplay < settings.targetLow    -> Color(0xFFF39C12)
        latestDisplay > settings.veryHigh     -> Color(0xFFE74C3C)
        latestDisplay > settings.targetHigh   -> Color(0xFFF39C12)
        else                                  -> Color(0xFF2ECC71)
    }

    // ── Tasa de cambio nativa del sensor (TR) ────────────────────────────────
    // Natives.lastglucose().rate contiene la tasa de cambio en mg/dL/min calculada
    // por la capa C++ a partir del campo `ch` del sensor (rateOfChange del Libre 3).
    // Es NaN cuando el dato no está disponible o el sensor está fuera de rango.
    val sensorRate: Float? = try {
        Natives.lastglucose()?.rate?.let { r -> if (r.isNaN()) null else r }
    } catch (_: Exception) { null }

    // Tendencia — preferir TR nativa; si no está disponible, calcular desde delta
    val (trendArrow, trendLabel) = when {
        latest == null -> Pair("→", "Estable")
        sensorRate != null -> {
            // rate está en mg/dL/min; Libre 3 lo reporta con resolución de ~0.1 mg/dL/min
            when {
                sensorRate >  2.0f -> Pair("↗↗", "Subiendo")
                sensorRate >  1.0f -> Pair("↗",  "Subiendo")
                sensorRate < -2.0f -> Pair("↙↙", "Bajando")
                sensorRate < -1.0f -> Pair("↙",  "Bajando")
                else               -> Pair("→",  "Estable")
            }
        }
        previous == null -> Pair("→", "Estable")
        else -> {
            val delta = latest.valueMgdL - previous.valueMgdL
            when {
                delta > 10f  -> Pair("↗↗", "Subiendo")
                delta > 4f   -> Pair("↗",  "Subiendo")
                delta < -10f -> Pair("↙↙", "Bajando")
                delta < -4f  -> Pair("↙",  "Bajando")
                else         -> Pair("→",  "Estable")
            }
        }
    }
    val trendColor = when (trendLabel) {
        "Subiendo" -> Color(0xFFF39C12)
        "Bajando"  -> Color(0xFF1E88E5)
        else       -> Color(0xFF8E8E93)
    }

    // Porcentajes TIR 24h
    val now    = System.currentTimeMillis()
    val cutoff = now - 24L * 3600L * 1000L
    val recent = entries.filter { it.timestampMs >= cutoff }
    val (tirPct, highPct, lowPct) = if (recent.isEmpty()) Triple(0, 0, 0)
    else {
        val lo = settings.targetLow; val hi = settings.targetHigh
        val inRange = recent.count { toDisplayUnit(it.valueMgdL, settings.unit) in lo..hi }
        val above   = recent.count { toDisplayUnit(it.valueMgdL, settings.unit) > hi }
        val below   = recent.count { toDisplayUnit(it.valueMgdL, settings.unit) < lo }
        Triple((inRange * 100) / recent.size, (above * 100) / recent.size, (below * 100) / recent.size)
    }

    // ── Δ delta (diferencia desde la lectura anterior en mg/dL) ──────────────
    val deltaDisplay = if (latest != null && previous != null) {
        val d = toDisplayUnit(latest.valueMgdL, settings.unit) - toDisplayUnit(previous.valueMgdL, settings.unit)
        val sign = if (d >= 0f) "+" else ""
        if (settings.unit == GlucoseUnit.MMOL) "$sign${"%.1f".format(d)}" else "$sign${d.toInt()}"
    } else null
    val deltaColor = when {
        deltaDisplay == null -> Color(0xFF8E8E93)
        latest != null && previous != null -> {
            val d = toDisplayUnit(latest.valueMgdL, settings.unit) - toDisplayUnit(previous.valueMgdL, settings.unit)
            when {
                kotlin.math.abs(d) > if (settings.unit == GlucoseUnit.MMOL) 1.1f else 20f -> Color(0xFFE74C3C)
                kotlin.math.abs(d) > if (settings.unit == GlucoseUnit.MMOL) 0.6f else 10f -> Color(0xFFF39C12)
                else -> Color(0xFF8E8E93)
            }
        }
        else -> Color(0xFF8E8E93)
    }

    // ── Age of data ───────────────────────────────────────────────────────────
    val ageStr = if (latest == null) null else {
        val ageMin = ((now - latest.timestampMs) / 60_000L).toInt()
        when {
            ageMin < 1  -> "ahora"
            ageMin < 60 -> "hace ${ageMin}m"
            else        -> "hace ${"%.0f".format(ageMin / 60f)}h"
        }
    }
    val ageColor = if (latest == null) Color(0xFF8E8E93) else {
        val ageMin = ((now - latest.timestampMs) / 60_000L).toInt()
        when {
            ageMin > 15 -> Color(0xFFE74C3C)   // sin datos > 15min: rojo
            ageMin > 7  -> Color(0xFFF39C12)   // datos algo antiguos: naranja
            else        -> Color(0xFF8E8E93)   // reciente: gris neutro
        }
    }

    // ── GMI y CV% ────────────────────────────────────────────────────────────
    // GMI (Glucose Management Indicator) es una estimación del HbA1c a partir de la
    // glucosa media. La fórmula ADA 2018 requiere al menos 14 días de datos continuos
    // para ser estadísticamente válida; con menos tiempo el valor es engañoso.
    //
    // Condición para mostrar GMI: el span temporal de los datos disponibles (no solo
    // las últimas 24h, sino todos los entries) debe cubrir ≥14 días.
    // CV% sí puede calcularse con los datos de las últimas 24h (mínimo 20 lecturas).
    val allEntriesSpanDays = if (entries.size >= 2) {
        val spanMs = entries.maxOf { it.timestampMs } - entries.minOf { it.timestampMs }
        spanMs / (24L * 3_600_000L).toFloat()
    } else 0f
    val gmiDataSufficient = allEntriesSpanDays >= 14f && recent.size >= 20

    val (gmiVal, cvStr) = if (recent.size >= 20) {
        val meanMgdl = recent.map { it.valueMgdL }.average().toFloat()
        // GMI solo si hay ≥14 días de historial total
        val gmi = if (gmiDataSufficient) 3.31f + 0.02392f * meanMgdl else null  // fórmula ADA 2018
        val sd  = kotlin.math.sqrt(recent.map { (it.valueMgdL - meanMgdl) * (it.valueMgdL - meanMgdl) }
                      .average()).toFloat()
        val cv  = if (meanMgdl > 0f) (sd / meanMgdl) * 100f else 0f
        val cvFmt = "%.0f%%".format(cv)
        Pair(gmi, cvFmt)
    } else Pair(null, null)
    // Formato y color del GMI según semáforo clínico ADA
    val gmiStr   = gmiVal?.let { "%.1f%%".format(it) }
    val gmiColor = when {
        gmiVal == null     -> Color(0xFF8E8E93)
        gmiVal < 7.0f      -> Color(0xFF2ECC71)   // ✅ Control óptimo
        gmiVal <= 7.5f     -> Color(0xFFF39C12)   // ⚠️ Aceptable / precaución
        else               -> Color(0xFFE74C3C)   // ❌ Elevado fuera de meta
    }
    // Días que faltan para alcanzar los 14 necesarios (para el tooltip educativo)
    val daysUntilGmi = if (gmiDataSufficient) 0 else (14f - allEntriesSpanDays).coerceAtLeast(0f).let { kotlin.math.ceil(it.toDouble()).toInt() }
    val cvColor = when {
        cvStr == null -> Color(0xFF8E8E93)
        recent.size >= 20 -> {
            val meanMgdl = recent.map { it.valueMgdL }.average().toFloat()
            val sd = kotlin.math.sqrt(recent.map { (it.valueMgdL - meanMgdl) * (it.valueMgdL - meanMgdl) }.average()).toFloat()
            val cv = if (meanMgdl > 0f) (sd / meanMgdl) * 100f else 0f
            when { cv > 36f -> Color(0xFFE74C3C); cv > 28f -> Color(0xFFF39C12); else -> Color(0xFF2ECC71) }
        }
        else -> Color(0xFF8E8E93)
    }

    // ── Predicción 20 min ────────────────────────────────────────────────────
    // Estrategia de slope (en mg/dL/min):
    //   1. TR nativa del sensor si está disponible y no es NaN.
    //   2. Pendiente lineal sobre ventana de 20 minutos como fallback.
    // Amortiguación: si glucosa > 200 mg/dL y el Δ instantáneo cambia de signo
    // respecto al slope calculado (pico prandial en reversión), se aplica un
    // factor 0.5 para evitar proyecciones disparadas fuera del rango clínico.
    fun computeSlope(): Float? {
        val raw: Float? = if (sensorRate != null) {
            sensorRate
        } else {
            val l = latest ?: return null
            val windowStart20 = l.timestampMs - 20L * 60_000L
            val pts = entries
                .filter { it.sensorIndex == l.sensorIndex && it.timestampMs >= windowStart20 }
                .sortedBy { it.timestampMs }
            if (pts.size >= 2) {
                val dtMin = (pts.last().timestampMs - pts.first().timestampMs) / 60_000f
                if (dtMin > 0f) (pts.last().valueMgdL - pts.first().valueMgdL) / dtMin else null
            } else null
        }
        if (raw == null || latest == null) return null
        // Δ instantáneo = diferencia entre la última y la penúltima lectura
        val instantDelta = if (previous != null) latest.valueMgdL - previous.valueMgdL else 0f
        // Amortiguación: glucosa elevada + cambio de signo entre slope y Δ instantáneo
        return if (latest.valueMgdL > 200f && raw * instantDelta < 0f) raw * 0.5f else raw
    }

    val predSlope = if (latest != null && now - latest.timestampMs <= 10L * 60_000L) computeSlope() else null

    val predValueDisplay: String? = predSlope?.let { slope ->
        val pred = toDisplayUnit((latest!!.valueMgdL + slope * 20f).coerceIn(20f, 400f), settings.unit)
        val arrow = when { slope > 1.0f -> " ↗"; slope < -1.0f -> " ↘"; else -> " →" }
        if (settings.unit == GlucoseUnit.MMOL) "%.1f$arrow".format(pred) else "${pred.toInt()}$arrow"
    }

    val predColor2 = predSlope?.let { slope ->
        val pred = toDisplayUnit((latest!!.valueMgdL + slope * 20f).coerceIn(20f, 400f), settings.unit)
        when {
            pred < settings.veryLow    -> Color(0xFFE74C3C)
            pred < settings.targetLow  -> Color(0xFFF39C12)
            pred > settings.veryHigh   -> Color(0xFFE74C3C)
            pred > settings.targetHigh -> Color(0xFFF39C12)
            else                       -> Color(0xFF2ECC71)
        }
    } ?: Color(0xFF8E8E93)

    val lowLabel  = if (settings.unit == GlucoseUnit.MMOL) "< %.1f".format(settings.targetLow)  else "< ${settings.targetLow.toInt()}"
    val highLabel = if (settings.unit == GlucoseUnit.MMOL) "> %.1f".format(settings.targetHigh) else "> ${settings.targetHigh.toInt()}"

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            // En modo chipsOnly el gauge ya ocupa la parte superior; reducimos el padding
            // vertical para que los chips no queden demasiado separados de la gráfica.
            .padding(horizontal = 12.dp, vertical = if (chipsOnly) 4.dp else 10.dp)
            .semantics { contentDescription = "Glucosa: $displayValue $unitSuffix $trendArrow. TIR $tirPct%, Alto $highPct%, Bajo $lowPct%" },
    ) {
        if (isLandscape) {
            // ── Landscape: valor grande + unidad + flecha ────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text       = displayValue,
                    style      = MaterialTheme.typography.headlineMedium,
                    color      = glucoseColor,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(4.dp))
                Column {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = unitSuffix,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text       = trendArrow,
                    style      = MaterialTheme.typography.titleMedium,
                    color      = trendColor,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Age + delta en la misma fila compacta
            Row(
                modifier          = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (ageStr != null)
                    Text(ageStr, style = MaterialTheme.typography.labelSmall, color = ageColor)
                Spacer(Modifier.weight(1f))
                if (deltaDisplay != null)
                    Text(deltaDisplay, style = MaterialTheme.typography.labelSmall,
                        color = deltaColor, fontWeight = FontWeight.SemiBold)
            }

            // ── Grid 2×2 de chips — caben perfectamente en 210dp ─────────────────
            // Fila 1: TIR + Alto
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TirChipCompact(value = "$tirPct%",  label = "TIR",  color = Color(0xFF2ECC71), modifier = Modifier.weight(1f))
                TirChipCompact(value = "$highPct%", label = "Alto", color = Color(0xFFF39C12), modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            // Fila 2: Bajo + GMI
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TirChipCompact(value = "$lowPct%", label = "Bajo", color = Color(0xFFE74C3C), modifier = Modifier.weight(1f))
                if (gmiStr != null)
                    TirChipCompact(value = gmiStr, label = "GMI", color = gmiColor, modifier = Modifier.weight(1f))
                else
                    GmiNaChipCompact(daysRemaining = daysUntilGmi, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            // Fila 3: CV% + Pred 20min
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (cvStr != null)
                    TirChipCompact(value = cvStr, label = "CV%", color = cvColor, modifier = Modifier.weight(1f))
                else
                    Spacer(Modifier.weight(1f))
                if (predValueDisplay != null)
                    TirChipCompact(value = predValueDisplay, label = "~20min", color = predColor2, modifier = Modifier.weight(1f))
                else
                    Spacer(Modifier.weight(1f))
            }

        } else {
            // ── Portrait (panel inferior, ancho completo) ──────────────────────
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))

            // Fila 1: valor de glucosa dominante + delta + badge tendencia
            // Solo se muestra cuando chipsOnly=false (layout clásico sin gauge).
            // Cuando chipsOnly=true el valor ya aparece en el GlucoseGauge de la sección [a].
            if (!chipsOnly) {
                Row(
                    modifier          = Modifier.fillMaxWidth().padding(top = 10.dp, start = 16.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text       = displayValue,
                        style      = MaterialTheme.typography.displayMedium,
                        color      = glucoseColor,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.width(4.dp))
                    Column(verticalArrangement = Arrangement.Bottom) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text  = unitSuffix,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Δ delta compacto junto al valor
                    if (deltaDisplay != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text       = deltaDisplay,
                            style      = MaterialTheme.typography.labelLarge,
                            color      = deltaColor,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = trendColor.copy(alpha = 0.13f),
                    ) {
                        Row(
                            modifier          = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text       = trendArrow,
                                style      = MaterialTheme.typography.labelMedium,
                                color      = trendColor,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text  = trendLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = trendColor,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    // Age of data al extremo derecho
                    if (ageStr != null) {
                        Text(
                            text  = ageStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = ageColor,
                        )
                    }
                }
            }

            // Fila 2: chips TIR / Alto / Bajo
            // En chipsOnly reducimos el padding vertical para compactar la sección.
            val chipRowPadV = if (chipsOnly) 3.dp else 6.dp
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 0.dp, vertical = chipRowPadV),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TirChip(value = "$tirPct%", label = "TIR",           color = Color(0xFF2ECC71), modifier = Modifier.weight(1f))
                TirChip(value = "$highPct%",label = "Alto $highLabel",color = Color(0xFFF39C12), modifier = Modifier.weight(1f))
                TirChip(value = "$lowPct%", label = "Bajo $lowLabel", color = Color(0xFFE74C3C), modifier = Modifier.weight(1f))
            }

            // Fila 3: GMI + CV% + Predicción en la misma fila cuando chipsOnly
            // (ahorra una fila entera y hace que todo quepa sin scroll)
            if (chipsOnly) {
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (gmiStr != null)
                        TirChip(value = gmiStr, label = "GMI",  color = gmiColor, modifier = Modifier.weight(1f))
                    else
                        GmiNaChip(daysRemaining = daysUntilGmi, modifier = Modifier.weight(1f))

                    if (cvStr != null)
                        TirChip(value = cvStr, label = "CV%", color = cvColor, modifier = Modifier.weight(1f))
                    else
                        Spacer(Modifier.weight(1f))

                    // Predicción en la misma fila que GMI y CV%
                    if (predValueDisplay != null)
                        TirChip(value = predValueDisplay, label = "~20min", color = predColor2, modifier = Modifier.weight(1f))
                    else
                        Spacer(Modifier.weight(1f))
                }
            } else {
                // Layout original (dos filas separadas) cuando no es chipsOnly
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(start = 0.dp, end = 0.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (gmiStr != null)
                        TirChip(value = gmiStr, label = "GMI (HbA1c est.)", color = gmiColor, modifier = Modifier.weight(1f))
                    else
                        GmiNaChip(daysRemaining = daysUntilGmi, modifier = Modifier.weight(1f))

                    if (cvStr != null)
                        TirChip(value = cvStr, label = "CV% variabilidad", color = cvColor, modifier = Modifier.weight(1f))
                    else
                        Spacer(Modifier.weight(1f))
                }
                // Fila 4: Predicción 20min
                if (predValueDisplay != null) {
                    SecondaryChip(
                        value    = predValueDisplay,
                        label    = "Predicción ~20min",
                        color    = predColor2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                    )
                }
            }

        }
    }
}

/**
 * Muestra la fecha de fin del sensor como texto semitransparente superpuesto en la
 * esquina inferior izquierda del panel, sin ocupar espacio propio.
 * Solo aparece cuando el borde derecho visible del gráfico coincide con el último dato
 * real (es decir, el usuario está "en el presente"), para no distraer al navegar el historial.
 */
@Composable
private fun SensorExpiryOverlay(
    settings:     ChartSettings,
    entries:      List<GlucoseEntry>,
    windowEndMs:  Long,
    windowMs:     Float,
) {
    if (settings.sensorEndsMs <= 0L) return

    // Solo mostrar cuando el usuario está viendo la parte más reciente de los datos
    val latestDataMs = if (entries.isNotEmpty()) entries.maxOf { it.timestampMs } else return
    val windowStartMs = windowEndMs - windowMs.toLong()
    val isAtPresent = latestDataMs >= windowStartMs && latestDataMs <= windowEndMs

    if (!isAtPresent) return

    val context = LocalContext.current

    // Usar DateFormat del sistema: respeta el idioma y preferencias de formato del usuario
    // (DD/MM/YYYY en España, MM/DD/YYYY en EEUU, etc.)
    val sensorEndsDate = remember(settings.sensorEndsMs, context) {
        val date = java.util.Date(settings.sensorEndsMs)
        val dateFmt = android.text.format.DateFormat.getMediumDateFormat(context)
        // Usar patrón del sistema para la hora: si el usuario tiene 12h, incluye AM/PM;
        // si tiene 24h, muestra H:mm. android.text.format.DateFormat.is24HourFormat()
        // es la fuente canónica de esta preferencia en Android.
        val timePattern = if (android.text.format.DateFormat.is24HourFormat(context)) "H:mm" else "h:mm a"
        val timeFmt = java.text.SimpleDateFormat(timePattern, java.util.Locale.getDefault())
        "${dateFmt.format(date)} ${timeFmt.format(date)}"
    }

    val isExpiringSoon = settings.sensorEndsMs - System.currentTimeMillis() < 24L * 3_600_000L
    val textColor = if (isExpiringSoon) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant

    Text(
        text     = "Fin sensor: $sensorEndsDate",
        style    = MaterialTheme.typography.labelSmall,
        color    = textColor.copy(alpha = 0.65f),
        modifier = Modifier.padding(start = 16.dp, bottom = 2.dp),
    )
}

/**
 * Columna de estadística individual: valor grande coloreado + etiqueta + sub-etiqueta.
 */
@Composable
private fun StatColumn(
    value:         String,
    valueColor:    Color,
    label:         String,
    subLabel:      String,
    subLabelColor: Color,
    modifier:      Modifier = Modifier,
) {
    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text       = label,
            style      = MaterialTheme.typography.labelMedium,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text       = value,
            style      = MaterialTheme.typography.titleLarge,
            color      = valueColor,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text  = subLabel,
            style = MaterialTheme.typography.labelSmall,
            color = subLabelColor,
        )
    }
}

/**
 * Versión compacta del chip para el panel lateral landscape (210dp).
 * Usa titleSmall en lugar de titleLarge para que quepa en un grid 2×2.
 */
@Composable
private fun TirChipCompact(
    value:    String,
    label:    String,
    color:    Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(10.dp),
        color    = MaterialTheme.colorScheme.surfaceContainer,
        border   = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = color.copy(alpha = 0.30f),
        ),
    ) {
        Column(
            modifier            = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text       = value,
                style      = MaterialTheme.typography.titleSmall,
                color      = color,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text  = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Versión compacta del chip GMI deshabilitado para el panel lateral landscape.
 */
@Composable
private fun GmiNaChipCompact(daysRemaining: Int, modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }
    val grayColor = MaterialTheme.colorScheme.onSurfaceVariant
    val sublabel = when {
        daysRemaining > 0 -> "~$daysRemaining días"
        else              -> "14 días"
    }
    Surface(
        modifier = modifier.clickable { showDialog = true },
        shape    = RoundedCornerShape(10.dp),
        color    = MaterialTheme.colorScheme.surfaceContainer,
        border   = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier            = Modifier
                .alpha(0.55f)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text       = "GMI",
                style      = MaterialTheme.typography.titleSmall,
                color      = grayColor,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text  = sublabel,
                style = MaterialTheme.typography.labelSmall,
                color = grayColor,
            )
        }
    }
    if (showDialog) {
        val msg = if (daysRemaining > 0)
            "Se necesitan al menos 14 días de datos continuos del sensor para calcular una estimación fiable de la HbA1c (GMI).\n\nFaltan aproximadamente $daysRemaining día${if (daysRemaining != 1) "s" else ""}."
        else
            "Se necesitan al menos 14 días de datos continuos del sensor para calcular una estimación fiable de la HbA1c (GMI)."
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title            = { Text("GMI — Requiere 14 días de datos") },
            text             = { Text(msg) },
            confirmButton    = { TextButton(onClick = { showDialog = false }) { Text("Entendido") } },
        )
    }
}


/**
 * Chip de estadística primaria (TIR/Alto/Bajo/GMI/CV%).
 *
 * Material 3: fondo SurfaceContainer neutro, borde sutil de 1dp con el color de acento,
 * el color solo aparece en el valor numérico — no satura todo el chip.
 */
@Composable
private fun TirChip(
    value:    String,
    label:    String,
    color:    Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(12.dp),
        color    = MaterialTheme.colorScheme.surfaceContainer,
        border   = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = color.copy(alpha = 0.30f),
        ),
    ) {
        Column(
            modifier            = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text       = value,
                style      = MaterialTheme.typography.titleLarge,
                color      = color,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text  = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * Chip secundario para métricas de apoyo (predicción 20min).
 * Más compacto y menos prominente que TirChip — solo fondo neutro sin borde de color.
 */
@Composable
private fun SecondaryChip(
    value:    String,
    label:    String,
    color:    Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(10.dp),
        color    = MaterialTheme.colorScheme.surfaceContainerLow,
        border   = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier            = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text       = value,
                style      = MaterialTheme.typography.titleMedium,
                color      = color,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text  = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
        }
    }
}

/**
 * Chip deshabilitado para GMI cuando no hay ≥14 días de historial.
 *
 * Siempre visible (no se oculta) — la presencia del chip comunica que la métrica existe
 * y estará disponible automáticamente al acumular suficiente historial.
 * La sublínea indica cuántos días faltan si se conoce la cifra exacta, o bien
 * un mensaje genérico de descubrimiento ("Disponible tras 14 días").
 * Al pulsar aparece un diálogo educativo con el motivo clínico de los 14 días.
 */
@Composable
private fun GmiNaChip(daysRemaining: Int, modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }
    val grayColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Sublínea adaptada: días exactos cuando se conocen, genérica cuando no
    val sublabel = when {
        daysRemaining > 0 -> "Faltan $daysRemaining día${if (daysRemaining != 1) "s" else ""}"
        else              -> "Disponible tras 14 días"
    }

    Surface(
        modifier = modifier.clickable { showDialog = true },
        shape    = RoundedCornerShape(12.dp),
        color    = MaterialTheme.colorScheme.surfaceContainer,
        border   = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier            = Modifier
                .alpha(0.55f)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text       = "GMI",
                style      = MaterialTheme.typography.titleLarge,
                color      = grayColor,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
            )
            Text(
                text       = sublabel,
                style      = MaterialTheme.typography.labelSmall,
                color      = grayColor,
                fontWeight = FontWeight.Medium,
                textAlign  = TextAlign.Center,
            )
        }
    }

    if (showDialog) {
        val msg = if (daysRemaining > 0)
            "Se necesitan al menos 14 días de datos continuos del sensor para calcular una estimación fiable de la HbA1c (GMI).\n\nFaltan aproximadamente $daysRemaining día${if (daysRemaining != 1) "s" else ""}."
        else
            "Se necesitan al menos 14 días de datos continuos del sensor para calcular una estimación fiable de la HbA1c (GMI)."
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title            = { Text("GMI — Requiere 14 días de datos") },
            text             = { Text(msg) },
            confirmButton    = {
                TextButton(onClick = { showDialog = false }) { Text("Entendido") }
            },
        )
    }
}

/**
 * Separador vertical fino entre columnas de estadísticas (portrait).
 */
@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(60.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/**
 * Separador horizontal fino entre filas de estadísticas (landscape).
 */
@Composable
private fun StatHDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Theme
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Tema M3 para el chart y el menú Vico.
 *
 * Usa Material You (dynamicColorScheme) en Android 12+ para aprovechar
 * los colores del fondo de pantalla del usuario.
 * En versiones anteriores usa darkColorScheme / lightColorScheme estándar de M3.
 *
 * [invertColors] es el toggle "Modo oscuro" propio de Juggluco — se hace XOR
 * con el tema del sistema para que el usuario pueda forzar modo oscuro
 * independientemente del ajuste del sistema.
 */
@Composable
private fun GlucoseTheme(
    invertColors: Boolean,
    content: @Composable () -> Unit,
) {
    val darkTheme = isSystemInDarkTheme() xor invertColors
    val context   = LocalContext.current

    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) darkColorScheme() else lightColorScheme()
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
