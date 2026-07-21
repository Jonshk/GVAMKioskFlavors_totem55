@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.gvam.kioskportal.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gvam.kioskportal.R
import com.gvam.kioskportal.model.BackgroundMode
import com.gvam.kioskportal.model.BackgroundScale
import com.gvam.kioskportal.model.CardStyle
import com.gvam.kioskportal.model.HeaderPosition
import com.gvam.kioskportal.model.IconSizeMode
import com.gvam.kioskportal.model.LayoutMode
import com.gvam.kioskportal.model.HeaderStyle
import com.gvam.kioskportal.model.HeaderTitleAlignment
import com.gvam.kioskportal.model.LauncherAppearance
import com.gvam.kioskportal.util.Prefs
import kotlin.math.roundToInt

class AppearanceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PortalTheme {
                AppearanceScreen(
                    onFinished = { finish() }
                )
            }
        }
    }
}

@Composable
private fun AppearanceScreen(
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val initial = remember { Prefs.loadAppearance(context) }

    var appearance by remember { mutableStateOf(initial) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    fun closeEditor() {
        if (appearance != initial) showDiscardDialog = true
        else onFinished()
    }

    val backgroundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            persistReadPermission(context, uri)
            appearance = appearance.copy(
                backgroundMode = BackgroundMode.CUSTOM_IMAGE,
                backgroundUri = uri.toString()
            )
        }
    }

    val headerImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            persistReadPermission(context, uri)
            appearance = appearance.copy(
                headerStyle = HeaderStyle.CUSTOM_IMAGE,
                headerImageUri = uri.toString()
            )
        }
    }

    BackHandler { closeEditor() }

    Scaffold(
        containerColor = Color(0xFFF3F6F0),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            EditorTopBar(onClose = ::closeEditor)
        },
        bottomBar = {
            EditorBottomBar(
                onSave = {
                    Prefs.saveAppearance(context, appearance)
                    onFinished()
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                horizontal = 14.dp,
                vertical = 14.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            stickyHeader {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFF3F6F0)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        AppearancePreview(appearance)
                    }
                }
            }

            item {
                SettingsSection(
                    title = "Fondo",
                    description = "Imagen, color y ajuste del fondo principal.",
                    icon = Icons.Filled.Wallpaper
                ) {
                    ChoiceRow(
                        values = BackgroundMode.values().toList(),
                        selected = appearance.backgroundMode,
                        label = ::backgroundModeLabel,
                        onSelected = {
                            appearance = appearance.copy(backgroundMode = it)
                        }
                    )

                    when (appearance.backgroundMode) {
                        BackgroundMode.CUSTOM_IMAGE -> {
                            Button(
                                onClick = {
                                    backgroundPicker.launch(arrayOf("image/*"))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Wallpaper, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (appearance.backgroundUri.isNullOrBlank()) {
                                        "Elegir imagen"
                                    } else {
                                        "Cambiar imagen"
                                    }
                                )
                            }
                        }

                        BackgroundMode.SOLID_COLOR -> {
                            HexColorEditor(
                                title = "Color del fondo",
                                value = appearance.backgroundColor,
                                onChanged = {
                                    appearance = appearance.copy(backgroundColor = it)
                                }
                            )
                        }

                        BackgroundMode.DEFAULT_IMAGE,
                        BackgroundMode.NONE -> Unit
                    }

                    if (
                        appearance.backgroundMode == BackgroundMode.DEFAULT_IMAGE ||
                        appearance.backgroundMode == BackgroundMode.CUSTOM_IMAGE
                    ) {
                        SettingLabel("Ajuste de imagen")
                        ChoiceRow(
                            values = BackgroundScale.values().toList(),
                            selected = appearance.backgroundScale,
                            label = ::backgroundScaleLabel,
                            onSelected = {
                                appearance = appearance.copy(backgroundScale = it)
                            }
                        )
                    }

                    SliderSetting(
                        title = "Oscurecimiento",
                        value = appearance.backgroundOverlay,
                        range = 0f..1f,
                        shownValue = percent(appearance.backgroundOverlay),
                        onChanged = {
                            appearance = appearance.copy(backgroundOverlay = it)
                        }
                    )
                }
            }

            item {
                SettingsSection(
                    title = "Tarjetas",
                    description = "Fondo, transparencia, borde, sombra y forma.",
                    icon = Icons.Filled.Palette
                ) {
                    ChoiceRow(
                        values = CardStyle.values().toList(),
                        selected = appearance.cardStyle,
                        label = ::cardStyleLabel,
                        onSelected = {
                            appearance = appearance.copy(cardStyle = it)
                        }
                    )

                    SliderSetting(
                        title = "Opacidad",
                        value = appearance.cardOpacity,
                        range = 0f..1f,
                        shownValue = percent(appearance.cardOpacity),
                        onChanged = {
                            appearance = appearance.copy(cardOpacity = it)
                        }
                    )

                    SliderSetting(
                        title = "Brillo del cristal",
                        value = appearance.cardBrightness,
                        range = 0f..1f,
                        shownValue = percent(appearance.cardBrightness),
                        onChanged = {
                            appearance = appearance.copy(cardBrightness = it)
                        }
                    )

                    SliderSetting(
                        title = "Profundidad visual",
                        description = "Este valor prepara la intensidad del efecto glass.",
                        value = appearance.cardBlurRadius,
                        range = 0f..40f,
                        shownValue = appearance.cardBlurRadius.roundToInt().toString(),
                        onChanged = {
                            appearance = appearance.copy(cardBlurRadius = it)
                        }
                    )

                    SliderSetting(
                        title = "Borde",
                        value = appearance.cardBorderOpacity,
                        range = 0f..1f,
                        shownValue = percent(appearance.cardBorderOpacity),
                        onChanged = {
                            appearance = appearance.copy(cardBorderOpacity = it)
                        }
                    )

                    SliderSetting(
                        title = "Redondeo",
                        value = appearance.cardCornerRadius,
                        range = 0f..60f,
                        shownValue = dpText(appearance.cardCornerRadius),
                        onChanged = {
                            appearance = appearance.copy(cardCornerRadius = it)
                        }
                    )

                    SliderSetting(
                        title = "Sombra",
                        value = appearance.cardShadow,
                        range = 0f..24f,
                        shownValue = dpText(appearance.cardShadow),
                        onChanged = {
                            appearance = appearance.copy(cardShadow = it)
                        }
                    )
                }
            }

            item {
                SettingsSection(
                    title = "Distribuci\u00F3n",
                    description = "Modo responsive, una columna, dos columnas o tres columnas.",
                    icon = Icons.Filled.ViewModule
                ) {
                    SettingLabel("Modo de distribuci\u00F3n")

                    ChoiceRow(
                        values = LayoutMode.values().toList(),
                        selected = appearance.layoutMode,
                        label = ::layoutModeLabel,
                        onSelected = {
                            appearance = appearance.copy(
                                layoutMode = it
                            )
                        }
                    )

                    Text(
                        text = layoutModeDescription(
                            appearance.layoutMode
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SliderSetting(
                        title = "Separaci\u00F3n",
                        value = appearance.cardSpacing,
                        range = 0f..40f,
                        shownValue = dpText(appearance.cardSpacing),
                        onChanged = {
                            appearance = appearance.copy(cardSpacing = it)
                        }
                    )

                    SliderSetting(
                        title = "Altura m\u00EDnima de tarjeta",
                        description = "La tarjeta crece autom\u00E1ticamente si el icono necesita m\u00E1s espacio.",
                        value = appearance.cardHeight,
                        range = 90f..300f,
                        shownValue = dpText(appearance.cardHeight),
                        onChanged = {
                            appearance = appearance.copy(cardHeight = it)
                        }
                    )
                }
            }

            item {
                SettingsSection(
                    title = "Iconos y nombres",
                    description = "Tamano global de iconos y texto.",
                    icon = Icons.Filled.Apps
                ) {
                    IconSizeSetting(
                        mode = appearance.iconSizeMode,
                        customSize = appearance.iconSize,
                        layoutMode = appearance.layoutMode,
                        onModeChanged = {
                            appearance = appearance.copy(
                                iconSizeMode = it
                            )
                        },
                        onCustomSizeChanged = {
                            appearance = appearance.copy(
                                iconSize = it.coerceIn(32, 160)
                            )
                        }
                    )

                    SwitchSetting(
                        title = "Mostrar nombres",
                        description = "Las aplicaciones con ajuste individual pueden ocultarlo.",
                        checked = appearance.showLabels,
                        onChanged = {
                            appearance = appearance.copy(showLabels = it)
                        }
                    )

                    SliderSetting(
                        title = "Tamano del texto",
                        value = appearance.textSize,
                        range = 10f..32f,
                        shownValue = "${appearance.textSize.roundToInt()} sp",
                        enabled = appearance.showLabels,
                        onChanged = {
                            appearance = appearance.copy(textSize = it)
                        }
                    )

                    HexColorEditor(
                        title = "Color de los nombres",
                        value = appearance.textColor,
                        enabled = appearance.showLabels,
                        onChanged = {
                            appearance = appearance.copy(textColor = it)
                        }
                    )
                }
            }

            item {
                SettingsSection(
                    title = "Cabecera",
                    description = "Visibilidad, posicion, fondo, color, titulo y botones.",
                    icon = Icons.Filled.Palette
                ) {
                    SwitchSetting(
                        title = "Mostrar cabecera",
                        description = "Oculta completamente la barra superior o inferior.",
                        checked = appearance.headerVisible,
                        onChanged = {
                            appearance = appearance.copy(headerVisible = it)
                        }
                    )

                    if (!appearance.headerVisible) {
                        HorizontalDivider()

                        SettingLabel(
                            "Accesos ocultos"
                        )

                        Text(
                            text = "La cabecera y todos sus controles quedan " +
                                "completamente ocultos. No aparece ningun boton " +
                                "flotante ni indicador sobre la pantalla.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = "Doble toque en la esquina superior izquierda: " +
                                "cambiar la distribuci\u00F3n.\n\n" +
                                "Doble toque en la esquina superior derecha: " +
                                "solicitar el PIN y abrir el men\u00FA administrativo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        HorizontalDivider()
                    }

                    ChoiceRow(
                        values = HeaderPosition.values().toList(),
                        selected = appearance.headerPosition,
                        label = ::headerPositionLabel,
                        enabled = appearance.headerVisible,
                        onSelected = {
                            appearance = appearance.copy(headerPosition = it)
                        }
                    )

                    SettingLabel("Estilo de cabecera")
                    ChoiceRow(
                        values = HeaderStyle.values().toList(),
                        selected = appearance.headerStyle,
                        label = ::headerStyleLabel,
                        enabled = appearance.headerVisible,
                        onSelected = {
                            appearance = appearance.copy(headerStyle = it)
                        }
                    )

                    if (appearance.headerStyle == HeaderStyle.CUSTOM_IMAGE) {
                        Button(
                            onClick = {
                                headerImagePicker.launch(arrayOf("image/*"))
                            },
                            enabled = appearance.headerVisible,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Wallpaper, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (appearance.headerImageUri.isNullOrBlank()) {
                                    "Elegir imagen de cabecera"
                                } else {
                                    "Cambiar imagen de cabecera"
                                }
                            )
                        }

                        ChoiceRow(
                            values = BackgroundScale.values().toList(),
                            selected = appearance.headerImageScale,
                            label = ::backgroundScaleLabel,
                            enabled = appearance.headerVisible,
                            onSelected = {
                                appearance = appearance.copy(headerImageScale = it)
                            }
                        )
                    } else {
                        HexColorEditor(
                            title = "Color de la cabecera",
                            value = appearance.headerColor,
                            enabled = appearance.headerVisible,
                            onChanged = {
                                appearance = appearance.copy(headerColor = it)
                            }
                        )
                    }

                    SliderSetting(
                        title = "Opacidad de cabecera",
                        value = appearance.topBarOpacity,
                        range = 0f..1f,
                        shownValue = percent(appearance.topBarOpacity),
                        enabled = appearance.headerVisible,
                        onChanged = {
                            appearance = appearance.copy(topBarOpacity = it)
                        }
                    )

                    SliderSetting(
                        title = "Altura",
                        value = appearance.headerHeight,
                        range = 48f..120f,
                        shownValue = dpText(appearance.headerHeight),
                        enabled = appearance.headerVisible,
                        onChanged = {
                            appearance = appearance.copy(headerHeight = it)
                        }
                    )

                    SliderSetting(
                        title = "Redondeo de cabecera",
                        value = appearance.headerCornerRadius,
                        range = 0f..60f,
                        shownValue = dpText(appearance.headerCornerRadius),
                        enabled = appearance.headerVisible,
                        onChanged = {
                            appearance = appearance.copy(headerCornerRadius = it)
                        }
                    )

                    SliderSetting(
                        title = "Margen horizontal",
                        value = appearance.headerHorizontalMargin,
                        range = 0f..40f,
                        shownValue = dpText(appearance.headerHorizontalMargin),
                        enabled = appearance.headerVisible,
                        onChanged = {
                            appearance = appearance.copy(headerHorizontalMargin = it)
                        }
                    )

                    SliderSetting(
                        title = "Margen vertical",
                        value = appearance.headerVerticalMargin,
                        range = 0f..30f,
                        shownValue = dpText(appearance.headerVerticalMargin),
                        enabled = appearance.headerVisible,
                        onChanged = {
                            appearance = appearance.copy(headerVerticalMargin = it)
                        }
                    )

                    SliderSetting(
                        title = "Sombra de cabecera",
                        value = appearance.headerShadow,
                        range = 0f..24f,
                        shownValue = dpText(appearance.headerShadow),
                        enabled = appearance.headerVisible,
                        onChanged = {
                            appearance = appearance.copy(headerShadow = it)
                        }
                    )

                    SliderSetting(
                        title = "Borde de cabecera",
                        value = appearance.headerBorderOpacity,
                        range = 0f..1f,
                        shownValue = percent(appearance.headerBorderOpacity),
                        enabled = appearance.headerVisible,
                        onChanged = {
                            appearance = appearance.copy(headerBorderOpacity = it)
                        }
                    )

                    HorizontalDivider()

                    SwitchSetting(
                        title = "Mostrar titulo",
                        description = "Permite ocultar solo el texto.",
                        checked = appearance.showTitle,
                        enabled = appearance.headerVisible,
                        onChanged = {
                            appearance = appearance.copy(showTitle = it)
                        }
                    )

                    HexColorEditor(
                        title = "Color del titulo",
                        value = appearance.headerTitleColor,
                        enabled = appearance.headerVisible && appearance.showTitle,
                        onChanged = {
                            appearance = appearance.copy(headerTitleColor = it)
                        }
                    )

                    SliderSetting(
                        title = "Tamano del titulo",
                        value = appearance.headerTitleSize,
                        range = 12f..36f,
                        shownValue = "${appearance.headerTitleSize.roundToInt()} sp",
                        enabled = appearance.headerVisible && appearance.showTitle,
                        onChanged = {
                            appearance = appearance.copy(headerTitleSize = it)
                        }
                    )

                    ChoiceRow(
                        values = HeaderTitleAlignment.values().toList(),
                        selected = appearance.headerTitleAlignment,
                        label = ::titleAlignmentLabel,
                        enabled = appearance.headerVisible && appearance.showTitle,
                        onSelected = {
                            appearance = appearance.copy(headerTitleAlignment = it)
                        }
                    )

                    SwitchSetting(
                        title = "Titulo en negrita",
                        description = "Activa o desactiva el peso fuerte.",
                        checked = appearance.headerTitleBold,
                        enabled = appearance.headerVisible && appearance.showTitle,
                        onChanged = {
                            appearance = appearance.copy(headerTitleBold = it)
                        }
                    )

                    SwitchSetting(
                        title = "Mostrar selector de distribuci\u00F3n",
                        description = "Bot\u00F3n para elegir autom\u00E1tica, una, dos o tres columnas.",
                        checked = appearance.showViewToggle,
                        enabled = appearance.headerVisible,
                        onChanged = {
                            appearance = appearance.copy(showViewToggle = it)
                        }
                    )

                    SwitchSetting(
                        title = "Mostrar menu",
                        description = "Boton de tres puntos del menu administrativo.",
                        checked = appearance.showMenuButton,
                        enabled = appearance.headerVisible,
                        onChanged = {
                            appearance = appearance.copy(showMenuButton = it)
                        }
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Restablecer apariencia")
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Cerrar sin guardar") },
            text = { Text("Los cambios realizados no se guardaran.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onFinished()
                    }
                ) {
                    Text("Cerrar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Restablecer apariencia") },
            text = {
                Text("Se recuperaran todos los valores predeterminados.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        appearance = LauncherAppearance()
                        showResetDialog = false
                    }
                ) {
                    Text("Restablecer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun EditorTopBar(
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .height(70.dp),
        shape = RoundedCornerShape(25.dp),
        color = Color(0xFF344E25),
        contentColor = Color.White,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Cerrar")
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Apariencia",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Personalizaci\u00F3n general",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD5F6B6)
                )
            }

            Icon(
                Icons.Filled.Palette,
                contentDescription = null,
                modifier = Modifier.padding(end = 12.dp)
            )
        }
    }
}

@Composable
private fun EditorBottomBar(
    onSave: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(52.dp)
        ) {
            Icon(Icons.Filled.Check, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Guardar apariencia", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AppearancePreview(
    appearance: LauncherAppearance
) {
    val context = LocalContext.current
    val backgroundBitmap = remember(appearance.backgroundUri) {
        loadBitmap(context, appearance.backgroundUri, 1400)
    }
    val headerBitmap = remember(appearance.headerImageUri) {
        loadBitmap(context, appearance.headerImageUri, 1000)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = Color.Black,
        shadowElevation = 3.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(26.dp))
        ) {
            PreviewBackground(appearance, backgroundBitmap)

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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = when {
                    !appearance.headerVisible -> Arrangement.Center
                    appearance.headerPosition == HeaderPosition.TOP ->
                        Arrangement.Top
                    else -> Arrangement.Bottom
                }
            ) {
                if (
                    appearance.headerVisible &&
                    appearance.headerPosition == HeaderPosition.TOP
                ) {
                    PreviewHeader(appearance, headerBitmap)
                    Spacer(Modifier.height(8.dp))
                }

                PreviewAppsLayout(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    appearance = appearance
                )

                if (
                    appearance.headerVisible &&
                    appearance.headerPosition == HeaderPosition.BOTTOM
                ) {
                    Spacer(Modifier.height(8.dp))
                    PreviewHeader(appearance, headerBitmap)
                }
            }

        }
    }
}


