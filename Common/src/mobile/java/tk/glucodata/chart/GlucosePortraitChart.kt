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

import tk.glucodata.Natives

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
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

private val COLOR_IN_RANGE    = Color(0xFF2ECC71)   // verde accesible: en rango
private val COLOR_WARNING     = Color(0xFFF39C12)   // naranja accesible: zona intermedia
private val COLOR_CRITICAL    = Color(0xFFE74C3C)   // rojo accesible: fuera de rango extremo
private val COLOR_TARGET_BAND = Color(0x1E2ECC71)   // franja objetivo ~12% alpha
private val COLOR_GRID        = Color(0x1A8E8E93)   // cuadrícula 10% alpha neutro
private val COLOR_AXIS_TEXT   = Color(0xFF8E8E93)   // texto ejes: gris neutro iOS/Material
private val COLOR_AXIS_TICK   = Color(0xFFBBBBBB)

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

        // Pulso animado del punto actual: halo que oscila entre 6dp y 14dp
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseRadius by infiniteTransition.animateFloat(
            initialValue = 6f,
            targetValue  = 14f,
            animationSpec = infiniteRepeatable(
                animation  = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseRadius",
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 44.dp, end = 12.dp, top = 8.dp, bottom = 36.dp)
        ) {
            drawPortraitChart(entries, settings, windowStartMs, curEnd, pulseRadius.dp.toPx())
        }
    }
}

