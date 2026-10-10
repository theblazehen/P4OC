package dev.blazelight.p4oc.domain.server

import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.workspace.Workspace

data class ScopedEvent(
    val serverRef: ServerRef,
    val generation: ServerGeneration,
    val workspaceKey: WorkspaceKey,
    val event: OpenCodeEvent,
)

/** Catalog changes can originate at a location or at the server; global tabs observe both. */
fun ScopedEvent.affectsCatalogIn(workspace: Workspace, activeGeneration: ServerGeneration): Boolean =
    serverRef == workspace.server && generation == activeGeneration &&
        (workspaceKey == workspace.key || workspaceKey == WorkspaceKey.Global || workspace.key == WorkspaceKey.Global)
