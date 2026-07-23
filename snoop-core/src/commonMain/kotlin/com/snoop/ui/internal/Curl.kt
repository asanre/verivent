package com.snoop.ui.internal

import com.snoop.core.model.NetworkTransaction

/**
 * Render a request as a copy-pasteable `curl` command. Redacted headers keep their `██` placeholder
 * so a shared command never leaks a secret.
 */
internal fun buildCurl(transaction: NetworkTransaction): String {
    val parts = mutableListOf("curl")
    if (transaction.method.uppercase() != "GET") {
        parts += "-X '${transaction.method.uppercase().escapeSingleQuotes()}'"
    }
    parts += "'${transaction.url.escapeSingleQuotes()}'"
    transaction.requestHeaders.forEach { header ->
        parts += "-H '${header.name.escapeSingleQuotes()}: ${header.value.escapeSingleQuotes()}'"
    }
    transaction.requestBody?.text?.takeIf { it.isNotEmpty() }?.let { body ->
        parts += "--data '${body.escapeSingleQuotes()}'"
    }
    return parts.joinToString(" \\\n  ")
}

private fun String.escapeSingleQuotes(): String = replace("'", "'\\''")
