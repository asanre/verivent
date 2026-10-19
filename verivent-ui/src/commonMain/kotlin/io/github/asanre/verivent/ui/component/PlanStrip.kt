package io.github.asanre.verivent.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.asanre.verivent.core.model.PlanSummary
import io.github.asanre.verivent.ui.theme.LocalInspectorColors
import io.github.asanre.verivent.ui.theme.TintPair

/** The timeline's way into the plan screen, with how this session's events did against the plan. */
@Composable
internal fun PlanStrip(
    plan: PlanSummary?,
    breaking: Int,
    matching: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalInspectorColors.current
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (plan != null) scheme.surface else scheme.background,
        border = if (plan != null) BorderStroke(1.dp, scheme.outlineVariant) else null,
        modifier = if (plan != null) modifier else modifier.dashedBorder(scheme.outline, 12.dp),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 44.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                InspectorIcons.Plan,
                contentDescription = null,
                tint = if (plan != null) scheme.onSurface else scheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            if (plan != null) {
                Text("Plan ${plan.version}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (breaking > 0) CountPill("$breaking break it", colors.planBreaks.pill)
                if (matching > 0) CountPill("$matching match", colors.planMatches.pill)
            } else {
                Text(
                    "No plan · events aren't checked",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text("Load", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), fontWeight = FontWeight.SemiBold)
            }
            Icon(
                InspectorIcons.ChevronRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun CountPill(text: String, tint: TintPair) {
    Surface(color = tint.container, contentColor = tint.content, shape = CircleShape) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
