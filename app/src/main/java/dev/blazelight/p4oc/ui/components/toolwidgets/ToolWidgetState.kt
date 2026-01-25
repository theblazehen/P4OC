package dev.blazelight.p4oc.ui.components.toolwidgets

/**
 * Display state for tool call widgets.
 * Supports progressive disclosure: Oneline → Compact → Expanded → Oneline
 */
enum class ToolWidgetState {
    /** Minimal single-line display: status icon + tool name */
    ONELINE,
    
    /** Summary display: tool name + brief description (~1-2 lines) */
    COMPACT,
    
    /** Full details: complete output, interactive elements, etc. */
    EXPANDED;
    
    /**
     * Get the next state in the cycle when user taps the widget.
     * Oneline → Compact → Expanded → Oneline
     */
    fun next(): ToolWidgetState = when (this) {
        ONELINE -> COMPACT
        COMPACT -> EXPANDED
        EXPANDED -> ONELINE
    }
    
    companion object {
        /**
         * Convert from string (for settings persistence)
         */
        fun fromString(value: String): ToolWidgetState = when (value.lowercase()) {
            "oneline" -> ONELINE
            "compact" -> COMPACT
            "expanded" -> EXPANDED
            else -> COMPACT // Default
        }
        
        /**
         * Convert to string (for settings persistence)
         */
        fun ToolWidgetState.toStringValue(): String = name.lowercase()
    }
}
