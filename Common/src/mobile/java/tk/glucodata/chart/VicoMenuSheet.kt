/*
 * Vico path — menú principal en Compose.
 *
 * Reemplaza el sistema de overlays View (VicoMenus.java + menus.xml) con una
 * implementación 100% Compose que:
 *   - Respeta automáticamente los WindowInsets (EdgeToEdge, notch, nav bar).
 *   - Funciona correctamente en portrait y landscape sin layouts XML separados.
 *   - No depende de ningún fichero de recursos XML de menús del viejo UI.
 *
 * Integración:
 *   GlucoseChartScreen muestra el menú directamente como estado Compose:
 *   cuando menuVisible=true se superpone VicoMenuSheet sobre el chart.
 *   El estado se gestiona en GlucoseChartScreen con un MutableState<Boolean>.
 */
package tk.glucodata.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// Modelo de datos — acciones del menú
// ─────────────────────────────────────────────────────────────────────────────

/** Acción ejecutable desde el menú (interfaz Java-friendly). */
fun interface VicoMenuAction {
    fun run()
}

/**
 * Conjunto completo de acciones y estados del menú Vico.
 * Se construye en Java (MainActivity / VicoMenusJava) y se pasa como dato
 * inmutable al composable — sin referencias directas a Activity desde Compose.
 */
data class VicoMenuActions(
    // ── Acciones ──────────────────────────────────────────────────────────────
    val onWatch:      VicoMenuAction,
    val onSensor:     VicoMenuAction,
    val onSettings:   VicoMenuAction,
    val onAbout:      VicoMenuAction,
    val onExport:     VicoMenuAction,
    val onMirror:     VicoMenuAction,
    val onClose:      VicoMenuAction,
    // ── Datos ─────────────────────────────────────────────────────────────────
    val onNewAmount:  VicoMenuAction,
    val onList:       VicoMenuAction,
    val onStatistics: VicoMenuAction,
    val onTalk:       VicoMenuAction,
    val onLastScan:   VicoMenuAction,
    val onNow:        VicoMenuAction,
    val onSearch:     VicoMenuAction,
    val onDate:       VicoMenuAction,
    val onDayBack:    VicoMenuAction,
    val onDayLater:   VicoMenuAction,
    val onWeekBack:   VicoMenuAction,
    val onWeekLater:  VicoMenuAction,
    // ── Ver — estado inicial de los toggles ──────────────────────────────────
    val systemUi:                Boolean,
    val onSystemUiChange:        (Boolean) -> Unit,
    val scans:                   Boolean,
    val onScansChange:           (Boolean) -> Unit,
    val calibratedScans:         Boolean,
    val onCalibratedScansChange: (Boolean) -> Unit,
    val stream:                  Boolean,
    val onStreamChange:          (Boolean) -> Unit,
    val calibratedStream:        Boolean,
    val onCalibratedStreamChange:(Boolean) -> Unit,
    val history:                 Boolean,
    val onHistoryChange:         (Boolean) -> Unit,
    val calibratedHistory:       Boolean,
    val onCalibratedHistoryChange:(Boolean) -> Unit,
    val amounts:                 Boolean,
    val onAmountsChange:         (Boolean) -> Unit,
    val meals:                   Boolean,
    val onMealsChange:           (Boolean) -> Unit,
    val darkMode:                Boolean,
    val onDarkModeChange:        (Boolean) -> Unit,
    val glucoseFloat:            Boolean,
    val onGlucoseFloatChange:    (Boolean) -> Unit,
)

// ─────────────────────────────────────────────────────────────────────────────
// Composable principal
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Menú principal del chart Vico.
 *
 * Se muestra como un sheet que sube desde abajo con fondo semitransparente.
 * Tres pestañas: Acciones / Datos / Ver.
 * Se descarta pulsando fuera del sheet o con el botón atrás del sistema.
 *
 * @param initialTab  0=Acciones, 1=Datos, 2=Ver.
 * @param actions     Acciones y estados del menú.
 * @param onDismiss   Llamado cuando el usuario cierra el menú.
 */
