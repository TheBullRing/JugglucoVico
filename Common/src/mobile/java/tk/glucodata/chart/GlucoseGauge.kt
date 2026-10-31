/*
 * GlucoseGauge — indicador de arco compacto para el modo portrait.
 *
 * Diseño limpio (sin saturación de marcas):
 *   • Arco multicolor (rojo/naranja/verde/naranja/rojo) con 4 líneas divisorias.
 *   • SIN etiquetas de escala exterior (0, 50, 100…300) → eliminan ruido visual.
 *   • SIN etiquetas numéricas en los divisores interiores → el color comunica el rango.
 *   • Dentro del hueco se muestran tres líneas:
 *       – Valor de glucosa actual  (texto grande, color de zona)
 *       – Unidad (mg/dL / mmol/L)  (texto pequeño, gris)
 *       – Delta + flecha de tendencia  ("+7 →")  (texto medio, color de delta)
 *   • Aguja: triángulo apunta al borde interior del arco; base queda fuera del texto.
 *
 * Geometría del arco (sistema Android: 0=derecha, sentido horario):
 *   ARC_START = 150°  → extremo inferior izquierdo (valor 0)
 *   ARC_END   = 390°  → extremo inferior derecho   (valor 300)
 *   Tope del arco: 270° → cy – R
 */
package tk.glucodata.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

private const val ARC_START_ANGLE        = 150f
private const val ARC_SWEEP              = 240f
private const val GAUGE_MIN_MGDL         = 0f
private const val GAUGE_MAX_MGDL         = 300f

// Grosor del arco: 0.13 → arco visible pero compacto (sin saturar de marcas).
private const val ARC_THICKNESS_FRACTION = 0.13f

// Anchura angular mínima por banda coloreada (evita que rojo1 y naranja1 desaparezcan).
private const val MIN_BAND_DEG = 8f

private const val DEFAULT_VERY_LOW    = 55f
private const val DEFAULT_TARGET_LOW  = 70f
private const val DEFAULT_TARGET_HIGH = 180f
private const val DEFAULT_VERY_HIGH   = 250f

private val GAUGE_COLOR_CRITICAL = Color(0xFFE74C3C)
private val GAUGE_COLOR_WARNING  = Color(0xFFF39C12)
private val GAUGE_COLOR_OK       = Color(0xFF2ECC71)
private val GAUGE_COLOR_TRACK    = Color(0x558E8E93)
private val GAUGE_COLOR_NEEDLE   = Color(0xFF1A1A2E)

private fun toDisplayUnit(mgdl: Float, unit: GlucoseUnit): Float =
    if (unit == GlucoseUnit.MMOL) mgdl / 18.0f else mgdl

/**
 * Indicador de arco semicircular compacto.
 *
 * @param currentMgdL  Glucosa actual en mg/dL. null → muestra "--".
 * @param settings     Configuración del gráfico (unidad, umbrales, etc.).
 * @param trendArrow   Flecha de tendencia: "→", "↗", "↗↗", "↙", "↙↙".
 * @param deltaDisplay Delta formateado, p. ej. "+7" o "+0.4". null si no hay dato previo.
 * @param deltaColor   Color para el texto de delta.
 * @param glucoseColor Color del valor de glucosa según zona clínica.
 * @param ageStr       Antigüedad del dato, p. ej. "ahora", "hace 5m". null si no hay dato.
 * @param ageColor     Color del texto de antigüedad.
 */
