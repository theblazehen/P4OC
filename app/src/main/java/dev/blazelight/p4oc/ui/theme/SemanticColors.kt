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
        val unmerged: Color @Composable get() = Color(0xFFFF5722) // Deep orange for conflicts
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
        
        // Media files - use accent colors
        val image: Color @Composable get() = Color(0xFFE91E63) // Pink
        val video: Color @Composable get() = LocalOpenCodeTheme.current.accent
        val audio: Color @Composable get() = Color(0xFF00BCD4) // Cyan
        
        // Archives - brown
        val archive: Color @Composable get() = Color(0xFF795548)
        
        // Shell/Terminal - blue grey
        val shell: Color @Composable get() = Color(0xFF607D8B)
        
        // Build files - teal
        val build: Color @Composable get() = Color(0xFF009688)
        
        // Git files - use error (red-ish) for visibility
        val git: Color @Composable get() = LocalOpenCodeTheme.current.error
        
        // Lock files - grey
        val lock: Color @Composable get() = LocalOpenCodeTheme.current.textMuted
        
        // Environment/secrets - orange for caution
        val env: Color @Composable get() = Color(0xFFFF5722)
        
        // Web files - indigo
        val web: Color @Composable get() = Color(0xFF3F51B5)
        
        // Database - deep purple
        val database: Color @Composable get() = Color(0xFF673AB7)
    }
    
    // Diff colors for code diff viewing
    object Diff {
        val addedBackground: Color @Composable get() = Color(0xFF2E7D32).copy(alpha = 0.15f)
        val removedBackground: Color @Composable get() = Color(0xFFC62828).copy(alpha = 0.15f)
        val addedText: Color @Composable get() = Color(0xFF4CAF50)
        val removedText: Color @Composable get() = Color(0xFFF44336)
    }
    
    // Terminal colors
    object Terminal {
        val green: Color get() = Color(0xFF00FF00)
        val background: Color get() = Color.Black
    }
    
    // Agent type colors
    object Agent {
        val coder: Color get() = Color(0xFF42A5F5)
        val researcher: Color get() = Color(0xFF66BB6A)
        val writer: Color get() = Color(0xFFAB47BC)
        val analyst: Color get() = Color(0xFFFFA726)
        val planner: Color get() = Color(0xFF26A69A)
        val reviewer: Color get() = Color(0xFFEF5350)
        val debugger: Color get() = Color(0xFFEC407A)
        val explorer: Color get() = Color(0xFF7E57C2)
        val default: Color get() = Color(0xFF78909C)
        
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
        val favorite: Color get() = Color(0xFFFFC107) // Gold/Amber for stars
    }
    
    // Status colors for general use
    object Status {
        val success: Color get() = Color(0xFF4CAF50)
        val successBackground: Color get() = Color(0xFF4CAF50).copy(alpha = 0.2f)
    }
    
    // Provider brand colors
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
    
    // MIME type colors for file attachments
    object MimeType {
        val image: Color get() = Color(0xFF66BB6A) // Green
        val video: Color get() = Color(0xFFEF5350) // Red
        val audio: Color get() = Color(0xFFAB47BC) // Purple
        val text: Color get() = Color(0xFF42A5F5) // Blue
        val pdf: Color get() = Color(0xFFEF5350) // Red
        val archive: Color get() = Color(0xFFFFA726) // Orange
        val data: Color get() = Color(0xFF26A69A) // Teal (JSON, XML)
        val default: Color get() = Color(0xFF78909C) // Blue grey
        
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
    
    // Reason/status indicator colors
    object Reason {
        val success: Color get() = Color(0xFF4CAF50) // Green
        val info: Color get() = Color(0xFF2196F3) // Blue
        val warning: Color get() = Color(0xFFFFA726) // Orange
        val error: Color get() = Color(0xFFF44336) // Red
        val default: Color get() = Color(0xFF78909C) // Blue grey
        
        fun forReason(reason: String): Color = when (reason.lowercase()) {
            "end_turn", "stop" -> success
            "tool_use", "tool_calls" -> info
            "max_tokens" -> warning
            "error" -> error
            else -> default
        }
    }
    
    // Permission type colors
    object Permission {
        val write: Color get() = Color(0xFFFFA726) // Orange - caution
        val read: Color get() = Color(0xFF66BB6A) // Green - safe
        val execute: Color get() = Color(0xFFEF5350) // Red - danger
        val delete: Color get() = Color(0xFFEF5350) // Red - danger
        val default: Color get() = Color(0xFF42A5F5) // Blue
        
        fun forType(type: String): Color = when (type.lowercase()) {
            "file.write", "file.edit" -> write
            "file.read" -> read
            "bash", "shell", "command" -> execute
            "file.delete" -> delete
            else -> default
        }
    }
    
    // Context usage percentage colors
    object Usage {
        val critical: Color get() = Color(0xFFF44336) // Red - >90%
        val high: Color get() = Color(0xFFFFA726) // Orange - >75%
        val medium: Color get() = Color(0xFFFFEE58) // Yellow - >50%
        // Below 50% uses theme primary
    }
    
    // Agent selector colors
    object AgentSelector {
        val build: Color get() = Color(0xFF3B82F6) // Blue
        val plan: Color get() = Color(0xFFA855F7) // Purple
        val default: Color get() = Color(0xFF6B7280) // Grey
        
        fun forName(name: String): Color = when (name.lowercase()) {
            "build" -> build
            "plan" -> plan
            else -> default
        }
    }
    
    // Terminal extra keys bar colors
    object TerminalKeys {
        val background: Color get() = Color(0xFF2B2B2B)
        val keyBackground: Color get() = Color(0xFF3C3C3C)
        val keyText: Color get() = Color(0xFFE0E0E0)
        val keyDisabledBackground: Color get() = Color(0xFF2A2A2A)
        val keyDisabledText: Color get() = Color(0xFF666666)
        val activeModifier: Color get() = Color(0xFF4CAF50) // Green when active
    }
    
    // Todo status colors
    object Todo {
        val pending: Color get() = Color(0xFF9E9E9E) // Grey
        val inProgress: Color get() = Color(0xFF2196F3) // Blue
        val completed: Color get() = Color(0xFF4CAF50) // Green
        val cancelled: Color get() = Color(0xFFF44336) // Red
        
        val priorityHigh: Color get() = Color(0xFFF44336) // Red
        val priorityMedium: Color get() = Color(0xFFFF9800) // Orange
        val priorityLow: Color get() = Color(0xFF4CAF50) // Green
        val priorityDefault: Color get() = Color(0xFF9E9E9E) // Grey
        
        fun forStatus(status: String): Color = when (status) {
            "pending" -> pending
            "in_progress" -> inProgress
            "completed" -> completed
            "cancelled" -> cancelled
            else -> pending
        }
        
        fun forPriority(priority: String): Color = when (priority.lowercase()) {
            "high" -> priorityHigh
            "medium" -> priorityMedium
            "low" -> priorityLow
            else -> priorityDefault
        }
    }
    
    // Syntax highlighting colors (VS Code Dark+ inspired)
    object Syntax {
        val comment: Color get() = Color(0xFF6A9955) // Green
        val string: Color get() = Color(0xFFCE9178) // Orange/Brown
        val keyword: Color get() = Color(0xFF569CD6) // Blue
        val type: Color get() = Color(0xFF4EC9B0) // Teal
        val text: Color get() = Color(0xFFD4D4D4) // Light grey
        val number: Color get() = Color(0xFFB5CEA8) // Light green
        val background: Color get() = Color(0xFF1E1E1E) // Dark background
    }
}
