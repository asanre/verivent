package com.snoop.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Icon set of the Snoop design, translated from the SVGs in `assets-nocommit/icons` (feather-style
 * outline, stroke 1.6-1.8, round caps/joins). Paths are 1:1 with the source SVG `d` data; each
 * icon is a single color so [androidx.compose.material3.Icon]'s `tint` recolors it at the call site.
 */
internal object SnoopIcons {

    val LogoMark: ImageVector by lazy {
        outline(
            name = "Snoop.LogoMark",
            strokeWidth = 1.8f,
            d = circle(10.5f, 10.5f, 6.6f) +
                " M20.2 20.2l-4.3-4.3" +
                " M6.9 11.1l1.9-2.4 1.9 3.2 1.9-4.1 1.6 3.3",
        )
    }

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
            d = roundedRect(9f, 9f, 11f, 11f, 2f) +
                " M6 15H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v1",
        )
    }

    val Share: ImageVector by lazy {
        outline(
            name = "Snoop.Share",
            d = circle(18f, 5f, 2.6f) + " " + circle(6f, 12f, 2.6f) + " " + circle(18f, 19f, 2.6f) +
                " M8.3 10.7l7.4-4.4" + " M8.3 13.3l7.4 4.4",
        )
    }

    val Link: ImageVector by lazy {
        outline(
            name = "Snoop.Link",
            d = "M9 17H7a4 4 0 0 1 0-8h1" + " M15 7h2a4 4 0 0 1 0 8h-1" + " M8 12h8",
        )
    }

    val BarsAnalytics: ImageVector by lazy {
        outline(
            name = "Snoop.BarsAnalytics",
            d = "M5 20V11" + " M12 20V4" + " M19 20v-6",
        )
    }

    val Phone: ImageVector by lazy {
        outline(
            name = "Snoop.Phone",
            d = roundedRect(7f, 3f, 10f, 18f, 2f) + " M11 18h2",
        )
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
