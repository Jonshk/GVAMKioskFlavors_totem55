@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gvam.kioskportal.ui

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import com.gvam.kioskportal.model.AppDisplayConfig
import com.gvam.kioskportal.model.BackgroundMode
import com.gvam.kioskportal.model.BackgroundScale
import com.gvam.kioskportal.model.CardStyle
import com.gvam.kioskportal.model.HeaderPosition
import com.gvam.kioskportal.model.IconSizeMode
import com.gvam.kioskportal.model.LayoutMode
import com.gvam.kioskportal.model.HeaderStyle
import com.gvam.kioskportal.model.HeaderTitleAlignment
import com.gvam.kioskportal.model.LauncherAppearance
import com.gvam.kioskportal.util.Pin
import com.gvam.kioskportal.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private fun shouldRunKiosk(): Boolean =
        (BuildConfig.IS_TOTEM || BuildConfig.KIOSK_FORCE) &&
            !Prefs.isMaintenance(this)

    private fun enterKioskUi() {
        val dpm = getSystemService(DevicePolicyManager::class.java)

        if (dpm.isLockTaskPermitted(packageName)) {
            runCatching { startLockTask() }
        }

        configureSystemBars()
    }

    private fun configureSystemBars() {
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

private data class LauncherAppEntry(
    val packageName: String,
    val originalLabel: String,
    val displayConfig: AppDisplayConfig,
    val icon: ImageBitmap?
) {
    val displayedLabel: String
        get() = displayConfig.resolveLabel(originalLabel)
}

private data class LauncherScreenData(
    val title: String = "GVAM MDM",
    val appearance: LauncherAppearance = LauncherAppearance(),
    val apps: List<LauncherAppEntry> = emptyList()
)

private data class CardVisual(
    val containerColor: Color,
    val borderColor: Color,
    val textColor: Color,
    val borderWidth: Dp,
    val shadowElevation: Dp,
    val showHighlight: Boolean,
    val highlightOpacity: Float
)

private enum class AdminTarget {
    NONE,
    MENU,
    TECH_PANEL
}

@Composable
private fun PortalScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var screenData by remember {
        mutableStateOf(LauncherScreenData())
    }
    var loading by remember { mutableStateOf(true) }
    var showCreatePin by rememberSaveable {
        mutableStateOf(!Prefs.hasPin(context))
    }
    var showVerifyPin by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var adminTarget by remember { mutableStateOf(AdminTarget.NONE) }
    var f12FirstAt by remember { mutableStateOf(0L) }
    var f12Count by remember { mutableStateOf(0) }

    fun reloadLauncher() {
        coroutineScope.launch {
            loading = true
            screenData = withContext(Dispatchers.IO) {
                loadLauncherScreenData(context)
            }
            loading = false
        }
    }

    fun requestAdminMenu() {
        if (Prefs.hasPin(context)) {
            adminTarget = AdminTarget.MENU
            showVerifyPin = true
        } else {
            menuOpen = true
        }
    }

    fun changeLayoutMode(mode: LayoutMode) {
        val updatedAppearance = screenData.appearance.copy(
            layoutMode = mode
        )

        screenData = screenData.copy(
            appearance = updatedAppearance
        )

        Prefs.saveAppearance(
            context,
            updatedAppearance
        )
    }

    LaunchedEffect(Unit) {
        reloadLauncher()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                reloadLauncher()

                val preferences = context.getSharedPreferences(
                    "portal_prefs",
                    Context.MODE_PRIVATE
                )

                when (preferences.getString("pending_action", null)) {
                    "open_admin" -> {
                        preferences.edit()
                            .remove("pending_action")
                            .apply()
                        adminTarget = AdminTarget.TECH_PANEL
                        showVerifyPin = true
                    }

                    "open_menu" -> {
                        preferences.edit()
                            .remove("pending_action")
                            .apply()
                        requestAdminMenu()
                    }
                }

                if (!Prefs.hasPin(context)) {
                    showCreatePin = true
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val appearance = screenData.appearance
    val headerAtTop =
        appearance.headerVisible &&
            appearance.headerPosition == HeaderPosition.TOP
    val headerAtBottom =
        appearance.headerVisible &&
            appearance.headerPosition == HeaderPosition.BOTTOM

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (
                    event.nativeKeyEvent.action ==
                    android.view.KeyEvent.ACTION_DOWN
                ) {
                    val code = event.nativeKeyEvent.keyCode

                    if (
                        event.isCtrlPressed &&
                        event.isAltPressed &&
                        code == android.view.KeyEvent.KEYCODE_K
                    ) {
                        adminTarget = AdminTarget.TECH_PANEL
                        showVerifyPin = true
                        return@onPreviewKeyEvent true
                    }

                    if (code == android.view.KeyEvent.KEYCODE_F12) {
                        val now = System.currentTimeMillis()

                        if (now - f12FirstAt > 4000L) {
                            f12FirstAt = now
                            f12Count = 1
                        } else {
                            f12Count += 1
                        }

                        if (f12Count >= 3) {
                            f12FirstAt = 0L
                            f12Count = 0
                            adminTarget = AdminTarget.TECH_PANEL
                            showVerifyPin = true
                        }

                        return@onPreviewKeyEvent true
                    }
                }

                false
            }
    ) {
        LauncherBackground(appearance)

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                if (headerAtTop) {
                    LauncherHeader(
                        title = screenData.title,
                        appearance = appearance,
                        onLayoutModeSelected = ::changeLayoutMode,
                        menuOpen = menuOpen,
                        onOpenMenu = ::requestAdminMenu,
                        onDismissMenu = { menuOpen = false },
                        isBottom = false
                    ) {
                        AdminMenuContent(
                            context = context,
                            onCloseMenu = { menuOpen = false }
                        )
                    }
                }
            },
            bottomBar = {
                if (headerAtBottom) {
                    LauncherHeader(
                        title = screenData.title,
                        appearance = appearance,
                        onLayoutModeSelected = ::changeLayoutMode,
                        menuOpen = menuOpen,
                        onOpenMenu = ::requestAdminMenu,
                        onDismissMenu = { menuOpen = false },
                        isBottom = true
                    ) {
                        AdminMenuContent(
                            context = context,
                            onCloseMenu = { menuOpen = false }
                        )
                    }
                }
            }
        ) { padding ->
            when {
                loading -> {
                    LoadingState(padding)
                }

                screenData.apps.isEmpty() -> {
                    EmptyState(
                        padding = padding,
                        onConfigure = ::requestAdminMenu
                    )
                }

                else -> {
                    ConfiguredAppsLayout(
                        apps = screenData.apps,
                        appearance = appearance,
                        padding = padding,
                        onAppClick = { app ->
                            launchSelectedApp(
                                context = context,
                                packageManager = context.packageManager,
                                packageName = app.packageName,
                                label = app.displayedLabel.ifBlank {
                                    app.originalLabel
                                }
                            )
                        }
                    )
                }
            }
        }

        if (!appearance.headerVisible) {
            HiddenHeaderSecretTouchLayer(
                appearance = appearance,
                menuOpen = menuOpen,
                onOpenMenu = ::requestAdminMenu,
                onDismissMenu = { menuOpen = false },
                onLayoutModeSelected = ::changeLayoutMode
            ) {
                AdminMenuContent(
                            context = context,
                            onCloseMenu = { menuOpen = false }
                        )
            }
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(72.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                adminTarget = AdminTarget.TECH_PANEL
                                showVerifyPin = true
                            }
                        )
                    }
            )
        }
    }

    if (showCreatePin) {
        PinCreateDialog(
            onCancel = {},
            onConfirm = { pin ->
                Pin.savePin(context, pin)
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
                adminTarget = AdminTarget.NONE
            },
            onResult = { correct ->
                showVerifyPin = false

                if (correct) {
                    when (adminTarget) {
                        AdminTarget.MENU -> menuOpen = true
                        AdminTarget.TECH_PANEL -> {
                            context.startActivity(
                                Intent(
                                    context,
                                    AdminUnlockActivity::class.java
                                )
                            )
                        }
                        AdminTarget.NONE -> Unit
                    }
                } else {
                    Toast.makeText(
                        context,
                        "PIN incorrecto",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                adminTarget = AdminTarget.NONE
            }
        )
    }
}


@Composable
private fun HiddenHeaderSecretTouchLayer(
    appearance: LauncherAppearance,
    menuOpen: Boolean,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onLayoutModeSelected: (LayoutMode) -> Unit,
    menuContent: @Composable ColumnScope.() -> Unit
) {
    /*
     * CABECERA OCULTA
     *
     * No se dibuja ningun boton, fondo, circulo, temporizador ni indicador.
     * Los accesos son zonas tactiles totalmente invisibles:
     *
     * - Doble toque arriba a la izquierda: cambia la distribucion.
     * - Doble toque arriba a la derecha: solicita el PIN y abre el menu normal.
     */
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(4.dp)
                .size(88.dp)
                .pointerInput(appearance.layoutMode) {
                    detectTapGestures(
                        onDoubleTap = {
                            onLayoutModeSelected(
                                nextLayoutMode(
                                    appearance.layoutMode
                                )
                            )
                        }
                    )
                }
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(4.dp)
                .size(88.dp)
                .pointerInput(menuOpen) {
                    detectTapGestures(
                        onDoubleTap = {
                            onOpenMenu()
                        }
                    )
                }
        ) {
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = onDismissMenu,
                offset = DpOffset(
                    x = (-4).dp,
                    y = 4.dp
                ),
                modifier = Modifier.widthIn(
                    min = 270.dp,
                    max = 340.dp
                ),
                content = menuContent
            )
        }
    }
}

