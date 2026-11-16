/*
 * Fase 8 — M2: resultado del escaneo NFC (path Vico).
 *
 * VicoScanResultState: estado estático alimentado desde Java
 * (MainActivity.showVicoScanResult) cuando se completa un escaneo NFC.
 *
 * VicoScanResultPopup: diálogo Material 3 que muestra la glucosa leída en
 * tamaño grande y el número de serie del sensor en tamaño pequeño.
 * Se cierra solo a los 8 s o al tocarlo.
 *
 * Reglas:
 *  - Ninguna clase NanoVG importa este fichero.
 */
package tk.glucodata.ui.menus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Resultado de un escaneo NFC listo para mostrar. [token] distingue escaneos consecutivos. */
data class VicoScanResult(
    val value: String,
    val unit: String,
    val sensorId: String,
    val token: Long,
)

object VicoScanResultState {
    private val _result = MutableStateFlow<VicoScanResult?>(null)
    val result: StateFlow<VicoScanResult?> = _result.asStateFlow()

    /** Llamado desde Java; seguro desde cualquier hilo. */
    @JvmStatic
    fun show(value: String, unit: String, sensorId: String?) {
        _result.value = VicoScanResult(value, unit, sensorId.orEmpty(), System.nanoTime())
    }

    @JvmStatic
    fun dismiss() { _result.value = null }
}

private const val AUTO_DISMISS_MS = 8_000L

@Composable
fun VicoScanResultPopup() {
    val result by VicoScanResultState.result.collectAsStateWithLifecycle()
    val current = result ?: return

    LaunchedEffect(current.token) {
        delay(AUTO_DISMISS_MS)
        VicoScanResultState.dismiss()
    }

    Dialog(onDismissRequest = { VicoScanResultState.dismiss() }) {
        Surface(
            shape          = MaterialTheme.shapes.extraLarge,
            color          = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier       = Modifier
                .widthIn(min = 220.dp)
                .clickable { VicoScanResultState.dismiss() },
        ) {
            Column(
                modifier            = Modifier.padding(horizontal = 32.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text       = current.value,
                    fontSize   = 64.sp,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text  = current.unit,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (current.sensorId.isNotEmpty()) {
                    Text(
                        text     = current.sensorId,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}
