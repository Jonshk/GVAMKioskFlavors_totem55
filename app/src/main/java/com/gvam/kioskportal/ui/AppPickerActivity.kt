@file:OptIn(ExperimentalMaterial3Api::class)

package com.gvam.kioskportal.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.gvam.kioskportal.R
import com.gvam.kioskportal.model.AppDisplayConfig
import com.gvam.kioskportal.model.CardStyle
import com.gvam.kioskportal.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/* =========================================================
   ACTIVITY
   ========================================================= */

class AppPickerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            PortalTheme {
                AppPickerScreen(
                    onDone = {
                        finish()
                    }
                )
            }
        }
    }
}

/* =========================================================
   DATOS
   ========================================================= */

private data class AppInfo(
    val packageName: String,
    val label: String,
    val originalIcon: Bitmap,
    val isSystem: Boolean
)

private enum class AppFilter {
    ALL,
    USER_ONLY,
    SELECTED
}

/* =========================================================
   PACKAGE MANAGER
   ========================================================= */

@Suppress("DEPRECATION")
private fun PackageManager.queryIntentActivitiesCompat(
    intent: Intent,
    flags: Int = 0
): List<ResolveInfo> {
    return if (
        Build.VERSION.SDK_INT >=
        Build.VERSION_CODES.TIRAMISU
    ) {
        queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(
                flags.toLong()
            )
        )
    } else {
        queryIntentActivities(
            intent,
            flags
        )
    }
}

@Suppress("DEPRECATION")
private fun isSystemApp(
    packageManager: PackageManager,
    packageName: String
): Boolean {
    return runCatching {
        val applicationInfo =
            packageManager.getApplicationInfo(
                packageName,
                0
            )

        (
            applicationInfo.flags and
                ApplicationInfo.FLAG_SYSTEM
            ) != 0
    }.getOrDefault(false)
}

private fun loadAllLaunchableApps(
    packageManager: PackageManager,
    excludedPackage: String
): List<AppInfo> {
    val launcherIntent =
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(
                Intent.CATEGORY_LAUNCHER
            )
        }

    return packageManager
        .queryIntentActivitiesCompat(
            launcherIntent,
            0
        )
        .mapNotNull { resolveInfo ->
            runCatching {
                val packageName =
                    resolveInfo
                        .activityInfo
                        .packageName

                if (
                    packageName ==
                    excludedPackage
                ) {
                    return@runCatching null
                }

                val label =
                    resolveInfo
                        .loadLabel(packageManager)
                        .toString()
                        .trim()
                        .ifBlank {
                            packageName
                        }

                val drawable =
                    resolveInfo.loadIcon(
                        packageManager
                    )

                val bitmap =
                    (
                        drawable as?
                            BitmapDrawable
                        )?.bitmap
                        ?: drawable.toBitmap(
                            width = 160,
                            height = 160,
                            config =
                                Bitmap.Config.ARGB_8888
                        )

                AppInfo(
                    packageName =
                        packageName,
                    label =
                        label,
                    originalIcon =
                        bitmap,
                    isSystem =
                        isSystemApp(
                            packageManager,
                            packageName
                        )
                )
            }.getOrNull()
        }
        .distinctBy {
            it.packageName
        }
        .sortedWith(
            compareBy<AppInfo> {
                it.isSystem
            }.thenBy {
                it.label.lowercase()
            }
        )
}

/* =========================================================
   ICONOS PERSONALIZADOS
   ========================================================= */

@Suppress("DEPRECATION")
private fun loadBitmapFromUri(
    context: Context,
    uriText: String?
): Bitmap? {
    if (uriText.isNullOrBlank()) {
        return null
    }

    return runCatching {
        val uri =
            Uri.parse(uriText)

        val bitmap =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.P
            ) {
                val source =
                    ImageDecoder.createSource(
                        context.contentResolver,
                        uri
                    )

                ImageDecoder.decodeBitmap(
                    source
                )
            } else {
                MediaStore.Images.Media
                    .getBitmap(
                        context.contentResolver,
                        uri
                    )
            }

        resizeBitmapIfNeeded(
            bitmap = bitmap,
            maximumSize = 512
        )
    }.getOrNull()
}

private fun resizeBitmapIfNeeded(
    bitmap: Bitmap,
    maximumSize: Int
): Bitmap {
    val largestSide =
        maxOf(
            bitmap.width,
            bitmap.height
        )

    if (largestSide <= maximumSize) {
        return bitmap
    }

    val scale =
        maximumSize.toFloat() /
            largestSide.toFloat()

    val newWidth =
        (bitmap.width * scale)
            .roundToInt()
            .coerceAtLeast(1)

    val newHeight =
        (bitmap.height * scale)
            .roundToInt()
            .coerceAtLeast(1)

    return Bitmap.createScaledBitmap(
        bitmap,
        newWidth,
        newHeight,
        true
    )
}

/* =========================================================
   PANTALLA PRINCIPAL
   ========================================================= */

