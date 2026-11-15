package com.apkrepo.web.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Indigo = Color(0xFF3F51B5)
private val IndigoDark = Color(0xFF9FA8DA)

private val LightColors = lightColorScheme(
    primary = Indigo,
    secondary = Color(0xFF5C6BC0),
)

private val DarkColors = darkColorScheme(
    primary = IndigoDark,
    secondary = Color(0xFF7986CB),
)

/** App theme; follows the browser's color scheme preference. */
@Composable
fun RepoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
