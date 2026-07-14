@file:OptIn(ExperimentalMaterial3Api::class)

package com.gvam.kioskportal.ui

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.gvam.kioskportal.util.Pin
import com.gvam.kioskportal.util.Prefs

/* =========================================================
   ACTIVITY PRINCIPAL MÓVIL
   ========================================================= */

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        if (shouldRunKiosk()) {
            enterKioskUi()
        }

        setContent {
            PortalTheme {
                PortalScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (shouldRunKiosk()) {
            enterKioskUi()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus && shouldRunKiosk()) {
            configureSystemBars()
        }
    }

    private fun shouldRunKiosk(): Boolean {
        return (BuildConfig.IS_TOTEM || BuildConfig.KIOSK_FORCE) &&
            !Prefs.isMaintenance(this)
    }

    /**
     * Solo inicia Lock Task si vuestro MDM ha autorizado
     * previamente este paquete.
     *
     * Esto evita entrar en PINNED durante las pruebas realizadas
     * fuera de la política del MDM.
     */
    private fun enterKioskUi() {
        val devicePolicyManager =
            getSystemService(DevicePolicyManager::class.java)

        if (devicePolicyManager.isLockTaskPermitted(packageName)) {
            runCatching {
                startLockTask()
            }
        }

        configureSystemBars()
    }

    /**
     * Oculta la barra superior y conserva visible la navegación inferior.
     *
     * Cuando el MDM aplique Lock Task multiaplicación, Inicio y Recientes
     * serán controlados mediante la política del MDM.
     */
    private fun configureSystemBars() {
        window.navigationBarColor =
            android.graphics.Color.BLACK

        WindowInsetsControllerCompat(
            window,
            window.decorView
        ).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            show(WindowInsetsCompat.Type.navigationBars())

            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false

            systemBarsBehavior =
                WindowInsetsControllerCompat
                    .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

/* =========================================================
   PANTALLA PRINCIPAL
   ========================================================= */

private enum class AdminTarget {
    None,
    Menu,
    TechPanel
}

@Composable
private fun PortalScreen() {
    val context = LocalContext.current
    val packageManager = context.packageManager

    var title by remember {
        mutableStateOf(
            Prefs.loadTitle(context) ?: "GVAM MDM"
        )
    }

    var apps by remember {
        mutableStateOf(
            loadSelectedEntries(context)
        )
    }

    var gridView by rememberSaveable {
        mutableStateOf(true)
    }

    var showCreatePin by rememberSaveable {
        mutableStateOf(!Prefs.hasPin(context))
    }

    var showVerifyPin by rememberSaveable {
        mutableStateOf(false)
    }

    var showTitleDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var menuOpen by remember {
        mutableStateOf(false)
    }

    var adminTarget by remember {
        mutableStateOf(AdminTarget.None)
    }

    var f12FirstAt by remember {
        mutableStateOf(0L)
    }

    var f12Count by remember {
        mutableStateOf(0)
    }

    val lifecycleOwner =
        LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer =
            object : DefaultLifecycleObserver {

                override fun onResume(
                    owner: LifecycleOwner
                ) {
                    title =
                        Prefs.loadTitle(context)
                            ?: "GVAM MDM"

                    apps =
                        loadSelectedEntries(context)

                    val preferences =
                        context.getSharedPreferences(
                            "portal_prefs",
                            Context.MODE_PRIVATE
                        )

                    when (
                        preferences.getString(
                            "pending_action",
                            null
                        )
                    ) {
                        "open_admin" -> {
                            preferences
                                .edit()
                                .remove("pending_action")
                                .apply()

                            adminTarget =
                                AdminTarget.TechPanel

                            showVerifyPin = true
                        }

                        "open_menu" -> {
                            preferences
                                .edit()
                                .remove("pending_action")
                                .apply()

                            if (Prefs.hasPin(context)) {
                                adminTarget =
                                    AdminTarget.Menu

                                showVerifyPin = true
                            } else {
                                menuOpen = true
                            }
                        }
                    }

                    if (!Prefs.hasPin(context)) {
                        showCreatePin = true
                    }
                }
            }

        lifecycleOwner.lifecycle.addObserver(
            observer
        )

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(
                observer
            )
        }
    }

    fun requestAdminMenu() {
        if (Prefs.hasPin(context)) {
            adminTarget =
                AdminTarget.Menu

            showVerifyPin = true
        } else {
            menuOpen = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (
                    event.nativeKeyEvent.action ==
                    android.view.KeyEvent.ACTION_DOWN
                ) {
                    val keyCode =
                        event.nativeKeyEvent.keyCode

                    val ctrlAlt =
                        event.isCtrlPressed &&
                            event.isAltPressed

                    if (
                        ctrlAlt &&
                        keyCode ==
                        android.view.KeyEvent.KEYCODE_K
                    ) {
                        adminTarget =
                            AdminTarget.TechPanel

                        showVerifyPin = true

                        return@onPreviewKeyEvent true
                    }

                    if (
                        keyCode ==
                        android.view.KeyEvent.KEYCODE_F12
                    ) {
                        val now =
                            System.currentTimeMillis()

                        if (
                            now - f12FirstAt >
                            4000
                        ) {
                            f12FirstAt = now
                            f12Count = 1
                        } else {
                            f12Count += 1
                        }

                        if (f12Count >= 3) {
                            f12Count = 0
                            f12FirstAt = 0L

                            adminTarget =
                                AdminTarget.TechPanel

                            showVerifyPin = true
                        }

                        return@onPreviewKeyEvent true
                    }
                }

                false
            }
    ) {
        BackgroundLayer()

        Scaffold(
            containerColor =
                Color.Transparent,
            contentWindowInsets =
                WindowInsets.safeDrawing,
            topBar = {
                PortalTopBar(
                    title = title,
                    gridView = gridView,
                    onToggleView = {
                        gridView = !gridView
                    },
                    menuOpen = menuOpen,
                    onOpenMenu = {
                        requestAdminMenu()
                    },
                    onDismissMenu = {
                        menuOpen = false
                    }
                ) {
                    MenuEntry(
                        text = "Aplicaciones",
                        icon = Icons.Filled.Apps
                    ) {
                        menuOpen = false

                        context.startActivity(
                            Intent(
                                context,
                                AppPickerActivity::class.java
                            )
                        )
                    }

                    MenuEntry(
                        text = "Cambiar título",
                        icon = Icons.Filled.Edit
                    ) {
                        menuOpen = false
                        showTitleDialog = true
                    }

                    if (
                        !Prefs.isSettingsHidden(context)
                    ) {
                        MenuEntry(
                            text =
                                "Ajustes de Android",
                            icon =
                                Icons.Filled.Settings
                        ) {
                            menuOpen = false

                            context.startActivity(
                                Intent(
                                    context,
                                    AdminUnlockActivity::class.java
                                ).putExtra(
                                    "target",
                                    "settings"
                                )
                            )
                        }
                    }

                    MenuEntry(
                        text =
                            "Información del dispositivo",
                        icon =
                            Icons.Filled.Info
                    ) {
                        menuOpen = false

                        context.startActivity(
                            Intent(
                                context,
                                AboutActivity::class.java
                            )
                        )
                    }
                }
            }
        ) { padding ->
            when {
                apps.isEmpty() -> {
                    EmptyAppsState(
                        padding = padding,
                        onConfigure = {
                            requestAdminMenu()
                        }
                    )
                }

                apps.size == 1 -> {
                    val onlyApp =
                        apps.first()

                    SingleAppSpot(
                        entry = onlyApp,
                        padding = padding,
                        onClick = {
                            launchSelectedApp(
                                context = context,
                                packageManager =
                                    packageManager,
                                entry = onlyApp
                            )
                        }
                    )
                }

                gridView -> {
                    AppsGrid(
                        apps = apps,
                        padding = padding,
                        onAppClick = { entry ->
                            launchSelectedApp(
                                context = context,
                                packageManager =
                                    packageManager,
                                entry = entry
                            )
                        }
                    )
                }

                else -> {
                    AppsList(
                        apps = apps,
                        padding = padding,
                        onAppClick = { entry ->
                            launchSelectedApp(
                                context = context,
                                packageManager =
                                    packageManager,
                                entry = entry
                            )
                        }
                    )
                }
            }
        }

        /*
         * Esquina técnica secreta.
         * Se mantiene pulsada la esquina inferior derecha.
         */
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(64.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            adminTarget =
                                AdminTarget.TechPanel

                            showVerifyPin = true
                        }
                    )
                }
        )
    }

    /* =====================================================
       DIÁLOGOS
       ===================================================== */

    if (showCreatePin) {
        PinCreateDialog(
            onCancel = {
                // El PIN inicial es obligatorio.
            },
            onConfirm = { newPin ->
                Pin.savePin(
                    context,
                    newPin
                )

                showCreatePin = false

                Toast.makeText(
                    context,
                    "PIN creado",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    if (showVerifyPin) {
        PinVerifyDialog(
            onCancel = {
                showVerifyPin = false
                adminTarget =
                    AdminTarget.None
            },
            onResult = { correct ->
                showVerifyPin = false

                if (correct) {
                    when (adminTarget) {
                        AdminTarget.Menu -> {
                            menuOpen = true
                        }

                        AdminTarget.TechPanel -> {
                            context.startActivity(
                                Intent(
                                    context,
                                    AdminUnlockActivity::class.java
                                )
                            )
                        }

                        AdminTarget.None -> Unit
                    }
                } else {
                    Toast.makeText(
                        context,
                        "PIN incorrecto",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                adminTarget =
                    AdminTarget.None
            }
        )
    }

    if (showTitleDialog) {
        TitleDialog(
            initial = title,
            onCancel = {
                showTitleDialog = false
            },
            onSave = { newTitle ->
                Prefs.saveTitle(
                    context,
                    newTitle.trim()
                )

                title =
                    Prefs.loadTitle(context)
                        ?: "GVAM MDM"

                showTitleDialog = false
            }
        )
    }
}

/* =========================================================
   CUADRÍCULA
   ========================================================= */

@Composable
private fun AppsGrid(
    apps: List<AppEntry>,
    padding: PaddingValues,
    onAppClick: (AppEntry) -> Unit
) {
    val configuration =
        LocalConfiguration.current

    val widthDp =
        configuration.screenWidthDp

    val columns =
        when {
            widthDp >= 840 -> 4
            widthDp >= 600 -> 3

            configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE &&
                widthDp >= 480 -> 3

            else -> 2
        }

    LazyVerticalGrid(
        columns =
            GridCells.Fixed(columns),
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(
                horizontal = 12.dp,
                vertical = 14.dp
            ),
        verticalArrangement =
            Arrangement.spacedBy(12.dp),
        horizontalArrangement =
            Arrangement.spacedBy(12.dp),
        contentPadding =
            PaddingValues(bottom = 20.dp)
    ) {
        gridItems(
            items = apps,
            key = {
                it.packageName
            }
        ) { entry ->
            AppGridCard(
                entry = entry,
                onClick = {
                    onAppClick(entry)
                }
            )
        }
    }
}

/* =========================================================
   LISTA
   ========================================================= */

@Composable
private fun AppsList(
    apps: List<AppEntry>,
    padding: PaddingValues,
    onAppClick: (AppEntry) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(
                horizontal = 12.dp,
                vertical = 14.dp
            ),
        verticalArrangement =
            Arrangement.spacedBy(10.dp),
        contentPadding =
            PaddingValues(bottom = 20.dp)
    ) {
        lazyItems(
            items = apps,
            key = {
                it.packageName
            }
        ) { entry ->
            AppListRow(
                entry = entry,
                onClick = {
                    onAppClick(entry)
                }
            )
        }
    }
}

/* =========================================================
   TARJETA DE CUADRÍCULA
   ========================================================= */

@Composable
private fun AppGridCard(
    entry: AppEntry,
    onClick: () -> Unit
) {
    val context =
        LocalContext.current

    val packageManager =
        context.packageManager

    val icon =
        remember(entry.packageName) {
            loadAppIconImage(
                packageManager =
                    packageManager,
                packageName =
                    entry.packageName
            )
        }

    val shape =
        RoundedCornerShape(24.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(
                min = 150.dp,
                max = 172.dp
            )
            .clip(shape)
            .clickable(
                onClick = onClick
            )
            .animateContentSize(),
        shape = shape,
        color =
            Color(0xE6FFFFFF),
        contentColor =
            Color(0xFF1C1B1F),
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(
            width = 1.dp,
            color =
                Color.White.copy(
                    alpha = 0.55f
                )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 12.dp,
                    vertical = 16.dp
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.Center
        ) {
            AppIcon(
                icon = icon,
                label = entry.label,
                size = 72.dp
            )

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            Text(
                text = entry.label,
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
                lineHeight = 19.sp,
                color =
                    Color(0xFF252327)
            )
        }
    }
}

/* =========================================================
   FILA DE LISTA
   ========================================================= */

@Composable
private fun AppListRow(
    entry: AppEntry,
    onClick: () -> Unit
) {
    val context =
        LocalContext.current

    val packageManager =
        context.packageManager

    val icon =
        remember(entry.packageName) {
            loadAppIconImage(
                packageManager =
                    packageManager,
                packageName =
                    entry.packageName
            )
        }

    val shape =
        RoundedCornerShape(20.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(
                min = 82.dp
            )
            .clip(shape)
            .clickable(
                onClick = onClick
            ),
        shape = shape,
        color =
            Color(0xE6FFFFFF),
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(
            width = 1.dp,
            color =
                Color.White.copy(
                    alpha = 0.5f
                )
        )
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
            AppIcon(
                icon = icon,
                label = entry.label,
                size = 54.dp
            )

            Spacer(
                modifier =
                    Modifier.width(16.dp)
            )

            Text(
                text = entry.label,
                modifier =
                    Modifier.weight(1f),
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                fontWeight =
                    FontWeight.SemiBold,
                color =
                    Color(0xFF252327),
                maxLines = 2,
                overflow =
                    TextOverflow.Ellipsis
            )

            Text(
                text = "›",
                fontSize = 34.sp,
                fontWeight =
                    FontWeight.Light,
                color =
                    Color(0xFF6B696D)
            )
        }
    }
}

/* =========================================================
   ICONO DE APLICACIÓN
   ========================================================= */

@Composable
private fun AppIcon(
    icon: ImageBitmap?,
    label: String,
    size: Dp
) {
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = label,
            modifier =
                Modifier.size(size),
            contentScale =
                ContentScale.Fit,
            filterQuality =
                FilterQuality.High
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(
                    RoundedCornerShape(
                        18.dp
                    )
                )
                .background(
                    MaterialTheme
                        .colorScheme
                        .primaryContainer
                ),
            contentAlignment =
                Alignment.Center
        ) {
            Text(
                text =
                    label
                        .trim()
                        .firstOrNull()
                        ?.uppercase()
                        ?: "?",
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                fontWeight =
                    FontWeight.Bold,
                color =
                    MaterialTheme
                        .colorScheme
                        .onPrimaryContainer
            )
        }
    }
}

/* =========================================================
   UNA SOLA APLICACIÓN
   ========================================================= */

@Composable
private fun SingleAppSpot(
    entry: AppEntry,
    padding: PaddingValues,
    onClick: () -> Unit
) {
    val context =
        LocalContext.current

    val packageManager =
        context.packageManager

    val icon =
        remember(entry.packageName) {
            loadAppIconImage(
                packageManager =
                    packageManager,
                packageName =
                    entry.packageName
            )
        }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        contentAlignment =
            Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(
                    max = 330.dp
                )
                .clip(
                    RoundedCornerShape(
                        30.dp
                    )
                )
                .clickable(
                    onClick = onClick
                ),
            shape =
                RoundedCornerShape(30.dp),
            color =
                Color(0xEFFFFFFF),
            tonalElevation = 5.dp,
            shadowElevation = 4.dp,
            border = BorderStroke(
                width = 1.dp,
                color =
                    Color.White.copy(
                        alpha = 0.65f
                    )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 24.dp,
                        vertical = 34.dp
                    ),
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {
                AppIcon(
                    icon = icon,
                    label = entry.label,
                    size = 104.dp
                )

                Spacer(
                    modifier =
                        Modifier.height(22.dp)
                )

                Text(
                    text = entry.label,
                    style =
                        MaterialTheme
                            .typography
                            .headlineSmall,
                    fontWeight =
                        FontWeight.Bold,
                    textAlign =
                        TextAlign.Center,
                    color =
                        Color(0xFF252327),
                    maxLines = 2,
                    overflow =
                        TextOverflow.Ellipsis
                )

                Spacer(
                    modifier =
                        Modifier.height(20.dp)
                )

                FilledTonalButton(
                    onClick = onClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape =
                        RoundedCornerShape(
                            18.dp
                        )
                ) {
                    Text(
                        text =
                            "Abrir aplicación",
                        fontWeight =
                            FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/* =========================================================
   ESTADO VACÍO
   ========================================================= */

@Composable
private fun EmptyAppsState(
    padding: PaddingValues,
    onConfigure: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
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
                RoundedCornerShape(28.dp),
            color =
                Color(0xEFFFFFFF),
            tonalElevation = 4.dp,
            shadowElevation = 3.dp
        ) {
            Column(
                modifier =
                    Modifier.padding(28.dp),
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier =
                        Modifier.size(76.dp),
                    shape = CircleShape,
                    color =
                        MaterialTheme
                            .colorScheme
                            .primaryContainer
                ) {
                    Box(
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Icon(
                            imageVector =
                                Icons.Filled.Apps,
                            contentDescription =
                                null,
                            modifier =
                                Modifier.size(
                                    38.dp
                                ),
                            tint =
                                MaterialTheme
                                    .colorScheme
                                    .onPrimaryContainer
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(20.dp)
                )

                Text(
                    text =
                        "No hay aplicaciones configuradas",
                    style =
                        MaterialTheme
                            .typography
                            .titleLarge,
                    fontWeight =
                        FontWeight.Bold,
                    textAlign =
                        TextAlign.Center,
                    color =
                        Color(0xFF252327)
                )

                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )

                Text(
                    text =
                        "Accede al menú de administración para seleccionar las aplicaciones que aparecerán en este dispositivo.",
                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium,
                    textAlign =
                        TextAlign.Center,
                    color =
                        Color(0xFF656267)
                )

                Spacer(
                    modifier =
                        Modifier.height(22.dp)
                )

                Button(
                    onClick = onConfigure,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape =
                        RoundedCornerShape(
                            18.dp
                        )
                ) {
                    Icon(
                        imageVector =
                            Icons.Filled.Settings,
                        contentDescription =
                            null
                    )

                    Spacer(
                        modifier =
                            Modifier.width(8.dp)
                    )

                    Text(
                        "Configurar aplicaciones"
                    )
                }
            }
        }
    }
}

/* =========================================================
   BARRA SUPERIOR
   ========================================================= */

@Composable
private fun PortalTopBar(
    title: String,
    gridView: Boolean,
    onToggleView: () -> Unit,
    menuOpen: Boolean,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    menuContent:
        @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                horizontal = 10.dp,
                vertical = 4.dp
            )
            .height(68.dp),
        shape =
            RoundedCornerShape(26.dp),
        color =
            Color(0xD9344E25),
        contentColor =
            Color.White,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
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
                    horizontal = 10.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleView,
                modifier =
                    Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector =
                        if (gridView) {
                            Icons.Filled.ViewList
                        } else {
                            Icons.Filled.ViewModule
                        },
                    contentDescription =
                        if (gridView) {
                            "Cambiar a vista de lista"
                        } else {
                            "Cambiar a vista de cuadrícula"
                        },
                    modifier =
                        Modifier.size(27.dp),
                    tint = Color.White
                )
            }

            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        horizontal = 8.dp
                    ),
                style =
                    MaterialTheme
                        .typography
                        .titleLarge,
                fontWeight =
                    FontWeight.Bold,
                textAlign =
                    TextAlign.Center,
                color = Color.White,
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis
            )

            Box(
                modifier =
                    Modifier.wrapContentSize(
                        Alignment.TopEnd
                    )
            ) {
                IconButton(
                    onClick = onOpenMenu,
                    modifier =
                        Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector =
                            Icons.Filled.MoreVert,
                        contentDescription =
                            "Abrir menú",
                        modifier =
                            Modifier.size(28.dp),
                        tint =
                            Color.White
                    )
                }

                MaterialTheme(
                    shapes =
                        MaterialTheme
                            .shapes
                            .copy(
                                extraSmall =
                                    RoundedCornerShape(
                                        20.dp
                                    )
                            )
                ) {
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest =
                            onDismissMenu,
                        offset =
                            DpOffset(
                                x = (-4).dp,
                                y = 4.dp
                            ),
                        modifier =
                            Modifier.widthIn(
                                min = 250.dp,
                                max = 310.dp
                            ),
                        content =
                            menuContent
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuEntry(
    text: String,
    icon:
        androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null
            )
        },
        onClick = onClick,
        contentPadding =
            PaddingValues(
                horizontal = 18.dp,
                vertical = 4.dp
            )
    )
}

