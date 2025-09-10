package com.gvam.kioskportal.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gvam.kioskportal.util.Prefs
import com.gvam.kioskportal.util.Pin

class AdminUnlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // Usa tu tema si quieres: PortalTheme { ... }
            MaterialTheme {
                Surface {
                    AdminUnlockScreen(
                        onCancel = { finish() },
                        onOpenHomePicker = { enterMaintenanceAnd { openHomePicker() } },
                        onOpenSettings   = { enterMaintenanceAnd { openSystemSettings() } },
                        onOpenAppInfo    = { enterMaintenanceAnd { openThisAppInfo() } },
                    )
                }
            }
        }
    }

    /** Activa mantenimiento + desancla + muestra barras y luego ejecuta [action]. */
    private fun enterMaintenanceAnd(action: () -> Unit) {
        Prefs.setMaintenance(this, true)
        runCatching { stopLockTask() }
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
        action()
        Toast.makeText(this, "Mantenimiento activado", Toast.LENGTH_SHORT).show()
        finish() // deja al técnico ya en la UI del sistema
    }

    private fun openSystemSettings() {
        val i = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(i) }
    }

    /** Intenta abrir el selector de Launcher (Home predeterminado). */
    private fun openHomePicker() {
        val home = Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (home.resolveActivity(packageManager) != null) {
            runCatching { startActivity(home) }
            return
        }
        val defaults = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (defaults.resolveActivity(packageManager) != null) {
            runCatching { startActivity(defaults) }
            return
        }
        openSystemSettings()
    }

    /** Ficha de información de esta app (para limpiar “Abrir de forma predeterminada”). */
    private fun openThisAppInfo() {
        val uri = Uri.parse("package:$packageName")
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(i) }
    }
}

@Composable
private fun AdminUnlockScreen(
    onCancel: () -> Unit,
    onOpenHomePicker: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppInfo: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun requireValidPin(doAfter: () -> Unit) {
        if (!Pin.verifyPin(ctx, pin)) {
            error = "PIN incorrecto"
        } else {
            error = null
            doAfter()
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Acceso técnico", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = {
                pin = it.filter(Char::isDigit).take(8)   // 4–8 dígitos
                error = null
            },
            singleLine = true,
            label = { Text("PIN de administrador") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(18.dp))

        Button(
            onClick = { requireValidPin { onOpenHomePicker() } },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Abrir selector de Launcher") }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = { requireValidPin { onOpenSettings() } },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Abrir Ajustes del sistema") }

        OutlinedButton(
            onClick = { requireValidPin { onOpenAppInfo() } },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Información de esta app") }

        Spacer(Modifier.height(14.dp))
        TextButton(onClick = onCancel) { Text("Cancelar") }
    }
}
