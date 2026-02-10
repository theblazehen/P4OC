package dev.blazelight.p4oc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.ui.navigation.NavGraph
import dev.blazelight.p4oc.ui.navigation.Screen
import dev.blazelight.p4oc.ui.tabs.MainTabScreen
import dev.blazelight.p4oc.ui.theme.PocketCodeTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    
    private val settingsDataStore: SettingsDataStore by inject()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            val themeMode by settingsDataStore.themeMode.collectAsStateWithLifecycle(initialValue = "system")
            val themeName by settingsDataStore.themeName.collectAsStateWithLifecycle(initialValue = SettingsDataStore.DEFAULT_THEME_NAME)
            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            
            PocketCodeTheme(themeName = themeName, darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Use NavGraph for initial Server/Setup screens,
                    // then MainTabScreen takes over after connection
                    val navController = rememberNavController()
                    NavGraph(
                        navController = navController,
                        startDestination = Screen.Server.route
                    )
                }
            }
        }
    }
}
