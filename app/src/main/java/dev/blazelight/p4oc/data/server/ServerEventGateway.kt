package dev.blazelight.p4oc.data.server

import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.server.ScopedEvent
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.workspace.Workspace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ServerEventGateway(
    private val events: Flow<OpenCodeEvent>,
) {
    fun scopedEvents(
        workspace: Workspace,
        generation: ServerGeneration,
    ): Flow<ScopedEvent> = events.map { event ->
        ScopedEvent(
            serverRef = workspace.server,
            generation = generation,
            workspaceKey = workspace.key,
            event = event,
        )
    }
}
