/*
 * Fase 8 — Menús Compose nativos para el path Vico.
 *
 * VicoWatchMealDialogs: diálogos que reemplazan los overlays Java de
 * Watch.show(), addnumberwithmenu() y addnumberview().
 *
 *  • VicoWatchDialog        — M6/Watch:  conectividad de relojes y gadgets
 *  • VicoMealSimpleDialog   — M13/Meal:  registro rápido de carbohidratos
 *  • VicoNumberEntryDialog  — M7/NumberEntry: entrada manual de glucosa/etiqueta
 *
 * Reglas:
 *  - Ninguna clase NanoVG importa este fichero.
 *  - Los diálogos Java originales siguen intactos en el path NanoVG.
 */
package tk.glucodata.ui.menus

import android.content.ComponentName
import android.content.pm.PackageManager
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
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tk.glucodata.Applic
import tk.glucodata.Natives
import tk.glucodata.Notify
import tk.glucodata.SuperGattCallback
import tk.glucodata.chart.NativeGraphDataRepository
import tk.glucodata.nums.numio

// ─────────────────────────────────────────────────────────────────────────────
// M6 / Watch — Conectividad de relojes y gadgets
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Diálogo "Relojes" — réplica en Compose de [tk.glucodata.Watch.show()].
 *
 * Expone las opciones de conectividad de relojes:
 *  - Notificar al reloj
 *  - Separado (alertseparate)
 *  - Watchdrip / GadgetBridge
 *  - WearOS + Configurar WearOS (navega a sub-diálogo)
 *  - Servidor web (xdrip) + Configurar servidor
 *  - Kerfstok (Garmin) + Estado Garmin
 */
