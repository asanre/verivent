package com.snoop.web

import com.snoop.core.internal.SnoopLog
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom

internal actual fun localHostAddress(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces().asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.asSequence() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { it.isSiteLocalAddress }
        ?.hostAddress
}.onFailure { SnoopLog.warn("web", "could not read the LAN address", it) }.getOrNull()

internal actual fun secureRandomBytes(size: Int): ByteArray =
    ByteArray(size).also { SecureRandom().nextBytes(it) }
