package io.github.asanre.verivent.sync

import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url

/**
 * A workspace's connection, as the web hands it out: `https://<api key>:<connection key>@<project>`.
 * [apiKey] is the project's publishable key and [key] the workspace's connection key.
 */
internal class Connection(private val project: String, val apiKey: String, val key: String) {
    fun rpc(function: String): String = "$project/rest/v1/rpc/$function"
}

internal fun parseConnection(text: String): Connection {
    val url = runCatching { Url(text.trim()) }.getOrNull()
    val apiKey = url?.user
    val key = url?.password
    require(
        url != null && url.protocol in setOf(URLProtocol.HTTPS, URLProtocol.HTTP) && url.host.isNotEmpty() &&
            !apiKey.isNullOrEmpty() && !key.isNullOrEmpty(),
    ) { "Not a Verivent connection: copy it from the workspace's Connect page, https://<api key>:<connection key>@<project>" }
    val project = URLBuilder(protocol = url.protocol, host = url.host, port = url.specifiedPort).buildString().trimEnd('/')
    return Connection(project, apiKey, key)
}
