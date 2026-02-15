package dev.blazelight.p4oc.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Semantic colors for git status indicators and file type icons.
 * Uses theme colors where possible, with Material-derived fallbacks for specificity.
 */
object SemanticColors {
    
    // Git Status Colors - map to theme semantic colors
    object Git {
        val added: Color @Composable get() = LocalOpenCodeTheme.current.success
        val modified: Color @Composable get() = LocalOpenCodeTheme.current.warning
        val deleted: Color @Composable get() = LocalOpenCodeTheme.current.error
        val renamed: Color @Composable get() = LocalOpenCodeTheme.current.info
        val copied: Color @Composable get() = LocalOpenCodeTheme.current.accent
        val untracked: Color @Composable get() = LocalOpenCodeTheme.current.textMuted
        val unmerged: Color @Composable get() = LocalOpenCodeTheme.current.warning
        val unknown: Color @Composable get() = LocalOpenCodeTheme.current.textMuted
    }
    
    // File Type Colors - derived from theme accent palette
    object FileType {
        // Code files - use success (green-ish)
        val code: Color @Composable get() = LocalOpenCodeTheme.current.success
        
        // Config files - use warning (orange-ish)
        val config: Color @Composable get() = LocalOpenCodeTheme.current.warning
        
        // Documentation - use info (blue-ish)
        val document: Color @Composable get() = LocalOpenCodeTheme.current.info
        
        // Media files - use accent/theme colors
        val image: Color @Composable get() = LocalOpenCodeTheme.current.accent
        val video: Color @Composable get() = LocalOpenCodeTheme.current.accent
        val audio: Color @Composable get() = LocalOpenCodeTheme.current.info
        
        // Archives - use textMuted
        val archive: Color @Composable get() = LocalOpenCodeTheme.current.textMuted
        
        // Shell/Terminal - use secondary
        val shell: Color @Composable get() = LocalOpenCodeTheme.current.secondary
        
        // Build files - use success
        val build: Color @Composable get() = LocalOpenCodeTheme.current.success
        
        // Git files - use error (red-ish) for visibility
        val git: Color @Composable get() = LocalOpenCodeTheme.current.error
        
        // Lock files - grey
        val lock: Color @Composable get() = LocalOpenCodeTheme.current.textMuted
        
        // Environment/secrets - orange for caution
        val env: Color @Composable get() = LocalOpenCodeTheme.current.warning
        
        // Web files - use primary
        val web: Color @Composable get() = LocalOpenCodeTheme.current.primary
        
        // Database - use accent
        val database: Color @Composable get() = LocalOpenCodeTheme.current.accent
    }
    
    // Diff colors for code diff viewing - derive from theme
    object Diff {
        val addedBackground: Color @Composable get() = LocalOpenCodeTheme.current.diffAddedBg
        val removedBackground: Color @Composable get() = LocalOpenCodeTheme.current.diffRemovedBg
        val addedText: Color @Composable get() = LocalOpenCodeTheme.current.diffAdded
        val removedText: Color @Composable get() = LocalOpenCodeTheme.current.diffRemoved
    }
    
    // Terminal colors - derive from theme
    object Terminal {
        val green: Color @Composable get() = LocalOpenCodeTheme.current.success
        val background: Color @Composable get() = LocalOpenCodeTheme.current.background
    }
    
    // Agent type colors - derive from theme semantic tokens
    object Agent {
        val coder: Color @Composable get() = LocalOpenCodeTheme.current.info
        val researcher: Color @Composable get() = LocalOpenCodeTheme.current.success
        val writer: Color @Composable get() = LocalOpenCodeTheme.current.accent
        val analyst: Color @Composable get() = LocalOpenCodeTheme.current.warning
        val planner: Color @Composable get() = LocalOpenCodeTheme.current.syntaxFunction
        val reviewer: Color @Composable get() = LocalOpenCodeTheme.current.error
        val debugger: Color @Composable get() = LocalOpenCodeTheme.current.error
        val explorer: Color @Composable get() = LocalOpenCodeTheme.current.accent
        val default: Color @Composable get() = LocalOpenCodeTheme.current.textMuted
        
