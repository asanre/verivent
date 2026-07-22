package com.snoop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.snoop.core.model.NetworkState
import com.snoop.core.model.NetworkTransaction

@Composable
fun SnoopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
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
