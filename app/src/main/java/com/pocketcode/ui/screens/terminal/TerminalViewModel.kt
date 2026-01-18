package com.pocketcode.ui.screens.terminal

import androidx.lifecycle.ViewModel
import com.pocketcode.core.termux.TermuxBridge
import com.pocketcode.core.termux.TermuxConstants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val termuxBridge: TermuxBridge
) : ViewModel() {

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    fun updateInput(input: String) {
        _uiState.update { it.copy(input = input) }
    }

    fun executeCommand() {
        val command = _uiState.value.input.trim()
        if (command.isBlank()) return

        _uiState.update { state ->
            state.copy(
                input = "",
                lines = state.lines + "$ $command"
            )
        }

        termuxBridge.runCommand(
            executable = "${TermuxConstants.TERMUX_BIN}/bash",
            arguments = listOf("-c", command),
            background = true,
            onResult = { result ->
                _uiState.update { state ->
                    val newLines = mutableListOf<String>()
                    if (result.stdout.isNotBlank()) {
                        newLines.addAll(result.stdout.lines())
                    }
                    if (result.stderr.isNotBlank()) {
                        newLines.addAll(result.stderr.lines().map { "stderr: $it" })
                    }
                    if (result.error != null) {
                        newLines.add("error: ${result.error}")
                    }
                    state.copy(lines = state.lines + newLines)
                }
            }
        )
    }

    fun clearTerminal() {
        _uiState.update { it.copy(lines = emptyList()) }
    }

    fun openTermux() {
        termuxBridge.openTermux()
    }
}

data class TerminalUiState(
    val input: String = "",
    val lines: List<String> = listOf("Welcome to Pocket Code Terminal", "Type commands to execute in Termux", "")
)
