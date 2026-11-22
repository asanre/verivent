package io.github.asanre.verivent.ios

import io.github.asanre.verivent.sync.VeriventSync

/** The workspace connection for native iOS hosts; Swift reaches it as `Verivent.connect`. */
object NativeSync {

    /** See `VeriventSync.connect`. */
    @Throws(IllegalArgumentException::class)
    fun connect(connection: String) = VeriventSync.connect(connection)

    fun disconnect() = VeriventSync.disconnect()
}