        @Composable
        fun forName(name: String): Color = when (name.lowercase()) {
            "coder", "coding" -> coder
            "researcher", "research" -> researcher
            "writer", "writing" -> writer
            "analyst", "analysis" -> analyst
            "planner", "planning" -> planner
            "reviewer", "review" -> reviewer
            "debugger", "debug" -> debugger
            "explorer", "explore" -> explorer
            else -> default
        }
    }
    
    // UI accent colors
    object Accent {
        val favorite: Color @Composable get() = LocalOpenCodeTheme.current.warning
    }
    
    // Status colors for general use
    object Status {
        val success: Color @Composable get() = LocalOpenCodeTheme.current.success
        val successBackground: Color @Composable get() = LocalOpenCodeTheme.current.success.copy(alpha = 0.2f)
    }
    
    // Provider brand colors - intentionally hardcoded (brand identity)
    object Provider {
        val anthropic: Color get() = Color(0xFFD97706)
        val openai: Color get() = Color(0xFF10A37F)
        val google: Color get() = Color(0xFF4285F4)
        val aws: Color get() = Color(0xFFFF9900)
        val azure: Color get() = Color(0xFF0078D4)
        val mistral: Color get() = Color(0xFFFF6B35)
        val groq: Color get() = Color(0xFFF55036)
        val ollama: Color get() = Color(0xFF6366F1)
        val xai: Color get() = Color(0xFF000000)
        val deepseek: Color get() = Color(0xFF0066FF)
        val default: Color get() = Color(0xFF78909C)
        
        fun forName(name: String): Pair<Color, String> = when (name.lowercase()) {
            "anthropic" -> anthropic to "A"
            "openai" -> openai to "O"
            "google" -> google to "G"
            "aws", "amazon" -> aws to "A"
            "azure" -> azure to "Az"
            "mistral" -> mistral to "M"
            "groq" -> groq to "Gr"
            "ollama" -> ollama to "Ol"
            "xai" -> xai to "X"
            "deepseek" -> deepseek to "D"
            else -> default to name.take(2).uppercase()
        }
    }
    
    // MIME type colors for file attachments - derive from theme
    object MimeType {
        val image: Color @Composable get() = LocalOpenCodeTheme.current.success
        val video: Color @Composable get() = LocalOpenCodeTheme.current.error
        val audio: Color @Composable get() = LocalOpenCodeTheme.current.accent
        val text: Color @Composable get() = LocalOpenCodeTheme.current.info
        val pdf: Color @Composable get() = LocalOpenCodeTheme.current.error
        val archive: Color @Composable get() = LocalOpenCodeTheme.current.warning
        val data: Color @Composable get() = LocalOpenCodeTheme.current.syntaxFunction
        val default: Color @Composable get() = LocalOpenCodeTheme.current.textMuted
        
        @Composable
        fun forMimeType(mimeType: String): Color = when {
            mimeType.startsWith("image/") -> image
            mimeType.startsWith("video/") -> video
            mimeType.startsWith("audio/") -> audio
            mimeType.startsWith("text/") -> text
            mimeType.contains("pdf") -> pdf
            mimeType.contains("zip") -> archive
            mimeType.contains("json") || mimeType.contains("xml") -> data
            else -> default
        }
    }
    
    // Reason/status indicator colors - derive from theme
    object Reason {
        val success: Color @Composable get() = LocalOpenCodeTheme.current.success
        val info: Color @Composable get() = LocalOpenCodeTheme.current.info
        val warning: Color @Composable get() = LocalOpenCodeTheme.current.warning
        val error: Color @Composable get() = LocalOpenCodeTheme.current.error
        val default: Color @Composable get() = LocalOpenCodeTheme.current.textMuted
        
        @Composable
        fun forReason(reason: String): Color = when (reason.lowercase()) {
            "end_turn", "stop" -> success
            "tool_use", "tool_calls" -> info
            "max_tokens" -> warning
            "error" -> error
            else -> default
        }
    }
    
