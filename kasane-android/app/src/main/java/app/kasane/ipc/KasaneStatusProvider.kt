package app.kasane.ipc

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import app.kasane.LumeCore
import app.kasane.core.Analysis
import app.kasane.core.Kind
import app.kasane.core.Phase
import app.kasane.store
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * `content://app.kasane.provider/status`, read-only, one row:
 * momentum 0..100, the open block, and today's rules kept of total.
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
        val momentum = Analysis(journal, today, LumeCore.active).momentum().map { it.value }
            .average().takeIf { !it.isNaN() }?.times(100)?.roundToInt() ?: 0
        val rules = journal.items.filter { it.kind == Kind.RULE && it.dueOn(today) }
        val kept = rules.count { it.finishedFor(journal.entry(today, it.id)) }
        val block = if (Phase.surfaceOf(LocalTime.now(), SleepLockStore.active(context)) == Phase.SANCTUARY) "SLEEP_LOCK"
        else Phase.of(LocalTime.now()).block.name
        return MatrixCursor(arrayOf("momentum", "block", "rulesKept", "rulesTotal")).apply {
            addRow(arrayOf<Any>(momentum.coerceIn(0, 100), block, kept, rules.size))
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.app.kasane.status"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
}
