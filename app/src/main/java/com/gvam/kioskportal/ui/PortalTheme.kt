package com.gvam.kioskportal.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PortalColors = lightColorScheme(
    primary = Color(0xFF5E8D32),
    onPrimary = Color.White,

    primaryContainer = Color(0xFFDDF1C9),
    onPrimaryContainer = Color(0xFF1B3408),

    secondary = Color(0xFF52634A),
    onSecondary = Color.White,

    secondaryContainer = Color(0xFFD5E8CB),
    onSecondaryContainer = Color(0xFF10200C),

    background = Color(0xFFF7F9F4),
    onBackground = Color(0xFF1B1C19),

    surface = Color.White,
    onSurface = Color(0xFF1B1C19),

    surfaceVariant = Color(0xFFE1E8DB),
    onSurfaceVariant = Color(0xFF43483F),

    outline = Color(0xFF73796E),
    outlineVariant = Color(0xFFC3C8BC),

    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val PortalTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),

    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 27.sp
    ),

    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp
    ),

    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 23.sp
    ),

    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),

    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),

    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    )
)

private val PortalShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Tema Compose compartido por la versión móvil y tótem.
 */
@Composable
fun PortalTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PortalColors,
        typography = PortalTypography,
        shapes = PortalShapes,
        content = content
    )
}