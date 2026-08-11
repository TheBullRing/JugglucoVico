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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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
    viewModel:   GlucoseChartViewModel,
    menuActions: VicoMenuActions,
    themeColors: IntArray = IntArray(0),
    onLongPress: (GlucoseEntry) -> Unit = {},
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
        GlucoseTheme(invertColors = settings.invertColors, themeColors = themeColors) {
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
        // IMPORTANTE: NO usar remember() para envolver menuActions.copy() porque
        // remember() congela el valor hasta que cambia la key, lo que provoca que
        // el toggle "Modo oscuro" necesite varios toques si el heartbeat no ha
        // actualizado settings.invertColors en el momento de abrir el menú.
        // En su lugar, pasamos settings.invertColors directamente a VicoMenuSheet
        // como parámetro separado — el composable lo lee como State reactivo.
        if (menuVisible) {
            GlucoseTheme(invertColors = settings.invertColors, themeColors = themeColors) {
                VicoMenuSheet(
                    actions      = menuActions,
                    darkModeState = settings.invertColors,
                    onDismiss    = { menuVisible = false },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Botón de menú con popup flotante de 3 opciones
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Botón circular ⌄ que al pulsarse abre el menú principal (VicoMenuSheet).
 */
@Composable
private fun MenuButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Abrir menú" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text       = "☰",
            style      = MaterialTheme.typography.titleMedium,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
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
    onMenuClick:    () -> Unit,
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
        // Panel lateral landscape: 160dp — proporcional al panel inferior portrait
        Column(modifier = Modifier.width(160.dp).fillMaxHeight()) {
            StatsPanelWithDirection(
                entries     = entries,
                settings    = settings,
                modifier    = Modifier.fillMaxWidth().weight(1f),
                isLandscape = true,
                onMenuClick = onMenuClick,
            )
            SensorExpiryOverlay(
                settings    = settings,
                entries     = entries,
                windowEndMs = windowEndMs,
                windowMs    = windowMs,
            )
        }
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
    onMenuClick:    () -> Unit,
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
            entries     = entries,
            settings    = settings,
            modifier    = Modifier.fillMaxWidth(),
            onMenuClick = onMenuClick,
        )
        // Fecha fin de sensor — debajo del panel de stats, sin ocupar espacio fijo
        SensorExpiryOverlay(
            settings     = settings,
            entries      = entries,
            windowEndMs  = windowEndMs,
            windowMs     = windowMs,
        )
    }
}

@Composable
private fun StatsPanelWithDirection(
    entries:     List<GlucoseEntry>,
    settings:    ChartSettings,
    modifier:    Modifier = Modifier,
    isLandscape: Boolean  = false,
    onMenuClick: () -> Unit,
) {
    val direction = if (settings.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        StatsPanel(entries, settings, modifier, isLandscape, onMenuClick)
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
    onMenuClick: () -> Unit = {},
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
        latestDisplay < settings.veryLow      -> Color(0xFFE53935)
        latestDisplay < settings.targetLow    -> Color(0xFFFFB300)
        latestDisplay > settings.veryHigh     -> Color(0xFFE53935)
        latestDisplay > settings.targetHigh   -> Color(0xFFFFB300)
        else                                  -> Color(0xFF43A047)
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
            StatColumn("$lowPct%", Color(0xFFE53935),  "Bajo",    "• $lowLabel",  Color(0xFFE53935), Modifier.fillMaxWidth())
            StatHDivider()
            // Tendencia en fila compacta — muestra el valor dinámico ("Estable", "Subiendo", "Bajando")
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
                Text(trendLabel, style = MaterialTheme.typography.bodySmall, color = trendColor, fontWeight = FontWeight.SemiBold)
            }
        } else {
            // ── Portrait (panel inferior, ancho completo) ──────────────────────
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))

            // Fila 1: valor de glucosa dominante + badge tendencia + botón menú
            Row(
                modifier          = Modifier.fillMaxWidth().padding(top = 10.dp, start = 16.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Número grande — lo más importante de la pantalla
                Text(
                    text       = displayValue,
                    style      = MaterialTheme.typography.displayMedium,
                    color      = glucoseColor,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.width(6.dp))
                Column(verticalArrangement = Arrangement.Bottom) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text  = unitSuffix,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                // Badge de tendencia: texto + fondo coloreado pill
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
                // Botón menú — limpio, sin borde
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .clickable(onClick = onMenuClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text  = "☰",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Fila 2: chips TIR / Alto / Bajo con fondo real
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TirChip(
                    value    = "$tirPct%",
                    label    = "TIR",
                    color    = Color(0xFF43A047),
                    modifier = Modifier.weight(1f),
                )
                TirChip(
                    value    = "$highPct%",
                    label    = "Alto  $highLabel",
                    color    = Color(0xFFFFB300),
                    modifier = Modifier.weight(1f),
                )
                TirChip(
                    value    = "$lowPct%",
                    label    = "Bajo  $lowLabel",
                    color    = Color(0xFFE53935),   // rojo: estándar clínico (Consenso Ámsterdam)
                    modifier = Modifier.weight(1f),
                )
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
 * Chip de estadística TIR/Alto/Bajo con fondo de Card y valor destacado.
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
        color    = color.copy(alpha = 0.10f),
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
                color = color.copy(alpha = 0.75f),
                fontWeight = FontWeight.Medium,
            )
        }
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
 * Applies a Material 3 ColorScheme derived from the current Activity theme.
 *
 * Instead of using Material You / dynamic colours (which ignore the user's custom
 * Juggluco theme), we read `windowBackground` and button colours directly from the
 * Activity's resolved theme attributes — the same values that `Applic.backgroundcolor`
 * and `DynamicThemeUtils.createDynamicButton()` use on the View side.
 *
 * [invertColors] is XOR-ed with the system dark-mode preference so the app's own
 * inverted-display toggle works correctly.
 */
@Composable
private fun GlucoseTheme(
    invertColors: Boolean,
    themeColors:  IntArray = IntArray(0),
    content: @Composable () -> Unit,
) {
    val darkTheme = isSystemInDarkTheme() xor invertColors

    // themeColors[0]=bg, [1]=primary(button bg), [2]=onPrimary(button text), [3]=onSurface
    // Resolved in Java from the Activity context which has the user's Juggluco theme.
    // Usar el contenido del array como clave, no la referencia (los IntArrays no son
    // comparables por valor en remember). Convertimos a lista para una clave estable.
    val themeKey = remember(themeColors) { themeColors.toList() }
    val colorScheme = remember(themeKey, darkTheme) {
        buildJugglucoColorScheme(themeColors, darkTheme)
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}

/**
 * Builds a Material 3 [ColorScheme] from pre-resolved Activity theme colours.
 * [themeColors] is int[]{bgColor, primaryColor, onPrimaryColor, onSurfaceColor}
 * as returned by DynamicThemeUtils.getThemeColors() called from Java.
 * Falls back to M3 defaults when the array is empty (e.g. previews/tests).
 */
private fun buildJugglucoColorScheme(themeColors: IntArray, darkTheme: Boolean): androidx.compose.material3.ColorScheme {
    if (themeColors.size < 4) {
        return if (darkTheme) darkColorScheme() else lightColorScheme()
    }

    val background = Color(themeColors[0])
    val primary    = Color(themeColors[1])
    val onPrimary  = Color(themeColors[2])
    val onSurface  = Color(themeColors[3])

    // Cuando invertColors=true (modo oscuro del usuario), derivamos una paleta oscura
    // invirtiendo la luminosidad del fondo: el chart canvas y el panel de stats
    // mostrarán el color de fondo oscuro en lugar del beige/claro del tema.
    val (bgFinal, onSurfaceFinal) = if (darkTheme) {
        // Invertir la luminosidad: acercamos el fondo a negro y el texto a blanco
        val invertedBg = Color(
            red   = (1f - background.red)   * 0.85f,
            green = (1f - background.green) * 0.85f,
            blue  = (1f - background.blue)  * 0.85f,
            alpha = 1f,
        )
        val invertedOnSurface = Color(
            red   = (1f - onSurface.red).coerceIn(0.75f, 1f),
            green = (1f - onSurface.green).coerceIn(0.75f, 1f),
            blue  = (1f - onSurface.blue).coerceIn(0.75f, 1f),
            alpha = 1f,
        )
        Pair(invertedBg, invertedOnSurface)
    } else {
        Pair(background, onSurface)
    }

    val surface              = bgFinal
    val primaryContainer     = primary
    val onPrimaryContainer   = onPrimary
    val outline              = onSurfaceFinal.copy(alpha = 0.30f)

    return if (darkTheme) {
        darkColorScheme(
            primary            = primary,
            onPrimary          = onPrimary,
            primaryContainer   = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            background         = bgFinal,
            onBackground       = onSurfaceFinal,
            surface            = surface,
            onSurface          = onSurfaceFinal,
            outline            = outline,
        )
    } else {
        lightColorScheme(
            primary            = primary,
            onPrimary          = onPrimary,
            primaryContainer   = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            background         = bgFinal,
            onBackground       = onSurfaceFinal,
            surface            = surface,
            onSurface          = onSurfaceFinal,
            outline            = outline,
        )
    }
}
