package dev.blazelight.p4oc.ui.tabs

import androidx.navigation.NavHostController
import dev.blazelight.p4oc.domain.model.SessionConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Represents a single tab in the top-level tab system.
 * 
 * Each tab has its own navigation stack (NavController) allowing independent
 * navigation within each tab. Tabs are generic containers - any screen type
 * can be shown in any tab.
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
    val sessionTitle: String? = null
) {
    /**
     * Short title for tab bar display (max 6 chars with ellipsis).
     */
    val shortTitle: String get() = when {
        sessionTitle == null -> ""
        sessionTitle.length <= 6 -> sessionTitle
        else -> sessionTitle.take(5) + "…"
    }
}

/**
 * Wrapper that holds TabState along with its NavController.
 * NavController is not data class friendly, so we keep it separate.
 */
class TabInstance(
    val state: TabState,
    val navController: NavHostController
) {
    val id: String get() = state.id
    val sessionId: String? get() = state.sessionId
    val sessionTitle: String? get() = state.sessionTitle
    val shortTitle: String get() = state.shortTitle
    
    /** Connection state for this tab (only relevant for chat tabs) */
    private val _connectionState = MutableStateFlow<SessionConnectionState?>(null)
    val connectionState: StateFlow<SessionConnectionState?> = _connectionState.asStateFlow()
    
    /** Update the connection state for this tab */
    fun updateConnectionState(state: SessionConnectionState?) {
        _connectionState.value = state
    }
    
    fun withState(newState: TabState): TabInstance {
        return TabInstance(newState, navController).also {
            it._connectionState.value = this._connectionState.value
        }
    }
    
    fun withSessionId(sessionId: String?, sessionTitle: String? = null): TabInstance {
        return withState(state.copy(sessionId = sessionId, sessionTitle = sessionTitle))
    }
}
