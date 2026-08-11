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
import androidx.compose.ui.platform.LocalConfiguration
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
    val portraitPivoted:         Boolean,
    val onPortraitPivotedChange: (Boolean) -> Unit,
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
 * @param initialTab   0=Acciones, 1=Datos, 2=Ver.
 * @param actions      Acciones y estados del menú.
 * @param darkModeState Valor reactivo de "modo oscuro" leído directamente del StateFlow de
 *                     settings (ChartSettings.invertColors). Se pasa por separado para evitar
 *                     el problema de congelado que ocurre cuando se envuelve en remember().
 *                     Si null, se usa el valor inicial de actions.darkMode.
 * @param onDismiss    Llamado cuando el usuario cierra el menú.
 */
@Composable
fun VicoMenuSheet(
    initialTab: Int = 0,
    actions: VicoMenuActions,
    darkModeState: Boolean = actions.darkMode,
    onDismiss: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    val tabs = listOf("Acciones", "Datos", "Ver")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss),
    ) {
        // Altura máxima = 72% de la pantalla; se encoge si el contenido es menor.
        val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // El sheet se encoge al contenido pero nunca supera el 72% de pantalla.
                // El truco: el inner Column scrollable usa fillMaxWidth (no fillMaxSize),
                // lo que permite que el outer Column mida su altura real.
                .heightIn(max = screenHeightDp * 0.72f)
                .clickable(enabled = false) {}
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                )
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // ── Asa visual ────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp, bottom = 2.dp)
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

            // ── Contenido scrollable — fillMaxWidth (no fillMaxSize) para que el
            //    outer Column mida la altura real del contenido ──────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                when (selectedTab) {
                    0 -> AccionesTab(actions, onDismiss)
                    1 -> DatosTab(actions, onDismiss)
                    2 -> VerTab(actions, darkModeState, onDismiss)
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
private fun VerTab(a: VicoMenuActions, darkModeState: Boolean, onDismiss: () -> Unit) {
    // Estado local para feedback inmediato; el callback persiste el cambio.
    // Se usa a.xxx como key del remember para que si el valor externo cambia
    // (p.ej. cuando se vuelve a abrir el menú con un estado distinto), el estado
    // local se reinicie correctamente y el toggle refleje el valor actual.
    var systemUi          by remember(a.systemUi)          { mutableStateOf(a.systemUi) }
    var scans             by remember(a.scans)             { mutableStateOf(a.scans) }
    var calibratedScans   by remember(a.calibratedScans)   { mutableStateOf(a.calibratedScans) }
    var stream            by remember(a.stream)            { mutableStateOf(a.stream) }
    var calibratedStream  by remember(a.calibratedStream)  { mutableStateOf(a.calibratedStream) }
    var history           by remember(a.history)           { mutableStateOf(a.history) }
    var calibratedHistory by remember(a.calibratedHistory) { mutableStateOf(a.calibratedHistory) }
    var amounts           by remember(a.amounts)           { mutableStateOf(a.amounts) }
    var meals             by remember(a.meals)             { mutableStateOf(a.meals) }
    // darkMode: se inicializa con darkModeState (valor reactivo del StateFlow) y
    // se usa darkModeState como key del remember para que se reinicie si el valor
    // externo cambia (p.ej. al volver a abrir el menú tras cambiar el tema).
    var darkMode          by remember(darkModeState)       { mutableStateOf(darkModeState) }
    var glucoseFloat      by remember(a.glucoseFloat)      { mutableStateOf(a.glucoseFloat) }

    // Calcular el nuevo valor antes de asignarlo para que el callback reciba
    // siempre el valor correcto, independientemente del scheduler de Compose.
    var portraitPivoted by remember(a.portraitPivoted) { mutableStateOf(a.portraitPivoted) }

    Column {
        SectionLabel("General")
        Row(Modifier.fillMaxWidth()) {
            ToggleButton("Interfaz sistema", systemUi,    Modifier.weight(1f)) { val n = !systemUi;    systemUi = n;          a.onSystemUiChange(n) }
            ToggleButton("Modo oscuro",      darkMode,    Modifier.weight(1f)) { val n = !darkMode;    darkMode = n;           a.onDarkModeChange(n) }
        }
        Row(Modifier.fillMaxWidth()) {
            ToggleButton("Glucosa flotante", glucoseFloat, Modifier.weight(1f)) { val n = !glucoseFloat; glucoseFloat = n;    a.onGlucoseFloatChange(n) }
            Spacer(Modifier.weight(1f))
        }
        SectionLabel("Gráfico portrait")
        Row(Modifier.fillMaxWidth()) {
            ToggleButton(
                label    = if (portraitPivoted) "Pivotado (Y=tiempo)" else "Tradicional (X=tiempo)",
                active   = true,   // siempre activo — es una selección, no on/off
                modifier = Modifier.weight(1f),
            ) {
                val n = !portraitPivoted
                portraitPivoted = n
                a.onPortraitPivotedChange(n)
            }
            Spacer(Modifier.weight(1f))
        }
        SectionLabel("Datos visibles")
        Row(Modifier.fillMaxWidth()) {
            ToggleButton("Escaneos",          scans,           Modifier.weight(1f)) { val n = !scans;           scans = n;           a.onScansChange(n) }
            ToggleButton("Escan. calibrados", calibratedScans, Modifier.weight(1f)) { val n = !calibratedScans; calibratedScans = n; a.onCalibratedScansChange(n) }
        }
        Row(Modifier.fillMaxWidth()) {
            ToggleButton("Stream",           stream,          Modifier.weight(1f)) { val n = !stream;          stream = n;          a.onStreamChange(n) }
            ToggleButton("Stream calibrado", calibratedStream,Modifier.weight(1f)) { val n = !calibratedStream; calibratedStream = n; a.onCalibratedStreamChange(n) }
        }
        Row(Modifier.fillMaxWidth()) {
            ToggleButton("Historial",        history,         Modifier.weight(1f)) { val n = !history;         history = n;         a.onHistoryChange(n) }
            ToggleButton("Hist. calibrado",  calibratedHistory,Modifier.weight(1f)){ val n = !calibratedHistory; calibratedHistory = n; a.onCalibratedHistoryChange(n) }
        }
        Row(Modifier.fillMaxWidth()) {
            ToggleButton("Cantidades",       amounts,         Modifier.weight(1f)) { val n = !amounts;         amounts = n;         a.onAmountsChange(n) }
            ToggleButton("Comidas",          meals,           Modifier.weight(1f)) { val n = !meals;           meals = n;           a.onMealsChange(n) }
        }
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
private fun ToggleButton(label: String, active: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    // Activo: fondo primario sólido (color del tema del usuario).
    // Inactivo: fondo transparente con borde sutil — diferencia visual clara ON vs OFF.
    val shape = RoundedCornerShape(10.dp)
    if (active) {
        Button(
            onClick  = onToggle,
            modifier = modifier.padding(4.dp),
            shape    = shape,
            colors   = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor   = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(label, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        OutlinedButton(
            onClick  = onToggle,
            modifier = modifier.padding(4.dp),
            shape    = shape,
            colors   = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
            ),
            border   = ButtonDefaults.outlinedButtonBorder(enabled = true),
        ) {
            Text(label, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MenuButton(label: String, onClick: () -> Unit) {
    // Use Button (primary/onPrimary) instead of FilledTonalButton (primaryContainer)
    // so the button colour always matches the user's custom Juggluco theme.
    Button(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth().padding(4.dp),
        shape    = RoundedCornerShape(10.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor   = MaterialTheme.colorScheme.onPrimary,
        ),
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
            // Compact padding so the Ver tab matches the visual density of the button grid
            .padding(horizontal = 8.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked         = checked,
            onCheckedChange = onCheckedChange,
            colors          = CheckboxDefaults.colors(
                checkedColor   = MaterialTheme.colorScheme.primary,
                checkmarkColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text       = text,
        style      = MaterialTheme.typography.labelSmall,
        color      = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier   = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 0.dp),
    )
}
