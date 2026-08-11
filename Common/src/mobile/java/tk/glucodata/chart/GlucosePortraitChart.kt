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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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

private val COLOR_IN_RANGE    = Color(0xFF43A047)   // verde: en rango
private val COLOR_WARNING     = Color(0xFFFFB300)   // amarillo: alto o bajo (zona intermedia)
private val COLOR_CRITICAL    = Color(0xFFE53935)   // rojo: muy alto o muy bajo
private val COLOR_TARGET_BAND = Color(0x1843A047)   // fondo verde muy suave: en rango
private val COLOR_WARN_BAND   = Color(0x14FFB300)   // fondo amarillo muy suave: zona intermedia
private val COLOR_CRIT_BAND   = Color(0x14E53935)   // fondo rojo muy suave: muy alto / muy bajo
private val COLOR_GRID        = Color(0x22888888)   // cuadrícula tenue
private val COLOR_AXIS_TEXT   = Color(0xFF888888)
private val COLOR_AXIS_TICK   = Color(0xFFAAAAAA)

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
                            prevDist = -1f
                            val curMs    = windowMsState.floatValue
                            // minEnd = minTs: el primer dato puede llegar hasta el borde derecho
                            // (windowEnd = minTs → windowStart = minTs - windowMs, ventana vacía
                            // a la izquierda del primer dato). Permite navegar las 8h completas.
                            val minEnd   = minTs
                            val proposed = windowEndState.longValue - (dy * curMs / size.height.toFloat()).toLong()
                            val clamped  = proposed.coerceIn(minEnd, maxEnd)
                            windowEndState.longValue = clamped
                            if (clamped == proposed) prevY = p.position.y
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
                                windowEndState.longValue = (centerMs + newMs / 2f).toLong().coerceIn(minTs, maxEnd)
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
        // paddingStart para etiquetas de tiempo, bottom para etiquetas de glucosa inferiores.
        // top mínimo: solo espacio para los ticks (8dp). La etiqueta "mg/dL" se dibuja
        // en el margen izquierdo dentro del propio canvas para no crear espacio en blanco.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 44.dp, end = 12.dp, top = 8.dp, bottom = 36.dp)
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

    val targetLow  = settings.targetLow
    val targetHigh = settings.targetHigh
    val veryLow    = settings.veryLow.coerceAtMost(targetLow)
    val veryHigh   = settings.veryHigh.coerceAtLeast(targetHigh)

    val visibleValues = entries
        .filter { it.timestampMs in windowStartMs..windowEndMs }
        .map { toDisplayUnit(it.valueMgdL, unit) }
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

    val xBandLeft      = glucoseToX(targetLow)
    val xBandRight     = glucoseToX(targetHigh)
    val xBandVeryLeft  = glucoseToX(veryLow).coerceAtLeast(0f)
    val xBandVeryRight = glucoseToX(veryHigh).coerceAtMost(canvasW)

    // ── 5 bandas de fondo: muy bajo | bajo | en rango | alto | muy alto ──────
    // Muy bajo (rojo): de 0 a veryLow
    if (xBandVeryLeft > 0f)
        drawRect(COLOR_CRIT_BAND,   Offset(0f, 0f),                    Size(xBandVeryLeft, canvasH))
    // Bajo (amarillo): de veryLow a targetLow
    if (xBandLeft > xBandVeryLeft)
        drawRect(COLOR_WARN_BAND,   Offset(xBandVeryLeft, 0f),         Size((xBandLeft - xBandVeryLeft).coerceAtLeast(0f), canvasH))
    // En rango (verde): de targetLow a targetHigh
    drawRect(COLOR_TARGET_BAND,     Offset(xBandLeft.coerceAtLeast(0f), 0f), Size((xBandRight - xBandLeft).coerceAtLeast(0f), canvasH))
    // Alto (amarillo): de targetHigh a veryHigh
    if (xBandVeryRight > xBandRight)
        drawRect(COLOR_WARN_BAND,   Offset(xBandRight.coerceAtLeast(0f), 0f), Size((xBandVeryRight - xBandRight).coerceAtLeast(0f), canvasH))
    // Muy alto (rojo): de veryHigh a borde derecho
    if (xBandVeryRight < canvasW)
        drawRect(COLOR_CRIT_BAND,   Offset(xBandVeryRight, 0f),        Size((canvasW - xBandVeryRight).coerceAtLeast(0f), canvasH))

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

    // ── Ticks y cuadrícula vertical (glucosa) ────────────────────────────────
    // Solo mostramos etiqueta/tick en los umbrales (targetLow/targetHigh) y en los
    // valores extremos del eje. Las intermedias cada 20 mg/dL se suprimen para
    // no saturar el gráfico — misma decisión que Dexcom Clarity y LibreLink.
    val labeledTicks = buildSet<Float> {
        // Umbrales siempre visibles (targetLow y targetHigh configurados por el usuario)
        add(targetLow); add(targetHigh)
        // Múltiplos redondos de 40 mg/dL (2 mmol/L) para escala regular y legible.
        // No se añade primer/último tick del rango: evita valores raros como 42, 63, etc.
        val step = if (unit == GlucoseUnit.MMOL) 2f else 40f
        ticks.forEach { if ((it % step) < 0.01f) add(it) }
    }
    val lastTickIdx = ticks.lastIndex
    for ((i, tick) in ticks.withIndex()) {
        val x = glucoseToX(tick)
        if (x < -0.5f || x > canvasW + 0.5f) continue
        val isThreshold = (tick == targetLow || tick == targetHigh)
        val showLabel   = tick in labeledTicks
        if (!isThreshold) {
            // Línea de cuadrícula: solo en ticks etiquetados, muy tenue
            if (showLabel)
                drawLine(COLOR_GRID, Offset(x, 0f), Offset(x, canvasH), 0.6.dp.toPx())
        }
        if (showLabel) {
            // Tick pequeño solo en el borde inferior (el superior ya no tiene margen suficiente)
            drawLine(COLOR_AXIS_TICK, Offset(x, canvasH), Offset(x, canvasH - 4.dp.toPx()), 1.dp.toPx())
            val isLast = (i == lastTickIdx)
            // Etiqueta solo en el borde inferior — integrada sin espacio en blanco extra
            drawGlucoseLabel(tick, unit, x, canvasH + 4.dp.toPx(), isLast = isLast)
        }
    }

    // ── Etiqueta de unidad: esquina superior izquierda dentro del canvas ──────
    // Coordenadas positivas → dentro del área del canvas, no en el margen de horas
    drawUnitLabel(unit, 4.dp.toPx(), 4.dp.toPx())

    // ── Umbrales targetLow / targetHigh: líneas discontinuas sutiles ─────────
    drawDashedVLine(xBandLeft,  0f, canvasH, COLOR_WARNING.copy(alpha = 0.55f), 1.dp.toPx(), 5.dp.toPx(), 4.dp.toPx())
    drawDashedVLine(xBandRight, 0f, canvasH, COLOR_WARNING.copy(alpha = 0.55f), 1.dp.toPx(), 5.dp.toPx(), 4.dp.toPx())

    // ── Curva de glucosa ─────────────────────────────────────────────────────
    val visible = entries
        .filter { it.timestampMs in windowStartMs..windowEndMs }
        .sortedBy { it.timestampMs }
    if (visible.isEmpty()) return
    if (visible.size == 1) {
        val e = visible[0]; val v = toDisplayUnit(e.valueMgdL, unit)
        drawCircle(glucoseColor(v, targetLow, targetHigh, veryLow, veryHigh), 5.dp.toPx(), Offset(glucoseToX(v), timeToY(e.timestampMs)))
        return
    }
    for ((_, se) in visible.groupBy { it.sensorIndex }.toSortedMap())
        drawGlucoseLine(se, targetLow, targetHigh, veryLow, veryHigh, ::timeToY, ::glucoseToX, unit, canvasH)
}

