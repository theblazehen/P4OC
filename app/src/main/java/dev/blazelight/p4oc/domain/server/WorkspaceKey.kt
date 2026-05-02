package dev.blazelight.p4oc.domain.server

import dev.blazelight.p4oc.domain.session.SessionId

sealed interface WorkspaceKey {
    @JvmInline
    value class Directory(val value: String) : WorkspaceKey {
        init {
            require(value.isNotBlank()) { "Workspace directory must not be blank" }
        }
    }

    data object Global : WorkspaceKey

    @JvmInline
    value class SessionScoped(val sessionId: SessionId) : WorkspaceKey
}
