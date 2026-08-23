/*
 * Fase 8 — Menús Compose nativos para el path Vico.
 *
 * VicoActionDialogs: diálogos de acción para el path Vico.
 *
 *  • VicoAboutDialog — Acerca de (versión, build time, CPU arch)
 *
 * Nota sobre Exportar: la exportación requiere ACTION_CREATE_DOCUMENT
 * (file picker del sistema) + onActivityResult, por lo que sigue
 * delegando al overlay Java existente (Dialogs.showexport) que funciona
 * sobre el ComposeView gracias a addContentView.
 *
 * Reglas:
 *  - Ninguna clase NanoVG importa este fichero.
 *  - Los diálogos Java originales siguen intactos en el path NanoVG.
 */
package tk.glucodata.ui.menus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tk.glucodata.BuildConfig
import tk.glucodata.Natives

// ─────────────────────────────────────────────────────────────────────────────
// Acerca de
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Diálogo "Acerca de" — réplica en Compose de [tk.glucodata.GlucoseCurve.doabout()].
 *
 * Muestra la información de versión, código, arquitectura CPU y tiempo de compilación
 * junto con el texto de licencia principal de la app.
 */
@Composable
fun VicoAboutDialog(onDismiss: () -> Unit) {
    val context      = LocalContext.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // Mismos datos que GlucoseCurve.doabout()
    val aboutText = remember {
        context.getString(tk.glucodata.R.string.about)
    }
    val versionCode = remember { BuildConfig.VERSION_CODE.toString() }
    val versionName = remember { BuildConfig.VERSION_NAME }
    val cpuArch     = remember { Natives.getCPUarch() ?: "" }
    val buildTime   = remember { BuildConfig.BUILD_TIME }

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
                    text       = "Acerca de Juggluco",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.padding(bottom = 12.dp),
                )

                // Información de versión
                AboutInfoRow("Versión",       versionName)
                AboutInfoRow("Código",        versionCode)
                if (cpuArch.isNotEmpty()) {
                    AboutInfoRow("Arquitectura", cpuArch)
                }
                AboutInfoRow("Compilado",     buildTime)

                Divider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 10.dp))

                // Texto de licencia / about de la app
                if (aboutText.isNotEmpty()) {
                    Text(
                        text  = aboutText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Row(
                    modifier              = Modifier.fillMaxWidth(),
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
private fun AboutInfoRow(label: String, value: String) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(
            text       = label,
            style      = MaterialTheme.typography.bodySmall,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text  = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
