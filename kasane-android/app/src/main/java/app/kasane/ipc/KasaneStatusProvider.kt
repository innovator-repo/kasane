package app.kasane.ipc

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import app.kasane.core.NextUp
import app.kasane.core.NextUpSummary
import app.kasane.core.Kind
import app.kasane.core.Phase
import app.kasane.store
import java.time.LocalDate
import java.time.LocalTime

/** The status row's shape, kept apart from the provider so it can be tested. */
object KasaneStatusRow {
    val COLUMNS = arrayOf("block", "rulesKept", "rulesTotal", "nextTitle", "nextAtMinute", "openCount", "doneCount")

    fun values(summary: NextUpSummary, block: String, rulesKept: Int, rulesTotal: Int): Array<Any> =
        arrayOf(block, rulesKept, rulesTotal, summary.next?.title ?: "", summary.nextAtMinute, summary.openCount, summary.doneCount)
}

/**
 * `content://app.kasane.provider/status`, read-only, one row: the open block, today's rules kept of
 * total, the next task (`nextTitle`, `nextAtMinute` or -1), and `openCount` / `doneCount` for today.
 * Only the Lume apps may read it (LumeTrust).
 */
class KasaneStatusProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? {
        if (!LumeTrust.isPeerCaller(callingPackage)) return null
        if (uri.path != "/status") return null
        val context = context ?: return null
        val journal = context.store.journal.value
        val today = LocalDate.now()
        val rules = journal.items.filter { it.kind == Kind.RULE && it.dueOn(today) }
        val kept = rules.count { it.finishedFor(journal.entry(today, it.id)) }
        val block = if (Phase.surfaceOf(LocalTime.now(), SleepLockStore.active(context)) == Phase.SANCTUARY) "SLEEP_LOCK"
        else Phase.of(LocalTime.now()).block.name
        return MatrixCursor(KasaneStatusRow.COLUMNS).apply {
            addRow(KasaneStatusRow.values(NextUp.summary(journal.tasks, today), block, kept, rules.size))
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.app.kasane.status"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
}
