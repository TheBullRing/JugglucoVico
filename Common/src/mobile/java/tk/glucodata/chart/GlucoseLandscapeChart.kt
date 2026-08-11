/*
 * Phase 6 — Landscape chart (Canvas propio, X=tiempo, Y=glucosa)
 *
 * Misma arquitectura que GlucosePortraitChart pero con los ejes en posición estándar:
 *   - Eje X = tiempo (izquierda = antiguo, derecha = reciente)
 *   - Eje Y = glucosa (abajo = bajo, arriba = alto)
 *
 * Al compartir el estado de ventana (windowEndMs / windowMs) con el chart vertical,
 * la franja de tiempo visible se mantiene al rotar el dispositivo.
 *
 * Colores de zona idénticos al chart vertical (5 zonas):
 *   - Verde     → en rango [targetLow, targetHigh]
 *   - Amarillo  → zona intermedia alta/baja (targetHigh..veryHigh o veryLow..targetLow)
 *   - Rojo      → muy alto (> veryHigh) o muy bajo (< veryLow)
 *   - Banda semitransparente verde entre las dos líneas de umbral
 */
package tk.glucodata.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

// ─────────────────────────────────────────────────────────────────────────────
// Colores de zona (idénticos a GlucosePortraitChart para consistencia visual)
// ─────────────────────────────────────────────────────────────────────────────

private val LC_COLOR_IN_RANGE    = Color(0xFF43A047)   // verde: en rango
private val LC_COLOR_WARNING     = Color(0xFFFFB300)   // amarillo: alto o bajo (zona intermedia)
private val LC_COLOR_CRITICAL    = Color(0xFFE53935)   // rojo: muy alto o muy bajo
private val LC_COLOR_TARGET_BAND = Color(0x1843A047)   // fondo verde muy suave
private val LC_COLOR_WARN_BAND   = Color(0x14FFB300)   // fondo amarillo muy suave
private val LC_COLOR_CRIT_BAND   = Color(0x14E53935)   // fondo rojo muy suave
private val LC_COLOR_GRID        = Color(0x22888888)   // cuadrícula tenue
private val LC_COLOR_AXIS_TEXT   = Color(0xFF888888)

// ─────────────────────────────────────────────────────────────────────────────
// Composable público
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Gráfico de glucosa en modo **horizontal**:
 *  - Eje X = tiempo  (izquierda = más antiguo, derecha = más reciente)
 *  - Eje Y = glucosa (abajo = valor bajo, arriba = valor alto)
 *
 * El estado de la ventana ([windowEndMs], [windowMs]) se recibe desde el padre
 * para que se preserve al rotar entre portrait y landscape.
 *
 * Gestos:
 *  - Arrastrar horizontalmente → desplazar la ventana de tiempo.
 *  - Pinch horizontal → zoom temporal (30 min … 24 h).
 */
