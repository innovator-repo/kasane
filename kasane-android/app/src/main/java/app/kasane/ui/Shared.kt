package app.kasane.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kasane.designsystem.Lume

@Composable
fun SectionLabel(text: String) {
    Text(text.uppercase(), fontSize = 12.sp, color = Lume.colors.textSecondary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp))
}

/** Opens an app by package, or a site in Chrome. Missing apps open their Play Store page. */
internal fun open(context: Context, target: String) {
    val intent = if (target.startsWith("http")) {
        Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
            if (context.packageManager.getLaunchIntentForPackage("com.android.chrome") != null) setPackage("com.android.chrome")
        }
    } else {
        context.packageManager.getLaunchIntentForPackage(target)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$target"))
    }
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}

/** The `< Kasane` back row above a secondary screen. */
@Composable
fun BackHeader(title: String, onBack: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 16.dp, top = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        androidx.compose.material3.IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = androidx.compose.ui.res.stringResource(app.kasane.R.string.now_back), tint = Lume.colors.text)
        }
        Text(androidx.compose.ui.res.stringResource(app.kasane.R.string.app_name) + "  ·  " + title, color = Lume.colors.textSecondary, fontSize = 14.sp)
    }
}
