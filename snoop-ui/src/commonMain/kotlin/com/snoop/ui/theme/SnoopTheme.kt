package com.snoop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.snoop.core.model.NetworkState
import com.snoop.core.model.NetworkTransaction

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

private val SnoopShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

@Composable
fun SnoopTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        shapes = SnoopShapes,
    ) {
        CompositionLocalProvider(
            LocalSnoopColors provides if (dark) SnoopDarkColors else SnoopLightColors,
            content = content,
        )
    }
}

private val channelPalette = listOf(
    Color(0xFF3B82F6), // blue
    Color(0xFF10B981), // green
    Color(0xFFF59E0B), // amber
    Color(0xFFEF4444), // red
    Color(0xFF8B5CF6), // violet
    Color(0xFFEC4899), // pink
    Color(0xFF14B8A6), // teal
)

/** Stable color per analytics channel so the eye can track a vendor down the timeline (Omnibug). */
internal fun channelColor(channel: String): Color =
    channelPalette[channel.hashCode().mod(channelPalette.size)]

private val Green = Color(0xFF16A34A)
private val Blue = Color(0xFF2563EB)
private val Amber = Color(0xFFD97706)
private val Red = Color(0xFFDC2626)
private val Gray = Color(0xFF6B7280)

internal fun statusColor(transaction: NetworkTransaction): Color = when (transaction.state) {
    NetworkState.Pending -> Gray
    NetworkState.Failed -> Red
    NetworkState.Complete -> when (transaction.status ?: 0) {
        in 200..299 -> Green
        in 300..399 -> Blue
        in 400..499 -> Amber
        else -> Red
    }
}
