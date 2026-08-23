/*
 * Fase 8 — Menús Compose nativos para el path Vico.
 *
 * VicoMenuState: jerarquía sellada que representa qué pantalla/diálogo
 * del path Vico está visible en cada momento.
 *
 * VicoMenuViewModel: ViewModel de Activity que mantiene el StateFlow del
 * estado activo. Las lambdas de MainActivity lo llaman directamente en
 * lugar de abrir los diálogos Java originales.
 *
 * Reglas:
 *  - Ninguna clase NanoVG importa este fichero.
 *  - Los diálogos Java originales siguen intactos y funcionales en el path NanoVG.
 */
package tk.glucodata.ui.menus

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ─────────────────────────────────────────────────────────────────────────────
// Estado del menú — una subclase activa a la vez
// ─────────────────────────────────────────────────────────────────────────────

sealed class VicoMenuState {
    /** Sin diálogo visible — estado por defecto. */
    object None : VicoMenuState()

    /** M4 — Pantalla de ajustes completa. */
    object Settings : VicoMenuState()

    /** M12a — Cargador Nightscout (NightPost). */
    object NightscoutUploader : VicoMenuState()

    /** M12b — Seguidor Nightscout. */
    object NightscoutFollower : VicoMenuState()

    /** M12c — LibreView. */
    object LibreView : VicoMenuState()

    /** M6 — Sensor Bluetooth. */
    object BlueSensor : VicoMenuState()

    /** M6/Watch — Conectividad de relojes y gadgets (Watch.show()). */
    object Watch : VicoMenuState()

    /** M3 — Calibración (entrada de glucosa capilar). */
    object Calibration : VicoMenuState()

    /** M13 — Registro de comida. */
    object Meal : VicoMenuState()

    /** M5 — Estadísticas / Time-in-Range. */
    object Stats : VicoMenuState()

    /** M8 — Historial / registro de datos. */
    object History : VicoMenuState()

    /** M11 — Configuración de alarmas. */
    object Alarms : VicoMenuState()

    /** M7 — Entrada manual de glucosa / etiquetas. */
    object NumberEntry : VicoMenuState()

    /** M10 — Información del sensor (callback showsensorinfo). */
    object SensorInfo : VicoMenuState()

    /** M14 — Entrada de insulina activa (IOB). */
    object Iob : VicoMenuState()

    /** Sub-menú de intercambio de datos (desde Settings → Intercambiar datos). */
    object DataExchange : VicoMenuState()

    /** Acerca de — información de versión y licencia. */
    object About : VicoMenuState()

    /** Exportar datos (scans, stream, historial, comidas…). */
    object Export : VicoMenuState()

    /** Último escaneo NFC — muestra el resultado del último scan. */
    object LastScan : VicoMenuState()

    /** Sensor — selección de sensor Bluetooth (bluediag.start). */
    object Sensor : VicoMenuState()

    /** Clon / Backup — copia de seguridad (Backup.mkbackupview). */
    object Backup : VicoMenuState()

    /** Hablar — configuración de TTS (Talker.config). */
    object Talk : VicoMenuState()

    /** Buscar — buscar datos en el gráfico (curveRef.startsearch). */
    object Search : VicoMenuState()

    /** Fecha — selector de fecha para el gráfico (curveRef.startdatepick). */
    object DatePick : VicoMenuState()
}

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * ViewModel de Activity para el menú del path Vico.
 *
 * Se obtiene con [androidx.lifecycle.ViewModelProvider] en [GlucoseChartViewBridge]
 * y se pasa a [GlucoseChartScreen] y a las lambdas de [MainActivity.startdisplay()].
 */
class VicoMenuViewModel : ViewModel() {
    private val _state = MutableStateFlow<VicoMenuState>(VicoMenuState.None)
    val state: StateFlow<VicoMenuState> = _state.asStateFlow()

    fun show(state: VicoMenuState) { _state.value = state }
    fun dismiss() { _state.value = VicoMenuState.None }
}
