package app.kasane.ipc

import android.content.Context
import android.database.Cursor
import android.net.Uri

/**
 * A companion app is usable only if it is installed AND its status provider answers.
 * Any exception or null result means "absent": nothing is drawn for it and nothing breaks.
 */
object Companion {
    fun statusUri(pkg: String): Uri = Uri.parse("content://$pkg.provider/status")

    /** One status row as column to value (Long, Double or String); null when the app is absent. */
    fun status(context: Context, pkg: String): Map<String, Any?>? {
        if (!Ecosystem.installed(context, pkg)) return null
        return try {
            context.contentResolver.query(statusUri(pkg), null, null, null, null)?.use { if (it.moveToFirst()) it.toMap() else null }
        } catch (_: Exception) {
            null
        }
    }

    fun available(context: Context, pkg: String): Boolean = status(context, pkg) != null

    private fun Cursor.toMap(): Map<String, Any?> = buildMap {
        for (i in 0 until columnCount) when (getType(i)) {
            Cursor.FIELD_TYPE_INTEGER -> put(getColumnName(i), getLong(i))
            Cursor.FIELD_TYPE_FLOAT -> put(getColumnName(i), getDouble(i))
            Cursor.FIELD_TYPE_STRING -> put(getColumnName(i), getString(i))
            else -> {}
        }
    }
}

/** The Lume apps the universal widgets can show. */
enum class LumeApp(val pkg: String) {
    KASANE("app.kasane"), SIGIL(Ecosystem.SIGIL), VOW(Ecosystem.VOW), REN(Ecosystem.REN), KATACHI(Ecosystem.KATACHI);

    companion object {
        val companions = listOf(SIGIL, VOW, REN, KATACHI)
    }
}

/**
 * What one app says for the universal widgets, read from its status row. Every field is null when
 * the app did not give that column, and a fact with nothing to say is not made at all.
 */
data class AppFact(
    val app: LumeApp,
    val count: Int? = null,
    val total: Int? = null,
    val flag: Boolean? = null,
    val text: String? = null,
    val minute: Int? = null,
) {
    /** The small live number or word on a dock tile. */
    fun badge(): String? = when (app) {
        LumeApp.KASANE, LumeApp.REN, LumeApp.KATACHI, LumeApp.SIGIL -> count?.takeIf { it > 0 }?.toString()
        LumeApp.VOW -> flag?.let { if (it) "on" else "off" }
    }

    companion object {
        private fun Map<String, Any?>.int(name: String): Int? = (this[name] as? Number)?.toInt()
        private fun Map<String, Any?>.str(name: String): String? = (this[name] as? String)?.takeIf { it.isNotBlank() }

        /** Maps a provider row to a fact, or null when the columns that matter are missing. */
        fun from(app: LumeApp, row: Map<String, Any?>?): AppFact? {
            if (row == null) return null
            return when (app) {
                LumeApp.KASANE -> row.int("openCount")?.let {
                    AppFact(app, count = it, total = row.int("doneCount")?.plus(it), text = row.str("nextTitle"),
                        minute = row.int("nextAtMinute")?.takeIf { m -> m >= 0 })
                }
                LumeApp.REN -> row.int("due")?.let { AppFact(app, count = it, total = row.int("newToday")) }
                LumeApp.KATACHI -> {
                    val solved = row.int("solved")
                    val target = row.int("target")
                    if (solved == null || target == null) null
                    else AppFact(app, count = (target - solved).coerceAtLeast(0), total = target)
                }
                LumeApp.VOW -> row.int("focusOn")?.let { AppFact(app, flag = it != 0, text = row.str("profileName")) }
                LumeApp.SIGIL -> {
                    val notes = row.int("noteCount")
                    val title = row.str("lastTitle")
                    if (notes == null && title == null) null else AppFact(app, count = notes, text = title)
                }
            }
        }
    }
}

/** Reads the installed-and-answering companions, in a fixed order. */
fun readFacts(context: Context): List<AppFact> =
    LumeApp.companions.mapNotNull { AppFact.from(it, Companion.status(context, it.pkg)) }
