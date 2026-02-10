package dev.blazelight.p4oc.data.repository

import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.repository.EventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow


class EventRepositoryImpl constructor(
    private val connectionManager: ConnectionManager
) : EventRepository {

    override val events: Flow<OpenCodeEvent>
        get() = connectionManager.getEventSource()?.events ?: emptyFlow()

    override val connectionState: StateFlow<ConnectionState>
        get() = connectionManager.connectionState

    override fun connect() {
        connectionManager.getEventSource()?.connect()
    }

    override fun disconnect() {
        connectionManager.disconnect()
    }
}
