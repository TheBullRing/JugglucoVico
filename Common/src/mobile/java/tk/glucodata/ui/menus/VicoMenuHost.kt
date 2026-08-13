/*
 * Fase 8 — Menús Compose nativos para el path Vico.
 *
 * VicoMenuHost: composable que observa el VicoMenuState actual y
 * despacha al diálogo/pantalla correspondiente.
 */
package tk.glucodata.ui.menus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun VicoMenuHost(viewModel: VicoMenuViewModel) {
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

        // Los estados siguientes delegan en código Java existente desde MainActivity.
        is VicoMenuState.BlueSensor  -> { /* M6  — bluediag.start(act) */ }
        is VicoMenuState.Meal        -> { /* M13 — diálogo Java comidas */ }
        is VicoMenuState.History     -> { /* M8  — diálogo Java historial */ }
        is VicoMenuState.NumberEntry -> { /* M7  — numberview.addnumberview() */ }
        is VicoMenuState.SensorInfo  -> { /* M10 — showsensorinfo() */ }
        is VicoMenuState.None        -> { /* Sin diálogo activo */ }
    }
}