@Composable
private fun AppPickerScreen(
    onDone: () -> Unit
) {
    val context =
        LocalContext.current

    val packageManager =
        context.packageManager

    val lifecycleOwner =
        LocalLifecycleOwner.current

    val coroutineScope =
        rememberCoroutineScope()

    val globalAppearance =
        remember {
            Prefs.loadAppearance(context)
        }

    var allApps by remember {
        mutableStateOf(
            emptyList<AppInfo>()
        )
    }

    var selectedOrder by remember {
        mutableStateOf(
            Prefs.loadSelectedPackagesOrdered(
                context
            )
        )
    }

    var appConfigs by remember {
        mutableStateOf(
            Prefs.loadAppDisplayConfigs(
                context
            )
        )
    }

    var searchText by remember {
        mutableStateOf("")
    }

    var filter by remember {
        mutableStateOf(
            AppFilter.ALL
        )
    }

    var loading by remember {
        mutableStateOf(true)
    }

    var editingApp by remember {
        mutableStateOf<AppInfo?>(null)
    }

    var editingConfig by remember {
        mutableStateOf<AppDisplayConfig?>(null)
    }

    var showDiscardDialog by remember {
        mutableStateOf(false)
    }

    fun reloadApps() {
        coroutineScope.launch {
            loading = true

            allApps =
                withContext(
                    Dispatchers.Default
                ) {
                    loadAllLaunchableApps(
                        packageManager =
                            packageManager,
                        excludedPackage =
                            context.packageName
                    )
                }

            loading = false
        }
    }

    fun openEditor(app: AppInfo) {
        val currentIndex =
            selectedOrder
                .indexOf(
                    app.packageName
                )
                .coerceAtLeast(0)

        val storedConfig =
            appConfigs[
                app.packageName
            ] ?: AppDisplayConfig(
                packageName =
                    app.packageName,
                position =
                    currentIndex
            )

        editingApp = app

        editingConfig =
            storedConfig.copy(
                position =
                    currentIndex
            )
    }

    fun selectApp(app: AppInfo) {
        if (
            app.packageName in
            selectedOrder
        ) {
            openEditor(app)
            return
        }

        selectedOrder =
            selectedOrder +
                app.packageName

        val config =
            appConfigs[
                app.packageName
            ] ?: AppDisplayConfig(
                packageName =
                    app.packageName,
                position =
                    selectedOrder.lastIndex
            )

        appConfigs =
            appConfigs +
                (
                    app.packageName to
                        config.copy(
                            position =
                                selectedOrder.lastIndex
                        )
                    )
    }

    fun removeApp(
        packageName: String
    ) {
        selectedOrder =
            selectedOrder.filterNot {
                it == packageName
            }
    }

    fun saveEverything() {
        Prefs.saveSelectedPackagesOrdered(
            context,
            selectedOrder
        )

        selectedOrder.forEachIndexed {
                index,
                packageName ->

            val current =
                appConfigs[packageName]
                    ?: AppDisplayConfig(
                        packageName =
                            packageName
                    )

            val updated =
                current.copy(
                    position = index
                )

            Prefs.saveAppDisplayConfig(
                context,
                updated
            )
        }

        onDone()
    }

    val iconPicker =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .OpenDocument()
        ) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver
                        .takePersistableUriPermission(
                            uri,
                            Intent
                                .FLAG_GRANT_READ_URI_PERMISSION
                        )
                }

                editingConfig =
                    editingConfig?.copy(
                        customIconUri =
                            uri.toString()
                    )
            }
        }

    LaunchedEffect(Unit) {
        reloadApps()
    }

    DisposableEffect(
        lifecycleOwner
    ) {
        val observer =
            object :
                DefaultLifecycleObserver {

                override fun onResume(
                    owner: LifecycleOwner
                ) {
                    reloadApps()
                }
            }

        lifecycleOwner.lifecycle
            .addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle
                .removeObserver(observer)
        }
    }

    val visibleApps =
        remember(
            allApps,
            selectedOrder,
            appConfigs,
            searchText,
            filter
        ) {
            val normalizedSearch =
                searchText
                    .trim()
                    .lowercase()

            allApps
                .asSequence()
                .filter { app ->
                    when (filter) {
                        AppFilter.ALL ->
                            true

                        AppFilter.USER_ONLY ->
                            !app.isSystem

                        AppFilter.SELECTED ->
                            app.packageName in
                                selectedOrder
                    }
                }
                .filter { app ->
                    normalizedSearch.isBlank() ||
                        app.label
                            .lowercase()
                            .contains(
                                normalizedSearch
                            ) ||
                        app.packageName
                            .lowercase()
                            .contains(
                                normalizedSearch
                            ) ||
                        (
                            appConfigs[
                                app.packageName
                            ]?.customLabel
                                ?.lowercase()
                                ?.contains(
                                    normalizedSearch
                                ) == true
                            )
                }
                .sortedWith(
                    compareByDescending<AppInfo> {
                        it.packageName in
                            selectedOrder
                    }.thenBy { app ->
                        selectedOrder
                            .indexOf(
                                app.packageName
                            )
                            .takeIf {
                                it >= 0
                            }
                            ?: Int.MAX_VALUE
                    }.thenBy {
                        it.label.lowercase()
                    }
                )
                .toList()
        }

    Box(
        modifier =
            Modifier.fillMaxSize()
    ) {
        PickerBackground()

        Scaffold(
            containerColor =
                Color.Transparent,
            contentWindowInsets =
                WindowInsets.safeDrawing,
            topBar = {
                PickerTopBar(
                    selectedCount =
                        selectedOrder.size,
                    onClose = {
                        showDiscardDialog = true
                    },
                    onRefresh = {
                        reloadApps()
                    }
                )
            },
            bottomBar = {
                PickerBottomBar(
                    selectedCount =
                        selectedOrder.size,
                    onSave = {
                        saveEverything()
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                SearchAndFilters(
                    searchText =
                        searchText,
                    onSearchChange = {
                        searchText = it
                    },
                    currentFilter =
                        filter,
                    onFilterChange = {
                        filter = it
                    },
                    resultCount =
                        visibleApps.size
                )

                when {
                    loading -> {
                        LoadingState()
                    }

                    visibleApps.isEmpty() -> {
                        EmptyResultState(
                            searchActive =
                                searchText
                                    .isNotBlank(),
                            filter =
                                filter
                        )
                    }

                    else -> {
                        AppPickerGrid(
                            apps =
                                visibleApps,
                            selectedOrder =
                                selectedOrder,
                            appConfigs =
                                appConfigs,
                            onPrimaryClick = {
                                app ->
                                selectApp(app)
                            },
                            onEdit = {
                                app ->
                                openEditor(app)
                            },
                            onRemove = {
                                packageName ->
                                removeApp(
                                    packageName
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    val currentApp =
        editingApp

    val currentConfig =
        editingConfig

    if (
        currentApp != null &&
        currentConfig != null
    ) {
        AppEditDialog(
            app =
                currentApp,
            config =
                currentConfig,
            globalIconSize =
                globalAppearance.iconSize,
            selectedCount =
                selectedOrder.size,
            onConfigChange = {
                editingConfig = it
            },
            onChooseIcon = {
                iconPicker.launch(
                    arrayOf("image/*")
                )
            },
            onReset = {
                editingConfig =
                    AppDisplayConfig(
                        packageName =
                            currentApp
                                .packageName,
                        position =
                            selectedOrder
                                .indexOf(
                                    currentApp
                                        .packageName
                                )
                                .coerceAtLeast(0)
                    )
            },
            onCancel = {
                editingApp = null
                editingConfig = null
            },
            onSave = {
                updatedConfig ->

                val oldOrder =
                    selectedOrder
                        .filterNot {
                            it ==
                                updatedConfig
                                    .packageName
                        }
                        .toMutableList()

                val insertPosition =
                    updatedConfig
                        .position
                        .coerceIn(
                            0,
                            oldOrder.size
                        )

                oldOrder.add(
                    insertPosition,
                    updatedConfig
                        .packageName
                )

                selectedOrder =
                    oldOrder

                val finalConfig =
                    updatedConfig.copy(
                        position =
                            insertPosition
                    )

                appConfigs =
                    appConfigs +
                        (
                            finalConfig
                                .packageName to
                                finalConfig
                            )

                Prefs.saveAppDisplayConfig(
                    context,
                    finalConfig
                )

                editingApp = null
                editingConfig = null
            }
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = {
                showDiscardDialog = false
            },
            title = {
                Text(
                    "Cerrar sin guardar"
                )
            },
            text = {
                Text(
                    "Los cambios realizados en la selección de aplicaciones podrían perderse."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog =
                            false

                        onDone()
                    }
                ) {
                    Text(
                        "Cerrar"
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog =
                            false
                    }
                ) {
                    Text(
                        "Cancelar"
                    )
                }
            }
        )
    }
}

/* =========================================================
   CABECERA
   ========================================================= */

@Composable
private fun PickerTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onRefresh: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                horizontal = 10.dp,
                vertical = 4.dp
            )
            .height(70.dp),
        shape =
            RoundedCornerShape(25.dp),
        color =
            Color(0xE3344E25),
        contentColor =
            Color.White,
        tonalElevation = 4.dp,
        shadowElevation = 3.dp,
        border = BorderStroke(
            width = 1.dp,
            color =
                Color.White.copy(
                    alpha = 0.18f
                )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 8.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier =
                    Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector =
                        Icons.Filled.Close,
                    contentDescription =
                        "Cerrar",
                    tint =
                        Color.White
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        horizontal = 6.dp
                    )
            ) {
                Text(
                    text =
                        "Configurar aplicaciones",
                    style =
                        MaterialTheme
                            .typography
                            .titleLarge,
                    fontWeight =
                        FontWeight.Bold,
                    color =
                        Color.White,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis
                )

                Text(
                    text =
                        if (
                            selectedCount == 1
                        ) {
                            "1 aplicación seleccionada"
                        } else {
                            "$selectedCount aplicaciones seleccionadas"
                        },
                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,
                    color =
                        Color(0xFFD5F6B6)
                )
            }

            IconButton(
                onClick = onRefresh,
                modifier =
                    Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector =
                        Icons.Filled.Refresh,
                    contentDescription =
                        "Actualizar aplicaciones",
                    tint =
                        Color.White
                )
            }
        }
    }
}

/* =========================================================
   BUSCADOR Y FILTROS
   ========================================================= */

@Composable
private fun SearchAndFilters(
    searchText: String,
    onSearchChange: (String) -> Unit,
    currentFilter: AppFilter,
    onFilterChange: (AppFilter) -> Unit,
    resultCount: Int
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 12.dp,
                vertical = 8.dp
            ),
        shape =
            RoundedCornerShape(24.dp),
        color =
            Color.White.copy(
                alpha = 0.95f
            ),
        tonalElevation = 2.dp,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier =
                Modifier.padding(12.dp)
        ) {
            OutlinedTextField(
                value =
                    searchText,
                onValueChange =
                    onSearchChange,
                modifier =
                    Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector =
                            Icons.Filled.Search,
                        contentDescription =
                            null
                    )
                },
                trailingIcon = {
                    if (
                        searchText
                            .isNotBlank()
                    ) {
                        IconButton(
                            onClick = {
                                onSearchChange("")
                            }
                        ) {
                            Icon(
                                imageVector =
                                    Icons.Filled.Close,
                                contentDescription =
                                    "Borrar búsqueda"
                            )
                        }
                    }
                },
                placeholder = {
                    Text(
                        "Buscar aplicación"
                    )
                },
                supportingText = {
                    Text(
                        if (
                            resultCount == 1
                        ) {
                            "1 resultado"
                        } else {
                            "$resultCount resultados"
                        }
                    )
                },
                shape =
                    RoundedCornerShape(
                        18.dp
                    ),
                colors =
                    OutlinedTextFieldDefaults
                        .colors(
                            focusedContainerColor =
                                Color.White,
                            unfocusedContainerColor =
                                Color.White
                        )
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(
                        rememberScrollState()
                    ),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        8.dp
                    )
            ) {
                PickerFilterChip(
                    selected =
                        currentFilter ==
                            AppFilter.ALL,
                    text =
                        "Todas",
                    onClick = {
                        onFilterChange(
                            AppFilter.ALL
                        )
                    }
                )

                PickerFilterChip(
                    selected =
                        currentFilter ==
                            AppFilter.USER_ONLY,
                    text =
                        "Usuario",
                    onClick = {
                        onFilterChange(
                            AppFilter.USER_ONLY
                        )
                    }
                )

                PickerFilterChip(
                    selected =
                        currentFilter ==
                            AppFilter.SELECTED,
                    text =
                        "Seleccionadas",
                    onClick = {
                        onFilterChange(
                            AppFilter.SELECTED
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun PickerFilterChip(
    selected: Boolean,
    text: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected =
            selected,
        onClick =
            onClick,
        label = {
            Text(text)
        },
        leadingIcon = {
            if (selected) {
                Icon(
                    imageVector =
                        Icons.Filled.Check,
                    contentDescription =
                        null,
                    modifier =
                        Modifier.size(
                            17.dp
                        )
                )
            }
        },
        colors =
            FilterChipDefaults
                .filterChipColors(
                    selectedContainerColor =
                        MaterialTheme
                            .colorScheme
                            .primaryContainer
                )
    )
}

/* =========================================================
   CUADRÍCULA
   ========================================================= */

@Composable
private fun AppPickerGrid(
    apps: List<AppInfo>,
    selectedOrder: List<String>,
    appConfigs: Map<String, AppDisplayConfig>,
    onPrimaryClick: (AppInfo) -> Unit,
    onEdit: (AppInfo) -> Unit,
    onRemove: (String) -> Unit
) {
    val configuration =
        LocalConfiguration.current

    val columns =
        when {
            configuration
                .screenWidthDp >= 1000 ->
                5

            configuration
                .screenWidthDp >= 840 ->
                4

            configuration
                .screenWidthDp >= 600 ->
                3

            else ->
                2
        }

    LazyVerticalGrid(
        columns =
            GridCells.Fixed(columns),
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = 12.dp
            ),
        contentPadding =
            PaddingValues(
                top = 4.dp,
                bottom = 18.dp
            ),
        verticalArrangement =
            Arrangement.spacedBy(
                12.dp
            ),
        horizontalArrangement =
            Arrangement.spacedBy(
                12.dp
            )
    ) {
        items(
            items = apps,
            key = {
                it.packageName
            }
        ) { app ->
            val selected =
                app.packageName in
                    selectedOrder

            val config =
                appConfigs[
                    app.packageName
                ] ?: AppDisplayConfig(
                    packageName =
                        app.packageName,
                    position =
                        selectedOrder
                            .indexOf(
                                app.packageName
                            )
                            .takeIf {
                                it >= 0
                            }
                            ?: Int.MAX_VALUE
                )

            AppPickerTile(
                app =
                    app,
                config =
                    config,
                selected =
                    selected,
                order =
                    selectedOrder
                        .indexOf(
                            app.packageName
                        ),
                onPrimaryClick = {
                    onPrimaryClick(app)
                },
                onEdit = {
                    onEdit(app)
                },
                onRemove = {
                    onRemove(
                        app.packageName
                    )
                }
            )
        }
    }
}

/* =========================================================
   TARJETA
   ========================================================= */

@Composable
private fun AppPickerTile(
    app: AppInfo,
    config: AppDisplayConfig,
    selected: Boolean,
    order: Int,
    onPrimaryClick: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    val context =
        LocalContext.current

    val customBitmap =
        remember(
            config.customIconUri
        ) {
            loadBitmapFromUri(
                context =
                    context,
                uriText =
                    config.customIconUri
            )
        }

    val previewBitmap =
        customBitmap
            ?: app.originalIcon

    val shape =
        RoundedCornerShape(22.dp)

    val previewDescription =
        when {
            !config.showLabel ->
                "Se mostrará sin nombre"

            config
                .resolveLabel(app.label)
                .isBlank() ->
                "Se mostrará sin nombre"

            config.customLabel != null ->
                "Mostrará: ${
                    config.resolveLabel(
                        app.label
                    )
                }"

            else ->
                "Nombre original"
        }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.92f)
            .clip(shape)
            .clickable(
                onClick =
                    onPrimaryClick
            ),
        shape =
            shape,
        color =
            if (selected) {
                Color(0xFFF1F9E9)
            } else {
                Color.White.copy(
                    alpha = 0.95f
                )
            },
        tonalElevation =
            if (selected) {
                4.dp
            } else {
                2.dp
            },
        shadowElevation =
            if (selected) {
                3.dp
            } else {
                1.dp
            },
        border = BorderStroke(
            width =
                if (selected) {
                    2.dp
                } else {
                    1.dp
                },
            color =
                if (selected) {
                    MaterialTheme
                        .colorScheme
                        .primary
                } else {
                    MaterialTheme
                        .colorScheme
                        .outlineVariant
                }
        )
    ) {
        Box(
            modifier =
                Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = 10.dp,
                        vertical = 14.dp
                    ),
                horizontalAlignment =
                    Alignment.CenterHorizontally,
                verticalArrangement =
                    Arrangement.Center
            ) {
                Image(
                    bitmap =
                        previewBitmap
                            .asImageBitmap(),
                    contentDescription =
                        app.label,
                    modifier =
                        Modifier.size(62.dp),
                    contentScale =
                        ContentScale.Fit,
                    filterQuality =
                        FilterQuality.High
                )

                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )

                Text(
                    text =
                        app.label,
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,
                    fontWeight =
                        FontWeight.SemiBold,
                    textAlign =
                        TextAlign.Center,
                    maxLines = 2,
                    overflow =
                        TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )

                Spacer(
                    modifier =
                        Modifier.height(5.dp)
                )

                Text(
                    text =
                        previewDescription,
                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,
                    color =
                        if (selected) {
                            MaterialTheme
                                .colorScheme
                                .primary
                        } else {
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                        },
                    textAlign =
                        TextAlign.Center,
                    maxLines = 2,
                    overflow =
                        TextOverflow.Ellipsis
                )

                if (selected) {
                    Spacer(
                        modifier =
                            Modifier.height(6.dp)
                    )

                    Text(
                        text =
                            "Posición ${order + 1}",
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,
                        fontWeight =
                            FontWeight.Bold,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )
                }
            }

            if (selected) {
                Surface(
                    modifier = Modifier
                        .align(
                            Alignment.TopStart
                        )
                        .padding(7.dp)
                        .size(30.dp),
                    shape =
                        CircleShape,
                    color =
                        MaterialTheme
                            .colorScheme
                            .primary
                ) {
                    Box(
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Icon(
                            imageVector =
                                Icons.Filled.Check,
                            contentDescription =
                                "Seleccionada",
                            tint =
                                Color.White,
                            modifier =
                                Modifier.size(
                                    18.dp
                                )
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .align(
                            Alignment.TopEnd
                        )
                        .padding(4.dp)
                ) {
                    IconButton(
                        onClick =
                            onEdit,
                        modifier =
                            Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector =
                                Icons.Filled.Edit,
                            contentDescription =
                                "Editar aplicación",
                            modifier =
                                Modifier.size(
                                    20.dp
                                )
                        )
                    }

                    IconButton(
                        onClick =
                            onRemove,
                        modifier =
                            Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector =
                                Icons.Filled.Close,
                            contentDescription =
                                "Quitar aplicación",
                            modifier =
                                Modifier.size(
                                    20.dp
                                ),
                            tint =
                                MaterialTheme
                                    .colorScheme
                                    .error
                        )
                    }
                }
            }
        }
    }
}

