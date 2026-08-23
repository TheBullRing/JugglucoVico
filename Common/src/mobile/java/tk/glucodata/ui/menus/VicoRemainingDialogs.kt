/*
 * Fase 8 — Menús Compose nativos pendientes.
 *
 * Contiene los diálogos Compose que reemplazan los siguientes overlays Java:
 *   - IOB.mkview()             → VicoIobDialog
 *   - Talker.config()          → VicoTalkDialog
 *   - curveRef.startdatepick() → VicoDatePickDialog
 *   - curveRef.startsearch()   → VicoSearchDialog
 *   - Natives.showlastscan()   → VicoLastScanDialog
 *   - Dialogs.showexport()     → VicoExportDialog
 *
 * Reglas:
 *   - Ninguna clase NanoVG importa este fichero.
 *   - Los diálogos Java originales permanecen intactos para el path NanoVG.
 */
package tk.glucodata.ui.menus

import android.media.AudioAttributes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import tk.glucodata.Natives
import tk.glucodata.chart.NativeGraphDataRepository
import java.util.Calendar

// ─────────────────────────────────────────────────────────────────────────────
// VicoIobDialog — M14: Insulina activa (IOB)
// Réplica Compose de IOB.mkview()
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Diálogo de gestión de insulina activa (IOB).
 *
 * Muestra la lista de tipos de insulina con sus dosis actuales
 * (Natives.getLabels / getInsulinType / setInsulinType) y el toggle IOB.
 */