@Composable
fun GlucoseLandscapeChart(
    entries:        List<GlucoseEntry>,
    settings:       ChartSettings,
    windowEndMs:    Long,
    windowMs:       Float,
    windowEndState: MutableLongState,
    windowMsState:  MutableFloatState,
    modifier:       Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .background(background)
            .semantics { contentDescription = "Glucose landscape chart" }
            .pointerInput(entries) {
                awaitEachGesture {
                    // Límites de scroll: nunca sobrepasar el último dato hacia el futuro
                    // ni retroceder más allá del primer dato hacia el pasado.
                    val maxEnd = if (entries.isEmpty()) Long.MAX_VALUE
                                 else entries.maxOf { it.timestampMs } + 5L * 60_000L
                    val minTs  = if (entries.isEmpty()) Long.MIN_VALUE
                                 else entries.minOf { it.timestampMs }

                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var prevX = down.position.x
                    var prevDist = -1f
                    do {
                        val event = awaitPointerEvent()
                        val changes = event.changes
                        if (changes.none { it.pressed }) break

                        if (changes.size == 1) {
                            val p  = changes[0]
                            val dx = p.position.x - prevX
                            prevDist = -1f
                            val curMs    = windowMsState.floatValue
                            // minEnd = minTs: el primer dato puede llegar al borde derecho
                            val proposed = windowEndState.longValue - (dx * curMs / size.width.toFloat()).toLong()
                            val clamped  = proposed.coerceIn(minTs, maxEnd)
                            windowEndState.longValue = clamped
                            if (clamped == proposed) prevX = p.position.x
                            p.consume()
                        } else {
                            val p0 = changes[0]; val p1 = changes[1]
                            val dist = (p0.position - p1.position).getDistance()
                            if (prevDist > 0f) {
                                val zoom = prevDist / dist
                                val curMs  = windowMsState.floatValue
                                val curEnd = windowEndState.longValue
                                val newMs  = (curMs * zoom).coerceIn(
                                    30L * 60_000L.toFloat(),
                                    24L * 3_600_000L.toFloat()
                                )
                                val centerMs = curEnd - curMs / 2f
                                windowMsState.floatValue = newMs
                                windowEndState.longValue = (centerMs + newMs / 2f).toLong().coerceIn(minTs, maxEnd)
                            }
                            prevDist = dist
                            prevX = ((p0.position.x + p1.position.x) / 2f)
                            p0.consume(); p1.consume()
                        }
                    } while (true)
                }
            }
    ) {
        val curEnd = windowEndState.longValue
        val curMs  = windowMsState.floatValue
        val windowStartMs = (curEnd - curMs).toLong()

        // bottom = 40dp: las etiquetas de hora ocupan ~26px de texto + 4dp de tick + margen.
        // Con 40dp hay espacio para la etiqueta en negrita del punto actual (más ancha).
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 48.dp, end = 8.dp, top = 8.dp, bottom = 40.dp)
        ) {
            drawLandscapeChart(
                entries       = entries,
                settings      = settings,
                windowStartMs = windowStartMs,
                windowEndMs   = curEnd,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Lógica principal de dibujo
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawLandscapeChart(
    entries:       List<GlucoseEntry>,
    settings:      ChartSettings,
    windowStartMs: Long,
    windowEndMs:   Long,
) {
    val canvasW = size.width
    val canvasH = size.height
    val unit    = settings.unit

    // ── Rango Y (glucosa) ─────────────────────────────────────────────────────
    val visibleValues = entries
        .filter { it.timestampMs in windowStartMs..windowEndMs }
        .map { lcDisplayUnit(it.valueMgdL, unit) }

    val targetLow  = settings.targetLow
    val targetHigh = settings.targetHigh
    val veryLow    = settings.veryLow.coerceAtMost(targetLow)
    val veryHigh   = settings.veryHigh.coerceAtLeast(targetHigh)

    val dataMin = if (visibleValues.isEmpty()) targetLow  else visibleValues.min()
    val dataMax = if (visibleValues.isEmpty()) targetHigh else visibleValues.max()
    val pad     = lcPaddingForUnit(unit)
    val yMin    = min(dataMin, targetLow)  - pad
    val yMax    = max(dataMax, targetHigh) + pad
    val yRange  = (yMax - yMin).coerceAtLeast(1f)

    // ── Funciones de conversión de coordenadas ────────────────────────────────
    //   timeToX   : timestamp → píxel X (izquierda = antiguo, derecha = reciente)
    //   glucoseToY: valor glucosa → píxel Y  (Y=0 es arriba: mayor glucosa → Y menor)
    fun timeToX(ms: Long): Float {
        val frac = (ms - windowStartMs).toFloat() / (windowEndMs - windowStartMs).toFloat()
        return frac * canvasW
    }
    fun glucoseToY(v: Float): Float = (1f - ((v - yMin) / yRange)) * canvasH

    // ── 5 bandas de fondo: muy bajo | bajo | en rango | alto | muy alto ──────
    val yBandTop       = glucoseToY(targetHigh)
    val yBandBottom    = glucoseToY(targetLow)
    val yBandVeryTop   = glucoseToY(veryHigh)
    val yBandVeryBot   = glucoseToY(veryLow)
    // Muy alto (rojo): de 0 a yBandVeryTop
    if (yBandVeryTop > 0f)
        drawRect(LC_COLOR_CRIT_BAND, Offset(0f, 0f),          Size(canvasW, yBandVeryTop.coerceAtLeast(0f)))
    // Alto (amarillo): de veryHigh a targetHigh
    if (yBandTop > yBandVeryTop)
        drawRect(LC_COLOR_WARN_BAND, Offset(0f, yBandVeryTop), Size(canvasW, (yBandTop - yBandVeryTop).coerceAtLeast(0f)))
    // En rango (verde)
    drawRect(LC_COLOR_TARGET_BAND,   Offset(0f, yBandTop),    Size(canvasW, (yBandBottom - yBandTop).coerceAtLeast(0f)))
    // Bajo (amarillo): de targetLow a veryLow
    if (yBandVeryBot > yBandBottom)
        drawRect(LC_COLOR_WARN_BAND, Offset(0f, yBandBottom), Size(canvasW, (yBandVeryBot - yBandBottom).coerceAtLeast(0f)))
    // Muy bajo (rojo): de veryLow a canvasH
    if (yBandVeryBot < canvasH)
        drawRect(LC_COLOR_CRIT_BAND, Offset(0f, yBandVeryBot), Size(canvasW, (canvasH - yBandVeryBot).coerceAtLeast(0f)))

    // ── Cuadrícula X (líneas verticales de tiempo) ────────────────────────────
    val gridIntervalMs = lcBestGridInterval(windowEndMs - windowStartMs)
    val firstGridMs    = (windowStartMs / gridIntervalMs + 1L) * gridIntervalMs
    var gridMs = firstGridMs
    while (gridMs <= windowEndMs) {
        val x = timeToX(gridMs)
        drawLine(
            color       = LC_COLOR_GRID,
            start       = Offset(x, 0f),
            end         = Offset(x, canvasH),
            strokeWidth = 1.dp.toPx(),
        )
        drawLcTimeLabel(gridMs, labelX = x, labelY = canvasH + 4.dp.toPx())
        gridMs += gridIntervalMs
    }

    // ── Cuadrícula Y (líneas horizontales de glucosa) ─────────────────────────
    // Misma densidad de etiquetas que el chart portrait: cada 40 mg/dL + siempre targetLow/targetHigh.
    // Los ticks intermedios (cada 20 mg/dL) se usan solo para líneas de cuadrícula tenues, sin etiqueta.
    val allTicks = lcGlucoseTickValues(yMin, yMax, unit)
    val labelStep = if (unit == GlucoseUnit.MMOL) 2f else 40f
    val labeledTicks = buildSet<Float> {
        add(targetLow); add(targetHigh)
        allTicks.forEach { if ((it % labelStep) < 0.01f) add(it) }
    }
    for (tick in allTicks) {
        val y = glucoseToY(tick)
        drawLine(
            color       = LC_COLOR_GRID,
            start       = Offset(0f, y),
            end         = Offset(canvasW, y),
            strokeWidth = 1.dp.toPx(),
        )
        if (tick in labeledTicks) {
            drawLcGlucoseLabel(tick, unit, labelX = -2.dp.toPx(), labelY = y)
        }
    }

    // ── Etiqueta de unidad (mg/dL o mmol/L) — esquina superior izquierda del canvas ──
    // Se dibuja dentro del canvas, sobre la cuadrícula, a coordenada nativa negativa en X
    // para aprovechar el margen start=48dp que tiene el Canvas.
    drawLcUnitLabel(unit, -46.dp.toPx(), 2.dp.toPx())

    // ── Solo 2 líneas de umbral (targetLow / targetHigh) ─────────────────────
    drawLine(LC_COLOR_WARNING.copy(alpha = 0.7f), Offset(0f, yBandBottom), Offset(canvasW, yBandBottom), 1.dp.toPx())
    drawLine(LC_COLOR_WARNING.copy(alpha = 0.7f), Offset(0f, yBandTop),    Offset(canvasW, yBandTop),    1.dp.toPx())

    // ── Línea de glucosa ──────────────────────────────────────────────────────
    val visible = entries
        .filter { it.timestampMs in windowStartMs..windowEndMs }
        .sortedBy { it.timestampMs }

    if (visible.isEmpty()) return

    if (visible.size == 1) {
        val e = visible[0]
        val v = lcDisplayUnit(e.valueMgdL, unit)
        drawCircle(
            color  = lcGlucoseColor(v, targetLow, targetHigh, veryLow, veryHigh),
            radius = 4.dp.toPx(),
            center = Offset(timeToX(e.timestampMs), glucoseToY(v)),
        )
        return
    }

    for ((_, sensorEntries) in visible.groupBy { it.sensorIndex }.toSortedMap()) {
        drawLandscapeLine(
            sensorEntries = sensorEntries,
            targetLow     = targetLow,
            targetHigh    = targetHigh,
            veryLow       = veryLow,
            veryHigh      = veryHigh,
            timeToX       = ::timeToX,
            glucoseToY    = ::glucoseToY,
            unit          = unit,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Línea de glucosa segmentada por color de zona
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawLandscapeLine(
    sensorEntries: List<GlucoseEntry>,
    targetLow:     Float,
    targetHigh:    Float,
    veryLow:       Float,
    veryHigh:      Float,
    timeToX:       (Long)  -> Float,
    glucoseToY:    (Float) -> Float,
    unit:          GlucoseUnit,
) {
    // Línea fina sin puntos — igual que portrait.
    val strokePx = 2.dp.toPx()
    for (i in 0 until sensorEntries.size - 1) {
        val a    = sensorEntries[i]
        val b    = sensorEntries[i + 1]
        val aVal = lcDisplayUnit(a.valueMgdL, unit)
        val bVal = lcDisplayUnit(b.valueMgdL, unit)
        drawLine(
            color       = lcGlucoseColor((aVal + bVal) / 2f, targetLow, targetHigh, veryLow, veryHigh),
            start       = Offset(timeToX(a.timestampMs), glucoseToY(aVal)),
            end         = Offset(timeToX(b.timestampMs), glucoseToY(bVal)),
            strokeWidth = strokePx,
            cap         = StrokeCap.Round,
        )
    }
    // Único punto en la lectura más reciente + etiqueta de fecha/hora sobre el canvas
    if (sensorEntries.isNotEmpty()) {
        val last  = sensorEntries.last()
        val v     = lcDisplayUnit(last.valueMgdL, unit)
        val ptX   = timeToX(last.timestampMs)
        val ptY   = glucoseToY(v)
        drawCircle(
            color  = lcGlucoseColor(v, targetLow, targetHigh, veryLow, veryHigh),
            radius = 3.dp.toPx(),
            center = Offset(ptX, ptY),
        )
        // Etiqueta de fecha/hora sobre el punto (dentro del canvas, no en el eje X)
        drawLcCurrentReadingLabel(last.timestampMs, ptX, ptY, size.width, size.height)
    }
}

/**
 * Dibuja la fecha/hora de la lectura actual DENTRO del canvas, junto al punto.
 * Aparece encima del punto si hay espacio, o debajo si está muy cerca del borde superior.
 * La alineación horizontal se ajusta para no salirse por los bordes.
 */
private fun DrawScope.drawLcCurrentReadingLabel(
    timestampMs: Long, ptX: Float, ptY: Float, canvasW: Float, canvasH: Float,
) {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestampMs }
    val timeStr = "%02d:%02d".format(
        cal.get(java.util.Calendar.HOUR_OF_DAY),
        cal.get(java.util.Calendar.MINUTE),
    )
    val months = arrayOf("ene","feb","mar","abr","may","jun","jul","ago","sep","oct","nov","dic")
    val dateStr = "${cal.get(java.util.Calendar.DAY_OF_MONTH)} ${months[cal.get(java.util.Calendar.MONTH)]}"
    val label = "$dateStr $timeStr"

    val paint = android.graphics.Paint().apply {
        color          = LC_COLOR_AXIS_TEXT.copy(alpha = 0.9f).toArgb()
        textSize       = 32f   // más grande que las etiquetas de eje (26f) para legibilidad
        isAntiAlias    = true
        isFakeBoldText = true
    }
    val textW = paint.measureText(label)
    val textH = paint.textSize

    // Alineación horizontal: intentar centrar en ptX, ajustar si se sale
    val labelX = (ptX - textW / 2f).coerceIn(2.dp.toPx(), canvasW - textW - 2.dp.toPx())
    paint.textAlign = android.graphics.Paint.Align.LEFT

    // Vertical: encima del punto con 6dp de separación; si no cabe, debajo
    val gap = 6.dp.toPx()
    val labelY = if (ptY - gap - textH > 0f) ptY - gap else ptY + gap + textH
    drawContext.canvas.nativeCanvas.drawText(label, labelX, labelY, paint)
}

// ─────────────────────────────────────────────────────────────────────────────
// Etiquetas de ejes
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawLcUnitLabel(unit: GlucoseUnit, labelX: Float, labelY: Float) {
    val text = if (unit == GlucoseUnit.MMOL) "mmol/L" else "mg/dL"
    val paint = android.graphics.Paint().apply {
        color          = LC_COLOR_AXIS_TEXT.toArgb()
        textSize       = 26f
        textAlign      = android.graphics.Paint.Align.LEFT
        isAntiAlias    = true
        isFakeBoldText = true
    }
    drawContext.canvas.nativeCanvas.drawText(text, labelX, labelY + paint.textSize, paint)
}


private fun DrawScope.drawLcTimeLabel(timestampMs: Long, labelX: Float, labelY: Float) {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestampMs }
    val text = "%02d:%02d".format(
        cal.get(java.util.Calendar.HOUR_OF_DAY),
        cal.get(java.util.Calendar.MINUTE),
    )
    val paint = android.graphics.Paint().apply {
        color       = LC_COLOR_AXIS_TEXT.toArgb()
        textSize    = 26f
        textAlign   = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(text, labelX, labelY + paint.textSize, paint)
}

private fun DrawScope.drawLcGlucoseLabel(value: Float, unit: GlucoseUnit, labelX: Float, labelY: Float) {
    val text = if (unit == GlucoseUnit.MMOL) "%.1f".format(value) else value.toInt().toString()
    val paint = android.graphics.Paint().apply {
        color       = LC_COLOR_AXIS_TEXT.toArgb()
        textSize    = 26f
        textAlign   = android.graphics.Paint.Align.RIGHT
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(text, labelX, labelY + paint.textSize / 3f, paint)
}

// ─────────────────────────────────────────────────────────────────────────────
// Utilidades
// ─────────────────────────────────────────────────────────────────────────────

private fun lcGlucoseColor(
    value:      Float,
    targetLow:  Float,
    targetHigh: Float,
    veryLow:    Float,
    veryHigh:   Float,
): Color = when {
    value < veryLow    -> LC_COLOR_CRITICAL
    value < targetLow  -> LC_COLOR_WARNING
    value > veryHigh   -> LC_COLOR_CRITICAL
    value > targetHigh -> LC_COLOR_WARNING
    else               -> LC_COLOR_IN_RANGE
}

private fun lcPaddingForUnit(unit: GlucoseUnit): Float =
    if (unit == GlucoseUnit.MMOL) 1.0f else 18f

private fun lcDisplayUnit(mgdl: Float, unit: GlucoseUnit): Float =
    if (unit == GlucoseUnit.MMOL) mgdl / 18.0f else mgdl

private fun lcBestGridInterval(windowMs: Long): Long = when {
    windowMs <= 2L  * 3_600_000L -> 30L * 60_000L
    windowMs <= 12L * 3_600_000L -> 60L * 60_000L   // 2h–12h → cada 1h
    windowMs <= 24L * 3_600_000L -> 2L  * 3_600_000L
    else                         -> 4L  * 3_600_000L
}

private fun lcGlucoseTickValues(yMin: Float, yMax: Float, unit: GlucoseUnit): List<Float> {
    val step  = if (unit == GlucoseUnit.MMOL) 1f else 20f
    val first = ceil(yMin / step) * step
    val result = mutableListOf<Float>()
    var v = first
    while (v <= yMax) {
        result += v
        v += step
    }
    return result
}