@Composable
fun VicoWatchDialog(
    onDismiss:         () -> Unit,
    onConfigureWearOS: () -> Unit,
    onConfigureServer: () -> Unit,
) {
    val context      = LocalContext.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // Estado leído una sola vez al abrir el diálogo, igual que Watch.java
    var notify    by remember { mutableStateOf(Applic.app.getnotify()) }
    var separate  by remember { mutableStateOf(Notify.alertseparate) }
    var watchdrip by remember { mutableStateOf(SuperGattCallback.doWearInt) }
    var gadget    by remember { mutableStateOf(SuperGattCallback.doGadgetbridge) }
    var wearOS    by remember { mutableStateOf(Applic.useWearos()) }
    var webServer by remember { mutableStateOf(Natives.getusexdripwebserver()) }
    val useGarminIn = remember { Natives.getusegarmin() }
    var kerfstok  by remember { mutableStateOf(useGarminIn) }

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape          = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier       = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = screenHeight * 0.88f),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text       = "Relojes",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.padding(bottom = 12.dp),
                )

                // ── Notificaciones ────────────────────────────────────────────
                WatchSwitchRow("Notificar", notify) { v ->
                    notify = v
                    Applic.app.setnotifyPublic(v)
                }
                WatchSwitchRow("Separado", separate) { v ->
                    separate = v
                    Notify.alertseparate = v
                    Natives.setSeparate(v)
                }

                Divider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))

                // ── Gadgets / protocolo ──────────────────────────────────────
                WatchSwitchRow("Watchdrip", watchdrip) { v ->
                    watchdrip = v
                    Natives.setwatchdrip(v)
                    tk.glucodata.watchdrip.set(v)
                    SuperGattCallback.doWearInt = v
                }
                WatchSwitchRow("GadgetBridge", gadget) { v ->
                    gadget = v
                    Natives.setgadgetbridge(v)
                    SuperGattCallback.doGadgetbridge = v
                }

                Divider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))

                // ── WearOS ───────────────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                ) {
                    Switch(
                        checked         = wearOS,
                        onCheckedChange = { v ->
                            wearOS = v
                            // Equivalente a Watch.setuseWearos(): activa/desactiva MessageReceiver
                            try {
                                val pm   = Applic.app.packageManager
                                val name = ComponentName(Applic.app, tk.glucodata.MessageReceiver::class.java)
                                val state = if (v) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                                            else   PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                                pm.setComponentEnabledSetting(name, state, PackageManager.DONT_KILL_APP)
                            } catch (_: Throwable) { }

                            if (v) {
                                if (!Applic.useWearos()) {
                                    tk.glucodata.MessageSender.initwearos(Applic.app)
                                } else {
                                    tk.glucodata.MessageSender.getMessageSender()?.finddevices()
                                }
                                Natives.switchSync()
                            }
                        },
                    )
                    Text(
                        "WearOS",
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (wearOS) {
                        OutlinedButton(onClick = { onDismiss(); onConfigureWearOS() }) {
                            Text("Config.")
                        }
                    }
                }

                Divider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))

                // ── Servidor web (xdrip) ─────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                ) {
                    Switch(
                        checked         = webServer,
                        onCheckedChange = { v ->
                            webServer = v
                            Natives.setusexdripwebserver(v)
                        },
                    )
                    Text(
                        "Servidor web",
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = { onDismiss(); onConfigureServer() }) {
                        Text("Config.")
                    }
                }

                Divider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))

                // ── Kerfstok (Garmin) ────────────────────────────────────────
                WatchSwitchRow("Kerfstok", kerfstok) { v ->
                    kerfstok = v
                    if (v && !useGarminIn) {
                        (context as? tk.glucodata.MainActivity)?.let { act ->
                            Applic.app.numdata.reinit(act)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── Botones de acción ────────────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Button(
                        onClick = {
                            // Persiste Garmin exactamente como Watch.java lo hace en OK.setOnClickListener
                            if (useGarminIn != kerfstok) {
                                Natives.setusegarmin(kerfstok)
                                if (!kerfstok) {
                                    Natives.sethasgarmin(false)
                                    Applic.app.numdata.stop()
                                }
                            }
                            onDismiss()
                        },
                    ) {
                        Text("OK", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
        Text(
            label,
            modifier = Modifier.padding(start = 12.dp),
            style    = MaterialTheme.typography.bodyMedium,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// M13 / Meal — Registro rápido de carbohidratos
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Diálogo de registro de comida simplificado para el path Vico.
 *
 * Registra una nueva entrada de comida usando [Natives.saveNum] con el
 * puntero de números activo ([numio.numptrs]) y el tipo de comida
 * [Natives.getmealvar()], igual que lo hace [tk.glucodata.NumberView.saveamount].
 */
@Composable
fun VicoMealSimpleDialog(onDismiss: () -> Unit) {
    val context      = LocalContext.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    var carbs by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape          = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier       = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = screenHeight * 0.88f),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Comida",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.padding(bottom = 12.dp),
                )

                OutlinedTextField(
                    value           = carbs,
                    onValueChange   = { carbs = it },
                    label           = { Text("Carbohidratos (g)") },
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier        = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value           = label,
                    onValueChange   = { label = it },
                    label           = { Text("Etiqueta (opcional)") },
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier        = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Button(
                        onClick = {
                            val carbVal = carbs.trim().toFloatOrNull()
                            when {
                                carbVal == null || carbVal <= 0f ->
                                    Toast.makeText(
                                        context,
                                        "Introduce un valor de carbohidratos válido",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                else -> {
                                    // Crea un mealptr nuevo y guarda la entrada igual que NumberView
                                    val mealPtr = Natives.getnewmealptr()
                                    val timeNow  = System.currentTimeMillis() / 1000L
                                    val mealType = Natives.getmealvar().toInt()
                                    val numPtr   = numio.numptrs[1]   // índice 1 = fuente manual
                                    if (numPtr != 0L) {
                                        Natives.saveNum(numPtr, timeNow, carbVal, mealType, mealPtr)
                                        NativeGraphDataRepository.notifyDataChanged()
                                    }
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

// ─────────────────────────────────────────────────────────────────────────────
// M7 / NumberEntry — Entrada manual de glucosa o etiqueta
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Diálogo de entrada manual de glucosa — réplica en Compose de
 * [tk.glucodata.NumberView.addnumberview()].
 *
 * Guarda usando [Natives.saveNum] con el puntero de números activo
 * ([numio.numptrs][1] = fuente manual), igual que [NumberView.saveamount].
 *
 * Ventajas sobre el overlay View Java en landscape:
 *  - Siempre visible por encima del teclado (Dialog Compose maneja WindowInsets.ime)
 *  - Ajusta al tamaño de la orientación actual
 *  - Respeta el tema del usuario (M3 Material You)
 */
@Composable
fun VicoNumberEntryDialog(onDismiss: () -> Unit) {
    val context      = LocalContext.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val unit         = remember { Natives.getunit() }      // 1=mmol/L, 2=mg/dL
    val unitLabel    = if (unit == 1) "mmol/L" else "mg/dL"

    var value     by remember { mutableStateOf("") }
    var noteLabel by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape          = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier       = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = screenHeight * 0.88f),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Añadir valor",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.padding(bottom = 12.dp),
                )

                OutlinedTextField(
                    value           = value,
                    onValueChange   = { value = it },
                    label           = { Text("Glucosa ($unitLabel)") },
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier        = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value           = noteLabel,
                    onValueChange   = { noteLabel = it },
                    label           = { Text("Etiqueta (opcional)") },
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier        = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Button(
                        onClick = {
                            val floatVal = value.trim().toFloatOrNull()
                            when {
                                floatVal == null || floatVal <= 0f ->
                                    Toast.makeText(
                                        context,
                                        "Introduce un valor de glucosa válido",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                else -> {
                                    // Convierte a mg/dL si la unidad es mmol/L, igual que NumberView
                                    val mgdl: Float = if (unit == 1) floatVal * Applic.mgdLmultPublic
                                                      else            floatVal
                                    val bloodType = Natives.getbloodvar().toInt()
                                    val timeNow   = System.currentTimeMillis() / 1000L
                                    val numPtr    = numio.numptrs[1]   // índice 1 = fuente manual
                                    if (numPtr != 0L) {
                                        Natives.saveNum(numPtr, timeNow, mgdl, bloodType, 0)
                                        NativeGraphDataRepository.notifyDataChanged()
                                    }
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
