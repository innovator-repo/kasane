package app.kasane.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kasane.R
import app.kasane.designsystem.Lume
import app.kasane.store

/** Reached from the gear: what reminders need on the phone, and the low-energy day switch. */
@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val store = LocalContext.current.store
    val floor by store.floorMode.collectAsStateWithLifecycle()
    Column(modifier.verticalScroll(rememberScrollState())) {
        BackHeader(stringResource(R.string.settings_title), onBack)
        SetupCard()
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_floor), color = Lume.colors.text, fontSize = 16.sp)
                Text(stringResource(R.string.settings_floor_hint), color = Lume.colors.textSecondary, fontSize = 14.sp)
            }
            Switch(checked = floor, onCheckedChange = { store.setFloorMode(it) })
        }
    }
}
