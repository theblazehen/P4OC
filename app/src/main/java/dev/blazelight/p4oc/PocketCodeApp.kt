package dev.blazelight.p4oc

import android.app.Application
import dev.blazelight.p4oc.core.notification.NotificationEventObserver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PocketCodeApp : Application() {
    
    @Inject
    lateinit var notificationEventObserver: NotificationEventObserver
    
    override fun onCreate() {
        super.onCreate()
        notificationEventObserver.start()
    }
}
