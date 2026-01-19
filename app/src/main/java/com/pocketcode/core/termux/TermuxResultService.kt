package com.pocketcode.core.termux

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.JobIntentService
import com.pocketcode.core.termux.TermuxConstants.EXTRA_PLUGIN_RESULT_BUNDLE
import com.pocketcode.core.termux.TermuxConstants.EXTRA_PLUGIN_RESULT_BUNDLE_ERRMSG
import com.pocketcode.core.termux.TermuxConstants.EXTRA_PLUGIN_RESULT_BUNDLE_EXIT_CODE
import com.pocketcode.core.termux.TermuxConstants.EXTRA_PLUGIN_RESULT_BUNDLE_STDERR
import com.pocketcode.core.termux.TermuxConstants.EXTRA_PLUGIN_RESULT_BUNDLE_STDOUT
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class TermuxResultService : JobIntentService() {

    companion object {
        private const val TAG = "TermuxResultService"
        private const val JOB_ID = 1001
        const val EXTRA_EXECUTION_ID = "execution_id"

        private val executionId = AtomicInteger(1000)
        private val callbacks = ConcurrentHashMap<Int, (TermuxCommandResult) -> Unit>()

        fun getNextExecutionId(): Int = executionId.incrementAndGet()

        fun registerCallback(id: Int, callback: (TermuxCommandResult) -> Unit) {
            callbacks[id] = callback
        }

        fun enqueueWork(context: Context, intent: Intent) {
            enqueueWork(context, TermuxResultService::class.java, JOB_ID, intent)
        }
    }

    override fun onHandleWork(intent: Intent) {
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
