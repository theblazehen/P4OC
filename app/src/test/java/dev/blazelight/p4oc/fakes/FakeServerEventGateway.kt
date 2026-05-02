package dev.blazelight.p4oc.fakes

import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.server.ScopedEvent
import dev.blazelight.p4oc.domain.server.ServerGeneration
import dev.blazelight.p4oc.domain.workspace.Workspace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map

class FakeServerEventGateway {
    private val events = MutableSharedFlow<OpenCodeEvent>(extraBufferCapacity = 64)

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

    suspend fun emit(event: OpenCodeEvent) {
        events.emit(event)
    }

    fun tryEmit(event: OpenCodeEvent): Boolean = events.tryEmit(event)
}