@Composable
private fun AdminMenuContent(
    context: Context,
    onCloseMenu: () -> Unit
) {
    MenuEntry(
        text = "Configurar aplicaciones",
        icon = Icons.Filled.Apps
    ) {
        onCloseMenu()
        context.startActivity(
            Intent(context, AppPickerActivity::class.java)
        )
    }

    MenuEntry(
        text = "Apariencia",
        icon = Icons.Filled.Palette
    ) {
        onCloseMenu()
        context.startActivity(
            Intent(context, AppearanceActivity::class.java)
        )
    }

    MenuEntry(
        text = "Informaci\u00F3n del dispositivo",
        icon = Icons.Filled.Info
    ) {
        onCloseMenu()
        context.startActivity(
            Intent(context, AboutActivity::class.java)
        )
    }

    if (!Prefs.isSettingsHidden(context)) {
        MenuEntry(
            text = "Ajustes de Android",
            icon = Icons.Filled.Settings
        ) {
            onCloseMenu()
            context.startActivity(
                Intent(
                    context,
                    AdminUnlockActivity::class.java
                ).putExtra("target", "settings")
            )
        }
    }
}

@Composable
private fun LauncherBackground(
    appearance: LauncherAppearance
) {
    val context = LocalContext.current
    val customBackground = remember(appearance.backgroundUri) {
        loadBitmapFromUri(
            context = context,
            uriText = appearance.backgroundUri,
            maximumSize = 2048
        )?.asImageBitmap()
    }

    Box(Modifier.fillMaxSize()) {
        when (appearance.backgroundMode) {
            BackgroundMode.DEFAULT_IMAGE -> {
                Image(
                    painter = painterResource(R.drawable.kiosk_default_bg),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale(appearance.backgroundScale)
                )
            }

            BackgroundMode.CUSTOM_IMAGE -> {
                if (customBackground != null) {
                    Image(
                        bitmap = customBackground,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = contentScale(
                            appearance.backgroundScale
                        ),
                        filterQuality = FilterQuality.High
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.kiosk_default_bg),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            BackgroundMode.SOLID_COLOR -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            colorFromLong(appearance.backgroundColor)
                        )
                )
            }

            BackgroundMode.NONE -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                )
            }
        }

        if (appearance.backgroundOverlay > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Color.Black.copy(
                            alpha = appearance.backgroundOverlay
                                .coerceIn(0f, 1f)
                        )
                    )
            )
        }
    }
}

