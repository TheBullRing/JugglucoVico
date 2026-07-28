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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// ─────────────────────────────────────────────────────────────────────────────
// Public entry-point
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Root composable. Call from the ComposeView created in MainActivity.startdisplay()
 * when [tk.glucodata.Applic.USE_VICO_CHART] is true.
 *
 * @param viewModel   The chart ViewModel (created via [GlucoseChartViewModel.Factory]).
 * @param onMenuTab   Called when the user selects a menu tab from the popup.
 *                    Receives the tab index: 0=Acciones, 1=Datos, 2=Ver.
 * @param onLongPress Invoked when the user long-presses a data point.
 *                    Receives the closest [GlucoseEntry] so the existing number-entry
 *                    dialog can be opened. Replaces Natives.longpress(x, y).
 */
@Composable
fun GlucoseChartScreen(
    viewModel: GlucoseChartViewModel,
    onMenuTab: (Int) -> Unit = {},
    onLongPress: (GlucoseEntry) -> Unit = {},
) {
    val entries  by viewModel.glucoseEntries.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    // ── Estado de ventana: objetos MutableState pasados POR REFERENCIA ────────
    // Pasar los State<> directamente (no los valores extraídos) permite que los
    // lambdas de gestos lean y escriban el valor actualizado en cada evento de
    // touch, sin depender del ciclo de recomposición de Compose. Si pasáramos
    // Long/Float por valor el lambda de pointerInput (que no se recrea en cada
    // frame) vería siempre el valor capturado en la primera composición.
    val windowEndState = remember { mutableLongStateOf(0L) }
    val windowMsState  = remember { mutableFloatStateOf(8L * 3_600_000L.toFloat()) }

    // Auto-inicializar sólo la primera vez (cuando windowEndMs == 0L).
    if (windowEndState.longValue == 0L) {
        windowEndState.longValue = if (entries.isNotEmpty())
            entries.maxOf { it.timestampMs } + 5L * 60L * 1000L
        else
            System.currentTimeMillis()
    }

    // Leer los valores actuales para pasarlos como datos de dibujo.
    val windowEndMs by windowEndState
    val windowMs    by windowMsState

    GlucoseTheme(invertColors = settings.invertColors) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Glucose chart screen" },
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isLandscape = maxWidth > 600.dp
                    if (isLandscape) {
                        LandscapeLayout(
                            entries          = entries,
                            settings         = settings,
                            windowEndMs      = windowEndMs,
                            windowMs         = windowMs,
                            windowEndState   = windowEndState,
                            windowMsState    = windowMsState,
                        )
                    } else {
                        PortraitLayout(
                            entries          = entries,
                            settings         = settings,
                            windowEndMs      = windowEndMs,
                            windowMs         = windowMs,
                            windowEndState   = windowEndState,
                            windowMsState    = windowMsState,
                        )
                    }
                }
                // Botón de menú (∨) con popup de 3 opciones.
                MenuPopupButton(
                    onMenuTab = onMenuTab,
                    modifier  = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Botón de menú con popup flotante de 3 opciones
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Botón circular ⌄ que al pulsarse muestra un DropdownMenu flotante con las
 * 3 opciones de menú (Acciones / Datos / Ver). El gráfico queda visible de fondo.
 * Al seleccionar una opción se llama [onMenuTab] con el índice (0/1/2) y se cierra
 * el popup; la Activity entonces abre el menú completo en la pestaña correcta.
 */
@Composable
private fun MenuPopupButton(onMenuTab: (Int) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // ── Botón circular ─────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                .clickable { expanded = true }
                .semantics { contentDescription = "Open menu" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text       = "⌄",
                style      = MaterialTheme.typography.titleMedium,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }

        // ── Popup flotante ─────────────────────────────────────────────────
        DropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text    = { Text("Acciones") },
                onClick = { expanded = false; onMenuTab(0) },
            )
            DropdownMenuItem(
                text    = { Text("Datos") },
                onClick = { expanded = false; onMenuTab(1) },
            )
            DropdownMenuItem(
                text    = { Text("Ver") },
                onClick = { expanded = false; onMenuTab(2) },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Layout variants
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Landscape: chart Canvas (X=tiempo, Y=glucosa) a la izquierda + panel de stats a la derecha.
 * Comparte el estado de ventana con PortraitLayout para que al rotar no se pierda la posición.
 */
@Composable
private fun LandscapeLayout(
    entries:        List<GlucoseEntry>,
    settings:       ChartSettings,
    windowEndMs:    Long,
    windowMs:       Float,
    windowEndState: MutableLongState,
    windowMsState:  MutableFloatState,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        GlucoseLandscapeChart(
            entries        = entries,
            settings       = settings,
            windowEndMs    = windowEndMs,
            windowMs       = windowMs,
            windowEndState = windowEndState,
            windowMsState  = windowMsState,
            modifier       = Modifier.weight(1f).fillMaxHeight(),
        )
        // Panel lateral landscape: siempre en columna vertical
        StatsPanelWithDirection(
            entries     = entries,
            settings    = settings,
            modifier    = Modifier.width(200.dp).fillMaxHeight(),
            isLandscape = true,
        )
    }
}

/**
 * Portrait: chart Canvas transpuesto (Y=tiempo, X=glucosa) arriba + panel de stats abajo.
 * El gráfico se envuelve en una Card con bordes redondeados para separarlo visualmente del fondo.
 * Comparte el estado de ventana con LandscapeLayout para que al rotar no se pierda la posición.
 */
@Composable
private fun PortraitLayout(
    entries:        List<GlucoseEntry>,
    settings:       ChartSettings,
    windowEndMs:    Long,
    windowMs:       Float,
    windowEndState: MutableLongState,
    windowMsState:  MutableFloatState,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Tarjeta con bordes redondeados que contiene el gráfico
        Card(
            modifier  = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            shape     = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors    = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            GlucosePortraitChart(
                entries        = entries,
                settings       = settings,
                windowEndMs    = windowEndMs,
                windowMs       = windowMs,
                windowEndState = windowEndState,
                windowMsState  = windowMsState,
                modifier       = Modifier.fillMaxSize(),
            )
        }
        StatsPanelWithDirection(
            entries  = entries,
            settings = settings,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StatsPanelWithDirection(
    entries:     List<GlucoseEntry>,
    settings:    ChartSettings,
    modifier:    Modifier = Modifier,
    isLandscape: Boolean  = false,
) {
    val direction = if (settings.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        StatsPanel(entries, settings, modifier, isLandscape)
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
 * Panel de estadísticas completo:
 *  - Fila 1: valor de glucosa actual (grande, coloreado) + flecha de tendencia
 *  - Fila 2: 4 columnas — TIR 24h / Alto / Bajo / Tendencia texto
 */
@Composable
private fun StatsPanel(
    entries:     List<GlucoseEntry>,
    settings:    ChartSettings,
    modifier:    Modifier = Modifier,
    isLandscape: Boolean  = false,
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

    // Color según zona
    val glucoseColor = when {
        latestDisplay == null               -> MaterialTheme.colorScheme.onSurfaceVariant
        latestDisplay < settings.targetLow  -> Color(0xFFE53935)
        latestDisplay > settings.targetHigh -> Color(0xFFFF8F00)
        else                                -> Color(0xFF43A047)
    }

    // Tendencia — flechas diagonales como en el diseño deseado
    val (trendArrow, trendLabel) = when {
        latest == null || previous == null -> Pair("→", "Estable")
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
        "Subiendo" -> Color(0xFFFF8F00)
        "Bajando"  -> Color(0xFF1E88E5)
        else       -> Color(0xFF888888)
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

    val lowLabel  = if (settings.unit == GlucoseUnit.MMOL) "< %.1f".format(settings.targetLow)  else "< ${settings.targetLow.toInt()}"
    val highLabel = if (settings.unit == GlucoseUnit.MMOL) "> %.1f".format(settings.targetHigh) else "> ${settings.targetHigh.toInt()}"

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { contentDescription = "Glucosa: $displayValue $unitSuffix $trendArrow. TIR $tirPct%, Alto $highPct%, Bajo $lowPct%" },
    ) {
        if (isLandscape) {
            // ── Landscape fila 1: valor actual + flecha ──────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text       = displayValue,
                    style      = MaterialTheme.typography.displaySmall,
                    color      = glucoseColor,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text  = unitSuffix,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text      = trendArrow,
                    style     = MaterialTheme.typography.titleLarge,
                    color     = trendColor,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        if (isLandscape) {
            // ── Landscape (panel lateral 200dp): stats en columna vertical ──────
            StatColumn("$tirPct%", Color(0xFF43A047), "TIR 24h",  "• En rango",   Color(0xFF43A047), Modifier.fillMaxWidth())
            StatHDivider()
            StatColumn("$highPct%", Color(0xFFFF8F00), "Alto",    "• $highLabel", Color(0xFFFF8F00), Modifier.fillMaxWidth())
            StatHDivider()
            StatColumn("$lowPct%", Color(0xFF1E88E5),  "Bajo",    "• $lowLabel",  Color(0xFF1E88E5), Modifier.fillMaxWidth())
            StatHDivider()
            // Tendencia en fila compacta
            Row(
                modifier          = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(trendColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(trendArrow, style = MaterialTheme.typography.titleSmall, color = trendColor, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Tendencia", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(trendLabel, style = MaterialTheme.typography.labelSmall, color = trendColor)
                }
            }
        } else {
            // ── Portrait (panel inferior, ancho completo) ──────────────────────
            // Separador superior del panel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            // Fila 1: valor actual de glucosa + unidad + flecha de tendencia
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, start = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text       = displayValue,
                    style      = MaterialTheme.typography.displaySmall,
                    color      = glucoseColor,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text  = unitSuffix,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier         = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(trendColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text       = trendArrow,
                        style      = MaterialTheme.typography.titleMedium,
                        color      = trendColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text  = trendLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = trendColor,
                )
            }
            Spacer(Modifier.height(4.dp))
            // Fila 2: 4 columnas TIR / Alto / Bajo / (Tendencia visual ya en fila 1 — reemplazada por vacío)
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.Top,
            ) {
                // TIR 24h — valor grande verde + "En rango"
                StatColumn(
                    value         = "$tirPct%",
                    valueColor    = Color(0xFF43A047),
                    label         = "TIR 24h",
                    subLabel      = "• En rango",
                    subLabelColor = Color(0xFF43A047),
                    modifier      = Modifier.weight(1f),
                )
                StatDivider()
                // Alto — valor grande naranja + "> umbral mg/dL"
                StatColumn(
                    value         = "$highPct%",
                    valueColor    = Color(0xFFFF8F00),
                    label         = "Alto",
                    subLabel      = "• $highLabel",
                    subLabelColor = Color(0xFFFF8F00),
                    modifier      = Modifier.weight(1f),
                )
                StatDivider()
                // Bajo — valor grande azul + "< umbral mg/dL"
                StatColumn(
                    value         = "$lowPct%",
                    valueColor    = Color(0xFF1E88E5),
                    label         = "Bajo",
                    subLabel      = "• $lowLabel",
                    subLabelColor = Color(0xFF1E88E5),
                    modifier      = Modifier.weight(1f),
                )
            }
        }
    }
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
 * Applies Material 3 / Material You theme.
 * Uses dynamic colour on Android 12+ (API 31) and a static fallback on older devices.
 * [invertColors] is XOR-ed with the system dark-mode preference so the app's own
 * inverted-display toggle works correctly.
 */
@Composable
private fun GlucoseTheme(
    invertColors: Boolean,
    content: @Composable () -> Unit,
) {
    val context   = LocalContext.current
    val darkTheme = isSystemInDarkTheme() xor invertColors

    val colorScheme = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) darkColorScheme() else lightColorScheme()
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
