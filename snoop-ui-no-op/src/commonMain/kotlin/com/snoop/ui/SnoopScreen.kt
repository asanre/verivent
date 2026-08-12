package com.snoop.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.snoop.core.SnoopCore

/** No-op mirror of `snoop-ui` for release builds: the inspector renders nothing. */
@Composable
fun SnoopScreen(
    modifier: Modifier = Modifier,
    groupByEvent: String? = SnoopCore.groupByEvent,
    groupLabelProperty: String? = SnoopCore.groupLabelProperty,
) = Unit
