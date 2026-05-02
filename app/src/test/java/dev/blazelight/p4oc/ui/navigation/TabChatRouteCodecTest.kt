package dev.blazelight.p4oc.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TabChatRouteCodecTest {
    @Test
    fun `tab chat route encode decode round trips ids without workspace directory`() {
        val route = TabChatRouteCodec.encode(
            tabId = "tab 1/with slash",
            sessionId = "session/ü 42",
        )

        assertEquals(TabChatRoute("tab 1/with slash", "session/ü 42"), TabChatRouteCodec.decode(route))
        assertEquals(false, route.contains("directory"))
    }

    @Test
    fun `tab chat route encode rejects blank ids`() {
        assertThrows(IllegalArgumentException::class.java) {
            TabChatRouteCodec.encode(tabId = "", sessionId = "session")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TabChatRouteCodec.encode(tabId = "tab", sessionId = "   ")
        }
    }

    @Test
    fun `tab chat route decode rejects unrelated and blank routes`() {
        assertNull(TabChatRouteCodec.decode("chat/session?directory=/repo"))
        assertNull(TabChatRouteCodec.decode("tab/%20/chat/session"))
    }
}
