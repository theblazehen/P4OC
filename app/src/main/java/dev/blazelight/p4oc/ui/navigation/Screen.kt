package dev.blazelight.p4oc.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    data object Setup : Screen("setup")
    data object Server : Screen("server")
    data object Sessions : Screen("sessions")
    
    data object Chat : Screen("chat/{sessionId}?directory={directory}") {
        fun createRoute(sessionId: String, directory: String? = null) = 
            if (directory != null) "chat/$sessionId?directory=${Uri.encode(directory)}" else "chat/$sessionId"
        const val ARG_SESSION_ID = "sessionId"
        const val ARG_DIRECTORY = "directory"
    }
    
    data object Terminal : Screen("terminal/{ptyId}") {
        fun createRoute(ptyId: String) = "terminal/$ptyId"
        const val ARG_PTY_ID = "ptyId"
    }
    
    data object Files : Screen("files")
    
    data object FileViewer : Screen("files/view?path={path}") {
        fun createRoute(path: String) = "files/view?path=${Uri.encode(path)}"
        const val ARG_PATH = "path"
    }
    
    data object DiffViewer : Screen("diff?content={content}&fileName={fileName}") {
        fun createRoute(diffContent: String, fileName: String? = null) = 
            "diff?content=${Uri.encode(diffContent)}&fileName=${Uri.encode(fileName ?: "")}"
        const val ARG_CONTENT = "content"
        const val ARG_FILE_NAME = "fileName"
    }
    
    data object ProviderConfig : Screen("settings/providers")
    
    data object Git : Screen("git?projectId={projectId}") {
        fun createRoute(projectId: String? = null) = if (projectId != null) "git?projectId=$projectId" else "git"
        const val ARG_PROJECT_ID = "projectId"
    }
    
    data object SessionsFiltered : Screen("sessions?projectId={projectId}") {
        fun createRoute(projectId: String) = "sessions?projectId=$projectId"
        const val ARG_PROJECT_ID = "projectId"
    }
    
    data object Settings : Screen("settings")
    
    data object VisualSettings : Screen("settings/visual")
    
    data object ModelControls : Screen("settings/models")
    
    data object AgentsConfig : Screen("settings/agents")
    
    data object Skills : Screen("settings/skills")
    
    data object Projects : Screen("projects")
}
