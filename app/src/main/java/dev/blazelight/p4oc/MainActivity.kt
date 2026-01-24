package dev.blazelight.p4oc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.ui.navigation.NavGraph
import dev.blazelight.p4oc.ui.navigation.Screen
import dev.blazelight.p4oc.ui.theme.PocketCodeTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var settingsDataStore: SettingsDataStore
    
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            val themeMode by settingsDataStore.themeMode.collectAsState(initial = "system")
            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            
            PocketCodeTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
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
