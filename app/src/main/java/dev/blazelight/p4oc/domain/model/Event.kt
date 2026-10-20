package dev.blazelight.p4oc.domain.model

import kotlinx.serialization.Serializable

sealed class OpenCodeEvent {
    data class MessageUpdated(val message: Message) : OpenCodeEvent()
    data class MessagePartUpdated(val part: Part, val delta: String?) : OpenCodeEvent()

    /** V2 publishes message projections; refresh the owned REST window instead of fabricating parts. */
    data class MessageRefreshRequested(val sessionID: String) : OpenCodeEvent()

    /** Ephemeral v2 text/reasoning content, keyed by the REST projection's per-type ordinal. */
    data class V2ContentChanged(
        val sessionID: String,
        val messageID: String,
        val partID: String,
        val kind: String,
        val text: String,
        val phase: String,
    ) : OpenCodeEvent()
    data class MessagePartDelta(
        val sessionID: String?,
        val messageID: String,
        val partID: String,
        val field: String,
        val delta: String,
    ) : OpenCodeEvent()
    data class MessageRemoved(val sessionID: String, val messageID: String) : OpenCodeEvent()
    data class PartRemoved(val sessionID: String, val messageID: String, val partID: String) : OpenCodeEvent()
    data class SessionCreated(val session: Session) : OpenCodeEvent()
    data class SessionUpdated(val session: Session) : OpenCodeEvent()
    data class SessionDeleted(val session: Session) : OpenCodeEvent()

    /** V2 events carry IDs and deltas, not a complete legacy Session snapshot. */
    data class SessionRefreshRequested(val sessionID: String, val removed: Boolean = false) : OpenCodeEvent()
    data class SessionStatusChanged(val sessionID: String, val status: SessionStatus) : OpenCodeEvent()
    data class SessionDiff(val sessionID: String, val diffs: List<FileDiff>) : OpenCodeEvent()
    data class SessionError(val sessionID: String?, val error: MessageError?) : OpenCodeEvent()
    data class SessionCompacted(val sessionID: String) : OpenCodeEvent()
    data class SessionIdle(val sessionID: String) : OpenCodeEvent()
    data class PermissionRequested(val permission: Permission) : OpenCodeEvent()
    data class PermissionReplied(val sessionID: String, val requestID: String, val reply: String) : OpenCodeEvent()
    data class QuestionAsked(val request: QuestionRequest) : OpenCodeEvent()
    data class QuestionReplied(
        val sessionID: String,
        val requestID: String,
        val answers: List<List<String>>,
    ) : OpenCodeEvent()
    data class QuestionRejected(val sessionID: String, val requestID: String) : OpenCodeEvent()

    /** V2 forms are read from their session rather than adapted into legacy questions. */
    data class FormRefreshRequested(val sessionID: String) : OpenCodeEvent()
    data class TodoUpdated(val sessionID: String, val todos: List<Todo>) : OpenCodeEvent()
    data class CommandExecuted(
        val name: String,
        val sessionID: String,
        val arguments: String,
        val messageID: String
    ) : OpenCodeEvent()
    data class FileEdited(val file: String) : OpenCodeEvent()
    data class FileWatcherUpdated(val file: String, val event: String) : OpenCodeEvent()
    data class VcsBranchUpdated(val branch: String?) : OpenCodeEvent()

    // Project and catalog events (aligned with SDK)
    data class ProjectUpdated(val project: Project) : OpenCodeEvent()

    /** V2 project projections require a fresh REST read for local workspace metadata. */
    data class ProjectRefreshRequested(val projectID: String) : OpenCodeEvent()
    data class ProjectDirectoriesUpdated(val projectID: String) : OpenCodeEvent()
    data object ModelsRefreshed : OpenCodeEvent()
    data object CatalogUpdated : OpenCodeEvent()
    data class McpToolsChanged(val server: String) : OpenCodeEvent()

    // Global lifecycle events (aligned with SDK)
    data object GlobalDisposed : OpenCodeEvent()

    /** The server rebuilt this location; all volatile pending UI state must be reconciled. */
    data class LocationShutdown(val directory: String?) : OpenCodeEvent()

    data object Connected : OpenCodeEvent()
    data class Disconnected(val reason: String?) : OpenCodeEvent()
    data class Error(val throwable: Throwable) : OpenCodeEvent()

    // Installation events (aligned with SDK)
    data class InstallationUpdated(val version: String) : OpenCodeEvent()
    data class InstallationUpdateAvailable(val version: String) : OpenCodeEvent()

    // LSP events (aligned with SDK)
    data class LspClientDiagnostics(val serverID: String, val path: String) : OpenCodeEvent()
    data object LspUpdated : OpenCodeEvent()

    // PTY events (aligned with SDK)
    data class PtyCreated(val pty: Pty) : OpenCodeEvent()
    data class PtyUpdated(val pty: Pty) : OpenCodeEvent()
    data class PtyExited(val id: String, val exitCode: Int) : OpenCodeEvent()
    data class PtyDeleted(val id: String) : OpenCodeEvent()

    // Server events (aligned with SDK)
    data class ServerInstanceDisposed(val directory: String) : OpenCodeEvent()
}

/**
 * The session an event belongs to, or null for events that are not session-scoped.
 * Used to route events that arrive without a location to the workspace that owns the session
 * (design lock F, routing rule 2): OpenCode v2 publishes e.g. `session.execution.failed`
 * without a location, only with `sessionID`.
 */
@Suppress("CyclomaticComplexMethod") // One flat entry per session-scoped event type; no real branching.
fun OpenCodeEvent.sessionIdOrNull(): String? = when (this) {
    is OpenCodeEvent.MessageUpdated -> message.sessionID
    is OpenCodeEvent.MessagePartUpdated -> part.sessionID
    is OpenCodeEvent.MessageRefreshRequested -> sessionID
    is OpenCodeEvent.V2ContentChanged -> sessionID
    is OpenCodeEvent.MessagePartDelta -> sessionID
    is OpenCodeEvent.MessageRemoved -> sessionID
    is OpenCodeEvent.PartRemoved -> sessionID
    is OpenCodeEvent.SessionCreated -> session.id
    is OpenCodeEvent.SessionUpdated -> session.id
    is OpenCodeEvent.SessionDeleted -> session.id
    is OpenCodeEvent.SessionRefreshRequested -> sessionID
    is OpenCodeEvent.SessionStatusChanged -> sessionID
    is OpenCodeEvent.SessionDiff -> sessionID
    is OpenCodeEvent.SessionError -> sessionID
    is OpenCodeEvent.SessionCompacted -> sessionID
    is OpenCodeEvent.SessionIdle -> sessionID
    is OpenCodeEvent.PermissionRequested -> permission.sessionID
    is OpenCodeEvent.PermissionReplied -> sessionID
    is OpenCodeEvent.QuestionAsked -> request.sessionID
    is OpenCodeEvent.QuestionReplied -> sessionID
    is OpenCodeEvent.QuestionRejected -> sessionID
    is OpenCodeEvent.FormRefreshRequested -> sessionID
    is OpenCodeEvent.TodoUpdated -> sessionID
    is OpenCodeEvent.CommandExecuted -> sessionID
    else -> null
}

@Serializable
sealed class SessionStatus {
    @Serializable
    data object Idle : SessionStatus()

    @Serializable
    data object Busy : SessionStatus()

    @Serializable
    data class Retry(val attempt: Int, val message: String, val next: Long) : SessionStatus()
}
