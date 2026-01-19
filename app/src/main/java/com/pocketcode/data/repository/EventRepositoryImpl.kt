package com.pocketcode.data.repository

import com.pocketcode.core.network.ConnectionManager
import com.pocketcode.core.network.ConnectionState
import com.pocketcode.domain.model.OpenCodeEvent
import com.pocketcode.domain.repository.EventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepositoryImpl @Inject constructor(
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
