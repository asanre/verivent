package io.github.asanre.verivent.ui.internal

import io.github.asanre.verivent.core.model.PlanIssue

/**
 * The sent property an issue belongs to: its own, or the one a nested path (`tracks[*].id`,
 * `item.price`) starts from. Null for a property that wasn't sent at all. A name holding a dot
 * could prefix another, so the longest match wins.
 */
internal fun issueRoot(path: String, sent: Set<String>): String? =
    if (path in sent) path else sent.filter { path.startsWith("$it.") || path.startsWith("$it[") }.maxByOrNull { it.length }

/** How an issue reads under its root property's row: nested ones say where. */
internal fun PlanIssue.lineUnder(root: String): String = if (property == root) message else "$property · $message"
