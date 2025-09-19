@file:OptIn(ExperimentalMaterial3Api::class)

package com.gvam.kioskportal.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gvam.kioskportal.BuildConfig
import com.gvam.kioskportal.R
import com.gvam.kioskportal.model.AppEntry
import com.gvam.kioskportal.util.Prefs
import com.gvam.kioskportal.util.Pin
import androidx.compose.foundation.clickable

/* ------------------- Activity (kiosco) ------------------- */

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        if (shouldRunKiosk()) enterKioskUi()

        setContent { PortalTheme { PortalScreen() } }
    }

    override fun onResume() {
        super.onResume()
        if (shouldRunKiosk()) enterKioskUi()
    }

    private fun shouldRunKiosk(): Boolean =
        (BuildConfig.IS_TOTEM || BuildConfig.KIOSK_FORCE) && !Prefs.isMaintenance(this)

    private fun enterKioskUi() {
        runCatching { startLockTask() }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

/* ------------------- UI raíz ------------------- */

private enum class AdminTarget { None, Menu, TechPanel }

@Composable
private fun PortalScreen() {
    val ctx = LocalContext.current
    val pm = ctx.packageManager

    var title by remember { mutableStateOf(Prefs.loadTitle(ctx) ?: "GVAM MDM") }
    var apps by remember { mutableStateOf(loadSelectedEntries(ctx)) }

    var gridView by rememberSaveable { mutableStateOf(true) }
    var showCreatePin by rememberSaveable { mutableStateOf(!Prefs.hasPin(ctx)) }
    var showVerifyPin by rememberSaveable { mutableStateOf(false) }
    var showTitleDialog by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var adminTarget by remember { mutableStateOf(AdminTarget.None) }

    // Atajo F12 × 3 (≤ 4 s)
    var f12FirstAt by remember { mutableStateOf(0L) }
    var f12Count by remember { mutableStateOf(0) }

    // Recarga al volver + acciones pendientes (servicio/USB)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                title = Prefs.loadTitle(ctx) ?: "GVAM MDM"
                apps = loadSelectedEntries(ctx)

                val sp = ctx.getSharedPreferences("portal_prefs", Context.MODE_PRIVATE)
                when (sp.getString("pending_action", null)) {
                    "open_admin" -> {
                        sp.edit().remove("pending_action").apply()
                        adminTarget = AdminTarget.TechPanel
                        showVerifyPin = true
                    }
                    "open_menu" -> {
                        sp.edit().remove("pending_action").apply()
                        if (Prefs.hasPin(ctx)) {
                            adminTarget = AdminTarget.Menu
                            showVerifyPin = true
                        } else menuOpen = true
                    }
                }

                if (!Prefs.hasPin(ctx)) showCreatePin = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { ev ->
                if (ev.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                    val code = ev.nativeKeyEvent.keyCode
                    val ctrlAlt = ev.isCtrlPressed && ev.isAltPressed

                    if (ctrlAlt && code == android.view.KeyEvent.KEYCODE_K) {
                        adminTarget = AdminTarget.TechPanel
                        showVerifyPin = true
                        return@onPreviewKeyEvent true
                    }

                    if (code == android.view.KeyEvent.KEYCODE_F12) {
                        val now = System.currentTimeMillis()
                        if (now - f12FirstAt > 4000) {
                            f12FirstAt = now; f12Count = 1
                        } else f12Count += 1
                        if (f12Count >= 3) {
                            f12Count = 0; f12FirstAt = 0L
                            adminTarget = AdminTarget.TechPanel
                            showVerifyPin = true
                        }
                        return@onPreviewKeyEvent true
                    }
                }
                false
            }
    ) {
        BackgroundLayer() // fondo + scrim

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopBar(
                    title = title,
                    gridView = gridView,
                    onToggleView = { gridView = !gridView },
                    menuOpen = menuOpen,
                    onOpenMenu = {
                        if (Prefs.hasPin(ctx)) {
                            adminTarget = AdminTarget.Menu
                            showVerifyPin = true
                        } else {
                            menuOpen = true
                        }
                    },
                    onDismissMenu = { menuOpen = false }
                ) {
                    MenuEntry("Configurar apps", Icons.Filled.Settings) {
                        menuOpen = false
                        ctx.startActivity(Intent(ctx, AppPickerActivity::class.java))
                    }
                    MenuEntry("Cambiar título", Icons.Filled.Edit) {
                        menuOpen = false
                        showTitleDialog = true
                    }
                    // (Wi-Fi eliminado)
                    if (!Prefs.isSettingsHidden(ctx)) {
                        // Abrir Ajustes pasando por AdminUnlock para evitar rebote
                        MenuEntry("Ajustes de Android", Icons.Filled.Settings) {
                            menuOpen = false
                            ctx.startActivity(
                                Intent(ctx, AdminUnlockActivity::class.java)
                                    .putExtra("target", "settings")
                            )
                        }
                    }
                    MenuEntry("Acerca de / Dispositivo", Icons.Filled.Info) {
                        menuOpen = false
                        ctx.startActivity(Intent(ctx, AboutActivity::class.java))
                    }
                }
            },
            contentWindowInsets = WindowInsets.safeDrawing
        ) { padding ->
            val cfg = LocalConfiguration.current
            val widthDp = cfg.screenWidthDp
            val columns = if (!gridView) 1 else when {
                widthDp >= 840 -> 4
                widthDp >= 600 -> 3
                cfg.orientation == Configuration.ORIENTATION_LANDSCAPE && widthDp >= 480 -> 3
                else -> 2
            }

            when {
                apps.isEmpty() -> {
                    Box(
                        Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center
                    ) { Text("No hay aplicaciones seleccionadas", color = Color.White) }
                }
                apps.size == 1 -> {
                    val only = apps.first()
                    SingleAppSpot(entry = only, padding = padding) {
                        pm.getLaunchIntentForPackage(only.packageName)
                            ?.let { ctx.startActivity(it) }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(apps) { entry ->
                            AppCard(entry = entry) {
                                pm.getLaunchIntentForPackage(entry.packageName)
                                    ?.let { ctx.startActivity(it) }
                            }
                        }
                    }
                }
            }
        }

        // Hot-corner (long-press)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(96.dp)
                .padding(8.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            adminTarget = AdminTarget.TechPanel
                            showVerifyPin = true
                        }
                    )
                }
        )
    }

    /* ---------- Diálogos ---------- */

    if (showCreatePin) {
        PinCreateDialog(
            onCancel = { /* bloquear cancelar si quieres forzar PIN */ },
            onConfirm = { newPin ->
                Pin.savePin(ctx, newPin)
                showCreatePin = false
                Toast.makeText(ctx, "PIN creado", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showVerifyPin) {
        PinVerifyDialog(
            onCancel = { showVerifyPin = false },
            onResult = { ok ->
                showVerifyPin = false
                if (ok) {
                    when (adminTarget) {
                        AdminTarget.Menu -> menuOpen = true
                        AdminTarget.TechPanel -> ctx.startActivity(
                            Intent(ctx, AdminUnlockActivity::class.java)
                        )
                        else -> {}
                    }
                } else {
                    Toast.makeText(ctx, "PIN incorrecto", Toast.LENGTH_SHORT).show()
                }
                adminTarget = AdminTarget.None
            }
        )
    }

    if (showTitleDialog) {
        TitleDialog(
            initial = title,
            onCancel = { showTitleDialog = false },
            onSave = { newTitle ->
                Prefs.saveTitle(ctx, newTitle.trim())
                title = Prefs.loadTitle(ctx) ?: "GVAM MDM"
                showTitleDialog = false
            }
        )
    }
}

/* ------------------- TopBar + Menú anclado (bordes 16.dp) ------------------- */

@Composable
private fun TopBar(
    title: String,
    gridView: Boolean,
    onToggleView: () -> Unit,
    menuOpen: Boolean,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    menuContent: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 10.dp)
            .height(66.dp)
            .clip(MaterialTheme.shapes.extraLarge),
        // ↑ más opacidad en la barra
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f)
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cambiar vista
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onToggleView() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (gridView) Icons.Filled.ViewList else Icons.Filled.ViewModule,
                    contentDescription = "Cambiar vista",
                    tint = Color.White
                )
            }

            Spacer(Modifier.width(8.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )

            // Botón ⋮ con menú anclado y esquinas redondeadas
            Box(
                modifier = Modifier
                    .wrapContentSize(Alignment.TopEnd)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onOpenMenu() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Menú",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )

                val menuShape = RoundedCornerShape(16.dp)
                MaterialTheme(
                    colorScheme = MaterialTheme.colorScheme,
                    typography  = MaterialTheme.typography,
                    shapes      = MaterialTheme.shapes.copy(extraSmall = menuShape)
                ) {
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = onDismissMenu,
                        offset = DpOffset(x = (-8).dp, y = 0.dp),
                        content = menuContent
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuEntry(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick
    )
}

/* ------------------- Tarjetas de apps (solo icono) ------------------- */

@Composable
private fun AppCard(
    entry: AppEntry,
    onClick: () -> Unit
) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val icon = remember(entry.packageName) { loadAppIconImage(ctx, pm, entry.packageName) }
    val shape = MaterialTheme.shapes.extraLarge

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.15f)
            .clip(shape)
            .clickable { onClick() },
        // ↑ más opacidad en las tarjetas
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f),
        shape = shape
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = entry.label,
                    modifier = Modifier.size(56.dp),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.Medium
                )
            }
        }
    }
}

