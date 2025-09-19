package com.gvam.kioskportal.kiosk

import android.app.Activity
import android.content.Intent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gvam.kioskportal.ui.PortalTheme
import com.gvam.kioskportal.ui.MainActivity
import com.gvam.kioskportal.util.Pin
import com.gvam.kioskportal.kiosk.SystemDialogsKiller.closeAll
import com.gvam.kioskportal.kiosk.StatusBarBlocker

class AdminUnlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PortalTheme {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    AdminScreen(
                        onCancel = { finish() },
                        onExitTemp = {
                            KioskState.setMaintenance(this, true)
                            runCatching { (this as Activity).stopLockTask() }
                            closeAll(this); StatusBarBlocker.stop(this)
                            Toast.makeText(this, "Modo mantenimiento activado", Toast.LENGTH_SHORT).show()
                            finish()
                        },
                        onExitChangeLauncher = {
                            KioskState.setMaintenance(this, true)
                            runCatching { (this as Activity).stopLockTask() }
                            closeAll(this); StatusBarBlocker.stop(this)
                            startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            finish()
                        },
                        onReactivate = {
                            KioskState.setMaintenance(this, false)
                            startActivity(Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                            Toast.makeText(this, "Kiosco reactivado", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminScreen(
    onCancel: () -> Unit,
    onExitTemp: () -> Unit,
    onExitChangeLauncher: () -> Unit,
    onReactivate: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Acceso técnico", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 8) { pin = it.filter(Char::isDigit); error = null } },
                label = { Text("PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword
                )
            )
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onCancel) { Text("Cancelar") }
                Button(onClick = {
                    if (Pin.verifyPin(ctx, pin)) onExitTemp() else error = "PIN incorrecto"
                }) { Text("Salir temporal") }
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = {
                if (Pin.verifyPin(ctx, pin)) onExitChangeLauncher() else error = "PIN incorrecto"
            }) { Text("Salir y cambiar lanzador") }
            Spacer(Modifier.height(18.dp))
            TextButton(onClick = {
                if (Pin.verifyPin(ctx, pin)) onReactivate() else error = "PIN incorrecto"
            }) { Text("Reactivar kiosco", color = Color(0xFF2E7D32)) }
        }
    }
}