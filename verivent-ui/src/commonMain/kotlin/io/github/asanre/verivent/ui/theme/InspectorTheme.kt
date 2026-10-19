package io.github.asanre.verivent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightScheme = lightColorScheme(
    primary = Color(0xFF6D5EFC),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDEBFE),
    onPrimaryContainer = Color(0xFF453AA8),
    background = Color(0xFFF7F7FB),
    onBackground = Color(0xFF1B1B20),
    surface = Color.White,
    onSurface = Color(0xFF1B1B20),
    surfaceVariant = Color(0xFFF1F1F6),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFD1D1DB),
    outlineVariant = Color(0xFFE6E6EE),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8B7DFF),
    onPrimary = Color(0xFF17171F),
    primaryContainer = Color(0xFF241F44),
    onPrimaryContainer = Color(0xFFC7BEFF),
    background = Color(0xFF0F0F14),
    onBackground = Color(0xFFE9E9EF),
    surface = Color(0xFF17171F),
    onSurface = Color(0xFFE9E9EF),
    surfaceVariant = Color(0xFF20202A),
    onSurfaceVariant = Color(0xFF9AA0AA),
    outline = Color(0xFF3A3A46),
    outlineVariant = Color(0xFF26262F),
)

private val InspectorShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

/** Parameter names and values, event names and plan JSON: what the host's code sent, verbatim. */
internal val codeTextStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp)

@Composable
fun InspectorTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        shapes = InspectorShapes,
    ) {
        CompositionLocalProvider(
            LocalInspectorColors provides if (dark) InspectorDarkColors else InspectorLightColors,
            content = content,
        )
    }
}
