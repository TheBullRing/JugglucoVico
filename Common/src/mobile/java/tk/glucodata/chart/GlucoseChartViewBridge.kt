/*
 * Phase 3 — Vico Integration
 * Phase 8 — Parallel Compose Menus (VicoMenuViewModel wiring)
 *
 * Java-callable bridge that sets up the ComposeView for MainActivity.
 * Exposes the VicoMenuViewModel so MainActivity can call show() from lambdas.
 */
package tk.glucodata.chart

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelProvider
import tk.glucodata.ui.menus.VicoMenuViewModel

/**
 * Resultado de [createGlucoseChartView]: la vista Compose y el ViewModel
 * de menús que MainActivity necesita para despachar diálogos.
 */
data class GlucoseChartViewResult(
    val composeView:   ComposeView,
    val menuViewModel: VicoMenuViewModel,
)

/**
 * Crea y devuelve un [GlucoseChartViewResult] con la vista Compose y el
 * [VicoMenuViewModel] de Activity.
 * Llamado desde Java (MainActivity.startdisplay).
 *
 * @param activity    La Activity anfitriona.
 * @param menuActions Acciones/estados del menú principal Compose.
 * @param onLongPress Llamado cuando el usuario pulsa prolongadamente un punto.
 */
fun createGlucoseChartView(
    activity:    ComponentActivity,
    menuActions: VicoMenuActions,
    onLongPress: ((GlucoseEntry) -> Unit) = {},
): GlucoseChartViewResult {
    val chartViewModel = ViewModelProvider(
        activity,
        GlucoseChartViewModel.Factory(NativeGraphDataRepository.instance),
    )[GlucoseChartViewModel::class.java]

    val menuViewModel = ViewModelProvider(activity)[VicoMenuViewModel::class.java]

    val view = ComposeView(activity).apply {
        setContent {
            GlucoseChartScreen(
                viewModel     = chartViewModel,
                menuActions   = menuActions,
                menuViewModel = menuViewModel,
                onLongPress   = onLongPress,
            )
        }
    }

    return GlucoseChartViewResult(composeView = view, menuViewModel = menuViewModel)
}
