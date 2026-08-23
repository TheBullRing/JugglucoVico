/*
 * Fase 8 — Menús Compose nativos para el path Vico.
 *
 * VicoMenuHost: composable que observa el VicoMenuState actual y
 * despacha al diálogo/pantalla correspondiente.
 *
 * Todos los estados se despachan a diálogos Compose nativos.
 * Solo M8 (History), M10 (SensorInfo) e Iob siguen delegando en Java
 * porque su implementación es compleja (RecyclerView, lógica JNI específica).
 */
package tk.glucodata.ui.menus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Composable que observa [VicoMenuViewModel.state] y renderiza el diálogo
 * o pantalla Compose correspondiente.
 *
 * @param viewModel       ViewModel del menú Vico.
 * @param onJavaDelegate  Llamado únicamente para M8/History, M10/SensorInfo
 *                        e Iob (aún no migrados a Compose).
 */
@Composable
fun VicoMenuHost(
    viewModel:      VicoMenuViewModel,
    onJavaDelegate: (VicoMenuState) -> Unit = {},
) {
    val menuState by viewModel.state.collectAsStateWithLifecycle()

    when (menuState) {
        // M4 — Ajustes
        is VicoMenuState.Settings ->
            VicoSettingsScreen(
                onDismiss    = { viewModel.dismiss() },
                onNavigateTo = { viewModel.show(it) },
            )

        // M12a — Cargador Nightscout
        is VicoMenuState.NightscoutUploader ->
            VicoNightscoutUploaderDialog(onDismiss = { viewModel.dismiss() })

        // Sub-menú Intercambiar datos
        is VicoMenuState.DataExchange ->
            VicoDataExchangeMenu(
                onNightscoutUploader = { viewModel.show(VicoMenuState.NightscoutUploader) },
                onNightscoutFollower = { viewModel.show(VicoMenuState.NightscoutFollower) },
                onLibreView          = { viewModel.show(VicoMenuState.LibreView) },
                onDismiss            = { viewModel.show(VicoMenuState.Settings) },
            )

        // M12b — Seguidor Nightscout (servidor Xdrip local)
        is VicoMenuState.NightscoutFollower ->
            VicoNightscoutFollowerDialog(onDismiss = { viewModel.show(VicoMenuState.DataExchange) })

        // M12c — LibreView
        is VicoMenuState.LibreView ->
            VicoLibreViewDialog(onDismiss = { viewModel.show(VicoMenuState.DataExchange) })

        // M3 — Calibración
        is VicoMenuState.Calibration ->
            VicoCalibrationDialog(onDismiss = { viewModel.show(VicoMenuState.Settings) })

        // M5 — Estadísticas
        is VicoMenuState.Stats ->
            VicoStatsDialog(onDismiss = { viewModel.dismiss() })

        // M11 — Alarmas
        is VicoMenuState.Alarms ->
            VicoAlarmsDialog(onDismiss = { viewModel.show(VicoMenuState.Settings) })

        // M6/Watch — Conectividad de relojes y gadgets (NATIVO COMPOSE)
        is VicoMenuState.Watch ->
            VicoWatchDialog(
                onDismiss         = { viewModel.dismiss() },
                onConfigureWearOS = { /* Wearos.show() requiere contexto Java; por ahora dismiss */ viewModel.dismiss() },
                onConfigureServer = { viewModel.show(VicoMenuState.NightscoutFollower) },
            )

        // M7/NumberEntry — Entrada manual de glucosa (NATIVO COMPOSE)
        is VicoMenuState.NumberEntry ->
            VicoNumberEntryDialog(onDismiss = { viewModel.dismiss() })

        // M13/Meal — Registro de comida simplificado (NATIVO COMPOSE)
        is VicoMenuState.Meal ->
            VicoMealSimpleDialog(onDismiss = { viewModel.dismiss() })

        // About — diálogo Compose nativo de información de versión
        is VicoMenuState.About ->
            VicoAboutDialog(onDismiss = { viewModel.dismiss() })

        // Export — el file picker del sistema requiere onActivityResult;
        // sigue delegando al overlay Java existente (Dialogs.showexport).
        // BlueSensor, History, SensorInfo, Iob, LastScan — Delegación en código Java de la Activity.
        // LaunchedEffect garantiza que la llamada ocurre una sola vez por cambio de estado.
        is VicoMenuState.BlueSensor,
        is VicoMenuState.History,
        is VicoMenuState.SensorInfo,
        is VicoMenuState.Iob,
        is VicoMenuState.Export,
        is VicoMenuState.LastScan -> {
            val capturedState = menuState
            LaunchedEffect(capturedState) {
                onJavaDelegate(capturedState)
                viewModel.dismiss()
            }
        }

        is VicoMenuState.None -> { /* Sin diálogo activo */ }
    }
}
