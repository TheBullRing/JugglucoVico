/*
 * Fase 8 — Menús Compose nativos para el path Vico.
 *
 * VicoSettingsScreen (M4): réplica 1-a-1 de Settings.java mksettings() en Compose.
 *
 * Lee y escribe exactamente las mismas claves que el diálogo Java original,
 * usando las mismas llamadas JNI estáticas (Natives.*) para garantizar
 * la paridad de datos entre ambos paths.
 *
 * Campos implementados (mobile, no-Wearable):
 *   1. Unidades (mmol/L vs mg/dL) — Natives.getunit() / Applic.setunit()
 *   2. Sonido NFC                  — Natives.nfcsound() / Natives.setnfcsound()
 *   3. Escanear inicia la app      — IntentScan (via Natives.getglobalscan/setglobalscan)
 *   4. Cámara (tecla)              — Natives.camerakey() / Natives.setcamerakey()
 *   5. Rotación de pantalla        — Natives.getScreenOrientation / setScreenOrientation
 *   6. Glucosa flotante            — Natives.getfloatglucose() / Floating.setfloatglucose()
 *   7. Calibración                 — navega a VicoMenuState.Calibration
 *   8. Notificación de glucosa     — Natives.getshowalways() / Notify.glucosestatus()
 *   9. Intercambiar datos          — navega a VicoMenuState.DataExchange
 *  10. Recordatorios               — (pendiente M11)
 *  11. Alarmas                     — navega a VicoMenuState.Alarms
 *  12. Etiquetas numéricas         — (pendiente)
 *  13. Escaneo de Google           — Natives.getGoogleScan() / Natives.setGoogleScan()
 *  14. Pantalla / Display          — (pendiente displaysettings)
 *  15. Usar nuevo gráfico (Vico)   — toggle Applic.setUseVicoChart() + recreate
 *  16. Ayuda / About / Intro       — botones de información
 *  17. Cerrar                      — dismiss
 */
package tk.glucodata.ui.menus

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import tk.glucodata.Applic
import tk.glucodata.Natives
import tk.glucodata.settings.Settings

// ─────────────────────────────────────────────────────────────────────────────
// Composable principal
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Diálogo de Ajustes para el path Vico.
 *
 * Se muestra como [Dialog] de Compose (WRAP_CONTENT) respetando automáticamente
 * los WindowInsets. Llama a las mismas funciones JNI que Settings.java.
 *
 * @param onDismiss            Cierra el diálogo (resetea VicoMenuState.None).
 * @param onNavigateTo         Navega a otro estado del menú (ej. Calibration, Alarms).
 */
