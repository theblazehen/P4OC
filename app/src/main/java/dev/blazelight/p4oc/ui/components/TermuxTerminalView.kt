package dev.blazelight.p4oc.ui.components

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

class KeyInterceptingContainer(context: Context) : FrameLayout(context) {
    
    var onKeyInput: ((String) -> Unit)? = null
    var terminalView: TerminalView? = null
    
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && terminalView?.mEmulator != null) {
            val handled = handleKeyDown(event)
            if (handled) return true
        }
        return super.dispatchKeyEvent(event)
    }
    
    private fun handleKeyDown(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        
        val code = when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> "\r"
            KeyEvent.KEYCODE_DEL -> "\u007f"
            KeyEvent.KEYCODE_FORWARD_DEL -> "\u001b[3~"
            KeyEvent.KEYCODE_TAB -> "\t"
            KeyEvent.KEYCODE_ESCAPE -> "\u001b"
            KeyEvent.KEYCODE_DPAD_UP -> "\u001b[A"
            KeyEvent.KEYCODE_DPAD_DOWN -> "\u001b[B"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "\u001b[C"
            KeyEvent.KEYCODE_DPAD_LEFT -> "\u001b[D"
            KeyEvent.KEYCODE_MOVE_HOME -> "\u001b[H"
            KeyEvent.KEYCODE_MOVE_END -> "\u001b[F"
            KeyEvent.KEYCODE_PAGE_UP -> "\u001b[5~"
            KeyEvent.KEYCODE_PAGE_DOWN -> "\u001b[6~"
            KeyEvent.KEYCODE_INSERT -> "\u001b[2~"
            else -> null
        }
        
        if (code != null) {
            onKeyInput?.invoke(code)
            return true
        }
        
        if (event.isCtrlPressed) {
            val char = event.unicodeChar and 0x1f
            if (char > 0) {
                onKeyInput?.invoke(char.toChar().toString())
                return true
            }
        }
        
        val unicodeChar = event.unicodeChar
        if (unicodeChar != 0) {
            onKeyInput?.invoke(unicodeChar.toChar().toString())
            return true
        }
        
        return false
    }
}

class TerminalInputView(context: Context) : View(context) {
    
    var onKeyInput: ((String) -> Unit)? = null
    
    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }
    
    override fun onCheckIsTextEditor(): Boolean = true
    
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_NULL
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_ACTION_NONE
        
        val view = this
        return object : BaseInputConnection(view, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                text?.toString()?.let { onKeyInput?.invoke(it) }
                return true
            }
            
            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                return true
            }
            
            override fun finishComposingText(): Boolean {
                return true
            }
            
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0) {
                    repeat(beforeLength) { onKeyInput?.invoke("\u007f") }
                }
                return true
            }
            
            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_DEL -> {
                            onKeyInput?.invoke("\u007f")
                            return true
                        }
                        KeyEvent.KEYCODE_ENTER -> {
                            onKeyInput?.invoke("\r")
                            return true
                        }
                    }
                    val unicodeChar = event.unicodeChar
                    if (unicodeChar != 0) {
                        onKeyInput?.invoke(unicodeChar.toChar().toString())
                        return true
                    }
                }
                return super.sendKeyEvent(event)
            }
        }
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val code = when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> "\r"
            KeyEvent.KEYCODE_DEL -> "\u007f"
            KeyEvent.KEYCODE_FORWARD_DEL -> "\u001b[3~"
            KeyEvent.KEYCODE_TAB -> "\t"
            KeyEvent.KEYCODE_ESCAPE -> "\u001b"
            KeyEvent.KEYCODE_DPAD_UP -> "\u001b[A"
            KeyEvent.KEYCODE_DPAD_DOWN -> "\u001b[B"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "\u001b[C"
            KeyEvent.KEYCODE_DPAD_LEFT -> "\u001b[D"
            else -> null
        }
        
        if (code != null) {
            onKeyInput?.invoke(code)
            return true
        }
        
        val unicodeChar = event.unicodeChar
        if (unicodeChar != 0) {
            onKeyInput?.invoke(unicodeChar.toChar().toString())
            return true
        }
        
        return super.onKeyDown(keyCode, event)
    }
}

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
            val container = FrameLayout(ctx)
            
            val terminalView = TerminalView(ctx, null).apply {
                setTextSize(14)
                setTypeface(Typeface.MONOSPACE)
                setTerminalViewClient(terminalViewClient)
                keepScreenOn = true
            }
            
            val inputView = TerminalInputView(ctx).apply {
                this.onKeyInput = onKeyInput
                layoutParams = FrameLayout.LayoutParams(1, 1)
            }
            
            container.addView(terminalView, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
            container.addView(inputView)
            
            terminalView.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    inputView.requestFocus()
                    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(inputView, InputMethodManager.SHOW_IMPLICIT)
                }
                false
            }
            
            container.tag = Pair(terminalView, inputView)
            inputView.requestFocus()
            container
        },
        update = { container ->
            @Suppress("UNCHECKED_CAST")
            val views = container.tag as? Pair<TerminalView, TerminalInputView>
            
            // Update the input callback on recomposition to avoid stale callbacks
            views?.second?.onKeyInput = onKeyInput
            
            emulator?.let { emu ->
                views?.first?.let { view ->
                    // Update the terminal view client to use the new callback
                    view.setTerminalViewClient(terminalViewClient)
                    view.mEmulator = emu
                    view.onScreenUpdated()
                }
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
            (context as? android.app.Activity)?.currentFocus?.let { view ->
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        override fun shouldBackButtonBeMappedToEscape(): Boolean = false

        override fun shouldEnforceCharBasedInput(): Boolean = true

        override fun shouldUseCtrlSpaceWorkaround(): Boolean = true

        override fun isTerminalViewSelected(): Boolean = true

        override fun copyModeChanged(copyMode: Boolean) {
            android.util.Log.d("TerminalView", "Copy mode changed: $copyMode")
        }

        override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean = false

        override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean = false

        override fun onLongPress(event: MotionEvent?): Boolean = false

        override fun readControlKey(): Boolean = false

        override fun readAltKey(): Boolean = false

        override fun readShiftKey(): Boolean = false

        override fun readFnKey(): Boolean = false

        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean {
            val char = if (ctrlDown && codePoint in 'a'.code..'z'.code) {
                (codePoint - 'a'.code + 1).toChar().toString()
            } else {
                codePoint.toChar().toString()
            }
            onKeyInput(char)
            return true
        }

        override fun onEmulatorSet() {
            android.util.Log.d("TerminalView", "Emulator set")
        }

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
