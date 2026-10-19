package io.github.asanre.verivent.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** No-op mirror of `verivent-ui` for release builds: the inspector renders nothing. */
@Composable
fun InspectorScreen(modifier: Modifier = Modifier, onClose: (() -> Unit)? = null) = Unit
