package com.pocketcode.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    data object Setup : Screen("setup")
    data object Server : Screen("server")
    data object Sessions : Screen("sessions")
    
    data object Chat : Screen("chat/{sessionId}") {
        fun createRoute(sessionId: String) = "chat/$sessionId"
        const val ARG_SESSION_ID = "sessionId"
    }
    
    data object Terminal : Screen("terminal")
    
    data object Files : Screen("files")
    
    data object FileViewer : Screen("files/view?path={path}") {
        fun createRoute(path: String) = "files/view?path=${Uri.encode(path)}"
        const val ARG_PATH = "path"
    }
    
    data object Settings : Screen("settings")
}
