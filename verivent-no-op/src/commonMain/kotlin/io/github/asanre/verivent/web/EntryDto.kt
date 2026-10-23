package io.github.asanre.verivent.web

import io.github.asanre.verivent.core.InternalVeriventApi
import io.github.asanre.verivent.core.model.AnalyticsEntry

/** No-op mirror of the exported entry: nothing is captured, so there is nothing to export. */
@InternalVeriventApi
fun encodeExportEntry(entry: AnalyticsEntry, pretty: Boolean = false): String = "{}"

/** No-op mirror of the session file: nothing is captured, so it is an empty array. */
@InternalVeriventApi
fun encodeExport(events: List<AnalyticsEntry>): String = "[]"
