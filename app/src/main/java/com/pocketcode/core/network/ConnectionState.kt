package com.pocketcode.core.network

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data class Error(val message: String?) : ConnectionState()

    val isConnected: Boolean get() = this is Connected
}
