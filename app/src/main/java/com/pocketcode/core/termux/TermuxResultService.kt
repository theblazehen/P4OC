package com.pocketcode.core.termux

import android.app.IntentService
import android.content.Intent
import android.util.Log
import com.pocketcode.core.termux.TermuxConstants.EXTRA_PLUGIN_RESULT_BUNDLE
import com.pocketcode.core.termux.TermuxConstants.EXTRA_PLUGIN_RESULT_BUNDLE_ERRMSG
import com.pocketcode.core.termux.TermuxConstants.EXTRA_PLUGIN_RESULT_BUNDLE_EXIT_CODE
import com.pocketcode.core.termux.TermuxConstants.EXTRA_PLUGIN_RESULT_BUNDLE_STDERR
import com.pocketcode.core.termux.TermuxConstants.EXTRA_PLUGIN_RESULT_BUNDLE_STDOUT
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Suppress("DEPRECATION")
class TermuxResultService : IntentService("TermuxResultService") {

    companion object {
        private const val TAG = "TermuxResultService"
        const val EXTRA_EXECUTION_ID = "execution_id"

        private val executionId = AtomicInteger(1000)
        private val callbacks = ConcurrentHashMap<Int, (TermuxCommandResult) -> Unit>()

        fun getNextExecutionId(): Int = executionId.incrementAndGet()

        fun registerCallback(id: Int, callback: (TermuxCommandResult) -> Unit) {
            callbacks[id] = callback
        }
    }

    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) {
            Log.w(TAG, "Received null intent")
            return
        }

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

        callbacks.remove(execId)?.invoke(result)
    }
}
