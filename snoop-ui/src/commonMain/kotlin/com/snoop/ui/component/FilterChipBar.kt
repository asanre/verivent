package com.snoop.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.snoop.ui.TypeFilter
import com.snoop.ui.theme.channelColor

@Composable
internal fun FilterChipBar(
    type: TypeFilter,
    channel: String?,
    channels: List<String>,
    onType: (TypeFilter) -> Unit,
    onChannel: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TypeFilter.entries.forEach { option ->
            FilterChip(
                selected = type == option,
                onClick = { onType(option) },
                label = { Text(option.name) },
            )
        }
        channels.forEach { name ->
            val color = channelColor(name)
            FilterChip(
                selected = channel == name,
                onClick = { onChannel(name) },
                label = { Text(name) },
                leadingIcon = {
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(10.dp).clip(CircleShape).background(color),
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = color.copy(alpha = 0.22f),
                ),
            )
        }
    }
}
