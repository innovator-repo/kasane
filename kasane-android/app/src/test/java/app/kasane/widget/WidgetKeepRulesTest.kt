package app.kasane.widget

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Regression: R8 renamed the widget classes, so after an update a widget slot could load another widget. */
class WidgetKeepRulesTest {
    @Test fun widgetAndReceiverClassNamesSurviveMinification() {
        val rules = File("proguard-rules.pro").readText()
        assertTrue(rules.contains("-keepnames class * extends androidx.glance.appwidget.GlanceAppWidget"))
        assertTrue(rules.contains("-keepnames class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver"))
    }
}
