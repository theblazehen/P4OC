package dev.blazelight.p4oc.data.session

import dev.blazelight.p4oc.data.remote.dto.ProjectDto
import dev.blazelight.p4oc.domain.model.SessionStatus
import dev.blazelight.p4oc.domain.session.WorkspaceSession

sealed interface RepoState {
    val snapshot: Snapshot

    data class Hydrating(
        override val snapshot: Snapshot = Snapshot(),
        val bufferedEvents: Int = 0,
    ) : RepoState

    data class Live(override val snapshot: Snapshot) : RepoState

    data class Stale(
        override val snapshot: Snapshot,
        val reason: String? = null,
    ) : RepoState
}

data class Snapshot(
    val sessions: Map<String, WorkspaceSession> = emptyMap(),
    val projects: List<ProjectDto> = emptyList(),
    val statuses: Map<String, SessionStatus> = emptyMap(),
)