/* =========================================================
   EDITOR DE APLICACIÓN
   ========================================================= */

@Composable
private fun AppEditDialog(
    app: AppInfo,
    config: AppDisplayConfig,
    globalIconSize: Int,
    selectedCount: Int,
    onConfigChange: (AppDisplayConfig) -> Unit,
    onChooseIcon: () -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onSave: (AppDisplayConfig) -> Unit
) {
    val context =
        LocalContext.current

    val customBitmap =
        remember(
            config.customIconUri
        ) {
            loadBitmapFromUri(
                context =
                    context,
                uriText =
                    config.customIconUri
            )
        }

    val previewBitmap =
        customBitmap
            ?: app.originalIcon

    val useOriginalName =
        config.customLabel == null

    val useGlobalIconSize =
        config.iconSize == null

    val currentIconSize =
        config.iconSize
            ?: globalIconSize

    Dialog(
        onDismissRequest =
            onCancel,
        properties =
            DialogProperties(
                usePlatformDefaultWidth =
                    false
            )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .heightIn(
                    max = 760.dp
                ),
            shape =
                RoundedCornerShape(28.dp),
            color =
                MaterialTheme
                    .colorScheme
                    .surface,
            tonalElevation = 8.dp,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 18.dp,
                            vertical = 16.dp
                        ),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Image(
                        bitmap =
                            previewBitmap
                                .asImageBitmap(),
                        contentDescription =
                            app.label,
                        modifier =
                            Modifier.size(58.dp),
                        contentScale =
                            ContentScale.Fit
                    )

                    Spacer(
                        modifier =
                            Modifier.width(14.dp)
                    )

                    Column(
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text(
                            text =
                                "Editar aplicación",
                            style =
                                MaterialTheme
                                    .typography
                                    .titleLarge,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            text =
                                app.label,
                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant,
                            maxLines = 1,
                            overflow =
                                TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick =
                            onCancel
                    ) {
                        Icon(
                            imageVector =
                                Icons.Filled.Close,
                            contentDescription =
                                "Cerrar"
                        )
                    }
                }

                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(
                            rememberScrollState()
                        )
                        .padding(18.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            18.dp
                        )
                ) {
                    EditorSectionTitle(
                        title =
                            "Nombre"
                    )

                    EditorSwitchRow(
                        title =
                            "Mostrar nombre",
                        description =
                            "Permite ocultar completamente el texto del icono.",
                        checked =
                            config.showLabel,
                        onCheckedChange = {
                            onConfigChange(
                                config.copy(
                                    showLabel = it
                                )
                            )
                        }
                    )

                    EditorSwitchRow(
                        title =
                            "Usar nombre original",
                        description =
                            app.label,
                        checked =
                            useOriginalName,
                        onCheckedChange = {
                                useOriginal ->
                                onConfigChange(
                                    config.copy(
                                        customLabel =
                                            if (
                                                useOriginal
                                            ) {
                                                null
                                            } else {
                                                app.label
                                            }
                                    )
                                )
                            }
                    )

                    OutlinedTextField(
                        value =
                            config.customLabel
                                ?: "",
                        onValueChange = {
                            value ->
                            onConfigChange(
                                config.copy(
                                    customLabel =
                                        value.take(
                                            50
                                        )
                                )
                            )
                        },
                        enabled =
                            !useOriginalName,
                        modifier =
                            Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                "Nombre personalizado"
                            )
                        },
                        placeholder = {
                            Text(
                                app.label
                            )
                        },
                        supportingText = {
                            Text(
                                if (
                                    config.customLabel ==
                                    ""
                                ) {
                                    "Vacío: se mostrará solamente el icono."
                                } else {
                                    "Puedes escribir cualquier nombre."
                                }
                            )
                        },
                        singleLine = true,
                        shape =
                            RoundedCornerShape(
                                16.dp
                            )
                    )

                    HorizontalDivider()

                    EditorSectionTitle(
                        title =
                            "Icono"
                    )

                    Image(
                        bitmap =
                            previewBitmap
                                .asImageBitmap(),
                        contentDescription =
                            "Vista previa",
                        modifier = Modifier
                            .align(
                                Alignment.CenterHorizontally
                            )
                            .size(
                                currentIconSize
                                    .coerceIn(
                                        40,
                                        140
                                    )
                                    .dp
                            ),
                        contentScale =
                            ContentScale.Fit,
                        filterQuality =
                            FilterQuality.High
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                10.dp
                            )
                    ) {
                        FilledTonalButton(
                            onClick =
                                onChooseIcon,
                            modifier =
                                Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector =
                                    Icons.Filled.Image,
                                contentDescription =
                                    null
                            )

                            Spacer(
                                modifier =
                                    Modifier.width(6.dp)
                            )

                            Text(
                                "Elegir imagen"
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                onConfigChange(
                                    config.copy(
                                        customIconUri =
                                            null
                                    )
                                )
                            },
                            enabled =
                                config.customIconUri !=
                                    null,
                            modifier =
                                Modifier.weight(1f)
                        ) {
                            Text(
                                "Usar original"
                            )
                        }
                    }

                    EditorSwitchRow(
                        title =
                            "Usar tamaño general",
                        description =
                            "Tamaño global: $globalIconSize dp",
                        checked =
                            useGlobalIconSize,
                        onCheckedChange = {
                                useGlobal ->
                                onConfigChange(
                                    config.copy(
                                        iconSize =
                                            if (
                                                useGlobal
                                            ) {
                                                null
                                            } else {
                                                globalIconSize
                                            }
                                    )
                                )
                            }
                    )

                    if (!useGlobalIconSize) {
                        Text(
                            text =
                                "Tamaño del icono: $currentIconSize dp",
                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium,
                            fontWeight =
                                FontWeight.SemiBold
                        )

                        Slider(
                            value =
                                currentIconSize
                                    .toFloat(),
                            onValueChange = {
                                value ->
                                onConfigChange(
                                    config.copy(
                                        iconSize =
                                            value
                                                .roundToInt()
                                                .coerceIn(
                                                    40,
                                                    140
                                                )
                                    )
                                )
                            },
                            valueRange =
                                40f..140f
                        )
                    }

                    HorizontalDivider()

                    EditorSectionTitle(
                        title =
                            "Estilo de tarjeta"
                    )

                    EditorSwitchRow(
                        title =
                            "Usar estilo general",
                        description =
                            "La tarjeta seguirá la configuración de Apariencia.",
                        checked =
                            config.useGlobalStyle,
                        onCheckedChange = {
                            useGlobal ->
                            onConfigChange(
                                config.copy(
                                    useGlobalStyle =
                                        useGlobal,
                                    cardStyle =
                                        if (
                                            useGlobal
                                        ) {
                                            null
                                        } else {
                                            config.cardStyle
                                                ?: CardStyle.GLASS
                                        }
                                )
                            )
                        }
                    )

                    if (!config.useGlobalStyle) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(
                                    rememberScrollState()
                                ),
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    8.dp
                                )
                        ) {
                            CardStyle.entries
                                .forEach {
                                    style ->

                                    FilterChip(
                                        selected =
                                            config.cardStyle ==
                                                style,
                                        onClick = {
                                            onConfigChange(
                                                config.copy(
                                                    cardStyle =
                                                        style
                                                )
                                            )
                                        },
                                        label = {
                                            Text(
                                                cardStyleLabel(
                                                    style
                                                )
                                            )
                                        },
                                        leadingIcon = {
                                            if (
                                                config.cardStyle ==
                                                style
                                            ) {
                                                Icon(
                                                    imageVector =
                                                        Icons.Filled.Check,
                                                    contentDescription =
                                                        null,
                                                    modifier =
                                                        Modifier.size(
                                                            17.dp
                                                        )
                                                )
                                            }
                                        }
                                    )
                                }
                        }

                        val opacity =
                            config.cardOpacity
                                ?: 0.65f

                        Text(
                            text =
                                "Transparencia: ${
                                    (opacity * 100)
                                        .roundToInt()
                                } %",
                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium,
                            fontWeight =
                                FontWeight.SemiBold
                        )

                        Slider(
                            value =
                                opacity,
                            onValueChange = {
                                value ->
                                onConfigChange(
                                    config.copy(
                                        cardOpacity =
                                            value.coerceIn(
                                                0f,
                                                1f
                                            )
                                    )
                                )
                            },
                            valueRange =
                                0f..1f
                        )
                    }

                    HorizontalDivider()

                    EditorSectionTitle(
                        title =
                            "Orden"
                    )

                    Text(
                        text =
                            "Posición ${
                                config.position + 1
                            } de $selectedCount",
                        style =
                            MaterialTheme
                                .typography
                                .bodyMedium
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                10.dp
                            )
                    ) {
                        OutlinedButton(
                            onClick = {
                                onConfigChange(
                                    config.copy(
                                        position =
                                            (
                                                config.position -
                                                    1
                                                )
                                                .coerceAtLeast(
                                                    0
                                                )
                                    )
                                )
                            },
                            enabled =
                                config.position > 0,
                            modifier =
                                Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector =
                                    Icons.Filled.ArrowUpward,
                                contentDescription =
                                    null
                            )

                            Spacer(
                                modifier =
                                    Modifier.width(5.dp)
                            )

                            Text(
                                "Subir"
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                onConfigChange(
                                    config.copy(
                                        position =
                                            (
                                                config.position +
                                                    1
                                                )
                                                .coerceAtMost(
                                                    (
                                                        selectedCount -
                                                            1
                                                        )
                                                        .coerceAtLeast(
                                                            0
                                                        )
                                                )
                                    )
                                )
                            },
                            enabled =
                                config.position <
                                    selectedCount - 1,
                            modifier =
                                Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector =
                                    Icons.Filled.ArrowDownward,
                                contentDescription =
                                    null
                            )

                            Spacer(
                                modifier =
                                    Modifier.width(5.dp)
                            )

                            Text(
                                "Bajar"
                            )
                        }
                    }

                    HorizontalDivider()

                    OutlinedButton(
                        onClick =
                            onReset,
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector =
                                Icons.Filled.RestartAlt,
                            contentDescription =
                                null
                        )

                        Spacer(
                            modifier =
                                Modifier.width(7.dp)
                        )

                        Text(
                            "Restablecer esta aplicación"
                        )
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp,
                            vertical = 12.dp
                        ),
                    horizontalArrangement =
                        Arrangement.End,
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick =
                            onCancel
                    ) {
                        Text(
                            "Cancelar"
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.width(8.dp)
                    )

                    Button(
                        onClick = {
                            onSave(config)
                        },
                        shape =
                            RoundedCornerShape(
                                16.dp
                            )
                    ) {
                        Icon(
                            imageVector =
                                Icons.Filled.Check,
                            contentDescription =
                                null
                        )

                        Spacer(
                            modifier =
                                Modifier.width(7.dp)
                        )

                        Text(
                            "Guardar cambios"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorSectionTitle(
    title: String
) {
    Text(
        text =
            title.uppercase(),
        style =
            MaterialTheme
                .typography
                .labelLarge,
        fontWeight =
            FontWeight.Bold,
        color =
            MaterialTheme
                .colorScheme
                .primary
    )
}

@Composable
private fun EditorSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier =
            Modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Column(
            modifier =
                Modifier.weight(1f)
        ) {
            Text(
                text =
                    title,
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                fontWeight =
                    FontWeight.SemiBold
            )

            Text(
                text =
                    description,
                style =
                    MaterialTheme
                        .typography
                        .bodySmall,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )
        }

        Spacer(
            modifier =
                Modifier.width(12.dp)
        )

        Switch(
            checked =
                checked,
            onCheckedChange =
                onCheckedChange
        )
    }
}

private fun cardStyleLabel(
    style: CardStyle
): String {
    return when (style) {
        CardStyle.NONE ->
            "Sin fondo"

        CardStyle.TRANSPARENT ->
            "Transparente"

        CardStyle.GLASS ->
            "Cristal"

        CardStyle.GLASS_STRONG ->
            "Cristal intenso"

        CardStyle.SOLID_LIGHT ->
            "Claro"

        CardStyle.SOLID_DARK ->
            "Oscuro"
    }
}

/* =========================================================
   BARRA INFERIOR
   ========================================================= */

@Composable
private fun PickerBottomBar(
    selectedCount: Int,
    onSave: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
        color =
            Color.White.copy(
                alpha = 0.98f
            ),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Column(
                modifier =
                    Modifier.weight(1f)
            ) {
                Text(
                    text =
                        if (
                            selectedCount == 1
                        ) {
                            "1 seleccionada"
                        } else {
                            "$selectedCount seleccionadas"
                        },
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    text =
                        "Pulsa una seleccionada para editarla",
                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis
                )
            }

            Spacer(
                modifier =
                    Modifier.width(12.dp)
            )

            Button(
                onClick =
                    onSave,
                modifier = Modifier
                    .widthIn(
                        min = 130.dp
                    )
                    .height(50.dp),
                shape =
                    RoundedCornerShape(
                        18.dp
                    ),
                colors =
                    ButtonDefaults
                        .buttonColors(
                            containerColor =
                                MaterialTheme
                                    .colorScheme
                                    .primary
                        )
            ) {
                Icon(
                    imageVector =
                        Icons.Filled.Check,
                    contentDescription =
                        null
                )

                Spacer(
                    modifier =
                        Modifier.width(7.dp)
                )

                Text(
                    text =
                        "Guardar",
                    fontWeight =
                        FontWeight.Bold
                )
            }
        }
    }
}

