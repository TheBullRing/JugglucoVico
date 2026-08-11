/*
 * Phase 3 — Vico Integration
 *
 * Java-callable bridge that sets up the ComposeView for MainActivity.
 */
package tk.glucodata.chart

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelProvider

/**
 * Creates and returns a [ComposeView] hosting [GlucoseChartScreen].
 * Called from Java (MainActivity.startdisplay).
 *
 * @param activity    The host Activity.
 * @param menuActions All menu actions/states for the native Compose menu.
 * @param onLongPress Called when the user long-presses a data point.
 */
@JvmOverloads
fun createGlucoseChartView(
    activity:    ComponentActivity,
    menuActions: VicoMenuActions? = null,
    // Legacy parameter, ignored when menuActions != null
    onMenuTab:   ((Int) -> Unit) = {},
    onLongPress: ((GlucoseEntry) -> Unit) = {},
): ComposeView {
    val viewModel = ViewModelProvider(
        activity,
        GlucoseChartViewModel.Factory(NativeGraphDataRepository.instance),
    )[GlucoseChartViewModel::class.java]

    return ComposeView(activity).apply {
        setContent {
            GlucoseChartScreen(
                viewModel   = viewModel,
                menuActions = menuActions,
                onMenuTab   = onMenuTab,
                onLongPress = onLongPress,
            )
        }
    }
}
