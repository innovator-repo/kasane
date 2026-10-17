package app.kasane

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate

/**
 * Katachi reports how many of the day's puzzles are solved, and the
 * "Pattern puzzles" count follows it. Only that item can be set this way,
 * only for today or yesterday, and a higher count logged by hand is kept.
 */
class PuzzleCountReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION || intent.getStringExtra("item") != ITEM) return
        val date = runCatching { LocalDate.parse(intent.getStringExtra("date")) }.getOrNull() ?: return
        val today = LocalDate.now()
        if (date != today && date != today.minusDays(1)) return
        val count = intent.getIntExtra("count", -1).takeIf { it in 0..MAX } ?: return
        val store = context.store
        val entry = store.journal.value.entry(date, ITEM)
        if (count > entry.count) store.setEntry(date, ITEM, entry.copy(count = count))
    }

    companion object {
        const val ACTION = "app.kasane.action.LOG_COUNT"
        private const val ITEM = "puzzles"
        private const val MAX = 50
    }
}