/* =========================================================
   ESTADOS
   ========================================================= */

@Composable
private fun LoadingState() {
    Box(
        modifier =
            Modifier.fillMaxSize(),
        contentAlignment =
            Alignment.Center
    ) {
        Surface(
            shape =
                RoundedCornerShape(24.dp),
            color =
                Color.White.copy(
                    alpha = 0.95f
                ),
            tonalElevation = 3.dp
        ) {
            Column(
                modifier =
                    Modifier.padding(28.dp),
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()

                Spacer(
                    modifier =
                        Modifier.height(16.dp)
                )

                Text(
                    text =
                        "Buscando aplicaciones…",
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                )
            }
        }
    }
}

@Composable
private fun EmptyResultState(
    searchActive: Boolean,
    filter: AppFilter
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment =
            Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(
                    max = 380.dp
                ),
            shape =
                RoundedCornerShape(26.dp),
            color =
                Color.White.copy(
                    alpha = 0.95f
                ),
            tonalElevation = 3.dp
        ) {
            Column(
                modifier =
                    Modifier.padding(28.dp),
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector =
                        Icons.Filled.Apps,
                    contentDescription =
                        null,
                    modifier =
                        Modifier.size(52.dp),
                    tint =
                        MaterialTheme
                            .colorScheme
                            .primary
                )

                Spacer(
                    modifier =
                        Modifier.height(16.dp)
                )

                Text(
                    text =
                        when {
                            searchActive ->
                                "No se encontraron aplicaciones"

                            filter ==
                                AppFilter.SELECTED ->
                                "No hay aplicaciones seleccionadas"

                            else ->
                                "No hay aplicaciones disponibles"
                        },
                    style =
                        MaterialTheme
                            .typography
                            .titleLarge,
                    fontWeight =
                        FontWeight.Bold,
                    textAlign =
                        TextAlign.Center
                )
            }
        }
    }
}

/* =========================================================
   FONDO
   ========================================================= */

@Composable
private fun PickerBackground() {
    Box(
        modifier =
            Modifier.fillMaxSize()
    ) {
        Image(
            painter =
                painterResource(
                    id =
                        R.drawable
                            .kiosk_default_bg
                ),
            contentDescription =
                null,
            modifier =
                Modifier.fillMaxSize(),
            contentScale =
                ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Color.Black.copy(
                        alpha = 0.18f
                    )
                )
        )
    }
}