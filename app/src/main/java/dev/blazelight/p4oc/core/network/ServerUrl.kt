package dev.blazelight.p4oc.core.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object ServerUrl {
    const val DEFAULT_PORT = 4096
    const val DEFAULT_USERNAME = "opencode"

    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        val candidate = if (trimmed.contains("://")) trimmed else "http://$trimmed"
        val sanitizedCandidate = stripIpv6ZoneId(candidate)
        val parsed = sanitizedCandidate.toHttpUrlOrNull() ?: return null
        val scheme = parsed.scheme.lowercase()
        if (scheme != "http" && scheme != "https") return null

        val host = parsed.host.substringBefore('%').lowercase()
        val formattedHost = if (':' in host) "[$host]" else host
        val port = if (hasExplicitPort(sanitizedCandidate)) parsed.port else DEFAULT_PORT

        return "$scheme://$formattedHost:$port"
    }

    fun endpointKey(input: String): String? = normalize(input)

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
