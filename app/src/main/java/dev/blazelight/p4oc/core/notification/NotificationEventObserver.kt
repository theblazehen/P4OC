package dev.blazelight.p4oc.core.notification

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch


class NotificationEventObserver constructor(
    private val connectionManager: ConnectionManager,
    private val notificationHelper: NotificationHelper
) : DefaultLifecycleObserver {
    
    companion object {
        private const val TAG = "NotificationEventObserver"
    }
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isInForeground = true
    
    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        observeEvents()
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
        when (event) {
            is OpenCodeEvent.PermissionRequested -> {
                Log.d(TAG, "Permission requested in background: ${event.permission.title}")
                notificationHelper.showPermissionNotification(
                    sessionId = event.permission.sessionID,
                    title = event.permission.title
                )
            }
            is OpenCodeEvent.QuestionAsked -> {
                val firstQuestion = event.request.questions.firstOrNull()?.question ?: "AI has a question"
                Log.d(TAG, "Question asked in background: $firstQuestion")
                notificationHelper.showQuestionNotification(
                    sessionId = event.request.sessionID,
                    question = firstQuestion
                )
            }
            else -> {}
        }
    }
}
