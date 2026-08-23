/*
 * GlucoseGauge -- velocimetro de glucosa en tiempo real.
 *
 * Geometria del arco:
 *   - ARC_START = 150 deg  (extremo inferior izquierdo del arco)
 *   - ARC_SWEEP = 240 deg  (el arco recorre 240 grados en sentido horario)
 *   - ARC_END   = 390 = 30 deg (extremo inferior derecho del arco)
 *   - Los extremos del arco caen en:
 *       start: (cx + R*cos(150), cy + R*sin(150)) = (cx - 0.866R, cy + 0.5R)
 *       end:   (cx + R*cos(30),  cy + R*sin(30))  = (cx + 0.866R, cy + 0.5R)
 *   => El punto mas bajo del arco es cy + 0.5R.
 *   => El punto mas alto del arco es cy - R (en el angulo 270 deg = tope superior).
 *   => Para que el arco cabe en el canvas de altura H:
 *        cy - R >= margen_top  =>  cy >= R + margen_top
 *        cy + 0.5R + stroke/2 <= H - margen_bot
 *
 * Con aspectRatio = 1.6 el canvas tiene H = W / 1.6.
 * Radio elegido = W * 0.40 => H = W/1.6 = 0.625W.
 * cy = H * 0.62 = 0.3875W.
 *   - top del arco: cy - R = 0.3875W - 0.40W = -0.0125W  (ligeramente fuera arriba, corregido con margen_top)
 * Se usa R = W * 0.385 y cy = H * 0.65 para que todo quede dentro.
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

// ----- Geometria ---------------------------------------------------------
// El arco empieza a 150 deg (abajo-izquierda) y va 240 deg en sentido horario
// hasta 390 deg = 30 deg (abajo-derecha).  El 0 mg/dL esta a la izquierda
// y el 300 mg/dL a la derecha, igual que un velocimetro de coche.
private const val ARC_START_ANGLE        = 150f   // grados, sistema Android (0=derecha, CW)
private const val ARC_SWEEP              = 240f   // grados totales del arco
private const val GAUGE_MAX_MGDL         = 300f
// El extremo inferior del arco cae a sin(150)=0.5 * R por debajo del centro.
// Reservamos ese espacio mas un margen de grosor del trazo.
// cy se calcula dinamicamente dentro del Canvas para que siempre quepa.

private const val ARC_THICKNESS_FRACTION = 0.13f  // grosor = 13% del radio

// ----- Colores -----------------------------------------------------------
private val GAUGE_COLOR_CRITICAL = Color(0xFFE74C3C)  // rojo
private val GAUGE_COLOR_WARNING  = Color(0xFFF39C12)  // naranja
private val GAUGE_COLOR_OK       = Color(0xFF2ECC71)  // verde
// Track con alpha suficiente para que se vea sobre fondos claros y oscuros
private val GAUGE_COLOR_TRACK    = Color(0x558E8E93)  // gris 33% alpha
private val GAUGE_COLOR_NEEDLE   = Color(0xFFFFFFFF)  // blanco

// -------------------------------------------------------------------------

@Composable
fun GlucoseGauge(
    currentMgdL:  Float?,
    settings:     ChartSettings,
    trendArrow:   String,
    glucoseColor: Color,
    modifier:     Modifier = Modifier,
) {
    // Angulo objetivo del puntero
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

    // aspectRatio = 1.6 da suficiente altura para que los extremos inferiores
    // del arco (que caen 0.5R por debajo del centro) no se corten.
    Box(
        modifier         = modifier.fillMaxWidth().aspectRatio(1.6f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Radio: limitado para que quepa entero en el canvas.
            // El arco ocupa: arriba  cy - R  y  abajo  cy + R*sin(150) = cy + 0.5R.
            // Con un margen de stroke/2 a cada lado:
            //   R + stroke/2 <= cy                     (arriba)
            //   cy + 0.5*R + stroke/2 <= h - pad_bot   (abajo, pad_bot = 8dp)
            // Elegimos R = min(w*0.40, h*0.56) y cy = R * 1.10 + pad_top
            val padTop = 6.dp.toPx()
            val padBot = 8.dp.toPx()
            val radius  = minOf(w * 0.40f, (h - padTop - padBot) / (1f + 0.5f + ARC_THICKNESS_FRACTION))
            val stroke  = radius * ARC_THICKNESS_FRACTION
            // cy calculado para que el tope superior del arco (cy - R) este a padTop del borde
            val cx = w / 2f
            val cy = padTop + radius + stroke / 2f

            // ---- 1. Arco de fondo (gris) ----------------------------------
            drawGaugeArc(cx, cy, radius, stroke,
                ARC_START_ANGLE, ARC_SWEEP, GAUGE_COLOR_TRACK)

            // ---- 2. Zonas de color ----------------------------------------
            // Umbrales en mg/dL (fromDisplayUnit convierte si el usuario usa mmol/L)
            val vl = fromDisplayUnit(settings.veryLow,    settings.unit).coerceIn(0f, GAUGE_MAX_MGDL)
            val tl = fromDisplayUnit(settings.targetLow,  settings.unit).coerceIn(0f, GAUGE_MAX_MGDL)
            val th = fromDisplayUnit(settings.targetHigh, settings.unit).coerceIn(0f, GAUGE_MAX_MGDL)
            val vh = fromDisplayUnit(settings.veryHigh,   settings.unit).coerceIn(0f, GAUGE_MAX_MGDL)

            // mgdlToAngle: mapea 0..300 mg/dL al rango angular 150..390 grados
            fun mgdlToAngle(mgdl: Float): Float =
                ARC_START_ANGLE + (mgdl / GAUGE_MAX_MGDL) * ARC_SWEEP

            // Los segmentos se pintan sobre el track (mismo radio/grosor) => tapan el gris
            drawGaugeArc(cx, cy, radius, stroke, mgdlToAngle(0f),  mgdlToAngle(vl)           - mgdlToAngle(0f),  GAUGE_COLOR_CRITICAL)
            drawGaugeArc(cx, cy, radius, stroke, mgdlToAngle(vl),  mgdlToAngle(tl)           - mgdlToAngle(vl),  GAUGE_COLOR_WARNING)
            drawGaugeArc(cx, cy, radius, stroke, mgdlToAngle(tl),  mgdlToAngle(th)           - mgdlToAngle(tl),  GAUGE_COLOR_OK)
            drawGaugeArc(cx, cy, radius, stroke, mgdlToAngle(th),  mgdlToAngle(vh)           - mgdlToAngle(th),  GAUGE_COLOR_WARNING)
            drawGaugeArc(cx, cy, radius, stroke, mgdlToAngle(vh),  mgdlToAngle(GAUGE_MAX_MGDL) - mgdlToAngle(vh), GAUGE_COLOR_CRITICAL)

            // ---- 3. Marcas separadoras en los 4 umbrales ------------------
            listOf(vl, tl, th, vh).forEach { mgdl ->
                val angleRad = Math.toRadians(mgdlToAngle(mgdl).toDouble())
                val innerR   = radius - stroke * 0.6f
                val outerR   = radius + stroke * 0.6f
                drawLine(
                    color       = surfaceColor.copy(alpha = 0.90f),
                    start       = Offset((cx + innerR * cos(angleRad)).toFloat(),
                                         (cy + innerR * sin(angleRad)).toFloat()),
                    end         = Offset((cx + outerR * cos(angleRad)).toFloat(),
                                         (cy + outerR * sin(angleRad)).toFloat()),
                    strokeWidth = 2.5f.dp.toPx(),
                )
            }

            // ---- 4. Marcas de escala en 0, 100, 200, 300 ------------------
            listOf(0f, 100f, 200f, 300f).forEach { mgdl ->
                val angleRad = Math.toRadians(mgdlToAngle(mgdl).toDouble())
                val innerR   = radius - stroke * 0.9f
                val outerR   = radius + stroke * 0.4f
                drawLine(
                    color       = surfaceColor.copy(alpha = 0.55f),
                    start       = Offset((cx + innerR * cos(angleRad)).toFloat(),
                                         (cy + innerR * sin(angleRad)).toFloat()),
                    end         = Offset((cx + outerR * cos(angleRad)).toFloat(),
                                         (cy + outerR * sin(angleRad)).toFloat()),
                    strokeWidth = 1.5f.dp.toPx(),
                )
            }

            // ---- 5. Puntero -----------------------------------------------
            val needleAngleRad = Math.toRadians(animatedAngle.value.toDouble())
            val needleLength   = radius - stroke * 0.5f
            val tailLength     = needleLength * 0.18f
            drawLine(
                color       = GAUGE_COLOR_NEEDLE,
                start       = Offset((cx - tailLength * cos(needleAngleRad)).toFloat(),
                                      (cy - tailLength * sin(needleAngleRad)).toFloat()),
                end         = Offset((cx + needleLength * cos(needleAngleRad)).toFloat(),
                                      (cy + needleLength * sin(needleAngleRad)).toFloat()),
                strokeWidth = 3.dp.toPx(),
                cap         = StrokeCap.Round,
            )
            drawCircle(color = GAUGE_COLOR_NEEDLE, radius = 5.5f.dp.toPx(), center = Offset(cx, cy))
            drawCircle(color = glucoseColor,       radius = 3.5f.dp.toPx(), center = Offset(cx, cy))

            // ---- 6. Texto -------------------------------------------------
            // El centro visual del hueco del arco esta aprox en cy - R*0.35
            // (entre el centro geometrico cy y el tope superior cy-R).
            val textCenterY = cy - radius * 0.30f

            val textPaint = android.graphics.Paint().apply {
                isAntiAlias  = true
                textAlign    = android.graphics.Paint.Align.CENTER
            }
            // Valor grande
            textPaint.color          = glucoseColor.toArgb()
            textPaint.textSize       = radius * 0.52f
            textPaint.isFakeBoldText = true
            drawContext.canvas.nativeCanvas.drawText(displayValue, cx, textCenterY, textPaint)

            // Unidad + flecha
            textPaint.color          = onSurfaceVariant.toArgb()
            textPaint.textSize       = radius * 0.185f
            textPaint.isFakeBoldText = false
            drawContext.canvas.nativeCanvas.drawText(
                "$unitSuffix  $trendArrow",
                cx,
                textCenterY + radius * 0.26f,
                textPaint,
            )
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