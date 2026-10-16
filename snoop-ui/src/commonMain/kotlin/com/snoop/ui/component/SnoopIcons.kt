package com.snoop.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Icon set of the Snoop design (feather-style outline, stroke 1.6-1.8, round caps/joins). Paths
 * are 1:1 with the design's SVG `d` data — the web viewer inlines the same paths — and each icon
 * is a single color so [androidx.compose.material3.Icon]'s `tint` recolors it at the call site.
 */
internal object SnoopIcons {

    val Search: ImageVector by lazy {
        outline(name = "Snoop.Search", d = circle(11f, 11f, 7f) + " M20 20l-3.2-3.2")
    }

    val Back: ImageVector by lazy {
        outline(name = "Snoop.Back", d = "M15 5l-7 7 7 7")
    }

    val ChevronRight: ImageVector by lazy {
        outline(name = "Snoop.ChevronRight", d = "M9 6l6 6-6 6")
    }

    val Pause: ImageVector by lazy {
        filled(
            name = "Snoop.Pause",
            d = roundedRect(6f, 5f, 4f, 14f, 1.3f) + " " + roundedRect(14f, 5f, 4f, 14f, 1.3f),
        )
    }

    val Play: ImageVector by lazy {
        filled(name = "Snoop.Play", d = "M7 5l12 7-12 7z")
    }

    val Clear: ImageVector by lazy {
        outline(
            name = "Snoop.Clear",
            d = "M4 7h16" +
                " M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" +
                " M6 7l1 12a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1l1-12",
        )
    }

    val Export: ImageVector by lazy {
        outline(
            name = "Snoop.Export",
            d = "M12 4v11" + " M8 11l4 4 4-4" + " M5 20h14",
        )
    }

    val Copy: ImageVector by lazy {
        outline(
            name = "Snoop.Copy",
            d = roundedRect(8f, 8f, 12f, 12f, 2f) +
                " M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2",
        )
    }

    val Share: ImageVector by lazy {
        outline(
            name = "Snoop.Share",
            d = circle(18f, 5f, 2.6f) + " " + circle(6f, 12f, 2.6f) + " " + circle(18f, 19f, 2.6f) +
                " M8.3 10.7l7.4-4.4" + " M8.3 13.3l7.4 4.4",
        )
    }

    val BarsAnalytics: ImageVector by lazy {
        outline(
            name = "Snoop.BarsAnalytics",
            d = "M5 20V11" + " M12 20V4" + " M19 20v-6",
        )
    }

    val Close: ImageVector by lazy {
        outline(name = "Snoop.Close", strokeWidth = 2.4f, d = "M6 6l12 12" + " M18 6L6 18")
    }

    val Check: ImageVector by lazy {
        outline(name = "Snoop.Check", strokeWidth = 2.2f, d = "M5 12.5l4.5 4.5L19 7.5")
    }

    val Plan: ImageVector by lazy {
        outline(name = "Snoop.Plan", strokeWidth = 1.8f, d = "M9 4h6l1 2h3v14H5V6h3z" + " M9 12l2 2 4-4")
    }

    val Alert: ImageVector by lazy {
        outline(name = "Snoop.Alert", strokeWidth = 2f, d = circle(12f, 12f, 9f) + " M12 8v5" + " M12 16.5v.5")
    }

    val Info: ImageVector by lazy {
        outline(name = "Snoop.Info", strokeWidth = 1.8f, d = circle(12f, 12f, 9f) + " M12 11v5" + " M12 8v.5")
    }

    val Clipboard: ImageVector by lazy {
        outline(name = "Snoop.Clipboard", strokeWidth = 1.8f, d = roundedRect(7f, 4f, 10f, 4f, 1f) + " M7 6H5v14h14V6h-2")
    }

    /** The plan screen's larger mark: [Plan] with the clipboard's clip drawn in. */
    val PlanBoard: ImageVector by lazy {
        outline(name = "Snoop.PlanBoard", d = "M9 4h6l1 2h3v14H5V6h3z" + " M9 4v3h6V4" + " M9 12l2 2 4-4")
    }

    val BreaksCircle: ImageVector by lazy {
        outline(name = "Snoop.BreaksCircle", strokeWidth = 2f, d = circle(12f, 12f, 9f) + " M9 9l6 6" + " M15 9l-6 6")
    }

    val MatchesCircle: ImageVector by lazy {
        outline(name = "Snoop.MatchesCircle", strokeWidth = 2f, d = circle(12f, 12f, 9f) + " M8 12.5l2.8 2.8L16.5 9.5")
    }
}

private fun circle(cx: Float, cy: Float, r: Float): String =
    "M${cx - r},$cy A$r,$r 0 1,0 ${cx + r},$cy A$r,$r 0 1,0 ${cx - r},$cy"

private fun roundedRect(x: Float, y: Float, w: Float, h: Float, r: Float): String {
    val right = x + w
    val bottom = y + h
    val left1 = x + r
    val right1 = right - r
    val top1 = y + r
    val bottom1 = bottom - r
    return "M$left1,$y L$right1,$y Q$right,$y $right,$top1 L$right,$bottom1 Q$right,$bottom $right1,$bottom" +
        " L$left1,$bottom Q$x,$bottom $x,$bottom1 L$x,$top1 Q$x,$y $left1,$y Z"
}

private fun outline(name: String, d: String, strokeWidth: Float = 1.6f): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        .build()

private fun filled(name: String, d: String): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = SolidColor(Color.Black),
            )
        }
        .build()
