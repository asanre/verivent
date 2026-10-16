package com.snoop.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snoop.ui.theme.TintPair

/** Small rounded label used for status codes, methods, channels and badges. */
@Composable
internal fun Pill(text: String, tint: TintPair, modifier: Modifier = Modifier, mono: Boolean = false) {
    Surface(
        modifier = modifier,
        color = tint.container,
        contentColor = tint.content,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            ),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
