package dev.blazelight.p4oc.core.termux

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import dev.blazelight.p4oc.core.termux.TermuxConstants.ACTION_RUN_COMMAND
import dev.blazelight.p4oc.core.termux.TermuxConstants.EXTRA_ARGUMENTS
import dev.blazelight.p4oc.core.termux.TermuxConstants.EXTRA_BACKGROUND
import dev.blazelight.p4oc.core.termux.TermuxConstants.EXTRA_COMMAND_PATH
import dev.blazelight.p4oc.core.termux.TermuxConstants.EXTRA_PENDING_INTENT
import dev.blazelight.p4oc.core.termux.TermuxConstants.EXTRA_SESSION_ACTION
import dev.blazelight.p4oc.core.termux.TermuxConstants.EXTRA_WORKDIR
import dev.blazelight.p4oc.core.termux.TermuxConstants.PERMISSION_RUN_COMMAND
import dev.blazelight.p4oc.core.termux.TermuxConstants.RUN_COMMAND_SERVICE
import dev.blazelight.p4oc.core.termux.TermuxConstants.TERMUX_BIN
import dev.blazelight.p4oc.core.termux.TermuxConstants.TERMUX_HOME
import dev.blazelight.p4oc.core.termux.TermuxConstants.TERMUX_PACKAGE
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TermuxBridge @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TermuxBridge"
        private const val DEFAULT_PORT = 4096
    }

    private val _status = MutableStateFlow<TermuxStatus>(TermuxStatus.Unknown)
    val status: StateFlow<TermuxStatus> = _status.asStateFlow()

    sealed class TermuxStatus {
        data object Unknown : TermuxStatus()
        data object NotInstalled : TermuxStatus()
        data object Installed : TermuxStatus()
        data object SetupRequired : TermuxStatus()
        data object OpenCodeNotInstalled : TermuxStatus()
        data object Ready : TermuxStatus()
        data class ServerRunning(val port: Int) : TermuxStatus()
    }

    fun isTermuxInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun hasRunCommandPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            PERMISSION_RUN_COMMAND
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun runCommand(
        executable: String,
        arguments: List<String> = emptyList(),
        workdir: String = TERMUX_HOME,
        background: Boolean = true,
        onResult: ((TermuxCommandResult) -> Unit)? = null
    ): Boolean {
        if (!isTermuxInstalled()) {
            Log.w(TAG, "Termux not installed")
            return false
        }

        val intent = Intent().apply {
            setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            action = ACTION_RUN_COMMAND
            putExtra(EXTRA_COMMAND_PATH, executable)
            putExtra(EXTRA_ARGUMENTS, arguments.toTypedArray())
            putExtra(EXTRA_WORKDIR, workdir)
            putExtra(EXTRA_BACKGROUND, background)

            if (!background) {
                putExtra(EXTRA_SESSION_ACTION, "0")
            }

            onResult?.let { callback ->
                val resultIntent = Intent(context, TermuxResultService::class.java)
                val executionId = TermuxResultService.getNextExecutionId()
                resultIntent.putExtra(TermuxResultService.EXTRA_EXECUTION_ID, executionId)

                val pendingIntent = PendingIntent.getService(
                    context,
                    executionId,
                    resultIntent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE
                )
                putExtra(EXTRA_PENDING_INTENT, pendingIntent)
                TermuxResultService.registerCallback(executionId, callback)
            }
        }

        return try {
            context.startService(intent)
            Log.d(TAG, "Started Termux command: $executable ${arguments.joinToString(" ")}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to run command", e)
            false
        }
    }

    fun startOpenCodeServer(
        port: Int = DEFAULT_PORT,
        projectDir: String? = null
    ): Boolean {
        val args = mutableListOf("serve", "--port", port.toString())
        projectDir?.let { args.addAll(listOf("--cwd", it)) }

        return runCommand(
            executable = "$TERMUX_BIN/opencode",
            arguments = args,
            background = false
        )
    }

    fun checkOpenCodeInstalled(callback: (Boolean) -> Unit) {
        runCommand(
            executable = "$TERMUX_BIN/which",
            arguments = listOf("opencode"),
            background = true,
            onResult = { result ->
                callback(result.exitCode == 0)
            }
        )
    }

    fun installOpenCode(): Boolean {
        val script = """
            echo "=== Installing OpenCode ==="
            pkg update -y
            pkg install -y nodejs
            npm install -g opencode
            echo ""
            echo "=== Installation Complete ==="
            opencode --version
            echo ""
            echo "Press Enter to close..."
            read
        """.trimIndent()

        return runCommand(
            executable = "$TERMUX_BIN/bash",
            arguments = listOf("-c", script),
            background = false
        )
    }

    fun openTermuxSetup(): Boolean {
        if (!isTermuxInstalled()) {
            return false
        }
        
        if (!hasRunCommandPermission()) {
            return openTermux()
        }
        
        val script = """
            echo "=== Pocket Code Setup ==="
            echo ""
            echo "Enabling external app access..."
            mkdir -p ~/.termux
            echo "allow-external-apps=true" >> ~/.termux/termux.properties
            echo ""
            echo "Done! Please restart Termux and Pocket Code."
            echo ""
            echo "Press Enter to close..."
            read
        """.trimIndent()

        return runCommand(
            executable = "$TERMUX_BIN/bash",
            arguments = listOf("-c", script),
            background = false
        )
    }

    suspend fun checkStatus(): TermuxStatus = withContext(Dispatchers.IO) {
        when {
            !isTermuxInstalled() -> TermuxStatus.NotInstalled
            !hasRunCommandPermission() -> TermuxStatus.SetupRequired
            else -> {
                var openCodeInstalled = false
                val latch = CountDownLatch(1)
                checkOpenCodeInstalled { installed ->
                    openCodeInstalled = installed
                    latch.countDown()
                }
                latch.await(5, TimeUnit.SECONDS)
                if (openCodeInstalled) TermuxStatus.Ready
                else TermuxStatus.OpenCodeNotInstalled
            }
        }.also { _status.value = it }
    }

    fun openTermux(): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                Log.w(TAG, "Could not get launch intent for Termux")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Termux", e)
            false
        }
    }
}
