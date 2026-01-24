package dev.blazelight.p4oc.core.termux

object TermuxConstants {
    const val TERMUX_PACKAGE = "com.termux"
    const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"

    const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
    const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"

    const val EXTRA_PLUGIN_RESULT_BUNDLE = "com.termux.EXTRA_PLUGIN_RESULT_BUNDLE"
    const val EXTRA_PLUGIN_RESULT_BUNDLE_STDOUT = "stdout"
    const val EXTRA_PLUGIN_RESULT_BUNDLE_STDERR = "stderr"
    const val EXTRA_PLUGIN_RESULT_BUNDLE_EXIT_CODE = "exitCode"
    const val EXTRA_PLUGIN_RESULT_BUNDLE_ERR = "err"
    const val EXTRA_PLUGIN_RESULT_BUNDLE_ERRMSG = "errmsg"

    const val TERMUX_HOME = "/data/data/com.termux/files/home"
    const val TERMUX_BIN = "/data/data/com.termux/files/usr/bin"

    const val PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"
}

data class TermuxCommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int?,
    val error: String?
) {
    val isSuccess: Boolean get() = exitCode == 0 && error == null
}
