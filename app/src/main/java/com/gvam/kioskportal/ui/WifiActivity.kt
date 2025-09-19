package com.gvam.kioskportal.ui

import android.Manifest
import android.content.*
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
class WifiActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                val ctx = LocalContext.current
                val wifi = remember {
                    ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                }

                var results by remember { mutableStateOf(listOf<ScanResult>()) }
                var scanning by remember { mutableStateOf(false) }
                var showPwdFor by remember { mutableStateOf<ScanResult?>(null) }
                var password by remember { mutableStateOf("") }

                // Receiver para resultados de escaneo
                DisposableEffect(Unit) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            results = wifi.scanResults?.filter { !it.SSID.isNullOrBlank() }?.sortedByDescending { it.level } ?: emptyList()
                            scanning = false
                        }
                    }
                    registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
                    onDispose {
                        runCatching { unregisterReceiver(receiver) }
                    }
                }

                // Permisos de ubicación / nearby (según API)
                val neededPerms = remember {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
                    } else {
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }
                val hasPerms = neededPerms.all {
                    checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                val permLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { grantedMap ->
                    val granted = grantedMap.values.all { it }
                    if (!granted) {
                        Toast.makeText(ctx, "Permisos requeridos para escanear redes.", Toast.LENGTH_LONG).show()
                        ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    } else {
                        startScan(wifi) { scanning = it }
                    }
                }

                fun ensurePermsThenScan() {
                    if (hasPerms) startScan(wifi) { scanning = it }
                    else permLauncher.launch(neededPerms)
                }

                fun connectTo(scan: ScanResult, pwd: String?) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val builder = WifiNetworkSuggestion.Builder().setSsid(scan.SSID)
                        val open = scan.capabilities.isNullOrBlank() ||
                                (!scan.capabilities.contains("WEP") &&
                                        !scan.capabilities.contains("WPA"))
                        if (!open && !pwd.isNullOrEmpty()) builder.setWpa2Passphrase(pwd)
                        val status = wifi.addNetworkSuggestions(listOf(builder.build()))
                        val msg = if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS)
                            "Sugerencia enviada. Android decidirá la conexión."
                        else "Error al sugerir red (código $status)"
                        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                    } else {
                        @Suppress("DEPRECATION")
                        val conf = WifiConfiguration().apply {
                            SSID = "\"${scan.SSID}\""
                            val open = scan.capabilities.isNullOrBlank() ||
                                    (!scan.capabilities.contains("WEP") &&
                                            !scan.capabilities.contains("WPA"))
                            if (open) {
                                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                            } else {
                                preSharedKey = "\"$pwd\""
                            }
                        }
                        @Suppress("DEPRECATION")
                        val net = wifi.addNetwork(conf)
                        @Suppress("DEPRECATION")
                        val ok = wifi.enableNetwork(net, true)
                        Toast.makeText(
                            ctx,
                            if (ok) "Intentando conectar (legacy)" else "No se pudo conectar (legacy)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                // UI
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Wi-Fi") },
                            navigationIcon = {
                                TextButton(onClick = { finish() }) { Text("Atrás") }
                            },
                            actions = {
                                TextButton(onClick = { ensurePermsThenScan() }) {
                                    Text(if (scanning) "Buscando…" else "Escanear")
                                }
                            }
                        )
                    }
                ) { padding ->
                    Column(
                        Modifier
                            .padding(padding)
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        if (!wifi.isWifiEnabled) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Wi-Fi está desactivado")
                                Button(onClick = {
                                    runCatching { wifi.isWifiEnabled = true }
                                    ensurePermsThenScan()
                                }) { Text("Activar") }
                            }
                        }

                        if (results.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(if (scanning) "Buscando redes…" else "Sin redes disponibles")
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(results, key = { it.BSSID ?: it.SSID }) { r ->
                                    WifiRow(
                                        result = r,
                                        onClick = {
                                            val open = r.capabilities.isNullOrBlank() ||
                                                    (!r.capabilities.contains("WEP") &&
                                                            !r.capabilities.contains("WPA"))
                                            if (open) connectTo(r, null)
                                            else { showPwdFor = r; password = "" }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Diálogo para contraseña
                    val target = showPwdFor
                    if (target != null) {
                        AlertDialog(
                            onDismissRequest = { showPwdFor = null },
                            title = { Text("Conectar a ${target.SSID}") },
                            text = {
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    placeholder = { Text("Contraseña") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    if (password.isBlank()) {
                                        Toast.makeText(ctx, "La contraseña no puede estar vacía", Toast.LENGTH_SHORT).show()
                                    } else {
                                        connectTo(target, password)
                                        showPwdFor = null
                                    }
                                }) { Text("Conectar") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showPwdFor = null }) { Text("Cancelar") }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun startScan(wifi: WifiManager, setScanning: (Boolean) -> Unit) {
        setScanning(true)
        val ok = wifi.startScan()
        if (!ok) setScanning(false)
    }
}

@Composable
private fun WifiRow(result: ScanResult, onClick: () -> Unit) {
    val secure = when {
        result.capabilities.contains("WPA3") -> "WPA3"
        result.capabilities.contains("WPA2") -> "WPA2"
        result.capabilities.contains("WPA")  -> "WPA"
        result.capabilities.contains("WEP")  -> "WEP"
        else -> "Abierta"
    }
    Card(onClick = onClick) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(result.SSID, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text("$secure • RSSI ${result.level} dBm", style = MaterialTheme.typography.bodySmall)
            }
            Text("Conectar", color = MaterialTheme.colorScheme.primary)
        }
    }
}
