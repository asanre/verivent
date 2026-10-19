package io.github.asanre.verivent.web

/**
 * Every bind is gated, loopback included: on Android the loopback interface is shared by every
 * installed app, so an open `127.0.0.1:9394` hands the whole capture buffer to any co-installed
 * app. A token is generated unless the host forces its own.
 */
internal fun resolveToken(token: String?): String = token ?: secureRandomToken()

/**
 * DNS-rebinding guard. A hostile page can point its own domain at this device and then talk to us
 * same-origin, which slips past both CORS and the `Origin` check. Reaching a debug server on a
 * device legitimately only ever happens through `localhost` or a literal address, so a request
 * carrying any real hostname is refused.
 */
internal fun hostHeaderAllowed(host: String?): Boolean {
    if (host.isNullOrEmpty()) return true
    val name = if (host.startsWith("[")) host.substringAfter('[').substringBefore(']') else host.substringBefore(':')
    return name == "localhost" || name.isAddressLiteral()
}

private fun String.isAddressLiteral(): Boolean = when {
    ':' in this -> all { it.isDigit() || it in "abcdefABCDEF:." }
    else -> '.' in this && all { it.isDigit() || it == '.' }
}

/** Constant-time compare, so a LAN attacker can't recover the token by timing prefixes. */
internal fun tokensEqual(expected: String, actual: String): Boolean {
    val a = expected.encodeToByteArray()
    val b = actual.encodeToByteArray()
    if (b.isEmpty()) return a.isEmpty()
    var diff = a.size xor b.size
    for (index in a.indices) diff = diff or (a[index].toInt() xor b[index % b.size].toInt())
    return diff == 0
}
