package io.github.asanre.verivent.web

import io.github.asanre.verivent.core.InternalVeriventApi
import io.github.asanre.verivent.core.model.TimelineEntry

/** No-op mirror of the exported entry: nothing is captured, so there is nothing to export. */
@InternalVeriventApi
fun encodeExportEntry(entry: TimelineEntry, pretty: Boolean = false): String = "{}"
