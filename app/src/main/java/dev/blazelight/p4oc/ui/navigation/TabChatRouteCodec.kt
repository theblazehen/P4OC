package dev.blazelight.p4oc.ui.navigation

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class TabChatRoute(
    val tabId: String,
    val sessionId: String,
)

object TabChatRouteCodec {
    const val TEMPLATE: String = "tab/{tabId}/chat/{sessionId}"

    fun encode(tabId: String, sessionId: String): String {
        require(tabId.isNotBlank()) { "Tab id must not be blank" }
        require(sessionId.isNotBlank()) { "Session id must not be blank" }
        return "tab/${tabId.routeEncode()}/chat/${sessionId.routeEncode()}"
    }

    fun chatRoute(sessionId: String): String {
        require(sessionId.isNotBlank()) { "Session id must not be blank" }
        return "chat/${sessionId.routeEncode()}"
    }

    fun decode(route: String): TabChatRoute? {
        val segments = route.split("/")
        if (segments.size != 4) return null
        if (segments[0] != "tab" || segments[2] != "chat") return null

        val tabId = segments[1].routeDecode()
        val sessionId = segments[3].routeDecode()
        if (tabId.isBlank() || sessionId.isBlank()) return null

        return TabChatRoute(tabId, sessionId)
    }
}

private fun String.routeEncode(): String = URLEncoder
    .encode(this, StandardCharsets.UTF_8.name())
    .replace("+", "%20")

private fun String.routeDecode(): String = URLDecoder.decode(this, StandardCharsets.UTF_8.name())
