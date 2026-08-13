/*
 * Fase 8 — Menús Compose nativos para el path Vico.
 *
 * VicoMenuHost: composable que observa el VicoMenuState actual y
 * despacha al diálogo/pantalla correspondiente.
 *
 * Se coloca en el Box raíz de GlucoseChartScreen, DESPUÉS del chart y
 * del VicoMenuSheet, de modo que los diálogos cubren todo el contenido.
 */
package tk.glucodata.ui.menus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Composable raíz del sistema de menús del path Vico.
 *
 * Observa [VicoMenuViewModel.state] y renderiza el diálogo activo.
 * Cuando el estado es [VicoMenuState.None] no renderiza nada.
 *
 * @param viewModel  ViewModel de Activity que gestiona el estado del menú.
 */
@Composable
fun VicoMenuHost(viewModel: VicoMenuViewModel) {
    val menuState by viewModel.state.collectAsStateWithLifecycle()

    when (menuState) {
        is VicoMenuState.Settings ->
            VicoSettingsScreen(
                onDismiss    = { viewModel.dismiss() },
                onNavigateTo = { viewModel.show(it) },
            )

        is VicoMenuState.NightscoutUploader ->
            VicoNightscoutUploaderDialog(onDismiss = { viewModel.dismiss() })

        is VicoMenuState.DataExchange ->
            VicoDataExchangeMenu(
                onNightscoutUploader = { viewModel.show(VicoMenuState.NightscoutUploader) },
                onNightscoutFollower = { viewModel.show(VicoMenuState.NightscoutFollower) },
                onLibreView          = { viewModel.show(VicoMenuState.LibreView) },
                onDismiss            = { viewModel.dismiss() },
            )

        // Los estados siguientes aún no tienen implementación Compose —
        // el host simplemente no muestra nada para esos estados (fallback al
        // código Java en MainActivity si se necesita funcionalidad inmediata).
        is VicoMenuState.NightscoutFollower -> { /* Pendiente M12b */ }
        is VicoMenuState.LibreView          -> { /* Pendiente M12c */ }
        is VicoMenuState.BlueSensor         -> { /* Pendiente M6  */ }
        is VicoMenuState.Calibration        -> { /* Pendiente M3  */ }
        is VicoMenuState.Meal               -> { /* Pendiente M13 */ }
        is VicoMenuState.Stats              -> { /* Pendiente M5  */ }
        is VicoMenuState.History            -> { /* Pendiente M8  */ }
        is VicoMenuState.Alarms             -> { /* Pendiente M11 */ }
        is VicoMenuState.NumberEntry        -> { /* Pendiente M7  */ }
        is VicoMenuState.SensorInfo         -> { /* Pendiente M10 */ }
        is VicoMenuState.None               -> { /* Nada que mostrar */ }
    }
}
