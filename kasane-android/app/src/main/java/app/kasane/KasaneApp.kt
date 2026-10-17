package app.kasane

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import app.kasane.core.Alert
import app.kasane.reminders.AlarmScheduler

class KasaneApp : Application() {
    lateinit var store: Store
        private set

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        createReminderChannels()
        AlarmScheduler.rearm(this)
    }

    // A channel's sound and vibration are fixed once made, so each alert level has its own.
    private fun createReminderChannels() {
        val light = NotificationChannel(channelFor(Alert.LIGHT), getString(R.string.channel_reminders_light),
            NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 180)
        }
        val medium = NotificationChannel(channelFor(Alert.MEDIUM), getString(R.string.channel_reminders),
            NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
        }
        val strong = NotificationChannel(channelFor(Alert.STRONG), getString(R.string.channel_reminders_strong),
            NotificationManager.IMPORTANCE_HIGH).apply {
            val alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            setSound(alarm, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(light, medium, strong))
    }

    companion object {
        /** The medium channel keeps the original id, so settings made for it carry over. */
        fun channelFor(alert: Alert): String = when (alert) {
            Alert.LIGHT -> "reminders_light"
            Alert.MEDIUM -> "reminders"
            Alert.STRONG -> "reminders_strong"
        }
    }
}
