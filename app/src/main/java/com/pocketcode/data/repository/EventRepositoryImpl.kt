package com.pocketcode.data.repository

import com.pocketcode.core.network.ConnectionState
import com.pocketcode.core.network.OpenCodeEventSource
import com.pocketcode.domain.model.OpenCodeEvent
import com.pocketcode.domain.repository.EventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepositoryImpl @Inject constructor(
    private val eventSource: OpenCodeEventSource
) : EventRepository {

    override val events: Flow<OpenCodeEvent>
        get() = eventSource.events

    override val connectionState: StateFlow<ConnectionState>
        get() = eventSource.connectionState

    override fun connect() {
        eventSource.connect()
    }

    override fun disconnect() {
        eventSource.disconnect()
    }
}
