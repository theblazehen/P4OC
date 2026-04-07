package dev.blazelight.p4oc

import android.app.Application
import dev.blazelight.p4oc.core.notification.NotificationEventObserver
import dev.blazelight.p4oc.di.allModules
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class PocketCodeApp : Application() {
    
    private val notificationEventObserver: NotificationEventObserver by inject()
    @Volatile
    private var notificationsStarted = false
    
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidLogger()
            androidContext(this@PocketCodeApp)
            modules(allModules)
        }

        // Lazy init: start notifications only when app enters foreground the first time
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (!notificationsStarted) {
                    notificationsStarted = true
                    notificationEventObserver.start()
                }
            }
        })
    }
}
