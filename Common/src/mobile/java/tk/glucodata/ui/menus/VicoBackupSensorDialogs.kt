/*
 * Fase 8 — Menús Compose nativos: Backup, Sensor BLE, History.
 *
 * Contiene los diálogos Compose que reemplazan los siguientes overlays Java:
 *   - Backup.mkbackupview()   → VicoBackupDialog
 *   - bluediag.start()        → VicoSensorDialog
 *   - getnumcontrol()         → VicoHistoryDialog
 *
 * Reglas:
 *   - Ninguna clase NanoVG importa este fichero.
 *   - Los diálogos Java originales permanecen intactos para el path NanoVG.
 */
package tk.glucodata.ui.menus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import tk.glucodata.Natives
import tk.glucodata.chart.NativeGraphDataRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// VicoBackupDialog — Clon / sincronización de red
// Réplica Compose de Backup.mkbackupview()
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Diálogo de gestión de hosts de sincronización (clon/backup).
 *
 * Muestra la lista de hosts configurados y permite:
 *   - Ver estado de cada host (mirrorStatus)
 *   - Crear QR automático (Home/ICE sender/receiver)
 *   - Eliminar un host
 *   - Añadir nuevo host vía subdiálogo
 */
@Composable
fun VicoBackupDialog(onDismiss: () -> Unit) {
    val hostCount = remember { Natives.backuphostNr() }
    // Índice del host seleccionado para edición/eliminación (-1 = ninguno)
    var selectedHost by remember { mutableIntStateOf(-1) }
    var showAddHost  by remember { mutableStateOf(false) }
    var showQrMenu   by remember { mutableStateOf(false) }

    if (showAddHost) {
        VicoBackupHostEditDialog(
            hostIndex = selectedHost,
            onDismiss = { showAddHost = false },
        )
        return
    }

    if (showQrMenu) {
        VicoBackupQrMenu(
            onDismiss = { showQrMenu = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Clon — Sincronización", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .heightIn(max = 400.dp),
            ) {
                if (hostCount == 0) {
                    Text(
                        "No hay hosts configurados.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(hostCount) { i ->
                            val label  = remember { Natives.getbackuplabel(i) ?: "Host ${i + 1}" }
                            val ips    = remember { Natives.getbackupIPs(i)   ?: emptyArray() }
                            val port   = remember { Natives.getbackuphostport(i) ?: "" }
                            val status = remember { Natives.mirrorStatus(i) ?: "" }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                ),
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            label,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(onClick = {
                                            selectedHost = i
                                            showAddHost  = true
                                        }) { Text("Editar") }
                                    }
                                    val ipStr = ips.filterNotNull().filter { it.isNotBlank() }.joinToString(" / ")
                                    if (ipStr.isNotBlank())
                                        Text("IP: $ipStr  Puerto: $port",
                                            style = MaterialTheme.typography.bodySmall)
                                    if (status.isNotBlank())
                                        Text(status,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    OutlinedButton(
                        onClick = { selectedHost = -1; showAddHost = true },
                        modifier = Modifier.weight(1f),
                    ) { Text("Añadir host") }
                    OutlinedButton(
                        onClick = { showQrMenu = true },
                        modifier = Modifier.weight(1f),
                    ) { Text("QR automático") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-diálogos de Backup
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Diálogo de edición de un host de backup.
 * Cuando hostIndex == -1 se crea un host nuevo.
 */
@Composable
private fun VicoBackupHostEditDialog(
    hostIndex: Int,
    onDismiss: () -> Unit,
) {
    val isNew = hostIndex < 0
    val existingIps  = if (!isNew) Natives.getbackupIPs(hostIndex) ?: emptyArray() else emptyArray()
    val existingPort = if (!isNew) Natives.getbackuphostport(hostIndex) ?: "" else ""
    val existingLabel= if (!isNew) Natives.getbackuplabel(hostIndex)    ?: "" else ""
    val existingPass = if (!isNew) Natives.getbackuppassword(hostIndex) ?: "" else ""
    val existingNums = if (!isNew) Natives.getbackuphostnums(hostIndex)   else false
    val existingStr  = if (!isNew) Natives.getbackuphoststream(hostIndex) else false
    val existingScans= if (!isNew) Natives.getbackuphostscans(hostIndex)  else false
    val existingRecv = if (!isNew) Natives.getbackuphostreceive(hostIndex) else 0

    var ip1     by remember { mutableStateOf(existingIps.getOrNull(0) ?: "") }
    var ip2     by remember { mutableStateOf(existingIps.getOrNull(1) ?: "") }
    var port    by remember { mutableStateOf(existingPort) }
    var label   by remember { mutableStateOf(existingLabel) }
    var pass    by remember { mutableStateOf(existingPass) }
    var nums    by remember { mutableStateOf(existingNums) }
    var stream  by remember { mutableStateOf(existingStr) }
    var scans   by remember { mutableStateOf(existingScans) }
    var receive by remember { mutableStateOf(existingRecv != 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(if (isNew) "Nuevo host" else "Editar host", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(value = label,  onValueChange = { label = it },
                    label = { Text("Etiqueta") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = ip1,    onValueChange = { ip1 = it },
                    label = { Text("IP 1") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = ip2,    onValueChange = { ip2 = it },
                    label = { Text("IP 2 (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = port,   onValueChange = { port = it },
                    label = { Text("Puerto") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = pass,   onValueChange = { pass = it },
                    label = { Text("Contraseña") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth())
                HorizontalDivider()
                VicoLabeledSwitch("Enviar cantidades",  nums,    { nums = it })
                VicoLabeledSwitch("Enviar stream",      stream,  { stream = it })
                VicoLabeledSwitch("Enviar escaneos",    scans,   { scans = it })
                VicoLabeledSwitch("Recibir datos",      receive, { receive = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val names = arrayOf(ip1, ip2, "", "")
                Natives.changebackuphost(
                    hostIndex, names, 2, false, port,
                    nums, stream, scans, false,
                    receive, false, false, pass,
                    0L, label, false, false, "", false,
                )
                onDismiss()
            }) { Text(if (isNew) "Crear" else "Guardar") }
        },
        dismissButton = {
            if (!isNew) {
                TextButton(onClick = {
                    Natives.deletebackuphost(hostIndex)
                    onDismiss()
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

/**
 * Menú QR automático: Home/ICE sender/receiver.
 */
@Composable
private fun VicoBackupQrMenu(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("QR automático", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(0.88f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Enviar desde", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { Natives.makeHomeSender(); onDismiss() },
                        modifier = Modifier.weight(1f)) { Text("Red local") }
                    Button(onClick = { Natives.makeICESender(); onDismiss() },
                        modifier = Modifier.weight(1f)) { Text("Internet") }
                }
                Text("Recibir en", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { Natives.makeHomeReceiver(); onDismiss() },
                        modifier = Modifier.weight(1f)) { Text("Red local") }
                    Button(onClick = { Natives.makeICEReceiver(); onDismiss() },
                        modifier = Modifier.weight(1f)) { Text("Internet") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// VicoSensorDialog — Información del sensor BLE activo
// Reemplaza bluediag.start() en el path Vico
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Diálogo de información y control del sensor Bluetooth activo.
 *
 * Muestra número de serie, dirección, estado de conexión y opciones básicas.
 * Usa SensorBluetooth.mygatts() para obtener la lista de sensores activos.
 */
@Composable
fun VicoSensorDialog(onDismiss: () -> Unit) {
    // Obtener la lista de sensores BLE activos via reflexión (sin acoplar Compose a Java BLE)
    val gatts = remember {
        try {
            val cls = Class.forName("tk.glucodata.SensorBluetooth")
            val m   = cls.getMethod("mygatts")
            @Suppress("UNCHECKED_CAST")
            (m.invoke(null) as? java.util.ArrayList<*>) ?: emptyList<Any>()
        } catch (_: Throwable) { emptyList<Any>() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Sensor Bluetooth", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .heightIn(max = 420.dp),
            ) {
                if (gatts.isEmpty()) {
                    Text("No hay sensores BLE activos.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(gatts) { gatt ->
                            VicoGattCard(gatt)
                        }
                    }
                }
                HorizontalDivider()
                // Botón de inicio de escaneo BLE
                Row(modifier = Modifier.padding(top = 6.dp)) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val cls = Class.forName("tk.glucodata.SensorBluetooth")
                                cls.getMethod("startscan").invoke(null)
                            } catch (_: Throwable) {}
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Buscar sensores BLE") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}

@Composable
private fun VicoGattCard(gatt: Any) {
    val serial  = remember { safeField(gatt, "SerialNumber") as? String ?: "(desconocido)" }
    val address = remember { safeField(gatt, "mActiveDeviceAddress") as? String ?: "" }
    val status  = remember {
        try {
            val m = gatt.javaClass.getMethod("constatstatusstr")
            m.invoke(gatt) as? String ?: ""
        } catch (_: Throwable) { "" }
    }
    val streaming = remember {
        try {
            val m = gatt.javaClass.getMethod("streamingEnabled")
            m.invoke(gatt) as? Boolean ?: false
        } catch (_: Throwable) { false }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(serial, fontWeight = FontWeight.Medium)
            if (address.isNotBlank())
                Text("Dirección: $address", style = MaterialTheme.typography.bodySmall)
            if (status.isNotBlank())
                Text("Estado: $status", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Streaming: ${if (streaming) "Activo" else "Inactivo"}",
                style = MaterialTheme.typography.bodySmall,
                color = if (streaming) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                OutlinedButton(
                    onClick = {
                        try { gatt.javaClass.getMethod("finishSensor").invoke(gatt) } catch (_: Throwable) {}
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Finalizar") }
                OutlinedButton(
                    onClick = {
                        try {
                            gatt.javaClass.getMethod("searchforDeviceAddress").invoke(gatt)
                            gatt.javaClass.getMethod("close").invoke(gatt)
                        } catch (_: Throwable) {}
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Olvidar") }
            }
        }
    }
}

private fun safeField(obj: Any, fieldName: String): Any? = try {
    val f = obj.javaClass.getField(fieldName)
    f.get(obj)
} catch (_: Throwable) { null }

// ─────────────────────────────────────────────────────────────────────────────
// VicoHistoryDialog — Historial / lista de datos
// Reemplaza getnumcontrol() en el path Vico
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Diálogo de historial de lecturas de glucosa.
 *
 * Llama a Natives.makenumbers() para generar la lista y muestra las entradas
 * más recientes. La lista completa está disponible a través del gráfico;
 * aquí mostramos las últimas 200 entradas como resumen rápido.
 */
@Composable
fun VicoHistoryDialog(onDismiss: () -> Unit) {
    // Disparar la carga de datos al abrir el diálogo
    LaunchedEffect(Unit) {
        Natives.makenumbers()
        NativeGraphDataRepository.notifyDataChanged()
    }

    // Obtener los datos de historial via reflexión (GlucoseEntry list desde NativeGraphDataRepository)
    val entries = remember {
        try {
            val cls = Class.forName("tk.glucodata.chart.NativeGraphDataRepository")
            val m   = cls.getMethod("getLastEntries", Int::class.java)
            @Suppress("UNCHECKED_CAST")
            (m.invoke(null, 200) as? List<*>) ?: emptyList<Any>()
        } catch (_: Throwable) {
            emptyList<Any>()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Historial de lecturas", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .heightIn(max = 450.dp),
            ) {
                if (entries.isEmpty()) {
                    Text("No hay datos disponibles.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    // Cabecera
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        Text("Fecha / Hora", modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold)
                        Text("Glucosa", modifier = Modifier.width(72.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider()
                    val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(entries) { entry ->
                            val tsMs = try {
                                val m = entry!!.javaClass.getMethod("getTimestampMs")
                                (m.invoke(entry) as? Long) ?: 0L
                            } catch (_: Throwable) { 0L }
                            val glVal = try {
                                val m = entry!!.javaClass.getMethod("getGlucoseMgDl")
                                (m.invoke(entry) as? Float) ?: 0f
                            } catch (_: Throwable) { 0f }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    if (tsMs > 0L) fmt.format(Date(tsMs)) else "--",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    if (glVal > 0f) "%.1f".format(glVal) else "--",
                                    modifier = Modifier.width(72.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}
