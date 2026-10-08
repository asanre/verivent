package com.snoop.ios

import com.snoop.web.SnoopBind

/**
 * Where the viewer listens. Mirrors `SnoopBind` so the bridge keeps snoop out of its exported
 * surface; [Lan] and [Host] are reachable off-device and are always token-gated.
 */
sealed interface SnoopViewerBind {
    /** Loopback only (`127.0.0.1`); open. Reachable from the iOS simulator as `localhost`. */
    data object Local : SnoopViewerBind

    /** Every interface (`0.0.0.0`), reachable across the LAN. */
    data object Lan : SnoopViewerBind

    /** A specific bind address you choose. Gated like [Lan]. */
    data class Host(val address: String) : SnoopViewerBind
}

internal val SnoopViewerBind.core: SnoopBind
    get() = when (this) {
        SnoopViewerBind.Local -> SnoopBind.Local
        SnoopViewerBind.Lan -> SnoopBind.Lan
        is SnoopViewerBind.Host -> SnoopBind.Host(address)
    }
