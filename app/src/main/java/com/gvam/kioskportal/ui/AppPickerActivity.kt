package com.gvam.kioskportal.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.core.graphics.drawable.toBitmap
import com.gvam.kioskportal.R
import com.gvam.kioskportal.util.Prefs

/* ---------- Activity ---------- */

class AppPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PortalTheme { AppPickerScreen(onDone = { finish() }) } }
    }
}

/* ---------- Datos + helpers ---------- */

private data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Bitmap,
    val isSystem: Boolean
)

private enum class AppFilter { ALL, USER_ONLY }

/** queryIntentActivities compatible con SDK 33+ */
@Suppress("DEPRECATION")
private fun PackageManager.queryIntentActivitiesCompat(
    intent: Intent,
    flags: Int = 0 // ← IMPORTANTE: sin MATCH_DEFAULT_ONLY
): List<ResolveInfo> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
    } else {
        queryIntentActivities(intent, flags)
    }
}

private fun isSystemApp(pm: PackageManager, pkg: String): Boolean = try {
    val ai = pm.getApplicationInfo(pkg, 0)
    (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
} catch (_: Exception) { false }

/** Carga TODAS las apps lanzables (usuario + sistema) */
private fun loadAllLaunchableApps(pm: PackageManager): List<AppInfo> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val list = pm.queryIntentActivitiesCompat(intent, 0) // ← sin filtro

    val mapped = list.map { ri ->
        val pkg = ri.activityInfo.packageName
        val label = ri.loadLabel(pm)?.toString().orEmpty()
        val drawable = ri.loadIcon(pm)
        val bmp = (drawable as? BitmapDrawable)?.bitmap
            ?: drawable.toBitmap(width = 128, height = 128)
        AppInfo(
            packageName = pkg,
            label = label,
            icon = bmp,
            isSystem = isSystemApp(pm, pkg)
        )
    }

    return mapped
        .distinctBy { it.packageName }
        .sortedWith(
            compareBy<AppInfo> { it.isSystem }    // usuario primero
                .thenBy { it.label.lowercase() }
        )
}

/* ---------- UI ---------- */

@Composable
private fun AppPickerScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager

    // Lista de apps (recargable)
    var allApps by remember { mutableStateOf(emptyList<AppInfo>()) }

    // Carga inicial
    LaunchedEffect(Unit) {
        allApps = loadAllLaunchableApps(pm)
    }

    // Recarga automática al volver a primer plano
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                allApps = loadAllLaunchableApps(pm)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // Filtro (todas / solo usuario)
    var filter by remember { mutableStateOf(AppFilter.ALL) }
    val visibleApps = remember(allApps, filter) {
        when (filter) {
            AppFilter.ALL -> allApps
            AppFilter.USER_ONLY -> allApps.filter { !it.isSystem }
        }
    }

    // Selección persistente
    val selected = remember {
        mutableStateListOf<String>().apply { addAll(Prefs.loadSelectedPackages(ctx)) }
    }

    Box(Modifier.fillMaxSize()) {
        BackgroundLayer()  // recurso fijo de fondo

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GlassTopBar(
                    title = "Seleccionar apps",
                    count = selected.size,
                    onClose = onDone,
                    onSave = {
                        Prefs.saveSelectedPackages(ctx, selected.toSet())
                        onDone()
                    },
                    onRefresh = { allApps = loadAllLaunchableApps(pm) } // botón refrescar
                )
            },
            contentWindowInsets = WindowInsets.safeDrawing
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                // Filtros
                FilterRow(current = filter, onChange = { filter = it })

                // Grid de apps
                val cfg = LocalConfiguration.current
                val columns = when {
                    cfg.screenWidthDp >= 1000 -> 5
                    cfg.screenWidthDp >= 840  -> 4
                    cfg.screenWidthDp >= 600  -> 3
                    else -> 2
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(visibleApps, key = { it.packageName }) { app ->
                        val isSelected = app.packageName in selected
                        AppPickTile(
                            app = app,
                            selected = isSelected,
                            onClick = {
                                if (isSelected) selected.remove(app.packageName)
                                else selected.add(app.packageName)
                            }
                        )
                    }
                }
            }
        }
    }
}

/* ---------- Controles de cabecera y filtros ---------- */

@Composable
private fun GlassTopBar(
    title: String,
    count: Int,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onRefresh: () -> Unit,
    height: Dp = 66.dp
) {
    val shape  = MaterialTheme.shapes.extraLarge
    val bg     = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f)
    val stroke = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 10.dp)
            .height(height)
            .clip(shape)
            .border(1.dp, stroke, shape),
        color = bg
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Seleccionadas: $count",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB2FF59),
                    maxLines = 1
                )
            }

            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refrescar", tint = Color.White)
            }

            Button(
                onClick = onSave,
                shape = RoundedCornerShape(999.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF43A047),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Guardar")
            }
        }
    }
}

@Composable
private fun FilterRow(
    current: AppFilter,
    onChange: (AppFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = current == AppFilter.ALL,
            onClick = { onChange(AppFilter.ALL) },
            label = { Text("Todas") }
        )
        FilterChip(
            selected = current == AppFilter.USER_ONLY,
            onClick = { onChange(AppFilter.USER_ONLY) },
            label = { Text("Solo usuario") }
        )
    }
}

/* ---------- Celdas ---------- */

@Composable
private fun AppPickTile(
    app: AppInfo,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = MaterialTheme.shapes.extraLarge
    val borderColor = if (selected) Color(0xFF7CB342) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val borderWidth = if (selected) 2.dp else 1.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.15f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.28f), shape)
            .border(borderWidth, borderColor, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                bitmap = app.icon.asImageBitmap(),
                contentDescription = app.label,
                modifier = Modifier.size(52.dp),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.Medium
            )
            // Sin texto bajo icono para que se vea limpio en el picker
        }

        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color(0xFF7CB342),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )
        }
    }
}

/* ---------- Fondo FIJO ---------- */

@Composable
private fun BackgroundLayer() {
    Image(
        painter = painterResource(R.drawable.kiosk_default_bg),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    )
}
