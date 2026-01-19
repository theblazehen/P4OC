package com.pocketcode.ui.components

import android.content.Context
import android.graphics.Typeface
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

@Composable
fun TermuxTerminalView(
    emulator: TerminalEmulator?,
    onKeyInput: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    val terminalViewClient = remember(onKeyInput) {
        createTerminalViewClient(context, onKeyInput)
    }
    
    AndroidView(
        factory = { ctx ->
            TerminalView(ctx, null).apply {
                setTextSize(14)
                setTypeface(Typeface.MONOSPACE)
                setTerminalViewClient(terminalViewClient)
                keepScreenOn = true
                isFocusable = true
                isFocusableInTouchMode = true
                requestFocus()
            }
        },
        update = { view ->
            emulator?.let { emu ->
                view.mEmulator = emu
                view.invalidate()
            }
        },
        modifier = modifier
    )
}

private fun createTerminalViewClient(
    context: Context,
    onKeyInput: (String) -> Unit
): TerminalViewClient {
    return object : TerminalViewClient {
        override fun onScale(scale: Float): Float = scale.coerceIn(10f, 40f)

        override fun onSingleTapUp(e: MotionEvent?) {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0)
        }

        override fun shouldBackButtonBeMappedToEscape(): Boolean = false

        override fun shouldEnforceCharBasedInput(): Boolean = true

        override fun shouldUseCtrlSpaceWorkaround(): Boolean = true

        override fun isTerminalViewSelected(): Boolean = true

        override fun copyModeChanged(copyMode: Boolean) {}

        override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean {
            return false
        }

        override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean = false

        override fun onLongPress(event: MotionEvent?): Boolean = false

        override fun readControlKey(): Boolean = false

        override fun readAltKey(): Boolean = false

        override fun readShiftKey(): Boolean = false

        override fun readFnKey(): Boolean = false

        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean {
            return false
        }

        override fun onEmulatorSet() {}

        override fun logError(tag: String?, message: String?) {
            android.util.Log.e(tag ?: "TerminalView", message ?: "")
        }

        override fun logWarn(tag: String?, message: String?) {
            android.util.Log.w(tag ?: "TerminalView", message ?: "")
        }

        override fun logInfo(tag: String?, message: String?) {
            android.util.Log.i(tag ?: "TerminalView", message ?: "")
        }

        override fun logDebug(tag: String?, message: String?) {
            android.util.Log.d(tag ?: "TerminalView", message ?: "")
        }

        override fun logVerbose(tag: String?, message: String?) {
            android.util.Log.v(tag ?: "TerminalView", message ?: "")
        }

        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
            android.util.Log.e(tag ?: "TerminalView", message, e)
        }

        override fun logStackTrace(tag: String?, e: Exception?) {
            android.util.Log.e(tag ?: "TerminalView", "Stack trace", e)
        }
    }
}
