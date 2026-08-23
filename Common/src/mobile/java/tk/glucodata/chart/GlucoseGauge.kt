/*
 * GlucoseGauge -- velocimetro de glucosa en tiempo real.
 *
 * Geometria del arco (sistema Android: 0=derecha, sentido horario):
 *   ARC_START = 150 deg  => extremo inferior izquierdo
 *   ARC_END   =  30 deg  => extremo inferior derecho
 *   Punto mas alto: 270 deg (tope del arco) => cy - R
 *   Puntos extremos inferiores: cy + R*sin(150) = cy + 0.5*R
 *
 * Colores respetan los umbrales del usuario:
 *   [0, veryLow)               => ROJO
 *   [veryLow, targetLow)       => NARANJA
 *   [targetLow, targetHigh]    => VERDE
 *   (targetHigh, veryHigh]     => NARANJA
 *   (veryHigh, GAUGE_MAX_MGDL] => ROJO
 *
 * Aguja triangular con punta en el borde interior del arco.
 * Etiquetas numericas en el exterior del arco en veryLow, targetLow,
 * targetHigh, veryHigh y en los extremos 0/GAUGE_MAX.
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
private const val GAUGE_MAX_MGDL         = 300f
private const val ARC_THICKNESS_FRACTION = 0.13f

private val GAUGE_COLOR_CRITICAL = Color(0xFFE74C3C)
private val GAUGE_COLOR_WARNING  = Color(0xFFF39C12)
private val GAUGE_COLOR_OK       = Color(0xFF2ECC71)
private val GAUGE_COLOR_TRACK    = Color(0x558E8E93)
private val GAUGE_COLOR_NEEDLE   = Color(0xFF1A1A2E)   // gris muy oscuro, visible en cualquier fondo

// Convierte valor display a mg/dL (necesario aqui para que mgdlToAngle
// pueda usarse antes de que el Canvas este construido)
private fun fromDisplayUnit(displayValue: Float, unit: GlucoseUnit): Float =
    if (unit == GlucoseUnit.MMOL) displayValue * 18.0f else displayValue

private fun toDisplayUnit(mgdl: Float, unit: GlucoseUnit): Float =
    if (unit == GlucoseUnit.MMOL) mgdl / 18.0f else mgdl

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

            // Espacio para etiquetas numéricas exteriores:
            //   - arriba/laterales: las etiquetas superiores e inferiores del arco
            //     (en 0° y 300 mg/dL, ángulos ~150° y ~30°) sobresalen 1.5× el tamaño
            //     del texto fuera del radio. Reservamos ~18dp por lado.
            val padTop  = 20.dp.toPx()
            val padBot  = 10.dp.toPx()
            val padSide = 32.dp.toPx()   // aumentado para que 0 y 300 no se corten

            val radius = minOf(
                w / 2f - padSide,
                (h - padTop - padBot) / (1.5f + ARC_THICKNESS_FRACTION / 2f),
            )
            val stroke = radius * ARC_THICKNESS_FRACTION
            val cx = w / 2f
            val cy = padTop + radius + stroke / 2f

            // ---- umbrales en mg/dL (los settings están en unidades de display) ----
            val vl = fromDisplayUnit(settings.veryLow,    settings.unit).coerceIn(0f, GAUGE_MAX_MGDL)
            val tl = fromDisplayUnit(settings.targetLow,  settings.unit).coerceIn(0f, GAUGE_MAX_MGDL)
            val th = fromDisplayUnit(settings.targetHigh, settings.unit).coerceIn(0f, GAUGE_MAX_MGDL)
            val vh = fromDisplayUnit(settings.veryHigh,   settings.unit).coerceIn(0f, GAUGE_MAX_MGDL)

            fun mgdlToAngle(mgdl: Float): Float =
                ARC_START_ANGLE + (mgdl.coerceIn(0f, GAUGE_MAX_MGDL) / GAUGE_MAX_MGDL) * ARC_SWEEP

            fun deg2rad(deg: Float) = Math.toRadians(deg.toDouble())

            // ---- 1. Arco de fondo (gris) -----------------------------------------
            drawGaugeArc(cx, cy, radius, stroke, ARC_START_ANGLE, ARC_SWEEP, GAUGE_COLOR_TRACK)

            // ---- 2. Cinco segmentos de color según umbrales del usuario ----------
            drawGaugeArc(cx, cy, radius, stroke, mgdlToAngle(0f),  mgdlToAngle(vl) - mgdlToAngle(0f),             GAUGE_COLOR_CRITICAL)
            drawGaugeArc(cx, cy, radius, stroke, mgdlToAngle(vl),  mgdlToAngle(tl) - mgdlToAngle(vl),             GAUGE_COLOR_WARNING)
            drawGaugeArc(cx, cy, radius, stroke, mgdlToAngle(tl),  mgdlToAngle(th) - mgdlToAngle(tl),             GAUGE_COLOR_OK)
            drawGaugeArc(cx, cy, radius, stroke, mgdlToAngle(th),  mgdlToAngle(vh) - mgdlToAngle(th),             GAUGE_COLOR_WARNING)
            drawGaugeArc(cx, cy, radius, stroke, mgdlToAngle(vh),  mgdlToAngle(GAUGE_MAX_MGDL) - mgdlToAngle(vh), GAUGE_COLOR_CRITICAL)

            // ---- 3. Divisores blancos en los 4 umbrales --------------------------
            listOf(vl, tl, th, vh).forEach { mgdl ->
                val rad    = deg2rad(mgdlToAngle(mgdl))
                val innerR = radius - stroke * 0.6f
                val outerR = radius + stroke * 0.6f
                drawLine(
                    color       = surfaceColor.copy(alpha = 0.90f),
                    start       = Offset((cx + innerR * cos(rad)).toFloat(), (cy + innerR * sin(rad)).toFloat()),
                    end         = Offset((cx + outerR * cos(rad)).toFloat(), (cy + outerR * sin(rad)).toFloat()),
                    strokeWidth = 2.5f.dp.toPx(),
                )
            }

            // ---- 4. Etiquetas numéricas en los umbrales y extremos ---------------
            // Se muestran en la unidad del usuario fuera del arco
            val labelMgdlList = listOf(0f, vl, tl, th, vh, GAUGE_MAX_MGDL)
            val labelPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                textAlign   = android.graphics.Paint.Align.CENTER
                textSize    = (radius * 0.16f).coerceAtMost(12.dp.toPx())
                color       = onSurfaceVariant.toArgb()
            }
            // Distancia desde el centro del arco hasta el centro del texto
            val labelDist = radius + stroke * 0.62f + labelPaint.textSize * 0.55f

            labelMgdlList.forEach { mgdl ->
                val deg = mgdlToAngle(mgdl)
                val rad = deg2rad(deg)
                // Tick pequeño hacia dentro del arco
                val tickInner = radius - stroke * 0.90f
                val tickOuter = radius + stroke * 0.10f
                drawLine(
                    color       = surfaceColor.copy(alpha = 0.60f),
                    start       = Offset((cx + tickInner * cos(rad)).toFloat(), (cy + tickInner * sin(rad)).toFloat()),
                    end         = Offset((cx + tickOuter * cos(rad)).toFloat(), (cy + tickOuter * sin(rad)).toFloat()),
                    strokeWidth = 1.5f.dp.toPx(),
                )
                // Texto de la etiqueta
                val dispVal = toDisplayUnit(mgdl, settings.unit)
                val label = if (settings.unit == GlucoseUnit.MMOL) "%.1f".format(dispVal) else dispVal.toInt().toString()
                val lx = (cx + labelDist * cos(rad)).toFloat()
                val ly = (cy + labelDist * sin(rad)).toFloat()
                val textBaselineY = ly - (labelPaint.ascent() + labelPaint.descent()) / 2f
                drawContext.canvas.nativeCanvas.drawText(label, lx, textBaselineY, labelPaint)
            }

            // ---- 5. Aguja triangular ---------------------------------------------
            // La punta llega al borde interior del arco; la base nace en el pivot.
            val needleAngleRad = deg2rad(animatedAngle.value)
            val needleTip   = radius - stroke * 0.55f  // punta: borde interior del arco
            val needleBase  = radius * 0.18f           // cola detrás del pivot
            val halfWidth   = stroke * 0.28f           // semiancho de la base

            val dirX  = ( cos(needleAngleRad)).toFloat()
            val dirY  = ( sin(needleAngleRad)).toFloat()
            val perpX = (-sin(needleAngleRad)).toFloat()
            val perpY = ( cos(needleAngleRad)).toFloat()

            val tipX  = cx + needleTip  * dirX
            val tipY  = cy + needleTip  * dirY
            val baseX = cx - needleBase * dirX
            val baseY = cy - needleBase * dirY

            val needlePath = Path().apply {
                moveTo(tipX, tipY)
                lineTo(baseX + halfWidth * perpX, baseY + halfWidth * perpY)
                lineTo(baseX - halfWidth * perpX, baseY - halfWidth * perpY)
                close()
            }
            // Aguja blanca con contorno fino para visibilidad en cualquier fondo
            drawPath(needlePath, color = GAUGE_COLOR_NEEDLE)
            drawPath(needlePath, color = surfaceColor.copy(alpha = 0.35f), style = Stroke(width = 1.dp.toPx()))

            // Pivot central
            drawCircle(color = GAUGE_COLOR_NEEDLE, radius = 5.5f.dp.toPx(), center = Offset(cx, cy))
            drawCircle(color = glucoseColor,       radius = 3.5f.dp.toPx(), center = Offset(cx, cy))

            // ---- 6. Texto del valor en el hueco interior -------------------------
            val visualCenterY = cy - radius * 0.50f

            val textPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                textAlign   = android.graphics.Paint.Align.CENTER
                typeface    = android.graphics.Typeface.DEFAULT_BOLD
            }

            textPaint.color    = glucoseColor.toArgb()
            textPaint.textSize = radius * 0.56f
            val valueBaselineY = visualCenterY - (textPaint.ascent() + textPaint.descent()) / 2f
            drawContext.canvas.nativeCanvas.drawText(displayValue, cx, valueBaselineY, textPaint)

            textPaint.color    = onSurfaceVariant.toArgb()
            textPaint.textSize = radius * 0.19f
            textPaint.typeface = android.graphics.Typeface.DEFAULT
            val unitBaselineY  = valueBaselineY + textPaint.descent() * 3.5f - textPaint.ascent()
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