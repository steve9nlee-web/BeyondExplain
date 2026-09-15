package com.beyondexplain.penangstalls.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Warm chilli-and-turmeric palette, a nod to a hawker stall at night.
private val Chilli = Color(0xFFC0392B)
private val Turmeric = Color(0xFFE08A1E)
private val Pandan = Color(0xFF2E7D57)

private val LightColors = lightColorScheme(
    primary = Chilli,
    secondary = Turmeric,
    tertiary = Pandan,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8A75),
    secondary = Color(0xFFFFC46B),
    tertiary = Color(0xFF7FD1A8),
)

@Composable
fun PenangStallsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
