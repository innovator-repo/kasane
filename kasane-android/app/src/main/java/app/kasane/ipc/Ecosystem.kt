package app.kasane.ipc

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

/** What Ren reports about its review queue. */
data class RenStatus(val due: Int, val retention: Int, val streak: Int, val doneToday: Boolean)

/** What Vow reports about reclaimed time and its rules. */
data class VowStatus(val reclaimedMinutes: Int, val rulesKept: Int, val rulesTotal: Int, val sleepLock: Boolean)

/** What Katachi reports about today's puzzles. */
data class KatachiStatus(val solved: Int, val target: Int, val doneToday: Boolean)

/**
 * Kasane's side of the Lume on-device bus. Every call is explicit (one package), guarded by the
 * signature permission, and does nothing when the other app is not installed. No network.
 */
object Ecosystem {
    const val PERMISSION = "app.lume.permission.ECOSYSTEM_SYNC"

    const val REN = "app.ren.japanese"
    const val KATACHI = "app.katachi"
    const val VOW = "com.vow.app"
    const val SIGIL = "com.sigil.app"
    const val DUOLINGO = "com.duolingo"

    const val ACTION_LOG_CHECK = "app.kasane.action.LOG_CHECK"
    const val ACTION_LOG_COUNT = "app.kasane.action.LOG_COUNT"
    const val ACTION_ECO_METRICS = "app.kasane.action.ECO_METRICS"
    const val ACTION_FLOOR_MODE = "app.lume.action.FLOOR_MODE"
    const val ACTION_RULE_EVENT = "app.lume.action.RULE_EVENT"

    val REN_STATUS: Uri = Uri.parse("content://app.ren.japanese.provider/status")
    val VOW_STATUS: Uri = Uri.parse("content://com.vow.app.provider/status")
    val KATACHI_STATUS: Uri = Uri.parse("content://app.katachi.provider/status")

    fun installed(context: Context, pkg: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") context.packageManager.getPackageInfo(pkg, 0)
        }
    }.isSuccess

    /** Tells Ren and Katachi that today is a Floor Mode day (5 cards, 1 puzzle), or that it no longer is. */
    fun sendFloorMode(context: Context, on: Boolean) {
        for (pkg in listOf(REN, KATACHI)) {
            if (!installed(context, pkg)) continue
            val intent = Intent(ACTION_FLOOR_MODE).setPackage(pkg).putExtra("on", on)
            runCatching { context.sendBroadcast(intent, PERMISSION) }
        }
    }

    fun renStatus(context: Context): RenStatus? {
        if (!installed(context, REN)) return null
        return query(context, REN_STATUS) { c ->
            RenStatus(c.int("due"), c.int("retention"), c.int("streak"), c.int("doneToday") != 0)
        }
    }

    fun vowStatus(context: Context): VowStatus? {
        if (!installed(context, VOW)) return null
        return query(context, VOW_STATUS) { c ->
            VowStatus(c.int("reclaimedMinutes"), c.int("rulesKept"), c.int("rulesTotal"), c.int("sleepLock") != 0)
        }
    }

    fun katachiStatus(context: Context): KatachiStatus? {
        if (!installed(context, KATACHI)) return null
        return query(context, KATACHI_STATUS) { c ->
            KatachiStatus(c.int("solved"), c.int("target"), c.int("doneToday") != 0)
        }
    }

    private fun android.database.Cursor.int(name: String): Int =
        getColumnIndex(name).takeIf { it >= 0 }?.let { getInt(it) } ?: 0

    private fun <T> query(context: Context, uri: Uri, read: (android.database.Cursor) -> T): T? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { if (it.moveToFirst()) read(it) else null }
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: RuntimeException) {
        null
    }

    /** `ren://session` or, for the 5-card round, `ren://session/quick`. Null if Ren is not installed. */
    fun renIntent(context: Context, quick: Boolean): Intent? = deepLink(context, REN, if (quick) "ren://session/quick" else "ren://session")

    fun katachiIntent(context: Context): Intent? = deepLink(context, KATACHI, "katachi://puzzle/next")

    fun sigilIntent(context: Context): Intent? = deepLink(context, SIGIL, "sigil://note/quick")

    private fun deepLink(context: Context, pkg: String, uri: String): Intent? =
        if (installed(context, pkg)) Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else null
}
