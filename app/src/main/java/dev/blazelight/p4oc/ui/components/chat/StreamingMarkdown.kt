package dev.blazelight.p4oc.ui.components.chat

import android.content.Context
import android.view.ViewGroup
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.fluid.afm.markdown.widget.PrinterMarkDownTextView
import com.fluid.afm.styles.MarkdownStyles

/**
 * Compose wrapper for FluidMarkdown's PrinterMarkDownTextView.
 * Handles both static and streaming markdown content with proper height change callbacks.
 */
@Composable
fun StreamingMarkdown(
    text: String,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    styles: MarkdownStyles = rememberMarkdownStyles(),
    onHeightChange: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current
    
    // Track the last text we've seen to detect new content
    var lastText by remember { mutableStateOf("") }
    var isStreamingStarted by remember { mutableStateOf(false) }
    
    // Key on styles to recreate view when theme changes
    val stylesKey = remember(styles) { System.identityHashCode(styles) }
    
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            createMarkdownTextView(ctx, styles, onHeightChange)
        },
        update = { view ->
            // Update styles if changed
            view.setMarkdownStyles(styles)
            
            if (text.isEmpty()) {
                view.setMarkdownText("")
                lastText = ""
                isStreamingStarted = false
                return@AndroidView
            }
            
            if (isStreaming) {
                if (!isStreamingStarted) {
                    // Start new streaming session
                    view.startPrinting(text)
                    isStreamingStarted = true
                    lastText = text
                } else if (text != lastText) {
                    // Append new content
                    view.appendPrinting(text, false) // false = replace full text
                    lastText = text
                }
            } else {
                // Static content or streaming finished
                if (isStreamingStarted) {
                    // Streaming just finished - stop gracefully
                    view.stopPrinting("")
                    isStreamingStarted = false
                }
                // Set final static content
                if (text != lastText || !isStreamingStarted) {
                    view.setMarkdownText(text)
                    lastText = text
                }
            }
        }
    )
}

private fun createMarkdownTextView(
    context: Context,
    styles: MarkdownStyles,
    onHeightChange: ((Int) -> Unit)?
): PrinterMarkDownTextView {
    return PrinterMarkDownTextView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        
        // Initialize with styles
        init(styles, null)
        
        // Set up height change listener for scroll coordination
        onHeightChange?.let { callback ->
            setSizeChangedListener { _, height ->
                callback(height)
            }
        }
        
        // Configure print parameters for smooth streaming
        setPrintParams(25, 1) // 25ms interval, 1 char at a time
    }
}
