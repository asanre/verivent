package com.snoop.web

/** This device's LAN IPv4 address (Wi-Fi), or null if none is available. */
internal expect fun localHostAddress(): String?

/** A human-readable name for this device, to tell devices apart on a shared network. */
internal expect fun localDeviceName(): String
