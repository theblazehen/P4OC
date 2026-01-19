package com.pocketcode.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

class PtyTerminalClient(
    private val context: Context,
    private val onTextChanged: () -> Unit = {},
    private val onTitleChanged: (String?) -> Unit = {},
    private val onSessionFinished: () -> Unit = {},
    private val onBellCallback: () -> Unit = {},
    private val onColorsChangedCallback: () -> Unit = {},
    private val onCursorStateChange: (Boolean) -> Unit = {},
    private val onPasteRequest: ((String) -> Unit)? = null
) : TerminalSessionClient {

    private val clipboardManager: ClipboardManager by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    override fun onTextChanged(changedSession: TerminalSession) {
        onTextChanged.invoke()
    }

    override fun onTitleChanged(changedSession: TerminalSession) {
        onTitleChanged(changedSession.title)
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
        onSessionFinished.invoke()
    }

    override fun onBell(session: TerminalSession) {
        onBellCallback()
    }

    override fun onColorsChanged(session: TerminalSession) {
        onColorsChangedCallback()
    }

    override fun onTerminalCursorStateChange(state: Boolean) {
        onCursorStateChange(state)
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
        text?.let {
            val clip = ClipData.newPlainText("Terminal", it)
            clipboardManager.setPrimaryClip(clip)
        }
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        try {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val pasteText = clip.getItemAt(0).coerceToText(context).toString()
                if (pasteText.isNotEmpty()) {
                    onPasteRequest?.invoke(pasteText) ?: session?.write(pasteText)
                }
            }
        } catch (e: SecurityException) {
            // Android 10+ restricts clipboard access when app is not in focus
            Log.w("PtyTerminalClient", "Clipboard access denied - app may not be in focus", e)
        } catch (e: Exception) {
            Log.e("PtyTerminalClient", "Failed to paste from clipboard", e)
        }
    }

    override fun getTerminalCursorStyle(): Int {
        return TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE
    }

    override fun logError(tag: String?, message: String?) {
        Log.e(tag ?: "PtyTerminalClient", message ?: "Unknown error")
    }

    override fun logWarn(tag: String?, message: String?) {
        Log.w(tag ?: "PtyTerminalClient", message ?: "Unknown warning")
    }

    override fun logInfo(tag: String?, message: String?) {
        Log.i(tag ?: "PtyTerminalClient", message ?: "")
    }

    override fun logDebug(tag: String?, message: String?) {
        Log.d(tag ?: "PtyTerminalClient", message ?: "")
    }

    override fun logVerbose(tag: String?, message: String?) {
        Log.v(tag ?: "PtyTerminalClient", message ?: "")
    }

    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag ?: "PtyTerminalClient", message, e)
    }

    override fun logStackTrace(tag: String?, e: Exception?) {
        Log.e(tag ?: "PtyTerminalClient", "Stack trace", e)
    }
}