/* =========================================================
   FONDO
   ========================================================= */

@Composable
private fun BackgroundLayer() {
    Box(
        modifier =
            Modifier.fillMaxSize()
    ) {
        Image(
            painter = painterResource(
                id =
                    R.drawable.kiosk_default_bg
            ),
            contentDescription = null,
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
                        alpha = 0.12f
                    )
                )
        )
    }
}

/* =========================================================
   DIÁLOGO CREAR PIN
   ========================================================= */

@Composable
private fun PinCreateDialog(
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var firstPin by rememberSaveable {
        mutableStateOf("")
    }

    var repeatedPin by rememberSaveable {
        mutableStateOf("")
    }

    val valid =
        firstPin.length in 4..8 &&
            firstPin == repeatedPin

    AlertDialog(
        onDismissRequest =
            onCancel,
        title = {
            Text(
                "Crear PIN de administrador"
            )
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        10.dp
                    )
            ) {
                Text(
                    text =
                        "Introduce un código de entre 4 y 8 dígitos.",
                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium
                )

                OutlinedTextField(
                    value =
                        firstPin,
                    onValueChange = { value ->
                        firstPin =
                            value
                                .filter(
                                    Char::isDigit
                                )
                                .take(8)
                    },
                    singleLine = true,
                    label = {
                        Text("PIN")
                    },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType =
                                KeyboardType
                                    .NumberPassword
                        ),
                    visualTransformation =
                        PasswordVisualTransformation(),
                    modifier =
                        Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value =
                        repeatedPin,
                    onValueChange = { value ->
                        repeatedPin =
                            value
                                .filter(
                                    Char::isDigit
                                )
                                .take(8)
                    },
                    singleLine = true,
                    label = {
                        Text(
                            "Repetir PIN"
                        )
                    },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType =
                                KeyboardType
                                    .NumberPassword
                        ),
                    visualTransformation =
                        PasswordVisualTransformation(),
                    modifier =
                        Modifier.fillMaxWidth()
                )

                if (
                    firstPin.isNotEmpty() &&
                    repeatedPin.isNotEmpty() &&
                    !valid
                ) {
                    Text(
                        text =
                            "Los PIN no coinciden o la longitud no es válida.",
                        color =
                            MaterialTheme
                                .colorScheme
                                .error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    if (valid) {
                        onConfirm(firstPin)
                    }
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel
            ) {
                Text("Cancelar")
            }
        },
        properties =
            DialogProperties(
                dismissOnClickOutside =
                    false
            )
    )
}