    // Permission type colors - derive from theme
    object Permission {
        val write: Color @Composable get() = LocalOpenCodeTheme.current.warning
        val read: Color @Composable get() = LocalOpenCodeTheme.current.success
        val execute: Color @Composable get() = LocalOpenCodeTheme.current.error
        val delete: Color @Composable get() = LocalOpenCodeTheme.current.error
        val default: Color @Composable get() = LocalOpenCodeTheme.current.info
        
        @Composable
        fun forType(type: String): Color = when (type.lowercase()) {
            "file.write", "file.edit" -> write
            "file.read" -> read
            "bash", "shell", "command" -> execute
            "file.delete" -> delete
            else -> default
        }
    }
    
    // Context usage percentage colors - derive from theme
    object Usage {
        val critical: Color @Composable get() = LocalOpenCodeTheme.current.error
        val high: Color @Composable get() = LocalOpenCodeTheme.current.warning
        val medium: Color @Composable get() = LocalOpenCodeTheme.current.warning
        // Below 50% uses theme primary
    }
    
    // Agent selector colors - derive from theme
    object AgentSelector {
        val build: Color @Composable get() = LocalOpenCodeTheme.current.info
        val plan: Color @Composable get() = LocalOpenCodeTheme.current.accent
        val default: Color @Composable get() = LocalOpenCodeTheme.current.textMuted
        
        @Composable
        fun forName(name: String): Color = when (name.lowercase()) {
            "build" -> build
            "plan" -> plan
            else -> default
        }
    }
    
    // Terminal extra keys bar colors - derive from theme
    object TerminalKeys {
        val background: Color @Composable get() = LocalOpenCodeTheme.current.background
        val keyText: Color @Composable get() = LocalOpenCodeTheme.current.textMuted
        val keyTextActive: Color @Composable get() = LocalOpenCodeTheme.current.text
        val keyPressed: Color @Composable get() = LocalOpenCodeTheme.current.backgroundElement
        val activeModifier: Color @Composable get() = LocalOpenCodeTheme.current.success
    }
    
    // Todo status colors - derive from theme
    object Todo {
        val pending: Color @Composable get() = LocalOpenCodeTheme.current.textMuted
        val inProgress: Color @Composable get() = LocalOpenCodeTheme.current.info
        val completed: Color @Composable get() = LocalOpenCodeTheme.current.success
        val cancelled: Color @Composable get() = LocalOpenCodeTheme.current.error
        
        val priorityHigh: Color @Composable get() = LocalOpenCodeTheme.current.error
        val priorityMedium: Color @Composable get() = LocalOpenCodeTheme.current.warning
        val priorityLow: Color @Composable get() = LocalOpenCodeTheme.current.success
        val priorityDefault: Color @Composable get() = LocalOpenCodeTheme.current.textMuted
        
        @Composable
        fun forStatus(status: String): Color = when (status) {
            "pending" -> pending
            "in_progress" -> inProgress
            "completed" -> completed
            "cancelled" -> cancelled
            else -> pending
        }
        
        @Composable
        fun forPriority(priority: String): Color = when (priority.lowercase()) {
            "high" -> priorityHigh
            "medium" -> priorityMedium
            "low" -> priorityLow
            else -> priorityDefault
        }
    }
    
    // Syntax highlighting colors - derive from theme
    object Syntax {
        val comment: Color @Composable get() = LocalOpenCodeTheme.current.syntaxComment
        val string: Color @Composable get() = LocalOpenCodeTheme.current.syntaxString
        val keyword: Color @Composable get() = LocalOpenCodeTheme.current.syntaxKeyword
        val type: Color @Composable get() = LocalOpenCodeTheme.current.syntaxType
        val text: Color @Composable get() = LocalOpenCodeTheme.current.text
        val number: Color @Composable get() = LocalOpenCodeTheme.current.syntaxNumber
        val background: Color @Composable get() = LocalOpenCodeTheme.current.backgroundElement
    }
}