@Composable
fun VicoIobDialog(onDismiss: () -> Unit) {
    val labels = remember { Natives.getLabels() ?: emptyList<String>() }
    // Los tipos de insulina van de 0 a labels.size-2 (último ítem reservado)
    val typeCount = (labels.size - 1).coerceAtLeast(0)
    var iobEnabled by remember { mutableStateOf(Natives.getIOB()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("IOB — Insulina activa", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Toggle IOB
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Activar IOB",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = iobEnabled,
                        onCheckedChange = { checked ->
                            if (Natives.setIOB(checked)) {
                                iobEnabled = checked
                            }
                        },
                    )
                }
                HorizontalDivider()
                // Lista de tipos de insulina en grid 3 columnas
                if (typeCount > 0) {
                    Text(
                        "Tipos de insulina",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val rows = (typeCount + 2) / 3
                    repeat(rows) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            for (col in 0..2) {
                                val idx = row * 3 + col
                                if (idx < typeCount) {
                                    val labelName = labels.getOrElse(idx) { "" }
                                    var typeVal by remember(idx) {
                                        mutableStateOf(Natives.getInsulinType(idx).toString())
                                    }
                                    OutlinedTextField(
                                        value = typeVal,
                                        onValueChange = { v ->
                                            typeVal = v
                                            v.toIntOrNull()?.let { Natives.setInsulinType(idx, it) }
                                        },
                                        label = { Text(labelName, maxLines = 1) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                    )
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Aceptar") }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// VicoTalkDialog — Configuración de TTS
// Réplica Compose de Talker.config()
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Diálogo de configuración de voz (TTS).
 *
 * Controla: activo, touchtalk, speakmessages, speakalarms, velocidad, pitch,
 * separación (segundos), tipo de audio (MEDIA o NOTIFICATION).
 */
@Composable
fun VicoTalkDialog(onDismiss: () -> Unit) {
    var active        by remember { mutableStateOf(Natives.getVoiceActive()) }
    var touchTalk     by remember { mutableStateOf(Natives.gettouchtalk()) }
    var speakMsgs     by remember { mutableStateOf(Natives.speakmessages()) }
    var speakAlarms   by remember { mutableStateOf(Natives.speakalarms()) }
    var speed         by remember { mutableStateOf(Natives.getVoiceSpeed().toString()) }
    var pitch         by remember { mutableStateOf(Natives.getVoicePitch().toString()) }
    var separation    by remember { mutableStateOf(Natives.getVoiceSeparation().toString()) }
    val isMedia = Natives.getSoundType() == AudioAttributes.USAGE_MEDIA
    var mediaSound    by remember { mutableStateOf(isMedia) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Configuración de voz", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VicoLabeledSwitch("Hablar glucosa",            active,      { active = it })
                VicoLabeledSwitch("Hablar al tocar",           touchTalk,   { touchTalk = it })
                VicoLabeledSwitch("Hablar mensajes",           speakMsgs,   { speakMsgs = it })
                VicoLabeledSwitch("Hablar alarmas",            speakAlarms, { speakAlarms = it })
                VicoLabeledSwitch("Sonido multimedia (MEDIA)", mediaSound,  { v ->
                    mediaSound = v
                    Natives.setSoundType(
                        if (v) AudioAttributes.USAGE_MEDIA
                        else AudioAttributes.USAGE_NOTIFICATION
                    )
                })
                HorizontalDivider()
                Text("Parámetros", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = speed,
                    onValueChange = { speed = it },
                    label = { Text("Velocidad (ej. 1.00)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pitch,
                    onValueChange = { pitch = it },
                    label = { Text("Tono (ej. 1.00)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = separation,
                    onValueChange = { separation = it },
                    label = { Text("Segundos entre lecturas") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val spd = speed.toFloatOrNull()    ?: Natives.getVoiceSpeed()
                val pit = pitch.toFloatOrNull()    ?: Natives.getVoicePitch()
                val sep = separation.toIntOrNull() ?: Natives.getVoiceSeparation()
                val pos = Natives.getVoiceTalker()
                Natives.saveVoice(spd, pit, sep, pos, active)
                tk.glucodata.SuperGattCallback.dotalk = active
                Natives.settouchtalk(touchTalk)
                Natives.setspeakmessages(speakMsgs)
                Natives.setspeakalarms(speakAlarms)
                onDismiss()
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// VicoDatePickDialog — Selector de fecha
// Réplica Compose de curveRef.startdatepick() + Natives.movedate()
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Diálogo Material3 de selección de fecha para navegar en el gráfico.
 * Llama a Natives.movedate() y refresca el repositorio al confirmar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VicoDatePickDialog(onDismiss: () -> Unit) {
    val startTimeSec = remember { Natives.getstarttime() }
    val startMs = startTimeSec * 1000L
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = startMs)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val selMs = pickerState.selectedDateMillis
                if (selMs != null) {
                    val cal = Calendar.getInstance().also { it.timeInMillis = selMs }
                    Natives.movedate(
                        startTimeSec,
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH) + 1,
                        cal.get(Calendar.DAY_OF_MONTH),
                    )
                    NativeGraphDataRepository.notifyDataChanged()
                }
                onDismiss()
            }) { Text("Ir") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// VicoSearchDialog — Búsqueda en el gráfico
// Réplica Compose de curveRef.startsearch() + Natives.search()
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Diálogo de búsqueda en el gráfico.
 * El usuario elige etiqueta, rango de glucosa y dirección;
 * llama a Natives.search() para posicionar el cursor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VicoSearchDialog(onDismiss: () -> Unit) {
    val labels = remember { Natives.getLabels() ?: listOf("Sin etiqueta") }
    var selectedLabel  by remember { mutableIntStateOf(0) }
    var valueUnder     by remember { mutableStateOf("0") }
    var valueAbove     by remember { mutableStateOf("400") }
    var forward        by remember { mutableStateOf(true) }
    var labelExpanded  by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Buscar", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Etiqueta", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
                ExposedDropdownMenuBox(
                    expanded = labelExpanded,
                    onExpandedChange = { labelExpanded = !labelExpanded },
                ) {
                    OutlinedTextField(
                        value = labels.getOrElse(selectedLabel) { "Todos" },
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(labelExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = labelExpanded,
                        onDismissRequest = { labelExpanded = false },
                    ) {
                        labels.forEachIndexed { i, lbl ->
                            DropdownMenuItem(
                                text = { Text(lbl) },
                                onClick = { selectedLabel = i; labelExpanded = false },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = valueUnder,
                        onValueChange = { valueUnder = it },
                        label = { Text("Min mg/dL") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = valueAbove,
                        onValueChange = { valueAbove = it },
                        label = { Text("Max mg/dL") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hacia adelante", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = forward, onCheckedChange = { forward = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val under = valueUnder.toFloatOrNull() ?: 0f
                val above = valueAbove.toFloatOrNull() ?: 400f
                Natives.search(selectedLabel, under, above, 0, Int.MAX_VALUE, forward, "", 0f)
                NativeGraphDataRepository.notifyDataChanged()
                onDismiss()
            }) { Text("Buscar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// VicoLastScanDialog — Resultado del último escaneo NFC
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Diálogo que muestra el resultado del último escaneo NFC.
 * Llama a Natives.showlastscan() y refresca el repositorio.
 */
@Composable
fun VicoLastScanDialog(onDismiss: () -> Unit) {
    var result by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        result = Natives.showlastscan()
        NativeGraphDataRepository.notifyDataChanged()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Último escaneo", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(0.88f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (result) {
                    null  -> CircularProgressIndicator()
                    true  -> {
                        val gl = Natives.lastglucose()
                        Text(
                            if (gl?.value != null) "\u2713  ${gl.value}" else "\u2713  Escaneo recibido",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    false -> Text(
                        "No hay escaneo disponible",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Aceptar") }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// VicoExportDialog — Exportar datos con ACTION_CREATE_DOCUMENT
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Composable que lanza el selector de fichero del sistema para exportar datos.
 * No muestra UI propia — abre directamente ACTION_CREATE_DOCUMENT.
 */
@Composable
fun VicoExportDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        if (uri != null) {
            try {
                val clazz  = Class.forName("tk.glucodata.Dialogs")
                val method = clazz.getMethod(
                    "exportToUri",
                    android.content.Context::class.java,
                    android.net.Uri::class.java,
                )
                method.invoke(null, context, uri)
            } catch (_: Throwable) {
                // exportToUri no disponible en esta versión — ignorar.
            }
            NativeGraphDataRepository.notifyDataChanged()
        }
        onDismiss()
    }

    var launched by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!launched) {
            launched = true
            launcher.launch("juggluco_export")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers internos compartidos
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun VicoLabeledSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