@Composable
fun GlucoseGauge(
    currentMgdL:  Float?,
    settings:     ChartSettings,
    trendArrow:   String,
    deltaDisplay: String?,
    deltaColor:   Color,
    glucoseColor: Color,
    ageStr:       String?,
    ageColor:     Color,
    modifier:     Modifier = Modifier,
) {
    val targetAngle = if (currentMgdL == null) ARC_START_ANGLE else {
        val range    = GAUGE_MAX_MGDL - GAUGE_MIN_MGDL
        val fraction = (currentMgdL.coerceIn(GAUGE_MIN_MGDL, GAUGE_MAX_MGDL) - GAUGE_MIN_MGDL) / range
        ARC_START_ANGLE + fraction * ARC_SWEEP
    }
    val animatedAngle = remember { Animatable(ARC_START_ANGLE) }
    LaunchedEffect(targetAngle) {
        animatedAngle.animateTo(targetAngle, animationSpec = tween(durationMillis = 800))
    }

    val displayValue = currentMgdL?.let {
        val v = toDisplayUnit(it, settings.unit)
        if (settings.unit == GlucoseUnit.MMOL) "%.1f".format(v) else v.toInt().toString()
    } ?: "--"
    val unitSuffix = if (settings.unit == GlucoseUnit.MMOL) "mmol/L" else "mg/dL"

    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor     = MaterialTheme.colorScheme.surface

    // aspectRatio 2.2: más ancho que alto → cabecera horizontal compacta.
    Box(
        modifier         = modifier.fillMaxWidth().aspectRatio(2.2f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // ── Geometría del arco ──────────────────────────────────────────────
            val padTop = h * 0.06f
            val padBot = h * 0.10f

            val lateralMargin   = w * 0.05f
            val radiusFromWidth = (w / 2f - lateralMargin) / 0.866f
            val availH          = h - padTop - padBot
            // Altura usada: desde cy-radius (tope) hasta cy+radius*0.5 (extremos) ≈ radius*1.5
            val radiusFromHeight = availH / (1.5f + ARC_THICKNESS_FRACTION * 0.5f)
            val radius = minOf(radiusFromWidth, radiusFromHeight)
            val stroke = radius * ARC_THICKNESS_FRACTION
            val cx = w / 2f
            val cy = padTop + radius

            // ── Umbrales con fallback clínico ───────────────────────────────────
            val vl = (if (settings.veryLow    > 0f) settings.veryLow    else DEFAULT_VERY_LOW).coerceIn(GAUGE_MIN_MGDL, GAUGE_MAX_MGDL)
            val tl = (if (settings.targetLow  > 0f) settings.targetLow  else DEFAULT_TARGET_LOW).coerceIn(GAUGE_MIN_MGDL, GAUGE_MAX_MGDL)
            val th = (if (settings.targetHigh > 0f) settings.targetHigh else DEFAULT_TARGET_HIGH).coerceIn(GAUGE_MIN_MGDL, GAUGE_MAX_MGDL)
            val vh = (if (settings.veryHigh   > 0f) settings.veryHigh   else DEFAULT_VERY_HIGH).coerceIn(GAUGE_MIN_MGDL, GAUGE_MAX_MGDL)

            fun mgdlToAngle(mgdl: Float): Float {
                val fraction = (mgdl.coerceIn(GAUGE_MIN_MGDL, GAUGE_MAX_MGDL) - GAUGE_MIN_MGDL) /
                        (GAUGE_MAX_MGDL - GAUGE_MIN_MGDL)
                return ARC_START_ANGLE + fraction * ARC_SWEEP
            }
            fun deg2rad(deg: Float) = Math.toRadians(deg.toDouble())

            val aMin = mgdlToAngle(GAUGE_MIN_MGDL)
            val aVl  = mgdlToAngle(vl)
            val aTl  = mgdlToAngle(tl)
            val aTh  = mgdlToAngle(th)
            val aVh  = mgdlToAngle(vh)
            val aMax = mgdlToAngle(GAUGE_MAX_MGDL)

            // ── Anchura mínima de banda ─────────────────────────────────────────
            val rawSweeps = floatArrayOf(
                aVl - aMin, aTl - aVl, aTh - aTl, aVh - aTh, aMax - aVh,
            )
            var greenBorrow = 0f
            for (i in rawSweeps.indices) {
                if (i != 2 && rawSweeps[i] < MIN_BAND_DEG) {
                    greenBorrow += MIN_BAND_DEG - rawSweeps[i]
                    rawSweeps[i] = MIN_BAND_DEG
                }
            }
            rawSweeps[2] = maxOf(rawSweeps[2] - greenBorrow, MIN_BAND_DEG)

            val startAngles = floatArrayOf(
                aMin,
                aMin + rawSweeps[0],
                aMin + rawSweeps[0] + rawSweeps[1],
                aMin + rawSweeps[0] + rawSweeps[1] + rawSweeps[2],
                aMin + rawSweeps[0] + rawSweeps[1] + rawSweeps[2] + rawSweeps[3],
            )
            val divAngles = floatArrayOf(aVl, aTl, aTh, aVh)

            // ── 1. Arco de fondo ────────────────────────────────────────────────
            drawGaugeArc(cx, cy, radius, stroke, ARC_START_ANGLE, ARC_SWEEP, GAUGE_COLOR_TRACK)

            // ── 2. Cinco bandas coloreadas ──────────────────────────────────────
            val bandColors = listOf(
                GAUGE_COLOR_CRITICAL, GAUGE_COLOR_WARNING,
                GAUGE_COLOR_OK,
                GAUGE_COLOR_WARNING, GAUGE_COLOR_CRITICAL,
            )
            for (i in 0..4) {
                drawGaugeArc(cx, cy, radius, stroke, startAngles[i], rawSweeps[i], bandColors[i])
            }

            // ── 3. Divisores (4 líneas entre bandas) ────────────────────────────
            divAngles.forEach { deg ->
                val rad    = deg2rad(deg)
                val innerR = radius - stroke * 0.6f
                val outerR = radius + stroke * 0.6f
                drawLine(
                    color       = surfaceColor.copy(alpha = 0.90f),
                    start       = Offset((cx + innerR * cos(rad)).toFloat(), (cy + innerR * sin(rad)).toFloat()),
                    end         = Offset((cx + outerR * cos(rad)).toFloat(), (cy + outerR * sin(rad)).toFloat()),
                    strokeWidth = 2.dp.toPx(),
                )
            }

            // ── 3b. Etiquetas de los 2 umbrales de decisión (targetLow / targetHigh) ─
            // Solo se muestran tl y th (p. ej. 70 y 180) en el borde exterior del arco.
            // Son los únicos valores que el usuario necesita para contextualizar la aguja:
            //   - dónde empieza la hipoglucemia  (tl)
            //   - dónde empieza la hiperglucemia (th)
            // Fuente pequeña y gris para que no compitan con el valor central.
            // Radio de posición: justo fuera del arco con un pequeño margen de aire.
            val threshLabelSize = (w * 0.028f).coerceIn(9.dp.toPx(), 11.dp.toPx())
            // +1.1 en lugar de 0.7: da ~2-3 px más de margen entre el arco y el texto,
            // especialmente para el 180 que cae cerca del punto más alto (≈270°).
            val threshLabelOutR = radius + stroke * 0.5f + threshLabelSize * 1.1f
            val threshLabelPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize    = threshLabelSize
                typeface    = android.graphics.Typeface.DEFAULT
                color       = onSurfaceVariant.copy(alpha = 0.60f).toArgb()
            }

            // Dibuja la etiqueta del umbral en el ángulo correcto con alineación dinámica.
            //
            // pushDown: desplazamiento vertical adicional proporcional a (-sin).
            // sin(270°) = -1  → pushDown máximo (el 180 está en el tope del arco, empuja
            //                   el texto hacia abajo para alejarlo de la barra de estado).
            // sin(206°) ≈ -0.44 → pushDown parcial para el 70 (sin problema real).
            // sin(lateral) ≈ 0  → pushDown ≈ 0 (ángulos próximos al ecuador del arco).
            // El factor threshLabelSize*0.9 calibra el desplazamiento en ~1 altura de texto.
            fun drawThreshLabel(mgdl: Float, angleDeg: Float) {
                val rad    = deg2rad(angleDeg)
                val sinVal = sin(rad).toFloat()
                val cosVal = cos(rad).toFloat()
                threshLabelPaint.textAlign = when {
                    cosVal < -0.25f -> android.graphics.Paint.Align.RIGHT
                    cosVal >  0.25f -> android.graphics.Paint.Align.LEFT
                    else            -> android.graphics.Paint.Align.CENTER
                }
                val lx = (cx + threshLabelOutR * cos(rad)).toFloat()
                val ly = (cy + threshLabelOutR * sinVal).toFloat()
                // pushDown: máximo cuando sin<0 (zona superior del arco), nulo en zona inferior.
                val pushDown = (-sinVal).coerceAtLeast(0f) * threshLabelSize * 0.9f
                val disp = toDisplayUnit(mgdl, settings.unit)
                val lbl  = if (settings.unit == GlucoseUnit.MMOL) "%.1f".format(disp) else disp.toInt().toString()
                val textCy = ly - (threshLabelPaint.ascent() + threshLabelPaint.descent()) / 2f + pushDown
                drawContext.canvas.nativeCanvas.drawText(lbl, lx, textCy, threshLabelPaint)
            }

            drawThreshLabel(tl, aTl)   // 70  (o equivalente mmol)  — lado izquierdo del arco
            drawThreshLabel(th, aTh)   // 180 (o equivalente mmol)  — lado derecho del arco

            // ── 4. Aguja ────────────────────────────────────────────────────────
            // Punta en el borde interior; base a 2×stroke hacia el centro → no llega al texto.
            val needleAngleRad = deg2rad(animatedAngle.value)
            val tipR  = radius - stroke * 0.5f
            val baseR = radius - stroke * 2.0f
            val halfW = stroke * 0.45f

            val dX = cos(needleAngleRad).toFloat()
            val dY = sin(needleAngleRad).toFloat()
            val pX = (-sin(needleAngleRad)).toFloat()
            val pY = (cos(needleAngleRad)).toFloat()

            val needlePath = Path().apply {
                moveTo(cx + tipR  * dX,               cy + tipR  * dY)
                lineTo(cx + baseR * dX + halfW * pX,  cy + baseR * dY + halfW * pY)
                lineTo(cx + baseR * dX - halfW * pX,  cy + baseR * dY - halfW * pY)
                close()
            }
            drawPath(needlePath, GAUGE_COLOR_NEEDLE)
            drawPath(needlePath, surfaceColor.copy(alpha = 0.20f), style = Stroke(width = 0.8.dp.toPx()))

            // ── 5. Textos centrales ─────────────────────────────────────────────
            // Jerarquía vertical dentro del hueco (arriba → abajo):
            //   Línea 1: valor grande  (241)
            //   Línea 2: unidad pequeña  (mg/dL)
            //   Línea 3: delta  +  flecha  dibujados como dos pasadas separadas
            //            para evitar solapamiento de glifos unicode.
            //   Línea 4: antigüedad del dato  (ahora · hace 5m)  en gris tenue
            //
            // maxTextWidth = 80% del diámetro interior → ningún texto toca el arco.
            val innerR       = radius - stroke / 2f
            val maxTextWidth = innerR * 1.6f

            val valueFontSize = (innerR * 0.76f).coerceAtMost(maxTextWidth / 1.8f)
            // subFontSize ya no se usa como línea independiente (unidad va inline en línea 3).
            val deltaFontSize = (valueFontSize * 0.40f).coerceAtMost(maxTextWidth / 4.8f)
            val ageFontSize   = (valueFontSize * 0.28f).coerceAtMost(maxTextWidth / 6.5f)

            val lineGap  = valueFontSize * 0.10f
            // totalH: valor + gap + línea3(delta+unidad+flecha) + [gap+age]
            val totalH   = valueFontSize + lineGap * 2f +
                           deltaFontSize + (if (ageStr != null) lineGap * 0.8f + ageFontSize else 0f)
            val blockTop = cy - totalH / 2f

            // ---- Línea 1: Valor de glucosa ----
            val valuePaint = android.graphics.Paint().apply {
                isAntiAlias = true
                textAlign   = android.graphics.Paint.Align.CENTER
                typeface    = android.graphics.Typeface.DEFAULT_BOLD
                textSize    = valueFontSize
                color       = glucoseColor.toArgb()
            }
            val valueY = blockTop + valueFontSize
            drawContext.canvas.nativeCanvas.drawText(displayValue, cx, valueY, valuePaint)

            // ---- Línea 2 eliminada: la unidad ahora va inline en la línea 3 ----
            // "+0 mg/dL →" se construye como un solo bloque de tres segmentos medidos.
            // Recalculamos unitY como punto de referencia para calcular deltaBaseY.
            val unitY = valueY + lineGap   // punto de referencia sin texto visible

            // ---- Línea 3: "+0 mg/dL →"  (delta · unidad · flecha en una sola fila) ----
            // Tres segmentos medidos individualmente y concatenados centrados en cx:
            //   [delta bold]  [" unitSuffix " regular gris]  [flecha regular coloreada]
            // Usar measureText por segmento garantiza que ningún glifo unicode se solape.
            val deltaBaseY = unitY + lineGap + deltaFontSize

            val deltaPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                textAlign   = android.graphics.Paint.Align.LEFT
                typeface    = android.graphics.Typeface.DEFAULT_BOLD
                textSize    = deltaFontSize
                color       = deltaColor.toArgb()
            }
            val unitInlinePaint = android.graphics.Paint().apply {
                isAntiAlias = true
                textAlign   = android.graphics.Paint.Align.LEFT
                typeface    = android.graphics.Typeface.DEFAULT
                textSize    = deltaFontSize * 0.82f   // ligeramente más pequeño que el delta
                color       = onSurfaceVariant.copy(alpha = 0.60f).toArgb()
            }
            val arrowPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                textAlign   = android.graphics.Paint.Align.LEFT
                typeface    = android.graphics.Typeface.DEFAULT
                textSize    = deltaFontSize
                color       = deltaColor.copy(alpha = 0.85f).toArgb()
            }

            if (deltaDisplay != null) {
                val gap      = deltaFontSize * 0.20f
                val unitText = " $unitSuffix "
                val dW = deltaPaint.measureText(deltaDisplay)
                val uW = unitInlinePaint.measureText(unitText)
                val aW = arrowPaint.measureText(trendArrow)
                val totalW = dW + uW + gap + aW
                val x0 = cx - totalW / 2f
                // baseline vertical: alinear las tres piezas a la misma línea de base
                val unitBaselineShift = (deltaFontSize - deltaFontSize * 0.82f) * 0.5f
                drawContext.canvas.nativeCanvas.drawText(deltaDisplay, x0,           deltaBaseY,                      deltaPaint)
                drawContext.canvas.nativeCanvas.drawText(unitText,     x0 + dW,      deltaBaseY - unitBaselineShift,  unitInlinePaint)
                drawContext.canvas.nativeCanvas.drawText(trendArrow,   x0 + dW + uW + gap, deltaBaseY,               arrowPaint)
            } else {
                // Sin delta: "mg/dL  →" centrado
                val unitText = "$unitSuffix  "
                val uW = unitInlinePaint.measureText(unitText)
                val aW = arrowPaint.measureText(trendArrow)
                val x0 = cx - (uW + aW) / 2f
                drawContext.canvas.nativeCanvas.drawText(unitText,   x0,      deltaBaseY, unitInlinePaint)
                drawContext.canvas.nativeCanvas.drawText(trendArrow, x0 + uW, deltaBaseY, arrowPaint)
            }

            // ---- Línea 4: Antigüedad del dato ----
            if (ageStr != null) {
                val agePaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textAlign   = android.graphics.Paint.Align.CENTER
                    typeface    = android.graphics.Typeface.DEFAULT
                    textSize    = ageFontSize
                    color       = ageColor.copy(alpha = 0.65f).toArgb()
                }
                val ageY = deltaBaseY + lineGap * 0.8f + ageFontSize
                drawContext.canvas.nativeCanvas.drawText(ageStr, cx, ageY, agePaint)
            }
        }
    }
}

private fun DrawScope.drawGaugeArc(
    cx: Float, cy: Float, radius: Float, stroke: Float,
    startAngle: Float, sweepAngle: Float, color: Color,
) {
    if (sweepAngle <= 0f) return
    drawArc(
        color      = color,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter  = false,
        topLeft    = Offset(cx - radius, cy - radius),
        size       = Size(radius * 2f, radius * 2f),
        style      = Stroke(width = stroke, cap = StrokeCap.Butt),
    )
}
