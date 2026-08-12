@file:OptIn(ExperimentalForeignApi::class)

package com.snoop.web

import com.snoop.core.internal.SnoopLog
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.darwin.freeifaddrs
import platform.darwin.getifaddrs
import platform.darwin.ifaddrs
import platform.posix.AF_INET
import platform.posix.arc4random_buf
import platform.posix.sockaddr_in
import platform.posix.uint8_tVar

// en0 is the Wi-Fi interface on iOS devices; on the simulator it maps to the Mac's LAN interface.
private const val WIFI_INTERFACE = "en0"

internal actual fun localHostAddress(): String? = memScoped {
    val list = alloc<CPointerVar<ifaddrs>>()
    if (getifaddrs(list.ptr) != 0) {
        SnoopLog.warn("web", "getifaddrs failed; no LAN address")
        return@memScoped null
    }
    try {
        var cur = list.value
        while (cur != null) {
            val ifa = cur.pointed
            val addr = ifa.ifa_addr
            if (addr != null &&
                addr.pointed.sa_family.toInt() == AF_INET &&
                ifa.ifa_name?.toKString() == WIFI_INTERFACE
            ) {
                // s_addr is in network byte order, so its four bytes sit in memory already ordered
                // left-to-right — reading them directly holds on any host endianness.
                val octets = addr.reinterpret<sockaddr_in>().pointed.sin_addr.ptr.reinterpret<uint8_tVar>()
                return@memScoped "${octets[0]}.${octets[1]}.${octets[2]}.${octets[3]}"
            }
            cur = ifa.ifa_next
        }
        null
    } finally {
        freeifaddrs(list.value)
    }
}

internal actual fun secureRandomBytes(size: Int): ByteArray =
    ByteArray(size).also { bytes -> bytes.usePinned { arc4random_buf(it.addressOf(0), size.convert()) } }
