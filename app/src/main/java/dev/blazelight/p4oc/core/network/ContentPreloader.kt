package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.core.log.AppLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ContentPreloader - Predictive content loading for instant user experience.
 * Preloads session data and messages before user opens them.
 */
class ContentPreloader(
    private val scope: CoroutineScope,
    private val connectionManager: ConnectionManager
) {
    companion object {
        private const val TAG = "ContentPreloader"
        private const val PRELOAD_AHEAD_COUNT = 3 // Preload next N sessions
    }

    private val preloadedSessions = mutableMapOf<String, PreloadedSession>()
    private val preloadMutex = kotlinx.coroutines.sync.Mutex()

    data class PreloadedSession(
        val sessionId: String,
        val title: String,
        val messageCount: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Trigger preload for sessions adjacent to currently viewed one.
     * Call this when user opens a session.
     */
    fun preloadAdjacentSessions(
        currentSessionId: String,
        recentSessionIds: List<String>
    ) {
        val currentIndex = recentSessionIds.indexOf(currentSessionId)
        if (currentIndex == -1) return

        // Identify sessions to preload (next 3)
        val toPreload = recentSessionIds
            .drop(currentIndex + 1)
            .take(PRELOAD_AHEAD_COUNT)
            .filter { it !in preloadedSessions }

        if (toPreload.isEmpty()) return

        scope.launch(Dispatchers.IO) {
            val api = connectionManager.getApi() ?: return@launch

            toPreload.forEach { sessionId ->
                launch {
                    try {
                        // Quick metadata fetch (lightweight)
                        val result = safeApiCall {
                            api.getSession(sessionId, null)
                        }

                        if (result is ApiResult.Success) {
                            preloadMutex.withLock {
                                preloadedSessions[sessionId] = PreloadedSession(
                                    sessionId = sessionId,
                                    title = result.data.title,
                                    messageCount = 0 // Unknown until messages are fetched
                                )
                            }
                            AppLog.d(TAG, "Preloaded session: $sessionId")
                        }
                    } catch (e: Exception) {
                        AppLog.w(TAG, "Failed to preload session: $sessionId", e)
                    }
                }
            }
        }
    }

    /**
     * Get preloaded session metadata if available.
     */
    suspend fun getPreloadedSession(sessionId: String): PreloadedSession? {
        return preloadMutex.withLock {
            preloadedSessions[sessionId]
        }
    }

    /**
     * Warm up connection with lightweight ping.
     */
    fun warmConnection() {
        scope.launch(Dispatchers.IO) {
            val api = connectionManager.getApi() ?: return@launch
            try {
                safeApiCall { api.health() }
                AppLog.d(TAG, "Connection warmed")
            } catch (_: Exception) { }
        }
    }

    /**
     * Clear stale preloaded data.
     */
    suspend fun cleanup(maxAgeMs: Long = 600000) { // 10 minutes
        val cutoff = System.currentTimeMillis() - maxAgeMs
        preloadMutex.withLock {
            preloadedSessions.entries.removeIf { it.value.timestamp < cutoff }
        }
    }

    /**
     * Clear all preloaded data.
     */
    suspend fun clear() {
        preloadMutex.withLock {
            preloadedSessions.clear()
        }
    }
}
