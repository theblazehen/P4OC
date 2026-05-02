package dev.blazelight.p4oc.domain.server

import dev.blazelight.p4oc.domain.model.OpenCodeEvent

data class ScopedEvent(
    val serverRef: ServerRef,
    val generation: ServerGeneration,
    val workspaceKey: WorkspaceKey,
    val event: OpenCodeEvent,
)
