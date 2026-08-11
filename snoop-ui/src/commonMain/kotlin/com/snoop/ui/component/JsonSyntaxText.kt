package com.snoop.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import com.snoop.ui.internal.JsonTokenType
import com.snoop.ui.internal.jsonSpans
import com.snoop.ui.theme.LocalSnoopColors

/** Monospace JSON with syntax colors. Expects already-formatted text (see [prettyPrintJson]). */
@Composable
internal fun JsonSyntaxText(pretty: String, modifier: Modifier = Modifier) {
    val colors = LocalSnoopColors.current
    val annotated = remember(pretty, colors) {
        buildAnnotatedString {
            append(pretty)
            jsonSpans(pretty).forEach { span ->
                val color = when (span.type) {
                    JsonTokenType.Key -> colors.jsonKey
                    JsonTokenType.StringValue -> colors.jsonString
                    JsonTokenType.Number -> colors.jsonNumber
                    JsonTokenType.Keyword -> colors.jsonKeyword
                    JsonTokenType.Punctuation -> colors.jsonPunctuation
                }
                addStyle(SpanStyle(color = color), span.start, span.end)
            }
        }
    }
    val baseStyle = MaterialTheme.typography.bodySmall
    Text(
        text = annotated,
        style = baseStyle.copy(fontFamily = FontFamily.Monospace, lineHeight = baseStyle.fontSize * 1.75f),
        modifier = modifier,
    )
}
