package com.snoop.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snoop.ui.internal.TypeFilter
import com.snoop.ui.theme.LocalSnoopColors
import com.snoop.ui.theme.TintPair

/**
 * Pill chip in the approved mockup style: unselected = hairline border on surface, selected =
 * inverted fill (or the [tint] pair when the chip represents a colored channel).
 */
@Composable
internal fun SnoopChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: TintPair? = null,
    leadingDot: Color? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val container = when {
        selected && tint != null -> tint.container
        selected -> scheme.onSurface
        else -> scheme.surface
    }
    val content = when {
        selected && tint != null -> tint.content
        selected -> scheme.surface
        else -> scheme.onSurface
    }
    val border = when {
        selected && tint != null -> BorderStroke(2.dp, tint.content)
        selected -> null
        else -> BorderStroke(1.dp, scheme.outlineVariant)
    }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
        border = border,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            leadingDot?.let { Box(Modifier.size(8.dp).clip(CircleShape).background(it)) }
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
internal fun FilterChipBar(
    type: TypeFilter,
    channel: String?,
    channels: List<String>,
    onType: (TypeFilter) -> Unit,
    onChannel: (String) -> Unit,
) {
    val colors = LocalSnoopColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TypeFilter.entries.forEach { option ->
            SnoopChip(
                label = option.name,
                selected = type == option,
                onClick = { onType(option) },
            )
        }
        channels.forEach { name ->
            val tint = colors.channelTint(name)
            SnoopChip(
                label = name,
                selected = channel == name,
                onClick = { onChannel(name) },
                tint = tint,
                leadingDot = tint.content,
            )
        }
    }
}
