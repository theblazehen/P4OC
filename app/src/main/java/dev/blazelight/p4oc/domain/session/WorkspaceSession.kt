package dev.blazelight.p4oc.domain.session

import dev.blazelight.p4oc.domain.model.Session
import dev.blazelight.p4oc.domain.workspace.Workspace

data class WorkspaceSession(
    val id: SessionId,
    val workspace: Workspace,
    val session: Session,
)
