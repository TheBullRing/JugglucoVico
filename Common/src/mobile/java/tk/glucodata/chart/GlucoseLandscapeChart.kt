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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
// Colores rediseñados: fondo neutro, franja objetivo sutil, grid muy tenue
// ─────────────────────────────────────────────────────────────────────────────

private val LC_COLOR_IN_RANGE    = Color(0xFF2ECC71)   // verde accesible: en rango
private val LC_COLOR_WARNING     = Color(0xFFF39C12)   // naranja accesible: zona intermedia
private val LC_COLOR_CRITICAL    = Color(0xFFE74C3C)   // rojo accesible: fuera de rango extremo
// Fondo: solo franja objetivo muy suave ~5% alpha
private val LC_COLOR_TARGET_BAND = Color(0x0D2ECC71)   // verde ~5% alpha
// Grid 10–15% alpha neutro
private val LC_COLOR_GRID        = Color(0x1A8E8E93)   // grid 10% alpha
private val LC_COLOR_GRID_MAJOR  = Color(0x268E8E93)   // grid horas exactas 15% alpha
private val LC_COLOR_AXIS_TEXT   = Color(0xFF8E8E93)   // gris neutro iOS/Material

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

        // start = 40dp: las etiquetas de glucosa (RIGHT-alineadas en x=-2dp) son ~36dp de ancho;
        // 40dp deja 2dp de respiración sin espacio muerto. La etiqueta "mg/dL" va en x=-38dp.

        // Pulso animado del punto actual
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseRadius by infiniteTransition.animateFloat(
            initialValue  = 5f,
            targetValue   = 13f,
            animationSpec = infiniteRepeatable(
                animation  = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseRadius",
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 40.dp, end = 8.dp, top = 8.dp, bottom = 40.dp)
        ) {
            drawLandscapeChart(
                entries       = entries,
                settings      = settings,
                windowStartMs = windowStartMs,
                windowEndMs   = curEnd,
                pulseRadius   = pulseRadius.dp.toPx(),
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
    pulseRadius:   Float = 8.dp.toPx(),
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

    // ── Fondo: solo franja objetivo suave (sin bandas de color saturadas) ─────
    val yBandTop    = glucoseToY(targetHigh)
    val yBandBottom = glucoseToY(targetLow)
    drawRect(LC_COLOR_TARGET_BAND, Offset(0f, yBandTop), Size(canvasW, (yBandBottom - yBandTop).coerceAtLeast(0f)))

    // ── Cuadrícula X (líneas verticales de tiempo) — muy tenues ──────────────
    val gridIntervalMs = lcBestGridInterval(windowEndMs - windowStartMs)
    val firstGridMs    = (windowStartMs / gridIntervalMs + 1L) * gridIntervalMs
    var gridMs = firstGridMs
    while (gridMs <= windowEndMs) {
        val x = timeToX(gridMs)
        drawLine(
            color       = LC_COLOR_GRID_MAJOR,
            start       = Offset(x, 0f),
            end         = Offset(x, canvasH),
            strokeWidth = 0.8.dp.toPx(),
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
    // para aprovechar el margen start=40dp que tiene el Canvas.
    // Etiqueta mg/dL eliminada: unidad ya queda clara en el panel inferior junto al número grande.
    // drawLcUnitLabel(unit, -38.dp.toPx(), 2.dp.toPx())

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

    // ── Predicción 20 min: cono de incertidumbre + línea punteada ────────────
    // Solo visible cuando estamos en el presente (último dato < 10 min)
    val lastEntry = visible.maxByOrNull { it.timestampMs }
    val nowMs     = System.currentTimeMillis()
    if (lastEntry != null && nowMs - lastEntry.timestampMs <= 10L * 60_000L) {
        val recentForSlope = entries
            .filter { it.sensorIndex == lastEntry.sensorIndex }
            .sortedBy { it.timestampMs }
            .takeLast(6)
        if (recentForSlope.size >= 2) {
            val oldest    = recentForSlope.first()
            val newest    = recentForSlope.last()
            val dtMin     = (newest.timestampMs - oldest.timestampMs) / 60_000f
            val slope     = if (dtMin > 0f) (newest.valueMgdL - oldest.valueMgdL) / dtMin else 0f
            val predMs    = 20L * 60_000L
            val predValue = lcDisplayUnit((newest.valueMgdL + slope * 20f).coerceIn(20f, 400f), unit)
            val ptX0      = timeToX(newest.timestampMs)
            val ptY0      = glucoseToY(lcDisplayUnit(newest.valueMgdL, unit))
            val ptX1      = timeToX(newest.timestampMs + predMs).coerceAtMost(canvasW)
            val ptY1      = glucoseToY(predValue)
            val predColor = lcGlucoseColor(predValue, targetLow, targetHigh, veryLow, veryHigh)

            // Cono de incertidumbre: se ensancha de 0 a ±18dp en 20 min
            val halfCone = 18.dp.toPx()
            val conePath = Path().apply {
                moveTo(ptX0, ptY0)
                lineTo(ptX1, ptY1 - halfCone)
                lineTo(ptX1, ptY1 + halfCone)
                close()
            }
            drawPath(conePath, predColor.copy(alpha = 0.07f))

            // Línea central punteada
            val dashPx = 7.dp.toPx(); val gapPx = 5.dp.toPx()
            var cx = ptX0
            while (cx < ptX1) {
                val ex = minOf(cx + dashPx, ptX1)
                val t0 = (cx - ptX0) / (ptX1 - ptX0).coerceAtLeast(1f)
                val t1 = (ex - ptX0) / (ptX1 - ptX0).coerceAtLeast(1f)
                drawLine(predColor.copy(alpha = 0.6f),
                    Offset(cx, ptY0 + (ptY1 - ptY0) * t0),
                    Offset(ex, ptY0 + (ptY1 - ptY0) * t1), 2.dp.toPx())
                cx += dashPx + gapPx
            }

            // Punto final de predicción
            drawCircle(predColor.copy(alpha = 0.25f), 6.dp.toPx(), Offset(ptX1, ptY1))
            drawCircle(predColor.copy(alpha = 0.55f), 3.dp.toPx(), Offset(ptX1, ptY1))
        }
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
            pulseRadius   = pulseRadius,
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
    pulseRadius:   Float = 8.dp.toPx(),
) {
    if (sensorEntries.isEmpty()) return

    // Precomputar coordenadas para relleno, línea y punto
    data class Pt(val x: Float, val y: Float)
    val pts = sensorEntries.map { e ->
        val v = lcDisplayUnit(e.valueMgdL, unit)
        Pt(timeToX(e.timestampMs), glucoseToY(v))
    }

    // ── Glow sutil + Bézier coloreado por zona ────────────────────────────────
    // glowPx = +1.5dp (no +3dp) para evitar que segmentos ruidosos se "empasten"
    val strokePx = 3.5.dp.toPx()
    val glowPx   = strokePx + 1.5.dp.toPx()
    for (i in 0 until pts.size - 1) {
        val p0 = pts[i]; val p1 = pts[i + 1]
        val cpX = p0.x + (p1.x - p0.x) * 0.33f
        val aVal = lcDisplayUnit(sensorEntries[i].valueMgdL, unit)
        val bVal = lcDisplayUnit(sensorEntries[i + 1].valueMgdL, unit)
        val segColor = lcGlucoseColor((aVal + bVal) / 2f, targetLow, targetHigh, veryLow, veryHigh)
        val path = Path().apply {
            moveTo(p0.x, p0.y)
            cubicTo(cpX, p0.y, cpX, p1.y, p1.x, p1.y)
        }
        // Glow: +1.5dp, alpha 12% — suficiente para despegar del fondo sin empastar picos
        drawPath(path, segColor.copy(alpha = 0.12f), style = Stroke(width = glowPx, cap = StrokeCap.Round))
        // Línea principal encima
        drawPath(path, segColor, style = Stroke(width = strokePx, cap = StrokeCap.Round))
    }

    // ── Punto final: círculo blanco con borde del color de zona ──────────────
    val last  = sensorEntries.last()
    val v     = lcDisplayUnit(last.valueMgdL, unit)
    val ptX   = timeToX(last.timestampMs)
    val ptY   = glucoseToY(v)
    val ptColor = lcGlucoseColor(v, targetLow, targetHigh, veryLow, veryHigh)
    drawCircle(ptColor.copy(alpha = 0.12f), pulseRadius, Offset(ptX, ptY))  // halo animado
    drawCircle(ptColor, 7.dp.toPx(), Offset(ptX, ptY),
        style = Stroke(width = 2.5.dp.toPx()))  // borde color zona
    drawCircle(Color.White, 4.5.dp.toPx(), Offset(ptX, ptY))  // relleno blanco

    // Etiqueta de fecha/hora
    drawLcCurrentReadingLabel(last.timestampMs, ptX, ptY, size.width, size.height)
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

    val fontSize = 26f
    val textPaint = android.graphics.Paint().apply {
        color          = LC_COLOR_AXIS_TEXT.copy(alpha = 0.95f).toArgb()
        textSize       = fontSize
        isAntiAlias    = true
        isFakeBoldText = true
        textAlign      = android.graphics.Paint.Align.LEFT
    }
    val textW  = textPaint.measureText(label)
    val textH  = fontSize
    val padH   = 5.dp.toPx()
    val padV   = 3.dp.toPx()
    val radius = 4.dp.toPx()
    // offset vertical extra: el tooltip flota 10dp por encima del punto
    val gapAbove = 10.dp.toPx()
    val gapSide  = 8.dp.toPx()

    // Vertical: encima si hay espacio; si no, debajo. Clamp al borde superior con 4dp de margen.
    val topMargin = 4.dp.toPx()
    val boxH = textH + padV * 2
    val rawY = when {
        ptY - gapAbove - boxH < topMargin -> ptY + gapAbove
        else                              -> ptY - gapAbove - boxH
    }.coerceAtLeast(topMargin)
    val labelY = rawY + padV + textH

    // Horizontal: a la izquierda del punto si está cerca del borde derecho, si no a la derecha
    val rawX = if (ptX + gapSide + textW + padH * 2 <= canvasW)
                   ptX + gapSide
               else (ptX - gapSide - textW - padH * 2).coerceAtLeast(0f)
    val labelX = rawX + padH

    val rect = android.graphics.RectF(rawX, rawY, rawX + textW + padH * 2, rawY + textH + padV * 2)

    // Fondo: relleno crema
    val bgPaint = android.graphics.Paint().apply {
        color       = android.graphics.Color.argb(190, 245, 244, 238)
        isAntiAlias = true
        style       = android.graphics.Paint.Style.FILL
    }
    drawContext.canvas.nativeCanvas.drawRoundRect(rect, radius, radius, bgPaint)

    // Borde sutil 1dp
    val borderPaint = android.graphics.Paint().apply {
        color       = android.graphics.Color.argb(60, 100, 100, 90)
        isAntiAlias = true
        style       = android.graphics.Paint.Style.STROKE
        strokeWidth = 1.dp.toPx()
    }
    drawContext.canvas.nativeCanvas.drawRoundRect(rect, radius, radius, borderPaint)
    drawContext.canvas.nativeCanvas.drawText(label, labelX, labelY, textPaint)
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
    // alpha 0xDD (~87%) en lugar de 0xFF para que no compita con la línea pero sí sea legible
    val paint = android.graphics.Paint().apply {
        color       = LC_COLOR_AXIS_TEXT.copy(alpha = 0.87f).toArgb()
        textSize    = 28f   // 26→28: un poco más grande para mejor legibilidad
        textAlign   = android.graphics.Paint.Align.RIGHT
        isAntiAlias = true
        isFakeBoldText = false
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
