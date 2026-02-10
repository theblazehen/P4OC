package dev.blazelight.p4oc.domain.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme

/**
 * Represents the connection and activity state of a session.
 * 
 * Hot sessions (SSE connected): ACTIVE, BUSY, AWAITING_INPUT, IDLE
 * Cold sessions (disconnected): BACKGROUND, ERROR
 */
enum class SessionConnectionState {
    /** Currently viewing this session, SSE connected */
    ACTIVE,
    
    /** SSE connected, agent is processing (streaming response) */
    BUSY,
    
    /** SSE connected, waiting for user action (question or permission) */
    AWAITING_INPUT,
    
    /** SSE connected, no activity */
    IDLE,
    
    /** Not connected, requires reload from server */
    BACKGROUND,
    
    /** Connection failed */
    ERROR;
    
    /** Whether this state represents a "hot" session with active SSE connection */
    val isHot: Boolean
        get() = this in listOf(ACTIVE, BUSY, AWAITING_INPUT, IDLE)
    
    /** Priority for inbox-style ordering (lower = higher priority, appears leftmost) */
    val priority: Int
        get() = when (this) {
            AWAITING_INPUT -> 0
            BUSY -> 1
            ACTIVE -> 2
            IDLE -> 3
            ERROR -> 4
            BACKGROUND -> 5
        }
    
    /** Whether this state should show a pulsing animation */
    val shouldPulse: Boolean
        get() = this == BUSY || this == AWAITING_INPUT
    
    /** Whether this state should show an attention badge */
    val showsAttentionBadge: Boolean
        get() = this == AWAITING_INPUT
}

/**
 * Centralized color mapping for session connection states.
 * Use this to get consistent indicator colors across all UI components.
 */
object SessionStateColors {
    /**
     * Returns the appropriate indicator color for a session connection state.
     * 
     * Color mapping:
     * - ACTIVE, BUSY: primary (active work)
     * - AWAITING_INPUT: warning (needs attention)
     * - IDLE: textMuted (inactive)
     * - BACKGROUND: borderSubtle (disconnected)
     * - ERROR: error (problem)
     */
    @Composable
    fun forState(state: SessionConnectionState): Color {
        val theme = LocalOpenCodeTheme.current
        return when (state) {
            SessionConnectionState.ACTIVE -> theme.primary
            SessionConnectionState.BUSY -> theme.primary
            SessionConnectionState.AWAITING_INPUT -> theme.warning
            SessionConnectionState.IDLE -> theme.textMuted
            SessionConnectionState.BACKGROUND -> theme.borderSubtle
            SessionConnectionState.ERROR -> theme.error
        }
    }
    
    /**
     * Returns the indicator color for a nullable state, or null if state is null.
     * Useful for tabs that may or may not have a connection state.
     */
    @Composable
    fun forStateOrNull(state: SessionConnectionState?): Color? {
        return state?.let { forState(it) }
    }
}