@Composable
private fun PreviewAppsLayout(
    modifier: Modifier,
    appearance: LauncherAppearance
) {
    val previewApps = listOf(
        "Galer\u00EDas" to "G",
        "Encuesta" to "E"
    )

    BoxWithConstraints(modifier = modifier) {
        val spacing = appearance.cardSpacing
            .coerceIn(4f, 14f)
            .dp

        val columns = resolvePreviewColumns(
            mode = appearance.layoutMode,
            availableWidth = maxWidth,
            appCount = previewApps.size
        )

        val cardWidth = if (columns == 1) {
            minOf(
                maxWidth * 0.72f,
                230.dp
            )
        } else {
            (
                maxWidth -
                    spacing * (columns - 1).toFloat()
                ) / columns.toFloat()
        }

        val rows = previewApps.chunked(columns)

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(
                spacing,
                Alignment.CenterVertically
            )
        ) {
            rows.forEach { rowApps ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        spacing,
                        Alignment.CenterHorizontally
                    )
                ) {
                    rowApps.forEach { (label, initial) ->
                        PreviewCard(
                            modifier = Modifier.width(cardWidth),
                            label = label,
                            initial = initial,
                            appearance = appearance,
                            layoutColumns = columns,
                            appCount = previewApps.size
                        )
                    }
                }
            }
        }
    }
}

