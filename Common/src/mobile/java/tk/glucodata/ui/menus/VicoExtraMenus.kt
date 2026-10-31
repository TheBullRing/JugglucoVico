/*
 * Fase 8 — Menús Compose nativos para el path Vico.
 *
 * VicoExtraMenus: diálogos adicionales del path Vico.
 *
 *  • VicoNightscoutFollowerDialog  — M12b, Seguidor Nightscout
 *  • VicoLibreViewDialog           — M12c, LibreView
 *  • VicoCalibrationDialog         — M3,   Calibración
 *  • VicoStatsDialog               — M5,   Estadísticas
 *  • VicoAlarmsDialog              — M11,  Alarmas
 */
package tk.glucodata.ui.menus

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tk.glucodata.Natives

// ─────────────────────────────────────────────────────────────────────────────
// M12b — Seguidor Nightscout (servidor Xdrip local)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Diálogo "Seguidor Nightscout" — réplica de [tk.glucodata.Nightscout.show()].
 * Gestiona el servidor Xdrip local (SSL port, interval, api secret).
 */
@Composable
fun VicoNightscoutFollowerDialog(onDismiss: () -> Unit) {
    val context      = LocalContext.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    var apiSecret    by remember { mutableStateOf(Natives.getApiSecret() ?: "") }
    var secretVisible by remember { mutableStateOf(false) }
    var sslPort      by remember { mutableStateOf("${Natives.getsslport()}") }
    var interval     by remember { mutableStateOf("${Natives.getinterval()}") }
    var serverActive by remember { mutableStateOf(Natives.getusexdripwebserver()) }
    var localOnly    by remember { mutableStateOf(Natives.getXdripServerLocal()) }
    var useSSL       by remember { mutableStateOf(Natives.getuseSSL()) }
    var treatments   by remember { mutableStateOf(Natives.getGiveAmounts()) }
    val nightError   = remember { Natives.nightError() }

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = screenHeight * 0.88f),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "Seguidor Nightscout",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                OutlinedTextField(
                    value = apiSecret,
                    onValueChange = { apiSecret = it },
                    label = { Text("api_secret") },
                    singleLine = true,
                    visualTransformation = if (secretVisible) VisualTransformation.None
                                          else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = { secretVisible = !secretVisible }) {
                            Text(if (secretVisible) "Ocultar" else "Ver")
                        }
                    },
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = sslPort,
                        onValueChange = { sslPort = it },
                        label = { Text("Puerto SSL") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = interval,
                        onValueChange = { interval = it },
                        label = { Text("Intervalo (min)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }

                if (nightError.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = nightError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Divider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

                FollowerSwitchRow("Servidor activo",       serverActive)  { serverActive = it; Natives.setusexdripwebserver(it) }
                FollowerSwitchRow("Solo red local",        localOnly)     { localOnly = it; Natives.setXdripServerLocal(it) }
                FollowerSwitchRow("Usar SSL",              useSSL)        {
                    val err = Natives.setuseSSL(it)
                    if (err == null) useSSL = it
                    else Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                }
                FollowerSwitchRow("Enviar tratamientos",   treatments)    { treatments = it; Natives.setGiveAmounts(it) }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Puerto HTTP = 17580 (fijo)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Divider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    TextButton(
                        onClick = {
                            val portNum = sslPort.trim().toIntOrNull()
                            val intervalNum = interval.trim().toIntOrNull()
                            when {
                                portNum == null || portNum < 1024 || portNum > 65535 ->
                                    Toast.makeText(context, "Puerto SSL no válido (1024–65535)", Toast.LENGTH_SHORT).show()
                                intervalNum == null ->
                                    Toast.makeText(context, "Intervalo no válido", Toast.LENGTH_SHORT).show()
                                else -> {
                                    val cur = Natives.getApiSecret() ?: ""
                                    if (apiSecret != cur) Natives.setApiSecret(apiSecret)
                                    if (portNum != Natives.getsslport()) Natives.setsslport(portNum)
                                    if (intervalNum != Natives.getinterval()) Natives.setinterval(intervalNum)
                                    onDismiss()
                                }
                            }
                        },
                    ) { Text("Guardar", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun FollowerSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, modifier = Modifier.padding(start = 12.dp),
             style = MaterialTheme.typography.bodyMedium)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// M12c — LibreView
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Diálogo LibreView — réplica de [tk.glucodata.Libreview] config.
 */
@Composable
fun VicoLibreViewDialog(onDismiss: () -> Unit) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    var email        by remember { mutableStateOf(Natives.getlibreemail() ?: "") }
    var password     by remember { mutableStateOf(Natives.getlibrepass() ?: "") }
    var passVisible  by remember { mutableStateOf(false) }
    var useLibreView by remember { mutableStateOf(Natives.getuselibreview()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = screenHeight * 0.88f),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "LibreView",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo electrónico") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    visualTransformation = if (passVisible) VisualTransformation.None
                                          else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = { passVisible = !passVisible }) {
                            Text(if (passVisible) "Ocultar" else "Ver")
                        }
                    },
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Switch(
                        checked = useLibreView,
                        onCheckedChange = { useLibreView = it; Natives.setuselibreview(it) },
                    )
                    Text("Activo", modifier = Modifier.padding(start = 12.dp),
                         style = MaterialTheme.typography.bodyMedium)
                }

                Divider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

                OutlinedButton(
                    onClick = { Natives.wakelibreview(0) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Enviar ahora") }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    TextButton(
                        onClick = {
                            Natives.setlibreemail(email)
                            Natives.setlibrepass(password)
                            onDismiss()
                        },
                    ) { Text("Guardar", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// M3 — Calibración
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Diálogo de calibración — réplica de [tk.glucodata.settings.Calibration.show()].
 */
@Composable
fun VicoCalibrationDialog(onDismiss: () -> Unit) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    var calibrateA  by remember { mutableStateOf(Natives.getCalibrateA()) }
    var doCalibrate by remember { mutableStateOf(Natives.getDoCalibrate()) }
    var calibPast   by remember { mutableStateOf(Natives.getCalibratePast()) }
    var allValues   by remember { mutableStateOf(Natives.getAllValues()) }
    val labels: List<String> = remember { Natives.getLabels()?.toList() ?: emptyList() }
    var bloodVar    by remember { mutableIntStateOf(Natives.getbloodvar().toInt()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = screenHeight * 0.88f),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "Calibración",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                CalibSwitchRow("Calibrar (A)", calibrateA) { calibrateA = it; Natives.setCalibrateA(it) }

                Divider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))

                if (labels.isNotEmpty()) {
                    Text(
                        "Tipo de glucosa capilar",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    labels.forEachIndexed { idx, label ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            RadioButton(
                                selected = bloodVar == idx,
                                onClick = { bloodVar = idx; Natives.setbloodvar(idx.toByte()) },
                            )
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Divider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))
                }

                CalibSwitchRow("Activo", doCalibrate) {
                    doCalibrate = it
                    Natives.setDoCalibrate(it)
                    if (it) Natives.setshowcalibratedstream(true)
                }
                CalibSwitchRow("Calibrar pasado",    calibPast)  { calibPast = it; Natives.setCalibratePast(it) }
                CalibSwitchRow("Todos los valores",  allValues)  { allValues = it; Natives.setAllValues(it) }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cerrar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalibSwitchRow(
    label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, modifier = Modifier.padding(start = 12.dp),
             style = MaterialTheme.typography.bodyMedium)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// M5 — Estadísticas
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Diálogo de estadísticas — réplica de [tk.glucodata.Stats].
 */
@Composable
fun VicoStatsDialog(onDismiss: () -> Unit) {
    var days       by remember { mutableStateOf("${Natives.getAnalysedays()}") }
    var useHistory by remember { mutableStateOf(false) } // se pasa a analysedays al confirmar

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Estadísticas",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                OutlinedTextField(
                    value = days,
                    onValueChange = { days = it },
                    label = { Text("Días") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Switch(
                        checked = useHistory,
                        onCheckedChange = { useHistory = it },
                    )
                    Text("Usar historial", modifier = Modifier.padding(start = 12.dp),
                         style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    TextButton(
                        onClick = {
                            val d = days.trim().toIntOrNull()
                            if (d != null && d > 0) {
                                Natives.analysedays(d, useHistory)
                                Natives.makepercentages()
                                tk.glucodata.chart.NativeGraphDataRepository.notifyDataChanged()
                                onDismiss()
                            }
                        },
                    ) { Text("Ver estadísticas", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// M11 — Alarmas
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Diálogo de alarmas — réplica de los campos que expone [Natives.setalarms]
 * y [Natives.setAdvancedAlarms].
 */
@Composable
fun VicoAlarmsDialog(onDismiss: () -> Unit) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val unit         = remember { Natives.getunit() } // 1=mmol/L, 2=mg/dL
    val unitLabel    = if (unit == 1) "mmol/L" else "mg/dL"

    var alarmsOn   by remember { mutableStateOf(Natives.getUSEALARM()) }
    var hasHigh    by remember { mutableStateOf(Natives.hasalarmhigh()) }
    var highVal    by remember { mutableFloatStateOf(Natives.alarmhigh()) }
    var hasLow     by remember { mutableStateOf(Natives.hasalarmlow()) }
    var lowVal     by remember { mutableFloatStateOf(Natives.alarmlow()) }
    var hasLoss    by remember { mutableStateOf(Natives.hasalarmloss()) }
    var hasValue   by remember { mutableStateOf(Natives.hasvaluealarm()) }
    var hasVHigh   by remember { mutableStateOf(Natives.hasalarmveryhigh()) }
    var vHighVal   by remember { mutableFloatStateOf(Natives.alarmveryhigh()) }
    var hasVLow    by remember { mutableStateOf(Natives.hasalarmverylow()) }
    var vLowVal    by remember { mutableFloatStateOf(Natives.alarmverylow()) }

    val hiRange = if (unit == 1) 4f..20f  else 72f..360f
    val loRange = if (unit == 1) 2f..12f  else 36f..216f

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = screenHeight * 0.88f),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Alarmas",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                AlarmMasterRow("Alarmas activadas", alarmsOn) { alarmsOn = it; Natives.setUSEALARM(it) }

                if (alarmsOn) {
                    Divider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))
                    AlarmSliderSection("Glucosa alta",     unitLabel, hasHigh,  highVal,  hiRange, { hasHigh  = it }) { highVal  = it }
                    Divider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                    AlarmSliderSection("Glucosa baja",     unitLabel, hasLow,   lowVal,   loRange, { hasLow   = it }) { lowVal   = it }
                    Divider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                    AlarmSliderSection("Glucosa muy alta", unitLabel, hasVHigh, vHighVal, hiRange, { hasVHigh = it }) { vHighVal = it }
                    Divider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                    AlarmSliderSection("Glucosa muy baja", unitLabel, hasVLow,  vLowVal,  loRange, { hasVLow  = it }) { vLowVal  = it }
                    Divider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                    AlarmMasterRow("Alarma por pérdida de señal", hasLoss)  { hasLoss  = it }
                    AlarmMasterRow("Alarma al recibir valor",     hasValue) { hasValue = it }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    TextButton(
                        onClick = {
                            Natives.setalarms(lowVal, highVal, hasLow, hasHigh, hasValue, hasLoss)
                            Natives.setAdvancedAlarms(
                                vLowVal, vHighVal, hasVLow, hasVHigh,
                                false, false, 0f, 0f,
                            )
                            onDismiss()
                        },
                    ) { Text("Guardar", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun AlarmMasterRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Switch(checked = checked, onCheckedChange = onChange)
        Text(label, modifier = Modifier.padding(start = 12.dp),
             style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AlarmSliderSection(
    title: String, unit: String,
    enabled: Boolean, value: Float,
    range: ClosedFloatingPointRange<Float>,
    onToggle: (Boolean) -> Unit,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Switch(checked = enabled, onCheckedChange = onToggle)
            Text(title, modifier = Modifier.padding(start = 12.dp).weight(1f),
                 style = MaterialTheme.typography.bodyMedium)
            if (enabled) {
                val fmt = if (unit == "mmol/L") "%.1f $unit" else "%.0f $unit"
                Text(fmt.format(value), style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.primary)
            }
        }
        if (enabled) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )
        }
    }
}
