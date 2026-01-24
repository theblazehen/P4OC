package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DirectoryManager @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) {
    
    private val _currentDirectory = MutableStateFlow<String?>(null)
    val currentDirectory: StateFlow<String?> = _currentDirectory.asStateFlow()
    
    private var onDirectoryChangedListener: (() -> Unit)? = null
    
    fun setOnDirectoryChangedListener(listener: (() -> Unit)?) {
        onDirectoryChangedListener = listener
    }
    
    /**
     * Load persisted directory from DataStore.
     * Call this on app startup before loading sessions.
     */
    suspend fun loadPersistedDirectory(): String? {
        val worktree = settingsDataStore.getProjectWorktree()
        if (worktree != null) {
            _currentDirectory.value = worktree
        }
        return worktree
    }
    
    /**
     * Set directory and persist to DataStore.
     */
    suspend fun setDirectoryAndPersist(directory: String?) {
        val newDir = directory?.takeIf { it.isNotBlank() }
        if (_currentDirectory.value != newDir) {
            _currentDirectory.value = newDir
            settingsDataStore.setProjectWorktree(newDir)
            onDirectoryChangedListener?.invoke()
        }
    }
    
    /**
     * Set directory without persisting (for temporary directory switches).
     */
    fun setDirectory(directory: String?) {
        val newDir = directory?.takeIf { it.isNotBlank() }
        if (_currentDirectory.value != newDir) {
            _currentDirectory.value = newDir
            onDirectoryChangedListener?.invoke()
        }
    }
    
    fun getDirectory(): String? = _currentDirectory.value
    
    suspend fun <T> withDirectory(directory: String?, block: suspend () -> T): T {
        val previousDirectory = _currentDirectory.value
        try {
            _currentDirectory.value = directory
            return block()
        } finally {
            _currentDirectory.value = previousDirectory
        }
    }
    
    suspend fun clearDirectory() {
        if (_currentDirectory.value != null) {
            _currentDirectory.value = null
            settingsDataStore.setProjectWorktree(null)
            onDirectoryChangedListener?.invoke()
        }
    }
}
