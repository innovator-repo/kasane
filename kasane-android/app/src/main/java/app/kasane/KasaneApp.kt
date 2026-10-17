package app.kasane

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import app.kasane.reminders.AlarmScheduler

class KasaneApp : Application() {
    lateinit var store: Store
        private set

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        val channel = NotificationChannel(CHANNEL_REMINDERS, getString(R.string.channel_reminders),
            NotificationManager.IMPORTANCE_HIGH)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        AlarmScheduler.rearm(this)
    }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
    }
}
