/*
 * Fase 8 — Menús Compose nativos para el path Vico.
 *
 * VicoMenuHost: composable que observa el VicoMenuState actual y
 * despacha al diálogo/pantalla correspondiente.
 *
 * Todos los estados tienen diálogo Compose nativo.
 */
package tk.glucodata.ui.menus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Composable que observa [VicoMenuViewModel.state] y renderiza el diálogo
 * o pantalla Compose correspondiente.
 *
 * Todos los estados del menú tienen implementación Compose nativa.
 *
 * @param viewModel         ViewModel del menú Vico.
 * @param onJavaDelegate    Mantenido para compatibilidad de firma; ya no se invoca.
 */
@Composable
fun VicoMenuHost(
    viewModel:      VicoMenuViewModel,
    onJavaDelegate: (VicoMenuState) -> Unit = {},
) {
    val menuState by viewModel.state.collectAsStateWithLifecycle()

    when (menuState) {
        // ── M4 — Ajustes ───────────────────────────────────────────────────
        is VicoMenuState.Settings ->
            VicoSettingsScreen(
                onDismiss    = { viewModel.dismiss() },
                onNavigateTo = { viewModel.show(it) },
            )

        // ── M12a — Cargador Nightscout ─────────────────────────────────────
        is VicoMenuState.NightscoutUploader ->
            VicoNightscoutUploaderDialog(onDismiss = { viewModel.dismiss() })

        // ── Sub-menú Intercambiar datos ────────────────────────────────────
        is VicoMenuState.DataExchange ->
            VicoDataExchangeMenu(
                onNightscoutUploader = { viewModel.show(VicoMenuState.NightscoutUploader) },
                onNightscoutFollower = { viewModel.show(VicoMenuState.NightscoutFollower) },
                onLibreView          = { viewModel.show(VicoMenuState.LibreView) },
                onDismiss            = { viewModel.show(VicoMenuState.Settings) },
            )

        // ── M12b — Seguidor Nightscout ─────────────────────────────────────
        is VicoMenuState.NightscoutFollower ->
            VicoNightscoutFollowerDialog(onDismiss = { viewModel.show(VicoMenuState.DataExchange) })

        // ── M12c — LibreView ───────────────────────────────────────────────
        is VicoMenuState.LibreView ->
            VicoLibreViewDialog(onDismiss = { viewModel.show(VicoMenuState.DataExchange) })

        // ── M3 — Calibración ───────────────────────────────────────────────
        is VicoMenuState.Calibration ->
            VicoCalibrationDialog(onDismiss = { viewModel.show(VicoMenuState.Settings) })

        // ── M5 — Estadísticas ──────────────────────────────────────────────
        is VicoMenuState.Stats ->
            VicoStatsDialog(onDismiss = { viewModel.dismiss() })

        // ── M11 — Alarmas ──────────────────────────────────────────────────
        is VicoMenuState.Alarms ->
            VicoAlarmsDialog(onDismiss = { viewModel.show(VicoMenuState.Settings) })

        // ── M6/Watch — Relojes y gadgets (COMPOSE NATIVO) ─────────────────
        is VicoMenuState.Watch ->
            VicoWatchDialog(
                onDismiss         = { viewModel.dismiss() },
                onConfigureWearOS = { viewModel.show(VicoMenuState.Wearos) },
                onConfigureServer = { viewModel.show(VicoMenuState.NightscoutFollower) },
            )

        // ── Watch → WearOS → Config. (COMPOSE NATIVO) ─────────────────────
        is VicoMenuState.Wearos ->
            VicoWearosDialog(onDismiss = { viewModel.show(VicoMenuState.Watch) })

        // ── M7/NumberEntry — Entrada manual de glucosa (COMPOSE NATIVO) ───
        is VicoMenuState.NumberEntry ->
            VicoNumberEntryDialog(onDismiss = { viewModel.dismiss() })

        // ── M13/Meal — Registro de comida (COMPOSE NATIVO) ────────────────
        is VicoMenuState.Meal ->
            VicoMealSimpleDialog(onDismiss = { viewModel.dismiss() })

        // ── About — Información de versión (COMPOSE NATIVO) ───────────────
        is VicoMenuState.About ->
            VicoAboutDialog(onDismiss = { viewModel.dismiss() })

        // ── M14/IOB — Insulina activa (COMPOSE NATIVO) ────────────────────
        is VicoMenuState.Iob ->
            VicoIobDialog(onDismiss = { viewModel.dismiss() })

        // ── Hablar / TTS (COMPOSE NATIVO) ─────────────────────────────────
        is VicoMenuState.Talk ->
            VicoTalkDialog(onDismiss = { viewModel.dismiss() })

        // ── Selector de fecha (COMPOSE NATIVO) ────────────────────────────
        is VicoMenuState.DatePick ->
            VicoDatePickDialog(onDismiss = { viewModel.dismiss() })

        // ── Buscar en el gráfico (COMPOSE NATIVO) ─────────────────────────
        is VicoMenuState.Search ->
            VicoSearchDialog(onDismiss = { viewModel.dismiss() })

        // ── Último escaneo NFC (COMPOSE NATIVO) ───────────────────────────
        is VicoMenuState.LastScan ->
            VicoLastScanDialog(onDismiss = { viewModel.dismiss() })

        // ── Exportar datos (COMPOSE NATIVO — ACTION_CREATE_DOCUMENT) ──────
        is VicoMenuState.Export ->
            VicoExportDialog(onDismiss = { viewModel.dismiss() })

        // ── Clon / Backup (COMPOSE NATIVO) ────────────────────────────────
        is VicoMenuState.Backup ->
            VicoBackupDialog(onDismiss = { viewModel.dismiss() })

        // ── Sensor BLE (COMPOSE NATIVO) ───────────────────────────────────
        is VicoMenuState.Sensor ->
            VicoSensorDialog(onDismiss = { viewModel.dismiss() })

        // ── Historial / Lista (COMPOSE NATIVO) ────────────────────────────
        is VicoMenuState.History ->
            VicoHistoryDialog(onDismiss = { viewModel.dismiss() })

        // ── BlueSensor — mismo diálogo que Sensor ─────────────────────────
        is VicoMenuState.BlueSensor ->
            VicoSensorDialog(onDismiss = { viewModel.dismiss() })

        // ── SensorInfo — mismo diálogo que Sensor ─────────────────────────
        is VicoMenuState.SensorInfo ->
            VicoSensorDialog(onDismiss = { viewModel.dismiss() })

        // ── None — sin diálogo activo ──────────────────────────────────────
        is VicoMenuState.None -> { /* sin diálogo */ }
    }
}
