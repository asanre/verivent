package com.snoop.web

import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

internal actual fun localHostAddress(): String? = runCatching {
    Collections.list(NetworkInterface.getNetworkInterfaces())
        .filter { it.isUp && !it.isLoopback }
        .flatMap { Collections.list(it.inetAddresses) }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { it.isSiteLocalAddress }
        ?.hostAddress
}.getOrNull()

internal actual fun localDeviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}"
