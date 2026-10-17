package app.kasane.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kasane.R
import app.kasane.core.Alert
import app.kasane.core.SleepLock
import app.kasane.store
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun TasksScreen(modifier: Modifier = Modifier, addRequested: Boolean = false, onAddShown: () -> Unit = {}) {
    val store = LocalContext.current.store
    val journal by store.journal.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    // A widget's add button opens the form straight away.
    LaunchedEffect(addRequested) {
        if (addRequested) {
            adding = true
            onAddShown()
        }
    }
    val today = LocalDate.now()
    // Open tasks by the date they are next due, then finished one-offs from the last week, newest first.
    val open = journal.tasks.filter { it.open }.sortedBy { it.nextDate(today) ?: LocalDate.MAX }
    val finished = journal.tasks.filter { !it.open && it.date >= today.minusDays(7).toString() }.sortedByDescending { it.date }

    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(stringResource(R.string.tab_tasks), style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp))
            }
            if (open.isEmpty()) {
                item {
                    Text(stringResource(R.string.tasks_empty), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
            items(open + finished, key = { it.id }) { task ->
                val date = task.nextDate(today)
                val done = task.doneOn(today)
                val late = date != null && date.isBefore(today) && !done
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = done, onCheckedChange = { store.setTaskDone(task.id, it) })
                    Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        Text(task.title, style = MaterialTheme.typography.bodyLarge)
                        val time = task.remindAt?.let { "%02d:%02d".format(it / 60, it % 60) }
                        val day = date?.format(DateTimeFormatter.ofPattern("EEE d MMM")) ?: task.date
                        val line = if (task.repeats) {
                            listOfNotNull(
                                if (done) stringResource(R.string.tasks_done_today)
                                else weekdaysLabel(task.repeat) ?: stringResource(R.string.habit_every_day),
                                time,
                                stringResource(R.string.tasks_next, day),
                            )
                        } else listOfNotNull(day, time)
                        Text(line.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { store.deleteTask(task.id) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
            }
        }
        FloatingActionButton(onClick = { adding = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.tasks_add))
        }
    }
    if (adding) AddTaskDialog(onDismiss = { adding = false }) { title, date, minute, repeat, alert ->
        store.addTask(title, date, minute, repeat, alert)
        adding = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTaskDialog(onDismiss: () -> Unit, onAdd: (String, LocalDate, Int?, List<Int>, Alert) -> Unit) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var minute by remember { mutableStateOf<Int?>(9 * 60) }
    var repeat by remember { mutableStateOf(Repeat.ONCE) }
    var weekdays by remember { mutableStateOf(emptyList<Int>()) }
    var alert by remember { mutableStateOf(Alert.MEDIUM) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val lock = remember { SleepLock() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tasks_add)) },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true,
                    label = { Text(stringResource(R.string.tasks_what)) }, modifier = Modifier.fillMaxWidth())
                Label(stringResource(R.string.tasks_repeat))
                Chips(listOf(Repeat.ONCE to R.string.tasks_once, Repeat.EVERY_DAY to R.string.habit_every_day,
                    Repeat.PICK to R.string.habit_pick_days), repeat) { repeat = it }
                if (repeat == Repeat.PICK) WeekdayPicker(weekdays) { weekdays = it }
                TextButton(onClick = { pickDate = true }) {
                    val day = date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy"))
                    Text(if (repeat == Repeat.ONCE) day else stringResource(R.string.tasks_starts, day))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { pickTime = true }) {
                        Text(minute?.let { stringResource(R.string.tasks_remind_at, "%02d:%02d".format(it / 60, it % 60)) }
                            ?: stringResource(R.string.tasks_no_reminder))
                    }
                    if (minute != null) TextButton(onClick = { minute = null }) { Text(stringResource(R.string.tasks_clear)) }
                }
                val chosen = minute
                if (chosen != null && lock.isLocked(LocalTime.of(chosen / 60, chosen % 60))) {
                    Text(stringResource(R.string.tasks_in_lock), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
                if (minute != null) AlertPicker(alert) { alert = it }
            }
        },
        confirmButton = {
            val days = when (repeat) {
                Repeat.ONCE -> emptyList()
                Repeat.EVERY_DAY -> (1..7).toList()
                Repeat.PICK -> weekdays
            }
            TextButton(enabled = title.isNotBlank() && (repeat != Repeat.PICK || weekdays.isNotEmpty()),
                onClick = { onAdd(title, date, minute, days, alert) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    }
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

private enum class Repeat { ONCE, EVERY_DAY, PICK }
