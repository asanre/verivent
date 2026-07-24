package com.snoop.web

/**
 * Where [SnoopWebServer] listens, which decides whether it needs a token.
 *
 * [Local] is the only open mode (loopback only — reach it with `adb forward` or the iOS simulator).
 * [Lan] and [Host] are reachable from other machines, so they are always token-gated: a generated
 * token when you don't pass your own to [SnoopWebServer.start].
 */
sealed interface SnoopBind {
    /** Loopback only (`127.0.0.1`); open. */
    data object Local : SnoopBind

    /** Every interface (`0.0.0.0`), reachable across the LAN. */
    data object Lan : SnoopBind

    /** A specific bind address you choose (e.g. one interface's IP). Gated like [Lan]. */
    data class Host(val address: String) : SnoopBind
}

internal val SnoopBind.bindAddress: String
    get() = when (this) {
        SnoopBind.Local -> SnoopWebServer.LOOPBACK
        SnoopBind.Lan -> "0.0.0.0"
        is SnoopBind.Host -> address
    }

/** Only loopback is served without a token; everything reachable off-device is gated. */
internal val SnoopBind.isOpen: Boolean get() = this == SnoopBind.Local
