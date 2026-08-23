/*
 * GlucoseGauge -- velocimetro de glucosa en tiempo real.
 *
 * Geometria del arco (sistema Android: 0=derecha, sentido horario):
 *   ARC_START = 150 deg  => extremo inferior izquierdo
 *   ARC_END   =  30 deg  => extremo inferior derecho
 *   Punto mas alto: 270 deg (tope del arco) => cy - R
 *   Puntos extremos inferiores: cy + R*sin(150) = cy + 0.5*R
 *
 * Centro geometrico del arco (cx, cy).
 * Centro visual del hueco interior: aprox cy - R*0.50
 * (mitad entre cy y el tope superior cy-R).
 *
 * Para centrar el texto hay que compensar la baseline de Android Paint:
 *   drawText pinta con la BASELINE en el Y dado, no el centro de la glifo.
 *   Centro real del glifo = baseline - (ascent + descent)/2
 *   => compensacion: deltaY = (paint.ascent() + paint.descent()) / 2
 *   => drawText en  textCenterY - deltaY  =>  glifo centrado en textCenterY
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
private const val GAUGE_MAX_MGDL         = 300f
private const val ARC_THICKNESS_FRACTION = 0.13f

private val GAUGE_COLOR_CRITICAL = Color(0xFFE74C3C)
private val GAUGE_COLOR_WARNING  = Color(0xFFF39C12)
private val GAUGE_COLOR_OK       = Color(0xFF2ECC71)
private val GAUGE_COLOR_TRACK    = Color(0x558E8E93)
private val GAUGE_COLOR_NEEDLE   = Color(0xFFFFFFFF)

@Composable
fun GlucoseGauge(
    currentMgdL:  Float?,
    settings:     ChartSettings,
    trendArrow:   String,
    glucoseColor: Color,
    modifier:     Modifier = Modifier,
) {
    val targetAngle = if (currentMgdL == null) ARC_START_ANGLE else {
        val fraction = currentMgdL.coerceIn(0f, GAUGE_MAX_MGDL) / GAUGE_MAX_MGDL
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

    Box(
        modifier         = modifier.fillMaxWidth().aspectRatio(1.6f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val padTop = 6.dp.toPx()
            val padBot = 10.dp.toPx()
            // Radio maximo para que el arco quepa:
            //   arriba: cy - R = padTop  =>  cy = R + padTop
            //   abajo:  cy + 0.5R + stroke/2 <= h - padBot
            //   => R + padTop + 0.5R + R*ARC_THICKNESS_FRACTION/2 <= h - padBot
            //   => R * (1.5 + ARC_THICKNESS_FRACTION/2) <= h - padBot - padTop
            val radius = minOf(
                w * 0.42f,
                (h - padTop - padBot) / (1.5f + ARC_THICKNESS_FRACTION / 2f),
            )
            val stroke = radius * ARC_THICKNESS_FRACTION
            val cx = w / 2f
            val cy = padTop + radius + stroke / 2f

            // ---- umbrales en mg/dL -------------------------------------------
            val vl = fromDisplayUnit(settings.veryLow,    settings.unit).coerceIn(0f, GAUGE_MAX_MGDL)
            val tl = fromDisplayUnit(settings.targetLow,  settings.unit).coerceIn(0f, GAUGE_MAX_MGDL)
            val th = fromDisplayUnit(settings.targetHigh, settings.unit).coerceIn(0f, GAUGE_MAX_MGDL)
            val vh = fromDisplayUnit(settings.veryHigh,   settings.unit).coerceIn(0f, GAUGE_MAX_MGDL)

            fun mgdlToAngle(mgdl: Float): Float =
                ARC_START_ANGLE + (mgdl / GAUGE_MAX_MGDL) * ARC_SWEEP

            // ---- 1. Arco de fondo (gris) -------------------------------------
            drawGaugeArc(cx, cy, radius, stroke, ARC_START_ANGLE, ARC_SWEEP, GAUGE_COLOR_TRACK)

            // ---- 2. Segmentos de color ---------------------------------------
            drawGaugeArc(cx, cy, radius, stroke, mgdlToAngle(0f),  mgdlToAngle(vl)            - mgdlToAngle(0f),  GAUGE_COLOR_CRITICAL)
            drawGaugeArc(cx, cy, radius, stroke, mgdlToAngle(vl),  mgdlToAngle(tl)            - mgdlToAngle(vl),  GAUGE_COLOR_WARNING)
            drawGaugeArc(cx, cy, radius, stroke, mgdlToAngle(tl),  mgdlToAngle(th)            - mgdlToAngle(tl),  GAUGE_COLOR_OK)
            drawGaugeArc(cx, cy, radius, stroke, mgdlToAngle(th),  mgdlToAngle(vh)            - mgdlToAngle(th),  GAUGE_COLOR_WARNING)
            drawGaugeArc(cx, cy, radius, stroke, mgdlToAngle(vh),  mgdlToAngle(GAUGE_MAX_MGDL) - mgdlToAngle(vh), GAUGE_COLOR_CRITICAL)

            // ---- 3. Marcas separadoras en los 4 umbrales ---------------------
            listOf(vl, tl, th, vh).forEach { mgdl ->
                val angleRad = Math.toRadians(mgdlToAngle(mgdl).toDouble())
                val innerR   = radius - stroke * 0.6f
                val outerR   = radius + stroke * 0.6f
                drawLine(
                    color       = surfaceColor.copy(alpha = 0.90f),
                    start       = Offset((cx + innerR * cos(angleRad)).toFloat(), (cy + innerR * sin(angleRad)).toFloat()),
                    end         = Offset((cx + outerR * cos(angleRad)).toFloat(), (cy + outerR * sin(angleRad)).toFloat()),
                    strokeWidth = 2.5f.dp.toPx(),
                )
            }

            // ---- 4. Marcas de escala en 0, 100, 200, 300 ---------------------
            listOf(0f, 100f, 200f, 300f).forEach { mgdl ->
                val angleRad = Math.toRadians(mgdlToAngle(mgdl).toDouble())
                val innerR   = radius - stroke * 0.9f
                val outerR   = radius + stroke * 0.4f
                drawLine(
                    color       = surfaceColor.copy(alpha = 0.55f),
                    start       = Offset((cx + innerR * cos(angleRad)).toFloat(), (cy + innerR * sin(angleRad)).toFloat()),
                    end         = Offset((cx + outerR * cos(angleRad)).toFloat(), (cy + outerR * sin(angleRad)).toFloat()),
                    strokeWidth = 1.5f.dp.toPx(),
                )
            }

            // ---- 5. Puntero --------------------------------------------------
            val needleAngleRad = Math.toRadians(animatedAngle.value.toDouble())
            val needleLength   = radius - stroke * 0.5f
            val tailLength     = needleLength * 0.18f
            drawLine(
                color       = GAUGE_COLOR_NEEDLE,
                start       = Offset((cx - tailLength * cos(needleAngleRad)).toFloat(), (cy - tailLength * sin(needleAngleRad)).toFloat()),
                end         = Offset((cx + needleLength * cos(needleAngleRad)).toFloat(), (cy + needleLength * sin(needleAngleRad)).toFloat()),
                strokeWidth = 3.dp.toPx(),
                cap         = StrokeCap.Round,
            )
            drawCircle(color = GAUGE_COLOR_NEEDLE, radius = 5.5f.dp.toPx(), center = Offset(cx, cy))
            drawCircle(color = glucoseColor,       radius = 3.5f.dp.toPx(), center = Offset(cx, cy))

            // ---- 6. Texto centrado en el hueco interior del arco -------------
            // Centro visual del hueco = cy - R*0.50 (mitad entre cy y el tope superior cy-R).
            // Android Paint.drawText usa BASELINE como Y. Para centrar geometricamente:
            //   baselineY = visualCenterY - (paint.ascent() + paint.descent()) / 2
            val visualCenterY = cy - radius * 0.50f

            val textPaint = android.graphics.Paint().apply {
                isAntiAlias  = true
                textAlign    = android.graphics.Paint.Align.CENTER
                typeface      = android.graphics.Typeface.DEFAULT_BOLD
            }

            // Valor grande: tamano proporcional al radio
            textPaint.color    = glucoseColor.toArgb()
            textPaint.textSize = radius * 0.56f
            val valueBaselineY = visualCenterY - (textPaint.ascent() + textPaint.descent()) / 2f
            drawContext.canvas.nativeCanvas.drawText(displayValue, cx, valueBaselineY, textPaint)

            // Unidad + flecha: justo debajo del valor
            textPaint.color    = onSurfaceVariant.toArgb()
            textPaint.textSize = radius * 0.19f
            textPaint.typeface = android.graphics.Typeface.DEFAULT
            // baseline de la unidad = baseline del valor + descent del valor + ascent de la unidad negado
            val unitBaselineY = valueBaselineY + textPaint.descent() * 3.5f - textPaint.ascent()
            drawContext.canvas.nativeCanvas.drawText("$unitSuffix  $trendArrow", cx, unitBaselineY, textPaint)
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

private fun fromDisplayUnit(displayValue: Float, unit: GlucoseUnit): Float =
    if (unit == GlucoseUnit.MMOL) displayValue * 18.0f else displayValue

private fun toDisplayUnit(mgdl: Float, unit: GlucoseUnit): Float =
    if (unit == GlucoseUnit.MMOL) mgdl / 18.0f else mgdl