package com.snoop.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.snoop.core.model.NetworkState
import com.snoop.core.model.NetworkTransaction

/** A tinted background plus the foreground that stays legible on it, per color mode. */
@Immutable
internal data class TintPair(val container: Color, val content: Color)

/**
 * Semantic colors the Material scheme has no role for: HTTP status, request methods, analytics
 * channels and JSON syntax. Explicit light/dark instances — alpha tints over pure hues wash out
 * in dark mode.
 */
@Immutable
internal data class SnoopColors(
    val success: TintPair,
    val info: TintPair,
    val warn: TintPair,
    val error: TintPair,
    val neutral: TintPair,
    val accent: TintPair,
    val ecom: TintPair,
    val channelPalette: List<TintPair>,
    val jsonKey: Color,
    val jsonString: Color,
    val jsonNumber: Color,
    val jsonKeyword: Color,
    val jsonPunctuation: Color,
    val liveDot: Color,
) {
    fun methodTint(method: String): TintPair = when (method.uppercase()) {
        "GET" -> info
        "POST" -> success
        "PUT", "PATCH" -> warn
        "DELETE" -> error
        else -> neutral
    }

    fun statusTint(transaction: NetworkTransaction): TintPair = when (transaction.state) {
        NetworkState.Pending -> neutral
        NetworkState.Failed -> error
        NetworkState.Complete -> when (transaction.status ?: 0) {
            in 200..299 -> success
            in 300..399 -> info
            in 400..499 -> warn
            else -> error
        }
    }

    /** Stable tint per analytics channel so the eye can track a vendor down the timeline. */
    fun channelTint(channel: String): TintPair =
        channelPalette[channel.hashCode().mod(channelPalette.size)]
}

internal val SnoopLightColors = SnoopColors(
    success = TintPair(Color(0xFFDCFCE7), Color(0xFF16A34A)),
    info = TintPair(Color(0xFFDBEAFE), Color(0xFF2563EB)),
    warn = TintPair(Color(0xFFFEF3C7), Color(0xFFB45309)),
    error = TintPair(Color(0xFFFEE2E2), Color(0xFFDC2626)),
    neutral = TintPair(Color(0xFFF3F4F6), Color(0xFF6B7280)),
    accent = TintPair(Color(0xFFEDEBFE), Color(0xFF6D5EFC)),
    ecom = TintPair(Color(0xFFD1FAE5), Color(0xFF047857)),
    channelPalette = listOf(
        TintPair(Color(0xFFDBEAFE), Color(0xFF1D4ED8)),
        TintPair(Color(0xFFD1FAE5), Color(0xFF047857)),
        TintPair(Color(0xFFFEF3C7), Color(0xFFB45309)),
        TintPair(Color(0xFFFEE2E2), Color(0xFFB91C1C)),
        TintPair(Color(0xFFEDE9FE), Color(0xFF6D28D9)),
        TintPair(Color(0xFFFCE7F3), Color(0xFFBE185D)),
        TintPair(Color(0xFFCCFBF1), Color(0xFF0F766E)),
    ),
    jsonKey = Color(0xFF1D4ED8),
    jsonString = Color(0xFF9A3412),
    jsonNumber = Color(0xFF6D28D9),
    jsonKeyword = Color(0xFF047857),
    jsonPunctuation = Color(0xFF6B7280),
    liveDot = Color(0xFF16A34A),
)

internal val SnoopDarkColors = SnoopColors(
    success = TintPair(Color(0xFF16321F), Color(0xFF4ADE80)),
    info = TintPair(Color(0xFF16243D), Color(0xFF60A5FA)),
    warn = TintPair(Color(0xFF33270F), Color(0xFFFBBF24)),
    error = TintPair(Color(0xFF3A1A1A), Color(0xFFF87171)),
    neutral = TintPair(Color(0xFF23232B), Color(0xFF9AA0AA)),
    accent = TintPair(Color(0xFF241F44), Color(0xFF8B7DFF)),
    ecom = TintPair(Color(0xFF123229), Color(0xFF34D399)),
    channelPalette = listOf(
        TintPair(Color(0xFF16243D), Color(0xFF60A5FA)),
        TintPair(Color(0xFF123229), Color(0xFF34D399)),
        TintPair(Color(0xFF33270F), Color(0xFFFBBF24)),
        TintPair(Color(0xFF3A1A1A), Color(0xFFF87171)),
        TintPair(Color(0xFF241F44), Color(0xFFA78BFA)),
        TintPair(Color(0xFF381626), Color(0xFFF472B6)),
        TintPair(Color(0xFF0F2E2A), Color(0xFF2DD4BF)),
    ),
    jsonKey = Color(0xFF60A5FA),
    jsonString = Color(0xFFFDBA74),
    jsonNumber = Color(0xFFA78BFA),
    jsonKeyword = Color(0xFF34D399),
    jsonPunctuation = Color(0xFF9AA0AA),
    liveDot = Color(0xFF22C55E),
)

internal val LocalSnoopColors = staticCompositionLocalOf { SnoopLightColors }