private fun DrawScope.drawPortraitChart(
    entries:       List<GlucoseEntry>,
    settings:      ChartSettings,
    windowStartMs: Long,
    windowEndMs:   Long,
    pulseRadius:   Float = 8.dp.toPx(),
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

    // ── Solo franja objetivo (targetLow..targetHigh) en verde muy suave ──────
    // Mismo tratamiento que el landscape: fondo neutro excepto la zona objetivo.
    drawRect(COLOR_TARGET_BAND, Offset(xBandLeft.coerceAtLeast(0f), 0f),
             Size((xBandRight - xBandLeft).coerceAtLeast(0f), canvasH))

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
    // isLast solo si el tick está realmente en el extremo derecho del canvas visible
    val visibleTicks = ticks.filter { glucoseToX(it) in -0.5f..(canvasW + 0.5f) }
    val lastVisibleTick = visibleTicks.lastOrNull()
    for (tick in ticks) {
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
            drawLine(COLOR_AXIS_TICK, Offset(x, canvasH), Offset(x, canvasH - 4.dp.toPx()), 1.dp.toPx())
            // "+" solo si el tick es el último visible Y el rango de datos lo supera
            val isLast = tick == lastVisibleTick && dataMax > tick
            drawGlucoseLabel(tick, unit, x, canvasH + 4.dp.toPx(), isLast = isLast)
        }
    }

    // ── Etiqueta de unidad: en el margen superior izquierdo (fuera del área de datos) ──
    // Se dibuja con coordenada X negativa para que caiga en el padding de 44dp reservado
    // para las etiquetas de hora, justo encima del primer tick de hora.
    drawUnitLabel(unit, -40.dp.toPx(), (-8).dp.toPx())

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
    // ── Predicción 20 min: cono de incertidumbre + línea punteada ────────────
    // La pendiente usa:
    //   1. TR nativa del sensor si está disponible (Natives.lastglucose().rate, mg/dL/min).
    //   2. En su defecto, pendiente lineal sobre la ventana temporal de los últimos 20 min.
    val lastEntry = visible.maxByOrNull { it.timestampMs }
    val nowMs     = System.currentTimeMillis()
    if (lastEntry != null && nowMs - lastEntry.timestampMs <= 10L * 60_000L) {
        // Obtener TR nativa del sensor (NaN → null)
        val sensorRate: Float? = try {
            Natives.lastglucose()?.rate?.let { r -> if (r.isNaN()) null else r }
        } catch (_: Exception) { null }

        // Calcular slope con misma lógica que StatsPanel + amortiguación en pico prandial
        val rawSlope: Float? = if (sensorRate != null) {
            sensorRate
        } else {
            val windowStart20 = lastEntry.timestampMs - 20L * 60_000L
            val recentForSlope = entries
                .filter { it.sensorIndex == lastEntry.sensorIndex && it.timestampMs >= windowStart20 }
                .sortedBy { it.timestampMs }
            if (recentForSlope.size >= 2) {
                val oldest = recentForSlope.first()
                val newest = recentForSlope.last()
                val dtMin  = (newest.timestampMs - oldest.timestampMs) / 60_000f
                if (dtMin > 0f) (newest.valueMgdL - oldest.valueMgdL) / dtMin else null
            } else null
        }
        // Δ instantáneo entre los dos últimos puntos visibles
        val prevEntry = visible.sortedBy { it.timestampMs }.let { s -> if (s.size >= 2) s[s.size - 2] else null }
        val instantDelta = if (prevEntry != null) lastEntry.valueMgdL - prevEntry.valueMgdL else 0f
        // Amortiguación: glucosa >200 + cambio de signo (pico prandial en reversión)
        val slope: Float? = if (rawSlope != null && lastEntry.valueMgdL > 200f && rawSlope * instantDelta < 0f)
            rawSlope * 0.5f else rawSlope

        if (slope != null) {
            val newest      = lastEntry
            val predMs      = 20L * 60_000L
            val predMgdL    = (newest.valueMgdL + slope * 20f).coerceIn(20f, 400f)
            val predValue   = toDisplayUnit(predMgdL, unit)
            val ptX0        = glucoseToX(toDisplayUnit(newest.valueMgdL, unit))
            val ptY0        = timeToY(newest.timestampMs)
            val ptX1Raw     = glucoseToX(predValue)
            // Y del punto futuro sin clamp para calcular intersección con borde
            val ptY1Raw     = timeToY(newest.timestampMs + predMs)
            val predColor   = glucoseColor(predValue, targetLow, targetHigh, veryLow, veryHigh)

            // Si ptY1Raw supera el canvas, recortamos linealmente al borde inferior
            // y ajustamos X proporcionalmente para mantener la dirección correcta.
            val rangeY = (ptY1Raw - ptY0).coerceAtLeast(1f)
            val rangeX = ptX1Raw - ptX0
            val (ptX1, ptY1) = if (ptY1Raw <= canvasH) {
                Pair(ptX1Raw, ptY1Raw)
            } else {
                // t = fracción del recorrido que cabe en el canvas
                val t = (canvasH - ptY0) / rangeY
                Pair(ptX0 + rangeX * t, canvasH)
            }

            // Solo dibujar si hay recorrido visible (ptY1 > ptY0)
            if (ptY1 > ptY0 + 2f) {
                val visibleRangeY = ptY1 - ptY0

                // Cono de incertidumbre: alpha más visible (0.13)
                val halfCone = 18.dp.toPx()
                val conePath = Path().apply {
                    moveTo(ptX0, ptY0)
                    lineTo((ptX1 - halfCone).coerceAtLeast(0f), ptY1)
                    lineTo((ptX1 + halfCone).coerceAtMost(canvasW), ptY1)
                    close()
                }
                drawPath(conePath, predColor.copy(alpha = 0.13f))

                // Línea punteada central
                val dashPx = 7.dp.toPx(); val gapPx = 5.dp.toPx()
                var cy2 = ptY0
                while (cy2 < ptY1) {
                    val ey = minOf(cy2 + dashPx, ptY1)
                    val t0 = (cy2 - ptY0) / visibleRangeY
                    val t1 = (ey  - ptY0) / visibleRangeY
                    drawLine(predColor.copy(alpha = 0.7f),
                        Offset(ptX0 + rangeX * t0, cy2),
                        Offset(ptX0 + rangeX * t1, ey), 2.5.dp.toPx())
                    cy2 += dashPx + gapPx
                }

                // Punto final (solo si está dentro del canvas)
                if (ptY1Raw <= canvasH) {
                    drawCircle(predColor.copy(alpha = 0.30f), 7.dp.toPx(), Offset(ptX1, ptY1))
                    drawCircle(predColor.copy(alpha = 0.65f), 3.5.dp.toPx(), Offset(ptX1, ptY1))
                }
            }
        }
    }

    for ((_, se) in visible.groupBy { it.sensorIndex }.toSortedMap())
        drawGlucoseLine(se, targetLow, targetHigh, veryLow, veryHigh, ::timeToY, ::glucoseToX, unit, canvasH, pulseRadius)
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
    pulseRadius:   Float = 8.dp.toPx(),
) {
    if (sensorEntries.isEmpty()) return

    // Precomputar coordenadas para reutilizar en relleno, línea y punto
    data class Pt(val x: Float, val y: Float, val color: Color)
    val pts = sensorEntries.map { e ->
        val v = toDisplayUnit(e.valueMgdL, unit)
        Pt(glucoseToX(v), timeToY(e.timestampMs), glucoseColor(v, targetLow, targetHigh, veryLow, veryHigh))
    }

    // ── 1. Área de relleno con gradiente horizontal (eje transpuesto) ─────────
    val lastVal   = toDisplayUnit(sensorEntries.last().valueMgdL, unit)
    val fillColor = glucoseColor(lastVal, targetLow, targetHigh, veryLow, veryHigh)
    val fillPath = Path().apply {
        moveTo(0f, pts.first().y)
        lineTo(pts.first().x, pts.first().y)
        for (i in 1 until pts.size) {
            val p0 = pts[i - 1]; val p1 = pts[i]
            // Bézier ligero: punto de control a 1/3 del segmento en X, sin mover Y
            // → suaviza ángulos sin aplanar picos clínicos
            val cpX = p0.x + (p1.x - p0.x) * 0.33f
            cubicTo(cpX, p0.y, cpX, p1.y, p1.x, p1.y)
        }
        lineTo(0f, pts.last().y)
        close()
    }
    val fillBrush = Brush.horizontalGradient(
        colors = listOf(Color.Transparent, fillColor.copy(alpha = 0.15f)),
        startX = 0f,
        endX   = pts.last().x.coerceAtLeast(1f),
    )
    drawPath(fillPath, fillBrush)

    // ── 2. Glow sutil + línea principal Bézier coloreada por zona ────────────
    val strokePx = 3.5.dp.toPx()
    val glowPx   = strokePx + 3.dp.toPx()  // glow = línea más ancha y semitransparente
    for (i in 0 until pts.size - 1) {
        val p0 = pts[i]; val p1 = pts[i + 1]
        val cpX = p0.x + (p1.x - p0.x) * 0.33f
        val segColor = glucoseColor(
            (toDisplayUnit(sensorEntries[i].valueMgdL, unit) + toDisplayUnit(sensorEntries[i + 1].valueMgdL, unit)) / 2f,
            targetLow, targetHigh, veryLow, veryHigh,
        )
        val path = Path().apply {
            moveTo(p0.x, p0.y)
            cubicTo(cpX, p0.y, cpX, p1.y, p1.x, p1.y)
        }
        // Glow: misma curva más gruesa y semitransparente debajo
        drawPath(path, segColor.copy(alpha = 0.18f), style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = glowPx, cap = StrokeCap.Round,
        ))
        // Línea principal encima
        drawPath(path, segColor, style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = strokePx, cap = StrokeCap.Round,
        ))
    }

    // ── 3. Punto final: círculo blanco con borde grueso del color de zona ────
    val last      = sensorEntries.last()
    val lastDisp  = toDisplayUnit(last.valueMgdL, unit)
    val lastColor = glucoseColor(lastDisp, targetLow, targetHigh, veryLow, veryHigh)
    val cx = glucoseToX(lastDisp)
    val cy = timeToY(last.timestampMs)
    // Halo pulsante animado
    drawCircle(lastColor.copy(alpha = 0.12f), pulseRadius, Offset(cx, cy))
    // Borde exterior del color de zona (stroke grueso)
    drawCircle(lastColor, 7.dp.toPx(), Offset(cx, cy),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx()))
    // Relleno blanco interior
    drawCircle(Color.White, 4.5.dp.toPx(), Offset(cx, cy))

    // ── 4. Etiqueta de fecha/hora junto al punto actual ───────────────────────
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
    val label = "$dateStr $timeStr"

    val fontSize  = 26f
    val textPaint = android.graphics.Paint().apply {
        color          = COLOR_AXIS_TEXT.toArgb()
        textSize       = fontSize
        isAntiAlias    = true
        isFakeBoldText = true
        textAlign      = android.graphics.Paint.Align.LEFT
    }
    val textW    = textPaint.measureText(label)
    val textH    = fontSize
    val padH     = 5.dp.toPx()
    val padV     = 3.dp.toPx()
    val radius   = 4.dp.toPx()
    val gapAbove = 10.dp.toPx()
    val gapSide  = 8.dp.toPx()
    val boxH     = textH + padV * 2

    // En portrait el punto actual suele estar en la zona superior del canvas (tiempo reciente = Y bajo).
    // Si el punto está en el 35% superior, colocamos la etiqueta DEBAJO para no salir del canvas.
    // En caso contrario la colocamos encima como siempre, con clamp al borde superior (margen 4dp).
    val topMargin = 4.dp.toPx()
    val rawY = when {
        cy - gapAbove - boxH < topMargin -> cy + gapAbove          // debajo del punto
        else                             -> cy - gapAbove - boxH   // encima del punto
    }.coerceAtLeast(topMargin)
    val labelY = rawY + padV + textH

    val rawX = if (cx + gapSide + textW + padH * 2 <= size.width) cx + gapSide
               else (cx - gapSide - textW - padH * 2).coerceAtLeast(0f)
    val labelX = rawX + padH

    val rect = android.graphics.RectF(rawX, rawY, rawX + textW + padH * 2, rawY + textH + padV * 2)

    val bgPaint = android.graphics.Paint().apply {
        color       = android.graphics.Color.argb(190, 245, 244, 238)
        isAntiAlias = true
        style       = android.graphics.Paint.Style.FILL
    }
    drawContext.canvas.nativeCanvas.drawRoundRect(rect, radius, radius, bgPaint)

    val borderPaint = android.graphics.Paint().apply {
        color       = android.graphics.Color.argb(60, 100, 100, 90)
        isAntiAlias = true
        style       = android.graphics.Paint.Style.STROKE
        strokeWidth = 1.dp.toPx()
    }
    drawContext.canvas.nativeCanvas.drawRoundRect(rect, radius, radius, borderPaint)
    drawContext.canvas.nativeCanvas.drawText(label, labelX, labelY, textPaint)
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