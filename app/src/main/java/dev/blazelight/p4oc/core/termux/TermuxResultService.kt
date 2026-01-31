package dev.blazelight.p4oc.core.termux

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import dev.blazelight.p4oc.core.termux.TermuxConstants.EXTRA_PLUGIN_RESULT_BUNDLE
import dev.blazelight.p4oc.core.termux.TermuxConstants.EXTRA_PLUGIN_RESULT_BUNDLE_ERRMSG
import dev.blazelight.p4oc.core.termux.TermuxConstants.EXTRA_PLUGIN_RESULT_BUNDLE_EXIT_CODE
import dev.blazelight.p4oc.core.termux.TermuxConstants.EXTRA_PLUGIN_RESULT_BUNDLE_STDERR
import dev.blazelight.p4oc.core.termux.TermuxConstants.EXTRA_PLUGIN_RESULT_BUNDLE_STDOUT
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class TermuxResultService : Service() {

    companion object {
        private const val TAG = "TermuxResultService"
        const val EXTRA_EXECUTION_ID = "execution_id"
        private const val CALLBACK_TIMEOUT_MS = 60_000L

        private val executionId = AtomicInteger(1000)
        private val callbacks = ConcurrentHashMap<Int, (TermuxCommandResult) -> Unit>()
        private val callbackTimestamps = ConcurrentHashMap<Int, Long>()
        private val executor = Executors.newSingleThreadExecutor()
        private val mainHandler = Handler(Looper.getMainLooper())

        fun getNextExecutionId(): Int = executionId.incrementAndGet()

        fun registerCallback(id: Int, callback: (TermuxCommandResult) -> Unit) {
            callbacks[id] = callback
            callbackTimestamps[id] = System.currentTimeMillis()
            cleanupStaleCallbacks()
        }
        
        private fun cleanupStaleCallbacks() {
            val now = System.currentTimeMillis()
            val staleIds = callbackTimestamps.entries
                .filter { now - it.value > CALLBACK_TIMEOUT_MS }
                .map { it.key }
            staleIds.forEach { id ->
                callbacks.remove(id)
                callbackTimestamps.remove(id)
                Log.w(TAG, "Cleaned up stale callback for execution $id")
            }
        }

        fun startService(context: Context, intent: Intent) {
            intent.setClass(context, TermuxResultService::class.java)
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            executor.execute {
                handleWork(intent)
                stopSelfResult(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun handleWork(intent: Intent) {
        val resultBundle = intent.getBundleExtra(EXTRA_PLUGIN_RESULT_BUNDLE)
        val execId = intent.getIntExtra(EXTRA_EXECUTION_ID, 0)

        Log.d(TAG, "Received result for execution $execId")

        val result = if (resultBundle != null) {
            TermuxCommandResult(
                stdout = resultBundle.getString(EXTRA_PLUGIN_RESULT_BUNDLE_STDOUT, ""),
                stderr = resultBundle.getString(EXTRA_PLUGIN_RESULT_BUNDLE_STDERR, ""),
                exitCode = resultBundle.getInt(EXTRA_PLUGIN_RESULT_BUNDLE_EXIT_CODE),
                error = resultBundle.getString(EXTRA_PLUGIN_RESULT_BUNDLE_ERRMSG)
            )
        } else {
            TermuxCommandResult("", "", null, "No result bundle")
        }

        val callback = callbacks.remove(execId)
        callbackTimestamps.remove(execId)
        if (callback != null) {
            mainHandler.post { callback.invoke(result) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }
}
