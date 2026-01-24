package dev.blazelight.p4oc.domain.repository

import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface EventRepository {
    val events: Flow<OpenCodeEvent>
    val connectionState: StateFlow<ConnectionState>
    fun connect()
    fun disconnect()
}
