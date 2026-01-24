package dev.blazelight.p4oc.terminal

import dev.blazelight.p4oc.core.network.PtyWebSocketClient
import com.termux.terminal.TerminalOutput

/**
 * Bridge between TerminalEmulator and WebSocket.
 * When the terminal emulator wants to send data to the shell (user input),
 * this class forwards it to the WebSocket connection.
 */
class WebSocketTerminalOutput(
    private val webSocket: PtyWebSocketClient,
    private val onTitleChanged: (String?, String?) -> Unit = { _, _ -> },
    private val onCopyText: (String) -> Unit = {},
    private val onPasteText: () -> Unit = {},
    private val onBell: () -> Unit = {},
    private val onColorsChanged: () -> Unit = {}
) : TerminalOutput() {

    override fun write(data: ByteArray, offset: Int, count: Int) {
        val text = String(data, offset, count, Charsets.UTF_8)
        webSocket.send(text)
    }

    override fun titleChanged(oldTitle: String?, newTitle: String?) {
        onTitleChanged(oldTitle, newTitle)
    }

    override fun onCopyTextToClipboard(text: String?) {
        text?.let { onCopyText(it) }
    }

    override fun onPasteTextFromClipboard() {
        onPasteText()
    }

    override fun onBell() {
        onBell.invoke()
    }

    override fun onColorsChanged() {
        onColorsChanged.invoke()
    }
}
