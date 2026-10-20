package app.kasane.ipc

import android.content.Context
import org.json.JSONObject

/** The latest summary each app sent, read back by the Momentum screen. Stays on the device. */
object EcoMetrics {
    private val SOURCES = setOf("vow", "ren", "katachi")
    private const val MAX_LENGTH = 2000

    private fun prefs(context: Context) = context.getSharedPreferences("eco_metrics", Context.MODE_PRIVATE)

    fun save(context: Context, source: String, payload: String) {
        if (source !in SOURCES || payload.length > MAX_LENGTH) return
        if (runCatching { JSONObject(payload) }.isFailure) return
        prefs(context).edit().putString(source, payload).putLong("$source.at", System.currentTimeMillis()).apply()
    }

    fun read(context: Context, source: String): JSONObject? =
        prefs(context).getString(source, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
}