@Composable
private fun SingleAppSpot(
    entry: AppEntry,
    padding: PaddingValues,
    onClick: () -> Unit
) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val icon = remember(entry.packageName) { loadAppIconImage(ctx, pm, entry.packageName) }
    val shape = MaterialTheme.shapes.extraLarge

    Box(
        Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .aspectRatio(1.1f)
                .clip(shape)
                .clickable { onClick() },
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f),
            shape = shape
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = entry.label,
                        modifier = Modifier.size(96.dp),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.Medium
                    )
                }
            }
        }
    }
}

/* ------------------- Fondo con scrim ------------------- */

@Composable
private fun BackgroundLayer() {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.kiosk_default_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // Scrim suave para “subir opacidad” y mejorar contraste
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.10f))
        )
    }
}

/* ------------------- Diálogos ------------------- */

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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = p2,
                    onValueChange = { if (it.length <= 8) p2 = it.filter(Char::isDigit) },
                    singleLine = true,
                    label = { Text("Repite PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (p1.isNotEmpty() && p2.isNotEmpty() && !ok) {
                    Text("Los PIN no coinciden o longitud inválida", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (ok) onConfirm(p1) }, enabled = ok) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } },
        properties = DialogProperties(dismissOnClickOutside = false)
    )
}

@Composable
private fun PinVerifyDialog(
    onCancel: () -> Unit,
    onResult: (Boolean) -> Unit
) {
    val ctx = LocalContext.current
    var pin by rememberSaveable { mutableStateOf("") }
    val ok = pin.length in 4..8

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("PIN de administrador") },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 8) pin = it.filter(Char::isDigit) },
                singleLine = true,
                label = { Text("Introduce el PIN") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = { onResult(Pin.verifyPin(ctx, pin)) }) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } },
        properties = DialogProperties(dismissOnClickOutside = false)
    )
}

@Composable
private fun TitleDialog(
    initial: String,
    onCancel: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by rememberSaveable { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Cambiar título") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("Título del portal") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value.trim()) }, enabled = value.isNotBlank()) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } },
        properties = DialogProperties(dismissOnClickOutside = false)
    )
}

/* ------------------- Datos ------------------- */

private fun loadSelectedEntries(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val selected = Prefs.loadSelectedPackages(ctx)
    return selected.map { pkg ->
        val label = try {
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (_: Exception) { pkg }
        AppEntry(pkg, label)
    }
}

/** Carga icono de app como ImageBitmap (128×128). */
private fun loadAppIconImage(
    ctx: Context,
    pm: PackageManager,
    pkg: String
) = runCatching {
    val d = pm.getApplicationIcon(pkg)
    val bmp = (d as? BitmapDrawable)?.bitmap
        ?: d.toBitmap(128, 128, Bitmap.Config.ARGB_8888)
    bmp.asImageBitmap()
}.getOrNull()
