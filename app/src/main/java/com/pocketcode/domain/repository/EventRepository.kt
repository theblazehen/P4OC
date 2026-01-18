package com.pocketcode.domain.repository

import com.pocketcode.core.network.ConnectionState
import com.pocketcode.domain.model.OpenCodeEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface EventRepository {
    val events: Flow<OpenCodeEvent>
    val connectionState: StateFlow<ConnectionState>
    fun connect()
    fun disconnect()
}
