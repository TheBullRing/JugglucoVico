/*
 * Fase 8 — Menús Compose nativos para el path Vico.
 *
 * VicoConnectivityDialogs: diálogos de conectividad del path Vico.
 *
 *  • VicoDataExchangeMenu    — sub-menú "Intercambiar datos" (desde Settings)
 *  • VicoNightscoutUploaderDialog — M12a, réplica de NightPost.config()
 *
 * Las funciones JNI usadas son exactamente las mismas que en el código Java
 * original: Natives.getnightuploadurl(), Natives.getnightuploadsecret(), etc.
 */
package tk.glucodata.ui.menus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tk.glucodata.NightPost
import tk.glucodata.Natives
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Sub-menú: Intercambiar datos
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Menú de intercambio de datos — equivalente al botón "Intercambiar datos"
 * de Settings.java que llama a exchanges().
 *
 * Presenta las opciones de conectividad disponibles y navega al diálogo
 * correspondiente de cada una.
 */
@Composable
fun VicoDataExchangeMenu(
    onNightscoutUploader: () -> Unit,
    onNightscoutFollower: () -> Unit,
    onLibreView:          () -> Unit,
    onDismiss:            () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape          = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier       = Modifier.fillMaxWidth(0.92f),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text       = "Intercambiar datos",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.padding(bottom = 8.dp),
                )

                OutlinedButton(
                    onClick  = { onDismiss(); onNightscoutUploader() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Cargador Nightscout") }

                OutlinedButton(
                    onClick  = { onDismiss(); onNightscoutFollower() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Seguidor Nightscout") }

                OutlinedButton(
                    onClick  = { onDismiss(); onLibreView() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("LibreView") }

                Spacer(Modifier.height(4.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// M12a — Cargador Nightscout (NightPost.config)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Diálogo "Cargador Nightscout" — réplica de [NightPost.config()].
 *
 * Lee y escribe con exactamente las mismas llamadas JNI que el código Java:
 *  - [Natives.getnightuploadurl] / [Natives.setNightUploader]
 *  - [Natives.getnightuploadsecret]
 *  - [Natives.getuseuploader]
 *  - [Natives.getnightscoutV3]
 *  - [Natives.getpostTreatments] / [Natives.setpostTreatments]
 *  - [Natives.wakeuploader]
 *  - [NightPost.askclearupload] → llama a [Natives.clearupload] internamente
 */
@Composable
fun VicoNightscoutUploaderDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    // ── Estado local inicializado con los valores JNI actuales ───────────
    var url             by remember { mutableStateOf(Natives.getnightuploadurl() ?: "") }
    var secret          by remember { mutableStateOf(Natives.getnightuploadsecret() ?: "") }
    var active          by remember { mutableStateOf(Natives.getuseuploader()) }
    var useV3           by remember { mutableStateOf(Natives.getnightscoutV3()) }
    var sendTreatments  by remember { mutableStateOf(Natives.getpostTreatments()) }
    var secretVisible   by remember { mutableStateOf(false) }

    // Estado de subida: timestamp + mensaje (los mismos campos que NightPost.uploadstatus)
    val statusText = remember {
        val ts  = NightPost.getUploadTime()
        val msg = NightPost.getUploadStatus() ?: ""
        if (ts == 0L) msg
        else {
            val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
            "${fmt.format(Date(ts))}: $msg"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape          = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier       = Modifier.fillMaxWidth(0.92f),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // ── Título ───────────────────────────────────────────────
                Text(
                    text       = "Cargador Nightscout",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.padding(bottom = 12.dp),
                )

                // ── URL ──────────────────────────────────────────────────
                OutlinedTextField(
                    value         = url,
                    onValueChange = { url = it },
                    label         = { Text("Nightscout URL") },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier      = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                // ── api_secret ───────────────────────────────────────────
                OutlinedTextField(
                    value                  = secret,
                    onValueChange          = { secret = it },
                    label                  = { Text("api_secret") },
                    singleLine             = true,
                    visualTransformation   = if (secretVisible) VisualTransformation.None
                                             else PasswordVisualTransformation(),
                    keyboardOptions        = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier               = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = { secretVisible = !secretVisible }) {
                            Text(if (secretVisible) "Ocultar" else "Ver")
                        }
                    },
                )

                Spacer(Modifier.height(8.dp))

                // ── Estado ───────────────────────────────────────────────
                Text(
                    text     = statusText,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                )

                Divider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))

                // ── Checkboxes ───────────────────────────────────────────
                ConnectivityCheckRow(
                    label   = "Activo",
                    checked = active,
                    onCheckedChange = { active = it },
                )
                ConnectivityCheckRow(
                    label   = "Usar API V3 (test)",
                    checked = useV3,
                    onCheckedChange = { useV3 = it },
                )
                ConnectivityCheckRow(
                    label   = "Enviar cantidades / tratamientos",
                    checked = sendTreatments,
                    onCheckedChange = { sendTreatments = it },
                )

                Divider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))

                // ── Botones de acción ────────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick  = { NightPost.clearUploadData(context) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Reenviar datos") }

                    OutlinedButton(
                        onClick  = { Natives.wakeuploader() },
                        modifier = Modifier.weight(1f),
                    ) { Text("Enviar ahora") }
                }

                Spacer(Modifier.height(8.dp))

                // ── Cancelar / Guardar ───────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    TextButton(
                        onClick = {
                            // Persiste exactamente igual que el código Java:
                            // setNightUploader(url, secret, active, v3)
                            Natives.setNightUploader(url, secret, active, useV3)
                            Natives.setpostTreatments(sendTreatments)
                            onDismiss()
                        },
                    ) {
                        Text("Guardar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Primitivo compartido
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConnectivityCheckRow(
    label:           String,
    checked:         Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Switch(
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
