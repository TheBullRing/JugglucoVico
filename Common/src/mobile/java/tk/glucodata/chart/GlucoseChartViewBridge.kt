/*
 * Phase 3 — Vico Integration
 *
 * Java-callable bridge that sets up the ComposeView for MainActivity.
 * This file exists because @Composable functions cannot be called directly from Java;
 * all Compose interop must go through this Kotlin file.
 */
package tk.glucodata.chart

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelProvider

/**
 * Creates and returns a [ComposeView] hosting [GlucoseChartScreen].
 * Designed to be called from Java (MainActivity.startdisplay).
 *
 * @param activity    The host Activity — used to scope the ViewModel.
 * @param onMenuTab   Called when the user selects one of the 3 popup menu options.
 *                    Receives the tab index: 0=Acciones, 1=Datos, 2=Ver.
 * @param onLongPress Called back (on the main thread) when the user long-presses a data point.
 *                    Receives the closest [GlucoseEntry].
 */
@JvmOverloads
fun createGlucoseChartView(
    activity:     ComponentActivity,
    onMenuTab:    ((Int) -> Unit) = {},
    onLongPress:  ((GlucoseEntry) -> Unit) = {},
): ComposeView {
    val viewModel = ViewModelProvider(
        activity,
        GlucoseChartViewModel.Factory(NativeGraphDataRepository.instance),
    )[GlucoseChartViewModel::class.java]

    return ComposeView(activity).apply {
        setContent {
            GlucoseChartScreen(
                viewModel   = viewModel,
                onMenuTab   = onMenuTab,
                onLongPress = onLongPress,
            )
        }
    }
}
