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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snoop.ui.internal.TypeFilter
import com.snoop.ui.theme.LocalSnoopColors
import com.snoop.ui.theme.TintPair

/**
 * Pill chip in the approved mockup style: unselected = hairline border on surface, selected =
 * inverted fill (or the [tint] pair when the chip represents a colored channel). An [alert] chip
 * keeps its [tint] while unselected, so it stands out before anyone picks it.
 */
@Composable
internal fun SnoopChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: TintPair? = null,
    leadingDot: Color? = null,
    leadingIcon: ImageVector? = null,
    alert: Boolean = false,
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
        alert && tint != null -> tint.content
        else -> scheme.onSurface
    }
    val border = when {
        selected && tint != null -> BorderStroke(2.dp, tint.content)
        selected -> null
        alert && tint != null -> BorderStroke(1.dp, tint.content.copy(alpha = 0.45f))
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
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            leadingDot?.let { Box(Modifier.size(8.dp).clip(CircleShape).background(it)) }
            leadingIcon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(14.dp)) }
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                fontWeight = if (selected || alert) FontWeight.SemiBold else FontWeight.Normal,
            )
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
    breaking: Int?,
    breaksPlan: Boolean,
    onBreaksPlan: () -> Unit,
    grouped: Boolean?,
    onGrouped: () -> Unit,
) {
    val colors = LocalSnoopColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        @Composable
        fun typeChip(option: TypeFilter) = SnoopChip(label = option.name, selected = type == option, onClick = { onType(option) })

        typeChip(TypeFilter.All)
        typeChip(TypeFilter.Analytics)
        breaking?.let { count ->
            SnoopChip(
                label = "Breaks plan · $count",
                selected = breaksPlan,
                onClick = onBreaksPlan,
                tint = colors.planBreaks.pill,
                leadingIcon = SnoopIcons.Alert,
                alert = true,
            )
        }
        typeChip(TypeFilter.Network)
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
        grouped?.let { SnoopChip(label = "Group", selected = it, onClick = onGrouped) }
    }
}
