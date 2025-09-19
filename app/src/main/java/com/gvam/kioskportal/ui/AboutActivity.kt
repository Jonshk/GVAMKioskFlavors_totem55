package com.gvam.kioskportal.ui

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.gvam.kioskportal.util.Prefs
import com.gvam.kioskportal.util.Pin

@OptIn(ExperimentalMaterial3Api::class)
class AboutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialTitle         = Prefs.loadTitle(this) ?: "Portal"
        val initialDeviceNumber  = Prefs.loadDeviceNumber(this).orEmpty()
        val hideSettingsInit     = Prefs.isSettingsHidden(this)
        val androidId            = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        // Version (compatible minSdk 26)
        val pInfo    = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
        val verName  = pInfo?.versionName ?: "—"
        val verCode  = pInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L

        setContent {
            MaterialTheme {
                val ctx = LocalContext.current
                var deviceNumber by rememberSaveable { mutableStateOf(initialDeviceNumber) }
                var hideSettings by rememberSaveable { mutableStateOf(hideSettingsInit) }
                var showPinDialog by rememberSaveable { mutableStateOf(false) }
                val scroll = rememberScrollState()

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Acerca de / Dispositivo", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            navigationIcon = { TextButton(onClick = { finish() }) { Text("Atrás") } }
                        )
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .padding(padding)
                            .padding(16.dp)
                            .fillMaxSize()
                            .verticalScroll(scroll),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // App
                        Text(initialTitle, style = MaterialTheme.typography.headlineSmall)
                        Text("Versión: $verName ($verCode)")
                        HorizontalDivider()

                        // Dispositivo
                        Text("Dispositivo", style = MaterialTheme.typography.titleMedium)
                        InfoRow("Fabricante", Build.MANUFACTURER.orEmpty())
                        InfoRow("Modelo", Build.MODEL.orEmpty())
                        InfoRow("Android", "${Build.VERSION.RELEASE ?: "—"} (SDK ${Build.VERSION.SDK_INT})")
                        InfoRow("ANDROID_ID", androidId)
                        HorizontalDivider()

                        // Número de dispositivo
                        Text("Número de dispositivo", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = deviceNumber,
                            onValueChange = { deviceNumber = it.take(32) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Ej.: KIOSK-001") },
                            singleLine = true
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(onClick = { Prefs.saveDeviceNumber(ctx, deviceNumber.trim()) }) {
                                Text("Guardar número")
                            }
                        }
                        HorizontalDivider()

                        // Ocultar Ajustes
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Ocultar Ajustes de Android", style = MaterialTheme.typography.titleMedium)
                                Text("Si se activa, la opción de Ajustes se oculta del menú.")
                            }
                            Switch(checked = hideSettings, onCheckedChange = {
                                hideSettings = it
                                Prefs.setHideSettings(ctx, it)
                            })
                        }
                        HorizontalDivider()

                        // Administración
                        Text("Administración", style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(onClick = { showPinDialog = true }) { Text("Cambiar PIN") }
                        }

                        // Botonera inferior
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(onClick = { finish() }) { Text("Cerrar") }
                            Spacer(Modifier.width(12.dp))
                            Button(onClick = {
                                Prefs.saveDeviceNumber(ctx, deviceNumber.trim())
                                Prefs.setHideSettings(ctx, hideSettings)
                                finish()
                            }) { Text("Guardar") }
                        }
                    }
                }

                if (showPinDialog) {
                    PinCreateDialog(
                        onCancel = { showPinDialog = false },
                        onConfirm = { newPin ->
                            Pin.savePin(ctx, newPin)
                            showPinDialog = false
                            // No Toast aquí: evitamos dependencia a Activity; úsalo si quieres.
                        }
                    )
                }
            }
        }
    }
}

/* ---------- Helpers UI ---------- */

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.widthIn(min = 120.dp).weight(0.4f))
        Text(value, modifier = Modifier.weight(0.6f))
    }
}

/* ---------- Diálogo Crear/Cambiar PIN ---------- */
@Composable
private fun PinCreateDialog(
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var p1 by rememberSaveable { mutableStateOf("") }
    var p2 by rememberSaveable { mutableStateOf("") }
    val ok = p1.length in 4..8 && p1 == p2

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Crear / Cambiar PIN (4–8 dígitos)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = p1,
                    onValueChange = { if (it.length <= 8) p1 = it.filter(Char::isDigit) },
                    singleLine = true,
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword
                    )
                )
                OutlinedTextField(
                    value = p2,
                    onValueChange = { if (it.length <= 8) p2 = it.filter(Char::isDigit) },
                    singleLine = true,
                    label = { Text("Repite PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword
                    )
                )
                if (p1.isNotEmpty() && p2.isNotEmpty() && !ok) {
                    Text("Los PIN no coinciden o longitud inválida", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (ok) onConfirm(p1) }, enabled = ok) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancelar") }
        },
        shape = MaterialTheme.shapes.large
    )
}
