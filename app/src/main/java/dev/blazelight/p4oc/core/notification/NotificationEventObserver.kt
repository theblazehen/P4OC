package dev.blazelight.p4oc.core.notification

import dev.blazelight.p4oc.core.log.AppLog
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.blazelight.p4oc.core.datastore.NotificationSettings
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.core.haptic.HapticFeedback
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.model.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch


/**
 * Best-effort background notifications for permission/question events.
 *
 * Notifications fire only while the app process is alive in the background.
 * There is no foreground service, so the OS (Doze, App Standby, OEM killers)
 * may kill the process within minutes of backgrounding. Long-lived background
 * notifications are NOT guaranteed.
 */
class NotificationEventObserver constructor(
    private val connectionManager: ConnectionManager,
    private val notificationHelper: NotificationHelper,
    private val settingsDataStore: SettingsDataStore,
    private val hapticFeedback: HapticFeedback,
) : DefaultLifecycleObserver {
    
    companion object {
        private const val TAG = "NotificationEventObserver"
    }
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isInForeground = true

    @Volatile
    private var cachedSettings = NotificationSettings()

    private val busySessions = mutableSetOf<String>()
    
    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        observeEvents()
        observeSettings()
    }
    
    fun stop() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        scope.cancel()
    }
    
    override fun onStart(owner: LifecycleOwner) {
        isInForeground = true
        notificationHelper.clearNotifications()
    }
    
    override fun onStop(owner: LifecycleOwner) {
        isInForeground = false
    }

    private fun observeSettings() {
        scope.launch {
            settingsDataStore.notificationSettings.collect { settings ->
                cachedSettings = settings
            }
        }
    }
    
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeEvents() {
        scope.launch {
            connectionManager.connection
                .filterNotNull()
                .flatMapLatest { it.eventSource.events }
                .collect { event ->
                    if (!isInForeground) {
                        handleEventInBackground(event)
                    }
                }
        }
    }
    
    private fun handleEventInBackground(event: OpenCodeEvent) {
        if (!cachedSettings.enabled) return

        when (event) {
            is OpenCodeEvent.PermissionRequested -> {
                if (!cachedSettings.permissionRequests) return
                AppLog.d(TAG, "Permission requested in background: ${event.permission.title}")
                notificationHelper.showPermissionNotification(
                    sessionId = event.permission.sessionID,
                    title = event.permission.title
                )
            }
            is OpenCodeEvent.QuestionAsked -> {
                if (!cachedSettings.questions) return
                val firstQuestion = event.request.questions.firstOrNull()?.question ?: "AI has a question"
                AppLog.d(TAG, "Question asked in background: $firstQuestion")
                notificationHelper.showQuestionNotification(
                    sessionId = event.request.sessionID,
                    question = firstQuestion
                )
            }
            is OpenCodeEvent.SessionStatusChanged -> {
                val isBusy = event.status is SessionStatus.Busy || event.status is SessionStatus.Retry
                if (isBusy) {
                    busySessions.add(event.sessionID)
                } else if (busySessions.remove(event.sessionID)) {
                    showCompletionFeedback(event.sessionID)
                }
            }
            is OpenCodeEvent.SessionIdle -> {
                if (busySessions.remove(event.sessionID)) {
                    showCompletionFeedback(event.sessionID)
                }
            }
            else -> {}
        }
    }

    private fun showCompletionFeedback(sessionId: String) {
        hapticFeedback.vibrate(cachedSettings.vibrationPattern)
        if (cachedSettings.notifyOnCompletion) {
            notificationHelper.showCompletionNotification(
                sessionId = sessionId,
                sessionTitle = null,
            )
        }
    }
}
