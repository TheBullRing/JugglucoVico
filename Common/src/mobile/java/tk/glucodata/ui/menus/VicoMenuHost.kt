/*
 * Fase 8 — Menús Compose nativos para el path Vico.
 *
 * VicoMenuHost: composable que observa el VicoMenuState actual y
 * despacha al diálogo/pantalla correspondiente.
 *
 * Para los estados que delegan en código Java existente (BlueSensor, Meal,
 * History, NumberEntry, SensorInfo, Iob), se invoca [onJavaDelegate] con el
 * estado correspondiente y luego se hace dismiss automático para limpiar el
 * VicoMenuState.  El código Java que muestra la vista View real lo invoca
 * la Activity desde dentro del lambda de [onJavaDelegate].
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
 * @param onJavaDelegate  Llamado cuando el estado activo corresponde a un diálogo
 *                        que se implementa con código Java/View (M6, M7, M8, M10,
 *                        M13, M14).  La Activity implementa este callback abriendo
 *                        el diálogo Java apropiado.  El host hace dismiss del estado
 *                        Vico inmediatamente tras la llamada.
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

        // M6, M7, M8, M10, M13, M14 — Delegación en código Java de la Activity.
        // LaunchedEffect garantiza que la llamada al delegado ocurre una sola vez
        // por cambio de estado (no se re-ejecuta en cada recomposición).
        is VicoMenuState.BlueSensor,
        is VicoMenuState.Meal,
        is VicoMenuState.History,
        is VicoMenuState.NumberEntry,
        is VicoMenuState.SensorInfo,
        is VicoMenuState.Iob -> {
            val capturedState = menuState
            LaunchedEffect(capturedState) {
                onJavaDelegate(capturedState)
                viewModel.dismiss()
            }
        }

        is VicoMenuState.None -> { /* Sin diálogo activo */ }
    }
}
