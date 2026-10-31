package app.kasane.ui

import app.kasane.designsystem.LumeTokens
import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.kasane.R
import app.kasane.reminders.AlarmScheduler

/**
 * The three things reminders need on a Samsung phone, one at a time, until
 * all are done. Then the card disappears.
 */
@Composable
fun SetupCard() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refresh++
                AlarmScheduler.rearm(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    // Reading `refresh` re-runs these checks after each permission screen.
    val step = (if (refresh < 0) null else when {
        !notificationsAllowed(context) -> Step(R.string.setup_notifications, R.string.setup_allow) {
            if (Build.VERSION.SDK_INT >= 33) notify.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        !exactAlarmsAllowed(context) -> Step(R.string.setup_alarms, R.string.setup_open_settings) {
            if (Build.VERSION.SDK_INT >= 31) {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            }
        }
        !batteryUnrestricted(context) -> Step(R.string.setup_battery, R.string.setup_allow) {
            requestUnrestricted(context)
        }
        else -> null
    }) ?: return

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(LumeTokens.Shape.M))
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(stringResource(step.body), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(top = 4.dp))
        TextButton(onClick = step.action, modifier = Modifier.padding(top = 4.dp)) { Text(stringResource(step.button)) }
    }
}

private class Step(val body: Int, val button: Int, val action: () -> Unit)

private fun notificationsAllowed(context: Context) = Build.VERSION.SDK_INT < 33 ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun exactAlarmsAllowed(context: Context) = Build.VERSION.SDK_INT < 31 ||
    context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

private fun batteryUnrestricted(context: Context) =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

@SuppressLint("BatteryLife")
private fun requestUnrestricted(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    runCatching { context.startActivity(intent) }
        .onFailure { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
}
