/*
 * GlucoseGauge — velocímetro de glucosa en tiempo real.
 *
 * Dibuja un arco de 240 grados con:
 *   - 5 zonas de color segun los umbrales del usuario
 *     (veryLow / targetLow / targetHigh / veryHigh)
 *   - Puntero radial animado que apunta al valor actual
 *   - Valor numerico grande en el centro del arco
 *   - Flecha de tendencia y unidad debajo del valor
 *   - Marcas separadoras en los 4 umbrales y en 0/100/200/300
 *
 * No usa Vico - implementado con Canvas de Compose puro.
 * Todos los valores se reciben desde GlucoseChartScreen (mismo origen que StatsPanel).
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

private const val ARC_START_ANGLE = 150f
private const val ARC_SWEEP       = 240f
private const val GAUGE_MAX_MGDL  = 300f
private const val ARC_THICKNESS_FRACTION = 0.115f

private val GAUGE_COLOR_CRITICAL = Color(0xFFE74C3C)
private val GAUGE_COLOR_WARNING  = Color(0xFFF39C12)
private val GAUGE_COLOR_OK       = Color(0xFF2ECC71)
private val GAUGE_COLOR_TRACK    = Color(0x228E8E93)
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
        modifier         = modifier.fillMaxWidth().aspectRatio(1.85f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx     = size.width / 2f
            val cy     = size.height * 0.70f
            val radius = minOf(size.width * 0.44f, size.height * 0.80f)
            val stroke = radius * ARC_THICKNESS_FRACTION

            drawGaugeArc(cx, cy, radius, stroke,
                startAngle = ARC_START_ANGLE, sweepAngle = ARC_SWEEP,
                color = GAUGE_COLOR_TRACK)

            val vl = fromDisplayUnit(settings.veryLow,    settings.unit).coerceIn(0f, GAUGE_MAX_MGDL)
            val tl = fromDisplayUnit(settings.targetLow,  settings.unit).coerceIn(0f, GAUGE_MAX_MGDL)
            val th = fromDisplayUnit(settings.targetHigh, settings.unit).coerceIn(0f, GAUGE_MAX_MGDL)
            val vh = fromDisplayUnit(settings.veryHigh,   settings.unit).coerceIn(0f, GAUGE_MAX_MGDL)

            fun mgdlToAngle(mgdl: Float): Float =
                ARC_START_ANGLE + (mgdl / GAUGE_MAX_MGDL) * ARC_SWEEP

            drawGaugeArc(cx, cy, radius, stroke, mgdlToAngle(0f),  mgdlToAngle(vl) - mgdlToAngle(0f), GAUGE_COLOR_CRITICAL)
            drawGaugeArc(cx, cy, radius, stroke, mgdlToAngle(vl),  mgdlToAngle(tl) - mgdlToAngle(vl), GAUGE_COLOR_WARNING)
            drawGaugeArc(cx, cy, radius, stroke, mgdlToAngle(tl),  mgdlToAngle(th) - mgdlToAngle(tl), GAUGE_COLOR_OK)
            drawGaugeArc(cx, cy, radius, stroke, mgdlToAngle(th),  mgdlToAngle(vh) - mgdlToAngle(th), GAUGE_COLOR_WARNING)
            drawGaugeArc(cx, cy, radius, stroke, mgdlToAngle(vh),  mgdlToAngle(GAUGE_MAX_MGDL) - mgdlToAngle(vh), GAUGE_COLOR_CRITICAL)

            listOf(vl, tl, th, vh).forEach { mgdl ->
                val angleRad = Math.toRadians(mgdlToAngle(mgdl).toDouble())
                val innerR   = radius - stroke * 0.55f
                val outerR   = radius + stroke * 0.55f
                drawLine(
                    color       = surfaceColor.copy(alpha = 0.85f),
                    start       = Offset((cx + innerR * cos(angleRad)).toFloat(), (cy + innerR * sin(angleRad)).toFloat()),
                    end         = Offset((cx + outerR * cos(angleRad)).toFloat(), (cy + outerR * sin(angleRad)).toFloat()),
                    strokeWidth = 2.dp.toPx(),
                )
            }

            listOf(0f, 100f, 200f, 300f).forEach { mgdl ->
                val angleRad = Math.toRadians(mgdlToAngle(mgdl).toDouble())
                val innerR   = radius - stroke
                val outerR   = radius + stroke * 0.3f
                drawLine(
                    color       = surfaceColor.copy(alpha = 0.5f),
                    start       = Offset((cx + innerR * cos(angleRad)).toFloat(), (cy + innerR * sin(angleRad)).toFloat()),
                    end         = Offset((cx + outerR * cos(angleRad)).toFloat(), (cy + outerR * sin(angleRad)).toFloat()),
                    strokeWidth = 1.5f.dp.toPx(),
                )
            }

            val needleAngleRad = Math.toRadians(animatedAngle.value.toDouble())
            val needleLength   = radius - stroke * 0.5f
            val needleEnd  = Offset((cx + needleLength * cos(needleAngleRad)).toFloat(), (cy + needleLength * sin(needleAngleRad)).toFloat())
            val tailLength = needleLength * 0.18f
            val tailEnd    = Offset((cx - tailLength * cos(needleAngleRad)).toFloat(), (cy - tailLength * sin(needleAngleRad)).toFloat())
            drawLine(color = GAUGE_COLOR_NEEDLE, start = tailEnd, end = needleEnd, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(color = GAUGE_COLOR_NEEDLE, radius = 5.5f.dp.toPx(), center = Offset(cx, cy))
            drawCircle(color = glucoseColor,       radius = 3.5f.dp.toPx(), center = Offset(cx, cy))

            val textPaint = android.graphics.Paint().apply {
                isAntiAlias  = true
                textAlign    = android.graphics.Paint.Align.CENTER
            }
            textPaint.color          = glucoseColor.toArgb()
            textPaint.textSize       = radius * 0.48f
            textPaint.isFakeBoldText = true
            drawContext.canvas.nativeCanvas.drawText(displayValue, cx, cy - radius * 0.20f, textPaint)

            textPaint.color          = onSurfaceVariant.toArgb()
            textPaint.textSize       = radius * 0.17f
            textPaint.isFakeBoldText = false
            drawContext.canvas.nativeCanvas.drawText("$unitSuffix  $trendArrow", cx, cy + radius * 0.04f, textPaint)
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