@Composable
private fun LauncherHeader(
    title: String,
    appearance: LauncherAppearance,
    onLayoutModeSelected: (LayoutMode) -> Unit,
    menuOpen: Boolean,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    isBottom: Boolean,
    menuContent: @Composable ColumnScope.() -> Unit
) {
    val context = LocalContext.current

    val headerBitmap = remember(appearance.headerImageUri) {
        loadBitmapFromUri(
            context = context,
            uriText = appearance.headerImageUri,
            maximumSize = 1200
        )?.asImageBitmap()
    }

    val shape = RoundedCornerShape(
        appearance.headerCornerRadius.coerceIn(0f, 60f).dp
    )

    val positionModifier = if (isBottom) {
        Modifier.navigationBarsPadding()
    } else {
        Modifier.statusBarsPadding()
    }

    Surface(
        modifier = positionModifier
            .fillMaxWidth()
            .padding(
                horizontal = appearance.headerHorizontalMargin
                    .coerceIn(0f, 40f)
                    .dp,
                vertical = appearance.headerVerticalMargin
                    .coerceIn(0f, 30f)
                    .dp
            )
            .height(
                appearance.headerHeight
                    .coerceIn(48f, 120f)
                    .dp
            ),
        shape = shape,
        color = Color.Transparent,
        contentColor = colorFromLong(appearance.headerTitleColor),
        border = BorderStroke(
            1.dp,
            Color.White.copy(
                alpha = appearance.headerBorderOpacity
                    .coerceIn(0f, 1f)
            )
        ),
        shadowElevation = appearance.headerShadow
            .coerceIn(0f, 24f)
            .dp
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
        ) {
            HeaderBackground(
                appearance = appearance,
                customImage = headerBitmap
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (appearance.showViewToggle) {
                    IconButton(
                        onClick = {
                            onLayoutModeSelected(
                                nextLayoutMode(appearance.layoutMode)
                            )
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = layoutModeIcon(
                                appearance.layoutMode
                            ),
                            contentDescription =
                                "Cambiar distribuci\u00F3n",
                            tint = colorFromLong(
                                appearance.headerTitleColor
                            )
                        )
                    }
                } else {
                    Spacer(Modifier.width(48.dp))
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                    contentAlignment = titleAlignment(
                        appearance.headerTitleAlignment
                    )
                ) {
                    if (appearance.showTitle) {
                        Text(
                            text = title,
                            color = colorFromLong(
                                appearance.headerTitleColor
                            ),
                            fontSize = appearance.headerTitleSize
                                .coerceIn(12f, 36f)
                                .sp,
                            fontWeight = if (
                                appearance.headerTitleBold
                            ) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                            textAlign = titleTextAlign(
                                appearance.headerTitleAlignment
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (appearance.showMenuButton) {
                    Box(
                        modifier = Modifier.wrapContentSize(
                            Alignment.TopEnd
                        )
                    ) {
                        IconButton(
                            onClick = onOpenMenu,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "Abrir men\u00FA",
                                tint = colorFromLong(
                                    appearance.headerTitleColor
                                )
                            )
                        }

                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = onDismissMenu,
                            offset = DpOffset(
                                x = (-4).dp,
                                y = 4.dp
                            ),
                            modifier = Modifier.widthIn(
                                min = 270.dp,
                                max = 340.dp
                            ),
                            content = menuContent
                        )
                    }
                } else {
                    Spacer(Modifier.width(48.dp))
                }
            }
        }
    }
}

private fun nextLayoutMode(
    current: LayoutMode
): LayoutMode =
    when (current) {
        LayoutMode.ADAPTIVE -> LayoutMode.ONE_COLUMN
        LayoutMode.ONE_COLUMN -> LayoutMode.TWO_COLUMNS
        LayoutMode.TWO_COLUMNS -> LayoutMode.THREE_COLUMNS
        LayoutMode.THREE_COLUMNS -> LayoutMode.ADAPTIVE
    }

private fun layoutModeIcon(
    mode: LayoutMode
): androidx.compose.ui.graphics.vector.ImageVector =
    when (mode) {
        LayoutMode.ADAPTIVE -> Icons.Filled.Apps
        LayoutMode.ONE_COLUMN -> Icons.AutoMirrored.Filled.ViewList
        LayoutMode.TWO_COLUMNS,
        LayoutMode.THREE_COLUMNS -> Icons.Filled.ViewModule
    }


private fun layoutModeDescription(
    mode: LayoutMode
): String =
    when (mode) {
        LayoutMode.ADAPTIVE ->
            "Se adapta al ancho y a la cantidad de aplicaciones."
        LayoutMode.ONE_COLUMN ->
            "Tarjetas centradas, una debajo de otra."
        LayoutMode.TWO_COLUMNS ->
            "Dos aplicaciones por fila."
        LayoutMode.THREE_COLUMNS ->
            "Tres aplicaciones por fila."
    }

@Composable
private fun HeaderBackground(
    appearance: LauncherAppearance,
    customImage: ImageBitmap?
) {
    val opacity = appearance.topBarOpacity.coerceIn(0f, 1f)

    when (appearance.headerStyle) {
        HeaderStyle.TRANSPARENT -> {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
            )
        }

        HeaderStyle.GLASS -> {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        colorFromLong(appearance.headerColor)
                            .copy(alpha = opacity * 0.78f)
                    )
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.14f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        HeaderStyle.SOLID -> {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        colorFromLong(appearance.headerColor)
                            .copy(alpha = opacity)
                    )
            )
        }

        HeaderStyle.CUSTOM_IMAGE -> {
            if (customImage != null) {
                Image(
                    bitmap = customImage,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale(
                        appearance.headerImageScale
                    ),
                    filterQuality = FilterQuality.High,
                    alpha = opacity
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            colorFromLong(appearance.headerColor)
                                .copy(alpha = opacity)
                        )
                )
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
        text = {
            Text(
                text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(icon, contentDescription = null)
        },
        onClick = onClick,
        contentPadding = PaddingValues(
            horizontal = 18.dp,
            vertical = 5.dp
        )
    )
}

