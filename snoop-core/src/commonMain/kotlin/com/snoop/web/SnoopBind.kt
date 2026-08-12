package com.snoop.web

/**
 * Where [SnoopWebServer] listens.
 *
 * [Local] is loopback only — reach it with `adb forward` or the iOS simulator. [Lan] and [Host] are
 * reachable from other machines. Every mode is token-gated (loopback is shared with every other app
 * on the device): a generated token unless you pass your own to [SnoopWebServer.start].
 */
sealed interface SnoopBind {
    /** Loopback only (`127.0.0.1`). */
    data object Local : SnoopBind

    /** Every interface (`0.0.0.0`), reachable across the LAN. */
    data object Lan : SnoopBind

    /** A specific bind address you choose (e.g. one interface's IP). */
    data class Host(val address: String) : SnoopBind
}

internal val SnoopBind.bindAddress: String
    get() = when (this) {
        SnoopBind.Local -> SnoopWebServer.LOOPBACK
        SnoopBind.Lan -> "0.0.0.0"
        is SnoopBind.Host -> address
    }

/** Loopback is unreachable from another machine, so it has no LAN URL to offer. */
internal val SnoopBind.isLoopbackOnly: Boolean get() = this == SnoopBind.Local