/* =========================================================
   DIÁLOGO VERIFICAR PIN
   ========================================================= */

@Composable
private fun PinVerifyDialog(
    onCancel: () -> Unit,
    onResult: (Boolean) -> Unit
) {
    val context =
        LocalContext.current

    var pin by rememberSaveable {
        mutableStateOf("")
    }

    val valid =
        pin.length in 4..8

    AlertDialog(
        onDismissRequest =
            onCancel,
        title = {
            Text(
                "Acceso de administrador"
            )
        },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { value ->
                    pin =
                        value
                            .filter(
                                Char::isDigit
                            )
                            .take(8)
                },
                singleLine = true,
                label = {
                    Text(
                        "PIN de administrador"
                    )
                },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType =
                            KeyboardType
                                .NumberPassword
                    ),
                visualTransformation =
                    PasswordVisualTransformation(),
                modifier =
                    Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onResult(
                        Pin.verifyPin(
                            context,
                            pin
                        )
                    )
                }
            ) {
                Text("Aceptar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel
            ) {
                Text("Cancelar")
            }
        },
        properties =
            DialogProperties(
                dismissOnClickOutside =
                    false
            )
    )
}

/* =========================================================
   DIÁLOGO CAMBIAR TÍTULO
   ========================================================= */

@Composable
private fun TitleDialog(
    initial: String,
    onCancel: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by rememberSaveable {
        mutableStateOf(initial)
    }

    AlertDialog(
        onDismissRequest =
            onCancel,
        title = {
            Text(
                "Cambiar título"
            )
        },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value =
                        it.take(40)
                },
                singleLine = true,
                label = {
                    Text(
                        "Título del launcher"
                    )
                },
                supportingText = {
                    Text(
                        "${value.length}/40"
                    )
                },
                modifier =
                    Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(value)
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel
            ) {
                Text("Cancelar")
            }
        },
        properties =
            DialogProperties(
                dismissOnClickOutside =
                    false
            )
    )
}

