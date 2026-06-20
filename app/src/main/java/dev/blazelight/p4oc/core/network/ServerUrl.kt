package dev.blazelight.p4oc.core.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object ServerUrl {
    const val DEFAULT_PORT = 4096
    const val DEFAULT_USERNAME = "opencode"

    fun normalizeConnectUrl(input: String): String? {
        val components = parse(input) ?: return null
        return buildUrl(
            scheme = components.scheme,
            host = components.formattedHost,
            port = components.port,
            path = components.path,
        )
    }

    fun endpointKey(input: String): String? {
        val components = parse(input) ?: return null
        return buildUrl(
            scheme = components.scheme,
            host = components.formattedHost,
            port = components.port,
            path = components.path,
        )
    }

    private data class ParsedServerUrl(
        val scheme: String,
        val formattedHost: String,
        val port: Int,
        val path: String,
    )

    private fun parse(input: String): ParsedServerUrl? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        val candidate = if (trimmed.contains("://")) trimmed else "http://$trimmed"
        val sanitizedCandidate = stripIpv6ZoneId(candidate)
        val parsed = sanitizedCandidate.toHttpUrlOrNull() ?: return null
        val scheme = parsed.scheme.lowercase()
        if (scheme != "http" && scheme != "https") return null

        val host = parsed.host.substringBefore('%').lowercase()
        val formattedHost = if (':' in host) "[$host]" else host
        val port = when {
            hasExplicitPort(sanitizedCandidate) -> parsed.port
            // An explicit https:// without a port means a reverse-proxied server on the standard TLS
            // port, not raw opencode on 4096. Bare host / http:// still default to opencode's port.
            scheme == "https" -> 443
            else -> DEFAULT_PORT
        }
        val path = parsed.encodedPath
            .takeUnless { it == "/" }
            ?.trimEnd('/')
            .orEmpty()

        return ParsedServerUrl(
            scheme = scheme,
            formattedHost = formattedHost,
            port = port,
            path = path,
        )
    }

    private fun buildUrl(
        scheme: String,
        host: String,
        port: Int,
        path: String,
    ): String {
        val base = "$scheme://$host:$port"
        return if (path.isEmpty()) base else "$base$path"
    }

    private fun stripIpv6ZoneId(candidate: String): String {
        val schemePrefix = candidate.substringBefore("://", missingDelimiterValue = "")
        if (schemePrefix.isBlank()) return candidate

        val prefix = "$schemePrefix://"
        val rest = candidate.removePrefix(prefix)
        if (!rest.startsWith("[")) return candidate

        val closingBracket = rest.indexOf(']')
        if (closingBracket < 0) return candidate

        val host = rest.substring(1, closingBracket).substringBefore('%')
        val remainder = rest.substring(closingBracket + 1)
        return "$prefix[$host]$remainder"
    }

    private fun hasExplicitPort(candidate: String): Boolean {
        val authority = candidate
            .substringAfter("://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('@')

        if (authority.startsWith("[")) {
            return authority.substringAfter("]", missingDelimiterValue = "").startsWith(":")
        }

        return authority.contains(':')
    }
}