private fun DrawScope.drawGlucoseLine(
    sensorEntries: List<GlucoseEntry>,
    targetLow:     Float,
    targetHigh:    Float,
    veryLow:       Float,
    veryHigh:      Float,
    timeToY:       (Long) -> Float,
    glucoseToX:    (Float) -> Float,
    unit:          GlucoseUnit,
    canvasH:       Float,
) {
    if (sensorEntries.isEmpty()) return

    // ── 1. Área de relleno con gradiente horizontal (eje transpuesto) ─────────
    // En el gráfico portrait el eje X es glucosa, el eje Y es tiempo.
    // El relleno va desde la curva hasta el borde IZQUIERDO del canvas (valor mínimo),
    // creando un gradiente que se desvanece hacia la izquierda — exactamente como
    // el gradiente vertical en los gráficos landscape estándar.
    val fillPath = Path().apply {
        // Empezar en el primer punto pegado al borde izquierdo
        val firstVal = toDisplayUnit(sensorEntries.first().valueMgdL, unit)
        moveTo(0f, timeToY(sensorEntries.first().timestampMs))
        lineTo(glucoseToX(firstVal), timeToY(sensorEntries.first().timestampMs))
        // Recorrer todos los puntos
        for (i in 1 until sensorEntries.size) {
            val v = toDisplayUnit(sensorEntries[i].valueMgdL, unit)
            lineTo(glucoseToX(v), timeToY(sensorEntries[i].timestampMs))
        }
        // Cerrar por el borde izquierdo (x=0) recorriendo de vuelta en Y
        lineTo(0f, timeToY(sensorEntries.last().timestampMs))
        close()
    }
    // Color del relleno: zona del último punto (lectura más reciente)
    val lastVal   = toDisplayUnit(sensorEntries.last().valueMgdL, unit)
    val fillColor = glucoseColor(lastVal, targetLow, targetHigh, veryLow, veryHigh)
    val fillBrush = Brush.horizontalGradient(
        colors = listOf(Color.Transparent, fillColor.copy(alpha = 0.18f)),
        startX = 0f,
        endX   = glucoseToX(lastVal).coerceAtLeast(1f),
    )
    drawPath(fillPath, fillBrush)

    // ── 2. Línea principal ────────────────────────────────────────────────────
    val strokePx = 2.5.dp.toPx()
    for (i in 0 until sensorEntries.size - 1) {
        val a    = sensorEntries[i]; val b = sensorEntries[i + 1]
        val aVal = toDisplayUnit(a.valueMgdL, unit)
        val bVal = toDisplayUnit(b.valueMgdL, unit)
        drawLine(
            glucoseColor((aVal + bVal) / 2f, targetLow, targetHigh, veryLow, veryHigh),
            Offset(glucoseToX(aVal), timeToY(a.timestampMs)),
            Offset(glucoseToX(bVal), timeToY(b.timestampMs)),
            strokePx,
            cap = StrokeCap.Round,
        )
    }

    // ── 3. Punto final con halo (lectura más reciente) ────────────────────────
    val last     = sensorEntries.last()
    val lastDisp = toDisplayUnit(last.valueMgdL, unit)
    val lastColor = glucoseColor(lastDisp, targetLow, targetHigh, veryLow, veryHigh)
    val cx = glucoseToX(lastDisp)
    val cy = timeToY(last.timestampMs)
    drawCircle(lastColor.copy(alpha = 0.20f), 8.dp.toPx(),  Offset(cx, cy))  // halo exterior
    drawCircle(lastColor,                     4.dp.toPx(),  Offset(cx, cy))  // punto sólido
    drawCircle(Color.White.copy(alpha = 0.8f),1.5.dp.toPx(),Offset(cx, cy)) // brillo central

    // ── 4. Etiqueta de fecha/hora junto al punto actual ───────────────────────
    // Siempre visible: permite ubicarse fácilmente en historiales de días anteriores.
    // Formato: "DD MMM HH:mm" si el día difiere del actual, "HH:mm" si es hoy.
    drawCurrentReadingLabel(last.timestampMs, cx, cy)
}

