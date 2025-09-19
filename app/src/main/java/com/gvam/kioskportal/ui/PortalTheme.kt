package com.gvam.kioskportal.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color

/**
 * Tema Compose único para móvil y tótem.
 * (Independiente del tema XML @style/Theme.GVAM del Manifest)
 */
@Composable
fun PortalTheme(content: @Composable () -> Unit) {
    val ff = FontFamily.SansSerif
    val typography = Typography(
        titleLarge = TextStyle(fontFamily = ff, fontSize = 22.sp, lineHeight = 26.sp),
        bodyMedium  = TextStyle(fontFamily = ff, fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge  = TextStyle(fontFamily = ff, fontSize = 14.sp)
    )
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF9CCC65) // verde base que ya usabas
        ),
        typography = typography,
        content = content
    )
}
