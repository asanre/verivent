package io.github.asanre.verivent.ui.internal

import androidx.compose.runtime.mutableStateOf

/**
 * Timeline selections that must outlive the composition. Closing the inspector finishes
 * `InspectorActivity` and discards its saved state, and an embedded `InspectorScreen` leaves composition
 * with no Activity involved at all, so `rememberSaveable` isn't enough — this lives for the process.
 *
 * The free-text query is deliberately not here: reopening behind a stale search that hides
 * everything disorients more than it helps. Neither is the collapsed set, keyed by entry id, whose
 * keys go stale as entries are evicted.
 */
internal object TimelineUiState {
    val filters = mutableStateOf(TimelineFilters())
}