/**
 * Dibuja "HH:mm" o "DD MMM HH:mm" junto al punto de lectura actual en portrait.
 * La etiqueta aparece a la derecha del punto (con margen) alineada verticalmente al centro.
 * Si no hay espacio a la derecha (cx > 80% del canvas), aparece a la izquierda.
 */
private fun DrawScope.drawCurrentReadingLabel(timestampMs: Long, cx: Float, cy: Float) {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestampMs }
    val timeStr = "%02d:%02d".format(
        cal.get(java.util.Calendar.HOUR_OF_DAY),
        cal.get(java.util.Calendar.MINUTE),
    )
    val months = arrayOf("ene","feb","mar","abr","may","jun","jul","ago","sep","oct","nov","dic")
    val dateStr = "${cal.get(java.util.Calendar.DAY_OF_MONTH)} ${months[cal.get(java.util.Calendar.MONTH)]}"
    // Siempre fecha + hora: el usuario necesita ubicarse en el historial incluso navegando hoy
    val label = "$dateStr $timeStr"

    val paint = android.graphics.Paint().apply {
        color       = COLOR_AXIS_TEXT.toArgb()
        textSize    = 34f   // suficientemente grande para leer con un vistazo rápido
        isAntiAlias = true
        isFakeBoldText = true
    }
    val textW = paint.measureText(label)
    val margin = 10.dp.toPx()
    // Colocar a la derecha si cabe, a la izquierda en caso contrario
    val labelX = if (cx + margin + textW < size.width) {
        paint.textAlign = android.graphics.Paint.Align.LEFT
        cx + margin
    } else {
        paint.textAlign = android.graphics.Paint.Align.RIGHT
        cx - margin
    }
    drawContext.canvas.nativeCanvas.drawText(label, labelX, cy + paint.textSize / 3f, paint)
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

