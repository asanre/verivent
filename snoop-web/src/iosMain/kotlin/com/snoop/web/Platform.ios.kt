@file:OptIn(ExperimentalForeignApi::class)

package com.snoop.web

import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.UIKit.UIDevice
import platform.darwin.freeifaddrs
import platform.darwin.getifaddrs
import platform.darwin.ifaddrs
import platform.posix.AF_INET
import platform.posix.sockaddr_in

// en0 is the Wi-Fi interface on iOS devices; on the simulator it maps to the Mac's LAN interface.
private const val WIFI_INTERFACE = "en0"

internal actual fun localHostAddress(): String? = memScoped {
    val list = alloc<CPointerVar<ifaddrs>>()
    if (getifaddrs(list.ptr) != 0) return@memScoped null
    try {
        var cur = list.value
        while (cur != null) {
            val ifa = cur.pointed
            val addr = ifa.ifa_addr
            if (addr != null &&
                addr.pointed.sa_family.toInt() == AF_INET &&
                ifa.ifa_name?.toKString() == WIFI_INTERFACE
            ) {
                // s_addr is a uint32 in network byte order; on little-endian ARM the first octet is
                // the least significant byte, so this reads left-to-right without inet_ntop.
                val s = addr.reinterpret<sockaddr_in>().pointed.sin_addr.s_addr
                return@memScoped "${s and 0xFFu}.${(s shr 8) and 0xFFu}.${(s shr 16) and 0xFFu}.${(s shr 24) and 0xFFu}"
            }
            cur = ifa.ifa_next
        }
        null
    } finally {
        freeifaddrs(list.value)
    }
}

internal actual fun localDeviceName(): String = UIDevice.currentDevice.name
