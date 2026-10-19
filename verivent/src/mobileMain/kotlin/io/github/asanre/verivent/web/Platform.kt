package io.github.asanre.verivent.web

/** This device's LAN IPv4 address (Wi-Fi), or null if none is available. */
internal expect fun localHostAddress(): String?

/** Cryptographically secure random bytes, backing the generated LAN access token. */
internal expect fun secureRandomBytes(size: Int): ByteArray

internal fun secureRandomToken(): String =
    secureRandomBytes(TOKEN_BYTES).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

private const val TOKEN_BYTES = 16