/* =========================================================
   ABRIR APLICACIÓN
   ========================================================= */

private fun launchSelectedApp(
    context: Context,
    packageManager: PackageManager,
    entry: AppEntry
) {
    val launchIntent =
        packageManager
            .getLaunchIntentForPackage(
                entry.packageName
            )

    if (launchIntent == null) {
        Toast.makeText(
            context,
            "${entry.label} no tiene una actividad de inicio disponible",
            Toast.LENGTH_LONG
        ).show()

        return
    }

    /*
     * getLaunchIntentForPackage suele incluir NEW_TASK.
     * Lo eliminamos para abrir la aplicación desde la tarea
     * actual del launcher y poder regresar mediante Atrás.
     */
    launchIntent.flags =
        launchIntent.flags and
            Intent.FLAG_ACTIVITY_NEW_TASK.inv()

    launchIntent.addFlags(
        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
    )

    runCatching {
        context.startActivity(
            launchIntent
        )
    }.onFailure { error ->
        Toast.makeText(
            context,
            "No se pudo abrir ${entry.label}: " +
                (error.message ?: "error desconocido"),
            Toast.LENGTH_LONG
        ).show()
    }
}

/* =========================================================
   CARGAR APLICACIONES
   ========================================================= */

private fun loadSelectedEntries(
    context: Context
): List<AppEntry> {
    val packageManager =
        context.packageManager

    return Prefs
        .loadSelectedPackages(context)
        .map { packageName ->
            val label =
                try {
                    val applicationInfo =
                        packageManager
                            .getApplicationInfo(
                                packageName,
                                0
                            )

                    packageManager
                        .getApplicationLabel(
                            applicationInfo
                        )
                        .toString()
                } catch (_: Exception) {
                    packageName
                }

            AppEntry(
                packageName,
                label
            )
        }
        .sortedBy {
            it.label.lowercase()
        }
}

/* =========================================================
   CARGAR ICONOS
   ========================================================= */

private fun loadAppIconImage(
    packageManager: PackageManager,
    packageName: String
) = runCatching {
    val drawable =
        packageManager
            .getApplicationIcon(
                packageName
            )

    val bitmap =
        (drawable as? BitmapDrawable)
            ?.bitmap
            ?: drawable.toBitmap(
                width = 160,
                height = 160,
                config =
                    Bitmap.Config.ARGB_8888
            )

    bitmap.asImageBitmap()
}.getOrNull()