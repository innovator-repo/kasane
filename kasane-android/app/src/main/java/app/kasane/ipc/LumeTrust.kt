package app.kasane.ipc

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Process

/**
 * Who may use the Lume links. The five apps can be signed with different keys, so a signature
 * permission cannot be the gate, and a permission two apps both declare stops the second one from
 * installing. Instead each broadcast carries a PendingIntent made by the sending app, whose creator
 * Android reports truthfully, and each provider checks the calling package. Only Lume apps pass;
 * a missing app simply never calls, and its integration stays off.
 */
object LumeTrust {
    val PEERS = setOf("com.vow.app", "app.katachi", "app.kasane", "com.sigil.app", "app.ren.japanese")

    private const val EXTRA_SENDER = "app.lume.extra.SENDER"
    /** Nothing receives this action; the PendingIntent is only proof of who made it. */
    private const val ACTION_IDENTITY = "app.lume.action.IDENTITY"

    /** Adds proof of the sending app to an outgoing broadcast. */
    fun stamp(context: Context, intent: Intent): Intent {
        val proof = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_IDENTITY).setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE
        )
        return intent.putExtra(EXTRA_SENDER, proof)
    }

    /** The Lume app that sent [intent], or null when it carries no proof or came from anyone else. */
    fun sender(intent: Intent): String? = runCatching {
        val proof = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_SENDER, PendingIntent::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra<PendingIntent>(EXTRA_SENDER)
        proof?.creatorPackage
    }.getOrNull()?.takeIf(::isPeer)

    fun fromPeer(intent: Intent): Boolean = sender(intent) != null

    fun isPeer(pkg: String?): Boolean = pkg != null && pkg in PEERS

    /** For providers: the app itself (its widgets, its own reads) or a Lume app. Pass ContentProvider.callingPackage. */
    fun isPeerCaller(callingPackage: String?): Boolean =
        Binder.getCallingUid() == Process.myUid() || isPeer(callingPackage)
}
