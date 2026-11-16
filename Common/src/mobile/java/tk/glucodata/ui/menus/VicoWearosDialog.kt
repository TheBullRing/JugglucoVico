/*
 * Fase 8 — Menús Compose nativos para el path Vico.
 *
 * VicoWearosDialog: réplica en Compose de Wearos.show() (Relojes → WearOS → Config.).
 * La lógica nativa vive en [tk.glucodata.WearosVicoBridge].
 *
 * Reglas:
 *  - Ninguna clase NanoVG importa este fichero.
 *  - Wearos.java (NanoVG) permanece intacto.
 */
package tk.glucodata.ui.menus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import tk.glucodata.R
import tk.glucodata.WearosVicoBridge

/**
 * Diálogo "Configurar WearOS": elige reloj, dónde está el sensor y dónde se
 * introducen las cantidades, o restablece valores predeterminados.
 */
@Composable
fun VicoWearosDialog(onDismiss: () -> Unit) {
    val context      = LocalContext.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    var targets by remember { mutableStateOf(WearosVicoBridge.targets()) }
    var selected by remember { mutableStateOf(if (targets.isEmpty()) -1 else 0) }
    var isSearching by remember { mutableStateOf(targets.isEmpty()) }
    var searchTrigger by remember { mutableStateOf(0) }

    // La detección de relojes es asíncrona: refrescar dispositivos y consultar
    // periódicamente mientras el diálogo esté activo para actualizar la lista.
    LaunchedEffect(searchTrigger) {
        WearosVicoBridge.refreshDevices()
        var count = 0
        while (true) {
            val found = WearosVicoBridge.targets()
            if (found.isNotEmpty()) {
                if (targets != found) {
                    targets = found
                    if (selected < 0 || selected >= found.size) {
                        selected = 0
                    }
                }
                isSearching = false
            } else {
                count++
                if (count >= 10) {
                    isSearching = false
                }
            }
            delay(500L)
        }
    }
    var menuOpen by remember { mutableStateOf(false) }

    var sensorOnWatch by remember { mutableStateOf(false) }
    var numsOnWatch   by remember { mutableStateOf(false) }
    var directAvail   by remember { mutableStateOf(false) }
    var numsAvail     by remember { mutableStateOf(false) }
    // true cuando el usuario ha tocado un radio: "Predeterminados" se desactiva
    // y "Cerrar" aplica los cambios (igual que Wearos.java).
    var changed       by remember { mutableStateOf(false) }
    var confirmDefaults by remember { mutableStateOf(false) }

    // Equivale a remake() + refreshStatus (cada segundo) de Wearos.java.
    LaunchedEffect(selected) {
        if (selected < 0) return@LaunchedEffect
        val target = targets[selected]
        changed = false
        WearosVicoBridge.switchSync()
        var first = true
        while (true) {
            val st = WearosVicoBridge.status(target)
            directAvail = st.directAvailable
            numsAvail   = st.numsAvailable
            if (first || !changed) {
                sensorOnWatch = st.directAvailable && st.sensorOnWatch
                numsOnWatch   = st.numsAvailable && st.numsOnWatch
            }
            first = false
            delay(1000L)
        }
    }

    fun applyAndClose() {
        if (selected >= 0 && selected < targets.size && changed) {
            (context as? android.app.Activity)?.let {
                WearosVicoBridge.apply(it, targets[selected], numsOnWatch, sensorOnWatch)
            }
        }
        onDismiss()
    }

    fun doDefaults() {
        WearosVicoBridge.setDefaults(targets[selected])
        onDismiss()
    }

    Dialog(
        onDismissRequest = ::applyAndClose,
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
                    text       = "WearOS",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.padding(bottom = 12.dp),
                )

                if (targets.isEmpty()) {
                    if (isSearching) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier          = Modifier.padding(bottom = 16.dp),
                        ) {
                            Text(
                                "Buscando relojes...",
                                style    = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        Text(
                            stringResource(R.string.nowatchesfound),
                            style    = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        OutlinedButton(
                            onClick = {
                                isSearching = true
                                searchTrigger++
                            },
                            modifier = Modifier.padding(bottom = 12.dp),
                        ) {
                            Text(stringResource(R.string.finddevices))
                        }
                    }
                } else {
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick  = { menuOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(targets[selected].title())
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            targets.forEachIndexed { i, t ->
                                DropdownMenuItem(
                                    text    = { Text(t.title()) },
                                    onClick = { selected = i; menuOpen = false },
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ── Cantidades ───────────────────────────────────────────
                    Text(stringResource(R.string.enternums), style = MaterialTheme.typography.bodyMedium)
                    PhoneWatchRow(
                        watchSelected = numsOnWatch,
                        enabled       = numsAvail,
                        onSelect      = { numsOnWatch = it; changed = true },
                    )

                    Spacer(Modifier.height(12.dp))

                    // ── Sensor directo ───────────────────────────────────────
                    val direct = stringResource(R.string.directsensor)
                    val conn   = stringResource(R.string.connectionl)
                    Text("$direct $conn".trim(), style = MaterialTheme.typography.bodyMedium)
                    PhoneWatchRow(
                        watchSelected = sensorOnWatch,
                        enabled       = directAvail,
                        onSelect      = { sensorOnWatch = it; changed = true },
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            (context as? tk.glucodata.MainActivity)?.let {
                                tk.glucodata.help.helplight(R.string.wearosinfo, it)
                            }
                        },
                    ) {
                        Text(stringResource(R.string.helpname))
                    }
                    Spacer(Modifier.weight(1f))
                    if (targets.isNotEmpty() && selected >= 0 && selected < targets.size) {
                        TextButton(
                            enabled = !changed,
                            onClick = {
                                if (WearosVicoBridge.defaultsNeedConfirm(targets[selected])) confirmDefaults = true
                                else doDefaults()
                            },
                        ) { Text(stringResource(R.string.defaults)) }
                    }
                    Button(onClick = ::applyAndClose) {
                        Text(stringResource(R.string.closename), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (confirmDefaults) {
        AlertDialog(
            onDismissRequest = { confirmDefaults = false },
            title            = { Text(stringResource(R.string.notsynced)) },
            text             = { Text(stringResource(R.string.lossdata)) },
            confirmButton    = {
                TextButton(onClick = { confirmDefaults = false; doDefaults() }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton    = {
                TextButton(onClick = { confirmDefaults = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun PhoneWatchRow(
    watchSelected: Boolean,
    enabled: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = !watchSelected, enabled = enabled, onClick = { onSelect(false) })
        Text(stringResource(R.string.phone), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.padding(start = 16.dp))
        RadioButton(selected = watchSelected, enabled = enabled, onClick = { onSelect(true) })
        Text(stringResource(R.string.watch), style = MaterialTheme.typography.bodyMedium)
    }
}
