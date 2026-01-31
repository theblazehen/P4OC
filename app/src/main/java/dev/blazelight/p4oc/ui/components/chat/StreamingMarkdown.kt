package dev.blazelight.p4oc.ui.components.chat

import android.content.Context
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
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
            
            // Always use immediate rendering - no artificial delays
            if (text != lastText) {
                view.setMarkdownText(text)
                // Ensure the hosting LazyColumn sees the new measured height.
                view.requestLayout()
                lastText = text
            }
        }
    )
}

private fun createMarkdownTextView(
    context: Context,
    styles: MarkdownStyles,
    onHeightChange: ((Int) -> Unit)?
): PrinterMarkDownTextView {
    // FluidMarkdown's view relies on AppCompat theme attributes; the app theme is a platform Material theme.
    val themedContext = ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_Light_NoActionBar)

    return PrinterMarkDownTextView(themedContext).apply {
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
        
    }
}
