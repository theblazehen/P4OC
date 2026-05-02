package dev.blazelight.p4oc.ui.tabs

import android.os.Bundle
import dev.blazelight.p4oc.domain.model.SessionConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Represents a single tab in the top-level tab system.
 * 
 * Each tab has its own navigation stack (NavController created inside the pager page)
 * allowing independent navigation within each tab. Tabs are generic containers -
 * any screen type can be shown in any tab.
 */
data class TabState(
    /** Unique identifier for this tab */
    val id: String = UUID.randomUUID().toString(),
    
    /** 
     * Session ID if this tab is currently showing a chat session.
     * Used for uniqueness check - only one tab can show a given session.
     * Null if showing non-chat content (Sessions list, Files, Terminal, Settings).
     */
    val sessionId: String? = null,
    
    /**
     * Optional session title for display in tab bar.
     * Only populated when sessionId is set.
     */
    val sessionTitle: String? = null,

    /** Workspace directory owned by this tab. Null means server-global workspace. */
    val workspaceDirectory: String? = null,

    /** Incremented when workspace changes so navigation graph scoped ViewModels are recreated. */
    val workspaceRevision: Int = 0,
) {
    /**
     * Short title for tab bar display (max 6 chars with ellipsis).
     */
    val shortTitle: String get() = when {
        sessionTitle == null -> ""
        sessionTitle.length <= 6 -> sessionTitle
        else -> sessionTitle.take(5) + "…"
    }

    companion object {
        fun withId(
            id: String,
            sessionId: String? = null,
            sessionTitle: String? = null,
            workspaceDirectory: String? = null,
            workspaceRevision: Int = 0,
        ): TabState = TabState(
            id = id,
            sessionId = sessionId,
            sessionTitle = sessionTitle,
            workspaceDirectory = workspaceDirectory,
            workspaceRevision = workspaceRevision,
        )
    }
}

/**
 * Wrapper that holds TabState. NavController is created inside the HorizontalPager
 * page composition scope, not stored here (to avoid ViewModelStore lifecycle crashes).
 */
class TabInstance(
    val state: TabState,
    /** Declarative start route for this tab's NavHost. Defaults to Sessions list. */
    val startRoute: String = "sessions"
) {
    val id: String get() = state.id
    val sessionId: String? get() = state.sessionId
    val sessionTitle: String? get() = state.sessionTitle
    val workspaceDirectory: String? get() = state.workspaceDirectory
    val workspaceRevision: Int get() = state.workspaceRevision
    val shortTitle: String get() = state.shortTitle
    
    /** Saved NavController state for restoring after page disposal/recreation */
    var savedNavState: Bundle? = null
    
    /** Connection state for this tab (only relevant for chat tabs) */
    private val _connectionState = MutableStateFlow<SessionConnectionState?>(null)
    val connectionState: StateFlow<SessionConnectionState?> = _connectionState.asStateFlow()
    
    /** Update the connection state for this tab */
    fun updateConnectionState(state: SessionConnectionState?) {
        _connectionState.value = state
    }
    
    fun withState(newState: TabState): TabInstance {
        return TabInstance(newState, startRoute).also {
            it.savedNavState = this.savedNavState
            it._connectionState.value = this._connectionState.value
        }
    }
    
    fun withSessionId(sessionId: String?, sessionTitle: String? = null): TabInstance {
        return withState(state.copy(sessionId = sessionId, sessionTitle = sessionTitle))
    }

    fun withWorkspaceDirectory(directory: String?): TabInstance {
        val normalized = directory?.takeIf { it.isNotBlank() }
        if (normalized == state.workspaceDirectory) return this
        return withState(
            state.copy(
                workspaceDirectory = normalized,
                workspaceRevision = state.workspaceRevision + 1,
                sessionId = null,
                sessionTitle = null,
            ),
        )
    }
}
