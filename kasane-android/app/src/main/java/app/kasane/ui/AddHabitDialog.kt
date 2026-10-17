package app.kasane.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.kasane.R
import app.kasane.core.Block
import app.kasane.core.Days
import app.kasane.core.Item
import app.kasane.core.Kind
import app.kasane.core.SleepLock
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

/** Adds a habit or rule of your own to Today, on chosen days, with an optional reminder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHabitDialog(onDismiss: () -> Unit, onAdd: (Item) -> Unit) {
    var title by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(Kind.CHECK) }
    var target by remember { mutableStateOf("") }
    var block by remember { mutableStateOf(Block.DAY) }
    // Null means the days picked in `weekdays`.
    var days by remember { mutableStateOf<Days?>(Days.EVERY_DAY) }
    var weekdays by remember { mutableStateOf(emptyList<Int>()) }
    var minute by remember { mutableStateOf<Int?>(null) }
    var pickTime by remember { mutableStateOf(false) }
    val lock = remember { SleepLock() }
    val needsTarget = kind == Kind.COUNT || kind == Kind.TIMED
    val goal = target.toIntOrNull()?.takeIf { it > 0 }
    val ready = title.isNotBlank() && (!needsTarget || goal != null) && (days != null || weekdays.isNotEmpty())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.habit_add)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true,
                    label = { Text(stringResource(R.string.tasks_what)) }, modifier = Modifier.fillMaxWidth())

                Label(stringResource(R.string.habit_kind))
                Chips(listOf(Kind.CHECK to R.string.habit_kind_check, Kind.COUNT to R.string.habit_kind_count,
                    Kind.TIMED to R.string.habit_kind_timed, Kind.RULE to R.string.habit_kind_rule), kind) { kind = it }
                Text(stringResource(when (kind) {
                    Kind.CHECK -> R.string.habit_kind_check_hint
                    Kind.COUNT -> R.string.habit_kind_count_hint
                    Kind.TIMED -> R.string.habit_kind_timed_hint
                    Kind.RULE -> R.string.habit_kind_rule_hint
                }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (needsTarget) {
                    OutlinedTextField(value = target, onValueChange = { v -> target = v.filter { it.isDigit() }.take(4) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text(stringResource(if (kind == Kind.COUNT) R.string.habit_goal_count else R.string.habit_goal_minutes)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }

                if (kind != Kind.RULE) {
                    Label(stringResource(R.string.habit_when))
                    Chips(listOf(Block.MORNING to R.string.block_morning, Block.DAY to R.string.block_day,
                        Block.NIGHT to R.string.block_night), block) { block = it }
                }

                Label(stringResource(R.string.habit_days))
                Chips(listOf(Days.EVERY_DAY to R.string.habit_every_day, Days.OFFICE_DAYS to R.string.habit_weekdays,
                    null to R.string.habit_pick_days), days) { days = it }
                if (days == null) WeekdayPicker(weekdays) { weekdays = it }

                if (kind != Kind.RULE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { pickTime = true }) {
                            Text(minute?.let { stringResource(R.string.tasks_remind_at, "%02d:%02d".format(it / 60, it % 60)) }
                                ?: stringResource(R.string.habit_add_reminder))
                        }
                        if (minute != null) TextButton(onClick = { minute = null }) { Text(stringResource(R.string.tasks_clear)) }
                    }
                    val chosen = minute
                    if (chosen != null && lock.isLocked(LocalTime.of(chosen / 60, chosen % 60))) {
                        Text(stringResource(R.string.tasks_in_lock), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = ready, onClick = {
                onAdd(Item(
                    id = "own_" + UUID.randomUUID().toString(),
                    title = title.trim(),
                    kind = kind,
                    block = if (kind == Kind.RULE) Block.ALL_DAY else block,
                    days = days ?: Days.EVERY_DAY,
                    weekdays = if (days == null) weekdays.sorted() else emptyList(),
                    target = if (needsTarget) goal ?: 0 else 0,
                    remindAt = if (kind == Kind.RULE) null else minute,
                ))
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )

    if (pickTime) {
        val start = minute ?: (9 * 60)
        val state = rememberTimePickerState(initialHour = start / 60, initialMinute = start % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = { minute = state.hour * 60 + state.minute; pickTime = false }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Toggles for Monday to Sunday. Days are 1 = Monday to 7 = Sunday. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun WeekdayPicker(selected: List<Int>, onChange: (List<Int>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (day in DayOfWeek.entries) {
            val on = day.value in selected
            FilterChip(selected = on, onClick = { onChange(if (on) selected - day.value else selected + day.value) },
                label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) })
        }
    }
}

/** Short names of the chosen days, such as "Mon, Thu", or null when it is every day. */
internal fun weekdaysLabel(days: List<Int>): String? =
    if (days.toSet().size == 7) null
    else days.sorted().joinToString(", ") { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault()) }

@Composable
internal fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun <T> Chips(options: List<Pair<T, Int>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((value, label) in options) {
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(stringResource(label)) })
        }
    }
}
