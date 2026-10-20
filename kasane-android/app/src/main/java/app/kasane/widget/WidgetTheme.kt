package app.kasane.widget

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import app.kasane.core.Orchestrator
import app.kasane.designsystem.LumePalette
import app.kasane.store
import java.time.LocalDateTime
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.kasane.MainActivity
import app.kasane.R
import app.kasane.ui.Dark
import app.kasane.ui.Light

private val Colors = ColorProviders(light = Light, dark = Dark)

@Composable
internal fun KasaneWidgetTheme(content: @Composable () -> Unit) = GlanceTheme(colors = Colors, content = content)

/** The widget's background; tapping anywhere outside a button opens the app on [tab]. */
@Composable
internal fun WidgetCard(tab: Int, content: @Composable ColumnScope.() -> Unit) {
    val context = LocalContext.current
    val open = Intent(context, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_TAB, tab)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    // After 21:00, with Ren or Duolingo still open, the card gets an amber ring.
    val journal by context.store.journal.collectAsState()
    val urgent = Orchestrator.urgent(journal, LocalDateTime.now(), context.store.floorToday())
    Box(
        GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(22.dp)
            .background(if (urgent) ColorProvider(LumePalette.Urgent) else ColorProvider(Color.Transparent))
            .padding(if (urgent) 2.dp else 0.dp),
    ) {
        Column(
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.surface)
                .cornerRadius(20.dp).padding(14.dp).clickable(actionStartActivity(open)),
            content = content,
        )
    }
}

/** A small "+ Add" button that opens the app's habit / task / rule chooser. */
@Composable
internal fun AddButton() {
    val context = LocalContext.current
    val add = Intent(context, MainActivity::class.java)
        .setAction(MainActivity.ACTION_ADD_FROM_WIDGET)
        .putExtra(MainActivity.EXTRA_PROMPT_ADD_TYPE, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    Box(
        GlanceModifier.background(GlanceTheme.colors.primaryContainer).cornerRadius(14.dp)
            .padding(horizontal = 10.dp, vertical = 4.dp).clickable(actionStartActivity(add)),
    ) {
        Text(context.getString(R.string.widget_add_task), style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer,
            fontSize = 13.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
internal fun headlineStyle(size: Int) =
    TextStyle(color = GlanceTheme.colors.onSurface, fontSize = size.sp, fontWeight = FontWeight.Medium)

@Composable
internal fun bodyStyle() = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp)

@Composable
internal fun labelStyle() = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp)
