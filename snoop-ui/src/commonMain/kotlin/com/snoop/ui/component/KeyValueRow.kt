package com.snoop.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snoop.core.model.KeyValue
import com.snoop.ui.theme.LocalSnoopColors

/** One property; [issue] marks it as breaking the tracking plan and says how. */
@Composable
internal fun KeyValueRow(item: KeyValue, valueAlignEnd: Boolean = false, issue: String? = null) {
    val error = LocalSnoopColors.current.error
    val marked = if (issue != null) Modifier.background(error.container) else Modifier
    Column(Modifier.fillMaxWidth().then(marked)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.weight(0.42f), verticalAlignment = Alignment.CenterVertically) {
                if (issue != null) IssueDot()
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Medium,
                    color = if (issue != null) error.content else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(Modifier.weight(0.58f), contentAlignment = if (valueAlignEnd) Alignment.CenterEnd else Alignment.CenterStart) {
                if (item.redacted) {
                    Box(
                        Modifier
                            .padding(top = 2.dp)
                            .width(64.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.onSurface),
                    )
                } else {
                    Text(
                        text = item.value,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = if (valueAlignEnd) TextAlign.End else TextAlign.Start,
                    )
                }
            }
        }
        issue?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = error.content,
                modifier = Modifier.padding(start = 20.dp, end = 8.dp, bottom = 8.dp),
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }
}

/** A property the tracking plan requires but the event didn't send. */
@Composable
internal fun MissingPropertyRow(name: String) {
    val error = LocalSnoopColors.current.error
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .dashedBorder(error.content, 8.dp)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IssueDot()
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Medium,
            color = error.content,
            modifier = Modifier.weight(1f),
        )
        Text("missing · required", style = MaterialTheme.typography.labelSmall, color = error.content)
    }
}

@Composable
private fun IssueDot() {
    Box(
        Modifier.padding(end = 6.dp).size(6.dp).clip(CircleShape)
            .background(LocalSnoopColors.current.error.content),
    )
}