private fun DrawScope.drawDashedVLine(
    x: Float, yStart: Float, yEnd: Float, color: Color,
    strokeWidth: Float, dashLength: Float, gapLength: Float,
) {
    var y = yStart; val total = dashLength + gapLength
    while (y < yEnd) {
        val e = min(y + dashLength, yEnd)
        drawLine(color, Offset(x, y), Offset(x, e), strokeWidth)
        y += total
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
        textSize    = 26f
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

/**
 * Devuelve el color del punto/línea según 5 zonas:
 *   < veryLow              → rojo  (muy bajo)
 *   veryLow .. targetLow   → amarillo (bajo)
 *   targetLow .. targetHigh → verde  (en rango)
 *   targetHigh .. veryHigh → amarillo (alto)
 *   > veryHigh             → rojo  (muy alto)
 */
private fun glucoseColor(
    value:      Float,
    targetLow:  Float,
    targetHigh: Float,
    veryLow:    Float,
    veryHigh:   Float,
): Color = when {
    value < veryLow    -> COLOR_CRITICAL
    value < targetLow  -> COLOR_WARNING
    value > veryHigh   -> COLOR_CRITICAL
    value > targetHigh -> COLOR_WARNING
    else               -> COLOR_IN_RANGE
}

private fun paddingForUnit(unit: GlucoseUnit): Float = if (unit == GlucoseUnit.MMOL) 1.0f else 18f

private fun toDisplayUnit(mgdl: Float, unit: GlucoseUnit): Float =
    if (unit == GlucoseUnit.MMOL) mgdl / 18.0f else mgdl

private fun bestGridInterval(windowMs: Long): Long = when {
    windowMs <= 2L  * 3_600_000L -> 30L * 60_000L
    windowMs <= 12L * 3_600_000L -> 60L * 60_000L   // 2h–12h → cada 1h
    windowMs <= 24L * 3_600_000L ->  2L * 3_600_000L
    else                          ->  4L * 3_600_000L
}

// Ticks cada 20 mg/dL para tener base de cálculo; los que se muestran se filtran en drawPortraitChart
private fun glucoseTickValues(xMin: Float, xMax: Float, unit: GlucoseUnit): List<Float> {
    val step  = if (unit == GlucoseUnit.MMOL) 2f else 20f
    val first = ceil(xMin / step) * step
    val result = mutableListOf<Float>()
    var v = first
    while (v <= xMax + step * 0.01f) { result += v; v += step }
    return result
}