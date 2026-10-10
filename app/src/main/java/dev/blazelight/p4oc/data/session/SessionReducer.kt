package dev.blazelight.p4oc.data.session

import dev.blazelight.p4oc.data.files.ofish.OfishSessionNames
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.model.Session
import dev.blazelight.p4oc.domain.model.SessionStatus
import dev.blazelight.p4oc.domain.session.SessionId
import dev.blazelight.p4oc.domain.session.WorkspaceSession
import dev.blazelight.p4oc.domain.workspace.Workspace

open class SessionReducer(
    private val workspace: Workspace,
) {
    open fun reduce(snapshot: Snapshot, event: OpenCodeEvent): Snapshot = when (event) {
        is OpenCodeEvent.SessionCreated -> snapshot.withSession(event.session)
        is OpenCodeEvent.SessionUpdated -> snapshot.withSession(event.session)
        is OpenCodeEvent.SessionDeleted -> snapshot.copy(
            sessions = snapshot.sessions - event.session.id,
            statuses = snapshot.statuses - event.session.id,
        )
        is OpenCodeEvent.SessionRefreshRequested -> if (event.removed) {
            snapshot.copy(
                sessions = snapshot.sessions - event.sessionID,
                statuses = snapshot.statuses - event.sessionID,
            )
        } else {
            snapshot
        }
        is OpenCodeEvent.SessionStatusChanged -> snapshot.withStatus(event.sessionID, event.status)
        is OpenCodeEvent.SessionIdle -> snapshot.withStatus(event.sessionID, SessionStatus.Idle)
        is OpenCodeEvent.SessionError -> event.sessionID?.let { sessionId ->
            snapshot.withStatus(sessionId, SessionStatus.Idle)
        } ?: snapshot
        else -> snapshot
    }

    private fun Snapshot.withSession(session: Session): Snapshot =
        if (OfishSessionNames.isOfishTitle(session.title)) {
            this
        } else {
            upsert(WorkspaceSession(SessionId(session.id), workspace, session))
        }

    private fun Snapshot.upsert(session: WorkspaceSession): Snapshot = copy(
        sessions = sessions + (session.id.value to session),
    )

    private fun Snapshot.withStatus(sessionId: String, status: SessionStatus): Snapshot = copy(
        statuses = statuses + (sessionId to status),
    )
}