@Composable
fun VicoMenuSheet(
    initialTab: Int = 0,
    actions: VicoMenuActions,
    onDismiss: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    val tabs = listOf("Acciones", "Datos", "Ver")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            // Pulsar fuera del sheet cierra el menú
            .clickable(onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // Absorbe clicks dentro del sheet para que no lo cierren
                .clickable(enabled = false) {}
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                )
                .statusBarsPadding()   // evita notch si el sheet sube hasta arriba
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // ── Asa visual ────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        RoundedCornerShape(2.dp),
                    ),
            )

            // ── Pestañas ──────────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = MaterialTheme.colorScheme.surface,
                contentColor     = MaterialTheme.colorScheme.primary,
            ) {
                tabs.forEachIndexed { i, label ->
                    Tab(
                        selected = selectedTab == i,
                        onClick  = { selectedTab = i },
                        text     = {
                            Text(
                                label,
                                fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal,
                                fontSize   = 14.sp,
                            )
                        },
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp)

            // ── Contenido ─────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                when (selectedTab) {
                    0 -> AccionesTab(actions, onDismiss)
                    1 -> DatosTab(actions, onDismiss)
                    2 -> VerTab(actions, onDismiss)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pestaña 0: Acciones
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AccionesTab(a: VicoMenuActions, onDismiss: () -> Unit) {
    TwoColumnGrid(listOf(
        "Relojes"    to { onDismiss(); a.onWatch.run() },
        "Sensor"     to { onDismiss(); a.onSensor.run() },
        "Ajustes"    to { onDismiss(); a.onSettings.run() },
        "Acerca de"  to { onDismiss(); a.onAbout.run() },
        "Exportar"   to { onDismiss(); a.onExport.run() },
        "Clon"       to { onDismiss(); a.onMirror.run() },
        "Cerrar app" to { onDismiss(); a.onClose.run() },
    ))
}

// ─────────────────────────────────────────────────────────────────────────────
// Pestaña 1: Datos
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DatosTab(a: VicoMenuActions, onDismiss: () -> Unit) {
    Column {
        TwoColumnGrid(listOf(
            "Nueva cantidad" to { onDismiss(); a.onNewAmount.run() },
            "Lista"          to { onDismiss(); a.onList.run() },
            "Estadísticas"   to { onDismiss(); a.onStatistics.run() },
            "Hablar"         to { onDismiss(); a.onTalk.run() },
            "Último escaneo" to { onDismiss(); a.onLastScan.run() },
            "Ahora"          to { onDismiss(); a.onNow.run() },
            "Buscar"         to { onDismiss(); a.onSearch.run() },
            "Fecha"          to { onDismiss(); a.onDate.run() },
        ))
        Spacer(Modifier.height(8.dp))
        SectionLabel("Navegar por fechas")
        TwoColumnGrid(listOf(
            "◀ Día atrás"       to { onDismiss(); a.onDayBack.run() },
            "Día adelante ▶"    to { onDismiss(); a.onDayLater.run() },
            "◀ Semana atrás"    to { onDismiss(); a.onWeekBack.run() },
            "Semana adelante ▶" to { onDismiss(); a.onWeekLater.run() },
        ))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pestaña 2: Ver
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VerTab(a: VicoMenuActions, onDismiss: () -> Unit) {
    // Los checkboxes de Ver mantienen estado local para que el toggle sea
    // inmediato visualmente. El cambio real se propaga vía los callbacks.
    var systemUi            by remember { mutableStateOf(a.systemUi) }
    var scans               by remember { mutableStateOf(a.scans) }
    var calibratedScans     by remember { mutableStateOf(a.calibratedScans) }
    var stream              by remember { mutableStateOf(a.stream) }
    var calibratedStream    by remember { mutableStateOf(a.calibratedStream) }
    var history             by remember { mutableStateOf(a.history) }
    var calibratedHistory   by remember { mutableStateOf(a.calibratedHistory) }
    var amounts             by remember { mutableStateOf(a.amounts) }
    var meals               by remember { mutableStateOf(a.meals) }
    var darkMode            by remember { mutableStateOf(a.darkMode) }
    var glucoseFloat        by remember { mutableStateOf(a.glucoseFloat) }

    Column {
        MenuCheckbox("Interfaz del sistema", systemUi) {
            systemUi = it; a.onSystemUiChange(it)
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)
        SectionLabel("Datos visibles")
        MenuCheckbox("Escaneos",             scans)           { scans = it;             a.onScansChange(it) }
        MenuCheckbox("Escaneos calibrados",  calibratedScans) { calibratedScans = it;   a.onCalibratedScansChange(it) }
        MenuCheckbox("Stream",               stream)          { stream = it;            a.onStreamChange(it) }
        MenuCheckbox("Stream calibrado",     calibratedStream){ calibratedStream = it;  a.onCalibratedStreamChange(it) }
        MenuCheckbox("Historial",            history)         { history = it;           a.onHistoryChange(it) }
        MenuCheckbox("Historial calibrado",  calibratedHistory){ calibratedHistory = it; a.onCalibratedHistoryChange(it) }
        MenuCheckbox("Cantidades",           amounts)         { amounts = it;           a.onAmountsChange(it) }
        MenuCheckbox("Comidas",              meals)           { meals = it;             a.onMealsChange(it) }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)
        SectionLabel("Pantalla")
        MenuCheckbox("Modo oscuro",      darkMode)    { darkMode = it;    a.onDarkModeChange(it) }
        MenuCheckbox("Glucosa flotante", glucoseFloat){ glucoseFloat = it; a.onGlucoseFloatChange(it) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Primitivos de UI reutilizables
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Grid de 2 columnas usando LazyVerticalGrid.
 * Soporta número impar de ítems: el último item ocupa su celda normal (media fila),
 * sin desbordarse ni romper el layout.
 * La altura se limita a un máximo para que el scroll exterior funcione correctamente.
 */
@Composable
private fun TwoColumnGrid(items: List<Pair<String, () -> Unit>>) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 600.dp),
        userScrollEnabled = false, // el scroll lo gestiona el Column padre
    ) {
        items(items) { (label, onClick) ->
            MenuButton(label = label, onClick = onClick)
        }
    }
}

@Composable
private fun MenuButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth().padding(4.dp),
        shape    = RoundedCornerShape(10.dp),
    ) {
        Text(label, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MenuCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.labelSmall,
        color    = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
    )
}
