package app.kasane.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.kasane.MainActivity
import app.kasane.R
import app.kasane.core.NextUp
import app.kasane.store
import java.time.LocalDate

/** The next task, a big Done button and how many are left. Works with no tasks at all. */
class NextUpWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = context.store
        provideContent {
            val journal by store.journal.collectAsState()
            val summary = NextUp.summary(journal.tasks, LocalDate.now())
            KasaneWidgetTheme { NextUpContent(summary.next?.id, summary.next?.title, summary.nextAtMinute, summary.openCount) }
        }
    }
}

class NextUpWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = NextUpWidget()
}

internal fun openKasane(context: Context): Intent =
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

@Composable
private fun NextUpContent(taskId: String?, title: String?, atMinute: Int, open: Int) {
    val context = androidx.glance.LocalContext.current
    Box(GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(22.dp).background(GlanceTheme.colors.surface)
        .clickable(actionStartActivity(openKasane(context))).padding(14.dp)) {
        Column(GlanceModifier.fillMaxSize()) {
            Text((if (atMinute >= 0) context.getString(R.string.now_next_up_at, "%02d:%02d".format(atMinute / 60, atMinute % 60))
            else context.getString(R.string.now_next_up)).uppercase(),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            Spacer(GlanceModifier.height(4.dp))
            Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.CenterStart) {
                Text(title ?: context.getString(R.string.now_clear),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Medium), maxLines = 3)
            }
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (taskId != null) {
                    Box(GlanceModifier.background(GlanceTheme.colors.primary).cornerRadius(20.dp).padding(horizontal = 18.dp, vertical = 10.dp)
                        .clickable(actionRunCallback<FinishTaskAction>(actionParametersOf(TaskIdKey to taskId)))) {
                        Text(context.getString(R.string.widget_done), style = TextStyle(color = GlanceTheme.colors.onPrimary,
                            fontSize = 14.sp, fontWeight = FontWeight.Medium))
                    }
                    Spacer(GlanceModifier.width(10.dp))
                    Text(context.getString(R.string.widget_nextup_left, open), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
                } else AddButton()
            }
        }
    }
}