private fun resolvePreviewColumns(
    mode: LayoutMode,
    availableWidth: Dp,
    appCount: Int
): Int {
    val requested = when (mode) {
        LayoutMode.ADAPTIVE -> {
            if (availableWidth < 500.dp) {
                1
            } else {
                2
            }
        }

        LayoutMode.ONE_COLUMN -> 1
        LayoutMode.TWO_COLUMNS -> 2
        LayoutMode.THREE_COLUMNS -> 3
    }

    return requested.coerceAtMost(
        appCount.coerceAtLeast(1)
    )
}

@Composable
private fun PreviewBackground(
    appearance: LauncherAppearance,
    customBitmap: Bitmap?
) {
    val scale = contentScale(appearance.backgroundScale)

    when (appearance.backgroundMode) {
        BackgroundMode.DEFAULT_IMAGE -> {
            Image(
                painter = painterResource(R.drawable.kiosk_default_bg),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = scale
            )
        }

        BackgroundMode.CUSTOM_IMAGE -> {
            if (customBitmap != null) {
                Image(
                    bitmap = customBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = scale,
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
                    .background(colorFromLong(appearance.backgroundColor))
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
}

@Composable
private fun PreviewHeader(
    appearance: LauncherAppearance,
    customBitmap: Bitmap?
) {
    val shape = RoundedCornerShape(
        appearance.headerCornerRadius.coerceIn(0f, 60f).dp
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = appearance.headerHorizontalMargin
                    .coerceIn(0f, 20f)
                    .dp,
                vertical = appearance.headerVerticalMargin
                    .coerceIn(0f, 12f)
                    .dp
            )
            .height(
                appearance.headerHeight
                    .coerceIn(48f, 90f)
                    .dp
            ),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(
            1.dp,
            Color.White.copy(
                alpha = appearance.headerBorderOpacity.coerceIn(0f, 1f)
            )
        ),
        shadowElevation = appearance.headerShadow.coerceIn(0f, 12f).dp
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
        ) {
            HeaderBackground(
                appearance = appearance,
                customBitmap = customBitmap
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (appearance.showViewToggle) {
                    Icon(
                        Icons.Filled.ViewModule,
                        contentDescription = null,
                        tint = colorFromLong(appearance.headerTitleColor),
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Spacer(Modifier.size(22.dp))
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = titleBoxAlignment(
                        appearance.headerTitleAlignment
                    )
                ) {
                    if (appearance.showTitle) {
                        Text(
                            text = "Vista previa",
                            color = colorFromLong(appearance.headerTitleColor),
                            fontSize = appearance.headerTitleSize
                                .coerceIn(12f, 28f)
                                .sp,
                            fontWeight = if (appearance.headerTitleBold) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (appearance.showMenuButton) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = null,
                        tint = colorFromLong(appearance.headerTitleColor),
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Spacer(Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun HeaderBackground(
    appearance: LauncherAppearance,
    customBitmap: Bitmap?
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
            if (customBitmap != null) {
                Image(
                    bitmap = customBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale(appearance.headerImageScale),
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
private fun PreviewCard(
    modifier: Modifier,
    label: String,
    initial: String,
    appearance: LauncherAppearance,
    layoutColumns: Int,
    appCount: Int
) {
    val cardColor = when (appearance.cardStyle) {
        CardStyle.NONE -> Color.Transparent
        CardStyle.TRANSPARENT ->
            Color.White.copy(alpha = appearance.cardOpacity * 0.30f)
        CardStyle.GLASS ->
            Color.White.copy(alpha = appearance.cardOpacity * 0.72f)
        CardStyle.GLASS_STRONG ->
            Color.White.copy(
                alpha = (0.22f + appearance.cardOpacity * 0.70f)
                    .coerceIn(0f, 0.95f)
            )
        CardStyle.SOLID_LIGHT ->
            Color.White.copy(alpha = appearance.cardOpacity.coerceAtLeast(0.88f))
        CardStyle.SOLID_DARK ->
            Color(0xFF171A16).copy(
                alpha = appearance.cardOpacity.coerceAtLeast(0.88f)
            )
    }

    val textColor = if (appearance.cardStyle == CardStyle.SOLID_DARK) {
        Color.White
    } else {
        colorFromLong(appearance.textColor)
    }

    val resolvedIconSize = resolveGlobalIconSize(
        appearance = appearance,
        layoutColumns = layoutColumns,
        appCount = appCount
    )

    val previewIconSize = when {
        layoutColumns <= 1 ->
            resolvedIconSize.coerceIn(28, 44)

        layoutColumns == 2 ->
            resolvedIconSize.coerceIn(32, 60)

        else ->
            resolvedIconSize.coerceIn(28, 50)
    }

    val previewCardHeight = if (layoutColumns <= 1) {
        78.dp
    } else {
        105.dp
    }

    Surface(
        modifier = modifier.height(previewCardHeight),
        shape = RoundedCornerShape(
            appearance.cardCornerRadius.coerceIn(0f, 60f).dp
        ),
        color = cardColor,
        border = if (appearance.cardStyle == CardStyle.NONE) {
            null
        } else {
            BorderStroke(
                1.dp,
                Color.White.copy(
                    alpha = appearance.cardBorderOpacity.coerceIn(0f, 1f)
                )
            )
        },
        shadowElevation = appearance.cardShadow.coerceIn(0f, 12f).dp
    ) {
        Box(Modifier.fillMaxSize()) {
            if (
                appearance.cardStyle == CardStyle.GLASS ||
                appearance.cardStyle == CardStyle.GLASS_STRONG
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(
                                        alpha = appearance.cardBrightness * 0.45f
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
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    modifier = Modifier.size(
                        previewIconSize.dp
                    ),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF78B23A)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            initial,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (appearance.showLabels) {
                    Spacer(Modifier.height(7.dp))
                    Text(
                        label,
                        color = textColor,
                        fontSize = appearance.textSize.coerceIn(10f, 22f).sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        ),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun SwitchSetting(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (enabled) 1f else 0.45f
                )
            )
        }

        Spacer(Modifier.width(12.dp))

        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onChanged
        )
    }
}

@Composable
private fun SliderSetting(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    shownValue: String,
    onChanged: (Float) -> Unit,
    description: String? = null,
    steps: Int = 0,
    enabled: Boolean = true
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (!description.isNullOrBlank()) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                shownValue,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChanged,
            valueRange = range,
            steps = steps,
            enabled = enabled
        )
    }
}

@Composable
private fun IconSizeSetting(
    mode: IconSizeMode,
    customSize: Int,
    layoutMode: LayoutMode,
    onModeChanged: (IconSizeMode) -> Unit,
    onCustomSizeChanged: (Int) -> Unit
) {
    val exampleColumns = when (layoutMode) {
        LayoutMode.ADAPTIVE,
        LayoutMode.ONE_COLUMN -> 1

        LayoutMode.TWO_COLUMNS -> 2
        LayoutMode.THREE_COLUMNS -> 3
    }

    val effectiveSize = resolveGlobalIconSize(
        appearance = LauncherAppearance(
            iconSizeMode = mode,
            iconSize = customSize
        ),
        layoutColumns = exampleColumns,
        appCount = 2
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Tama\u00F1o de los iconos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "El modo autom\u00E1tico adapta el icono a la distribuci\u00F3n.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                "$effectiveSize dp",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        ChoiceRow(
            values = IconSizeMode.values().toList(),
            selected = mode,
            label = ::iconSizeModeLabel,
            onSelected = onModeChanged
        )

        Text(
            text = iconSizeModeDescription(
                mode = mode,
                effectiveSize = effectiveSize
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (mode == IconSizeMode.CUSTOM) {
            Slider(
                value = customSize
                    .toFloat()
                    .coerceIn(32f, 160f),
                onValueChange = {
                    onCustomSizeChanged(
                        it.roundToInt().coerceIn(32, 160)
                    )
                },
                valueRange = 32f..160f
            )

            Text(
                text = "Ajuste exacto entre 32 y 160 dp.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun <T> ChoiceRow(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    enabled: Boolean = true,
    onSelected: (T) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        values.forEach { value ->
            FilterChip(
                selected = selected == value,
                enabled = enabled,
                onClick = { onSelected(value) },
                label = { Text(label(value)) },
                leadingIcon = {
                    if (selected == value) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            )
        }
    }
}

// ============================================================================
// SELECTOR DE COLOR
// Ademas del campo hexadecimal, al tocar el cuadrado de color (o el boton de
// paleta) se abre una rejilla de muestras para elegir el color tocando.
// Se usa en los 7 puntos de color de esta pantalla sin cambiar sus llamadas.
// ============================================================================

private val PALETA_COLORES: List<Long> = listOf(
    0xFFF44336L, 0xFFE91E63L, 0xFF9C27B0L, 0xFF673AB7L,
    0xFF3F51B5L, 0xFF2196F3L, 0xFF03A9F4L, 0xFF00BCD4L,
    0xFF009688L, 0xFF4CAF50L, 0xFF8BC34AL, 0xFFCDDC39L,
    0xFFFFEB3BL, 0xFFFFC107L, 0xFFFF9800L, 0xFFFF5722L,
    0xFF795548L, 0xFF9E9E9EL, 0xFF607D8BL, 0xFF000000L,
    0xFFFFFFFFL, 0xFF1B2117L, 0xFF344E25L, 0xFF252327L
)

@Composable
private fun HexColorEditor(
    title: String,
    value: Long,
    enabled: Boolean = true,
    onChanged: (Long) -> Unit
) {
    var text by remember(value) {
        mutableStateOf(formatColor(value))
    }
    var showPalette by remember { mutableStateOf(false) }

    val parsed = parseColor(text)
    val currentColor = parsed ?: value

    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cuadrado de color: tocable, abre la paleta.
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(enabled = enabled) { showPalette = true },
                shape = RoundedCornerShape(14.dp),
                color = colorFromLong(currentColor),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant
                )
            ) {}

            Spacer(Modifier.width(12.dp))

            OutlinedTextField(
                value = text,
                enabled = enabled,
                onValueChange = { input ->
                    val filtered = input
                        .uppercase()
                        .filter {
                            it == '#' ||
                                it in '0'..'9' ||
                                it in 'A'..'F'
                        }
                        .take(9)

                    text = filtered
                    parseColor(filtered)?.let(onChanged)
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("#AARRGGBB") },
                isError = text.isNotBlank() && parsed == null
            )

            Spacer(Modifier.width(8.dp))

            IconButton(
                onClick = { showPalette = true },
                enabled = enabled
            ) {
                Icon(
                    Icons.Filled.Palette,
                    contentDescription = "Abrir paleta de colores"
                )
            }
        }
    }

    if (showPalette) {
        AlertDialog(
            onDismissRequest = { showPalette = false },
            title = { Text("Elegir color") },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.height(220.dp)
                ) {
                    items(PALETA_COLORES) { colorValor ->
                        val seleccionado = colorValor == currentColor

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    colorFromLong(colorValor),
                                    CircleShape
                                )
                                .border(
                                    width = if (seleccionado) 3.dp else 1.dp,
                                    color = if (seleccionado) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.Gray
                                    },
                                    shape = CircleShape
                                )
                                .clickable {
                                    text = formatColor(colorValor)
                                    onChanged(colorValor)
                                    showPalette = false
                                }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPalette = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

private fun persistReadPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
}

private fun loadBitmap(
    context: Context,
    uriText: String?,
    maxSize: Int
): Bitmap? {
    if (uriText.isNullOrBlank()) return null

    return runCatching {
        context.contentResolver
            .openInputStream(Uri.parse(uriText))
            ?.use { input -> BitmapFactory.decodeStream(input) }
            ?.let { resizeBitmap(it, maxSize) }
    }.getOrNull()
}

private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
    val largest = maxOf(bitmap.width, bitmap.height)
    if (largest <= maxSize || largest <= 0) return bitmap

    val scale = maxSize.toFloat() / largest
    return Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * scale).roundToInt().coerceAtLeast(1),
        (bitmap.height * scale).roundToInt().coerceAtLeast(1),
        true
    )
}

private fun contentScale(scale: BackgroundScale): ContentScale =
    when (scale) {
        BackgroundScale.CROP -> ContentScale.Crop
        BackgroundScale.FIT -> ContentScale.Fit
        BackgroundScale.STRETCH -> ContentScale.FillBounds
    }

private fun titleBoxAlignment(
    alignment: HeaderTitleAlignment
): Alignment =
    when (alignment) {
        HeaderTitleAlignment.START -> Alignment.CenterStart
        HeaderTitleAlignment.CENTER -> Alignment.Center
        HeaderTitleAlignment.END -> Alignment.CenterEnd
    }

private fun backgroundModeLabel(value: BackgroundMode): String =
    when (value) {
        BackgroundMode.DEFAULT_IMAGE -> "Predeterminado"
        BackgroundMode.CUSTOM_IMAGE -> "Imagen"
        BackgroundMode.SOLID_COLOR -> "Color"
        BackgroundMode.NONE -> "Sin fondo"
    }

private fun backgroundScaleLabel(value: BackgroundScale): String =
    when (value) {
        BackgroundScale.CROP -> "Recortar"
        BackgroundScale.FIT -> "Encajar"
        BackgroundScale.STRETCH -> "Estirar"
    }

private fun cardStyleLabel(value: CardStyle): String =
    when (value) {
        CardStyle.NONE -> "Sin fondo"
        CardStyle.TRANSPARENT -> "Transparente"
        CardStyle.GLASS -> "Cristal"
        CardStyle.GLASS_STRONG -> "Cristal intenso"
        CardStyle.SOLID_LIGHT -> "Claro"
        CardStyle.SOLID_DARK -> "Oscuro"
    }


private fun layoutModeLabel(value: LayoutMode): String =
    when (value) {
        LayoutMode.ADAPTIVE -> "Autom\u00E1tica"
        LayoutMode.ONE_COLUMN -> "Una columna"
        LayoutMode.TWO_COLUMNS -> "Dos columnas"
        LayoutMode.THREE_COLUMNS -> "Tres columnas"
    }

private fun layoutModeDescription(value: LayoutMode): String =
    when (value) {
        LayoutMode.ADAPTIVE ->
            "En pantallas estrechas apila las tarjetas y, con m\u00E1s espacio, las coloca juntas."

        LayoutMode.ONE_COLUMN ->
            "Todas las aplicaciones quedan centradas, una debajo de otra."

        LayoutMode.TWO_COLUMNS ->
            "Coloca dos aplicaciones por fila y centra la \u00FAltima si queda sola."

        LayoutMode.THREE_COLUMNS ->
            "Coloca tres aplicaciones por fila y centra las filas incompletas."
    }

private fun iconSizeModeLabel(value: IconSizeMode): String =
    when (value) {
        IconSizeMode.AUTO -> "Autom\u00E1tico"
        IconSizeMode.SMALL -> "Peque\u00F1o"
        IconSizeMode.MEDIUM -> "Mediano"
        IconSizeMode.LARGE -> "Grande"
        IconSizeMode.CUSTOM -> "Personalizado"
    }

private fun iconSizeModeDescription(
    mode: IconSizeMode,
    effectiveSize: Int
): String =
    when (mode) {
        IconSizeMode.AUTO ->
            "Tama\u00F1o calculado seg\u00FAn la distribuci\u00F3n actual: $effectiveSize dp."

        IconSizeMode.SMALL ->
            "Tama\u00F1o compacto de 48 dp."

        IconSizeMode.MEDIUM ->
            "Tama\u00F1o equilibrado de 72 dp."

        IconSizeMode.LARGE ->
            "Tama\u00F1o grande de 104 dp."

        IconSizeMode.CUSTOM ->
            "Tama\u00F1o manual actual: $effectiveSize dp."
    }


private fun headerPositionLabel(value: HeaderPosition): String =
    when (value) {
        HeaderPosition.TOP -> "Arriba"
        HeaderPosition.BOTTOM -> "Abajo"
    }

private fun headerStyleLabel(value: HeaderStyle): String =
    when (value) {
        HeaderStyle.TRANSPARENT -> "Transparente"
        HeaderStyle.GLASS -> "Cristal"
        HeaderStyle.SOLID -> "Color solido"
        HeaderStyle.CUSTOM_IMAGE -> "Imagen"
    }

private fun titleAlignmentLabel(value: HeaderTitleAlignment): String =
    when (value) {
        HeaderTitleAlignment.START -> "Izquierda"
        HeaderTitleAlignment.CENTER -> "Centro"
        HeaderTitleAlignment.END -> "Derecha"
    }

private fun percent(value: Float): String =
    "${(value * 100).roundToInt()} %"

private fun dpText(value: Float): String =
    "${value.roundToInt()} dp"

private fun colorFromLong(value: Long): Color =
    Color(value.toInt())

private fun formatColor(value: Long): String =
    "#%08X".format(value and 0xFFFFFFFFL)

private fun parseColor(text: String): Long? {
    val clean = text.trim().removePrefix("#")
    val normalized = when (clean.length) {
        6 -> "FF$clean"
        8 -> clean
        else -> return null
    }

    return runCatching {
        normalized.toLong(16) and 0xFFFFFFFFL
    }.getOrNull()
}