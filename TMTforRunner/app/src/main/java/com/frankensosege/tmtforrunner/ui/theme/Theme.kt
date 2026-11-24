package com.frankensosege.tmtforrunner.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Purple80  = Color(0xFFD0BCFF)
val Purple40  = Color(0xFF6200EE)
val Coral     = Color(0xFFE76F51)
val Teal      = Color(0xFF009688)
val BgLight   = Color(0xFFF8F8F8)
val TextPrimary = Color(0xFF1A1A2E)

private val LightColors = lightColorScheme(
    primary        = Purple40,
    secondary      = Teal,
    tertiary       = Coral,
    background     = BgLight,
    surface        = Color.White,
    onPrimary      = Color.White,
    onSecondary    = Color.White,
    onBackground   = TextPrimary,
    onSurface      = TextPrimary,
)

@Composable
fun TMTforRunnerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography  = Typography(),
        content     = content
    )
}