@Composable
private fun ConfiguredAppsLayout(
    apps: List<LauncherAppEntry>,
    appearance: LauncherAppearance,
    padding: PaddingValues,
    onAppClick: (LauncherAppEntry) -> Unit
) {
    if (apps.size == 1) {
        SingleCenteredApp(
            app = apps.first(),
            appearance = appearance,
            padding = padding,
            onAppClick = onAppClick
        )
        return
    }

    val spacing = appearance.cardSpacing
        .coerceIn(0f, 40f)
        .dp

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        val availableWidth = (
            maxWidth - 24.dp
        ).coerceAtLeast(1.dp)

        val columns = resolveLayoutColumns(
            mode = appearance.layoutMode,
            availableWidth = availableWidth,
            spacing = spacing,
            appCount = apps.size
        )

        if (apps.size == 2) {
            TwoCenteredApps(
                apps = apps,
                appearance = appearance,
                columns = columns,
                availableWidth = availableWidth,
                spacing = spacing,
                onAppClick = onAppClick
            )
            return@BoxWithConstraints
        }

        val cardWidth = if (columns == 1) {
            minOf(
                availableWidth,
                440.dp
            )
        } else {
            (
                availableWidth -
                    spacing * (columns - 1).toFloat()
                ) / columns.toFloat()
        }

        val rows = apps.chunked(columns)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(
                top = 12.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            items(
                items = rows,
                key = { row ->
                    row.joinToString("|") {
                        it.packageName
                    }
                }
            ) { rowApps ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        spacing,
                        Alignment.CenterHorizontally
                    )
                ) {
                    rowApps.forEach { app ->
                        Box(
                            modifier = Modifier.width(cardWidth)
                        ) {
                            ConfiguredAppCard(
                                app = app,
                                appearance = appearance,
                                layoutColumns = columns,
                                appCount = apps.size,
                                onClick = {
                                    onAppClick(app)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TwoCenteredApps(
    apps: List<LauncherAppEntry>,
    appearance: LauncherAppearance,
    columns: Int,
    availableWidth: Dp,
    spacing: Dp,
    onAppClick: (LauncherAppEntry) -> Unit
) {
    val verticalSpacing = maxOf(spacing, 44.dp)
    val horizontalSpacing = maxOf(spacing, 20.dp)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (columns <= 1) {
            val cardWidth = minOf(
                availableWidth * 0.72f,
                320.dp
            ).coerceAtLeast(180.dp)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(verticalSpacing)
            ) {
                apps.forEach { app ->
                    Box(
                        modifier = Modifier.width(cardWidth)
                    ) {
                        ConfiguredAppCard(
                            app = app,
                            appearance = appearance,
                            layoutColumns = 1,
                            appCount = apps.size,
                            onClick = { onAppClick(app) }
                        )
                    }
                }
            }
        } else {
            val cardWidth = minOf(
                (availableWidth - horizontalSpacing) / 2f,
                300.dp
            ).coerceAtLeast(140.dp)

            Row(
                horizontalArrangement = Arrangement.spacedBy(
                    horizontalSpacing,
                    Alignment.CenterHorizontally
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                apps.forEach { app ->
                    Box(
                        modifier = Modifier.width(cardWidth)
                    ) {
                        ConfiguredAppCard(
                            app = app,
                            appearance = appearance,
                            layoutColumns = 2,
                            appCount = apps.size,
                            onClick = { onAppClick(app) }
                        )
                    }
                }
            }
        }
    }
}

private fun resolveLayoutColumns(
    mode: LayoutMode,
    availableWidth: Dp,
    spacing: Dp,
    appCount: Int
): Int {
    val requested = when (mode) {
        LayoutMode.ADAPTIVE -> {
            val minimumCardWidth = 210f
            val calculated = (
                (
                    availableWidth.value +
                        spacing.value
                    ) /
                    (
                        minimumCardWidth +
                            spacing.value
                        )
                ).toInt()

            calculated.coerceIn(1, 3)
        }

        LayoutMode.ONE_COLUMN -> 1
        LayoutMode.TWO_COLUMNS -> 2
        LayoutMode.THREE_COLUMNS -> 3
    }

    return requested.coerceAtMost(
        appCount.coerceAtLeast(1)
    )
}

private fun resolveGlobalIconSize(
    appearance: LauncherAppearance,
    layoutColumns: Int,
    appCount: Int
): Int =
    when (appearance.iconSizeMode) {
        IconSizeMode.AUTO -> {
            when {
                appCount <= 1 -> 120
                layoutColumns <= 1 -> 104
                layoutColumns == 2 -> 80
                else -> 64
            }
        }

        IconSizeMode.SMALL -> 48
        IconSizeMode.MEDIUM -> 72
        IconSizeMode.LARGE -> 104
        IconSizeMode.CUSTOM -> appearance.iconSize
    }.coerceIn(32, 160)

@Composable
private fun SingleCenteredApp(
    app: LauncherAppEntry,
    appearance: LauncherAppearance,
    padding: PaddingValues,
    onAppClick: (LauncherAppEntry) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        val cardWidth = minOf(
            maxWidth * 0.72f,
            320.dp
        ).coerceAtLeast(180.dp)

        Box(
            modifier = Modifier.width(cardWidth)
        ) {
            ConfiguredAppCard(
                app = app,
                appearance = appearance,
                layoutColumns = 1,
                appCount = 1,
                onClick = {
                    onAppClick(app)
                }
            )
        }
    }
}

@Composable
private fun ConfiguredAppCard(
    app: LauncherAppEntry,
    appearance: LauncherAppearance,
    layoutColumns: Int,
    appCount: Int,
    onClick: () -> Unit
) {
    val config = app.displayConfig
    val visual = resolveCardVisual(appearance, config)

    val shape = RoundedCornerShape(
        appearance.cardCornerRadius.coerceIn(0f, 60f).dp
    )

    val resolvedIconSize = (
        config.iconSize ?: resolveGlobalIconSize(
            appearance = appearance,
            layoutColumns = layoutColumns,
            appCount = appCount
        )
    ).coerceIn(32, 160)

    val iconSize = resolvedIconSize.dp

    val showLabel =
        appearance.showLabels &&
            config.showLabel &&
            app.displayedLabel.isNotBlank()

    val minimumRequiredHeight =
        resolvedIconSize.toFloat() +
            if (showLabel) {
                maxOf(
                    48f,
                    appearance.textSize * 2.8f
                )
            } else {
                30f
            }

    val resolvedCardHeight = maxOf(
        appearance.cardHeight.coerceIn(90f, 300f),
        minimumRequiredHeight
    ).coerceAtMost(340f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(resolvedCardHeight.dp)
            .clip(shape)
            .clickable(onClick = onClick)
            .animateContentSize(),
        shape = shape,
        color = visual.containerColor,
        contentColor = visual.textColor,
        tonalElevation = visual.shadowElevation,
        shadowElevation = visual.shadowElevation,
        border = if (visual.borderWidth > 0.dp) {
            BorderStroke(
                visual.borderWidth,
                visual.borderColor
            )
        } else {
            null
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            if (visual.showHighlight) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(
                                        alpha = visual.highlightOpacity
                                    ),
                                    Color.White.copy(
                                        alpha = visual.highlightOpacity * 0.25f
                                    ),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = 10.dp,
                        vertical = 12.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ConfiguredAppIcon(
                    icon = app.icon,
                    fallbackText = app.displayedLabel.ifBlank {
                        app.originalLabel
                    },
                    size = iconSize,
                    textColor = visual.textColor
                )

                if (showLabel) {
                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = app.displayedLabel,
                        color = visual.textColor,
                        fontSize = appearance.textSize
                            .coerceIn(10f, 32f)
                            .sp,
                        lineHeight = (
                            appearance.textSize * 1.18f
                        ).coerceIn(12f, 38f).sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfiguredAppListRow(
    app: LauncherAppEntry,
    appearance: LauncherAppearance,
    onClick: () -> Unit
) {
    val config = app.displayConfig
    val visual = resolveCardVisual(appearance, config)

    val shape = RoundedCornerShape(
        appearance.cardCornerRadius.coerceIn(0f, 60f).dp
    )

    val iconSize = (
        config.iconSize ?: appearance.iconSize
    ).coerceIn(32, 90).dp

    val showLabel =
        appearance.showLabels &&
            config.showLabel &&
            app.displayedLabel.isNotBlank()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 82.dp)
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = visual.containerColor,
        contentColor = visual.textColor,
        shadowElevation = visual.shadowElevation,
        border = if (visual.borderWidth > 0.dp) {
            BorderStroke(
                visual.borderWidth,
                visual.borderColor
            )
        } else {
            null
        }
    ) {
        Box(Modifier.fillMaxWidth()) {
            if (visual.showHighlight) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(
                                        alpha = visual.highlightOpacity
                                    ),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp,
                        vertical = 12.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ConfiguredAppIcon(
                    icon = app.icon,
                    fallbackText = app.displayedLabel.ifBlank {
                        app.originalLabel
                    },
                    size = iconSize,
                    textColor = visual.textColor
                )

                if (showLabel) {
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = app.displayedLabel,
                        modifier = Modifier.weight(1f),
                        color = visual.textColor,
                        fontSize = appearance.textSize
                            .coerceIn(10f, 32f)
                            .sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }

                Icon(
                    Icons.Filled.KeyboardArrowRight,
                    contentDescription = "Abrir aplicacion",
                    tint = visual.textColor.copy(alpha = 0.70f),
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

@Composable
private fun ConfiguredAppIcon(
    icon: ImageBitmap?,
    fallbackText: String,
    size: Dp,
    textColor: Color
) {
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = fallbackText,
            modifier = Modifier.size(size),
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.High
        )
    } else {
        Surface(
            modifier = Modifier.size(size),
            shape = RoundedCornerShape(18.dp),
            color = Color.White.copy(alpha = 0.20f),
            border = BorderStroke(
                1.dp,
                Color.White.copy(alpha = 0.30f)
            )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = fallbackText
                        .trim()
                        .firstOrNull()
                        ?.uppercase()
                        ?: "?",
                    color = textColor,
                    fontSize = (size.value * 0.38f).sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun resolveCardVisual(
    appearance: LauncherAppearance,
    config: AppDisplayConfig
): CardVisual {
    val individual = !config.useGlobalStyle
    val style = if (individual) {
        config.cardStyle ?: appearance.cardStyle
    } else {
        appearance.cardStyle
    }

    val opacity = if (individual) {
        config.cardOpacity ?: appearance.cardOpacity
    } else {
        appearance.cardOpacity
    }.coerceIn(0f, 1f)

    val customColor = config.cardColor?.let(::colorFromLong)
    val globalTextColor = colorFromLong(appearance.textColor)

    return when (style) {
        CardStyle.NONE -> CardVisual(
            containerColor = Color.Transparent,
            borderColor = Color.Transparent,
            textColor = globalTextColor,
            borderWidth = 0.dp,
            shadowElevation = 0.dp,
            showHighlight = false,
            highlightOpacity = 0f
        )

        CardStyle.TRANSPARENT -> CardVisual(
            containerColor = (customColor ?: Color.White).copy(
                alpha = opacity * 0.32f
            ),
            borderColor = Color.White.copy(
                alpha = appearance.cardBorderOpacity * 0.65f
            ),
            textColor = globalTextColor,
            borderWidth = 1.dp,
            shadowElevation = (
                appearance.cardShadow * 0.35f
            ).dp,
            showHighlight = false,
            highlightOpacity = 0f
        )

        CardStyle.GLASS -> CardVisual(
            containerColor = (customColor ?: Color.White).copy(
                alpha = opacity * 0.72f
            ),
            borderColor = Color.White.copy(
                alpha = appearance.cardBorderOpacity.coerceIn(0f, 1f)
            ),
            textColor = globalTextColor,
            borderWidth = 1.dp,
            shadowElevation = appearance.cardShadow
                .coerceIn(0f, 24f)
                .dp,
            showHighlight = true,
            highlightOpacity = appearance.cardBrightness
                .coerceIn(0f, 1f) * 0.45f
        )

        CardStyle.GLASS_STRONG -> CardVisual(
            containerColor = (customColor ?: Color.White).copy(
                alpha = (
                    0.22f + opacity * 0.70f
                ).coerceIn(0f, 0.95f)
            ),
            borderColor = Color.White.copy(
                alpha = (
                    appearance.cardBorderOpacity + 0.18f
                ).coerceIn(0f, 1f)
            ),
            textColor = globalTextColor,
            borderWidth = 1.5.dp,
            shadowElevation = (
                appearance.cardShadow + 2f
            ).coerceIn(0f, 24f).dp,
            showHighlight = true,
            highlightOpacity = (
                appearance.cardBrightness + 0.16f
            ).coerceIn(0f, 0.65f)
        )

        CardStyle.SOLID_LIGHT -> CardVisual(
            containerColor = (customColor ?: Color.White).copy(
                alpha = opacity.coerceAtLeast(0.88f)
            ),
            borderColor = Color.Black.copy(alpha = 0.08f),
            textColor = globalTextColor,
            borderWidth = 1.dp,
            shadowElevation = appearance.cardShadow.dp,
            showHighlight = false,
            highlightOpacity = 0f
        )

        CardStyle.SOLID_DARK -> CardVisual(
            containerColor = (
                customColor ?: Color(0xFF171A16)
            ).copy(
                alpha = opacity.coerceAtLeast(0.88f)
            ),
            borderColor = Color.White.copy(alpha = 0.15f),
            textColor = if (customColor == null) {
                Color.White
            } else {
                globalTextColor
            },
            borderWidth = 1.dp,
            shadowElevation = appearance.cardShadow.dp,
            showHighlight = false,
            highlightOpacity = 0f
        )
    }
}

@Composable
private fun LoadingState(
    padding: PaddingValues
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.92f)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(14.dp))
                Text(
                    "Cargando launcher...",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    padding: PaddingValues,
    onConfigure: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 390.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color.White.copy(alpha = 0.94f),
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(76.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Apps,
                            contentDescription = null,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    "No hay aplicaciones configuradas",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    "Selecciona las aplicaciones que apareceran en este dispositivo.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(22.dp))

                Button(
                    onClick = onConfigure,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Configurar aplicaciones")
                }
            }
        }
    }
}

@Composable
private fun PinCreateDialog(
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var first by rememberSaveable { mutableStateOf("") }
    var second by rememberSaveable { mutableStateOf("") }
    val valid = first.length in 4..8 && first == second

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Crear PIN de administrador") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Introduce un codigo de entre 4 y 8 digitos.")

                OutlinedTextField(
                    value = first,
                    onValueChange = {
                        first = it.filter(Char::isDigit).take(8)
                    },
                    singleLine = true,
                    label = { Text("PIN") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = second,
                    onValueChange = {
                        second = it.filter(Char::isDigit).take(8)
                    },
                    singleLine = true,
                    label = { Text("Repetir PIN") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(first) }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancelar")
            }
        },
        properties = DialogProperties(
            dismissOnClickOutside = false
        )
    )
}

@Composable
private fun PinVerifyDialog(
    onCancel: () -> Unit,
    onResult: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var pin by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Acceso de administrador") },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    pin = it.filter(Char::isDigit).take(8)
                },
                singleLine = true,
                label = { Text("PIN de administrador") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword
                ),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = pin.length in 4..8,
                onClick = {
                    onResult(Pin.verifyPin(context, pin))
                }
            ) {
                Text("Aceptar")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancelar")
            }
        }
    )
}

private fun loadLauncherScreenData(
    context: Context
): LauncherScreenData {
    val packageManager = context.packageManager
    val appearance = Prefs.loadAppearance(context)
    val title = Prefs.loadTitle(context) ?: "GVAM MDM"
    val configs = Prefs.loadAppDisplayConfigs(context)
    val packages = Prefs.loadSelectedPackagesOrdered(context)

    val apps = packages.mapIndexed { index, packageName ->
        val originalLabel = runCatching {
            val appInfo = packageManager.getApplicationInfo(
                packageName,
                0
            )
            packageManager
                .getApplicationLabel(appInfo)
                .toString()
                .trim()
                .ifBlank { packageName }
        }.getOrDefault(packageName)

        val config = (
            configs[packageName] ?: AppDisplayConfig(
                packageName = packageName,
                position = index
            )
        ).copy(position = index)

        val customIcon = loadBitmapFromUri(
            context = context,
            uriText = config.customIconUri,
            maximumSize = 512
        )

        val originalIcon = if (customIcon == null) {
            runCatching {
                val drawable = packageManager.getApplicationIcon(
                    packageName
                )
                (drawable as? BitmapDrawable)?.bitmap
                    ?: drawable.toBitmap(
                        width = 192,
                        height = 192,
                        config = Bitmap.Config.ARGB_8888
                    )
            }.getOrNull()
        } else {
            null
        }

        LauncherAppEntry(
            packageName = packageName,
            originalLabel = originalLabel,
            displayConfig = config,
            icon = (customIcon ?: originalIcon)?.asImageBitmap()
        )
    }

    return LauncherScreenData(
        title = title,
        appearance = appearance.copy(
            showTitle = Prefs.isTitleVisible(context)
        ),
        apps = apps
    )
}

private fun loadBitmapFromUri(
    context: Context,
    uriText: String?,
    maximumSize: Int
): Bitmap? {
    if (uriText.isNullOrBlank()) return null

    return runCatching {
        context.contentResolver
            .openInputStream(Uri.parse(uriText))
            ?.use { input -> BitmapFactory.decodeStream(input) }
            ?.let {
                resizeBitmap(it, maximumSize)
            }
    }.getOrNull()
}

private fun resizeBitmap(
    bitmap: Bitmap,
    maximumSize: Int
): Bitmap {
    val largest = maxOf(bitmap.width, bitmap.height)

    if (largest <= maximumSize || largest <= 0) {
        return bitmap
    }

    val scale = maximumSize.toFloat() / largest.toFloat()

    return Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * scale).toInt().coerceAtLeast(1),
        (bitmap.height * scale).toInt().coerceAtLeast(1),
        true
    )
}

