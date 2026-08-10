/*
 * Phase 6 - Portrait chart (transpuesto)
 *
 * EJE Y = TIEMPO (arriba = mas antiguo, abajo = mas reciente)
 * EJE X = GLUCOSA (izquierda = valor bajo, derecha = valor alto)
 *
 * Zonas de color de fondo (izquierda a derecha):
 *   - Rojo/rosa semitransparente   -> por debajo de targetLow
 *   - Verde semitransparente       -> entre targetLow y targetHigh
 *   - Naranja/dorado semitransparente -> por encima de targetHigh
 *
 * Colores de puntos y lineas por valor:
 *   - Verde  -> dentro del rango objetivo (targetLow..targetHigh)
 *   - Naranja -> por encima del rango (targetHigh..targetHigh+highDelta)
 *   - Rojo   -> muy por encima (> targetHigh+highDelta) o por debajo de targetLow
 *
 * Eje X superior: etiqueta "mg/dL"/"mmol/L" alineada a la izquierda, ticks con "220+".
 * Eje X inferior: mismos valores en espejo.
 * Cuadricula horizontal punteada en cada hora + lineas de umbral solidas coloreadas.
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

private val COLOR_IN_RANGE    = Color(0xFF43A047)
private val COLOR_ABOVE_RANGE = Color(0xFFFF8F00)
private val COLOR_CRITICAL    = Color(0xFFE53935)
private val COLOR_TARGET_BAND = Color(0x2543A047)  // verde suave
private val COLOR_HIGH_BAND   = Color(0x22FF8F00)  // naranja suave
private val COLOR_LOW_BAND    = Color(0x22E53935)  // rojo suave
private val COLOR_GRID        = Color(0x33888888)  // cuadricula mas visible
private val COLOR_AXIS_TEXT   = Color(0xFF888888)
private val COLOR_AXIS_TICK   = Color(0xFFAAAAAA)

private const val HIGH_DELTA_MGDL = 40f
private const val HIGH_DELTA_MMOL = 2.2f

@Composable
fun GlucosePortraitChart(
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
            .semantics { contentDescription = "Glucose portrait chart" }
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
                    var prevY    = down.position.y
                    var prevDist = -1f
                    do {
                        val event   = awaitPointerEvent()
                        val changes = event.changes
                        if (changes.none { it.pressed }) break
                        if (changes.size == 1) {
                            val p  = changes[0]
                            val dy = p.position.y - prevY
                            prevY    = p.position.y
                            prevDist = -1f
                            val curMs = windowMsState.floatValue
                            val minEnd = minTs + curMs.toLong()
                            val proposed = windowEndState.longValue - (dy * curMs / size.height.toFloat()).toLong()
                            windowEndState.longValue = proposed.coerceIn(minEnd, maxEnd)
                            p.consume()
                        } else {
                            val p0   = changes[0]; val p1 = changes[1]
                            val dist = (p0.position - p1.position).getDistance()
                            if (prevDist > 0f) {
                                val zoom     = prevDist / dist
                                val curMs    = windowMsState.floatValue
                                val curEnd   = windowEndState.longValue
                                val newMs    = (curMs * zoom).coerceIn(30L * 60_000L.toFloat(), 24L * 3_600_000L.toFloat())
                                val centerMs = curEnd - curMs / 2f
                                windowMsState.floatValue = newMs
                                val minEnd = minTs + newMs.toLong()
                                windowEndState.longValue = (centerMs + newMs / 2f).toLong().coerceIn(minEnd, maxEnd)
                            }
                            prevDist = dist
                            prevY    = (p0.position.y + p1.position.y) / 2f
                            p0.consume(); p1.consume()
                        }
                    } while (true)
                }
            }
    ) {
        val curEnd        = windowEndState.longValue
        val curMs         = windowMsState.floatValue
        val windowStartMs = (curEnd - curMs).toLong()
        // paddingStart amplio para etiquetas de tiempo (52dp), top para etiqueta de unidad + ticks (52dp)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 56.dp, end = 12.dp, top = 52.dp, bottom = 36.dp)
        ) {
            drawPortraitChart(entries, settings, windowStartMs, curEnd)
        }
    }
}

private fun DrawScope.drawPortraitChart(
    entries:       List<GlucoseEntry>,
    settings:      ChartSettings,
    windowStartMs: Long,
    windowEndMs:   Long,
) {
    val canvasW = size.width
    val canvasH = size.height
    val unit    = settings.unit

    val visibleValues = entries
        .filter { it.timestampMs in windowStartMs..windowEndMs }
        .map { toDisplayUnit(it.valueMgdL, unit) }
    val targetLow  = settings.targetLow
    val targetHigh = settings.targetHigh
    val dataMin = if (visibleValues.isEmpty()) targetLow  else visibleValues.min()
    val dataMax = if (visibleValues.isEmpty()) targetHigh else visibleValues.max()
    val pad     = paddingForUnit(unit)
    val xMin    = min(dataMin, targetLow)  - pad
    val xMax    = max(dataMax, targetHigh) + pad
    val xRange  = (xMax - xMin).coerceAtLeast(1f)

    val ticks = glucoseTickValues(xMin, xMax, unit)

    fun timeToY(ms: Long): Float =
        (ms - windowStartMs).toFloat() / (windowEndMs - windowStartMs).toFloat() * canvasH
    fun glucoseToX(v: Float): Float = (v - xMin) / xRange * canvasW

    val xBandLeft  = glucoseToX(targetLow)
    val xBandRight = glucoseToX(targetHigh)

    // ── 3 bandas de fondo ────────────────────────────────────────────────────
    if (xBandLeft > 0f)
        drawRect(COLOR_LOW_BAND,    Offset(0f, 0f),                           Size(xBandLeft.coerceAtLeast(0f), canvasH))
    drawRect(COLOR_TARGET_BAND,     Offset(xBandLeft.coerceAtLeast(0f), 0f),  Size((xBandRight - xBandLeft).coerceAtLeast(0f), canvasH))
    if (xBandRight < canvasW)
        drawRect(COLOR_HIGH_BAND,   Offset(xBandRight.coerceAtLeast(0f), 0f), Size((canvasW - xBandRight).coerceAtLeast(0f), canvasH))

    // ── Cuadricula horizontal (lineas de hora) punteada ──────────────────────
    val gridIntervalMs = bestGridInterval(windowEndMs - windowStartMs)
    val firstGridMs    = (windowStartMs / gridIntervalMs + 1L) * gridIntervalMs
    var gridMs = firstGridMs
    while (gridMs <= windowEndMs) {
        val y = timeToY(gridMs)
        drawDashedHLine(y, 0f, canvasW, COLOR_GRID, 1.2.dp.toPx(), 8.dp.toPx(), 5.dp.toPx())
        // Etiqueta de hora en el margen izquierdo (fuera del canvas = coordenada negativa)
        drawTimeLabel(gridMs, -6.dp.toPx(), y)
        gridMs += gridIntervalMs
    }

    // ── Ticks y cuadricula vertical (glucosa) ─────────────────────────────────
    val lastTickIdx = ticks.lastIndex
    for ((i, tick) in ticks.withIndex()) {
        val x = glucoseToX(tick)
        if (x < -0.5f || x > canvasW + 0.5f) continue
        val isThreshold = (tick == targetLow || tick == targetHigh)
        if (!isThreshold) {
            // Linea de cuadricula vertical fina
            drawLine(COLOR_GRID, Offset(x, 0f), Offset(x, canvasH), 0.8.dp.toPx())
        }
        // Ticks pequenos en el borde superior e inferior del canvas
        drawLine(COLOR_AXIS_TICK, Offset(x, 0f),     Offset(x, 4.dp.toPx()),  1.dp.toPx())
        drawLine(COLOR_AXIS_TICK, Offset(x, canvasH), Offset(x, canvasH - 4.dp.toPx()), 1.dp.toPx())
        val isLast = (i == lastTickIdx)
        // Etiqueta en la parte superior (encima del canvas, y negativa) e inferior
        drawGlucoseLabel(tick, unit, x, -36.dp.toPx(), isLast = isLast)
        drawGlucoseLabel(tick, unit, x, canvasH + 4.dp.toPx(), isLast = isLast)
    }

    // ── Etiqueta de unidad (arriba izquierda, alineada al margen) ────────────
    drawUnitLabel(unit, -56.dp.toPx(), -52.dp.toPx())

    // ── Lineas de umbral solidas coloreadas (mas gruesas) ────────────────────
    drawLine(COLOR_CRITICAL,    Offset(xBandLeft,  0f), Offset(xBandLeft,  canvasH), 2.dp.toPx())
    drawLine(COLOR_ABOVE_RANGE, Offset(xBandRight, 0f), Offset(xBandRight, canvasH), 2.dp.toPx())

    // ── Curva de glucosa ─────────────────────────────────────────────────────
    val visible = entries
        .filter { it.timestampMs in windowStartMs..windowEndMs }
        .sortedBy { it.timestampMs }
    if (visible.isEmpty()) return
    if (visible.size == 1) {
        val e = visible[0]; val v = toDisplayUnit(e.valueMgdL, unit)
        drawCircle(glucoseColor(v, targetLow, targetHigh, unit), 5.dp.toPx(), Offset(glucoseToX(v), timeToY(e.timestampMs)))
        return
    }
    for ((_, se) in visible.groupBy { it.sensorIndex }.toSortedMap())
        drawGlucoseLine(se, targetLow, targetHigh, ::timeToY, ::glucoseToX, unit)
}

private fun DrawScope.drawGlucoseLine(
    sensorEntries: List<GlucoseEntry>,
    targetLow:     Float,
    targetHigh:    Float,
    timeToY:       (Long) -> Float,
    glucoseToX:    (Float) -> Float,
    unit:          GlucoseUnit,
) {
    val strokePx  = 2.5.dp.toPx()
    val dotRadius = 5.dp.toPx()

    // Segmentos de linea coloreados segun el punto medio
    for (i in 0 until sensorEntries.size - 1) {
        val a    = sensorEntries[i]; val b = sensorEntries[i + 1]
        val aVal = toDisplayUnit(a.valueMgdL, unit)
        val bVal = toDisplayUnit(b.valueMgdL, unit)
        drawLine(
            glucoseColor((aVal + bVal) / 2f, targetLow, targetHigh, unit),
            Offset(glucoseToX(aVal), timeToY(a.timestampMs)),
            Offset(glucoseToX(bVal), timeToY(b.timestampMs)),
            strokePx,
            cap = StrokeCap.Round,
        )
    }
    // Puntos individuales coloreados
    for (e in sensorEntries) {
        val v = toDisplayUnit(e.valueMgdL, unit)
        drawCircle(
            glucoseColor(v, targetLow, targetHigh, unit),
            dotRadius,
            Offset(glucoseToX(v), timeToY(e.timestampMs)),
        )
    }
}

private fun DrawScope.drawDashedHLine(
    y: Float, xStart: Float, xEnd: Float, color: Color,
    strokeWidth: Float, dashLength: Float, gapLength: Float,
) {
    var x = xStart; val total = dashLength + gapLength
    while (x < xEnd) {
        val e = min(x + dashLength, xEnd)
        drawLine(color, Offset(x, y), Offset(e, y), strokeWidth)
        x += total
    }
}

private fun DrawScope.drawTimeLabel(timestampMs: Long, labelX: Float, labelY: Float) {
    val cal  = java.util.Calendar.getInstance().apply { timeInMillis = timestampMs }
    val text = "%02d:%02d".format(
        cal.get(java.util.Calendar.HOUR_OF_DAY),
        cal.get(java.util.Calendar.MINUTE),
    )
    val paint = android.graphics.Paint().apply {
        color       = COLOR_AXIS_TEXT.toArgb()
        textSize    = 30f
        textAlign   = android.graphics.Paint.Align.RIGHT
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(text, labelX, labelY + paint.textSize / 3f, paint)
}

private fun DrawScope.drawGlucoseLabel(
    value: Float, unit: GlucoseUnit, labelX: Float, labelY: Float, isLast: Boolean = false,
) {
    val numStr = if (unit == GlucoseUnit.MMOL) "%.1f".format(value) else value.toInt().toString()
    val text   = if (isLast) "$numStr+" else numStr
    val paint  = android.graphics.Paint().apply {
        color       = COLOR_AXIS_TEXT.toArgb()
        textSize    = 26f
        textAlign   = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(text, labelX, labelY + paint.textSize, paint)
}

private fun DrawScope.drawUnitLabel(unit: GlucoseUnit, labelX: Float, labelY: Float) {
    val text  = if (unit == GlucoseUnit.MMOL) "mmol/L" else "mg/dL"
    val paint = android.graphics.Paint().apply {
        color          = COLOR_AXIS_TEXT.toArgb()
        textSize       = 28f
        textAlign      = android.graphics.Paint.Align.LEFT
        isAntiAlias    = true
        isFakeBoldText = true
    }
    drawContext.canvas.nativeCanvas.drawText(text, labelX, labelY + paint.textSize, paint)
}

private fun glucoseColor(value: Float, targetLow: Float, targetHigh: Float, unit: GlucoseUnit): Color {
    val highDelta = if (unit == GlucoseUnit.MMOL) HIGH_DELTA_MMOL else HIGH_DELTA_MGDL
    return when {
        value < targetLow              -> COLOR_CRITICAL
        value > targetHigh + highDelta -> COLOR_CRITICAL
        value > targetHigh             -> COLOR_ABOVE_RANGE
        else                           -> COLOR_IN_RANGE
    }
}

private fun paddingForUnit(unit: GlucoseUnit): Float = if (unit == GlucoseUnit.MMOL) 1.0f else 18f

private fun toDisplayUnit(mgdl: Float, unit: GlucoseUnit): Float =
    if (unit == GlucoseUnit.MMOL) mgdl / 18.0f else mgdl

private fun bestGridInterval(windowMs: Long): Long = when {
    windowMs <= 2L  * 3_600_000L -> 30L * 60_000L
    windowMs <= 6L  * 3_600_000L -> 60L * 60_000L
    windowMs <= 12L * 3_600_000L ->  2L * 3_600_000L
    else                          ->  4L * 3_600_000L
}

private fun glucoseTickValues(xMin: Float, xMax: Float, unit: GlucoseUnit): List<Float> {
    val step  = if (unit == GlucoseUnit.MMOL) 2f else 20f
    val first = ceil(xMin / step) * step
    val result = mutableListOf<Float>()
    var v = first
    while (v <= xMax + step * 0.01f) { result += v; v += step }
    return result
}