@Composable
fun VicoSettingsScreen(
    onDismiss:    () -> Unit,
    onNavigateTo: ((VicoMenuState) -> Unit)? = null,
) {
    val context  = LocalContext.current
    val activity = context as? Activity

    // ── Estado local — se inicializa con los valores JNI actuales ──────────
    var unit              by remember { mutableIntStateOf(Natives.getunit()) }  // 1=mmol/L, 2=mg/dL
    var nfcSound          by remember { mutableStateOf(Natives.nfcsound()) }
    var globalScan        by remember { mutableStateOf(Settings.isIntentScanEnabled(context)) }
    var googleScan        by remember { mutableStateOf(Natives.getGoogleScan()) }
    var floatGlucose      by remember { mutableStateOf(Natives.getfloatglucose()) }
    var glucoseNotify     by remember { mutableStateOf(Natives.getshowalways()) }
    // Orientación: mapear desde la constante Android al índice del spinner
    // 0 = Libre (-1/UNSPECIFIED), 1 = Landscape, 2 = Reverse landscape
    val orientationValues = listOf(-1, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE)
    val orientationLabels = listOf("Libre", "Horizontal", "Horizontal invertido")
    var orientationIndex  by remember {
        val cur = Natives.getScreenOrientation()
        mutableIntStateOf(orientationValues.indexOfFirst { it == cur }.coerceAtLeast(0))
    }
    val hasnfc = tk.glucodata.MainActivity.hasnfc
    val cameraKey = remember { if (!tk.glucodata.Applic.isWearable) Natives.camerakey() else 0 }
    var cameraKeyEnabled by remember { mutableStateOf(cameraKey == 1) }

    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // ── Título ──────────────────────────────────────────────────
                Text(
                    text       = "Ajustes",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.padding(bottom = 12.dp),
                )

                // ── 1. Unidades ─────────────────────────────────────────────
                SettingSectionLabel("Unidades de glucosa")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier.fillMaxWidth(),
                ) {
                    RadioButton(
                        selected = unit == 1,
                        onClick  = {
                            unit = 1
                            (context.applicationContext as? Applic)?.setunit(1)
                        },
                    )
                    Text("mmol/L", modifier = Modifier.padding(end = 16.dp))
                    RadioButton(
                        selected = unit == 2,
                        onClick  = {
                            unit = 2
                            (context.applicationContext as? Applic)?.setunit(2)
                        },
                    )
                    Text("mg/dL")
                }

                SettingDivider()

                // ── 2. Sonido NFC ───────────────────────────────────────────
                if (hasnfc) {
                    SettingCheckRow(
                        label   = "Sonido al escanear NFC",
                        checked = nfcSound,
                        onCheckedChange = {
                            nfcSound = it
                            Natives.setnfcsound(it)
                        },
                    )
                    // ── 3. Escanear inicia la app ───────────────────────────
                    SettingCheckRow(
                        label   = "Escanear inicia la app",
                        checked = globalScan,
                        onCheckedChange = {
                            globalScan = it
                            Settings.setIntentScanEnabled(context, it)
                        },
                    )
                    SettingDivider()
                }

                // ── 4. Cámara (tecla) ───────────────────────────────────────
                if (cameraKey > 0) {
                    SettingCheckRow(
                        label   = "Deshabilitar tecla de cámara",
                        checked = cameraKeyEnabled,
                        onCheckedChange = {
                            cameraKeyEnabled = it
                            Natives.setcamerakey(if (it) 1 else 2)
                        },
                    )
                    SettingDivider()
                }

                // ── 5. Rotación de pantalla ─────────────────────────────────
                SettingSectionLabel("Orientación de pantalla")
                Column {
                    orientationLabels.forEachIndexed { idx, label ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier          = Modifier.fillMaxWidth(),
                        ) {
                            RadioButton(
                                selected = orientationIndex == idx,
                                onClick  = {
                                    orientationIndex = idx
                                    val value = orientationValues[idx]
                                    Natives.setScreenOrientation(value)
                                    activity?.let {
                                        if (it is tk.glucodata.MainActivity)
                                            it.applyScreenOrientation(it.resources.configuration)
                                    }
                                },
                            )
                            Text(label)
                        }
                    }
                }

                SettingDivider()

                // ── 6. Glucosa flotante ─────────────────────────────────────
                SettingCheckRow(
                    label   = "Glucosa flotante",
                    checked = floatGlucose,
                    onCheckedChange = {
                        floatGlucose = it
                        activity?.let { act ->
                            tk.glucodata.Floating.setfloatglucose(act, it)
                        }
                    },
                )

                // ── 8. Notificación de glucosa ──────────────────────────────
                SettingCheckRow(
                    label   = "Notificación de glucosa en barra",
                    checked = glucoseNotify,
                    onCheckedChange = {
                        glucoseNotify = it
                        tk.glucodata.Notify.glucosestatus(it)
                    },
                )

                // ── 13. Escaneo de Google ───────────────────────────────────
                SettingCheckRow(
                    label   = "Escaneo de Google",
                    checked = googleScan,
                    onCheckedChange = {
                        googleScan = it
                        Natives.setGoogleScan(it)
                    },
                )

                SettingDivider()

                // ── Botones de navegación ───────────────────────────────────
                SettingSectionLabel("Configuración avanzada")
                Row(
                    modifier            = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 7. Calibración
                    OutlinedButton(
                        onClick  = { onNavigateTo?.invoke(VicoMenuState.Calibration) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Calibración") }

                    // 9. Intercambiar datos
                    OutlinedButton(
                        onClick  = { onNavigateTo?.invoke(VicoMenuState.DataExchange) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Intercambiar datos") }
                }
                Row(
                    modifier            = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 11. Alarmas
                    OutlinedButton(
                        onClick  = { onNavigateTo?.invoke(VicoMenuState.Alarms) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Alarmas") }

                    // 10. Recordatorios (pendiente)
                    OutlinedButton(
                        onClick  = { /* Pendiente M11 — recordatorios */ },
                        modifier = Modifier.weight(1f),
                    ) { Text("Recordatorios") }
                }

                SettingDivider()

                // ── 15. Desactivar nuevo gráfico (Vico) ─────────────────────
                SettingSectionLabel("Cambio de interfaz")
                OutlinedButton(
                    onClick  = {
                        onDismiss()
                        Applic.setUseVicoChart(false)
                        activity?.recreate()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Volver al gráfico clásico (NanoVG)")
                }

                SettingDivider()

                Spacer(Modifier.height(4.dp))

                // ── Cerrar ──────────────────────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cerrar")
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Primitivos de UI locales al fichero
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingSectionLabel(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.labelMedium,
        color    = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingDivider() {
    Divider(
        modifier  = Modifier.padding(vertical = 6.dp),
        thickness = 0.5.dp,
        color     = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun SettingCheckRow(
    label:          String,
    checked:        Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        androidx.compose.material3.Switch(
            checked         = checked,
            onCheckedChange = onCheckedChange,
        )
        Text(
            text     = label,
            modifier = Modifier.padding(start = 12.dp),
            style    = MaterialTheme.typography.bodyMedium,
        )
    }
}
