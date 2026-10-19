package io.github.asanre.verivent.analytics

/**
 * Splits one captured entry's JSON into console lines that fit the platform's per-message limit.
 *
 * A line that fits is the bare JSON, so a reader can parse it as is. Longer ones become pieces
 * prefixed with `[<id> <i>/<n>] `, to be concatenated in order. The limit is in UTF-8 bytes, which
 * is what the console truncates on, and a piece never splits a surrogate pair.
 */
internal fun consoleLines(id: String, json: String, maxBytes: Int): List<String> {
    if (json.utf8Size() <= maxBytes) return listOf(json)
    // Room for the widest prefix this id can get; piece counts past 9999 aren't realistic.
    val budget = maxBytes - "[$id 9999/9999] ".length
    val pieces = mutableListOf<String>()
    var start = 0
    var bytes = 0
    var index = 0
    while (index < json.length) {
        val char = json[index]
        val width = if (char.isHighSurrogate() && index + 1 < json.length) 2 else 1
        val size = when {
            width == 2 -> 4
            char.code < 0x80 -> 1
            char.code < 0x800 -> 2
            else -> 3
        }
        if (bytes + size > budget) {
            pieces += json.substring(start, index)
            start = index
            bytes = 0
        }
        bytes += size
        index += width
    }
    pieces += json.substring(start)
    return pieces.mapIndexed { i, piece -> "[$id ${i + 1}/${pieces.size}] $piece" }
}

private fun String.utf8Size(): Int = encodeToByteArray().size

/** Writes one line to the platform console under the `VeriventEvent` tag. */
internal expect fun platformConsole(line: String)

/** Largest console message, in UTF-8 bytes, that the platform shows without truncating. */
internal expect val consoleMaxBytes: Int