private fun launchSelectedApp(
    context: Context,
    packageManager: PackageManager,
    packageName: String,
    label: String
) {
    val launchIntent =
        packageManager.getLaunchIntentForPackage(packageName)

    if (launchIntent == null) {
        Toast.makeText(
            context,
            "$label no tiene una pantalla de inicio disponible",
            Toast.LENGTH_LONG
        ).show()
        return
    }

    launchIntent.flags =
        launchIntent.flags and
            Intent.FLAG_ACTIVITY_NEW_TASK.inv()

    launchIntent.addFlags(
        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
    )

    runCatching {
        context.startActivity(launchIntent)
    }.onFailure { error ->
        Toast.makeText(
            context,
            "No se pudo abrir $label: ${
                error.message ?: "error desconocido"
            }",
            Toast.LENGTH_LONG
        ).show()
    }
}

private fun contentScale(
    scale: BackgroundScale
): ContentScale =
    when (scale) {
        BackgroundScale.CROP -> ContentScale.Crop
        BackgroundScale.FIT -> ContentScale.Fit
        BackgroundScale.STRETCH -> ContentScale.FillBounds
    }

private fun titleAlignment(
    alignment: HeaderTitleAlignment
): Alignment =
    when (alignment) {
        HeaderTitleAlignment.START -> Alignment.CenterStart
        HeaderTitleAlignment.CENTER -> Alignment.Center
        HeaderTitleAlignment.END -> Alignment.CenterEnd
    }

private fun titleTextAlign(
    alignment: HeaderTitleAlignment
): TextAlign =
    when (alignment) {
        HeaderTitleAlignment.START -> TextAlign.Start
        HeaderTitleAlignment.CENTER -> TextAlign.Center
        HeaderTitleAlignment.END -> TextAlign.End
    }

private fun colorFromLong(value: Long): Color =
    Color(value.toInt())
